# capacitor-maplibre-native

Native [MapLibre GL](https://maplibre.org/maplibre-native/android/api/) map plugin for Capacitor 7.

Renders a real **native Android `MapView`** behind the Capacitor WebView — GPU-accelerated OpenGL rendering, not a WebView map. Built for ride-sharing and navigation apps.

| | |
|---|---|
| Platform | Android (API 23+) |
| Capacitor | 7.x |
| MapLibre Android SDK | 11.11.0 |
| iOS | Not implemented |

---

## How it works

The plugin places a native `MapView` behind the Capacitor `WebView`. The `WebView` background is made transparent over your map element, so the native rendering shows through. Your HTML UI sits on top.

Touches in the map area are intercepted by a transparent native `View` that forwards them to the `MapView` — giving you full native gesture performance for pan, pinch-zoom, two-finger rotation, and tilt.

```
Activity window
├── MapView              ← native OpenGL, renders tiles at 60fps
├── WebView              ← transparent over map, renders your HTML UI
└── MapTouchForwarder    ← invisible, routes touches to MapView
```

---

## Installation

```bash
npm install capacitor-maplibre-native
npx cap sync android
```

That's it. Capacitor auto-discovers the plugin on sync. No manual registration needed.

---

## Android setup

### 1. Location permissions

If you plan to use `enableLocationDot()`, `enableCompassMode()`, or `enableGpsMode()`, add these to `android/app/src/main/AndroidManifest.xml` inside `<manifest>`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### 2. Transparent WebView background

For the native map to show through, your app's HTML must have a transparent background. Add this to your CSS:

```css
html, body {
  background: transparent !important;
}
```

The plugin sets the WebView background color to transparent automatically when it loads. But if your body or html element has a non-transparent background, it will cover the map.

### 3. Map placeholder element

Add a `<div>` where the map should appear. It must have a transparent or no background:

```html
<div id="map"
     style="position: fixed; top: 0; left: 0; right: 0; height: 60vh; background: transparent;">
</div>
```

The native map renders behind this element, aligned to its position on screen.

---

## Quick start

```js
import { ChhittooMap } from 'capacitor-maplibre-native';

async function showMap() {
  const el   = document.getElementById('map');
  const rect = el.getBoundingClientRect();

  await ChhittooMap.create({
    lat:      26.8065,
    lng:      87.2834,
    zoom:     14,
    styleUrl: 'https://tiles.openfreemap.org/styles/liberty',
    x:        Math.round(rect.x),
    y:        Math.round(rect.y),
    width:    Math.round(rect.width),
    height:   Math.round(rect.height),
  });

  // Place a pickup marker
  await ChhittooMap.setMarker({
    id:    'pickup',
    lat:   26.812,
    lng:   87.279,
    icon:  'pickup',
    title: 'Pickup point',
  });

  // Draw a route from pickup to dropoff
  await ChhittooMap.setRoute({
    coordinates: [
      [87.279, 26.812],
      [87.283, 26.806],
      [87.295, 26.799],
    ],
    color: '#1a1a1a',
    width: 5,
  });
}
```

---

## Map styles

The `styleUrl` option accepts any MapLibre GL style source.

### Free styles (no API key)

| Provider | Style | URL |
|---|---|---|
| OpenFreeMap | Liberty | `https://tiles.openfreemap.org/styles/liberty` |
| OpenFreeMap | Bright | `https://tiles.openfreemap.org/styles/bright` |
| CARTO | Positron | `https://basemaps.cartocdn.com/gl/positron-gl-style/style.json` |
| CARTO | Voyager | `https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json` |
| CARTO | Dark Matter | `https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json` |
| Protomaps | Light | `https://api.protomaps.com/styles/v4/light/en.json` |
| Protomaps | Dark | `https://api.protomaps.com/styles/v4/dark/en.json` |

### Satellite (ESRI, free)

ESRI World Imagery is completely free with no API key. Pass an inline raster style JSON:

```js
const esriSatellite = JSON.stringify({
  version: 8,
  sources: {
    tiles: {
      type: 'raster',
      tiles: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'],
      tileSize: 256,
      attribution: '© Esri, Maxar, Earthstar Geographics',
      maxzoom: 19,
    },
  },
  layers: [{ id: 'raster', type: 'raster', source: 'tiles' }],
});

await ChhittooMap.setStyleUrl({ url: esriSatellite });
```

### Paid providers (Stadia Maps, MapTiler)

These require an API key but offer free developer tiers:

```js
// Stadia Maps — alidade_smooth is ideal for ride-sharing apps
// (muted background makes your markers and routes stand out)
const url = `https://tiles.stadiamaps.com/styles/alidade_smooth.json?api_key=${YOUR_KEY}`;

// MapTiler
const url = `https://api.maptiler.com/maps/streets-v2/style.json?key=${YOUR_KEY}`;
```

---

## Custom marker icons

By default the plugin uses coloured circle fallbacks. To use custom icons:

1. Add your PNG or vector drawable XML files to:
   `android/src/main/res/drawable/`

2. Name them exactly:
   - `ic_pickup.xml` (or `.png`) — shown for `icon: "pickup"`
   - `ic_dropoff.xml` — shown for `icon: "dropoff"`
   - `ic_driver_car.xml` — shown for `icon: "driver"`
   - `ic_rider.xml` — shown for `icon: "rider"`

3. For custom icon names, pass any string to `icon` and add the corresponding
   `ic_<name>.xml` drawable to your `res/drawable/` folder.

---

## API reference

### `create(options)`

Initializes and displays the native map. Must be called before any other method.

| Option | Type | Required | Description |
|---|---|---|---|
| `lat` | `number` | Yes | Initial center latitude |
| `lng` | `number` | Yes | Initial center longitude |
| `zoom` | `number` | No | Initial zoom level (0–22). Default `15` |
| `styleUrl` | `string` | Yes | MapLibre style URL or inline style JSON |
| `mode` | `"rider"` \| `"driver"` | No | Initial mode. Default `"rider"` |
| `x` | `number` | Yes | Placeholder element left offset (CSS px) |
| `y` | `number` | Yes | Placeholder element top offset (CSS px) |
| `width` | `number` | Yes | Placeholder element width (CSS px) |
| `height` | `number` | Yes | Placeholder element height (CSS px) |

---

### `destroy()`

Removes the native map from the screen and frees all resources. Cancels all animations.

---

### `updateBounds(options)`

Repositions and resizes the native map to match a new element bounding rect.
Call on orientation change, scroll events, or any layout change.

| Option | Type | Description |
|---|---|---|
| `x` | `number` | New left offset (CSS px) |
| `y` | `number` | New top offset (CSS px) |
| `width` | `number` | New width (CSS px) |
| `height` | `number` | New height (CSS px) |

---

### `setRiderMode()`

Switches to the passenger view: pitch 0°, north-up, zoom 15, all gestures enabled.

---

### `setDriverMode(options)`

Switches to 2.5D navigation: pitch 45°, zoom 17. Camera follows and rotates with
`updateDriverMarker` calls automatically.

| Option | Type | Description |
|---|---|---|
| `bearing` | `number` | Initial heading in degrees. Default `0` |

---

### `updateDriverMarker(options)`

Animates the driver icon to a new GPS position with smooth interpolation.
Call this on every GPS location update.

| Option | Type | Required | Description |
|---|---|---|---|
| `lat` | `number` | Yes | New latitude |
| `lng` | `number` | Yes | New longitude |
| `bearing` | `number` | No | Heading in degrees (rotates the car icon). Default `0` |
| `duration` | `number` | No | Animation duration in ms. Default `1000` |

---

### `setMarker(options)`

Adds a named marker or moves an existing one (by `id`).

| Option | Type | Required | Description |
|---|---|---|---|
| `id` | `string` | Yes | Unique identifier. Reuse to move the marker |
| `lat` | `number` | Yes | Latitude |
| `lng` | `number` | Yes | Longitude |
| `icon` | `string` | No | Icon name. Default `"pickup"` |
| `title` | `string` | No | Label text above the marker |

---

### `removeMarker(options)`

| Option | Type | Description |
|---|---|---|
| `id` | `string` | ID of the marker to remove |

---

### `setRoute(options)`

Draws a route polyline. Coordinates use GeoJSON order: `[lng, lat]`.

| Option | Type | Required | Description |
|---|---|---|---|
| `coordinates` | `[number, number][]` | Yes | `[lng, lat]` pairs |
| `color` | `string` | No | Hex color. Default `"#4A90E2"` |
| `width` | `number` | No | Line width in dp. Default `5` |

---

### `clearRoute()`

Removes the route polyline from the map.

---

### `animateCamera(options)`

Smoothly animates the camera. All fields except `lat` and `lng` are optional.

| Option | Type | Description |
|---|---|---|
| `lat` | `number` | Target latitude |
| `lng` | `number` | Target longitude |
| `zoom` | `number` | Target zoom |
| `pitch` | `number` | Target pitch (0–60°) |
| `bearing` | `number` | Target bearing (0–360°) |
| `duration` | `number` | Animation duration in ms. Default `1200` |

---

### `fitBounds(options)`

Zooms and pans the camera to show all the given coordinates.

| Option | Type | Description |
|---|---|---|
| `coordinates` | `{ lat, lng }[]` | Points to include |
| `padding` | `number` | Edge padding in dp. Default `80` |

---

### `enableLocationDot()`

Shows the user's GPS position as a blue dot. Camera centers on the user.
Requires location permission.

---

### `enableCompassMode()`

Compass navigation: map rotates in real time to match the phone's compass heading.
The blue dot shows a direction cone. Works standing still.
Requires location permission.

---

### `enableGpsMode()`

GPS navigation: map rotates based on direction of travel.
Best for driving. Requires movement to update heading.
Requires location permission.

---

### `disableLocation()`

Hides the location dot and stops all location-based camera following.

---

### `enable3DBuildings()`

Extrudes buildings to their real-world heights. Works with vector tile styles
(OpenFreeMap, CARTO, Stadia, MapTiler). Has no effect on raster styles.
Camera tilts to 55° automatically.

---

### `disable3DBuildings()`

Removes building extrusion, resets pitch to 0°.

---

### `enable3DTerrain()`

Tilts the camera to 60° for a landscape depth effect.
Best used with ESRI Hybrid or Google Terrain tiles.

---

### `disable3DTerrain()`

Resets camera pitch to 0°.

---

### `startCityTour(options?)`

Starts a 360° orbital camera animation at 55° pitch. Loops until stopped.

| Option | Type | Description |
|---|---|---|
| `duration` | `number` | Time per orbit in ms. Default `20000` |

---

### `stopCityTour()`

Stops the orbital animation.

---

### `setStyleUrl(options)`

Changes the map style at runtime. Existing markers and routes are preserved.

| Option | Type | Description |
|---|---|---|
| `url` | `string` | MapLibre style URL or inline style JSON |

---

## TypeScript

The package ships with TypeScript definitions. Import the plugin and option types:

```typescript
import { ChhittooMap } from 'capacitor-maplibre-native';
import type {
  CreateOptions,
  MarkerOptions,
  RouteOptions,
  DriverMarkerOptions,
} from 'capacitor-maplibre-native';
```

---

## Common patterns

### Ride-sharing: rider view

```js
import { ChhittooMap } from 'capacitor-maplibre-native';
import { Geolocation } from '@capacitor/geolocation';

// Show map with pickup and dropoff
await ChhittooMap.create({ lat, lng, zoom: 14, styleUrl, ...bounds });

await ChhittooMap.setMarker({ id: 'pickup',  lat: p.lat, lng: p.lng, icon: 'pickup',  title: 'Pickup' });
await ChhittooMap.setMarker({ id: 'dropoff', lat: d.lat, lng: d.lng, icon: 'dropoff', title: 'Dropoff' });

await ChhittooMap.setRoute({ coordinates: routeCoords, color: '#000', width: 4 });

// Fit all points on screen
await ChhittooMap.fitBounds({ coordinates: [pickup, dropoff], padding: 80 });

// Animate driver in real time (called from your WebSocket/polling handler)
function onDriverUpdate(lat, lng, bearing) {
  ChhittooMap.updateDriverMarker({ lat, lng, bearing, duration: 1000 });
}
```

### Ride-sharing: driver view

```js
await ChhittooMap.create({ lat, lng, zoom: 17, mode: 'driver', styleUrl, ...bounds });

// Camera now follows driver position and rotates automatically
navigator.geolocation.watchPosition(pos => {
  ChhittooMap.updateDriverMarker({
    lat:      pos.coords.latitude,
    lng:      pos.coords.longitude,
    bearing:  pos.coords.heading ?? 0,
    duration: 1000,
  });
});
```

### Compass navigation (on foot)

```js
import { Geolocation } from '@capacitor/geolocation';

await Geolocation.requestPermissions();
await ChhittooMap.enableCompassMode();
// Map now rotates as the user turns their phone
```

### Switch between day and night styles

```js
const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

await ChhittooMap.setStyleUrl({
  url: isDark
    ? 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json'
    : 'https://basemaps.cartocdn.com/gl/positron-gl-style/style.json',
});
```

---

## Platform support

| Feature | Android | iOS |
|---|---|---|
| Map rendering | ✅ | ❌ |
| Markers | ✅ | ❌ |
| Route | ✅ | ❌ |
| Driver animation | ✅ | ❌ |
| Compass navigation | ✅ | ❌ |
| 3D buildings | ✅ | ❌ |
| All tile styles | ✅ | ❌ |

iOS is not currently implemented. PRs welcome.

---

## Contributing

1. Fork the repository
2. Make your changes in `android/src/main/java/com/chhittoo/maplibre/`
3. Test with the example app in the plugin's `www/` folder
4. Open a pull request

Please open an issue before starting work on large changes.

---

## License

MIT — see [LICENSE](LICENSE)

Built and maintained by [Yashitech Solutions Pvt. Ltd.](https://yashitech.com)