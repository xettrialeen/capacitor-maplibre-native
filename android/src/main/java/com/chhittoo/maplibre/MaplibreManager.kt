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
import org.maplibre.android.location.engine.LocationEngineRequest
// ─────────────────────────────────────────────
// MapTouchForwarder — transparent view above
// the WebView that routes map-area touches to
// the native MapView.
//
// THREE fixes for reliable two-finger rotation:
//
// FIX A — MotionEvent.obtain(event)
//   Android RECYCLES touch events after delivery.
//   If we pass the original event object to MapView
//   it may already be recycled by the time MapLibre's
//   gesture detector processes it. obtain() creates
//   an owned copy we control the lifecycle of.
//
// FIX B — requestDisallowInterceptTouchEvent(true)
//   Once a gesture starts, tell the parent ViewGroup
//   not to steal events (e.g. WebView scroll can
//   intercept mid-gesture and cancel rotation).
//
// FIX C — isMotionEventSplittingEnabled = false
//   (set on the parent in create())
//   Android can SPLIT multi-touch events between
//   different child views. First finger → Forwarder,
//   second finger → WebView. This breaks rotation
//   because MapLibre needs BOTH pointers. Disabling
//   splitting ensures all pointers in a gesture
//   sequence go to the same view that got ACTION_DOWN.
// ─────────────────────────────────────────────
class MapTouchForwarder(context: Context) : View(context) {
    var target: MapView? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val mv = target ?: return false

        // FIX B: once a gesture starts, block parent interception
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN ->
                parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }

        // FIX A: copy the event — Android recycles the original
        // after onTouchEvent returns; MapLibre reads it asynchronously
        val copy = MotionEvent.obtain(event)
        mv.dispatchTouchEvent(copy)
        copy.recycle()

        return true
    }
}

