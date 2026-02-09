# Handoff: APISIX Config Adapter — Plugin Config + Service Architecture

**To**: Team 1 (Configuration Adapters)
**From**: InfoSec (AuthZ implementation)
**Date**: 2026-02-13
**Supersedes**: Previous handoff (2026-02-02) — `X-Authz-Backend` header via `proxy-rewrite` is no longer needed.

---

## Summary

APISIX now uses **Plugin Config** and **Services** to handle auth and authz.
The Config Adapter needs to set two IDs when creating routes:

| Field | Protected routes |
|-------|-----------------|
| `service_id` | Required |
| `plugin_config_id` | Required |

That's it. No per-route plugin configuration needed. Health checks go directly to backends (not through APISIX).

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│ Plugin Config (id: 1)                                   │
│   openid-connect (JWT validation)                       │
│   opa (authorization, with_service=true)                │
│   request-id (tracing)                                  │
└──────────────────────┬──────────────────────────────────┘
                       │ plugin_config_id: 1
                       ▼
┌──────────────────────────────────────┐
│ Route: /v2/*                         │
│   service_id: svc-portal-backend     │
│   plugin_config_id: 1  ← protected  │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ Service: svc-portal-backend          │
│   name: "portal-backend"  ← OPA     │
│   upstream: host:8089      reads     │
│                            this      │
└──────────────────────────────────────┘
```

Health checks go directly to backend (port 8089), not through APISIX.

**How OPA identifies the backend**: The OPA plugin is configured with `with_service: true`, which sends the APISIX Service object to OPA. OPA reads `input.service.name` to determine which backend's permission mappings to use. No headers or proxy-rewrite needed.

---

## What the Config Adapter Needs to Do

### 1. Create/manage APISIX Services (one per backend)

Each backend gets an APISIX Service. The **service name** is the backend identifier that OPA uses.

```bash
# Create a service via APISIX Admin API
curl http://127.0.0.1:9180/apisix/admin/services/svc-portal-backend \
  -H "X-API-KEY: $ADMIN_KEY" -X PUT -d '{
    "name": "portal-backend",
    "desc": "Portal Backend API Service",
    "upstream": {
        "type": "roundrobin",
        "nodes": {"portal-backend:8089": 1},
        "timeout": {"connect": 6, "send": 6, "read": 6}
    }
}'
```

**Service naming convention**:
| Backend | Service ID | Service name | OPA data file |
|---------|-----------|--------------|---------------|
| Portal Backend | `svc-portal-backend` | `portal-backend` | `backends/portal_backend/data.json` |
| FROST Server | `svc-frost-server` | `frost-server` | `backends/frost_server/data.json` |

The service name uses dashes. OPA converts dashes to underscores for the data file lookup (`portal-backend` → `portal_backend`).

### 2. Reference the Plugin Config on protected routes

The Plugin Config (id: `1`) contains openid-connect + OPA + request-id. Protected routes reference it.

```bash
# Create a protected route
curl http://127.0.0.1:9180/apisix/admin/routes/portal-backend-api \
  -H "X-API-KEY: $ADMIN_KEY" -X PUT -d '{
    "name": "Portal Backend API",
    "uri": "/v2/*",
    "service_id": "svc-portal-backend",
    "plugin_config_id": 1
}'
```

---

## Plugin Config Details

The Plugin Config is created once (e.g., during infrastructure setup, not per-route).

### Production config

```json
{
    "id": 1,
    "desc": "Standard auth + authz (openid-connect + OPA)",
    "plugins": {
        "openid-connect": {
            "client_id": "apisix-validator",
            "client_secret": "...",
            "discovery": "https://auth.civitas.io/realms/civitas-core/.well-known/openid-configuration",
            "bearer_only": true,
            "use_jwks": true,
            "ssl_verify": true,
            "set_userinfo_header": true
        },
        "opa": {
            "host": "http://opa:8181",
            "policy": "civitas/authz/decision",
            "with_route": true,
            "with_service": true,
            "with_consumer": false,
            "send_headers_upstream": ["X-Allowed-Scope-Ids"]
        },
        "request-id": {
            "include_in_response": true
        }
    }
}
```

**Key OPA settings**:
- `with_service: true` — Sends the Service object to OPA (this is how OPA identifies the backend)
- `with_route: true` — Sends Route metadata to OPA (for logging/debugging)
- `send_headers_upstream` — Forwards OPA's `X-Allowed-Scope-Ids` response header to the backend for collection filtering (M5.5)

### Dev vs. Production: JWT validation (openid-connect plugin)

Both dev and production use dynamic JWKS discovery — no static keys. The only differences are TLS and hostname:

| Setting | Dev | Production |
|---------|-----|------------|
| `discovery` | `http://civitas-keycloak:8080/...` | `https://auth.civitas.io/...` |
| `use_jwks` | `true` | `true` |
| `ssl_verify` | `false` (no TLS in dev) | `true` |
| Key rotation | Automatic via JWKS | Automatic via JWKS |

**Dev hostname convention**: All Keycloak access uses the `civitas-keycloak` hostname — both from Docker (via Docker DNS) and from the host (via `/etc/hosts: 127.0.0.1 civitas-keycloak`). This ensures the JWT `iss` claim matches everywhere, since Keycloak in dev mode uses the request hostname as the issuer.

See `dev-environment/apisix/apisix_conf/apisix.yaml` for the full openid-connect config with comments.

---

## APISIX Plugin Precedence

If a route needs to override a specific plugin from the Plugin Config:

**Precedence**: Consumer > Consumer Group > **Route** > **Plugin Config** > Service

Route-level plugins override Plugin Config plugins of the same name. To disable a plugin on a specific route:

```json
{
    "uri": "/v2/special-endpoint",
    "service_id": "svc-portal-backend",
    "plugin_config_id": 1,
    "plugins": {
        "openid-connect": {
            "_meta": {"disable": true}
        }
    }
}
```

---

## What Changed (vs. Previous Handoff)

| Before | After |
|--------|-------|
| Each route needed `proxy-rewrite` plugin to set `X-Authz-Backend` header | No per-route plugin config needed |
| Each route had inline openid-connect + opa plugin config | Routes reference shared `plugin_config_id: 1` |
| OPA read backend from `X-Authz-Backend` request header | OPA reads backend from `input.service.name` (APISIX metadata) |
| Backend identity passed as HTTP header | Backend identity from APISIX service config (not user-accessible) |

**Benefits**:
- Simpler route creation (just two IDs instead of full plugin config)
- Centralized auth/authz config (change once in Plugin Config, applies to all routes)
- Backend identity comes from infrastructure config, not a request header
- Public routes are just routes without `plugin_config_id`

---

## Testing

After creating a service and route:

```bash
# 1. Verify service exists
curl http://127.0.0.1:9180/apisix/admin/services/svc-portal-backend \
  -H "X-API-KEY: $ADMIN_KEY"

# 2. Verify route references service and plugin config
curl http://127.0.0.1:9180/apisix/admin/routes/portal-backend-api \
  -H "X-API-KEY: $ADMIN_KEY"

# 3. Test protected endpoint (should return 401 without token)
curl -v http://localhost:9080/v2/users
# Expected: 401 Unauthorized

# 4. Test with valid token
curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/v2/users
# Expected: 200 (if user has READ_USER permission) or 403 (if not)

# 5. Test health check (direct to backend, not through APISIX)
curl http://localhost:8089/actuator/health
# Expected: 200

# 6. Check OPA decision directly
curl -X POST http://localhost:8181/v1/data/civitas/authz/request_info \
  -d '{"input": {"request": {"method": "GET", "path": "/v2/users"}, "service": {"name": "portal-backend"}}}'
# Expected: "backend": "portal-backend"
```

---

## Checklist

- [ ] Config Adapter creates APISIX Services for each backend (with correct `name`)
- [ ] Protected routes set both `service_id` and `plugin_config_id: 1`
- [ ] Service names follow convention: lowercase with dashes (`portal-backend`, `frost-server`)
- [ ] Tested: protected endpoint returns 401 without token
- [ ] Tested: protected endpoint returns 200/403 with valid token
- [ ] Tested: health check works direct to backend (port 8089)

---

## Questions / Contact

If you have questions about this architecture, reach out to the InfoSec team working on AuthZ.
