Architecture
============

High-Level Overview
-------------------

.. code-block:: text

    ┌─────────────────────────────────────────────────────────┐
    │                   Browser / gRPC Client                 │
    └───────────────────────────┬─────────────────────────────┘
                                │ HTTP / gRPC
    ┌───────────────────────────▼─────────────────────────────┐
    │               Spring Boot Application                   │
    │                                                         │
    │  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
    │  │ REST API     │  │ gRPC Server  │  │ Web / Proxy   │  │
    │  │ (OpenAPI)    │  │ (Protobuf)   │  │ Controllers   │  │
    │  └──────┬───────┘  └──────┬───────┘  └──────┬────────┘  │
    │         │                 │                 │           │
    │  ┌──────▼─────────────────▼──────┐          │           │
    │  │        Service Layer          │          │           │
    │  │  RouteService               │          │           │
    │  │  WaypointService              │          │           │
    │  └──────────────┬────────────────┘          │           │
    │                 │                           │           │
    │  ┌──────────────▼────────────────┐          │           │
    │  │       Repository Layer        │          │           │
    │  │  RouteRepository            │          │           │
    │  │  WaypointRepository           │          │           │
    │  └──────────────┬────────────────┘          │           │
    └─────────────────┼───────────────────────────┼───────────┘
                      │ Bolt                      │ HTTP/WMS
              ┌───────▼───────┐          ┌────────▼────────┐
              │    Neo4j      │          │   GeoServer     │
              │  (port 7687)  │          │  (port 8600)    │
              └───────────────┘          └─────────────────┘

Package Structure
-----------------

All application code lives under ``com.honeybadger.flightlogistics``.

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Package
     - Responsibility
   * - ``api``
     - Delegate implementations for the OpenAPI-generated ``RoutesApi`` and
       ``WaypointsApi`` controller interfaces.
   * - ``controller``
     - ``HomeController`` (home page + info endpoints) and
       ``GeoServerWmsProxy`` (tile caching proxy).
   * - ``domain``
     - Neo4j node entities (``RouteEntity``, ``WaypointEntity``) and the
       ``RouteStatus`` enum.
   * - ``repository``
     - Spring Data Neo4j repositories with custom Cypher queries.
   * - ``service``
     - Business logic: ``RouteService`` and ``WaypointService``.
   * - (root)
     - ``FlightLogisticsApplication`` (entry point),
       ``Neo4jBrowserInfo`` (record), ``Neo4jBrowserLogger`` (startup logger).

Request Flow — REST
-------------------

1. An HTTP request arrives at a generated Spring MVC controller (e.g.
   ``RoutesApiController``).
2. The controller delegates to the hand-written ``RoutesApiDelegateImpl``.
3. The delegate calls the appropriate ``RouteService`` method.
4. ``RouteService`` uses ``RouteRepository`` to execute Cypher against
   Neo4j and maps the result to the OpenAPI model.
5. The delegate wraps the model in a ``ResponseEntity`` and returns it.

Map Tile Caching
----------------

``GeoServerWmsProxy`` intercepts ``GET /api/map/wms`` requests from the Leaflet
map in the browser.  Each request's query parameters are normalised (lowercased,
sorted) and hashed with SHA-256 to produce a stable cache key.  Tiles are
cached as PNG files under ``~/.flight-logistics/tile-cache/`` (configurable via
``map.tile-cache.dir``).  Cached tiles are served with a 30-day
``Cache-Control: max-age, immutable`` header.  If GeoServer is unreachable and
no cached tile exists, the proxy returns ``503 Service Unavailable``.

Graph Data Model
----------------

.. code-block:: text

    (:Route {id, name, description, status})
        -[:HAS_WAYPOINT]->
    (:Waypoint {id, name, latitude, longitude, sequenceNumber})

Waypoints are ordered by ``sequenceNumber``. Reordering a route's waypoints
updates all ``sequenceNumber`` values atomically via a single Cypher
``FOREACH`` statement.

Qt Desktop Client
-----------------

The Qt desktop client (``qt-client/``) communicates with the server over gRPC
and renders a live map by requesting WMS tiles directly from GeoServer.

Map Aspect-Ratio Correction
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

WMS ``GetMap`` requests using ``SRS=EPSG:4326`` specify the bounding box in
**decimal degrees**.  One degree of latitude always represents the same
real-world distance, but one degree of longitude shrinks towards the poles by a
factor of :math:`\cos(\phi)`, where :math:`\phi` is the latitude.

If the bounding-box longitude span were chosen simply to match the widget's
pixel width, the resulting tile would appear **squashed vertically** at UK
latitudes (~55 °N, where :math:`\cos(55°) \approx 0.574`).

``MapWidget`` corrects for this by computing the longitude span from the
latitude span, the widget's pixel aspect ratio, and the cosine of the
viewport's mid-latitude:

.. math::

   \Delta\lambda = \Delta\phi \times \frac{W}{H} \times \frac{1}{\cos\phi_{\text{mid}}}

where:

* :math:`\Delta\lambda` — corrected longitude span sent in the WMS BBOX
* :math:`\Delta\phi` — latitude span of the current viewport
* :math:`W / H` — widget width ÷ height (pixel aspect ratio)
* :math:`\phi_{\text{mid}}` — mid-latitude of the viewport in radians

The same corrected longitude range is used when projecting waypoint markers
onto the widget (``geoToPixel``), in panning (degrees per pixel), and in
scroll-wheel zooming (cursor-pin longitude), so all three stay consistent with
the tile.

