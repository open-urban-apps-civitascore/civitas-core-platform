#!/usr/bin/env bash
# Continuously publish synthetic sensor readings to MQTT until Ctrl+C.
# Varies station_id, lat/lon, temperature, and timestamp — good for live-demoing
# the pipeline (rows appear in PostGIS in real time).
#
# Usage:
#   ./publish-loop.sh                # default: every 2s, 5 stations, indefinite
#   INTERVAL=1 STATIONS=10 ./publish-loop.sh
#   COUNT=20 ./publish-loop.sh       # publish 20 messages then stop
#   MQTT_VERSION=mqttv5 ./publish-loop.sh   # publish as MQTT 5 (mqttv31/mqttv311/mqttv5)

set -euo pipefail
# Force POSIX numeric locale so awk uses `.` (not the German `,`) as decimal sep.
export LC_ALL=C

INTERVAL="${INTERVAL:-2}"          # seconds between publishes
STATIONS="${STATIONS:-5}"          # rotate station-001 .. station-N
COUNT="${COUNT:-0}"                # 0 = infinite
BROKER="${BROKER:-civitas-nifi-demo-mosquitto}"
MQTT_VERSION="${MQTT_VERSION:-}"   # mqttv31 | mqttv311 | mqttv5, empty = mosquitto_pub default (3.1.1)

echo "Publishing every ${INTERVAL}s across ${STATIONS} stations" \
     "$([ "$COUNT" -gt 0 ] && echo "(${COUNT} messages total)" || echo "(Ctrl+C to stop)")" >&2

i=0
while true; do
  i=$((i + 1))
  station=$(printf "sensor-%03d" $(( (i % STATIONS) + 1 )))
  # Lat in 50..53, Lon in 8..11, jittered per station for visible spread
  lat=$(awk -v s=$(( i % STATIONS )) 'BEGIN { printf "%.3f", 50.5 + s * 0.5 }')
  lon=$(awk -v s=$(( i % STATIONS )) 'BEGIN { printf "%.3f", 8.5 + s * 0.7 }')
  temp=$(awk -v i=$i 'BEGIN { printf "%.2f", 15 + ((i * 17) % 20) }')
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  msg=$(printf '{"lat":%s,"lon":%s,"temperature":%s,"ts":"%s","station_id":"%s"}' \
        "$lat" "$lon" "$temp" "$ts" "$station")

  if [[ -n "$MQTT_VERSION" ]]; then
    docker exec "$BROKER" mosquitto_pub -h localhost -V "$MQTT_VERSION" -t "sensors/${station}/temp" -m "$msg"
  else
    docker exec "$BROKER" mosquitto_pub -h localhost -t "sensors/${station}/temp" -m "$msg"
  fi
  echo "  → $station  $msg" >&2

  if [[ "$COUNT" -gt 0 && "$i" -ge "$COUNT" ]]; then break; fi
  sleep "$INTERVAL"
done
