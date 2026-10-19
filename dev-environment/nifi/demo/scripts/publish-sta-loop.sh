#!/usr/bin/env bash
# Publish SensorThings records to MQTT for a FROST sink on the ThingTree port.
#
# A FROST sink without a Mapping writes every message as one record in the structure of its port.
# This script sends the ThingTree structure: a Thing with its Location, one Datastream with its
# Sensor and ObservedProperty, and one measurement. The port finds the Thing by
# properties.reference and the Datastream by its own properties.reference within that Thing,
# creates what is missing, and appends the measurement — so nothing has to be provisioned first.
#
# The sink node must have the port ThingTree.
#
# Usage:
#   ./publish-sta-loop.sh                       # every 2s, 3 stations, indefinite
#   COUNT=6 ./publish-sta-loop.sh               # 6 records then stop
#   INTERVAL=1 STATIONS=5 ./publish-sta-loop.sh
#   TOPIC=sensors/sta DATASTREAM_NAME=Temperature ./publish-sta-loop.sh
#
# Every station-N is the Thing "${REFERENCE_PREFIX}-N" with the Datastream "$DATASTREAM_NAME".

set -euo pipefail
# Force POSIX numeric locale so awk uses `.` (not the German `,`) as decimal separator.
export LC_ALL=C

INTERVAL="${INTERVAL:-2}"                       # seconds between records
STATIONS="${STATIONS:-3}"                       # rotate station-001 .. station-N
COUNT="${COUNT:-0}"                             # 0 = infinite
BROKER="${BROKER:-civitas-nifi-demo-mosquitto}" # container name; published via docker exec
TOPIC="${TOPIC:-sensors/sta}"                   # must match the DataSource's topic filter
REFERENCE_PREFIX="${REFERENCE_PREFIX:-sta-station}"
DATASTREAM_NAME="${DATASTREAM_NAME:-Temperature}"

echo "Publishing ThingTree records to '${TOPIC}' every ${INTERVAL}s across ${STATIONS} station(s)" \
     "$([ "$COUNT" -gt 0 ] && echo "(${COUNT} total)" || echo "(Ctrl+C to stop)")" >&2
echo "Thing reference: ${REFERENCE_PREFIX}-<n>, Datastream reference: ${DATASTREAM_NAME}" >&2

i=0
while true; do
  i=$((i + 1))
  n=$(( (i % STATIONS) + 1 ))
  ref=$(printf "%s-%03d" "$REFERENCE_PREFIX" "$n")
  # Lat 50..53, Lon 8..11, jittered per station so the stations are visibly apart on a map.
  lat=$(awk -v s="$n" 'BEGIN { printf "%.6f", 50.5 + s * 0.5 }')
  lon=$(awk -v s="$n" 'BEGIN { printf "%.6f", 8.5 + s * 0.7 }')
  temp=$(awk -v i="$i" 'BEGIN { printf "%.2f", 15 + ((i * 17) % 20) }')
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  # One record per message. properties.reference is the identity of the Thing and, within it, of
  # the Datastream; a second message for the same station finds both and appends the measurement.
  envelope=$(cat <<JSON
{
  "name": "STA Station ${ref}",
  "description": "Synthetic SensorThings station for pipeline testing",
  "properties": { "reference": "${ref}" },
  "Locations": [
    {
      "name": "Location of ${ref}",
      "description": "Synthetic location",
      "encodingType": "application/geo+json",
      "location": { "type": "Point", "coordinates": [${lon}, ${lat}] }
    }
  ],
  "Datastreams": [
    {
      "name": "${DATASTREAM_NAME} of ${ref}",
      "description": "Synthetic ${DATASTREAM_NAME} readings",
      "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
      "unitOfMeasurement": {
        "name": "degree Celsius",
        "symbol": "degC",
        "definition": "http://unitsofmeasure.org/ucum.html#para-30"
      },
      "properties": { "reference": "${DATASTREAM_NAME}" },
      "Sensor": {
        "name": "Synthetic sensor",
        "description": "Sensor of the demo stations",
        "encodingType": "text/plain",
        "metadata": "synthetic"
      },
      "ObservedProperty": {
        "name": "${DATASTREAM_NAME}",
        "definition": "http://dd.eionet.europa.eu/vocabulary/aq/meteoparameter/54",
        "description": "Air temperature"
      },
      "Observations": [
        { "phenomenonTime": "${ts}", "resultTime": "${ts}", "result": ${temp} }
      ]
    }
  ]
}
JSON
)

  docker exec -i "$BROKER" mosquitto_pub -h localhost -t "$TOPIC" -s <<<"$envelope"
  echo "  → ${ref}  result=${temp}  at ${ts}" >&2

  if [[ "$COUNT" -gt 0 && "$i" -ge "$COUNT" ]]; then break; fi
  sleep "$INTERVAL"
done
