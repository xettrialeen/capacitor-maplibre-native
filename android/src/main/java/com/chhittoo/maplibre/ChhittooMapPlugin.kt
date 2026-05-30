package com.chhittoo.maplibre

import android.graphics.Color
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "ChhittooMap")
class ChhittooMapPlugin : Plugin() {

    var manager: MaplibreManager? = null
        private set

    override fun load() {
        activity.runOnUiThread { bridge.webView.setBackgroundColor(Color.TRANSPARENT) }
        manager = MaplibreManager(bridge, activity)
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    override fun handleOnStart()   { super.handleOnStart();  manager?.onStart()  }
    override fun handleOnResume()  { super.handleOnResume(); manager?.onResume() }
    override fun handleOnPause()   { super.handleOnPause();  manager?.onPause()  }
    override fun handleOnStop()    { super.handleOnStop();   manager?.onStop()   }
    override fun handleOnDestroy() { super.handleOnDestroy(); manager?.destroy() }

    // ── Create / Destroy ─────────────────────────────────────────────────────
    @PluginMethod fun create(call: PluginCall) {
        val d = getDensity()
        manager?.create(
            lat      = call.getDouble("lat")      ?: 26.8065,
            lng      = call.getDouble("lng")      ?: 87.2834,
            zoom     = call.getDouble("zoom")     ?: 15.0,
            styleUrl = call.getString("styleUrl") ?: "https://tiles.openfreemap.org/styles/liberty",
            mode     = call.getString("mode")     ?: "rider",
            xPx      = dpToPx(call.getDouble("x")      ?: 0.0, d),
            yPx      = dpToPx(call.getDouble("y")      ?: 0.0, d),
            widthPx  = dpToPx(call.getDouble("width")  ?: 0.0, d),
            heightPx = dpToPx(call.getDouble("height") ?: 0.0, d),
            onReady  = { call.resolve(JSObject().apply { put("ready", true) }) },
            onError  = { err -> call.reject("Map creation failed: $err") }
        )
    }

    @PluginMethod fun destroy(call: PluginCall) {
        activity.runOnUiThread { manager?.destroy(); call.resolve() }
    }

    @PluginMethod fun updateBounds(call: PluginCall) {
        val d = getDensity()
        activity.runOnUiThread {
            manager?.updateBounds(
                dpToPx(call.getDouble("x") ?: 0.0, d), dpToPx(call.getDouble("y") ?: 0.0, d),
                dpToPx(call.getDouble("width") ?: 0.0, d), dpToPx(call.getDouble("height") ?: 0.0, d))
            call.resolve()
        }
    }

    // ── Mode ─────────────────────────────────────────────────────────────────
    @PluginMethod fun setRiderMode(call: PluginCall)  { activity.runOnUiThread { manager?.setRiderMode();                                call.resolve() } }
    @PluginMethod fun setDriverMode(call: PluginCall) { activity.runOnUiThread { manager?.setDriverMode((call.getDouble("bearing") ?: 0.0).toFloat()); call.resolve() } }

    // ── Driver marker ─────────────────────────────────────────────────────────
    @PluginMethod fun updateDriverMarker(call: PluginCall) {
        val lat = call.getDouble("lat") ?: return
        val lng = call.getDouble("lng") ?: return
        activity.runOnUiThread {
            manager?.updateDriverMarker(lat, lng, (call.getDouble("bearing") ?: 0.0).toFloat(), call.getInt("duration") ?: 1000)
            call.resolve()
        }
    }

    // ── Markers ───────────────────────────────────────────────────────────────
    @PluginMethod fun setMarker(call: PluginCall) {
        val id  = call.getString("id")  ?: return call.reject("id required")
        val lat = call.getDouble("lat") ?: return call.reject("lat required")
        val lng = call.getDouble("lng") ?: return call.reject("lng required")
        val icon  = call.getString("icon")  ?: "pickup"
        val title = call.getString("title") ?: ""
        activity.runOnUiThread {
            try {
                // setMarker() now THROWS on failure instead of silently returning.
                // The throw propagates here and becomes a call.reject() so the
                // JS side sees the actual error in the catch block.
                manager?.setMarker(id, lat, lng, icon, title)
                call.resolve(JSObject().apply { put("id", id) })
            } catch (e: Exception) {
                call.reject("setMarker failed: ${e.message}")
            }
        }
    }

    @PluginMethod fun removeMarker(call: PluginCall) {
        val id = call.getString("id") ?: return call.reject("id required")
        activity.runOnUiThread { manager?.removeMarker(id); call.resolve() }
    }

    // ── Route ─────────────────────────────────────────────────────────────────
    @PluginMethod fun setRoute(call: PluginCall) {
        val coords = call.getArray("coordinates") ?: return call.reject("coordinates required")
        val color  = call.getString("color") ?: "#4A90E2"
        val width  = call.getInt("width") ?: 5
        val points = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until coords.length()) {
            val pair = coords.getJSONArray(i)
            points.add(Pair(pair.getDouble(0), pair.getDouble(1)))
        }
        activity.runOnUiThread { manager?.setRoute(points, color, width.toFloat()); call.resolve() }
    }

