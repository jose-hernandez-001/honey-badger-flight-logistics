#!/usr/bin/env bash
#
# MIT License
#
# Copyright (c) 2026 José Hernández
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
set -euo pipefail

echo "Honey Badger Flight Logistics"
echo "Copyright (c) 2026 José Hernández. All rights reserved."
echo ""

# Determine the directory of the script and the path to the JAR file
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="${SCRIPT_DIR}/target/flight-logistics-0.0.1-SNAPSHOT.jar"

# Check if the JAR file exists
if [[ ! -f "${JAR}" ]]; then
  echo "ERROR: JAR not found at ${JAR}" >&2
  echo "Run 'mvn package' first." >&2
  exit 1
fi

# Allow users to set JAVA_OPTS and SPRING_OPTS via environment variables
JAVA_OPTS="${JAVA_OPTS:-}"
SPRING_OPTS="${SPRING_OPTS:-}"

# Disable Ctrl-C echoing to prevent terminal issues after exit
stty -echoctl
trap 'stty echoctl' EXIT

# Start long-running Docker Compose services (idempotent — safe to run when already up)
echo "Starting Docker Compose services..."
docker compose up --detach --wait --no-recreate neo4j geoserver

# Run GeoServer data initialisation if not already done (profile: init)
echo "Running GeoServer initialisation (skipped if already complete)..."
if [[ ! -f "${SCRIPT_DIR}/geoserver/data/ne_10m_land.zip" ]]; then
  echo "GeoServer local map package not found under ${SCRIPT_DIR}/geoserver/data/."
  echo "The init container will download and cache the Natural Earth dataset on first run."
  echo "For a fully offline setup, run: sh geoserver/download-natural-earth.sh"
fi
docker compose --profile init up --no-recreate geoserver-init

# Execute the application with the specified options
exec java ${JAVA_OPTS} -jar "${JAR}" ${SPRING_OPTS} "$@"
