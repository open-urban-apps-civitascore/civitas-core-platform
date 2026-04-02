# AuthZ — Test Data & Integration Tests

This directory contains authorization test data and integration tests for the
APISIX → OPA → AuthZ Repository chain. The actual services live in
`dev-environment/apisix/` (shared dev-env).

## Quick Start

```bash
# 1. Start the dev environment with full authz
cd dev-environment
./start-portal-dev.sh --authz=full

# 2. Run integration tests (seeds test data automatically)
cd authz
./integration-test.sh
```

## What's Here

| File | Purpose |
|------|---------|
| `integration-test.sh` | 20-scenario integration test suite (seeds data, then tests) |
| `seed-authz-data.sql` | Test data: 3 users with different permission levels |
| `README.md` | This file |

## Test Users

Pre-provisioned in `../keycloak/realm-export.json` with pinned UUIDs
(`e2e00000-...-{1,2,3}`). Imported automatically when Keycloak starts.

| User | Email | Permissions |
|------|-------|-------------|
| Admin | `authz.admin@e2e.civitas.dev` | All (DataArchitect role) |
| Reader | `authz.reader@e2e.civitas.dev` | Read-only (DataConsumer role) |
| NoPerms | `authz.none@e2e.civitas.dev` | None |

Password: `test123`

The `seed-authz-data.sql` creates the matching database records (users, roles,
permissions, assignments) using the same pinned UUIDs as `external_id`.

## Architecture

```
Request with JWT
       │
       ▼
    APISIX (port 9080)
       │ 1. openid-connect: validates JWT, sets X-Userinfo header (base64 claims)
       │ 2. opa: calls OPA for authorization decision (with_service=true sends
       │         service metadata so OPA knows which backend to use)
       ▼
      OPA (port 8181)
       │ 1. Decode X-Userinfo header (base64 → JSON)
       │ 2. Extract sub (externalId) from JWT claims
       │ 3. http.send() to AuthZ Repository
       ▼
 AuthZ Repository (port 8091)
       │ GET /api/v1/user-context/{externalId}
       │ Returns: userId, groups, assignments, permissions
       ▼
      OPA
       │ Evaluate permission against user_context
       │ Return: {allowed: true/false, reason: "..."}
       ▼
    APISIX
       │ allowed=true → forward to backend (200)
       │ allowed=false → reject (403)
       ▼
 Portal Backend (port 8089)
```

## Integration Tests

```bash
./integration-test.sh
```

Tests 20 scenarios across 7 groups: admin full access, reader read-only,
reader write-denied, no-perms denied, unauthenticated, new resource endpoints
(datasources/datastructures), and null-permission endpoints (dataspaces/catalogs).

Requires:
- `start-portal-dev.sh --authz=full` (full authz mode)
- Portal Backend running on port 8089

## Manual Testing

```bash
# Get JWT for admin user
# Keycloak is configured with KC_HOSTNAME=localhost, so tokens from localhost:8080 are
# accepted by APISIX (which discovers Keycloak via Docker DNS at civitas-keycloak:8080).
TOKEN=$(curl -sf -X POST "http://civitas-keycloak:8080/realms/civitas-core/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=portal-frontend&client_secret=dev-only-portal-frontend-secret&username=authz.admin@e2e.civitas.dev&password=test123" \
  | jq -r '.access_token')

# Test authorized request
curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/v1/datasets
# Expected: 200

# Test unauthorized request (as reader, trying to delete)
READER_TOKEN=$(curl -sf -X POST "http://civitas-keycloak:8080/realms/civitas-core/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=portal-frontend&client_secret=dev-only-portal-frontend-secret&username=authz.reader@e2e.civitas.dev&password=test123" \
  | jq -r '.access_token')

curl -X DELETE -H "Authorization: Bearer $READER_TOKEN" http://localhost:9080/v1/datasets/123
# Expected: 403
```

## Troubleshooting

### Check OPA Decision Logs

```bash
# One-line summary per decision (live tail)
docker logs -f civitas-opa 2>&1 | jq -r 'select(.decision_id) | "\(.timestamp) \(.input.request.method) \(.input.request.path) → allowed=\(.result.allow) \(.result.reason // "")"'

# Full decision details (last 50 log entries)
docker logs civitas-opa --tail 50 | jq 'select(.decision_id)'
```

Note: Sensitive headers (X-Access-Token, X-Userinfo) are masked in decision logs by `policy/mask.rego`.

### Check AuthZ Repository Logs

```bash
docker logs civitas-authz-repository --tail 50
```

### Test OPA Directly

```bash
curl -X POST http://localhost:8181/v1/data/civitas/authz/decision -d '{
  "input": {
    "request": {
      "method": "GET",
      "path": "/v1/datasets",
      "headers": {}
    },
    "service": {"name": "portal-backend"}
  }
}'
```

### Verify User Context Fetch

```bash
# Get user context directly from AuthZ Repository
curl http://localhost:8091/api/v1/user-context/{keycloak-user-id}
```

## Notes

- APISIX uses dynamic JWKS discovery (`use_jwks: true`) for JWT validation — no static keys
- Keycloak uses `KC_HOSTNAME=localhost` so browser tokens match APISIX's issuer expectation without `/etc/hosts` changes
- AuthZ Repository uses port 8091 to avoid conflict with Kafka-UI (8090)
- The AuthZ Repository Dockerfile uses a pre-built JAR approach because `portal-model:1.0.0-SNAPSHOT` is not in Maven Central — see Dockerfile comments
