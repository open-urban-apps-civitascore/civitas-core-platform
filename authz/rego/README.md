# CIVITAS AuthZ Rego Policies

OPA (Open Policy Agent) policies for authorization decisions in the CIVITAS CORE Platform.

## Overview

This directory contains Rego policies that evaluate authorization requests from APISIX. OPA acts as the Policy Decision Point (PDP) - it receives requests, evaluates them against policies and user context, and returns allow/deny decisions.

**Key Design Principles:**
- **Fail-secure**: Unknown endpoints, invalid paths, and missing data result in denial
- **Multi-backend**: Provider architecture supports different API styles (REST, OData)
- **Defense-in-depth**: Path validation even though APISIX normalizes URLs
- **Separation of concerns**: Library → Providers → Dispatcher → Policy

## Directory Structure

```
authz/rego/
├── policy/                      # Core policy modules
│   ├── main.rego                # Entry point: allow/deny decision with reason
│   ├── permission_eval.rego     # Permission lookup and evaluation
│   └── resource_mapping.rego    # Dispatcher: routes to backend providers
│
├── lib/                         # Shared libraries
│   └── genericrestmapper.rego   # Reusable REST path parsing (/v1/resource/{id})
│
├── providers/                   # Backend-specific implementations
│   ├── portal_backend.rego      # Portal Backend API (REST)
│   └── frost_server.rego        # FROST Server stub (OData - F-005)
│
├── data/                        # OPA data directory (data.* namespace)
│   └── backends/                # Endpoint → permission mappings (data.backends.*)
│       ├── portal_backend/
│       │   └── data.json        # → data.backends.portal_backend.endpoints
│       └── frost_server/
│           └── data.json        # → data.backends.frost_server.endpoints
│
├── test/                        # Unit tests (mirrors source structure)
│   ├── lib/
│   ├── providers/
│   └── policy/
│
├── .manifest                    # OPA bundle manifest
├── build-bundle.sh              # Bundle build with pre-flight checks
└── run-tests.sh                 # Test runner (Docker-based)
```

## Architecture

### User Context (M5)

OPA needs user context (groups, roles, permissions) to evaluate authorization. This comes from two sources:

1. **JWT claims from Keycloak**: Contains system roles and group memberships
2. **AuthZ Repository lookup**: Maps Keycloak `sub` (externalId) to internal userId and fetches full permission hierarchy from database

**M5 Integration**:
- APISIX validates JWT and passes claims to OPA via the OPA plugin
- OPA extracts `sub` from JWT claims
- OPA calls AuthZ Repository (`http.send`) to get user's permissions: `GET /api/v1/user-context/{externalId}`
- AuthZ Repository does the externalId→userId mapping and returns groups/assignments/permissions
- OPA evaluates policies with the combined context

**Why two sources?**
- JWT contains Keycloak-managed data (system roles, group memberships)
- AuthZ Repository contains platform-managed data (fine-grained permissions, scope assignments)
- The split allows Keycloak to handle identity while the platform manages authorization

### Request Flow

```
APISIX Request → resource_mapping.rego (dispatcher)
                        │
                        ├─ input.service.name: "portal-backend"
                        │       └→ portal_backend.rego → genericrestmapper.rego
                        │
                        └─ input.service.name: "frost-server"
                                └→ frost_server.rego (stub, denies all)

                        ↓
                 permission_eval.rego
                 (lookup permission from backend data)
                        ↓
                    main.rego
                 (final decision)
```

Backend identification uses APISIX Service metadata (`with_service=true` in the OPA plugin).
OPA reads `input.service.name` to dispatch to the correct provider.

### Why This Architecture?

**Problem**: Different backends have different URL patterns:
- Portal Backend: REST style `/v1/users/123`
- FROST Server: OData style `/v1.1/Things(123)/Datastreams`

**Solution**: Provider pattern with shared library:
1. `genericrestmapper.rego` - Handles common REST `/version/resource/{id}` patterns
2. Each provider wraps the library or implements custom parsing
3. `resource_mapping.rego` dispatches based on `input.service.name` (APISIX service metadata)
4. FROST provider is a stub until OData parsing is implemented (F-005)

