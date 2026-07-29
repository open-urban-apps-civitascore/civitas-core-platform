#!/usr/bin/env bash
# Tear down ALL demo PGs (handles duplicates from interrupted deploys).
# For each: stop processors, drop queued FlowFiles, poll until controller
# services are DISABLED, then DELETE. Idempotent — no PG = success.
#
# Usage:
#   ./cleanup.sh
# Optional env: NIFI_BASE, KEYCLOAK_TOKEN_URL, NIFI_CLIENT_ID, NIFI_CLIENT_SECRET, GROUP_NAME.

set -euo pipefail

BASE="${NIFI_BASE:-https://localhost:8443}"
# NiFi is secured with OIDC; obtain a token from Keycloak via the client-credentials grant.
KEYCLOAK_TOKEN_URL="${KEYCLOAK_TOKEN_URL:-http://localhost:8080/realms/civitas-core/protocol/openid-connect/token}"
CLIENT_ID="${NIFI_CLIENT_ID:-nifi}"
CLIENT_SECRET="${NIFI_CLIENT_SECRET:-nifi-dev-secret}"
GROUP_NAME="${GROUP_NAME:-civitas-mqtt-postgis-demo}"
CLIENT="cleanup-sh-$$"

TOKEN=$(curl -s -X POST "$KEYCLOAK_TOKEN_URL" \
  -d "grant_type=client_credentials" -d "client_id=$CLIENT_ID" -d "client_secret=$CLIENT_SECRET" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
H=(-H "Authorization: Bearer $TOKEN")

ROOT=$(curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/root" \
       | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

PGS=$(curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/$ROOT/process-groups" \
      | python3 -c "
import sys,json
for g in json.load(sys.stdin).get('processGroups', []):
    if g['component']['name'] == '$GROUP_NAME':
        print(g['id'])
")

if [[ -z "$PGS" ]]; then
  echo "No demo PG named '$GROUP_NAME' — nothing to clean up." >&2
  exit 0
fi

for PG in $PGS; do
  echo ">> cleanup PG $PG" >&2

  # Stop everything in the PG
  curl -sk -X PUT "$BASE/nifi-api/flow/process-groups/$PG" "${H[@]}" -H "Content-Type: application/json" \
    -d "{\"id\":\"$PG\",\"state\":\"STOPPED\"}" -o /dev/null -w "   stop: %{http_code}\n" >&2

  # Drop any queued FlowFiles (otherwise DELETE returns 409 "Queue not empty")
  for CONN in $(curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/$PG/connections" \
                | python3 -c "
import sys,json
for c in json.load(sys.stdin).get('connections', []):
    print(c['id'])
"); do
    DROP_RESP=$(curl -sk -X POST "$BASE/nifi-api/flowfile-queues/$CONN/drop-requests" "${H[@]}")
    DROP_ID=$(echo "$DROP_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['dropRequest']['id'])" 2>/dev/null || echo "")
    if [[ -n "$DROP_ID" ]]; then
      for _ in 1 2 3 4 5; do
        FIN=$(curl -sk "${H[@]}" "$BASE/nifi-api/flowfile-queues/$CONN/drop-requests/$DROP_ID" \
              | python3 -c "import sys,json; print(json.load(sys.stdin)['dropRequest']['finished'])" 2>/dev/null || echo "False")
        [[ "$FIN" == "True" ]] && break
        sleep 1
      done
      curl -sk -X DELETE "$BASE/nifi-api/flowfile-queues/$CONN/drop-requests/$DROP_ID" "${H[@]}" -o /dev/null
    fi
  done

  # Disable controller services (async — poll until all DISABLED)
  curl -sk -X PUT "$BASE/nifi-api/flow/process-groups/$PG/controller-services" "${H[@]}" -H "Content-Type: application/json" \
    -d "{\"id\":\"$PG\",\"state\":\"DISABLED\"}" -o /dev/null -w "   disable-request: %{http_code}\n" >&2
  for i in 1 2 3 4 5 6 7 8 9 10; do
    STATES=$(curl -sk "${H[@]}" "$BASE/nifi-api/flow/process-groups/$PG/controller-services" \
             | python3 -c "
import sys,json
print(' '.join(c['component']['state'] for c in json.load(sys.stdin).get('controllerServices',[])))
")
    case "$STATES" in
      *ENABLING*|*ENABLED*|*DISABLING*) sleep 1 ;;
      *) echo "   CSs all DISABLED (poll $i)" >&2; break ;;
    esac
  done

  # Finally delete the PG
  PV=$(curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/$PG" \
       | python3 -c "import sys,json; print(json.load(sys.stdin)['revision']['version'])")
  curl -sk -X DELETE "$BASE/nifi-api/process-groups/$PG?version=$PV&clientId=$CLIENT&disconnectedNodeAcknowledged=false" \
    "${H[@]}" -o /dev/null -w "   delete: %{http_code}\n" >&2
done

echo "Done." >&2
