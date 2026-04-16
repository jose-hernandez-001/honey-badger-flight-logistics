Development Guide
=================

Prerequisites
-------------

* Java 21 (JDK)
* Maven 3.9.14+
* Docker (for Neo4j and GeoServer via Docker Compose)
* Python 3.10+ (optional — only required to build the Sphinx documentation)

Getting Started
---------------

1. Clone the repository:

   .. code-block:: bash

       git clone https://github.com/doraemoncito/neo4j-flight-logistics.git
       cd neo4j-flight-logistics

2. Build and run the application (Neo4j and GeoServer start automatically via
   Docker Compose):

   .. code-block:: bash

       ./mvnw spring-boot:run

3. Open the application in a browser:

   .. code-block:: text

       http://localhost:8080

Running Tests
-------------

Unit and integration tests:

.. code-block:: bash

    ./mvnw verify

This runs:

* JUnit 5 unit tests (``test`` phase).
* Cucumber BDD integration tests (``integration-test`` phase via Failsafe).
* JMeter performance tests (``integration-test`` phase).

Checking for Outdated Dependencies
------------------------------------

.. code-block:: bash

    ./mvnw versions:display-dependency-updates
    ./mvnw versions:display-plugin-updates

Checking Licence Headers
-------------------------

.. code-block:: bash

    ./mvnw license:check

To add missing headers automatically:

.. code-block:: bash

    ./mvnw license:format

Code Style
----------

The project uses the Google Java Style Guide.  Lombok is used for boilerplate
reduction; do not add hand-written getters, setters, or constructors where a
Lombok annotation suffices.

OpenAPI Code Generation
-----------------------

The Spring MVC delegate interfaces and model classes are generated from
``src/main/resources/static/openapi.yaml`` during the ``initialize`` phase.
Do **not** edit generated sources under ``target/generated-sources/``.

To add a new endpoint:

1. Add the path and schema to ``openapi.yaml``.
2. Run ``./mvnw generate-sources`` to regenerate.
3. Implement the new delegate method in the appropriate
   ``*ApiDelegateImpl`` class.

Protobuf / gRPC
---------------

Protobuf schemas are generated from the OpenAPI spec using
``openapi-generator`` (``protobuf-schema`` generator) and then compiled with
``protobuf-maven-plugin``.  Generated stubs live in
``target/generated-sources/protobuf/``.

Building the Documentation
--------------------------

Documentation is built with Sphinx and requires the packages listed in
``docs/requirements.txt``.

.. code-block:: bash

    # Install dependencies (first time only)
    pip install -r docs/requirements.txt

    # Build HTML docs via Maven (outputs to target/docs/html/)
    ./mvnw site -P docs

    # Or build directly with sphinx-build
    sphinx-build -b html docs target/docs/html

Project Layout
--------------

.. code-block:: text

    .
    ├── compose.yaml                  # Docker Compose for Neo4j + GeoServer
    ├── docs/                         # Sphinx documentation source
    ├── geoserver/                    # GeoServer seed data and init scripts
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/honeybadger/flightlogistics/
        │   │   ├── api/              # OpenAPI delegate implementations
        │   │   ├── controller/       # HomeController, GeoServerWmsProxy
        │   │   ├── domain/           # Neo4j entities and RouteStatus
        │   │   ├── repository/       # Spring Data Neo4j repositories
        │   │   └── service/          # Business logic
        │   └── resources/
        │       ├── application.yaml
        │       └── static/           # Frontend HTML, CSS, JS
        └── test/
            ├── java/                 # Unit tests + BDD step definitions
            └── resources/features/  # Cucumber feature files