### Data Path Convention

Backend data files must be at `data/backends/{backend_id}/data.json` to resolve as `data.backends.{backend_id}.*`:

```
data/backends/portal_backend/data.json  →  data.backends.portal_backend.endpoints
data/backends/frost_server/data.json    →  data.backends.frost_server.endpoints
```

## Usage

### Running Tests

```bash
./run-tests.sh
```

Runs all unit tests via Docker (no local OPA installation required).

### Building a Bundle

```bash
./build-bundle.sh              # Auto-generates revision from git SHA
./build-bundle.sh v1.0.0       # Explicit revision
```

Pre-flight checks:
1. `opa fmt --diff` - Formatting
2. `opa check --strict` - Type checking
3. `opa test policy lib providers test data -v` - Unit tests

Output: `bundle.tar.gz` containing compiled policies and data.

### Testing Manually

```bash
# Start AuthZ stack (OPA + APISIX + AuthZ Repository)
cd dev-environment/authz && docker compose up -d

# Query a decision
curl -X POST http://localhost:8181/v1/data/civitas/authz/decision \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "request": {
        "method": "GET",
        "path": "/v1/users",
        "headers": {}
      },
      "service": {"name": "portal-backend"}
    }
  }'
```

## Adding a New Backend

1. **Create provider** at `providers/{backend_id}.rego`:
   ```rego
   package civitas.authz.providers.my_backend

   import data.civitas.authz.lib.restmapper

   endpoints := data.backends.my_backend.endpoints
   path_pattern := restmapper.match_pattern(input.request.path, endpoints)
   ```

2. **Create data file** at `data/backends/{backend_id}/data.json`:
   ```json
   {
     "_version": "1.0.0",
     "_backend_id": "my-backend",
     "endpoints": {
       "/v1/things": {"GET": "THING_READ", "POST": "THING_CREATE"},
       "/v1/things/{id}": {"GET": "THING_READ", "DELETE": "THING_DELETE"},
       "/v1/things/{id}/publish": {"POST": ["THING_UPDATE", "THING_PUBLISH"]}
     }
   }
   ```

   **Naming convention**: `ENTITY_ACTION` (e.g., `THING_READ`, not `READ_THING`).
   **AND-permissions**: Use JSON arrays for endpoints requiring multiple permissions simultaneously (e.g., `["THING_UPDATE", "THING_PUBLISH"]`). The user must hold **all** listed permissions.

3. **Register in dispatcher** (`policy/resource_mapping.rego`):
   ```rego
   import data.civitas.authz.providers.my_backend

   path_pattern := my_backend.path_pattern if {
       backend_data_key == "my_backend"
   }

   backend_endpoints := my_backend.endpoints if {
       backend_data_key == "my_backend"
   }
   ```

4. **Add tests** at `test/providers/{backend_id}_test.rego`

5. **Update docker-compose.yml** if needed for volume mounts

## Security Notes

- **Backend ID validation**: Only alphanumeric, dashes, underscores allowed
- **Path validation**: Rejects `..`, null bytes, backslashes, paths > 2048 chars
- **Fail-secure defaults**: Unknown backends/endpoints/paths → deny
- **No filesystem access**: `data.backends[key]` is in-memory lookup, not file read

## Key Files Reference

| File | Purpose |
|------|---------|
| `policy/main.rego` | Entry point, produces `{allowed: bool, reason: string}` |
| `policy/permission_eval.rego` | Checks if user has required permission(s) — supports AND-permission sets |
| `policy/resource_mapping.rego` | Identifies backend, dispatches to provider |
| `lib/genericrestmapper.rego` | Path validation, `/version/resource/{id}` matching |
| `data/backends/*/data.json` | Endpoint → permission mappings per backend |

## Related Documentation

- ADR-001: Collection Endpoint Filtering — see official ADR repository
- AuthZ Deployment Guide — see MR comments (Team 3)
