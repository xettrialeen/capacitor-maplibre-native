# Changelog

All notable changes to this project are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project uses [Semantic Versioning](https://semver.org/).

---

## [1.0.0] — 2026-05-30

Initial public release.

### Added

**Core map**
- Native MapLibre Android SDK 11.11.0 integration via Capacitor 7 plugin bridge
- `create()` — renders native `MapView` behind the Capacitor `WebView`
- `destroy()` — removes map and frees all resources
- `updateBounds()` — repositions map on layout change / orientation change

**Map modes**
- `setRiderMode()` — overhead view, north-up, pitch 0°
- `setDriverMode()` — 2.5D navigation, pitch 45°, zoom 17, camera auto-follows driver

**Markers**
- `setMarker()` — add or move a named marker (SymbolLayer per marker, full GPU rendering)
- `removeMarker()` — remove a named marker
- `updateDriverMarker()` — animate driver icon with smooth position + bearing interpolation

**Route**
- `setRoute()` — draw route polyline with white border
- `clearRoute()` — remove route

**Camera**
- `animateCamera()` — smooth camera animation to any position / zoom / pitch / bearing
- `fitBounds()` — auto-zoom to show all given coordinates

**Location & Navigation**
- `enableLocationDot()` — blue GPS dot, camera centers on user
- `enableCompassMode()` — map rotates with device compass (sensor fusion, works standing still)
- `enableGpsMode()` — map rotates based on GPS movement direction
- `disableLocation()` — hide location indicator

**3D**
- `enable3DBuildings()` — building extrusion via `FillExtrusionLayer`
- `disable3DBuildings()`
- `enable3DTerrain()` — 60° camera tilt for landscape depth effect
- `disable3DTerrain()`
- `startCityTour()` — 360° orbital camera animation
- `stopCityTour()`

**Style**
- `setStyleUrl()` — runtime style switching, preserves markers and route
- Inline raster style JSON support (ESRI satellite, Google Maps, custom raster sources)

**Touch handling**
- `MapTouchForwarder` — transparent overlay routes all gestures to native `MapView`
- Multi-touch split disabled (`isMotionEventSplittingEnabled = false`) for reliable two-finger rotation
- `MotionEvent.obtain()` used for event forwarding to prevent recycling issues
- `requestDisallowInterceptTouchEvent` prevents parent gesture interference mid-rotation

**TypeScript**
- Full TypeScript definitions (`src/index.d.ts`)
- All method options fully typed