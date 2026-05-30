// capacitor-maplibre-native type definitions

/**
 * GeoJSON coordinate pair in longitude-first order (standard GeoJSON format).
 * Example: [87.2834, 26.8065]  →  [lng, lat]
 */
export type LngLat = [number, number];

/**
 * Geographic coordinate in latitude-first order.
 */
export interface LatLng {
  lat: number;
  lng: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Create / Destroy
// ─────────────────────────────────────────────────────────────────────────────

export interface CreateOptions {
  /**
   * Initial map center latitude.
   * @default 0
   */
  lat: number;

  /**
   * Initial map center longitude.
   * @default 0
   */
  lng: number;

  /**
   * Initial zoom level (0 = world, 22 = max detail).
   * @default 15
   */
  zoom?: number;

  /**
   * MapLibre style URL or inline style JSON string.
   * Any valid MapLibre GL style source is accepted — vector tile URLs,
   * raster tile URLs wrapped in a style JSON object, or a full style JSON string.
   * @default "https://tiles.openfreemap.org/styles/liberty"
   */
  styleUrl: string;

  /**
   * Initial map mode.
   * - `"rider"` — overhead view, pitch 0°, north-up, free gestures
   * - `"driver"` — 2.5D navigation, pitch 45°, zoom 17, camera follows driver position
   * @default "rider"
   */
  mode?: 'rider' | 'driver';

