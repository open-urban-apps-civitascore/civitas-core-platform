# M5: Full AuthZ Integration Stack

This directory contains the unified authorization stack for local development and testing.

## Architecture

```
Request with JWT
       │
       ▼
    APISIX (port 9080)
       │ 1. proxy-rewrite: sets X-Authz-Backend header
       │ 2. openid-connect: validates JWT, sets X-Userinfo header (base64 claims)
       │ 3. opa: calls OPA for authorization decision
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

## Prerequisites

Before starting the authz stack, ensure these are running:

1. **PostgreSQL** (civitas-postgres-portal)
   ```bash
   cd dev-environment/postgres && docker compose up -d
   ```

2. **Keycloak** (civitas-keycloak)
   ```bash
   cd dev-environment/keycloak && docker compose up -d
   ```

3. **Portal Backend** (port 8089)
   ```bash
   cd portal-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

4. **Create civitas-network** (if not exists)
   ```bash
   docker network create civitas-network
   ```

## Setup

### 1. Create Keycloak Test Users

```bash
./seed-keycloak-users.sh
```

This creates three test users in Keycloak:
- `authz.admin@e2e.civitas.dev` - Full permissions
- `authz.reader@e2e.civitas.dev` - Read-only permissions
- `authz.none@e2e.civitas.dev` - No permissions

### 2. Seed Database

Update the `external_id` values in `seed-authz-data.sql` with the Keycloak user IDs from step 1, then:

```bash
docker exec -i civitas-postgres-portal psql -U admin -d portal_backend < seed-authz-data.sql
```

### 3. Start the AuthZ Stack

```bash
docker compose up -d
```

This starts:
- AuthZ Repository (port 8091)
- OPA (port 8181)
- APISIX (ports 9080, 9443)

## Testing

### Health Checks

```bash
# AuthZ Repository
curl http://localhost:8091/actuator/health

# OPA
curl http://localhost:8181/health

# APISIX (via backend)
curl http://localhost:9080/v2/actuator/health
```

### Integration Tests

```bash
./integration-test.sh
```

This tests:
- Admin user can read/write
- Reader user can read but not write
- No-perms user gets 403 on protected, 200 on /users/me
- Unauthenticated requests get 401

### Manual Testing

```bash
# Get JWT for admin user
TOKEN=$(curl -sf -X POST "http://localhost:8080/realms/civitas-core/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=portal-frontend&username=authz.admin@e2e.civitas.dev&password=TestPassword123!" \
  | jq -r '.access_token')

# Test authorized request
curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/v2/datasets
# Expected: 200

# Test unauthorized request (as reader, trying to delete)
READER_TOKEN=$(curl -sf -X POST "http://localhost:8080/realms/civitas-core/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=portal-frontend&username=authz.reader@e2e.civitas.dev&password=TestPassword123!" \
  | jq -r '.access_token')

curl -X DELETE -H "Authorization: Bearer $READER_TOKEN" http://localhost:9080/v2/datasets/123
# Expected: 403
```

## Troubleshooting

### Check OPA Decision Logs

```bash
docker logs civitas-opa --tail 50 | jq 'select(.decision_id)'
```

### Check AuthZ Repository Logs

```bash
docker logs civitas-authz-repository --tail 50
```

### Test OPA Directly

```bash
# Test with mock input
curl -X POST http://localhost:8181/v1/data/civitas/authz/decision -d '{
  "input": {
    "request": {
      "method": "GET",
      "path": "/v2/datasets",
      "headers": {"x-authz-backend": "portal-backend"}
    },
    "user_context": {
      "userId": "test-user",
      "externalId": "test-ext",
      "groups": [{
        "id": "g1",
        "name": "TestGroup",
        "assignments": [{
          "roleId": "r1",
          "roleName": "DataArchitect",
          "roleType": "DATA",
          "scopeType": "TENANT",
          "scopeId": "civitas-core",
          "permissions": ["READ_DATASET"]
        }]
      }]
    }
  }
}'
```

### Verify User Context Fetch

```bash
# Get user context directly from AuthZ Repository
curl http://localhost:8091/api/v1/user-context/{keycloak-user-id}
```

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Unified authz stack |
| `apisix-config.yaml` | APISIX config with OPA plugin enabled |
| `apisix-routes.yaml` | Routes with openid-connect + opa plugins |
| `seed-authz-data.sql` | Test data for database |
| `seed-keycloak-users.sh` | Creates test users in Keycloak |
| `integration-test.sh` | Automated integration tests |

## Notes

- The APISIX config uses a static public key for JWT validation (see comments in apisix-routes.yaml for why)
- In production, use dynamic JWKS with HTTPS
- AuthZ Repository uses port 8091 to avoid conflict with Kafka-UI (8090)
