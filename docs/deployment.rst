Deployment
==========

The application is packaged as an executable JAR and can be run anywhere Java
21 is available.

Building the JAR
----------------

.. code-block:: bash

    ./mvnw package -DskipTests

The output artifact is ``target/flight-logistics-<version>.jar``.

Running the JAR
---------------

.. code-block:: bash

    java -jar target/flight-logistics-0.0.1-SNAPSHOT.jar

Required environment variables (or ``application.yaml`` overrides):

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Variable
     - Description
   * - ``SPRING_NEO4J_URI``
     - Bolt URI for the Neo4j instance (e.g. ``bolt://db:7687``).
   * - ``SPRING_NEO4J_AUTHENTICATION_PASSWORD``
     - Neo4j password.
   * - ``GEOSERVER_WMS_URL``
     - Full WMS URL (e.g. ``http://geoserver:8600/geoserver/wms``).

Docker Compose (Development)
-----------------------------

The ``compose.yaml`` at the project root starts Neo4j and GeoServer:

.. code-block:: bash

    docker compose up --detach neo4j geoserver

Neo4j is available at:

* **Bolt**: ``bolt://localhost:7687``
* **Browser**: ``http://localhost:7474``

GeoServer is available at:

* **WMS**: ``http://localhost:8600/geoserver/wms``
* **Admin**: ``http://localhost:8600/geoserver/web/``

GeoServer Seed Data
-------------------

Run ``geoserver/init.sh`` the first time to provision the Natural Earth
layers.  This script calls ``geoserver/download-natural-earth.sh`` to fetch
the shapefiles and then publishes them via the GeoServer REST API.

.. code-block:: bash

    bash geoserver/init.sh

Port Reference
--------------

.. list-table::
   :header-rows: 1
   :widths: 20 20 60

   * - Service
     - Port
     - Notes
   * - Flight Logistics
     - 8080
     - Spring Boot HTTP server.
   * - Flight Logistics gRPC
     - 9090
     - Spring gRPC server.
   * - Neo4j Bolt
     - 7687
     - Graph database query interface.
   * - Neo4j Browser
     - 7474
     - Neo4j web console.
   * - GeoServer
     - 8600
     - WMS tile service + admin UI.

Health Check
------------

Spring Boot Actuator exposes health information at:

.. code-block:: text

    GET http://localhost:8080/actuator/health
