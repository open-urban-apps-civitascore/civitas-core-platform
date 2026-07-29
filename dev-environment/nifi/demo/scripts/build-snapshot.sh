#!/usr/bin/env bash
# Regenerate MQTT_TO_POSTGIS_demo.snapshot.json.
#
# Builds the flow in NiFi via REST (controller services, processors with
# RecordPath mapping, connections), downloads it as a snapshot, then deletes
# the temporary PG. Needs a running NiFi instance.
#
# Usage:
#   ./build-snapshot.sh                # writes to /tmp/nifi-scaffold/snapshot.json
#   ./build-snapshot.sh ./out.json     # writes to ./out.json

set -euo pipefail

BASE="${NIFI_BASE:-https://localhost:8443}"
# NiFi is secured with OIDC; obtain a token from Keycloak via the client-credentials grant.
KEYCLOAK_TOKEN_URL="${KEYCLOAK_TOKEN_URL:-http://localhost:8080/realms/civitas-core/protocol/openid-connect/token}"
CLIENT_ID="${NIFI_CLIENT_ID:-nifi}"
CLIENT_SECRET="${NIFI_CLIENT_SECRET:-nifi-dev-secret}"
ROOT="${NIFI_ROOT_PG:-4651c93d-019e-1000-3a1c-6a2d5a2dfea0}"
SNAPSHOT_OUT="${1:-/tmp/nifi-scaffold/snapshot.json}"
CLIENT="build-flow-$$"

TOKEN=$(curl -s -X POST "$KEYCLOAK_TOKEN_URL" \
  -d "grant_type=client_credentials" -d "client_id=$CLIENT_ID" -d "client_secret=$CLIENT_SECRET" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
H=(-H "Authorization: Bearer $TOKEN")

# call URL METHOD BODY  → echoes body on success, exits if non-2xx
call() {
  local method="$1" url="$2" body="${3:-}"
  local resp http
  if [[ -n "$body" ]]; then
    resp=$(curl -sk -w "\n%{http_code}" -X "$method" "$url" "${H[@]}" -H "Content-Type: application/json" -d "$body")
  else
    resp=$(curl -sk -w "\n%{http_code}" -X "$method" "$url" "${H[@]}")
  fi
  http=$(echo "$resp" | tail -n1)
  body_resp=$(echo "$resp" | sed '$d')
  if [[ ! "$http" =~ ^2 ]]; then
    echo "HTTP $http on $method $url" >&2
    echo "$body_resp" >&2
    exit 1
  fi
  echo "$body_resp"
}

id_of()  { python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"; }
ver_of() { python3 -c "import sys,json; print(json.load(sys.stdin)['revision']['version'])"; }

echo ">> create child PG" >&2
PG=$(call POST "$BASE/nifi-api/process-groups/$ROOT/process-groups" '{
  "revision":{"version":0,"clientId":"'"$CLIENT"'"},
  "component":{"name":"civitas-mqtt-postgis-demo","position":{"x":0,"y":0}}
}' | id_of)
echo "   PG=$PG" >&2

# ------------- Controller Services -------------

mk_cs() {
  local type="$1" name="$2" props_json="$3"
  call POST "$BASE/nifi-api/process-groups/$PG/controller-services" "{
    \"revision\":{\"version\":0,\"clientId\":\"$CLIENT\"},
    \"component\":{\"type\":\"$type\",\"name\":\"$name\",\"properties\":$props_json}
  }"
}

echo ">> create JsonTreeReader" >&2
READER=$(mk_cs "org.apache.nifi.json.JsonTreeReader" "JsonTreeReader" '{
  "Schema Access Strategy": "infer-schema"
}' | id_of)

echo ">> create JsonRecordSetWriter" >&2
WRITER=$(mk_cs "org.apache.nifi.json.JsonRecordSetWriter" "JsonRecordSetWriter" '{
  "Schema Write Strategy": "no-schema",
  "Schema Access Strategy": "inherit-record-schema",
  "Pretty Print JSON": "false",
  "Suppress Null Values": "never-suppress"
}' | id_of)

echo ">> create DBCPConnectionPool" >&2
DBCP=$(mk_cs "org.apache.nifi.dbcp.DBCPConnectionPool" "PostGISConnectionPool" '{
  "Database Connection URL": "jdbc:postgresql://civitas-nifi-demo-postgis:5432/nifi_demo",
  "Database Driver Class Name": "org.postgresql.Driver",
  "Database Driver Locations": "/opt/nifi/drivers/postgresql.jar",
  "Database User": "nifi",
  "Password": "nifi-demo-password"
}' | id_of)

echo ">> enable controller services" >&2
enable_cs() {
  local cs="$1"
  local cv
  cv=$(curl -sk "${H[@]}" "$BASE/nifi-api/controller-services/$cs" | ver_of)
  call PUT "$BASE/nifi-api/controller-services/$cs/run-status" "{
    \"revision\":{\"version\":$cv,\"clientId\":\"$CLIENT\"},
    \"state\":\"ENABLED\",
    \"disconnectedNodeAcknowledged\":false
  }" > /dev/null
}
enable_cs "$READER"
enable_cs "$WRITER"
enable_cs "$DBCP"
sleep 3
for cs in "$READER" "$WRITER" "$DBCP"; do
  state=$(curl -sk "${H[@]}" "$BASE/nifi-api/controller-services/$cs" \
          | python3 -c "import sys,json; print(json.load(sys.stdin)['component']['state'])")
  echo "   CS $cs : $state" >&2
done

# ------------- Processors -------------

