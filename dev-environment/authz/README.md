# AuthZ Development Environment

This directory contains the authorization stack for local development and testing,
plus dev-mode startup scripts that run the **full platform with authorization**.

## Two Ways to Start

There are two startup scripts in the `dev-environment/` directory. Both include the
full AuthZ stack (OPA, AuthZ Repository, APISIX with authorization). Choose based on
your workflow:

| | `backend/start.sh` | `authz/start-dev.sh` |
|---|---|---|
| **Mode** | Full Docker — all services containerized | Dev mode — backend runs locally with hot-reload |
| **Use when** | CI, demos, or you just need the stack running | Active backend development |
| **Hot-reload** | No (rebuild container on changes) | Yes (Spring Boot devtools) |
| **Idempotent** | No (tears down and rebuilds each run) | Yes (safe to re-run on running stack) |
| **Health checks** | Docker healthchecks only | Waits for each service with timeouts |
| **Skip builds** | No | `--skip-build` flag available |

## Quick Start (Dev Mode)

```bash
cd dev-environment/authz
./start-dev.sh              # Everything from scratch
./start-dev.sh --skip-build # Same, but skip Maven builds
```

Or run the pieces independently:

```bash
./start-infra.sh             # Infrastructure only (PostgreSQL, Keycloak)
./start-authz.sh             # AuthZ stack only (assumes infra is up)
./start-authz.sh --skip-build
```

All scripts are idempotent — safe to re-run on an already-running stack.

### What each script does

**`start-infra.sh`** — Infrastructure services:
1. PostgreSQL (Docker, creates civitas-network automatically)
2. Keycloak (Docker, waits for realm endpoint)

**`start-authz.sh`** — Application stack (requires infra):
1. Verifies PostgreSQL and Keycloak are up
2. Creates test users in Keycloak (idempotent)
3. Seeds authorization data in database (ON CONFLICT)
4. Maven builds (portal-model, portal-backend, authz-repository)
5. Starts Portal Backend on host (with Spring Boot devtools)
6. Starts AuthZ Docker stack (AuthZ Repository + OPA + APISIX)

**`start-dev.sh`** — Wrapper: checks prerequisites, then runs `start-infra.sh` + `start-authz.sh`

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

## Prerequisites

- Docker and Docker Compose
- Java 21 and Maven 3.9+
- jq (for JSON parsing)
- `/etc/hosts` must contain: `127.0.0.1 civitas-keycloak` (JWT issuer alignment)

## Manual Setup (if not using start-dev.sh)

### 1. Infrastructure

```bash
cd dev-environment/postgres && docker compose up -d   # creates civitas-network automatically
cd dev-environment/keycloak && docker compose up -d
```

### 2. Create Keycloak Test Users

```bash
./seed-keycloak-users.sh
```

Creates three test users (password: `test123`):
- `authz.admin@e2e.civitas.dev` — Full permissions (DataArchitect)
- `authz.reader@e2e.civitas.dev` — Read-only permissions (DataConsumer)
- `authz.none@e2e.civitas.dev` — No permissions

### 3. Seed Database

The `external_id` values in `seed-authz-data.sql` must match Keycloak user IDs. If the IDs match (they will on a fresh Keycloak install), run directly:

```bash
docker exec -i civitas-postgres-portal psql -U admin -d portal_backend < seed-authz-data.sql
```

### 4. Build AuthZ Repository

The AuthZ Repository depends on `portal-model:1.0.0-SNAPSHOT` (local dependency). Build on host first:

```bash
mvn -f portal-model/pom.xml install -DskipTests
mvn -f authz/repository/pom.xml package -DskipTests
```

### 5. Start Portal Backend

```bash
cd portal-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres
```

### 6. Start the AuthZ Stack

```bash
docker compose up -d
```

This starts AuthZ Repository (8091), OPA (8181), and APISIX (9080/9443).

## Testing

### Integration Tests

```bash
./integration-test.sh
```

Tests 20 scenarios across 7 groups: admin full access, reader read-only, reader write-denied, no-perms denied, unauthenticated, new resource endpoints (datasources/datastructures), and removed endpoints (dataspaces/catalogs).

### Health Checks

```bash
curl http://localhost:8091/actuator/health   # AuthZ Repository
curl http://localhost:8181/health             # OPA
curl http://localhost:8089/v2/actuator/health # Backend (direct, not through APISIX)
```

### Manual Testing

```bash
# Get JWT for admin user
# IMPORTANT: Use civitas-keycloak hostname so the JWT issuer matches APISIX's expectation.
# Prerequisite: /etc/hosts must contain: 127.0.0.1 civitas-keycloak
TOKEN=$(curl -sf -X POST "http://civitas-keycloak:8080/realms/civitas-core/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=portal-frontend&client_secret=dev-only-portal-frontend-secret&username=authz.admin@e2e.civitas.dev&password=test123" \
  | jq -r '.access_token')

# Test authorized request
curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/v2/datasets
# Expected: 200

# Test unauthorized request (as reader, trying to delete)
READER_TOKEN=$(curl -sf -X POST "http://civitas-keycloak:8080/realms/civitas-core/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=portal-frontend&client_secret=dev-only-portal-frontend-secret&username=authz.reader@e2e.civitas.dev&password=test123" \
  | jq -r '.access_token')

curl -X DELETE -H "Authorization: Bearer $READER_TOKEN" http://localhost:9080/v2/datasets/123
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
      "path": "/v2/datasets",
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

## Files

| File | Purpose |
|------|---------|
| `start-dev.sh` | Wrapper: prerequisite checks, then full stack from scratch |
| `start-infra.sh` | Infrastructure: PostgreSQL, Keycloak |
| `start-authz.sh` | Application: users, seed, builds, backend, authz stack |
| `_common.sh` | Shared functions, configuration, and prerequisite checks |
| `docker-compose.yml` | AuthZ Repository + OPA + APISIX |
| `apisix-config.yaml` | APISIX config with OPA plugin enabled |
| `apisix-routes.yaml` | Routes with openid-connect + opa plugins |
| `../apisix/opa-config/data.json` | OPA data config (AuthZ Repository URL) — mounted as directory so OPA loads it at `data.config` |
| `seed-authz-data.sql` | Test data for database |
| `seed-keycloak-users.sh` | Creates test users in Keycloak |
| `integration-test.sh` | 20-scenario integration test suite |

## Notes

- APISIX uses dynamic JWKS discovery (`use_jwks: true`) for JWT validation — no static keys
- All Keycloak access uses the `civitas-keycloak` hostname (requires `/etc/hosts: 127.0.0.1 civitas-keycloak`)
- AuthZ Repository uses port 8091 to avoid conflict with Kafka-UI (8090)
- The AuthZ Repository Dockerfile uses a pre-built JAR approach because `portal-model:1.0.0-SNAPSHOT` is not in Maven Central — see Dockerfile comments
