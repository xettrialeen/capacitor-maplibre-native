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

The plugin places a native `MapView` behind the Capacitor `WebView`. The `WebView` background is made transparent over your map element so the native rendering shows through. Your HTML UI sits on top.

Touches in the map area are intercepted by a transparent native `View` (`MapTouchForwarder`) that forwards them to the `MapView` — full native gesture performance for pan, pinch-zoom, two-finger rotation, and tilt.

```
Activity window
├── MapView              ← native OpenGL, renders tiles at 60fps
├── WebView              ← transparent over map, renders your HTML UI
└── MapTouchForwarder    ← invisible, routes all touches to MapView
```

---

## Installation

```bash
npm install capacitor-maplibre-native
npx cap sync android
```

Capacitor auto-discovers the plugin on sync. No manual registration needed.

---

## Android setup

### 1. Location permissions

Required for `enableLocationDot()`, `enableCompassMode()`, and `enableGpsMode()`. Add to `android/app/src/main/AndroidManifest.xml` inside `<manifest>`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### 2. Transparent WebView background

```css
html, body {
  background: transparent !important;
}
```

The plugin sets the WebView background to transparent automatically. Make sure your HTML/body element doesn't override this with a solid color.

### 3. Map placeholder element

```html
<div id="map"
     style="position: fixed; top: 0; left: 0; right: 0; height: 60vh; background: transparent;">
</div>
```

The native map renders behind this element, aligned to its bounding rect.

---

## Quick start

```js
import { ChhittooMap } from 'capacitor-maplibre-native';

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

await ChhittooMap.setMarker({
  id: 'pickup', lat: 26.812, lng: 87.279, icon: 'pickup', title: 'Pickup'
});

await ChhittooMap.setRoute({
  coordinates: [[87.279, 26.812], [87.283, 26.806], [87.295, 26.799]],
  color: '#18181b',
  width: 5,
});
```

---

## Map styles

The `styleUrl` option accepts any MapLibre GL style source.

### Free — no API key

| Provider | Style | URL |
|---|---|---|
| OpenFreeMap | Liberty | `https://tiles.openfreemap.org/styles/liberty` |
| OpenFreeMap | Bright | `https://tiles.openfreemap.org/styles/bright` |
| CARTO | Positron | `https://basemaps.cartocdn.com/gl/positron-gl-style/style.json` |
| CARTO | Voyager | `https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json` |
| CARTO | Dark Matter | `https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json` |
| Protomaps | Light | `https://api.protomaps.com/styles/v4/light/en.json` |
| Protomaps | Dark | `https://api.protomaps.com/styles/v4/dark/en.json` |

### Satellite — ESRI (free)

```js
const esriSatellite = JSON.stringify({
  version: 8,
  glyphs: 'https://fonts.openmaptiles.org/{fontstack}/{range}.pbf',
  sources: {
    tiles: {
      type: 'raster',
      tiles: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'],
      tileSize: 256,
      attribution: '© Esri, Maxar',
      maxzoom: 19,
    },
  },
  layers: [{ id: 'raster', type: 'raster', source: 'tiles' }],
});

await ChhittooMap.setStyleUrl({ url: esriSatellite });
```

> **Note:** Always include a `glyphs` URL in inline raster style JSON. Without it, marker text rendering fails which can also suppress marker icons on some devices.

### Paid providers

```js
// Stadia Maps — alidade_smooth is ideal for ride-sharing
const url = `https://tiles.stadiamaps.com/styles/alidade_smooth.json?api_key=${KEY}`;

