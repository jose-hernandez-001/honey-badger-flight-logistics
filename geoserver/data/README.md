# Local GeoServer Seed Data

This directory stores the Natural Earth ZIP packages used by `geoserver/init.sh`.

For a fully offline GeoServer bootstrap, prefetch the package before starting the app:

```sh
sh geoserver/download-natural-earth.sh
```

The init container mounts this directory at `/seed-data` and publishes any ZIPs found here into GeoServer.

Expected files:

- `ne_10m_land.zip`
- `ne_10m_coastline.zip`
- `ne_10m_admin_0_countries.zip`
- `ne_10m_admin_1_states_provinces.zip`
- `ne_10m_populated_places.zip`
- `ne_10m_airports.zip`