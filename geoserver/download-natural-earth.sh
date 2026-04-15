#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
DATA_DIR="${DATA_DIR:-${SCRIPT_DIR}/data}"
NE_BASE="https://naturalearth.s3.amazonaws.com"

mkdir -p "${DATA_DIR}"

download_zip() {
  name="$1"
  url="$2"
  zip="${DATA_DIR}/${name}.zip"

  if [ -f "${zip}" ]; then
    echo "Already present: ${zip}"
    return 0
  fi

  echo "Downloading ${name}..."
  curl -sfL -o "${zip}" "${url}"
  echo "Saved ${zip}"
}

download_zip "ne_10m_land" \
  "${NE_BASE}/10m_physical/ne_10m_land.zip"
download_zip "ne_10m_coastline" \
  "${NE_BASE}/10m_physical/ne_10m_coastline.zip"
download_zip "ne_10m_admin_0_countries" \
  "${NE_BASE}/10m_cultural/ne_10m_admin_0_countries.zip"
download_zip "ne_10m_admin_1_states_provinces" \
  "${NE_BASE}/10m_cultural/ne_10m_admin_1_states_provinces.zip"
download_zip "ne_10m_populated_places" \
  "${NE_BASE}/10m_cultural/ne_10m_populated_places.zip"
download_zip "ne_10m_airports" \
  "${NE_BASE}/10m_cultural/ne_10m_airports.zip"

echo ""
echo "Local GeoServer Natural Earth package is ready in ${DATA_DIR}."