// MapTiler
const url = `https://api.maptiler.com/maps/streets-v2/style.json?key=${KEY}`;
```

---

## Custom marker icons

Add PNG or vector drawable XML files to `android/src/main/res/drawable/`:

| File | Used for |
|---|---|
| `ic_pickup.xml` | `icon: "pickup"` |
| `ic_dropoff.xml` | `icon: "dropoff"` |
| `ic_driver_car.xml` | `icon: "driver"` (navigation arrow) |
| `ic_rider.xml` | `icon: "rider"` |

If a drawable is not found the plugin falls back to a coloured circle automatically.

---

## API reference

### `create(options)`

Initializes and displays the native map.

| Option | Type | Required | Description |
|---|---|---|---|
| `lat` | `number` | Yes | Initial center latitude |
| `lng` | `number` | Yes | Initial center longitude |
| `zoom` | `number` | No | Zoom level 0–22. Default `15` |
| `styleUrl` | `string` | Yes | MapLibre style URL or inline style JSON |
| `mode` | `"rider"` \| `"driver"` | No | Initial mode. Default `"rider"` |
| `x` | `number` | Yes | Placeholder element left offset (CSS px) |
| `y` | `number` | Yes | Placeholder element top offset (CSS px) |
| `width` | `number` | Yes | Placeholder element width (CSS px) |
| `height` | `number` | Yes | Placeholder element height (CSS px) |

---

### `destroy()`

Removes the map and frees all resources.

---

### `updateBounds(options)`

Repositions the native map when the placeholder element moves or resizes.

| Option | Type | Description |
|---|---|---|
| `x` / `y` / `width` / `height` | `number` | New bounding rect from `getBoundingClientRect()` |

---

### `setRiderMode()`

Overhead view — pitch 0°, north-up, zoom 15, all gestures enabled.

---

### `setDriverMode(options)`

2.5D navigation — pitch 45°, zoom 17. Camera follows and rotates with `updateDriverMarker`.

| Option | Type | Description |
|---|---|---|
| `bearing` | `number` | Initial heading in degrees. Default `0` |

---

### `updateDriverMarker(options)`

Animates the driver icon to a new GPS position. Returns route progress data if a route is active.

| Option | Type | Required | Description |
|---|---|---|---|
| `lat` | `number` | Yes | New latitude |
| `lng` | `number` | Yes | New longitude |
| `bearing` | `number` | No | Heading in degrees — rotates the icon. Default `0` |
| `duration` | `number` | No | Animation duration in ms. Default `1000` |

**Returns:**

```ts
{
  distanceRemaining: number  // km remaining on the route
  distanceTraveled:  number  // km already traveled
  totalDistance:     number  // total route distance in km
  percentComplete:   number  // 0–100
  etaMinutes:        number  // estimated minutes at 30 km/h
}
```

> **Smooth animation tip:** call this once per second with `duration: 1000`. The native `ValueAnimator` handles 60fps interpolation between calls. Calling more frequently than once per second causes bridge overhead and makes the map feel laggy.

---

### `setMarker(options)`

Adds a marker or moves an existing one (matched by `id`).

| Option | Type | Required | Description |
|---|---|---|---|
| `id` | `string` | Yes | Unique identifier |
| `lat` | `number` | Yes | Latitude |
| `lng` | `number` | Yes | Longitude |
| `icon` | `string` | No | Icon name. Default `"pickup"` |
| `title` | `string` | No | Label text shown above the marker |

Markers survive tile style switches automatically.

---

### `removeMarker(options)`

| Option | Type | Description |
|---|---|---|
| `id` | `string` | ID of the marker to remove |

---

### `setRoute(options)`

Draws a route polyline. Uses GeoJSON coordinate order: `[longitude, latitude]`.

The route is split into a **remaining** portion (colored) and **traveled** portion (gray) that updates automatically on every `updateDriverMarker` call.

| Option | Type | Required | Description |
|---|---|---|---|
| `coordinates` | `[number, number][]` | Yes | `[lng, lat]` pairs |
| `color` | `string` | No | Hex color. Default `"#18181b"` |
| `width` | `number` | No | Line width in dp. Default `5` |

Route and style (color/width) survive tile style switches automatically.

---

### `clearRoute()`

Removes the route polyline and resets route progress state.

---

### `animateCamera(options)`

Smoothly animates the camera to a new position.

| Option | Type | Description |
|---|---|---|
| `lat` / `lng` | `number` | Target position |
| `zoom` | `number` | Target zoom (optional) |
| `pitch` | `number` | Target pitch 0–60° (optional) |
| `bearing` | `number` | Target bearing 0–360° (optional) |
| `duration` | `number` | Animation ms. Default `1200` |

---

### `fitBounds(options)`

Zooms and pans to show all given coordinates.

| Option | Type | Description |
|---|---|---|
| `coordinates` | `{ lat, lng }[]` | Points to include |
| `padding` | `number` | Edge padding in dp. Default `80` |

---

### `enableLocationDot()`

Shows the user's GPS position as a blue dot. Camera centers on the user. Map stays north-up. Requires location permission.

---

### `enableCompassMode()`

**Compass navigation** — map rotates in real time to match the device's compass heading. Turn the phone left and the map turns left. Uses Android sensor fusion (accelerometer + magnetometer), works standing still. Requires location permission.

---

### `enableGpsMode()`

**GPS navigation** — map rotates based on direction of travel. Best for driving. Requires actual movement to update heading. Requires location permission.

---

### `disableLocation()`

Hides the location dot and stops all location-based camera following.

---

### `enable3DBuildings()`

Extrudes buildings to their real-world heights using vector tile data. Works with OpenMapTiles-schema styles (OpenFreeMap, CARTO, Stadia, MapTiler). Has no effect on raster styles (ESRI, Google Maps). Camera tilts to 55° automatically.

---

### `disable3DBuildings()`

Removes building extrusion, resets pitch.

---

### `enable3DTerrain()`

Tilts the camera to 60° for a landscape depth effect. Best with ESRI Hybrid or Google Terrain tiles.

---

### `disable3DTerrain()`

Resets camera pitch.

---

### `startCityTour(options?)`

360° orbital camera animation at 55° pitch. Loops until stopped.

| Option | Type | Description |
|---|---|---|
| `duration` | `number` | Time per orbit in ms. Default `20000` |

---

### `stopCityTour()`

Stops the orbital animation.

---

### `setStyleUrl(options)`

Changes the map style at runtime. All existing markers and the route are **automatically re-drawn** on the new style — no manual re-adding needed.

| Option | Type | Description |
|---|---|---|
| `url` | `string` | MapLibre style URL or inline style JSON |

---

## TypeScript

```typescript
import { ChhittooMap } from 'capacitor-maplibre-native';
import type {
  CreateOptions,
  MarkerOptions,
  RouteOptions,
  DriverMarkerOptions,
  RouteProgress,
} from 'capacitor-maplibre-native';
```

---

## Usage examples

- [Vanilla JS](#vanilla-js)
- [React](#react)
- [Svelte](#svelte)

---

### Vanilla JS

#### Rider view

```js
import { ChhittooMap } from 'capacitor-maplibre-native';

