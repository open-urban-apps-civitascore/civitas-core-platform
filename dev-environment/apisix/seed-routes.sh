#!/bin/bash
# =============================================================================
# Seed APISIX routes via Admin API
# =============================================================================
# Creates the static portal-backend route, service, and plugin config.
# Run after APISIX has started in traditional mode.
#
# Usage: ./seed-routes.sh [--allowall]
#   --allowall  Injects X-Allowed-Scope-Ids: * (dev shortcut, skips OPA scoping)
#
# Idempotent: uses PUT with fixed IDs, safe to re-run.

ADMIN_URL="${APISIX_ADMIN_URL:-http://localhost:9180}"
ADMIN_KEY="${APISIX_ADMIN_KEY:-edd1c9f034335f136f87ad84b625c8f1}"
MODE="authz"

if [ "$1" = "--allowall" ]; then
  MODE="allowall"
fi

echo "Seeding APISIX routes (mode: $MODE)..."

# Wait for Admin API to be ready
for i in $(seq 1 30); do
  if curl -sf -o /dev/null "$ADMIN_URL/apisix/admin/routes" -H "X-API-KEY: $ADMIN_KEY" 2>/dev/null; then
    echo "APISIX Admin API ready."
    break
  fi
  echo "Waiting for APISIX Admin API... ($i/30)"
  sleep 2
done

# 1. Create service: portal-backend
echo "Creating service: svc-portal-backend..."
curl -sf -X PUT "$ADMIN_URL/apisix/admin/services/svc-portal-backend" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "portal-backend",
    "desc": "Portal Backend API Service",
    "upstream": {
      "type": "roundrobin",
      "nodes": {
        "portal-backend:8089": 1
      },
      "timeout": {
        "connect": 6,
        "send": 6,
        "read": 6
      }
    }
  }' > /dev/null && echo " OK" || echo " FAILED"

# 2. Create plugin config
if [ "$MODE" = "allowall" ]; then
  echo "Creating plugin config: allow-all (wildcard scope)..."
  PROXY_REWRITE='{
    "headers": {
      "set": {
        "X-Allowed-Scope-Ids": "*"
      }
    }
  }'
else
  echo "Creating plugin config: full authz (OPA scope filtering)..."
  PROXY_REWRITE='{
    "headers": {
      "remove": ["X-Allowed-Scope-Ids"]
    }
  }'
fi

curl -sf -X PUT "$ADMIN_URL/apisix/admin/plugin_configs/1" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"desc\": \"Standard auth + authz (openid-connect + OPA)\",
    \"plugins\": {
      \"openid-connect\": {
        \"client_id\": \"apisix-validator\",
        \"client_secret\": \"unused-for-jwks-validation\",
        \"discovery\": \"http://civitas-keycloak:8080/realms/civitas-core/.well-known/openid-configuration\",
        \"bearer_only\": true,
        \"use_jwks\": true,
        \"ssl_verify\": false,
        \"set_userinfo_header\": true
      },
      \"opa\": {
        \"host\": \"http://civitas-opa:8181\",
        \"policy\": \"civitas/authz/decision\",
        \"with_route\": true,
        \"with_service\": true,
        \"with_consumer\": false,
        \"send_headers_upstream\": [\"X-Allowed-Scope-Ids\"]
      },
      \"proxy-rewrite\": $PROXY_REWRITE,
      \"request-id\": {
        \"include_in_response\": true
      }
    }
  }" > /dev/null && echo " OK" || echo " FAILED"

# 3. Create route: portal-backend API
echo "Creating route: portal-backend-api (/v1/*)..."
curl -sf -X PUT "$ADMIN_URL/apisix/admin/routes/portal-backend-api" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Portal Backend API",
    "uri": "/v1/*",
    "service_id": "svc-portal-backend",
    "plugin_config_id": "1",
    "status": 1
  }' > /dev/null && echo " OK" || echo " FAILED"

echo "APISIX route seeding complete."
