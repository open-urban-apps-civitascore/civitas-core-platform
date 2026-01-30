# Handoff: APISIX Config Adapter - X-Authz-Backend Header

**To**: Team 1 (Configuration Adapters)
**From**: InfoSec (AuthZ implementation)
**Date**: 2026-02-02
**Priority**: Required for M5 (Full AuthZ Integration)

---

## Summary

The APISIX Config Adapter must set an `X-Authz-Backend` header on each route it creates. This header tells OPA which backend's permission mappings to use for authorization decisions.

---

## Requirement

When the Config Adapter creates or updates an APISIX route, it must include the `proxy-rewrite` plugin configuration to set the `X-Authz-Backend` header.

### Header Specification

| Header | Value | Description |
|--------|-------|-------------|
| `X-Authz-Backend` | Backend identifier (e.g., `portal-backend`, `frost-server`) | Identifies which permission mappings file OPA should use |

### Backend Identifiers

| Backend | Header Value | Mappings File |
|---------|--------------|---------------|
| Portal Backend | `portal-backend` | `authz/rego/data/backends/portal_backend.json` |
| FROST Server | `frost-server` | `authz/rego/data/backends/frost_server.json` (TBD) |

---

## APISIX Route Configuration

The Config Adapter should generate route configurations like this:

```yaml
routes:
  - id: portal-backend-api
    uri: /v2/*
    upstream:
      nodes:
        "portal-backend:8089": 1
    plugins:
      # REQUIRED: Set backend identifier for OPA
      proxy-rewrite:
        headers:
          set:
            X-Authz-Backend: portal-backend

      # JWT validation (existing)
      openid-connect:
        # ... existing config

      # OPA authorization (M5)
      opa:
        # ... to be configured in M5
```

### Key Points

1. **Header must be set before OPA plugin runs** - The `proxy-rewrite` plugin sets request headers that OPA can read
2. **One header value per route** - Each route should have exactly one backend identifier
3. **Value must match data file naming** - `portal-backend` → `portal_backend` (OPA converts dashes to underscores)

---

## Why This Is Needed

### Architecture Context

```
Request → APISIX → [proxy-rewrite sets X-Authz-Backend] → OPA → Backend
                                                           ↓
                                                   Reads permission mappings
                                                   from data/backends/{backend}.json
```

OPA needs to know which backend a request is destined for so it can:
1. Load the correct permission mappings (each backend has different endpoints)
2. Validate that the path exists in that backend's configuration
3. Look up the required permission for the specific endpoint + HTTP method

### Multi-Backend Support

CIVITAS CORE will have multiple backends (Portal, FROST, potentially others). Each backend:
- Has different URL patterns
- Requires different permissions
- May have different public endpoints

The `X-Authz-Backend` header is the contract between APISIX (routing) and OPA (authorization).

---

## Implementation Notes

### For New Routes

When creating a new route via the Config Adapter:

```json
{
  "plugins": {
    "proxy-rewrite": {
      "headers": {
        "set": {
          "X-Authz-Backend": "<backend-identifier>"
        }
      }
    }
  }
}
```

### For Existing Routes

Existing routes without the header will fail authorization with `unknown_backend` error.

### Backend Identifier Convention

- Use lowercase with dashes: `portal-backend`, `frost-server`
- OPA internally converts to underscores for data file lookup
- Must match the key in the backend's JSON file (e.g., `"portal_backend": {...}`)

---

## Testing

After implementation, verify with:

```bash
# Send request through APISIX
curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/v2/users

# Check OPA decision includes backend
curl -X POST http://localhost:8181/v1/data/civitas/authz/request_info \
  -d '{"input": {"request": {"method": "GET", "path": "/v2/users", "headers": {"x-authz-backend": "portal-backend"}}}}'
```

Expected: `"backend": "portal-backend"` in response.

---

## Current Dev Environment Config

For reference, the dev environment APISIX config already includes this header:

**File**: `dev-environment/apisix/apisix_conf/apisix.yaml`

```yaml
- id: portal-backend-api
  uri: /v2/*
  plugins:
    proxy-rewrite:
      headers:
        set:
          X-Authz-Backend: portal-backend
```

---

## Questions / Contact

If you have questions about this requirement, please reach out to the InfoSec team working on AuthZ.

---

## Checklist

- [ ] Config Adapter sets `X-Authz-Backend` header on all routes
- [ ] Header value matches backend identifier convention
- [ ] Tested with OPA to verify header is received
- [ ] Documentation updated for route creation process
