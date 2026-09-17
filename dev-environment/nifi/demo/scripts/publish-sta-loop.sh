#!/usr/bin/env bash
# Publish OGC SensorThings (STA) envelopes to MQTT for a FROST sink in passthrough mode.
#
# A FROST sink fed by an MQTT source runs passthrough: no record mapping is involved and the
# SOURCE must deliver the STA envelope itself. The sink splits it into two independent legs:
#
#   $.things        → looked up by  properties.reference        → PATCH if found, else POST
#   $.observations  → the Datastream is looked up by
#                     parameters.reference + parameters.name (scoped to the dataset's project),
#                     its @iot.id is merged in, and the observation is POSTed to /Observations
#
# The Datastream is NOT created by the flow. An observation whose Datastream does not resolve is
# routed to the error sink, so provision it first with ./provision-sta-datastream.sh.
#
# Usage:
#   ./publish-sta-loop.sh                       # every 2s, 3 stations, indefinite
#   COUNT=6 ./publish-sta-loop.sh               # 6 envelopes then stop
#   INTERVAL=1 STATIONS=5 ./publish-sta-loop.sh
#   TOPIC=sensors/sta DATASTREAM_NAME=Temperature ./publish-sta-loop.sh
#
# Every station-N publishes observations against Datastream
#   properties.reference = "${REFERENCE_PREFIX}-N"   name = "$DATASTREAM_NAME"
# so provisioning and publishing agree on the lookup keys without further configuration.

set -euo pipefail
# Force POSIX numeric locale so awk uses `.` (not the German `,`) as decimal separator.
export LC_ALL=C

INTERVAL="${INTERVAL:-2}"                       # seconds between envelopes
STATIONS="${STATIONS:-3}"                       # rotate station-001 .. station-N
COUNT="${COUNT:-0}"                             # 0 = infinite
BROKER="${BROKER:-civitas-nifi-demo-mosquitto}" # container name; published via docker exec
TOPIC="${TOPIC:-sensors/sta}"                   # must match the DataSource's topic filter
REFERENCE_PREFIX="${REFERENCE_PREFIX:-sta-station}"
DATASTREAM_NAME="${DATASTREAM_NAME:-Temperature}"

echo "Publishing STA envelopes to '${TOPIC}' every ${INTERVAL}s across ${STATIONS} station(s)" \
     "$([ "$COUNT" -gt 0 ] && echo "(${COUNT} total)" || echo "(Ctrl+C to stop)")" >&2
echo "Datastream lookup keys: properties.reference=${REFERENCE_PREFIX}-<n>, name=${DATASTREAM_NAME}" >&2

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

  # `properties.reference` is the Thing's identity for the upsert; `parameters.reference`/`.name`
  # are the Datastream's lookup keys. Locations is a deep insert, so a first POST creates the
  # Thing with its Location in one call.
  envelope=$(cat <<JSON
{
  "things": [
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
      ]
    }
  ],
  "observations": [
    {
      "phenomenonTime": "${ts}",
      "resultTime": "${ts}",
      "result": ${temp},
      "parameters": { "reference": "${ref}", "name": "${DATASTREAM_NAME}" }
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