const el   = document.getElementById('map');
const rect = el.getBoundingClientRect();

await ChhittooMap.create({
  lat: 26.8065, lng: 87.2834, zoom: 14,
  styleUrl: 'https://tiles.openfreemap.org/styles/liberty',
  x: Math.round(rect.x), y: Math.round(rect.y),
  width: Math.round(rect.width), height: Math.round(rect.height),
});

// Place markers — route preview appears automatically when both are set
await ChhittooMap.setMarker({ id: 'pickup',  lat: 26.812, lng: 87.279, icon: 'pickup',  title: 'Pickup' });
await ChhittooMap.setMarker({ id: 'dropoff', lat: 26.799, lng: 87.295, icon: 'dropoff', title: 'Dropoff' });

// Draw route — splits into remaining (colored) and traveled (gray) automatically
await ChhittooMap.setRoute({
  coordinates: [[87.279, 26.812], [87.283, 26.806], [87.295, 26.799]],
  color: '#18181b', width: 4,
});

// Update driver — returns progress data on every call
const result = await ChhittooMap.updateDriverMarker({
  lat: 26.810, lng: 87.281, bearing: 170, duration: 1000,
});
console.log(`${result.distanceRemaining} km · ${result.etaMinutes} min remaining`);
```

#### Driver view

```js
await ChhittooMap.create({ ...bounds, zoom: 17, mode: 'driver', styleUrl });

navigator.geolocation.watchPosition(pos => {
  ChhittooMap.updateDriverMarker({
    lat:      pos.coords.latitude,
    lng:      pos.coords.longitude,
    bearing:  pos.coords.heading ?? 0,
    duration: 1000,
  });
});
```

#### Compass navigation

```js
import { Geolocation } from '@capacitor/geolocation';

await Geolocation.requestPermissions();
await ChhittooMap.enableCompassMode();
// Map now rotates as the user turns their phone
```

---

### React

```tsx
// hooks/useMaplibre.ts
import { useEffect, useRef } from 'react';
import { ChhittooMap } from 'capacitor-maplibre-native';

export function useMaplibre(styleUrl: string) {
  const mapRef  = useRef<HTMLDivElement>(null);
  const readyRef = useRef(false);

  useEffect(() => {
    const el = mapRef.current;
    if (!el) return;

    const rect = el.getBoundingClientRect();
    ChhittooMap.create({
      lat: 26.8065, lng: 87.2834, zoom: 14, styleUrl,
      x: Math.round(rect.x), y: Math.round(rect.y),
      width: Math.round(rect.width), height: Math.round(rect.height),
    }).then(() => { readyRef.current = true });

    return () => {
      readyRef.current = false;
      ChhittooMap.destroy();
    };
  }, []);

  return { mapRef, ready: readyRef };
}
```

```tsx
// RiderScreen.tsx
import React, { useEffect } from 'react';
import { ChhittooMap } from 'capacitor-maplibre-native';
import { useMaplibre } from '../hooks/useMaplibre';

