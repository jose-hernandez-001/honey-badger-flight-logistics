# Honey Badger Flight Logistics

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Neo4j](https://img.shields.io/badge/Neo4j-Graph%20DB-008CC1?logo=neo4j&logoColor=white)](https://neo4j.com)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-3.0-6BA539?logo=openapiinitiative&logoColor=white)](https://www.openapis.org)
[![gRPC](https://img.shields.io/badge/gRPC-Protobuf-244c5a?logo=grpc)](https://grpc.io)

A Spring Boot reference application demonstrating delivery route and waypoint management with **Neo4j**, **GeoServer**,
and **gRPC**.

The application manages *delivery routes* — sequences of geo-located waypoints that a cargo aircraft must visit — and
exposes them through a fully documented OpenAPI 3 interface and a Bootstrap/Leaflet single-page frontend.

## Features

- **Delivery Route CRUD** — create, read, update, patch, and delete routes
- **Waypoint management** — ordered waypoints per route with reordering support
- **Graph persistence** — route and waypoint nodes stored in Neo4j with relationship-based ordering
- **Map tiles** — a transparent caching proxy forwards WMS tile requests to GeoServer and stores tiles on disk
- **gRPC** — Protobuf-based service definitions code-generated from the OpenAPI spec
- **OpenAPI UI** — interactive docs served at `/scalar.html` (Scalar) and `/redoc.html` (Redoc)
- **Single-page frontend** — Bootstrap 5 UI for browsing routes on a Leaflet map

## Technology Stack

| Component             | Technology                                  |
|-----------------------|---------------------------------------------|
| Application framework | Spring Boot 4 / Java 21                     |
| Database              | Neo4j (Spring Data Neo4j)                   |
| Map server            | GeoServer (WMS)                             |
| API style             | REST (OpenAPI 3) + gRPC (Protobuf)          |
| Frontend              | Bootstrap 5.3, Bootstrap Icons, Leaflet 1.9 |
| Build                 | Maven 3.9.14+                               |

## Prerequisites

- Java 21 (JDK)
- Maven 3.9.14+
- Docker (for Neo4j and GeoServer via Docker Compose)
- Python 3.10+ *(optional — only required to build the Sphinx documentation)*

## Getting Started

1. Clone the repository:

   ```bash
   git clone https://github.com/jose-hernandez-001/honey-badger-flight-logistics.git
   cd honey-badger-flight-logistics
   ```

2. Start infrastructure (recommended)

   It's recommended to start Neo4j and GeoServer first so the application can connect immediately. Run:

   ```bash
   docker compose up --detach neo4j geoserver
   ```

   Wait a few seconds for the services to become healthy (Neo4j Browser at http://localhost:7474).

3. Build and run the application

   You can start the application with Maven. Note: the Maven build contains an exec execution that can start Docker Compose automatically, but starting the services manually (step 2) is more reliable during development.

   ```bash
   ./mvnw clean package spring-boot:run
   ```

4. Open the application in a browser:

   ```
   http://localhost:8080
   ```

## Running Tests

```bash
./mvnw verify
```

This runs:

- JUnit 5 unit tests (`test` phase)
- Cucumber BDD integration tests (`integration-test` phase via Failsafe)
- JMeter performance tests (`integration-test` phase)

## Building and Running the JAR

```bash
./mvnw package -DskipTests
java -jar target/flight-logistics-0.0.1-SNAPSHOT.jar
```

## Docker Compose (Infrastructure)

Start Neo4j and GeoServer in the background:

```bash
docker compose up --detach neo4j geoserver
```

| Service         | URL                                  |
|-----------------|--------------------------------------|
| Application     | http://localhost:8080                |
| Neo4j Browser   | http://localhost:7474                |
| Neo4j Bolt      | bolt://localhost:7687                |
| GeoServer Admin | http://localhost:8600/geoserver/web/ |
| GeoServer WMS   | http://localhost:8600/geoserver/wms  |

Default Neo4j credentials: `neo4j` / `notverysecret`

### GeoServer Seed Data

To pre-download Natural Earth shapefiles for offline use:

```bash
sh geoserver/download-natural-earth.sh
```

To initialise GeoServer with the seed data (run once):

```bash
docker compose --profile init up geoserver-init
```

## Configuration

Key properties (overridable via environment variables):

| Environment variable                   | Description                                               |
|----------------------------------------|-----------------------------------------------------------|
| `SPRING_NEO4J_URI`                     | Bolt URI (e.g. `bolt://db:7687`)                          |
| `SPRING_NEO4J_AUTHENTICATION_PASSWORD` | Neo4j password                                            |
| `GEOSERVER_WMS_URL`                    | Full WMS URL (e.g. `http://geoserver:8600/geoserver/wms`) |

## Documentation

Full documentation is available under [docs/](docs/). To build the HTML docs:

```bash
pip install -r docs/requirements.txt
sphinx-build -b html docs target/docs/html
```

The built documentation is then available at `target/docs/html/index.html`.

## License

This project is licensed under the [MIT License](LICENSE).