  /**
   * Bounding rect of the element behind which the native map should render.
   * Obtain these from `element.getBoundingClientRect()` in your JavaScript.
   * All values are in CSS pixels.
   */
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface BoundsOptions {
  /** CSS pixel values from `element.getBoundingClientRect()`. */
  x: number;
  y: number;
  width: number;
  height: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Markers
// ─────────────────────────────────────────────────────────────────────────────

export interface MarkerOptions {
  /**
   * Unique marker identifier. Calling `setMarker` again with the same `id`
   * moves the existing marker to the new position instead of creating a duplicate.
   */
  id: string;

  /** Marker latitude. */
  lat: number;

  /** Marker longitude. */
  lng: number;

  /**
   * Icon name. Must match one of the built-in icon identifiers or a
   * drawable resource name in your app's `res/drawable/` folder.
   *
   * Built-in values:
   * - `"pickup"`  — green location pin  (`ic_pickup.xml`)
   * - `"dropoff"` — red location pin    (`ic_dropoff.xml`)
   * - `"rider"`   — purple person dot   (`ic_rider.xml`)
   * - `"driver"`  — blue car icon       (`ic_driver_car.xml`)
   *
   * @default "pickup"
   */
  icon?: 'pickup' | 'dropoff' | 'rider' | 'driver' | string;

  /**
   * Optional text label rendered above the marker.
   * Pass an empty string to show no label.
   */
  title?: string;
}

export interface RemoveMarkerOptions {
  /** ID of the marker to remove. */
  id: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Driver
// ─────────────────────────────────────────────────────────────────────────────

export interface DriverMarkerOptions {
  /** New latitude for the driver position. */
  lat: number;

  /** New longitude for the driver position. */
  lng: number;

  /**
   * GPS heading in degrees (0–360, 0 = north).
   * The driver car icon rotates to face this direction.
   * @default 0
   */
  bearing?: number;

  /**
   * Animation duration in milliseconds.
   * Match this to your GPS update interval for smooth movement.
   * @default 1000
   */
  duration?: number;
}

export interface DriverModeOptions {
  /**
   * Initial camera bearing in degrees.
   * @default 0
   */
  bearing?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Route
// ─────────────────────────────────────────────────────────────────────────────

export interface RouteOptions {
  /**
   * Array of [longitude, latitude] coordinate pairs (GeoJSON order).
   * Matches the output format of routing APIs like OSRM and Valhalla.
   *
   * @example
   * [[87.2790, 26.8120], [87.2834, 26.8065], [87.2950, 26.7990]]
   */
  coordinates: LngLat[];

  /**
   * Route line color as a CSS hex string.
   * @default "#4A90E2"
   */
  color?: string;

  /**
   * Route line width in density-independent pixels.
   * @default 5
   */
  width?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Camera
// ─────────────────────────────────────────────────────────────────────────────

export interface CameraOptions {
  /** Target latitude. */
  lat: number;

  /** Target longitude. */
  lng: number;

  /**
   * Target zoom level.
   * Omit to keep the current zoom.
   */
  zoom?: number;

  /**
   * Camera pitch (tilt) in degrees. 0 = flat overhead, 60 = steep perspective.
   * Omit to keep the current pitch.
   */
  pitch?: number;

  /**
   * Camera bearing (rotation) in degrees. 0 = north-up.
   * Omit to keep the current bearing.
   */
  bearing?: number;

  /**
   * Animation duration in milliseconds.
   * @default 1200
   */
  duration?: number;
}

export interface FitBoundsOptions {
  /**
   * Array of `{ lat, lng }` points to include in the visible area.
   * The camera zooms and pans to show all of them.
   */
  coordinates: LatLng[];

  /**
   * Padding around the bounds in density-independent pixels.
   * @default 80
   */
  padding?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// 3D
// ─────────────────────────────────────────────────────────────────────────────

export interface CityTourOptions {
  /**
   * Total duration of one 360° orbit in milliseconds.
   * The animation loops indefinitely until `stopCityTour()` is called.
   * @default 20000
   */
  duration?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Style
// ─────────────────────────────────────────────────────────────────────────────

export interface StyleUrlOptions {
  /**
   * MapLibre style URL or inline style JSON.
   *
   * Vector tile style URL:
   * `"https://tiles.openfreemap.org/styles/liberty"`
   *
   * Inline raster tile style (Google Maps, ESRI):
   * Pass the result of `JSON.stringify({ version: 8, sources: { ... }, layers: [...] })`
   */
  url: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Plugin interface
// ─────────────────────────────────────────────────────────────────────────────

export interface ChhittooMapPlugin {
  // ── Lifecycle ─────────────────────────────────────────────────────────────

  /**
   * Creates and displays the native MapView.
   *
   * The map renders behind the Capacitor WebView. Pass the bounding rect of a
   * placeholder `<div>` element so the native view aligns with your layout.
   * The WebView's background must be transparent over that element.
   *
   * @example
   * const el = document.getElementById('map-container');
   * const rect = el.getBoundingClientRect();
   * await ChhittooMap.create({
   *   lat: 26.8065, lng: 87.2834, zoom: 14,
   *   styleUrl: 'https://tiles.openfreemap.org/styles/liberty',
   *   x: Math.round(rect.x), y: Math.round(rect.y),
   *   width: Math.round(rect.width), height: Math.round(rect.height),
   * });
   */
  create(options: CreateOptions): Promise<void>;

  /**
   * Destroys the native MapView and removes it from the view hierarchy.
   * Cancels all running animations and clears internal state.
   */
  destroy(): Promise<void>;

  /**
   * Updates the position and size of the native MapView.
   * Call this when the placeholder element moves or resizes —
   * for example on orientation change, scroll, or layout reflow.
   *
   * @example
   * window.addEventListener('resize', async () => {
   *   const rect = el.getBoundingClientRect();
   *   await ChhittooMap.updateBounds({
   *     x: Math.round(rect.x), y: Math.round(rect.y),
   *     width: Math.round(rect.width), height: Math.round(rect.height),
   *   });
   * });
   */
  updateBounds(options: BoundsOptions): Promise<void>;

  // ── Map mode ──────────────────────────────────────────────────────────────

  /**
   * Switches to overhead rider mode.
   * - Camera pitch resets to 0° (flat, north-up)
   * - Zoom resets to 15
   * - All gestures re-enabled
   *
   * Use this for the passenger's view in a ride-sharing app.
   */
  setRiderMode(): Promise<void>;

  /**
   * Switches to 2.5D driver navigation mode.
   * - Camera tilts to 45°, zooms to 17
   * - Camera automatically follows and rotates with the driver marker
   *   when `updateDriverMarker` is called
   *
   * @param options.bearing  Initial compass heading in degrees.
   */
  setDriverMode(options: DriverModeOptions): Promise<void>;

  // ── Driver marker ─────────────────────────────────────────────────────────

  /**
   * Animates the driver car icon to a new GPS position.
   *
   * The icon glides smoothly from its current position to the new one,
   * and rotates to face the given bearing. In driver mode the camera
   * follows automatically.
   *
   * Call this on every GPS update (typically every 1–3 seconds).
   *
   * @example
   * navigator.geolocation.watchPosition(pos => {
   *   ChhittooMap.updateDriverMarker({
   *     lat: pos.coords.latitude,
   *     lng: pos.coords.longitude,
   *     bearing: pos.coords.heading ?? 0,
   *     duration: 1000,
   *   });
   * });
   */
  updateDriverMarker(options: DriverMarkerOptions): Promise<void>;

  // ── Markers ───────────────────────────────────────────────────────────────

  /**
   * Adds a named marker to the map, or moves it if it already exists.
   *
   * Internally uses a MapLibre `SymbolLayer` per marker, meaning markers
   * render at full GPU speed and respect map rotation/tilt.
   *
   * @example
   * await ChhittooMap.setMarker({
   *   id: 'pickup',
   *   lat: 26.812, lng: 87.279,
   *   icon: 'pickup',
   *   title: 'Your pickup',
   * });
   */
  setMarker(options: MarkerOptions): Promise<void>;

  /**
   * Removes a previously added marker by its ID.
   * Does nothing if the marker does not exist.
   */
  removeMarker(options: RemoveMarkerOptions): Promise<void>;

  // ── Route ─────────────────────────────────────────────────────────────────

  /**
   * Draws a route polyline on the map.
   *
   * Coordinates use GeoJSON order: `[longitude, latitude]`.
   * This matches the output of OSRM, Valhalla, and most routing APIs directly.
   *
   * A white border is rendered behind the route line automatically for contrast.
   *
   * @example
   * await ChhittooMap.setRoute({
   *   coordinates: [[87.279, 26.812], [87.283, 26.806], [87.295, 26.799]],
   *   color: '#1a1a1a',
   *   width: 5,
   * });
   */
  setRoute(options: RouteOptions): Promise<void>;

  /**
   * Removes the current route polyline from the map.
   */
  clearRoute(): Promise<void>;

  // ── Camera ────────────────────────────────────────────────────────────────

  /**
   * Smoothly animates the camera to a new position.
   *
   * Any combination of lat/lng, zoom, pitch, and bearing can be specified.
   * Omitted properties remain at their current values.
   *
   * @example
   * // Fly to a location at zoom 16, tilted 45°
   * await ChhittooMap.animateCamera({
   *   lat: 26.8065, lng: 87.2834,
   *   zoom: 16, pitch: 45,
   *   duration: 1500,
   * });
   */
  animateCamera(options: CameraOptions): Promise<void>;

  /**
   * Zooms and pans the camera to fit all the given coordinates on screen.
   *
   * Useful for showing the full route — pass the pickup, dropoff, and
   * current driver position together.
   *
   * @example
   * await ChhittooMap.fitBounds({
   *   coordinates: [pickup, dropoff, driverPosition],
   *   padding: 80,
   * });
   */
  fitBounds(options: FitBoundsOptions): Promise<void>;

  // ── Location & Navigation ─────────────────────────────────────────────────

  /**
   * Shows the user's current GPS position as a blue dot on the map.
   * The camera centers on the user. The map stays north-up.
   *
   * Requires `ACCESS_FINE_LOCATION` permission.
   * Use `@capacitor/geolocation` to request permission first.
   *
   * @example
   * import { Geolocation } from '@capacitor/geolocation';
   * await Geolocation.requestPermissions();
   * await ChhittooMap.enableLocationDot();
   */
  enableLocationDot(): Promise<void>;

  /**
   * Enables compass navigation mode.
   *
   * The map rotates in real time to match the device's compass heading —
   * turn the phone left and the map turns left. A direction cone on the
   * blue dot shows which way you're facing.
   *
   * Uses Android sensor fusion (accelerometer + magnetometer). Works
   * standing still, unlike GPS-based heading.
   *
   * Requires `ACCESS_FINE_LOCATION` permission.
   */
  enableCompassMode(): Promise<void>;

  /**
   * Enables GPS navigation mode.
   *
   * The map rotates to match the direction of travel based on GPS bearing.
   * Best for driving. Requires actual movement to update heading.
   * The indicator uses an arrow shape instead of a direction cone.
   *
   * Requires `ACCESS_FINE_LOCATION` permission.
   */
  enableGpsMode(): Promise<void>;

  /**
   * Hides the location dot and stops all location-based camera following.
   */
  disableLocation(): Promise<void>;

  // ── 3D ────────────────────────────────────────────────────────────────────

  /**
   * Extrudes buildings to their real-world heights using vector tile data.
   *
   * Buildings "grow" from the ground as the user zooms in (zoom 14–15).
   * Works with any OpenMapTiles-schema style: OpenFreeMap, CARTO,
   * Stadia, MapTiler. Has no effect on raster styles (ESRI, Google Maps)
   * because those contain no vector building data.
   *
   * The camera automatically tilts to 55° when called.
   */
  enable3DBuildings(): Promise<void>;

  /**
   * Removes 3D building extrusion and resets camera pitch to 0°.
   */
  disable3DBuildings(): Promise<void>;

  /**
   * Tilts the camera to 60° to give a landscape/terrain depth effect.
   *
   * Note: True GPU terrain extrusion (like Google Earth) requires
   * MapLibre Android SDK 12+ or a terrain-embedded style JSON.
   * This method provides the best approximation available in SDK 11.x.
   */
  enable3DTerrain(): Promise<void>;

  /**
   * Resets camera pitch and zoom to flat overhead view.
   */
  disable3DTerrain(): Promise<void>;

  /**
   * Starts a cinematic 360° orbital camera animation around the current
   * map center at 55° pitch. Loops continuously until `stopCityTour()`.
   *
   * Automatically enables 3D buildings first for the best visual effect.
   * Zoom in to 16+ and move to a city center before calling.
   */
  startCityTour(options?: CityTourOptions): Promise<void>;

  /**
   * Stops the city tour orbital animation.
   */
  stopCityTour(): Promise<void>;

  // ── Style ─────────────────────────────────────────────────────────────────

  /**
   * Changes the map's tile style at runtime without recreating the map.
   *
   * All existing markers and the route are preserved. The driver layer
   * and route layer are re-added to the new style automatically.
   *
   * @example
   * // Switch to dark mode
   * await ChhittooMap.setStyleUrl({
   *   url: 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json',
   * });
   */
  setStyleUrl(options: StyleUrlOptions): Promise<void>;
}