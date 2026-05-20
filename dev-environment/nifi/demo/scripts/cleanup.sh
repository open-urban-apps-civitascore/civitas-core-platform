#!/usr/bin/env bash
# Tear down the demo PG: stop, disable controller services, delete.
#
# Usage:
#   ./cleanup.sh
# Optional env: NIFI_BASE, NIFI_USER, NIFI_PASS, GROUP_NAME.

set -euo pipefail

BASE="${NIFI_BASE:-https://localhost:8443}"
USER="${NIFI_USER:-admin}"
PASS="${NIFI_PASS:-nifi-dev-password-1234567890}"
GROUP_NAME="${GROUP_NAME:-civitas-mqtt-postgis-demo}"
CLIENT="cleanup-sh-$$"

TOKEN=$(curl -sk -X POST "$BASE/nifi-api/access/token" -d "username=$USER&password=$PASS")
H=(-H "Authorization: Bearer $TOKEN")

ROOT=$(curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/root" \
       | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

PG=$(curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/$ROOT/process-groups" \
     | python3 -c "
import sys,json
for g in json.load(sys.stdin).get('processGroups', []):
    if g['component']['name'] == '$GROUP_NAME':
        print(g['id']); break
")
if [[ -z "$PG" ]]; then
  echo "No demo PG named '$GROUP_NAME' found." >&2
  exit 0
fi
echo ">> stop PG $PG" >&2
curl -sk -X PUT "$BASE/nifi-api/flow/process-groups/$PG" "${H[@]}" -H "Content-Type: application/json" \
  -d "{\"id\":\"$PG\",\"state\":\"STOPPED\"}" -o /dev/null -w "   stop: %{http_code}\n" >&2

echo ">> disable controller services" >&2
curl -sk -X PUT "$BASE/nifi-api/flow/process-groups/$PG/controller-services" "${H[@]}" -H "Content-Type: application/json" \
  -d "{\"id\":\"$PG\",\"state\":\"DISABLED\"}" -o /dev/null -w "   disable: %{http_code}\n" >&2
sleep 3

PV=$(curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/$PG" | python3 -c "import sys,json; print(json.load(sys.stdin)['revision']['version'])")
curl -sk -X DELETE "$BASE/nifi-api/process-groups/$PG?version=$PV&clientId=$CLIENT&disconnectedNodeAcknowledged=false" \
  "${H[@]}" -o /dev/null -w "   delete: %{http_code}\n" >&2
echo "Done." >&2
