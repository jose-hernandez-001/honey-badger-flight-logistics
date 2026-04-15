# Honey Badger Flight Logistics — Qt Client

A Qt 6 desktop application that connects to the Flight Logistics gRPC server
and displays routes on a GeoServer WMS map.

## Features

| Feature | Detail |
|---|---|
| Route list | Lists all routes with status colouring |
| Waypoint table | Shows sequence, coordinates, altitude, speed and heading |
| WMS map | Fetches live map tiles from the local GeoServer instance |
| Zoom / pan | Scroll-wheel zoom (centred on cursor), left-drag pan |
| Auto-zoom | Viewport automatically fits the selected route's waypoints |

## Prerequisites

### Runtime services

The Qt client connects to the same services started by `run.sh`:

| Service | Default address |
|---|---|
| gRPC server | `localhost:9090` |
| GeoServer WMS | `http://127.0.0.1:8600/geoserver/wms` |

Start both services before running the client:

```bash
cd ..           # back to project root
bash run.sh
```

### Build dependencies (Ubuntu / Debian)

```bash
sudo apt install \
    qt6-base-dev \
    qt6-declarative-dev \
    libqt6concurrent6 \
    libprotobuf-dev \
    protobuf-compiler \
    protobuf-compiler-grpc \
    libgrpc++-dev \
    cmake \
    ninja-build
```

On Fedora / RHEL:

```bash
sudo dnf install \
    qt6-qtbase-devel \
    protobuf-devel \
    grpc-devel \
    grpc-plugins \
    cmake ninja-build
```

## Build

```bash
cd qt-client
cmake -B build -G Ninja
cmake --build build -j$(nproc)
```

## Run

```bash
./build/flight-logistics-client
```

## Map layers

The map widget fetches these WMS layers from the `uk-flight` GeoServer
workspace (populated by `geoserver/init.sh`):

- `ne_10m_land` — land masses
- `ne_10m_admin_1_states_provinces` — UK constituent countries
- `ne_10m_admin_0_countries` — international boundaries
- `ne_10m_coastline` — coastlines

## Proto files

`proto/` contains copies of the protobuf definitions generated from the
OpenAPI specification at `src/main/resources/static/openapi.yaml`.
If the API changes, regenerate the Java protos with the Maven build and copy
the updated files from `target/generated-sources/proto/` into `proto/`.
