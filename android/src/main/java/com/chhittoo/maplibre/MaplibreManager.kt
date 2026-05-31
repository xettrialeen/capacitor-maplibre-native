package com.chhittoo.maplibre

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.getcapacitor.Bridge
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import kotlin.math.*

class MapTouchForwarder(context: Context) : View(context) {
    var target: MapView? = null
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val mv = target ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN ->
                parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        val copy = MotionEvent.obtain(event)
        mv.dispatchTouchEvent(copy)
        copy.recycle()
        return true
    }
}

// ─────────────────────────────────────────────
// Marker data — persists across style switches
// so markers are automatically re-drawn when tiles change
// ─────────────────────────────────────────────
data class MarkerData(
    val lat: Double, val lng: Double,
    val iconName: String, val title: String
)

// ─────────────────────────────────────────────
// Route progress data returned to JS on every
// driver position update when a route is active
// ─────────────────────────────────────────────
data class RouteProgress(
    val distanceRemainingKm: Double,
    val distanceTraveledKm: Double,
    val totalDistanceKm: Double,
    val percentComplete: Double,
    val etaMinutes: Int
)

class MaplibreManager(
    private val bridge: Bridge,
    private val activity: Activity
) {
    private var mapView: MapView? = null
    private var touchForwarder: MapTouchForwarder? = null
    private var maplibreMap: MapLibreMap? = null
    private var currentStyle: Style? = null
    private var locationActivated = false

    private var driverLatLng       = LatLng(0.0, 0.0)
    private var driverAnimator: ValueAnimator? = null
    private var cityTourAnimator: ValueAnimator? = null
    private var lastDriverBearing  = 0f
    private var is3DBuildingsOn    = false
    private var is3DTerrainOn      = false

    // Route progress — store full coordinates so we can split them
    // as the driver advances along the route
    private var routePoints = listOf<Pair<Double, Double>>() // (lng, lat)

    // Layer / source IDs
    private val DRIVER_SOURCE_ID       = "chhittoo-driver-source"
    private val DRIVER_LAYER_ID        = "chhittoo-driver-layer"
    private val ROUTE_SOURCE_ID        = "chhittoo-route-source"        // remaining portion
    private val ROUTE_LAYER_ID         = "chhittoo-route-layer"
    private val ROUTE_BORDER_ID        = "chhittoo-route-border"
    private val ROUTE_TRAVELED_SOURCE  = "chhittoo-route-traveled"      // completed portion
    private val ROUTE_TRAVELED_LAYER   = "chhittoo-route-traveled-layer"
    private val BUILDINGS_LAYER_ID     = "chhittoo-3d-buildings"
    private val DRIVER_ICON            = "chhittoo-driver-icon"
    private val PICKUP_ICON            = "chhittoo-pickup-icon"
    private val DROPOFF_ICON           = "chhittoo-dropoff-icon"
    private val RIDER_ICON             = "chhittoo-rider-icon"

    private val markerSources  = mutableMapOf<String, GeoJsonSource>()
    private val markerLayers   = mutableMapOf<String, SymbolLayer>()
    // Marker data survives style switches — sources/layers don't, but positions do
    private val markerDataMap  = mutableMapOf<String, MarkerData>()
    // Route style survives style switches so the line colour/width is preserved
    private var routeColor     = "#18181b"
    private var routeWidth     = 4f
    private var currentMode    = "rider"

    // ─────────────────────────────────────────────
    // STYLE helper
    // ─────────────────────────────────────────────

    private fun applyStyle(map: MapLibreMap, styleStr: String, onLoaded: Style.OnStyleLoaded) {
        if (styleStr.trimStart().startsWith('{'))
            map.setStyle(Style.Builder().fromJson(styleStr), onLoaded)
        else
            map.setStyle(styleStr, onLoaded)
    }

    // ─────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────

    fun create(
        lat: Double, lng: Double, zoom: Double, styleUrl: String, mode: String,
        xPx: Int, yPx: Int, widthPx: Int, heightPx: Int,
        onReady: () -> Unit, onError: (String) -> Unit
    ) {
        activity.runOnUiThread {
            try {
                MapLibre.getInstance(activity)
                currentMode = mode
                is3DBuildingsOn = false; is3DTerrainOn = false
                locationActivated = false
                routePoints = emptyList()

                val cameraPos = CameraPosition.Builder()
                    .target(LatLng(lat, lng))
                    .zoom(if (mode == "driver") 17.0 else zoom)
                    .tilt(if (mode == "driver") 45.0 else 0.0)
                    .bearing(0.0).build()

                val parent = bridge.webView.parent as ViewGroup
                parent.isMotionEventSplittingEnabled = false

                mapView = MapView(activity)
                parent.addView(mapView, 0, ViewGroup.LayoutParams(widthPx, heightPx))
                mapView!!.x = xPx.toFloat()
                mapView!!.y = yPx.toFloat()

                touchForwarder = MapTouchForwarder(activity).also {
                    it.target = mapView
                    parent.addView(it, ViewGroup.LayoutParams(widthPx, heightPx))
                    it.x = xPx.toFloat(); it.y = yPx.toFloat()
                }

                mapView!!.onCreate(null)
                mapView!!.onStart()
                mapView!!.onResume()

                mapView!!.getMapAsync { map ->
                    maplibreMap = map
                    map.cameraPosition = cameraPos
                    map.uiSettings.apply {
                        isAttributionEnabled    = false; isLogoEnabled           = false
                        isCompassEnabled        = true;  isRotateGesturesEnabled = true
                        isTiltGesturesEnabled   = true;  isScrollGesturesEnabled = true
                        isZoomGesturesEnabled   = true
                    }
                    applyStyle(map, styleUrl) { style ->
                        currentStyle = style
                        setupIcons(style)
                        setupDriverLayer(style, LatLng(lat, lng))
                        setupRouteLayer(style)
                        onReady()
                    }
                }
            } catch (e: Exception) { onError(e.message ?: "Unknown error") }
        }
    }

    fun destroy() {
        driverAnimator?.cancel(); cityTourAnimator?.cancel()
        mapView?.onPause(); mapView?.onStop(); mapView?.onDestroy()
        val parent = bridge.webView.parent as? ViewGroup
        parent?.isMotionEventSplittingEnabled = true
        parent?.removeView(touchForwarder); parent?.removeView(mapView)
        touchForwarder = null; mapView = null; maplibreMap = null; currentStyle = null
        markerSources.clear(); markerLayers.clear(); markerDataMap.clear()
        routePoints = emptyList(); routeColor = "#18181b"; routeWidth = 4f
        is3DBuildingsOn = false; is3DTerrainOn = false; locationActivated = false
    }

    fun updateBounds(xPx: Int, yPx: Int, widthPx: Int, heightPx: Int) {
        listOf<View?>(mapView, touchForwarder).filterNotNull().forEach { v ->
            v.x = xPx.toFloat(); v.y = yPx.toFloat()
            v.layoutParams = v.layoutParams.also { it.width = widthPx; it.height = heightPx }
            v.requestLayout()
        }
    }

    // ─────────────────────────────────────────────
    // ICONS
    // ─────────────────────────────────────────────

    private fun setupIcons(style: Style) {
        loadIcon(style, DRIVER_ICON,  "ic_driver_car", Color.parseColor("#4A90E2"))
        loadIcon(style, PICKUP_ICON,  "ic_pickup",     Color.parseColor("#27AE60"))
        loadIcon(style, DROPOFF_ICON, "ic_dropoff",    Color.parseColor("#E74C3C"))
        loadIcon(style, RIDER_ICON,   "ic_rider",      Color.parseColor("#8E44AD"))
    }

    private fun loadIcon(style: Style, iconId: String, drawableName: String, fallbackColor: Int) {
        val resId = activity.resources.getIdentifier(drawableName, "drawable", activity.packageName)
        val bmp: Bitmap = if (resId != 0) {
            ContextCompat.getDrawable(activity, resId)?.let { d ->
                val s = 96; val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, s, s); d.draw(Canvas(b)); b
            } ?: makeFallbackCircle(fallbackColor)
        } else makeFallbackCircle(fallbackColor)
        style.addImage(iconId, bmp)
    }

    private fun makeFallbackCircle(color: Int): Bitmap {
        val s = 64; val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        c.drawCircle(32f, 32f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawCircle(32f, 32f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f })
        return b
    }

    // ─────────────────────────────────────────────
    // DRIVER LAYER
    // ─────────────────────────────────────────────

    private fun setupDriverLayer(style: Style, initial: LatLng) {
        driverLatLng = initial
        val feature = Feature.fromGeometry(Point.fromLngLat(initial.longitude, initial.latitude))
        feature.addNumberProperty("bearing", 0f)
        style.addSource(GeoJsonSource(DRIVER_SOURCE_ID, FeatureCollection.fromFeature(feature)))
        style.addLayer(SymbolLayer(DRIVER_LAYER_ID, DRIVER_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage(DRIVER_ICON),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotationAlignment("map"),
                PropertyFactory.iconSize(1.2f),
                PropertyFactory.iconAnchor("center")
            )
        })
    }

    // ─────────────────────────────────────────────
    // ROUTE LAYER
    //
    // Three layers (bottom to top):
    //   1. traveled border  — white, wide, behind traveled line
    //   2. traveled line    — gray, shows completed portion of route
    //   3. remaining border — white, wide, behind remaining line
    //   4. remaining line   — brand color, shows ahead portion
    //
    // As the driver moves, the split point between traveled/remaining
    // updates on every GPS position, giving Uber-style progress.
    // ─────────────────────────────────────────────

    private fun setupRouteLayer(style: Style) {
        // Remaining route source (full route initially, shrinks as driver advances)
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID,
            FeatureCollection.fromFeatures(emptyArray<Feature>())))

        // Traveled route source (empty initially, grows as driver advances)
        style.addSource(GeoJsonSource(ROUTE_TRAVELED_SOURCE,
            FeatureCollection.fromFeatures(emptyArray<Feature>())))

        // White border behind remaining route
        style.addLayerBelow(LineLayer(ROUTE_BORDER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color.WHITE), PropertyFactory.lineWidth(9f),
                PropertyFactory.lineJoin("round"), PropertyFactory.lineCap("round"),
                PropertyFactory.lineOpacity(0.9f))
        }, DRIVER_LAYER_ID)

        // Remaining route — main color, full width
        style.addLayerBelow(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor("#18181b"), PropertyFactory.lineWidth(5f),
                PropertyFactory.lineJoin("round"), PropertyFactory.lineCap("round"))
        }, DRIVER_LAYER_ID)

        // Traveled route — gray, slightly thinner (below remaining so overlap looks clean)
        style.addLayerBelow(LineLayer(ROUTE_TRAVELED_LAYER, ROUTE_TRAVELED_SOURCE).apply {
            setProperties(
                PropertyFactory.lineColor("#9ca3af"), PropertyFactory.lineWidth(4f),
                PropertyFactory.lineJoin("round"), PropertyFactory.lineCap("round"),
                PropertyFactory.lineOpacity(0.8f))
        }, ROUTE_LAYER_ID)
    }

    // ─────────────────────────────────────────────
    // DRIVER ANIMATION + ROUTE PROGRESS
    // ─────────────────────────────────────────────

    fun updateDriverMarker(lat: Double, lng: Double, bearing: Float, durationMs: Int): RouteProgress? {
        val map   = maplibreMap ?: return null
        val style = currentStyle ?: return null
        val to    = LatLng(lat, lng)

        driverAnimator?.cancel()
        driverAnimator = ValueAnimator.ofObject(LatLngEvaluator(), driverLatLng, to).apply {
            duration = durationMs.toLong(); interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                // Use currentStyle / maplibreMap (live properties) — NOT the 'style'
                // and 'map' values captured at animation-start time.
                // When the user switches tiles, MapLibre destroys the old Style object.
                // The closed-over 'style' would then point to a dead object, crashing
                // on getSourceAs(). Using the live property means we either get the
                // new style (correct) or null (safe no-op via ?.).
                val activeStyle = currentStyle ?: return@addUpdateListener
                val activeMap   = maplibreMap  ?: return@addUpdateListener

                val pos = anim.animatedValue as LatLng
                val ib  = interpolateBearing(lastDriverBearing, bearing, anim.animatedFraction)
                val f   = Feature.fromGeometry(Point.fromLngLat(pos.longitude, pos.latitude))
                f.addNumberProperty("bearing", ib)
                activeStyle.getSourceAs<GeoJsonSource>(DRIVER_SOURCE_ID)
                    ?.setGeoJson(FeatureCollection.fromFeature(f))
                driverLatLng = pos
                if (currentMode == "driver") {
                    activeMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(pos).zoom(17.0).tilt(45.0)
                            .bearing(ib.toDouble()).build()))
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { driverLatLng = to }
            })
        }
        driverAnimator!!.start()

        // Update route split at new driver position and return progress to JS
        return splitRouteAtDriver(lat, lng)
    }

    // ─────────────────────────────────────────────
    // ROUTE PROGRESS — split and measure
    // ─────────────────────────────────────────────

    private fun splitRouteAtDriver(driverLat: Double, driverLng: Double): RouteProgress? {
        val style = currentStyle ?: return null
        if (routePoints.size < 2) return null

        // Find the index of the nearest route point to the driver
        var nearestIdx = 0
        var minDist    = Double.MAX_VALUE
        routePoints.forEachIndexed { i, (lng, lat) ->
            val d = haversineKm(driverLat, driverLng, lat, lng)
            if (d < minDist) { minDist = d; nearestIdx = i }
        }

        // Traveled: start → nearestIdx (inclusive)
        val traveledPts  = routePoints.subList(0, nearestIdx + 1)
        // Remaining: nearestIdx → end
        val remainingPts = routePoints.subList(nearestIdx, routePoints.size)

        // Update traveled source
        if (traveledPts.size >= 2) {
            val line = Feature.fromGeometry(
                LineString.fromLngLats(traveledPts.map { Point.fromLngLat(it.first, it.second) }))
            style.getSourceAs<GeoJsonSource>(ROUTE_TRAVELED_SOURCE)
                ?.setGeoJson(FeatureCollection.fromFeature(line))
        }

        // Update remaining source
        if (remainingPts.size >= 2) {
            val line = Feature.fromGeometry(
                LineString.fromLngLats(remainingPts.map { Point.fromLngLat(it.first, it.second) }))
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeature(line))
        } else {
            // Driver has passed all route points — clear remaining
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        }

        // Calculate distances
        val totalDist    = routePoints.zipWithNext { (lng1, lat1), (lng2, lat2) ->
            haversineKm(lat1, lng1, lat2, lng2) }.sum()
        val traveledDist = if (traveledPts.size >= 2)
            traveledPts.zipWithNext { (lng1, lat1), (lng2, lat2) ->
                haversineKm(lat1, lng1, lat2, lng2) }.sum()
        else 0.0
        val remainingDist = (totalDist - traveledDist).coerceAtLeast(0.0)
        val pct           = if (totalDist > 0) (traveledDist / totalDist * 100.0) else 0.0

        // ETA at average 30 km/h urban speed
        val etaMins = if (remainingDist > 0) (remainingDist / 30.0 * 60.0).toInt().coerceAtLeast(1) else 0

        return RouteProgress(
            distanceRemainingKm = remainingDist,
            distanceTraveledKm  = traveledDist,
            totalDistanceKm     = totalDist,
            percentComplete     = pct,
            etaMinutes          = etaMins
        )
    }

    // Haversine great-circle distance in kilometres
    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a    = sin(dLat / 2).pow(2) +
                   cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun interpolateBearing(from: Float, to: Float, fraction: Float): Float {
        var diff = to - from
        while (diff > 180) diff -= 360; while (diff < -180) diff += 360
        val r = from + diff * fraction; lastDriverBearing = if (fraction >= 1f) to else r; return r
    }

    // ─────────────────────────────────────────────
    // MARKERS
    //
    // FIX: split into two separate SymbolLayers.
    //
    // Root cause of "markers only show on Bright":
    // A single SymbolLayer that has BOTH iconImage and textField
    // fails silently on styles without a "glyphs" URL (raster styles
    // like Google/ESRI) and on some vector styles where glyph loading
    // fails — MapLibre suppresses the ENTIRE symbol (including the icon)
    // if any property in the layer has a rendering error.
    //
    // Fix: icon-only layer (always renders) + text-only layer (optional,
    // fails independently without taking down the icon).
    // ─────────────────────────────────────────────

    fun setMarker(id: String, lat: Double, lng: Double, iconName: String, title: String) {
        val style   = currentStyle ?: throw IllegalStateException("Map style not loaded yet")
        val iconId  = mapIconName(iconName)
        val srcId   = "marker-src-$id"
        val iconLid = "marker-icon-$id"
        val textLid = "marker-text-$id"

        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
        if (title.isNotEmpty()) feature.addStringProperty("title", title)

        // Marker already exists — just move it
        val existing = markerSources[id]
        if (existing != null) {
            existing.setGeoJson(feature)
            return
        }

        // ── RE-REGISTER icon right now, every time ──────────────────────────
        // Root cause of "markers only show on CARTO":
        // OpenFreeMap, Protomaps, ESRI, and Google styles load their sprite
        // asynchronously AFTER OnStyleLoaded fires. When the sprite finishes
        // loading, MapLibre clears and rebuilds its image atlas — silently
        // removing any custom images we added in setupIcons().
        // CARTO's CDN is fast enough that this race doesn't occur there.
        //
        // Fix: always call addImage immediately before creating the SymbolLayer
        // so the image is guaranteed to exist at layer creation time, regardless
        // of when the sprite loaded (or whether it cleared our images).
        loadIcon(style, iconId, markerDrawable(iconName), markerFallback(iconName))

        // Persist data so it survives tile switches
        markerDataMap[id] = MarkerData(lat, lng, iconName, title)

        // First placement — create source + layers
        val source = GeoJsonSource(srcId, feature)
        style.addSource(source)
        markerSources[id] = source

        // Icon layer — isolated from text/glyph rendering
        style.addLayer(SymbolLayer(iconLid, srcId).withProperties(
            PropertyFactory.iconImage(iconId),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.iconAnchor("bottom")
        ))
        style.getLayerAs<SymbolLayer>(iconLid)?.let { markerLayers[id] = it }

        // Text label — separate layer; a glyph failure won't affect the icon
        if (title.isNotEmpty()) {
            style.addLayer(SymbolLayer(textLid, srcId).withProperties(
                PropertyFactory.textField(Expression.get("title")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textAnchor("bottom"),
                PropertyFactory.textOffset(arrayOf(0f, -2.8f)),
                PropertyFactory.textColor("#1a1a1a"),
                PropertyFactory.textHaloColor("#ffffff"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textAllowOverlap(false)
            ))
        }
    }

    private fun markerDrawable(name: String): String = when (name) {
        "driver"  -> "ic_driver_car"
        "dropoff" -> "ic_dropoff"
        "rider"   -> "ic_rider"
        else      -> "ic_pickup"
    }

    private fun markerFallback(name: String): Int = when (name) {
        "driver"  -> Color.parseColor("#18181b")
        "dropoff" -> Color.parseColor("#E74C3C")
        "rider"   -> Color.parseColor("#8E44AD")
        else      -> Color.parseColor("#27AE60")
    }

    fun removeMarker(id: String) {
        val style = currentStyle ?: return
        runCatching { style.removeLayer("marker-icon-$id") }
        runCatching { style.removeLayer("marker-text-$id") }
        runCatching { style.removeSource("marker-src-$id") }
        markerSources.remove(id); markerLayers.remove(id); markerDataMap.remove(id)
    }

    private fun mapIconName(name: String): String = when (name) {
        "driver"  -> DRIVER_ICON;  "pickup"  -> PICKUP_ICON
        "dropoff" -> DROPOFF_ICON; "rider"   -> RIDER_ICON; else -> PICKUP_ICON
    }

    // ─────────────────────────────────────────────
    // ROUTE
    // ─────────────────────────────────────────────

    fun setRoute(points: List<Pair<Double, Double>>, colorHex: String, widthDp: Float) {
        val style = currentStyle ?: return; if (points.isEmpty()) return

        // Store everything — survives style switches
        routePoints = points
        routeColor  = colorHex
        routeWidth  = widthDp

        val collection = FeatureCollection.fromFeature(Feature.fromGeometry(
            LineString.fromLngLats(points.map { Point.fromLngLat(it.first, it.second) })))

        // Show full route in remaining layer; clear traveled
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(collection)
        style.getSourceAs<GeoJsonSource>(ROUTE_TRAVELED_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))

        style.getLayerAs<LineLayer>(ROUTE_LAYER_ID)?.setProperties(
            PropertyFactory.lineColor(Color.parseColor(colorHex)), PropertyFactory.lineWidth(widthDp))
        style.getLayerAs<LineLayer>(ROUTE_BORDER_ID)?.setProperties(PropertyFactory.lineWidth(widthDp + 4f))
        style.getLayerAs<LineLayer>(ROUTE_TRAVELED_LAYER)?.setProperties(
            PropertyFactory.lineWidth((widthDp - 1f).coerceAtLeast(2f)))
    }

    fun clearRoute() {
        val empty = FeatureCollection.fromFeatures(emptyArray<Feature>())
        currentStyle?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(empty)
        currentStyle?.getSourceAs<GeoJsonSource>(ROUTE_TRAVELED_SOURCE)?.setGeoJson(empty)
        routePoints = emptyList()
        routeColor  = "#18181b"
        routeWidth  = 4f
    }

    // ─────────────────────────────────────────────
    // LOCATION + COMPASS
    // ─────────────────────────────────────────────

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun activateLocation(camMode: Int, renderMode: Int) {
        val map   = maplibreMap ?: return
        val style = currentStyle ?: return
        if (!hasLocationPermission()) return

        val lc = map.locationComponent
        val options = LocationComponentOptions.builder(activity)
            .accuracyAnimationEnabled(true)
            .accuracyAlpha(0.12f)
            .accuracyColor(Color.parseColor("#4A90E2"))
            .elevation(5f)
            .build()

        val locationEngineRequest =
            org.maplibre.android.location.engine.LocationEngineRequest.Builder(1000L)
                .setPriority(org.maplibre.android.location.engine.LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setFastestInterval(500L)
                .setDisplacement(1f)
                .build()

        val activationOptions = LocationComponentActivationOptions
            .builder(activity, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .locationEngineRequest(locationEngineRequest)
            .build()

        if (!locationActivated) {
            lc.activateLocationComponent(activationOptions)
            locationActivated = true
        }
        lc.isLocationComponentEnabled = true
        lc.cameraMode  = camMode
        lc.renderMode  = renderMode
    }

    fun enableLocationDot()  = activateLocation(CameraMode.TRACKING,         RenderMode.COMPASS)
    fun enableCompassMode()  = activateLocation(CameraMode.TRACKING_COMPASS,  RenderMode.COMPASS)
    fun enableGpsMode()      = activateLocation(CameraMode.TRACKING_GPS,      RenderMode.GPS)

    fun disableLocation() {
        val lc = maplibreMap?.locationComponent ?: return
        lc.isLocationComponentEnabled = false
        lc.cameraMode = CameraMode.NONE; lc.renderMode = RenderMode.NORMAL
    }

    // ─────────────────────────────────────────────
    // 3D BUILDINGS
    // ─────────────────────────────────────────────

    fun enable3DBuildings() {
        val style = currentStyle ?: return; if (is3DBuildingsOn) return
        val srcId = findVectorBuildingSource(style) ?: return
        try {
            style.addLayer(FillExtrusionLayer(BUILDINGS_LAYER_ID, srcId).apply {
                sourceLayer = "building"
                setFilter(Expression.all(Expression.eq(Expression.get("extrude"), "true"), Expression.has("height")))
                setProperties(
                    PropertyFactory.fillExtrusionColor(
                        Expression.interpolate(Expression.linear(), Expression.zoom(),
                            Expression.stop(15, Expression.literal("#d4cdc6")),
                            Expression.stop(17, Expression.literal("#bfb8b0")))),
                    PropertyFactory.fillExtrusionHeight(
                        Expression.interpolate(Expression.linear(), Expression.zoom(),
                            Expression.stop(14, Expression.literal(0)),
                            Expression.stop(14.5, Expression.get("height")))),
                    PropertyFactory.fillExtrusionBase(
                        Expression.interpolate(Expression.linear(), Expression.zoom(),
                            Expression.stop(14, Expression.literal(0)),
                            Expression.stop(14.5, Expression.get("min_height")))),
                    PropertyFactory.fillExtrusionOpacity(0.75f))
            })
            is3DBuildingsOn = true
            maplibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().tilt(55.0).zoom(16.0).build()), 1200)
        } catch (_: Exception) {}
    }

    fun disable3DBuildings() {
        runCatching { currentStyle?.removeLayer(BUILDINGS_LAYER_ID) }
        is3DBuildingsOn = false
        maplibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().tilt(0.0).build()), 800)
    }

    private fun findVectorBuildingSource(style: Style): String? =
        listOf("openmaptiles","maptiler_planet","maptiler","carto","stadia","versatiles","composite")
            .firstOrNull { id -> runCatching { style.getSourceAs<VectorSource>(id) != null }.getOrDefault(false) }

    fun enable3DTerrain() {
        if (is3DTerrainOn) return; is3DTerrainOn = true
        maplibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().tilt(60.0).zoom(11.0).build()), 1400)
    }

    fun disable3DTerrain() {
        is3DTerrainOn = false
        maplibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().tilt(0.0).zoom(14.0).build()), 800)
    }

    // ─────────────────────────────────────────────
    // CITY TOUR
    // ─────────────────────────────────────────────

    fun startCityTour(durationMs: Int = 20_000) {
        val map = maplibreMap ?: return
        cityTourAnimator?.cancel()
        val startBearing = map.cameraPosition.bearing.toFloat()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().tilt(55.0).zoom(16.0).build()), 1000)
        cityTourAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = durationMs.toLong(); interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                map.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .bearing(((startBearing + anim.animatedValue as Float) % 360).toDouble())
                        .tilt(55.0).zoom(16.0).build()))
            }
        }
        cityTourAnimator!!.start()
    }

    fun stopCityTour() { cityTourAnimator?.cancel(); cityTourAnimator = null }

    // ─────────────────────────────────────────────
    // CAMERA
    // ─────────────────────────────────────────────

    fun setRiderMode() {
        currentMode = "rider"; stopCityTour()
        maplibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(driverLatLng).tilt(0.0).bearing(0.0).zoom(15.0).build()), 800)
    }

    fun setDriverMode(bearing: Float) {
        currentMode = "driver"; stopCityTour()
        maplibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(driverLatLng).zoom(17.0).tilt(45.0)
                .bearing(bearing.toDouble()).build()), 800)
    }

    fun animateCamera(lat: Double, lng: Double, zoom: Double?, pitch: Double?, bearing: Double?, durationMs: Int) {
        val map = maplibreMap ?: return
        val b   = CameraPosition.Builder().target(LatLng(lat, lng))
        zoom?.let { b.zoom(it) }; pitch?.let { b.tilt(it) }; bearing?.let { b.bearing(it) }
        map.animateCamera(CameraUpdateFactory.newCameraPosition(b.build()), durationMs)
    }

    fun fitBounds(points: List<Pair<Double, Double>>, paddingPx: Int) {
        val map = maplibreMap ?: return; if (points.isEmpty()) return
        val b   = LatLngBounds.Builder()
        points.forEach { (lat, lng) -> b.include(LatLng(lat, lng)) }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), paddingPx), 1200)
    }

    // ─────────────────────────────────────────────
    // STYLE
    // ─────────────────────────────────────────────

    fun setStyleUrl(url: String) {
        val map = maplibreMap ?: return

        // Cancel animations before style swap — old Style object will be destroyed
        driverAnimator?.cancel()
        cityTourAnimator?.cancel()

        // Null currentStyle immediately so any calls during the swap window
        // (e.g. updateDriverMarker from JS setInterval) bail safely via ?: return
        currentStyle = null

        is3DBuildingsOn = false; is3DTerrainOn = false; locationActivated = false

        // Clear only the MapLibre references — NOT the data.
        // markerDataMap and routePoints/routeColor/routeWidth survive so we can
        // re-draw everything automatically once the new style is ready.
        markerSources.clear()
        markerLayers.clear()

        applyStyle(map, url) { style ->
            currentStyle = style

            // Rebuild base layers in the new style
            setupIcons(style)
            setupDriverLayer(style, driverLatLng)
            setupRouteLayer(style)

            // Re-draw route if one was active
            if (routePoints.size >= 2) {
                val col = FeatureCollection.fromFeature(Feature.fromGeometry(
                    LineString.fromLngLats(routePoints.map { Point.fromLngLat(it.first, it.second) })))
                style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(col)
                style.getLayerAs<LineLayer>(ROUTE_LAYER_ID)?.setProperties(
                    PropertyFactory.lineColor(Color.parseColor(routeColor)),
                    PropertyFactory.lineWidth(routeWidth))
                style.getLayerAs<LineLayer>(ROUTE_BORDER_ID)?.setProperties(
                    PropertyFactory.lineWidth(routeWidth + 4f))
                style.getLayerAs<LineLayer>(ROUTE_TRAVELED_LAYER)?.setProperties(
                    PropertyFactory.lineWidth((routeWidth - 1f).coerceAtLeast(2f)))
            }

            // Re-draw all markers — icons are re-registered inside setMarker
            markerDataMap.forEach { (id, data) ->
                runCatching { setMarker(id, data.lat, data.lng, data.iconName, data.title) }
            }
        }
    }

    // ─────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────

    fun onStart()     { mapView?.onStart() }
    fun onResume()    { mapView?.onResume() }
    fun onPause()     { mapView?.onPause() }
    fun onStop()      { mapView?.onStop() }
    fun onLowMemory() { mapView?.onLowMemory() }
}

class LatLngEvaluator : TypeEvaluator<LatLng> {
    override fun evaluate(f: Float, s: LatLng, e: LatLng) = LatLng(
        s.latitude  + (e.latitude  - s.latitude)  * f,
        s.longitude + (e.longitude - s.longitude) * f
    )
}