export function RiderScreen() {
  const { mapRef } = useMaplibre('https://tiles.openfreemap.org/styles/liberty');

  useEffect(() => {
    const t = setTimeout(async () => {
      await ChhittooMap.setMarker({ id: 'pickup',  lat: 26.812, lng: 87.279, icon: 'pickup',  title: 'Pickup' });
      await ChhittooMap.setMarker({ id: 'dropoff', lat: 26.799, lng: 87.295, icon: 'dropoff', title: 'Dropoff' });
      await ChhittooMap.setRoute({ coordinates: [[87.279,26.812],[87.295,26.799]], color: '#18181b', width: 4 });
    }, 500);
    return () => clearTimeout(t);
  }, []);

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <div ref={mapRef} style={{ flex: 1, background: 'transparent' }} />
      <div style={{ height: 200, background: '#fff', padding: 16 }}>
        <p>Waiting for driver...</p>
      </div>
    </div>
  );
}
```

---

### Svelte

```svelte
<!-- MapView.svelte -->
<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { ChhittooMap } from 'capacitor-maplibre-native';

  export let styleUrl = 'https://tiles.openfreemap.org/styles/liberty';
  export let lat = 26.8065;
  export let lng = 87.2834;
  export let zoom = 14;

  let mapEl: HTMLDivElement;

  onMount(async () => {
    const rect = mapEl.getBoundingClientRect();
    await ChhittooMap.create({
      lat, lng, zoom, styleUrl,
      x: Math.round(rect.x), y: Math.round(rect.y),
      width: Math.round(rect.width), height: Math.round(rect.height),
    });
  });

  onDestroy(() => ChhittooMap.destroy());
</script>

<div bind:this={mapEl} style="width:100%;height:100%;background:transparent" />
```

---

## Common patterns

### Route progress display

```js
// updateDriverMarker returns live progress every call
const result = await ChhittooMap.updateDriverMarker({ lat, lng, bearing, duration: 1000 });

document.getElementById('distance').textContent = `${result.distanceRemaining.toFixed(1)} km`;
document.getElementById('eta').textContent      = `~${result.etaMinutes} min`;
document.getElementById('progress').style.width = `${result.percentComplete}%`;
```

### Auto route preview on pickup + dropoff

```js
let pickupSet = false, dropoffSet = false;

async function setPickup(lat, lng) {
  await ChhittooMap.setMarker({ id: 'pickup', lat, lng, icon: 'pickup', title: 'Pickup' });
  pickupSet = true;
  if (dropoffSet) await showRoutePreview();
}

async function setDropoff(lat, lng) {
  await ChhittooMap.setMarker({ id: 'dropoff', lat, lng, icon: 'dropoff', title: 'Dropoff' });
  dropoffSet = true;
  if (pickupSet) await showRoutePreview();
}

// Replace with your routing API response in production
async function showRoutePreview() {
  await ChhittooMap.setRoute({ coordinates: [[pickupLng, pickupLat], [dropoffLng, dropoffLat]], color: '#18181b', width: 4 });
  await ChhittooMap.fitBounds({ coordinates: [pickup, dropoff], padding: 80 });
}
```

### Switch tiles without losing state

```js
// Markers and route are automatically re-drawn on the new style —
// no need to re-add anything manually after setStyleUrl.
await ChhittooMap.setStyleUrl({
  url: 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json',
});
```

### Day / night style based on system preference

```js
const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
await ChhittooMap.setStyleUrl({
  url: isDark
    ? 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json'
    : 'https://tiles.openfreemap.org/styles/liberty',
});
```

### Handle layout changes

```js
const el = document.getElementById('map');
window.addEventListener('resize', () => {
  const rect = el.getBoundingClientRect();
  ChhittooMap.updateBounds({
    x: Math.round(rect.x), y: Math.round(rect.y),
    width: Math.round(rect.width), height: Math.round(rect.height),
  });
});
```

---

## Platform support

| Feature | Android | iOS |
|---|---|---|
| Map rendering | ✅ | ❌ |
| Markers | ✅ | ❌ |
| Route with progress split | ✅ | ❌ |
| Route progress (distance / ETA) | ✅ | ❌ |
| Driver animation | ✅ | ❌ |
| Compass navigation | ✅ | ❌ |
| GPS navigation | ✅ | ❌ |
| 3D buildings | ✅ | ❌ |
| City tour | ✅ | ❌ |
| All tile styles | ✅ | ❌ |
| State persistence across tile switch | ✅ | ❌ |

iOS is not currently implemented. PRs welcome.

---

## Contributing

1. Fork the repository
2. Make changes in `android/src/main/java/com/chhittoo/maplibre/`
3. Open a pull request

Please open an issue before large changes.

---

## License

MIT — see [LICENSE](LICENSE)

Built and maintained by [Yashitech Solutions Pvt. Ltd.](https://yashitech.com)