mk_proc() {
  # config_json must include "autoTerminatedRelationships" (NiFi 2.x puts it inside config)
  local type="$1" name="$2" config_json="$3"
  call POST "$BASE/nifi-api/process-groups/$PG/processors" "{
    \"revision\":{\"version\":0,\"clientId\":\"$CLIENT\"},
    \"component\":{
      \"type\":\"$type\",\"name\":\"$name\",\"position\":{\"x\":0,\"y\":0},
      \"config\":$config_json
    }
  }"
}

echo ">> create ConsumeMQTT" >&2
MQTT=$(mk_proc "org.apache.nifi.processors.mqtt.ConsumeMQTT" "ConsumeMQTT" "{
  \"properties\": {
    \"Broker URI\": \"tcp://civitas-nifi-demo-mosquitto:1883\",
    \"Topic Filter\": \"sensors/+/temp\",
    \"Quality of Service\": \"0\",
    \"Client ID\": \"civitas-nifi-demo-consumer\",
    \"MQTT Specification Version\": \"0\",
    \"Session State\": \"true\",
    \"Session Expiry Interval\": \"24 hrs\",
    \"Add Attributes as Fields\": \"true\",
    \"Max Queue Size\": \"1024\"
  },
  \"autoTerminatedRelationships\": []
}" | id_of)
echo "   MQTT=$MQTT" >&2

echo ">> create ConvertRecord" >&2
CONV=$(mk_proc "org.apache.nifi.processors.standard.ConvertRecord" "ConvertRecord" "{
  \"properties\": {
    \"Record Reader\": \"$READER\",
    \"Record Writer\": \"$WRITER\",
    \"Include Zero Record FlowFiles\": \"true\"
  },
  \"autoTerminatedRelationships\": [\"failure\"]
}" | id_of)
echo "   CONV=$CONV" >&2

echo ">> create UpdateRecord (name=mapping)" >&2
UPD=$(mk_proc "org.apache.nifi.processors.standard.UpdateRecord" "mapping" "{
  \"properties\": {
    \"Record Reader\": \"$READER\",
    \"Record Writer\": \"$WRITER\",
    \"Replacement Value Strategy\": \"record-path-value\",
    \"/geom\": \"concat('POINT(', /lon, ' ', /lat, ')')\",
    \"/measurement_time\": \"toDate( /ts , \\\"yyyy-MM-dd'T'HH:mm:ss'Z'\\\")\",
    \"/temperature\": \"/temperature\",
    \"/station_id\": \"/station_id\"
  },
  \"autoTerminatedRelationships\": [\"failure\"]
}" | id_of)
echo "   UPD=$UPD" >&2

echo ">> create PutDatabaseRecord" >&2
PUT=$(mk_proc "org.apache.nifi.processors.standard.PutDatabaseRecord" "PutDatabaseRecord" "{
  \"properties\": {
    \"Record Reader\": \"$READER\",
    \"Database Connection Pooling Service\": \"$DBCP\",
    \"Statement Type\": \"INSERT\",
    \"Schema Name\": \"public\",
    \"Table Name\": \"sensor_observations\",
    \"Translate Field Names\": \"true\",
    \"Unmatched Field Behavior\": \"Ignore Unmatched Fields\",
    \"Unmatched Column Behavior\": \"Ignore Unmatched Columns\"
  },
  \"autoTerminatedRelationships\": [\"success\", \"failure\", \"retry\"]
}" | id_of)
echo "   PUT=$PUT" >&2

# ------------- Connections -------------

mk_conn() {
  local src="$1" dst="$2" rels_json="$3"
  call POST "$BASE/nifi-api/process-groups/$PG/connections" "{
    \"revision\":{\"version\":0,\"clientId\":\"$CLIENT\"},
    \"component\":{
      \"source\":{\"id\":\"$src\",\"groupId\":\"$PG\",\"type\":\"PROCESSOR\"},
      \"destination\":{\"id\":\"$dst\",\"groupId\":\"$PG\",\"type\":\"PROCESSOR\"},
      \"selectedRelationships\":$rels_json
    }
  }" > /dev/null
}
echo ">> create connections" >&2
mk_conn "$MQTT" "$CONV" '["Message"]'
mk_conn "$CONV" "$UPD"  '["success"]'
mk_conn "$UPD"  "$PUT"  '["success"]'

# ------------- Download snapshot -------------
echo ">> download snapshot" >&2
curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/$PG/download" > "$SNAPSHOT_OUT"
echo "   wrote $SNAPSHOT_OUT ($(wc -c < "$SNAPSHOT_OUT") bytes)" >&2

# ------------- Cleanup -------------
echo ">> cleanup: disable CSs + delete PG" >&2
for cs in "$READER" "$WRITER" "$DBCP"; do
  cv=$(curl -sk "${H[@]}" "$BASE/nifi-api/controller-services/$cs" | ver_of)
  call PUT "$BASE/nifi-api/controller-services/$cs/run-status" "{
    \"revision\":{\"version\":$cv,\"clientId\":\"$CLIENT\"},
    \"state\":\"DISABLED\",
    \"disconnectedNodeAcknowledged\":false
  }" > /dev/null
done
sleep 2
PV=$(curl -sk "${H[@]}" "$BASE/nifi-api/process-groups/$PG" | ver_of)
call DELETE "$BASE/nifi-api/process-groups/$PG?version=$PV&clientId=$CLIENT&disconnectedNodeAcknowledged=false" > /dev/null
echo "   PG deleted" >&2

# emit the PG ID so caller can grep for it if needed
echo "$PG"
