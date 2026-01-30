# Handoff: AuthZ System Deployment

**To**: Team 3 (Deployment/Infrastructure)
**From**: InfoSec (AuthZ implementation)
**Date**: 2026-02-03
**Priority**: Required for M5 (Full AuthZ Integration)

---

## Summary

This document covers deployment of the CIVITAS authorization system, consisting of:

1. **OPA (Open Policy Agent)** - Policy evaluation service
2. **AuthZ Repository** - User context REST API (provides user permissions to OPA)
3. **APISIX OPA Plugin** - Routes requests through OPA for authorization

---

## Architecture Overview

```
Request → APISIX → [JWT validation] → [OPA plugin] → Backend Service
                                            ↓
                                    OPA (Rego policies)
                                            ↓
                                    AuthZ Repository
                                            ↓
                                    PostgreSQL (read-only)
```

**Request flow**:
1. APISIX validates JWT (openid-connect plugin)
2. APISIX calls OPA for authorization decision (opa plugin)
3. OPA evaluates Rego policies, fetching user context from AuthZ Repository
4. OPA returns allow/deny decision
5. APISIX forwards request to backend (if allowed) or returns 403

---

## Component 1: OPA (Open Policy Agent)

### Container Image

```yaml
image: openpolicyagent/opa:1.4.2-static
```

Use the `-static` variant (Alpine-based, smaller image). Pin the version for reproducibility.

### Deployment Options

#### Option A: Volume Mounts (Dev/Simple)

Mount policy directories directly:

```yaml
services:
  opa:
    image: openpolicyagent/opa:1.4.2-static
    command:
      - "run"
      - "--server"
      - "--addr=0.0.0.0:8181"
      - "--log-level=info"
      - "--log-format=json"
      - "--set=decision_logs.console=true"
      - "/policies"
      - "/lib"
      - "/providers"
      - "/data"
    volumes:
      - ./authz/rego/policy:/policies:ro
      - ./authz/rego/lib:/lib:ro
      - ./authz/rego/providers:/providers:ro
      - ./authz/rego/backends:/data/backends:ro
    ports:
      - "8181:8181"
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8181/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 5s
```

#### Option B: Bundle (Production Recommended)

Use pre-built OPA bundle for immutable deployments:

```yaml
services:
  opa:
    image: openpolicyagent/opa:1.4.2-static
    command:
      - "run"
      - "--server"
      - "--addr=0.0.0.0:8181"
      - "--log-level=info"
      - "--log-format=json"
      - "--set=decision_logs.console=true"
      - "--bundle"
      - "/bundle/bundle.tar.gz"
    volumes:
      - ./bundle.tar.gz:/bundle/bundle.tar.gz:ro
    # ... same ports and healthcheck
```

**Building the bundle** (CI artifact or manual):
```bash
cd authz/rego
./build-bundle.sh v1.0.0
# Output: bundle.tar.gz
```

The build script runs pre-flight checks (format, type-check, tests) before building.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OPA_LOG_LEVEL` | `info` | Log verbosity: `debug`, `info`, `warn`, `error` |
| `OPA_LOG_FORMAT` | `json` | Output format: `json` or `text` |

### Health Check

```
GET http://opa:8181/health
```

Returns `200 OK` when ready to accept queries.

### Decision Endpoint

```
POST http://opa:8181/v1/data/civitas/authz/decision
Content-Type: application/json

{
  "input": {
    "request": {
      "method": "GET",
      "path": "/v2/users",
      "headers": {"x-authz-backend": "portal-backend"}
    },
    "user_context": {
      "userId": "user-1",
      "groups": [...]
    }
  }
}
```

Response:
```json
{
  "result": {
    "allowed": true,
    "reason": "user has READ_USER permission"
  }
}
```

### Decision Logging

Decision logs are written to stdout in JSON format (configured via `decision_logs.console=true`). In production, collect these with your logging infrastructure.

---

## Component 2: AuthZ Repository

### Container Build

```dockerfile
# Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY authz/repository/pom.xml .
COPY authz/repository/src ./src
COPY portal-model ./portal-model
RUN mvn clean package -DskipTests

