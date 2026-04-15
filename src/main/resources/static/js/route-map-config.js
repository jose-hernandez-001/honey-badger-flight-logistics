(function (global) {
  'use strict';

  // GeoServer's built-in "line" style renders blue strokes. The route page
  // needs thin black admin and coastline lines, so the page sends an explicit
  // SLD with the WMS request instead of depending on server-side style state.
  // The SLD contains one named layer entry for each overlay drawn above land.
  var boundarySld = [
    '<StyledLayerDescriptor version="1.0.0"',
    ' xmlns="http://www.opengis.net/sld"',
    ' xmlns:ogc="http://www.opengis.net/ogc"',
    ' xmlns:xlink="http://www.w3.org/1999/xlink"',
    ' xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">',
    '<NamedLayer><Name>uk-flight:ne_10m_admin_1_states_provinces</Name><UserStyle><FeatureTypeStyle><Rule><LineSymbolizer><Stroke><CssParameter name="stroke">#000000</CssParameter><CssParameter name="stroke-width">0.5</CssParameter></Stroke></LineSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer>',
    '<NamedLayer><Name>uk-flight:ne_10m_coastline</Name><UserStyle><FeatureTypeStyle><Rule><LineSymbolizer><Stroke><CssParameter name="stroke">#000000</CssParameter><CssParameter name="stroke-width">0.5</CssParameter></Stroke></LineSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer>',
    '</StyledLayerDescriptor>'
  ].join('');

  // Central map configuration consumed by route.html. Keeping these values in
  // a dedicated file makes it easier to tune cartography and route styling
  // without editing the larger route page script.
  global.ROUTE_MAP_CONFIG = {
    // The UI talks to the local WMS proxy rather than GeoServer directly so it
    // benefits from the app's tile cache and stable browser-facing endpoint.
    wmsUrl: '/api/map/wms',

    // Default viewport covering the UK. The route page falls back to these
    // bounds before waypoints load, and also when there are not enough points
    // to calculate a route-specific bounding box.
    bounds: [[49.5, -8.5], [61.5, 2.5]],

    // Layer order matters. Land is drawn first, then the black outline overlay,
    // then airports. Interactive waypoint markers and the route route are
    // added later by the route page on top of these WMS tiles.
    layers: {
      // Land tiles are requested as transparent PNGs and tinted in the browser
      // with the map-land-tint CSS class so the sea color can come from the map
      // container background while land keeps a separate visual treatment.
      land: {
        layers: 'uk-flight:ne_10m_land',
        format: 'image/png',
        transparent: true,
        version: '1.1.0',
        className: 'map-land-tint',
        attribution: '© <a href="https://www.naturalearthdata.com/" target="_blank">Natural Earth</a>'
      },

      // Administrative boundaries and coastline are combined into a single WMS
      // request because they share the same stroke styling and should render as
      // one visual overlay above the land layer.
      boundaries: {
        layers: 'uk-flight:ne_10m_admin_1_states_provinces,uk-flight:ne_10m_coastline',
        format: 'image/png',
        transparent: true,
        version: '1.1.0',
        sld_body: boundarySld
      },

      // Airports are kept as a separate overlay so their opacity can be tuned
      // independently from the land and boundary layers.
      airports: {
        layers: 'uk-flight:ne_10m_airports',
        format: 'image/png',
        transparent: true,
        version: '1.1.0',
        opacity: 0.85
      }
    },

    // Styling for the Leaflet polyline that connects route waypoints. This is
    // intentionally brighter and thicker than the boundary overlay so the route
    // remains obvious even when it crosses dense map details.
    routeLine: {
      color: '#003087',
      weight: 4,
      dashArray: '6 4',
      opacity: 1
    }
  };
})(window);