class MaplibreManager(
    private val bridge: Bridge,
    private val activity: Activity
) {
    private var mapView: MapView? = null
    private var touchForwarder: MapTouchForwarder? = null
    private var maplibreMap: MapLibreMap? = null
    private var currentStyle: Style? = null
    private var locationActivated = false

    private var driverLatLng        = LatLng(0.0, 0.0)
    private var driverAnimator: ValueAnimator? = null
    private var cityTourAnimator: ValueAnimator? = null
    private var lastDriverBearing   = 0f
    private var is3DBuildingsOn     = false
    private var is3DTerrainOn       = false

    private val DRIVER_SOURCE_ID    = "chhittoo-driver-source"
    private val DRIVER_LAYER_ID     = "chhittoo-driver-layer"
    private val ROUTE_SOURCE_ID     = "chhittoo-route-source"
    private val ROUTE_LAYER_ID      = "chhittoo-route-layer"
    private val ROUTE_BORDER_ID     = "chhittoo-route-border"
    private val BUILDINGS_LAYER_ID  = "chhittoo-3d-buildings"
    private val DRIVER_ICON         = "chhittoo-driver-icon"
    private val PICKUP_ICON         = "chhittoo-pickup-icon"
    private val DROPOFF_ICON        = "chhittoo-dropoff-icon"
    private val RIDER_ICON          = "chhittoo-rider-icon"

    private val markerSources = mutableMapOf<String, GeoJsonSource>()
    private val markerLayers  = mutableMapOf<String, SymbolLayer>()
    private var currentMode   = "rider"

    // ─────────────────────────────────────────────
    // STYLE: inline JSON vs URL
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

                val cameraPos = CameraPosition.Builder()
                    .target(LatLng(lat, lng))
                    .zoom(if (mode == "driver") 17.0 else zoom)
                    .tilt(if (mode == "driver") 45.0 else 0.0)
                    .bearing(0.0).build()

                val parent = bridge.webView.parent as ViewGroup

                // FIX C: disable touch event splitting so ALL pointers in a
                // multi-finger gesture go to the same child view (MapTouchForwarder).
                // Without this, Android may route the second finger to the WebView,
                // breaking rotation because MapLibre needs both pointers.
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
        // Restore split touch events for the rest of the app
        parent?.isMotionEventSplittingEnabled = true
        parent?.removeView(touchForwarder); parent?.removeView(mapView)
        touchForwarder = null; mapView = null; maplibreMap = null; currentStyle = null
        markerSources.clear(); markerLayers.clear()
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
    // ██  CURRENT LOCATION + COMPASS NAVIGATION  ██
    //
    // enableLocationDot()   — shows blue dot, centers on user, north-up map
    // enableCompassMode()   — map ROTATES with phone compass, like Google Maps nav
    //                         When you turn the phone the map turns with it.
    //                         The blue cone shows which direction you face.
    // disableLocation()     — removes dot and stops following
    // ─────────────────────────────────────────────

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Activates the MapLibre LocationComponent (the blue dot) and optionally
     * sets the tracking/render mode.
     */
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

        // High accuracy — uses GPS chip, not just cell towers / WiFi
        val locationEngineRequest = LocationEngineRequest.Builder(1000L)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setFastestInterval(500L)   // never update faster than 500ms
            .setDisplacement(1f)        // only update if moved at least 1 metre
            .build()

        val activationOptions = LocationComponentActivationOptions
            .builder(activity, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .locationEngineRequest(locationEngineRequest)  // ← attach it here
            .build()

        if (!locationActivated) {
            lc.activateLocationComponent(activationOptions)
            locationActivated = true
        }

        lc.isLocationComponentEnabled = true
        lc.cameraMode  = camMode
        lc.renderMode  = renderMode
    }

    /**
     * Shows the blue location dot.
     * Camera centers on user but map stays north-up.
     */
    fun enableLocationDot() = activateLocation(CameraMode.TRACKING, RenderMode.COMPASS)

    /**
     * Full compass navigation mode — exactly like Google Maps heading-up.
     *
     * CameraMode.TRACKING_COMPASS  → the camera auto-rotates to match the
     *   phone's compass bearing.  When you turn left, the map turns left.
     *
     * RenderMode.COMPASS → the blue dot has a directional cone showing
     *   which way you're facing at all times.
     *
     * This uses Android's sensor fusion (accelerometer + magnetometer),
     * NOT GPS bearing, so it works even when you're standing still.
     */
    fun enableCompassMode() = activateLocation(CameraMode.TRACKING_COMPASS, RenderMode.COMPASS)

    /**
     * GPS navigation mode — map rotates based on GPS movement direction.
     * Best for driving (requires actual movement to update heading).
     * Arrow-shaped indicator instead of compass cone.
     */
    fun enableGpsMode() = activateLocation(CameraMode.TRACKING_GPS, RenderMode.GPS)

    /**
     * Disable location dot and stop all location following.
     */
    fun disableLocation() {
        val lc = maplibreMap?.locationComponent ?: return
        lc.isLocationComponentEnabled = false
        lc.cameraMode = CameraMode.NONE
        lc.renderMode  = RenderMode.NORMAL
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
        val s = 64; val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawCircle(32f, 32f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawCircle(32f, 32f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f })
        return b
    }

    // ─────────────────────────────────────────────
    // DRIVER + ROUTE LAYERS
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

    private fun setupRouteLayer(style: Style) {
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID,
            FeatureCollection.fromFeatures(emptyArray<Feature>())))
        style.addLayerBelow(LineLayer(ROUTE_BORDER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color.WHITE), PropertyFactory.lineWidth(9f),
                PropertyFactory.lineJoin("round"), PropertyFactory.lineCap("round"),
                PropertyFactory.lineOpacity(0.9f))
        }, DRIVER_LAYER_ID)
        style.addLayerBelow(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor("#4A90E2"), PropertyFactory.lineWidth(5f),
                PropertyFactory.lineJoin("round"), PropertyFactory.lineCap("round"))
        }, DRIVER_LAYER_ID)
    }

    // ─────────────────────────────────────────────
    // DRIVER ANIMATION
    // ─────────────────────────────────────────────

    fun updateDriverMarker(lat: Double, lng: Double, bearing: Float, durationMs: Int) {
        val map   = maplibreMap ?: return
        val style = currentStyle ?: return
        val to    = LatLng(lat, lng)
        driverAnimator?.cancel()
        driverAnimator = ValueAnimator.ofObject(LatLngEvaluator(), driverLatLng, to).apply {
            duration = durationMs.toLong(); interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val pos = anim.animatedValue as LatLng
                val ib  = interpolateBearing(lastDriverBearing, bearing, anim.animatedFraction)
                val f   = Feature.fromGeometry(Point.fromLngLat(pos.longitude, pos.latitude))
                f.addNumberProperty("bearing", ib)
                style.getSourceAs<GeoJsonSource>(DRIVER_SOURCE_ID)
                    ?.setGeoJson(FeatureCollection.fromFeature(f))
                driverLatLng = pos
                if (currentMode == "driver") {
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(pos).zoom(17.0).tilt(45.0)
                            .bearing(ib.toDouble()).build()))
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { driverLatLng = to }
            })
        }
        driverAnimator!!.start()
    }

    private fun interpolateBearing(from: Float, to: Float, fraction: Float): Float {
        var diff = to - from
        while (diff > 180) diff -= 360; while (diff < -180) diff += 360
        val r = from + diff * fraction; lastDriverBearing = if (fraction >= 1f) to else r; return r
    }

    // ─────────────────────────────────────────────
    // ██  MARKERS  ██
    //
    // FIX HISTORY:
    //   • Expression.literal(arrayOf(...)) was causing setProperties() to
    //     silently drop ALL properties including iconImage — marker invisible.
    //     Fixed: use arrayOf() directly for textOffset.
    //   • style.getLayerAs(layerId)!! could NPE — removed the !! operator.
    //   • GeoJsonSource constructor now throws instead of silent-return so
    //     errors surface back to JS via call.reject().
    // ─────────────────────────────────────────────

    fun setMarker(id: String, lat: Double, lng: Double, iconName: String, title: String) {
        // Throw so the plugin can reject() the JS call — no more silent failures
        val style = currentStyle
            ?: throw IllegalStateException("Map style not loaded yet")

        val iconId  = mapIconName(iconName)
        val srcId   = "marker-src-$id"
        val layerId = "marker-layer-$id"

        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
        if (title.isNotEmpty()) feature.addStringProperty("title", title)

        // Marker already exists — just move it, don't re-create source/layer
        val existing = markerSources[id]
        if (existing != null) {
            existing.setGeoJson(feature)   // ← pass Feature directly, no FeatureCollection wrapper
            return
        }

        // First placement — create source + layer
        // Use Feature directly to avoid FeatureCollection overload ambiguity
        val source = GeoJsonSource(srcId, feature)
        style.addSource(source)
        markerSources[id] = source

        val layer = SymbolLayer(layerId, srcId).withProperties(
            // Icon
            PropertyFactory.iconImage(iconId),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.iconAnchor("bottom"),
            // Label — placed above the pin
            PropertyFactory.textField(Expression.get("title")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textAnchor("bottom"),
            // textOffset takes a plain Float[] — do NOT wrap in Expression.literal()
            // Wrapping it was the bug: Expression.literal(Array<Float>) creates an
            // invalid expression and causes setProperties() to silently drop ALL
            // properties in the batch, including iconImage.
            PropertyFactory.textOffset(arrayOf(0f, -1.2f)),
            PropertyFactory.textColor("#1a1a1a"),
            PropertyFactory.textHaloColor("#ffffff"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textAllowOverlap(false)
        )

        style.addLayer(layer)
        style.getLayerAs<SymbolLayer>(layerId)?.let { markerLayers[id] = it }
    }

    fun removeMarker(id: String) {
        val style = currentStyle ?: return
        style.removeLayer("marker-layer-$id")
        style.removeSource("marker-src-$id")
        markerSources.remove(id)
        markerLayers.remove(id)
    }

    private fun mapIconName(name: String): String = when (name) {
        "driver"  -> DRIVER_ICON
        "pickup"  -> PICKUP_ICON
        "dropoff" -> DROPOFF_ICON
        "rider"   -> RIDER_ICON
        else      -> PICKUP_ICON
    }

    // ─────────────────────────────────────────────
    // ROUTE
    // ─────────────────────────────────────────────

    fun setRoute(points: List<Pair<Double, Double>>, colorHex: String, widthDp: Float) {
        val style = currentStyle ?: return; if (points.isEmpty()) return
        val collection = FeatureCollection.fromFeature(Feature.fromGeometry(
            LineString.fromLngLats(points.map { Point.fromLngLat(it.first, it.second) })))
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(collection)
        style.getLayerAs<LineLayer>(ROUTE_LAYER_ID)?.setProperties(
            PropertyFactory.lineColor(Color.parseColor(colorHex)), PropertyFactory.lineWidth(widthDp))
        style.getLayerAs<LineLayer>(ROUTE_BORDER_ID)?.setProperties(PropertyFactory.lineWidth(widthDp + 4f))
    }

    fun clearRoute() {
        currentStyle?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
    }

    // ─────────────────────────────────────────────
    // 3D BUILDINGS
    // ─────────────────────────────────────────────

    fun enable3DBuildings() {
        val style = currentStyle ?: return
        if (is3DBuildingsOn) return
        val sourceId = findVectorBuildingSource(style) ?: return
        try {
            style.addLayer(FillExtrusionLayer(BUILDINGS_LAYER_ID, sourceId).apply {
                sourceLayer = "building"
                setFilter(Expression.all(
                    Expression.eq(Expression.get("extrude"), "true"),
                    Expression.has("height")))
                setProperties(
                    PropertyFactory.fillExtrusionColor(
                        Expression.interpolate(Expression.linear(), Expression.zoom(),
                            Expression.stop(15, Expression.literal("#d4cdc6")),
                            Expression.stop(17, Expression.literal("#bfb8b0")),
                            Expression.stop(19, Expression.literal("#aba49c")))),
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
        try { currentStyle?.removeLayer(BUILDINGS_LAYER_ID) } catch (_: Exception) {}
        is3DBuildingsOn = false
        maplibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().tilt(0.0).build()), 800)
    }

    private fun findVectorBuildingSource(style: Style): String? =
        listOf("openmaptiles", "maptiler_planet", "maptiler", "carto", "stadia", "versatiles", "composite")
            .firstOrNull { id -> runCatching { style.getSourceAs<VectorSource>(id) != null }.getOrDefault(false) }

    // ─────────────────────────────────────────────
    // LANDSCAPE / TERRAIN
    // ─────────────────────────────────────────────

    fun enable3DTerrain() {
        if (is3DTerrainOn) return
        is3DTerrainOn = true
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
        is3DBuildingsOn = false; is3DTerrainOn = false
        locationActivated = false
        markerSources.clear(); markerLayers.clear()
        applyStyle(map, url) { style ->
            currentStyle = style
            setupIcons(style)
            setupDriverLayer(style, driverLatLng)
            setupRouteLayer(style)
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