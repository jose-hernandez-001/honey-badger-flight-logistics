#!/bin/sh
# GeoServer UK demo data initialisation.
# Idempotent: exits early if the workspace already exists.
set -eu

GEOSERVER_URL="${GEOSERVER_URL:-http://geoserver:8080/geoserver}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-geoserver}"
DATA_DIR="${DATA_DIR:-/seed-data}"
WORKSPACE="uk-flight"
NE_BASE="https://naturalearth.s3.amazonaws.com"

mkdir -p "${DATA_DIR}"

# ---------------------------------------------------------------------------
# Skip if already initialised
# ---------------------------------------------------------------------------
if curl -sf -u "${ADMIN_USER}:${ADMIN_PASS}" \
     "${GEOSERVER_URL}/rest/workspaces/${WORKSPACE}.json" -o /dev/null 2>&1; then
  echo "Workspace '${WORKSPACE}' already exists — skipping initialisation."
  exit 0
fi

# ---------------------------------------------------------------------------
# Create workspace
# ---------------------------------------------------------------------------
echo "Creating workspace '${WORKSPACE}'..."
curl -sf -u "${ADMIN_USER}:${ADMIN_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"workspace\":{\"name\":\"${WORKSPACE}\"}}" \
  "${GEOSERVER_URL}/rest/workspaces"
echo

# ---------------------------------------------------------------------------
# Helper: use a local Natural Earth zip when present, otherwise download and
# persist it to the mounted seed-data directory for future offline runs.
# ---------------------------------------------------------------------------
ensure_zip() {
  local name="$1"
  local url="$2"
  local zip="${DATA_DIR}/${name}.zip"

  if [ -f "${zip}" ]; then
    echo "Using local package ${zip}"
    return 0
  fi

  echo "Downloading ${name} into ${zip}..."
  if ! curl -sfL -o "${zip}" "${url}"; then
    rm -f "${zip}"
    echo "ERROR: could not obtain ${name}. Download the local package first with sh geoserver/download-natural-earth.sh" >&2
    exit 1
  fi
}

publish_layer() {
  local name="$1"
  local url="$2"
  local zip="${DATA_DIR}/${name}.zip"

  ensure_zip "${name}" "${url}"

  echo "Publishing ${name} to GeoServer..."
  curl -sf -u "${ADMIN_USER}:${ADMIN_PASS}" \
    -H "Content-Type: application/zip" \
    --data-binary "@${zip}" \
    -X PUT \
    "${GEOSERVER_URL}/rest/workspaces/${WORKSPACE}/datastores/${name}/file.shp"

  echo "  → ${name} published."
}

# ---------------------------------------------------------------------------
# Natural Earth layers (10 m resolution)
# ---------------------------------------------------------------------------

# Physical: land masses (ocean background context)
publish_layer "ne_10m_land" \
  "${NE_BASE}/10m_physical/ne_10m_land.zip"

# Physical: coastline
publish_layer "ne_10m_coastline" \
  "${NE_BASE}/10m_physical/ne_10m_coastline.zip"

# Cultural: sovereign country boundaries (shows UK outline)
publish_layer "ne_10m_admin_0_countries" \
  "${NE_BASE}/10m_cultural/ne_10m_admin_0_countries.zip"

# Cultural: first-level subdivisions (England / Scotland / Wales / N.Ireland)
publish_layer "ne_10m_admin_1_states_provinces" \
  "${NE_BASE}/10m_cultural/ne_10m_admin_1_states_provinces.zip"

# Cultural: populated places (cities / towns for labelling)
publish_layer "ne_10m_populated_places" \
  "${NE_BASE}/10m_cultural/ne_10m_populated_places.zip"

# Cultural: airports
publish_layer "ne_10m_airports" \
  "${NE_BASE}/10m_cultural/ne_10m_airports.zip"

echo ""
echo "======================================================"
echo " GeoServer UK demo data initialisation complete."
echo " Workspace : ${WORKSPACE}"
echo " Preview   : ${GEOSERVER_URL}/web/"
echo "======================================================"