# Runtime
FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/target/authz-repository-*.jar /app/app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Note: Requires `portal-model` module (shared JPA entities).

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5432/portal_backend` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Yes | `admin` | DB username |
| `DATABASE_PASSWORD` | Yes | `admin` | DB password |
| `SERVER_PORT` | No | `8090` | HTTP port |
| `SQL_LOGGING` | No | `INFO` | Set to `DEBUG` for SQL query logging |

### Database Access

**Security requirement**: Use a **read-only** database account with SELECT permission on these tables only:
- `users`
- `groups`
- `group_members`
- `assignments`
- `roles`
- `role_permissions`
- `permissions`

This service shares the `portal_backend` database but should have its own credentials with minimal privileges.

### Health Check

```
GET http://authz-repository:8090/actuator/health
```

Returns `200 OK` with health details (DB connection status).

### API Endpoint

```
GET http://authz-repository:8090/api/user-context/{userId}
```

Returns user's groups, assignments, roles, and permissions for OPA to evaluate.

---

## Component 3: APISIX OPA Plugin

### Plugin Configuration

Add the OPA plugin to protected routes in APISIX:

```yaml
routes:
  - id: portal-backend-api
    uri: /v2/*
    upstream:
      nodes:
        "portal-backend:8089": 1
    plugins:
      # 1. Set backend identifier (existing - see Team 1 handoff)
      proxy-rewrite:
        headers:
          set:
            X-Authz-Backend: portal-backend

      # 2. JWT validation (existing)
      openid-connect:
        # ... existing config

      # 3. OPA authorization (NEW for M5)
      opa:
        host: "http://opa:8181"
        policy: "civitas/authz/decision"
        with_route: false
        with_service: false
        with_consumer: false
        keepalive: true
        keepalive_timeout: 60000
        keepalive_pool: 5
```

### Plugin Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| `host` | `http://opa:8181` | OPA server URL |
| `policy` | `civitas/authz/decision` | Policy path (minus `/v1/data/`) |
| `with_route` | `false` | Don't send APISIX route info to OPA |
| `with_service` | `false` | Don't send APISIX service info |
| `with_consumer` | `false` | Don't send APISIX consumer info |
| `keepalive` | `true` | Reuse connections to OPA |

### Security Note: URL Normalization

**IMPORTANT**: APISIX's OPA plugin sends `ctx.var.uri` (the normalized path) to OPA, NOT the raw `request_uri`. This means:

- Path traversal attempts (`/../`) are resolved before OPA sees them
- URL encoding (`%2e%2e`) is decoded
- Duplicate slashes are normalized

This is handled by APISIX before the request reaches OPA. Our Rego policies include additional path validation as defense-in-depth, but the primary protection is APISIX's normalization.

Reference: https://github.com/apache/apisix/blob/master/apisix/plugins/opa/helper.lua#L41

---

## Network Requirements

All components must be on the same Docker network (or Kubernetes namespace with appropriate NetworkPolicy):

```yaml
networks:
  civitas-network:
    name: civitas-network

services:
  opa:
    networks: [civitas-network]
  authz-repository:
    networks: [civitas-network]
  apisix:
    networks: [civitas-network]
  postgres:
    networks: [civitas-network]
```

### Service Discovery

| Service | Port | Internal DNS |
|---------|------|--------------|
| OPA | 8181 | `opa:8181` or `civitas-opa:8181` |
| AuthZ Repository | 8090 | `authz-repository:8090` |

---

## Deployment Order

1. **PostgreSQL** - Database must be running and migrated
2. **AuthZ Repository** - Needs database connection
3. **OPA** - Needs AuthZ Repository for user context (graceful degradation if unavailable)
4. **APISIX** - Update routes with OPA plugin (can be rolling update)

---

## Verification Steps

### 1. Verify OPA is healthy

```bash
curl http://localhost:8181/health
# Expected: 200 OK
```

### 2. Verify OPA loads policies

```bash
curl http://localhost:8181/v1/policies
# Expected: JSON listing all loaded policies
```

### 3. Verify AuthZ Repository is healthy

```bash
curl http://localhost:8090/actuator/health
# Expected: {"status":"UP",...}
```

### 4. Test authorization decision

```bash
curl -X POST http://localhost:8181/v1/data/civitas/authz/decision \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "request": {
        "method": "GET",
        "path": "/v2/users",
        "headers": {"x-authz-backend": "portal-backend"}
      },
      "user_context": {
        "userId": "test-user",
        "groups": [{
          "assignments": [{
            "permissions": ["READ_USER"]
          }]
        }]
      }
    }
  }'

# Expected: {"result":{"allowed":true,"reason":"user has READ_USER permission"}}
```

### 5. Test end-to-end through APISIX

```bash
# Get a valid JWT from Keycloak
TOKEN=$(curl -s -X POST "http://keycloak:8080/realms/civitas-core/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=portal-frontend" \
  -d "username=test-user" \
  -d "password=test-password" | jq -r '.access_token')

# Make request through APISIX
curl -H "Authorization: Bearer $TOKEN" http://apisix:9080/v2/users
# Expected: 200 OK (if user has READ_USER permission) or 403 Forbidden
```

---

## Troubleshooting

### OPA returns "unknown_backend"

- Check that `X-Authz-Backend` header is being set by APISIX
- Verify backend identifier matches data file (e.g., `portal-backend` → `backends/portal_backend/data.json`)

### OPA returns "endpoint_not_configured"

- Check that the requested path exists in the backend's data file
- Verify path pattern matching (e.g., `/v2/users/123` matches `/v2/users/{id}`)

### AuthZ Repository connection refused

- Check network connectivity between OPA and AuthZ Repository
- Verify AuthZ Repository health endpoint
- Check database connection from AuthZ Repository

### APISIX returns 503 for OPA

- OPA is not reachable
- Check OPA container is running and healthy
- Verify network connectivity

---

## Related Documents

- [Team 1 Handoff: X-Authz-Backend Header](TEAM1-APISIX-CONFIG-ADAPTER.md) - Config Adapter requirements
- [AuthZ Rego README](../../../authz/rego/README.md) - Policy architecture documentation
- [MILESTONES.md](../project-info/MILESTONES.md) - M4, M4.5, M5 milestone details

---

## Checklist

- [ ] OPA container deployed with correct image version
- [ ] OPA health check passing
- [ ] AuthZ Repository deployed with read-only DB credentials
- [ ] AuthZ Repository health check passing
- [ ] APISIX routes updated with OPA plugin
- [ ] End-to-end authorization test passing
- [ ] Decision logs being collected
- [ ] Network policies configured (if Kubernetes)
