#!/usr/bin/env bash
# Multipart-uploads MQTT_TO_POSTGIS_demo.snapshot.json to NiFi as a child PG.
#
# Bruno 3.3.0 strips the Authorization header from multipart-form requests, so
# the upload step in the demo lives here as a curl one-shot instead. Every
# other step is in the Bruno collection.
#
# Usage:
#   ./upload-snapshot.sh
# Optional env: NIFI_BASE, NIFI_USER, NIFI_PASS, GROUP_NAME.
# Prints the new PG id on stdout (suitable for `demoPgId` in the Bruno env).

set -euo pipefail

BASE="${NIFI_BASE:-https://localhost:8443}"
USER="${NIFI_USER:-admin}"
PASS="${NIFI_PASS:-nifi-dev-password-1234567890}"
GROUP_NAME="${GROUP_NAME:-civitas-mqtt-postgis-demo}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
SNAPSHOT="$SCRIPT_DIR/../MQTT_TO_POSTGIS_demo.snapshot.json"

TOKEN=$(curl -sk -X POST "$BASE/nifi-api/access/token" -d "username=$USER&password=$PASS")
ROOT=$(curl -sk -H "Authorization: Bearer $TOKEN" "$BASE/nifi-api/process-groups/root" \
       | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

curl -sk -X POST "$BASE/nifi-api/process-groups/$ROOT/process-groups/upload" \
     -H "Authorization: Bearer $TOKEN" \
     -F "id=$ROOT" \
     -F "groupName=$GROUP_NAME" \
     -F "positionX=200" \
     -F "positionY=200" \
     -F "clientId=upload-snapshot.sh" \
     -F "file=@$SNAPSHOT" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
