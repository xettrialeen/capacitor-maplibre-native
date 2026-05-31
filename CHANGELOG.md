# Changelog

---

## [1.0.2] — 2026-05-31

### New features

**Route progress — traveled vs remaining split**
`setRoute` now renders two line layers. As the driver moves, the route behind them turns gray (traveled) and the route ahead stays the brand color (remaining). Updates automatically on every `updateDriverMarker` call with no extra code needed.

**Route progress data returned from `updateDriverMarker`**
Every call now returns a progress object:
```ts
{
  distanceRemaining: number  // km left
  distanceTraveled:  number  // km covered
  totalDistance:     number  // full route length
  percentComplete:   number  // 0–100
  etaMinutes:        number  // at 30 km/h average
}
```

**Auto route preview on pickup + dropoff**
Once both a pickup and a dropoff marker are placed, show the route and distance immediately — exactly like Uber's booking screen before a ride is confirmed. No driver animation required.

**Compass navigation mode** (`enableCompassMode`)
The map rotates in real time to match the device compass heading. Uses Android sensor fusion (accelerometer + magnetometer) — works standing still, not just when moving. The blue dot shows a direction cone.

**GPS navigation mode** (`enableGpsMode`)
Map rotates based on GPS movement direction. Best for driving. Arrow indicator instead of compass cone.

**Location dot** (`enableLocationDot`)
Shows current GPS position as a blue dot, centers camera on user, map stays north-up.

**High accuracy location engine**
All location modes use `PRIORITY_HIGH_ACCURACY`, 500ms fastest interval, 1m displacement threshold. GPS chip is used instead of cell towers or WiFi.

**3D buildings** (`enable3DBuildings` / `disable3DBuildings`)
Building extrusion from vector tile height data using `FillExtrusionLayer`. Buildings grow from ground level as you zoom past 14. Works with any OpenMapTiles-schema style. Camera auto-tilts to 55° on enable.

**Landscape view** (`enable3DTerrain` / `disable3DTerrain`)
Tilts the camera to 60° for terrain depth. Best with ESRI Hybrid or Google Terrain tiles.

**City tour** (`startCityTour` / `stopCityTour`)
360° orbital camera animation at 55° pitch. Loops until stopped. Auto-enables 3D buildings.

**GPS navigation arrow icon**
New `ic_driver_car.xml` — a clean hollow chevron arrow pointing in the direction of travel, like Google Maps and Uber navigation mode. Built entirely from `<path>` elements, no unsupported attributes.

**Smooth animated progress bar**
Progress fill animates with `requestAnimationFrame` and cubic ease-out instead of CSS transitions. `_progressFrom` is updated on every frame so interrupted animations continue from the correct position rather than snapping back to 0%.

---

### Bug fixes

**Markers only showing on CARTO tiles**
Root cause: OpenFreeMap, Protomaps, ESRI, and Google styles load their sprite atlas asynchronously after `OnStyleLoaded` fires. When the sprite finishes loading, MapLibre rebuilds its image atlas and silently clears any custom images registered earlier. CARTO's CDN is fast enough that this race didn't occur there.
Fix: `loadIcon()` is now called inside `setMarker()` immediately before the `SymbolLayer` is created, guaranteeing the image exists at the exact moment the layer needs it regardless of when the sprite loaded.

**Icon suppressed by text rendering failure**
Root cause: a single `SymbolLayer` with both `iconImage` and `textField` properties fails silently on styles without a `glyphs` URL. MapLibre suppresses the entire symbol — including the icon — if any property in the layer errors.
Fix: icon and text are now separate `SymbolLayer`s. A glyph failure on the text layer has no effect on the icon layer. Raster inline styles also now include a `glyphs` URL.

**Two-finger rotation unreliable**
Three fixes applied together:
1. `MotionEvent.obtain(event)` — Android recycles touch events after `onTouchEvent` returns. Forwarding the original object means MapLibre's gesture detector sometimes reads a zeroed-out event. A copy prevents this.
2. `requestDisallowInterceptTouchEvent(true)` — prevents the parent `ViewGroup` from intercepting and cancelling a gesture mid-rotation.
3. `parent.isMotionEventSplittingEnabled = false` — the main cause. Android splits multi-touch events between child views by default. First finger went to `MapTouchForwarder`, second finger went to the `WebView`. MapLibre never saw both pointers so rotation couldn't start.

**App crash on tile switch during driver animation**
Root cause: `ValueAnimator` lambdas close over the `style` and `map` variables captured at animation-start time. When `setStyleUrl` loads a new style, MapLibre destroys the old `Style` object internally. The still-running animation then called `getSourceAs()` on the dead object and crashed.
Fix: animation listeners now use `currentStyle` and `maplibreMap` (live class properties) instead of closed-over snapshots. Added `?: return@addUpdateListener` guards so the listener exits safely during a style transition. Both animations are also cancelled before the style swap begins.

**Route and markers disappear after tile switch**
Root cause: `setStyleUrl` cleared `routePoints`, `markerSources`, and `markerLayers` but never re-drew them. The JS side kept its state (`routeActive`, progress values, marker flags) but the map was blank.
Fix: a new `MarkerData` map and `routeColor`/`routeWidth` fields persist across style switches. Only the MapLibre layer/source references are cleared on swap. After the new style's `OnStyleLoaded` fires, the route and all markers are automatically re-drawn in the correct order (icons first, then layers).

**Progress bar resetting to 0% on every update**
Root cause: `_progressFrom` was only updated when the `requestAnimationFrame` loop completed fully (`else _progressFrom = to`). When a new GPS update arrived and `cancelAnimationFrame()` was called mid-animation, `_progressFrom` was still 0, so the next animation started from 0 instead of the current bar position.
Fix: `_progressFrom = current` is set on every frame so interrupting the animation always leaves the correct starting value for the next one.

**Driver animation step-like appearance**
Root cause: 6 demo waypoints ~250m apart animated in 1.1s each look like teleportation, not movement. The `ValueAnimator` was smooth but the steps were too large.
Fix: `buildSmoothPath()` generates 3 intermediate positions per segment (18 total) using linear interpolation. Combined with a 1000ms interval matching the animation duration exactly, the handoff between animations is seamless and the movement looks like real GPS tracking.

**`android:rx` build error on vector drawable**
Root cause: `<rect android:rx="...">` inside a `<vector>` is SVG syntax. Android's `<vector>` drawable format does not support `<rect>` elements at all — AAPT rejects them.
Fix: all rounded rectangles (wheels, headlights) replaced with `<path>` elements using quadratic bezier curves (`Q`) to achieve the same visual result.

---

## [1.0.0] — 2026-05-30

Initial release.

- Native MapLibre Android SDK 11.11.0 integration via Capacitor 7
- `MapTouchForwarder` for native gesture pass-through
- `create`, `destroy`, `updateBounds`
- `setRiderMode`, `setDriverMode`
- `updateDriverMarker` with `ValueAnimator` bearing interpolation
- `setMarker`, `removeMarker`
- `setRoute`, `clearRoute` with white border layer
- `animateCamera`, `fitBounds`
- `setStyleUrl` with inline raster JSON support
- TypeScript definitions
- Full README with React, Svelte, and Vanilla JS examples