    @PluginMethod fun clearRoute(call: PluginCall) {
        activity.runOnUiThread { manager?.clearRoute(); call.resolve() }
    }

    // ── Camera ────────────────────────────────────────────────────────────────
    @PluginMethod fun animateCamera(call: PluginCall) {
        val lat = call.getDouble("lat") ?: return call.reject("lat required")
        val lng = call.getDouble("lng") ?: return call.reject("lng required")
        activity.runOnUiThread {
            manager?.animateCamera(lat, lng, call.getDouble("zoom"), call.getDouble("pitch"),
                call.getDouble("bearing"), call.getInt("duration") ?: 1200)
            call.resolve()
        }
    }

    @PluginMethod fun fitBounds(call: PluginCall) {
        val coords  = call.getArray("coordinates") ?: return call.reject("coordinates required")
        val padding = call.getInt("padding") ?: 80
        val points  = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until coords.length()) {
            val o = coords.getJSONObject(i)
            points.add(Pair(o.getDouble("lat"), o.getDouble("lng")))
        }
        activity.runOnUiThread {
            manager?.fitBounds(points, (padding * getDensity()).toInt())
            call.resolve()
        }
    }

    // ── Location + Compass Navigation ─────────────────────────────────────────

    /**
     * Shows the blue location dot. Camera centers on user. Map stays north-up.
     * Requires location permission to be granted first from JS.
     */
    @PluginMethod fun enableLocationDot(call: PluginCall) {
        activity.runOnUiThread { manager?.enableLocationDot(); call.resolve() }
    }

    /**
     * Compass navigation mode — map rotates with device compass.
     * Exactly like Google Maps heading-up: turn your phone left and the map turns left.
     * Uses Android sensor fusion (accelerometer + magnetometer), works standing still.
     */
    @PluginMethod fun enableCompassMode(call: PluginCall) {
        activity.runOnUiThread { manager?.enableCompassMode(); call.resolve() }
    }

    /**
     * GPS navigation mode — map rotates based on GPS movement direction.
     * Best for driving. Requires actual movement to update heading.
     */
    @PluginMethod fun enableGpsMode(call: PluginCall) {
        activity.runOnUiThread { manager?.enableGpsMode(); call.resolve() }
    }

    /**
     * Disables location dot and stops all location following.
     */
    @PluginMethod fun disableLocation(call: PluginCall) {
        activity.runOnUiThread { manager?.disableLocation(); call.resolve() }
    }

    // ── 3D ────────────────────────────────────────────────────────────────────
    @PluginMethod fun enable3DBuildings(call: PluginCall)  { activity.runOnUiThread { manager?.enable3DBuildings(); call.resolve() } }
    @PluginMethod fun disable3DBuildings(call: PluginCall) { activity.runOnUiThread { manager?.disable3DBuildings(); call.resolve() } }
    @PluginMethod fun enable3DTerrain(call: PluginCall)    { activity.runOnUiThread { manager?.enable3DTerrain(); call.resolve() } }
    @PluginMethod fun disable3DTerrain(call: PluginCall)   { activity.runOnUiThread { manager?.disable3DTerrain(); call.resolve() } }

    @PluginMethod fun startCityTour(call: PluginCall) {
        activity.runOnUiThread { manager?.startCityTour(call.getInt("duration") ?: 20_000); call.resolve() }
    }
    @PluginMethod fun stopCityTour(call: PluginCall) {
        activity.runOnUiThread { manager?.stopCityTour(); call.resolve() }
    }

    // ── Style ─────────────────────────────────────────────────────────────────
    @PluginMethod fun setStyleUrl(call: PluginCall) {
        val url = call.getString("url") ?: return call.reject("url required")
        activity.runOnUiThread { manager?.setStyleUrl(url); call.resolve() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun getDensity(): Float = context.resources.displayMetrics.density
    private fun dpToPx(dp: Double, density: Float): Int = (dp * density).toInt()
}