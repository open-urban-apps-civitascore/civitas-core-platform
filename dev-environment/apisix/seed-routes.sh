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
# openid-connect session secret. With bearer_only=false, APISIX's openid-connect plugin DOES process
# session cookies, so this is a real authentication credential, NOT an inert schema value: anyone who
# knows it can mint session state that the gateway trusts. Therefore there is NO committed default —
# if not injected, a fresh RANDOM secret is generated per run (unguessable, never a known constant).
# PRODUCTION MUST inject a stable, strong OIDC_SESSION_SECRET (e.g. from a Kubernetes Secret).
OIDC_SESSION_SECRET="${OIDC_SESSION_SECRET:-$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')}"
if [ -z "$OIDC_SESSION_SECRET" ]; then
  echo "FATAL: could not derive an OIDC_SESSION_SECRET (inject one explicitly)" >&2
  exit 1
fi
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

# 2. Create service: frost-server
# Dummy upstream is required so APISIX resolves the service object and passes
# it to plugins (OPA uses input.service.name to identify the backend).
# Dynamic routes bring their own upstream_id; this upstream is never actually hit.
echo "Creating service: svc-frost-server..."
curl -sf -X PUT "$ADMIN_URL/apisix/admin/services/svc-frost-server" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "frost-server",
    "desc": "FROST Server (SensorThings API) - metadata for OPA backend identification",
    "upstream": {
      "type": "roundrobin",
      "nodes": { "127.0.0.1:1": 1 }
    }
  }' > /dev/null && echo " OK" || echo " FAILED"

# 3. Create plugin config
if [ "$MODE" = "allowall" ]; then
  echo "Creating plugin config: allow-all (wildcard scope)..."
  # Wildcard scope short-circuits pool filtering in the backend, but strip a
  # client-supplied X-Allowed-Pool-Ids anyway for parity/defense-in-depth.
  PROXY_REWRITE='{
    "headers": {
      "set": {
        "X-Allowed-Scope-Ids": "*"
      },
      "remove": ["X-Allowed-Pool-Ids"]
    }
  }'
  # In allow-all mode the rego policy's null-permission branch (main.rego, rule 1,
  # "authenticated_endpoint") returns {allow: true} with no "headers" field. The opa plugin
  # manages any header named in send_headers_upstream against its OWN decision response — with no
  # "headers" field there, it clears those header names from the upstream request, silently
  # undoing proxy-rewrite's wildcard "set" above (proxy-rewrite runs in an earlier phase, so its
  # header IS present when opa's plugin runs, but opa scrubs it before proxying upstream). Omit
  # send_headers_upstream here (it requires >=1 items, so it can't just be emptied) so
  # proxy-rewrite's wildcard is the only thing touching these headers.
  OPA_SEND_HEADERS_UPSTREAM_JSON=""
else
  echo "Creating plugin config: full authz (OPA scope filtering)..."
  PROXY_REWRITE='{
    "headers": {
      "remove": ["X-Allowed-Scope-Ids", "X-Allowed-Pool-Ids"]
    }
  }'
  OPA_SEND_HEADERS_UPSTREAM_JSON='"send_headers_upstream": ["X-Allowed-Scope-Ids", "X-Allowed-Pool-Ids"],'
fi

curl -sf -X PUT "$ADMIN_URL/apisix/admin/plugin_configs/1" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"desc\": \"Standard auth + authz (openid-connect + OPA)\",
    \"plugins\": {
      \"serverless-pre-function\": {
        \"phase\": \"rewrite\",
        \"functions\": [
          \"return function(conf, ctx) ngx.req.clear_header('X-Userinfo'); ngx.req.clear_header('X-Access-Token'); ngx.req.clear_header('X-Id-Token') end\",
          \"return function() local uri = ngx.var.uri; if uri and ngx.re.find(uri, '^/v1/datasets/[^/]+/[^/]+/\$', 'jo') then ngx.req.set_uri(string.sub(uri, 1, -2), false) end end\"
        ]
      },
      \"openid-connect\": {
        \"client_id\": \"apisix-validator\",
        \"client_secret\": \"unused-for-jwks-validation\",
        \"discovery\": \"http://civitas-keycloak:8080/realms/civitas-core/.well-known/openid-configuration\",
        \"bearer_only\": false,
        \"unauth_action\": \"pass\",
        \"access_token_in_authorization_header\": true,
        \"session\": { \"secret\": \"$OIDC_SESSION_SECRET\" },
        \"use_jwks\": true,
        \"ssl_verify\": false,
        \"set_userinfo_header\": true
      },
      \"opa\": {
        \"host\": \"http://civitas-opa:8181\",
        \"policy\": \"civitas/authz/decision\",
        $OPA_SEND_HEADERS_UPSTREAM_JSON
        \"with_route\": true,
        \"with_service\": true,
        \"with_consumer\": false
      },
      \"proxy-rewrite\": $PROXY_REWRITE,
      \"request-id\": {
        \"include_in_response\": true
      }
    }
  }" > /dev/null && echo " OK" || echo " FAILED"

# 4. Create route: portal-backend API
# Host-agnostic management API catch-all (issue #1368): the frontend reaches it on
# localhost:9080. The per-named-API FROST proxies live under api.localhost/v1/datasets/{id}/{slug}
# (one route per slug) and are created dynamically by the APISIX saga handler — they match a more specific URI and
# therefore wins over this /v1/* catch-all on the API vhost.
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
