Requirements for project: Centralized AuthZ with APISIX and OPA

Here's what we are going to do. We're going to take this project and add/change:

    - APISIX instance: this is to be the central gateway for all frontend->backend requests. In prod, it'll be used for all sorts of things from rate limiting to logging, but here in dev, we're only interested in using it for AuthN and AuthZ.\
        ○ We'll need to make sure any frontend->backend traffic goes through APISIX\
        ○ AuthZ will be done through integrating the OPA plugin for APISIX\
        ○ AuthN is token validation only, i.e. client is expected to send bearer token and we only do JKWS\
    - OPA\
        ○ We need it deployed\
        ○ We need quite of bit of Rego to implement the authz model, details to follow\
    - AuthZ Adapter\
        ○ Meant to decouple OPA from the Portal Backend DB where the Permission info lives.\
    - Config Adapter APISIX\
        ○ The whole CIVITAS/CORE integration architecture is based around a Kafka message bus. Components are connected to it via Configuration Adapters. A Config Adapter exists already for APISIX, but we probably won't need to use it as we can add both openid-connect and opa plugin settings as plugin metadata (because some endpoints won't have authz at all and thus we need a possibility to override)\
    - Currently, the front end uses Next.JS Nextauth as a backend-for-frontend to do all the oauth2 token handling. We'll need to proxy all traffic to APISIX from there, including of course the user's OAuth2 token. We'll probably do that somewhere in civitas-core-platform/portal-frontend/src/app/api\
    - Probably there's more stuff. Help me think it through.

Test Cases:\
    - We need to flesh this out in detail\
    - Here's what i see:\
        ○ Definitely at least one happy case, one failure case end to end ui test as a smoke test (i.e. user logs in, accesses some data and then either succeeds or fails)\
        ○ Non-ui-integration businesss logic tests - we'll need a good number of tbd test cases to cover the various angles of the authz concept\
        ○ Possibly unit tests for the\
    - Also, I'd like you to know what you think you need for good "pragmatically TDD" (i.e. we want test cases that make you more nimble, no full coverage needed).

Things you need to be aware of:\
    - We're working in the platform project. This project essentially includes: portal frontend/backend and configuration adapters. Additionally and for dev purposes only, it contains keycloak and docker - Team 1 and 2 work here.\
    - The platform project is bits and pieces atm; no component end-to-end integration exists. We'll have to make that happen before we implement anything, otherwise we can't test. So that's our Milestone M0.
    - Just for context: there's three teams (Team 1: Configuration Adapters, Team 2: Portal Frontend and Backend, Team 3: Deployment/DevOps/anything Kerberos, but not Operations - we just build a product. I'm in an entirely different Team (infosec), but I'm helping out here with the engineering as it's crunch time!\
    - There's also a deployment project that bundles everything up (both 3rd party and custom code) and includes helm charts for k8s deployment - this is what Team 3 is working on.\
    - This was already PoC'ed. I'll probably make stuff from the poc available to you later. We're following the same architecture, but there were some simplifications made in the PoC, i.e. don't blindly follow it.\
    - We'll hand our dev config over to team 3 and they'll adapt it for prod. Any java or rego code needs to be handed over production ready though.\
    - We'll hand over our Nextauth changes to Team 2\
    - Sources of information (in order of precedence, i.e. when information conflicts, sources higher on the list trump information from sources lower in the list)\
        ○ My instructions\
        ○ CIVITAS/CORE Docs\
        ○ PoC information

Non functional requirements (in addition to the project wide NFRs):\
    - We need to document any requirements we have towards/settings we need in:\
        ○ Kubernetes (we'll run in k8s later, for dev it's just docker-compose)\
        ○ Keycloak

Our deliverables are:\
    - APISIX config for AuthZ\
    - AuthZ Adapter Service Java Code+Tests and Dockerfile\
    - Rego code + tests\
    - Docker-compose file that wires them (plus OPA) together\
    - Changes in next.js\
    - Docs

---

## Clarified Requirements (from Q&A)

### Architecture Decisions

1. **OPA data integration pattern**: OPA queries the AuthZ Adapter as an external HTTP data source at decision time (not bundles, not push). Caching/data distribution to be added in a later release.

2. **AuthZ Adapter DB access**: Direct read-only PostgreSQL connection to the Portal Backend DB (not via REST API). Simpler, accepted coupling.

3. **APISIX OPA plugin**: Official APISIX `opa` plugin. Sends request metadata (method, path, headers) to OPA; OPA returns allow/deny.

4. **APISIX Config Adapter**: Out of scope. APISIX AuthN/AuthZ configuration is static (config files / docker-compose). The Kafka-based Config Adapter will only add routes that pick up authn/authz defaults. Whether this assumption holds remains TBD.

5. **Multi-frontend/backend gateway**: Portal frontend is the only frontend for now, portal backend the only backend. Conceptually APISIX is the PEP for all frontends and will serve multiple backends (v2.0 adds FROST server).

6. **Endpoints without AuthZ**: Some datasets are exposed entirely anonymously (e.g., municipal traffic statistics). These endpoints skip AuthZ or both AuthN and AuthZ.

7. **Keycloak JWT claims**: Expect roles and groups in the token. Groups may not be configured in Keycloak yet; user has a sprint ticket to add group claims and other Keycloak settings.

8. **Frontend proxy integration point**: `portal-frontend/src/app/api/[...path]/route.ts` is the single point of change. Retarget from backend (localhost:8089) to APISIX (localhost:9080). Bearer token is already injected by the BFF proxy.

### M0 — End-to-End Integration Baseline

9. **Current state**: Frontend-to-backend integration is unverified and assumed broken. M0 = make the full stack work end-to-end (frontend -> backend -> DB) before adding any new components.

10. **Test users**: Create Keycloak test users as needed. VM is exclusively ours.

### Testing Strategy

11. **E2E smoke tests**: Playwright against fully dockerized stack (Keycloak + APISIX + OPA + AuthZ Adapter + Backend + Frontend). Open to alternatives.

12. **Pragmatic TDD layers**:
    - Rego unit tests (OPA's built-in test framework)
    - AuthZ Adapter integration tests (Testcontainers + PostgreSQL)
    - APISIX integration test (docker-compose with OPA + mocked adapter)
    - 1-2 Playwright E2E smoke tests (happy path + failure path)
    - Consider DB->OPA->APISIX integration test to catch data format/naming issues that component tests might miss

### Project Structure

13. **New code location**: `/authz/` directory at repo root with subdirectories for each component.

---

## Authorization Data Model

Source: CIVITAS/CORE Docs + Portal Backend DB Schema (V1-V6 migrations)

### Core Entities

| Entity | DB Table | Key Fields |
|--------|----------|------------|
| User | `users` | id (UUID), first_name, last_name, email, external_id (Keycloak link), active |
| Group | `groups` | id, name, parent_group_id (hierarchical), contact_user_id |
| Role | `roles` | id, name, role_type (SYSTEM/DATA/GOVERNANCE), readonly |
| Permission | `permissions` | id, name, permission_type (SYSTEM/DATA/GOVERNANCE), category |
| Assignment | `assignments` | id, group_id, role_id, scope_type, scope_id, is_inherited, parent_assignment_id |
| DataSpace | `data_spaces` | id, name, parent_dataspace_id (hierarchical), owner_user_id, external_id |
| DataSet | `datasets` | id, name, owner_user_id, external_id, format |

### Join Tables

| Table | Links |
|-------|-------|
| `group_members` | groups <-> users (M:N) |
| `group_roles` | groups <-> roles (M:N) — DEPRECATED, use assignments instead |
| `role_permissions` | roles <-> permissions (M:N) |
| `dataset_dataspaces` | datasets <-> dataspaces (M:N) |

### Enums (Java: `de.civitascore.portal.model.embedded`)

- **ScopeType**: `TENANT`, `DATASPACE`, `DATASET`
- **RoleType**: `SYSTEM` (binary assignments), `DATA` (ternary), `GOVERNANCE` (ternary)
- **PermissionType**: `SYSTEM`, `DATA`, `GOVERNANCE`
- **AssignmentType**: `BINARY` (system roles: group+role only), `TERNARY` (data/governance: group+role+scope) — derived from RoleType, not stored in DB

### Authorization Decision Logic (Ternary Model)

The authorization model resolves: **Group x Role x Scope**

1. Identify user -> find their groups (via `group_members`)
2. For each group -> find assignments (via `assignments` table)
3. Each assignment specifies: which role, at what scope (tenant/dataspace/dataset)
4. Role -> permissions (via `role_permissions`)
5. Check if the required permission exists for the target resource scope
6. Inheritance: platform-level assignments cascade to all dataspaces/datasets; dataspace-level cascades to contained datasets

Key rules:
- Users never directly hold roles; only groups do
- More specific scope overrides broader scope
- System and default roles (readonly=true) cannot be modified
- New users get "Standard User" role automatically

### Scope Inheritance

```
TENANT (platform-wide)
  └── DATASPACE (domain-specific, hierarchical via parent_dataspace_id)
        └── DATASET (individual resource, linked via dataset_dataspaces)
```

Assignments at a broader scope cascade downward. Overlapping dataspaces are permitted.

### Standard Roles

| Role | Type | Scope | Key Responsibilities |
|------|------|-------|---------------------|
| Platform Admin | SYSTEM | Tenant | Tenant config, system monitoring |
| Tenant Admin | SYSTEM | Tenant | User/role/permission management |
| Data Architect | DATA | Ternary | Data governance, dataspace admin, full CRUD |
| Data Consumer | DATA | Ternary | Data usage, catalog access (read-heavy) |
| Data Steward | DATA | Ternary | Domain data management, dataset lifecycle |
| Data Owner | DATA+GOV | Ternary | Guidelines, release authorization |
| Data Gatekeeper | DATA+GOV | Ternary | Governance model, privacy authorization |

### Permission Matrix (Data Roles)

Operations per resource type (from CIVITAS/CORE docs):

| Object | EXISTS | READ | CREATE | UPDATE | DELETE | RELEASE | USE |
|--------|--------|------|--------|--------|--------|---------|-----|
| DataSet | All 5 | All 5 | Arch,Stew,Own | Arch,Stew,Own | Arch,Stew,Own | Own,Gate | Arch,Stew,Own+Own |
| DataSet Payload | - | Own,Stew,Cons,Gate | Arch,Stew | Arch,Stew | Arch,Stew | - | - |
| DataSource | Arch,Stew,Cons,Gate | All 4 | Arch,Stew,Own | All 3 | All 3 | Own,Gate | All 3 |
| DataStructure | Arch,Stew,Cons,Gate | All 4 | Arch,Stew,Own | All 3 | All 3 | Own,Gate | All 3 |
| DataSpace | All 5 | All 5 | Arch only | Arch,Stew,Own | Arch only | - | - |
| DataCatalogue | All 5 | All 5 | Arch only | Arch,Stew,Own | Arch only | - | - |
| Tag | All 5 | All 5 | Arch,Gate | Arch,Gate | Arch,Gate | - | - |

Full matrix: https://docs.core.civitasconnect.digital/review-arch-v2-doc/docs_v2/Architecture/Architecture_General/Authorization_Data_Model

---

## Design Decisions (from Q&A Round 2)

### Resource Identification in AuthZ Requests

**Decision**: OPA Rego policies parse the URL path and HTTP verb from the request metadata that APISIX sends. Backend teams are responsible for encoding resource type and identity in URL/verb combinations. APISIX routes will use path prefixes to distinguish backends (e.g., `GET /cc/frost/dataset/1`).

**Rationale**: Keeping parsing logic in Rego (not custom APISIX plugins) decouples the PDP from APISIX. While APISIX is the committed PEP for now, this avoids vendor lock-in in the policy logic. Custom APISIX plugin development is also assumed to be harder than Rego policy work.

**Team dependency**: Backend teams must design URL patterns that carry enough information for PDP decisions using only what APISIX can see (path, verb, headers).

### HTTP-to-Permission Operation Mapping

**Decision**: Per-backend mappings. Team 2 will deliver the portal backend mappings. Other backends (e.g., FROST) only have CRUD operations.

**Working assumption** (pending Team 2 confirmation):
- GET → READ (HEAD → EXISTS)
- POST → CREATE (or RELEASE/USE for specific sub-resource endpoints)
- PUT/PATCH → UPDATE
- DELETE → DELETE

The full permission operation set (EXISTS, READ, CREATE, UPDATE, DELETE, RELEASE, USE) is only used by the portal backend. The mapping will need to be configurable per backend/route.

### AuthZ Adapter Role

**Decision**: The AuthZ Adapter is purely a data adapter — it returns user authorization data (groups, assignments, roles, permissions, scopes) for a given user identity. It does NOT make policy decisions. OPA is the sole PDP.

**Contract (conceptual)**: Given a user identifier (from JWT `sub` claim), return the user's complete authorization context: group memberships, assignments with scopes, roles with permissions. OPA Rego then evaluates this data against the request.

### Multi-Tenancy

**Decision**: Not implemented for v2.0. However:
- Data structures should be tenant-aware (fields exist in schema already via `scope_type=TENANT`)
- Rego policies should work correctly when no tenant info is provided
- Do not hard-code single-tenant assumptions in ways that would break future multi-tenancy

### Backend API Scope

**Decision**: For dev environment, only portal backend (localhost:8089). FROST server is production-only and responsibility of Team 1 or Team 3.

**Action needed**: Verify portal backend API contract and implementation status. Swagger docs at `localhost:8089/v2/swagger-ui.html` once backend is running.

---

## External Dependencies / Blockers

| Dependency | Owner | Status | Impact |
|-----------|-------|--------|--------|
| HTTP-to-permission operation mappings for portal backend | Team 2 | Promised, not yet delivered | Blocks detailed Rego policy writing |
| Keycloak group claims configuration | User (sprint ticket) | Planned | Blocks group-based AuthZ testing |
| Backend URL pattern conventions for APISIX routing | Backend teams | Communicated, pending | Blocks APISIX route config |
| Portal backend API implementation completeness | Team 2 | Unknown, needs verification | Blocks M0 E2E integration |

---

## Performance: AuthZ Adapter Caching Strategy

### Problem

Every HTTP request through APISIX triggers: APISIX → OPA → AuthZ Adapter → PostgreSQL. Without caching, every single API request hits the DB with a multi-table join across users, group_members, groups, assignments, roles, role_permissions, and permissions.

### Decision: Caffeine In-Process Cache + Materialized View (Optional Milestone)

**Caffeine (Spring @Cacheable)**:
- In-process JVM cache, keyed by user external_id, TTL ~60s
- Eliminates ~99% of DB hits under normal session-based usage patterns
- ~10 lines of code, zero infrastructure, zero Team 3 involvement
- Swap to Redis later by changing one Spring profile (service interface stays the same)

**Materialized View (Flyway migration)**:
- Pre-joins the 6-table authorization chain into a flat `authz_user_context` lookup
- Adapter query becomes `SELECT * FROM authz_user_context WHERE external_id = ?`
- Even on cache miss, query is fast. Benefits every future caching strategy.
- Refresh strategy: on-demand or scheduled (TBD based on write frequency)

**Why these two**:
- Lowest cost, lowest risk, highest impact for a rushed v2.0 release
- Zero new infrastructure or team dependencies
- Leaves all upgrade paths open (Redis, OPA bundles, read replicas)

**Rejected for now**:
- Redis: same benefit as Caffeine at single-instance scale, adds container + Team 3 dependency
- OPA Bundles: architectural pivot, more Rego complexity, save for post-2.0
- APISIX decision caching: custom Lua, security risk with stale allow/deny decisions
- DB read replica: ops burden, doesn't solve per-request hit without caching anyway

**Security note**: Stale cache (up to 60s) means a revoked permission could still grant access briefly. Acceptable tradeoff for v2.0; discuss shorter TTL or active invalidation (e.g., via Kafka events on assignment changes) for future hardening.
