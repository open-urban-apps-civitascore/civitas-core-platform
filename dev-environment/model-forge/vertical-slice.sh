#!/usr/bin/env bash
#
# Vertical slice: create a schema via portal-backend (which persists it through the
# embedded Model Forge) and then browse it in the Model Forge Admin UI — with the
# Admin UI pointed at portal-backend's OWN database, so you see exactly what the
# backend created.
#
# This differs from local-demo.md, where the Admin UI runs against a SEPARATE database
# (model_forge_admin) and therefore never shows portal-backend's artifacts.
#
# Prerequisite: the platform stack must already be running, e.g.:
#   cd dev-environment && ./start-portal-dev.sh --authz=allowall --config-adapter=auto --backend=auto --frontend=auto
#
# Usage:
#   bash dev-environment/model-forge/vertical-slice.sh
#
# Everything is overridable via env vars (see the defaults below).

set -euo pipefail

# --- repo layout -------------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # civitas-core-platform
ADMIN_UI_DIR="$REPO_ROOT/model-forge/model-forge-admin-ui"

# --- endpoints / creds (match api/portal-backend/bruno-api/environments/local.bru) ---
BACKEND_URL="${BACKEND_URL:-http://localhost:8089}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${REALM:-civitas-core}"
CLIENT_ID="${CLIENT_ID:-portal-frontend}"
CLIENT_SECRET="${CLIENT_SECRET:-dev-only-portal-frontend-secret}"
# NB: not USERNAME/PASSWORD — Windows/Git Bash pre-sets $USERNAME (your login name),
# which would override the default and send the wrong user to Keycloak.
KC_USERNAME="${KC_USERNAME:-dev@civitas.local}"
KC_PASSWORD="${KC_PASSWORD:-dev123}"

# --- Admin UI, pointed at portal-backend's DB / model_forge schema -----------
ADMIN_UI_PORT="${ADMIN_UI_PORT:-8092}"   # 8090=Kafka UI, 8091=authz-repository — both taken by the stack
PORTAL_DB_URL="${PORTAL_DB_URL:-jdbc:postgresql://localhost:5432/portal_backend?sslmode=disable}"
PORTAL_DB_USER="${PORTAL_DB_USER:-admin}"
PORTAL_DB_PASS="${PORTAL_DB_PASS:-admin}"

# tiny JSON field extractor (uses node so no jq dependency is required)
json() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const v=JSON.parse(s);console.log(v[process.argv[1]]??"")}catch(e){process.exit(1)}})' "$1"; }

echo "==> 1/4  Checking portal-backend at $BACKEND_URL ..."
if ! curl -fsS "$BACKEND_URL/v1/actuator/health" >/dev/null 2>&1; then
  echo "    portal-backend is not reachable. Start the stack first:"
  echo "      cd $REPO_ROOT/dev-environment && ./start-portal-dev.sh --authz=allowall --config-adapter=auto --backend=auto --frontend=auto"
  exit 1
fi

echo "==> 2/4  Fetching an access token from Keycloak ($REALM) ..."
TOKEN=$(curl -fsS -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -d grant_type=password -d "client_id=$CLIENT_ID" -d "client_secret=$CLIENT_SECRET" \
  -d "username=$KC_USERNAME" -d "password=$KC_PASSWORD" | json access_token)
if [ -z "$TOKEN" ]; then echo "    token fetch failed (Keycloak up? creds correct?)"; exit 1; fi

echo "==> 3/4  Creating a DataStructure + schema version via portal-backend ..."
DS_ID=$(curl -fsS -X POST "$BACKEND_URL/v1/datastructures" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Vertical Slice DataStructure","description":"vertical-slice smoke test: portal-backend -> Model Forge","createdFromDataSource":false,"dataStructureVersionIds":[],"assignments":[]}' | json id)
echo "    DataStructure id = $DS_ID"

curl -fsS -X POST "$BACKEND_URL/v1/datastructures/$DS_ID/versions" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"dataStructureVersionSource":"OWN","version":"1.0.0","description":"Vertical-slice schema version","modelName":"VerticalSliceModel","model":{"$schema":"https://json-schema.org/draft/2020-12/schema","title":"VerticalSliceModel","type":"object","properties":{"name":{"type":"string"},"count":{"type":"integer"}}},"styles":{}}' >/dev/null
echo "    schema version 1.0.0 stored -> a Model Forge Element was persisted in the model_forge schema"

echo "==> 4/4  Starting the Model Forge Admin UI against portal-backend's database ..."
echo "    -> open  http://localhost:$ADMIN_UI_PORT   (look for 'VerticalSliceModel' under Elements)"
echo "    (Ctrl+C stops the Admin UI. The created artifact stays in the DB.)"
cd "$ADMIN_UI_DIR"
SERVER_PORT="$ADMIN_UI_PORT" \
SPRING_DATASOURCE_URL="$PORTAL_DB_URL" \
SPRING_DATASOURCE_USERNAME="$PORTAL_DB_USER" \
SPRING_DATASOURCE_PASSWORD="$PORTAL_DB_PASS" \
MODEL_FORGE_ADMIN_UI_SEED_ENABLED=false \
MODEL_FORGE_REGISTRY_MIGRATIONS_ENABLED=false \
exec mvn -q spring-boot:run
