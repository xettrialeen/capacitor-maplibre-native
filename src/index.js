import { registerPlugin } from '@capacitor/core';

/**
 * capacitor-maplibre-native
 *
 * Native MapLibre GL map for Capacitor 7.
 * Renders a real native Android MapView — not a WebView map.
 *
 * @example
 * import { ChhittooMap } from 'capacitor-maplibre-native';
 *
 * await ChhittooMap.create({
 *   lat: 26.8065,
 *   lng: 87.2834,
 *   zoom: 14,
 *   styleUrl: 'https://tiles.openfreemap.org/styles/liberty',
 *   x: Math.round(rect.x),
 *   y: Math.round(rect.y),
 *   width: Math.round(rect.width),
 *   height: Math.round(rect.height),
 * });
 */
const ChhittooMap = registerPlugin('ChhittooMap');

export { ChhittooMap };