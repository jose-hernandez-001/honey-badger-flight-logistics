Introduction
============

Honey Badger Flight Logistics is a Spring Boot reference application that demonstrates
how to build a production-ready REST API backed by a **Neo4j** graph database,
with real-time map rendering via **GeoServer** and inter-service communication
over **gRPC**.

The application manages *delivery routes* — sequences of geo-located waypoints
that a cargo aircraft must visit — and exposes them through a fully documented
OpenAPI 3 interface.

Features
--------

* **Delivery Route CRUD** — create, read, update, patch, and delete routes.
* **Waypoint management** — ordered waypoints per route with reordering support.
* **Graph persistence** — route and waypoint nodes stored in Neo4j with
  relationship-based ordering.
* **Map tiles** — a transparent caching proxy forwards WMS tile requests to
  GeoServer and stores tiles on disk for fast re-serving.
* **gRPC** — Protobuf-based service definitions are code-generated from the
  OpenAPI spec, ready for gRPC clients.
* **OpenAPI UI** — interactive docs served at ``/scalar.html`` (Scalar) and
  ``/redoc.html`` (Redoc).
* **Single-page frontend** — Bootstrap 5 UI for browsing routes on a Leaflet
  map.

Technology Stack
----------------

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Component
     - Technology
   * - Application framework
     - Spring Boot 4 / Java 21
   * - Database
     - Neo4j (Spring Data Neo4j)
   * - Map server
     - GeoServer (WMS)
   * - API style
     - REST (OpenAPI 3) + gRPC (Protobuf)
   * - Frontend
     - Bootstrap 5.3, Bootstrap Icons, Leaflet 1.9
   * - Build
     - Maven 3.9.14+

Licence
-------

This project is released under the `MIT Licence <https://opensource.org/licenses/MIT>`_.
