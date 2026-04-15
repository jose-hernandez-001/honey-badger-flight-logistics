Configuration
=============

All runtime settings are defined in
``src/main/resources/application.yaml``.  Spring Boot's externalized
configuration mechanism allows any property to be overridden via environment
variables, system properties, or a profile-specific YAML file.

Core Properties
---------------

.. list-table::
   :header-rows: 1
   :widths: 40 20 40

   * - Property
     - Default
     - Description
   * - ``spring.neo4j.uri``
     - ``bolt://localhost:7687``
     - Neo4j Bolt connection URI.
   * - ``spring.neo4j.authentication.username``
     - ``neo4j``
     - Neo4j username.
   * - ``spring.neo4j.authentication.password``
     - *(required)*
     - Neo4j password.
   * - ``geoserver.wms-url``
     - ``http://127.0.0.1:8600/geoserver/wms``
     - Upstream GeoServer WMS endpoint used by the tile proxy.
   * - ``map.tile-cache.dir``
     - ``${user.home}/.flight-logistics/tile-cache``
     - Directory where WMS tile PNGs are cached.

Neo4j Browser
-------------

At startup, ``Neo4jBrowserLogger`` resolves the browser and connection URLs from
the Neo4j URI and logs them.  The frontend fetches these via
``GET /api/neo4j/browser-url``.

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Endpoint
     - Description
   * - ``/api/neo4j/browser-url``
     - Returns ``browserUrl``, ``connectionUrl``, ``username``, ``password``.
   * - ``/api/geoserver/info``
     - Returns ``adminUrl`` derived from ``geoserver.wms-url``.

Docker Compose
--------------

A ``compose.yaml`` at the project root starts Neo4j and GeoServer as Docker
containers.  Maven's ``exec-maven-plugin`` runs ``docker compose up --detach
--wait --no-recreate neo4j geoserver`` during the ``initialize`` phase so both
services are available before the application starts.

.. code-block:: bash

    # Start services manually
    docker compose up --detach neo4j geoserver

Environment Variable Overrides
--------------------------------

Spring Boot maps environment variables to properties by uppercasing and
replacing ``.`` and ``-`` with ``_``.  For example:

.. code-block:: bash

    SPRING_NEO4J_URI=bolt://db.example.com:7687
    GEOSERVER_WMS_URL=http://geoserver.example.com/geoserver/wms
