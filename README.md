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

## Usage examples

- [Vanilla JS / HTML](#vanilla-js--html)
- [React](#react)
- [Svelte](#svelte)

---

### Vanilla JS / HTML

#### Rider view — show pickup, dropoff, and live driver position

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

await ChhittooMap.setMarker({ id: 'pickup',  lat: 26.812, lng: 87.279, icon: 'pickup',  title: 'Pickup' });
await ChhittooMap.setMarker({ id: 'dropoff', lat: 26.799, lng: 87.295, icon: 'dropoff', title: 'Dropoff' });

await ChhittooMap.setRoute({
  coordinates: [[87.279, 26.812], [87.283, 26.806], [87.295, 26.799]],
  color: '#18181b',
  width: 4,
});

await ChhittooMap.fitBounds({
  coordinates: [{ lat: 26.812, lng: 87.279 }, { lat: 26.799, lng: 87.295 }],
  padding: 80,
});

// Called from your WebSocket / polling handler
function onDriverUpdate(lat, lng, bearing) {
  ChhittooMap.updateDriverMarker({ lat, lng, bearing, duration: 1000 });
}
```

#### Driver view — 2.5D navigation

```js
await ChhittooMap.create({
  lat: 26.8065, lng: 87.2834, zoom: 17, mode: 'driver',
  styleUrl: 'https://tiles.openfreemap.org/styles/liberty',
  x: Math.round(rect.x), y: Math.round(rect.y),
  width: Math.round(rect.width), height: Math.round(rect.height),
});

// Camera follows + rotates with every GPS update
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

#### `useMaplibre` hook (reusable)

Create this hook once and use it in any component.

```tsx
// hooks/useMaplibre.ts
import { useEffect, useRef, useCallback } from 'react';
import { ChhittooMap } from 'capacitor-maplibre-native';

const STYLE = 'https://tiles.openfreemap.org/styles/liberty';

export function useMaplibre() {
  const mapRef  = useRef<HTMLDivElement>(null);
  const readyRef = useRef(false);

  useEffect(() => {
    const el = mapRef.current;
    if (!el) return;

    const rect = el.getBoundingClientRect();

    ChhittooMap.create({
      lat: 26.8065, lng: 87.2834, zoom: 14,
      styleUrl: STYLE,
      x: Math.round(rect.x),     y: Math.round(rect.y),
      width: Math.round(rect.width), height: Math.round(rect.height),
    }).then(() => {
      readyRef.current = true;
    });

    // Clean up when the component unmounts
    return () => {
      readyRef.current = false;
      ChhittooMap.destroy();
    };
  }, []);

  const setPickup = useCallback((lat: number, lng: number) => {
    if (!readyRef.current) return;
    ChhittooMap.setMarker({ id: 'pickup', lat, lng, icon: 'pickup', title: 'Pickup' });
  }, []);

  const setDropoff = useCallback((lat: number, lng: number) => {
    if (!readyRef.current) return;
    ChhittooMap.setMarker({ id: 'dropoff', lat, lng, icon: 'dropoff', title: 'Dropoff' });
  }, []);

  const setRoute = useCallback((coords: [number, number][]) => {
    if (!readyRef.current) return;
    ChhittooMap.setRoute({ coordinates: coords, color: '#18181b', width: 4 });
  }, []);

  const updateDriver = useCallback((lat: number, lng: number, bearing: number) => {
    if (!readyRef.current) return;
    ChhittooMap.updateDriverMarker({ lat, lng, bearing, duration: 1000 });
  }, []);

  return { mapRef, setPickup, setDropoff, setRoute, updateDriver };
}
```

#### Rider screen component

```tsx
// screens/RiderScreen.tsx
import React, { useEffect } from 'react';
import { useMaplibre } from '../hooks/useMaplibre';

const PICKUP  = { lat: 26.812, lng: 87.279 };
const DROPOFF = { lat: 26.799, lng: 87.295 };

export function RiderScreen() {
  const { mapRef, setPickup, setDropoff, setRoute } = useMaplibre();

  useEffect(() => {
    // Small delay lets create() finish before placing markers
    const t = setTimeout(() => {
      setPickup(PICKUP.lat, PICKUP.lng);
      setDropoff(DROPOFF.lat, DROPOFF.lng);
      setRoute([[87.279, 26.812], [87.283, 26.806], [87.295, 26.799]]);
    }, 500);

    return () => clearTimeout(t);
  }, [setPickup, setDropoff, setRoute]);

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>

      {/* Map placeholder — transparent so native view shows through */}
      <div
        ref={mapRef}
        style={{ flex: 1, background: 'transparent' }}
      />

      {/* UI panel sits on top */}
      <div style={{ height: 200, background: '#fff', padding: 16 }}>
        <p>Waiting for driver...</p>
      </div>
    </div>
  );
}
```

#### Driver screen component

```tsx
// screens/DriverScreen.tsx
import React, { useEffect, useRef } from 'react';
import { ChhittooMap } from 'capacitor-maplibre-native';

const STYLE = 'https://tiles.openfreemap.org/styles/liberty';

export function DriverScreen() {
  const mapRef   = useRef<HTMLDivElement>(null);
  const watchRef = useRef<number | null>(null);

  useEffect(() => {
    const el = mapRef.current;
    if (!el) return;

    const rect = el.getBoundingClientRect();

    ChhittooMap.create({
      lat: 26.8065, lng: 87.2834, zoom: 17,
      mode: 'driver',
      styleUrl: STYLE,
      x: Math.round(rect.x),     y: Math.round(rect.y),
      width: Math.round(rect.width), height: Math.round(rect.height),
    }).then(() => {
      // Start following GPS after map is ready
      watchRef.current = navigator.geolocation.watchPosition(pos => {
        ChhittooMap.updateDriverMarker({
          lat:     pos.coords.latitude,
          lng:     pos.coords.longitude,
          bearing: pos.coords.heading ?? 0,
          duration: 1000,
        });
      });
    });

    return () => {
      if (watchRef.current !== null) {
        navigator.geolocation.clearWatch(watchRef.current);
      }
      ChhittooMap.destroy();
    };
  }, []);

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <div ref={mapRef} style={{ flex: 1, background: 'transparent' }} />
      <div style={{ height: 120, background: '#18181b', color: '#fff', padding: 16 }}>
        <p>Navigation active</p>
      </div>
    </div>
  );
}
```

#### Style switcher in React

```tsx
import React, { useState } from 'react';
import { ChhittooMap } from 'capacitor-maplibre-native';

const STYLES = {
  light: 'https://tiles.openfreemap.org/styles/liberty',
  dark:  'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json',
  sat:   'https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json',
} as const;

type StyleKey = keyof typeof STYLES;

export function StyleSwitcher() {
  const [active, setActive] = useState<StyleKey>('light');

  const switchStyle = async (key: StyleKey) => {
    await ChhittooMap.setStyleUrl({ url: STYLES[key] });
    setActive(key);
  };

  return (
    <div style={{ display: 'flex', gap: 8 }}>
      {(Object.keys(STYLES) as StyleKey[]).map(key => (
        <button
          key={key}
          onClick={() => switchStyle(key)}
          style={{ fontWeight: active === key ? 700 : 400 }}
        >
          {key}
        </button>
      ))}
    </div>
  );
}
```

---

### Svelte

#### `MapView.svelte` — reusable map component

```svelte
<!-- lib/MapView.svelte -->
<script lang="ts">
  import { onMount, onDestroy, createEventDispatcher } from 'svelte';
  import { ChhittooMap } from 'capacitor-maplibre-native';

  export let lat: number      = 26.8065;
  export let lng: number      = 87.2834;
  export let zoom: number     = 14;
  export let mode: 'rider' | 'driver' = 'rider';
  export let styleUrl: string = 'https://tiles.openfreemap.org/styles/liberty';

  const dispatch = createEventDispatcher<{ ready: void }>();

  let mapEl: HTMLDivElement;
  let ready = false;

  onMount(async () => {
    const rect = mapEl.getBoundingClientRect();

    await ChhittooMap.create({
      lat, lng, zoom, mode, styleUrl,
      x:      Math.round(rect.x),
      y:      Math.round(rect.y),
      width:  Math.round(rect.width),
      height: Math.round(rect.height),
    });

    ready = true;
    dispatch('ready');
  });

  onDestroy(() => {
    ChhittooMap.destroy();
  });

  // Expose helpers to parent via bind:this or exports
  export async function placeMarker(
    id: string, markerLat: number, markerLng: number,
    icon = 'pickup', title = ''
  ) {
    if (!ready) return;
    await ChhittooMap.setMarker({ id, lat: markerLat, lng: markerLng, icon, title });
  }

  export async function drawRoute(coords: [number, number][]) {
    if (!ready) return;
    await ChhittooMap.setRoute({ coordinates: coords, color: '#18181b', width: 4 });
  }

  export async function moveDriver(
    driverLat: number, driverLng: number, bearing: number
  ) {
    if (!ready) return;
    await ChhittooMap.updateDriverMarker({
      lat: driverLat, lng: driverLng, bearing, duration: 1000,
    });
  }
</script>

<!-- Transparent placeholder — native map renders behind this -->
<div
  bind:this={mapEl}
  class="map-el"
/>

<style>
  .map-el {
    width: 100%;
    height: 100%;
    background: transparent;
  }
</style>
```

#### Rider screen

```svelte
<!-- routes/ride/+page.svelte -->
<script lang="ts">
  import { onDestroy } from 'svelte';
  import MapView from '$lib/MapView.svelte';

  const PICKUP  = { lat: 26.812, lng: 87.279 };
  const DROPOFF = { lat: 26.799, lng: 87.295 };
  const ROUTE: [number, number][] = [
    [87.279, 26.812],
    [87.283, 26.806],
    [87.295, 26.799],
  ];

  let mapView: MapView;

  async function onMapReady() {
    await mapView.placeMarker('pickup',  PICKUP.lat,  PICKUP.lng,  'pickup',  'Pickup');
    await mapView.placeMarker('dropoff', DROPOFF.lat, DROPOFF.lng, 'dropoff', 'Dropoff');
    await mapView.drawRoute(ROUTE);
  }

  // Called when a driver update arrives (WebSocket / polling)
  export function onDriverLocation(lat: number, lng: number, bearing: number) {
    mapView.moveDriver(lat, lng, bearing);
  }
</script>

<div class="screen">
  <div class="map-area">
    <MapView
      bind:this={mapView}
      lat={26.8065}
      lng={87.2834}
      zoom={14}
      on:ready={onMapReady}
    />
  </div>

  <div class="panel">
    <p>Looking for your driver...</p>
  </div>
</div>

<style>
  .screen    { height: 100vh; display: flex; flex-direction: column; }
  .map-area  { flex: 1; }
  .panel     { height: 220px; background: #fff; padding: 20px; }
</style>
```

#### Driver screen with GPS tracking

```svelte
<!-- routes/drive/+page.svelte -->
<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { ChhittooMap } from 'capacitor-maplibre-native';

  let mapEl: HTMLDivElement;
  let watchId: number | null = null;

  onMount(async () => {
    const rect = mapEl.getBoundingClientRect();

    await ChhittooMap.create({
      lat: 26.8065, lng: 87.2834,
      zoom: 17,
      mode: 'driver',
      styleUrl: 'https://tiles.openfreemap.org/styles/liberty',
      x:      Math.round(rect.x),
      y:      Math.round(rect.y),
      width:  Math.round(rect.width),
      height: Math.round(rect.height),
    });

    watchId = navigator.geolocation.watchPosition(pos => {
      ChhittooMap.updateDriverMarker({
        lat:      pos.coords.latitude,
        lng:      pos.coords.longitude,
        bearing:  pos.coords.heading ?? 0,
        duration: 1000,
      });
    });
  });

  onDestroy(() => {
    if (watchId !== null) navigator.geolocation.clearWatch(watchId);
    ChhittooMap.destroy();
  });
</script>

<div class="screen">
  <!-- Transparent — native 2.5D map renders behind -->
  <div bind:this={mapEl} class="map-area" />

  <div class="nav-bar">
    <span>Navigation active</span>
  </div>
</div>

<style>
  .screen   { height: 100vh; display: flex; flex-direction: column; }
  .map-area { flex: 1; background: transparent; }
  .nav-bar  { height: 80px; background: #18181b; color: #fff; display: flex; align-items: center; padding: 0 20px; }
</style>
```

#### Reactive style switching in Svelte

```svelte
<script lang="ts">
  import { ChhittooMap } from 'capacitor-maplibre-native';

  const styles = [
    { label: 'Light',      url: 'https://tiles.openfreemap.org/styles/liberty' },
    { label: 'Dark',       url: 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json' },
    { label: 'Satellite',  url: '' }, // set esriSatellite inline JSON here
  ];

  let activeIndex = 0;

  async function switchStyle(index: number) {
    activeIndex = index;
    await ChhittooMap.setStyleUrl({ url: styles[index].url });
  }
</script>

<div class="style-bar">
  {#each styles as style, i}
    <button
      class:active={i === activeIndex}
      on:click={() => switchStyle(i)}
    >
      {style.label}
    </button>
  {/each}
</div>

<style>
  .style-bar { display: flex; gap: 8px; padding: 8px; background: rgba(255,255,255,0.9); border-radius: 8px; }
  button.active { font-weight: 700; }
</style>
```

---

## Common patterns

### Compass navigation

```js
import { Geolocation } from '@capacitor/geolocation';

// Always request permission before enabling location features
await Geolocation.requestPermissions();
await ChhittooMap.enableCompassMode();
// Map now rotates as the user turns their phone
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

### Handle layout changes (orientation, resize)

```js
// Vanilla JS
const el = document.getElementById('map');

window.addEventListener('resize', () => {
  const rect = el.getBoundingClientRect();
  ChhittooMap.updateBounds({
    x: Math.round(rect.x),     y: Math.round(rect.y),
    width: Math.round(rect.width), height: Math.round(rect.height),
  });
});

// React — inside useEffect with ResizeObserver
const observer = new ResizeObserver(() => {
  const rect = mapRef.current?.getBoundingClientRect();
  if (rect) ChhittooMap.updateBounds({ ... });
});
observer.observe(mapRef.current);

// Svelte — use svelte-resize-observer or window resize
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