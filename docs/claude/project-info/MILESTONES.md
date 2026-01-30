# MILESTONES

Project plan for: Centralized AuthZ with APISIX and OPA

## Overview

```
M0   E2E Integration Baseline (existing stack works)
 │
M0.5 Playwright Smoke Test Harness (browser-level regression gate)
 │
M1   APISIX Gateway — Passthrough (traffic routed through APISIX, no auth)
 │
M2   AuthN — APISIX JWT Validation (token validation via Keycloak JWKS)
 │
M3   AuthZ Repository Service (Java service, DB → user authz context)
 │
M4   OPA + Rego Policies (resource-level permissions, no scope checks)
 │
M4.5 Scope Enforcement (TENANT → DATASPACE → DATASET hierarchy) [DEFERRED - may be cut from v2]
 │
M4.6 Provider Architecture & OPA Bundles (modular backends, production hardening)
 │
M5   Full AuthZ Integration (APISIX → OPA → Repository, wired and tested)
 │
M6   Documentation & Handoff
 │
M7   [Optional] Performance — Caching (Caffeine + materialized view)
```

Each milestone builds on the previous. No milestone should be started until its predecessor's exit criteria are met.

**Regression rule**: The M0.5 Playwright smoke test is the shared definition of "it works." It runs after every milestone. If it fails, the milestone is not done — regardless of what backend tests say.

---

## Completed

### Documentation Restructuring (2026-01-26)
- Created modular `/docs/claude/` structure
- Compacted CLAUDE.md to quick reference format
- Established changelog, backlog, and milestone tracking

### Dev Environment Setup (2026-01-26)
- Fixed PostgreSQL, Keycloak, backend, frontend configuration issues
- Created custom dev-server.cjs for Next.js on 9p filesystem
- Resolved TD-001 (Keycloak bootstrap), TD-002 (backend realm), TD-003 (pnpm native builds)
- Fixed CSP blocking eval() in Next.js dev mode

### M0 — E2E Integration Baseline (2026-01-27)
- Full stack running: PostgreSQL + Keycloak + Portal Backend + Portal Frontend
- Created Keycloak test user (testuser / testpass123)
- Verified full E2E flow: browser → Next.js BFF → Keycloak PKCE login → backend → PostgreSQL
- Test data seeded via `e2e/scripts/seed-test-users.sh`

### M0.5 — Playwright Smoke Test Harness (2026-01-27)
- Added `e2e/smoke.spec.ts`: authenticated dashboard + BFF proxy→backend API call
- Added `e2e/smoke-unauth.spec.ts`: redirect to login + 401 on protected API
- All 5 smoke tests pass (auth setup + 2 authenticated + 2 unauthenticated)
- `pnpm test:e2e` runs headless via Playwright

### M1 — APISIX Gateway Passthrough (2026-01-27)
- Switched APISIX to standalone mode (no etcd); declarative YAML routes
- Route `/v2/*` → portal backend via `host.docker.internal:8089`
- Frontend BFF retargeted to APISIX (:9080)
- All 9 smoke tests pass (chromium/firefox/webkit, 15.4s)

### M2 — AuthN: APISIX JWT Validation (2026-01-28)
- Added `openid-connect` plugin to APISIX route config
- Local JWT validation using Keycloak public key (avoids JWKS URL resolution issues in Docker)
- `bearer_only: true` — only validates tokens, no login redirect
- `set_userinfo_header: true` — passes decoded JWT claims to backend (prep for OPA)
- curl tests: no token → 401, valid token → 200, invalid token → 401
- BFF already forwards `Authorization: Bearer` header (verified in code)
- Added `groups` protocol mapper to Keycloak client → JWT now includes user's groups

### M3 — AuthZ Repository Service (2026-01-28)
- Created `/authz/repository/` Spring Boot project (Java 21, Spring Boot 3.5.0, Spring Data JPA)
- JPA entities: User, Group, Assignment, Role, Permission (read-only from portal-backend DB)
- REST endpoint: `GET /api/v1/user-context/{externalId}` returns nested authz context
- Single JOIN FETCH query for efficient eager loading of entire permission graph
- Dockerfile: multi-stage build, non-root user, healthcheck
- 19 tests total: 10 unit tests + 9 integration tests (Testcontainers + PostgreSQL)
- Note: Flat group membership only; hierarchical groups out of scope for this release

### M4 — OPA + Rego Policies (2026-01-30, updated 2026-02-03)
- Added OPA to docker-compose (`dev-environment/opa/docker-compose.yml`)
- Created Rego policy structure in `authz/rego/`:
  - `policy/main.rego` - Entry point with allow/deny decision
  - `policy/resource_mapping.rego` - URL path → resource type + operation (with security hardening)
  - `policy/permission_eval.rego` - Permission evaluation (resource-level)
  - `backends/portal_backend/data.json` - Permission mappings (OPA bundle structure)
- Security hardening (2026-02-03):
  - Backend ID validation (alphanumeric only)
  - Path validation (no traversal, null bytes, backslashes)
  - Defense-in-depth for URL normalization
- 66 Rego unit tests (all passing, including 8 security tests)
- HTTP API verified for all decision types
- 5/5 Playwright smoke tests pass (no regression)
- Note: Scope enforcement deferred to M4.5, APISIX wiring to M5
- Note: public_endpoints.rego removed (handled at APISIX level)

### M4.6 — Provider Architecture & OPA Bundles (2026-02-03)
- Created modular provider architecture for multi-backend support:
  - `lib/genericrestmapper.rego` - Reusable path validation and pattern matching
  - `providers/portal_backend.rego` - Portal Backend provider wrapping genericrestmapper
  - `providers/frost_server.rego` - FROST Server stub (OData parsing deferred)
- Refactored `resource_mapping.rego` as dispatcher routing to providers
- Created OPA bundle infrastructure:
  - `.manifest` with revision tracking and roots configuration
  - `build-bundle.sh` with pre-flight checks (format, type check, tests)
- Added GitLab CI pipeline (`.gitlab/ci/authz.yml`):
  - `lint-authz` - Format checking
  - `check-authz` - Strict type checking
  - `test-authz` - Unit tests
  - `bundle-authz` - Bundle build artifact
- Updated docker-compose.yml for new directory structure (lib/, providers/, backends/)
- Reorganized tests into `test/lib/`, `test/providers/`, `test/policy/`
- 111 Rego tests pass (was 76, added 35 new tests for library/providers)

---

## M0 — E2E Integration Baseline

**Goal**: Prove the existing stack works end-to-end: Browser → Frontend → Backend → PostgreSQL.

### Tasks
1. Start full stack: PostgreSQL + Keycloak + Portal Backend + Portal Frontend
2. Create Keycloak test users in `civitas-core` realm (at minimum: one admin, one regular user)
3. Verify backend API is functional (hit Swagger at `localhost:8089/v2/swagger-ui.html`, curl a few endpoints)
4. Verify frontend can authenticate via Keycloak and fetch data from backend through the BFF proxy
5. Seed DB with test data: at least one dataspace, one dataset, one group, roles, permissions, assignments

### Exit Criteria
- [x] Can log in via Keycloak from the frontend
- [x] Frontend successfully fetches and displays data from backend
- [x] Backend CRUD operations work (verified via Swagger or curl)
- [x] Test data seeded in DB for use by later milestones

### Tests (Pragmatic TDD)
- Manual verification is sufficient for M0
- Document the curl/Swagger commands that prove it works (reusable as sanity checks)

### Known Risks
- Frontend-to-backend integration is unverified and assumed broken
- Frontend startup on 9p filesystem is extremely slow (may need /tmp workaround)
- Backend API completeness is unknown

---

## M0.5 — Playwright Smoke Test Harness

**Goal**: Establish a browser-level regression test that becomes the shared definition of "it works" for every subsequent milestone. This is the user's view — if Playwright can't do it, the user can't do it.

### Tasks
1. Set up Playwright in `portal-frontend` (or a dedicated test project)
2. Write **login smoke test**: Open browser → navigate to app → redirected to Keycloak → log in → land on authenticated page → verify data loads from backend
3. Write **basic CRUD smoke test**: After login, perform at least one read operation that hits the backend API and verify the response renders in the UI
4. Make tests runnable via a single command (`pnpm test:e2e` or similar)

### Exit Criteria
- [x] `pnpm test:e2e` runs headless and passes
- [x] Login flow works end-to-end in the browser (Keycloak redirect → auth → return)
- [x] At least one backend data fetch is verified from the browser
- [x] Test fails clearly when any component is down (Keycloak, backend, DB)

### Regression Rule
This test suite runs after every subsequent milestone as a gate:
- **M1**: Same tests pass, but traffic now flows through APISIX (transparent to browser)
- **M2**: Same tests pass with AuthN enabled (BFF already forwards tokens)
- **M5**: Tests expanded with an AuthZ failure case (user without permissions gets 403/error in UI)
- If the smoke test fails after a milestone, that milestone is not done.

### Notes
- Keep tests minimal and fast. They exist to catch "it's broken from the browser" — not to test every feature.
- Tests should be resilient to UI changes (use data-testid attributes, not CSS selectors).

---

## M1 — APISIX Gateway (Passthrough)

**Goal**: Route all frontend→backend traffic through APISIX. No authentication or authorization yet — pure passthrough proxy.

### Tasks
1. Add APISIX + etcd to docker-compose (`dev-environment/apisix/docker-compose.yml`)
2. Configure APISIX route: proxy `/v2/*` to portal backend (`localhost:8089`)
3. Retarget frontend catch-all proxy (`src/app/api/[...path]/route.ts`) from backend to APISIX (`localhost:9080`)
4. Update `.env.local` / `.env.local.template` with APISIX URL
5. Verify all existing functionality still works through APISIX

### Exit Criteria
- [x] APISIX is running and accessible at `localhost:9080`
- [x] Frontend traffic flows: Browser → NextAuth BFF → APISIX → Backend
- [x] All M0 functionality still works (no regressions) — 9/9 smoke tests pass (chromium/firefox/webkit)
- [x] APISIX config is declarative YAML (standalone mode, no etcd/Admin API needed)

### Tests
- Re-run M0 verification steps — everything must still work
- Verify in APISIX access logs that requests are flowing through

### Notes
- APISIX route config should use path prefixes that allow future multi-backend routing (e.g., `/v2/*` for portal backend, future `/frost/*` for FROST server)
- Keep APISIX config declarative (yaml config, not Admin API calls) for reproducibility

---

## M2 — AuthN: APISIX JWT Validation

**Goal**: APISIX validates JWT Bearer tokens via Keycloak JWKS. Unauthenticated requests get 401. Authenticated requests pass through.

### Tasks
0. If current backlog already contains any AuthN/AuthZ code, remove it as AuthN/AuthZ will be handled by APISIX.
1. Configure APISIX `openid-connect` or `jwt-auth` plugin with Keycloak JWKS endpoint (`http://keycloak:8080/realms/civitas-core/protocol/openid-connect/certs`)
2. Apply plugin to protected routes (all `/v2/*` except explicitly public endpoints)
3. Ensure the BFF proxy in Next.js forwards the Bearer token (already does this — verify)
4. Configure Keycloak to include groups in JWT claims (coordinate with user's sprint ticket)
5. Test: authenticated request → 200, missing/expired/invalid token → 401

### Exit Criteria
- [x] Request with valid JWT → passes through to backend
- [x] Request without token → 401
- [x] Request with expired token → 401 (implicit: tokens are validated locally via JWKS)
- [x] Request with invalid signature → 401
- [x] Groups claim present in JWT

### Tests
- curl tests with valid/invalid/expired/missing tokens
- Frontend still works (token is forwarded correctly)

### External Dependencies
- Keycloak group claims configuration (user's sprint ticket)
  - **Not a blocker**: AuthN works without groups. Groups are needed for AuthZ (M4+).

---

## M3 — AuthZ Repository Service

**Goal**: Java service that reads user authorization context from PostgreSQL and exposes it via REST API for OPA to consume.

### Project Structure
```
/authz/
  repository/       ← Spring Boot service (this milestone)
    src/
    pom.xml
    Dockerfile
  rego/             ← OPA policies (M4)
  docker-compose.yml ← Full authz stack (M5)
```

### Tasks
1. Scaffold Spring Boot project in `/authz/repository/`
   - Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL driver
   - Read-only connection to portal backend DB
2. Implement REST endpoint: `GET /api/v1/user-context/{externalId}`
   - Input: Keycloak `sub` claim (external_id in users table)
   - Output: JSON with user's authorization context:
     ```json
     {
       "userId": "uuid",
       "externalId": "keycloak-sub",
       "groups": [
         {
           "id": "uuid",
           "name": "...",
           "assignments": [
             {
               "roleId": "uuid",
               "roleName": "...",
               "roleType": "DATA",
               "scopeType": "DATASPACE",
               "scopeId": "uuid",
               "permissions": ["DATASET_READ", "DATASET_CREATE", ...]
             }
           ]
         }
       ]
     }
     ```
3. Dockerfile for the service
4. DB test seed script (SQL) for integration tests

**Note**: Hierarchical groups and inherited assignments are out of scope for this release. Flat group membership and direct assignments only.

### Exit Criteria
- [x] Repository service starts and connects to PostgreSQL
- [x] `GET /api/v1/user-context/{externalId}` returns correct authz context for seeded test users
- [x] Dockerfile builds and runs
- [x] All 19 tests pass (10 unit + 9 integration)

### Tests (Pragmatic TDD)
- **Integration tests** (Testcontainers + PostgreSQL): Seed DB with known data, verify API response
- Test cases:
  - User with single group, single role, single scope → correct context
  - User with multiple groups → all group contexts returned
  - User with TENANT scope assignment → scope info included
  - User with DATASPACE scope → scope info included
  - User with no assignments → empty but valid response
  - Unknown externalId → 404
- These tests are our safety net for Rego policy development in M4

---

## M4 — OPA + Rego Policies

**Goal**: OPA evaluates authorization decisions using Rego policies. Policies parse APISIX request metadata, fetch user context from AuthZ Repository, and return allow/deny.

### Tasks
1. ✅ Add OPA to docker-compose (`dev-environment/opa/docker-compose.yml`)
2. ✅ Write Rego policy package `civitas.authz`:
   - ✅ Parse request path to extract: backend prefix, resource type, resource ID
   - ✅ Map HTTP verb to permission operation (GET→READ, POST→CREATE, etc.)
   - ⏳ Call AuthZ Repository external data source for user context → M5
   - ✅ Evaluate: does the user have a permission matching the required operation + resource type?
   - ⏳ Handle scope inheritance (TENANT → DATASPACE → DATASET) → M4.5
   - ✅ Handle anonymous/public endpoints (allow without AuthZ)
3. ✅ Define the request→resource→permission mapping configuration (`authz/rego/data/portal_backend_mappings.json`)
4. ⏳ OPA configuration: external data source pointing to AuthZ Repository → M5

### Rego Policy Structure (proposed)
```
authz/rego/
  policy/
    main.rego           ← Entry point: allow/deny decision
    resource_mapping.rego ← URL path → resource type + operation
    permission_eval.rego  ← Permission evaluation with scope inheritance
    public_endpoints.rego ← Endpoints that skip AuthZ
  test/
    main_test.rego
    resource_mapping_test.rego
    permission_eval_test.rego
  data/
    portal_backend_mappings.json  ← HTTP verb+path → permission operation (from Team 2)
```

### Exit Criteria
- [x] OPA running and reachable
- [x] Rego policies correctly allow/deny for all test scenarios
- [x] URL parsing works for all portal backend endpoint patterns (`/v2/datasets/{id}`, `/v2/users/me`, etc.)
- [ ] ~~Scope inheritance works (TENANT → DATASPACE → DATASET)~~ → Moved to M4.5
- [x] Public endpoints pass without AuthZ
- [x] All Rego unit tests pass (49 tests)

### Tests (Pragmatic TDD)
- **Rego unit tests** (OPA built-in test framework, `opa test`):
  - Resource mapping: various URL patterns → correct resource type + operation
  - Permission eval: user with DataArchitect role at DATASPACE scope → can READ datasets in that dataspace
  - Permission eval: user with DataConsumer role → cannot DELETE datasets
  - Scope inheritance: TENANT-scoped assignment → allows access to all dataspaces/datasets
  - Scope inheritance: DATASPACE-scoped assignment → allows access to datasets in that dataspace only
  - No matching assignment → deny
  - Public endpoint → allow regardless of user
  - Missing/unknown user → deny

### External Dependencies
- HTTP-to-permission operation mappings from Team 2 (use working assumptions until delivered)
- Backend URL pattern conventions (use current `/v2/{resource}` pattern)

---

## M4.6 — Provider Architecture & OPA Bundles

**Goal**: Modular Rego architecture for multi-backend support + production-ready OPA bundle packaging.

### Background
- Current Rego works for portal-backend REST patterns (`/version/resource/{id}`)
- FROST server uses OData patterns (completely different)
- Production needs atomic deployments, versioning, and integrity verification

### Tasks

#### Provider Architecture
1. Create `authz/rego/lib/genericrestmapper.rego`:
   - Reusable path pattern matching for REST APIs
   - `match_pattern(path, endpoints)` → matched pattern or ""
   - `parse_path(path)` → validated path parts
2. Create `authz/rego/providers/portal_backend.rego`:
   - Thin wrapper around genericrestmapper
   - Backend-specific configuration
3. Create `authz/rego/providers/frost_server.rego` (stub):
   - OData-specific parsing (to be implemented when FROST integration starts)
4. Update `resource_mapping.rego` to dispatch to providers based on `X-Authz-Backend` header
5. Move data files to sit alongside providers:
   ```
   authz/rego/
   ├── lib/
   │   └── genericrestmapper.rego
   ├── providers/
   │   ├── portal_backend/
   │   │   ├── provider.rego
   │   │   └── data.json
   │   └── frost_server/
   │       ├── provider.rego
   │       └── data.json
   └── policy/
       ├── main.rego
       ├── permission_eval.rego
       └── resource_mapping.rego  (dispatcher)
   ```

#### OPA Bundles
1. Create `.manifest` file with revision tracking and roots
2. Add CI pipeline step: `opa build -b . -o bundle.tar.gz`
3. Add pre-bundle validation:
   - `opa fmt --diff` (format check)
   - `opa check --strict` (type check)
   - `opa test` (unit tests)
4. Document bundle deployment workflow
5. [Optional] Add `.signatures.json` for integrity verification

### Exit Criteria
- [x] genericrestmapper works standalone with tests
- [x] portal_backend provider wraps genericrestmapper correctly
- [x] frost_server provider stub exists (OData parsing deferred)
- [x] Data files in backends/ directory (unchanged location for data.backends.* paths)
- [x] OPA bundle builds successfully
- [x] All 111 Rego tests pass (76 original + 35 new)
- [x] Bundle can be loaded by OPA in dev-environment

### Tests
- Unit tests for genericrestmapper (path parsing, pattern matching)
- Unit tests for each provider
- Integration test: bundle loads and works end-to-end
- Verify provider dispatch based on X-Authz-Backend header

### Notes
- This is a refactoring milestone — no new authorization logic
- FROST OData parsing is out of scope; just create the structure
- Bundle signing is optional for v2.0

---

## M5 — Full AuthZ Integration

**Goal**: Wire APISIX → OPA → AuthZ Repository into a working chain. Verify with integration tests.

### Tasks
1. Configure APISIX `opa` plugin on protected routes
   - OPA endpoint: `http://opa:8181/v1/data/civitas/authz/allow`
   - Send request method, path, headers (including Authorization)
2. Create unified docker-compose (`/authz/docker-compose.yml`) wiring:
   - APISIX + etcd
   - OPA (with Rego policies mounted)
   - AuthZ Repository (connected to PostgreSQL)
   - PostgreSQL (shared with portal backend)
   - Keycloak
   - Portal Backend
3. Seed test data: users in Keycloak + matching users/groups/roles/permissions/assignments in DB
4. Integration test: full chain from HTTP request → APISIX → OPA → Repository → DB → decision
5. Verify: authorized user gets data, unauthorized user gets 403

### Exit Criteria
- [ ] Full docker-compose stack starts successfully
- [ ] Authorized request: valid JWT + sufficient permissions → 200 + data
- [ ] Unauthorized request: valid JWT + insufficient permissions → 403
- [ ] Unauthenticated request: no/invalid JWT → 401
- [ ] Public endpoint: no JWT needed → 200
- [ ] APISIX logs show OPA plugin invocations
- [ ] OPA decision logs show correct evaluation

### Tests (Pragmatic TDD)
- **DB→Repository→OPA→APISIX integration test** (docker-compose based):
  - Seed DB with specific permission scenario
  - Send HTTP request with JWT for that user
  - Assert correct allow/deny from APISIX
  - This catches data format/naming mismatches between components
- Test scenarios:
  - DataArchitect reads dataset in their dataspace → 200
  - DataConsumer tries to delete dataset → 403
  - Platform Admin accesses system endpoint → 200
  - User with no group memberships → 403 on protected endpoints
  - Anonymous access to public endpoint → 200
- **Playwright smoke test expansion** (adds to M0.5 suite):
  - AuthZ failure case: User logs in, attempts action they lack permissions for → UI shows error/denied
  - This is the browser-level proof that AuthZ works end-to-end
- **Regression**: All existing M0.5 Playwright tests must still pass

---

## M6 — Documentation & Handoff

**Goal**: Production-ready documentation for Team 3 (deployment) and Team 2 (frontend).

### Deliverables
1. **APISIX Configuration Guide**: Route config, plugin config, environment variables, expected Keycloak settings
2. **Keycloak Requirements**: Required realm settings, client configs, group claims, token content expectations
3. **Kubernetes Requirements**: Container specs, resource requirements, networking (which services talk to which), health check endpoints, environment variables
4. **AuthZ Repository Operations Guide**: Configuration, DB connection, health endpoint, logging
5. **Rego Policy Guide**: Policy structure, how to add new resource mappings, how to run tests
6. **Architecture Decision Record**: Why APISIX + OPA + Repository, alternatives considered, tradeoffs

### Exit Criteria
- [ ] Team 3 can deploy the authz stack from the documentation alone
- [ ] Team 2 understands the frontend proxy changes
- [ ] All configuration is documented (no tribal knowledge)

---

## M7 — [Optional] Performance: Caching

**Goal**: Reduce DB load from per-request queries to ~1 query per user per TTL period.

### Tasks
1. Add Caffeine cache to AuthZ Repository (`@Cacheable`, keyed by externalId, TTL ~60s)
2. Create materialized view via Flyway migration pre-joining the authorization chain
3. Update repository queries to use materialized view
4. Add cache metrics endpoint (hits/misses/evictions)

### Exit Criteria
- [ ] Cache hit → no DB query (verify via metrics or logs)
- [ ] Cache miss → single fast query against materialized view
- [ ] All M5 integration tests still pass
- [ ] Cache TTL is configurable via environment variable

### Notes
- See REQUIREMENTS.md "Performance: AuthZ Repository Caching Strategy" for full analysis and rejected alternatives
- Security: 60s stale window is accepted for v2.0. Active invalidation (Kafka events) is a post-2.0 enhancement.
- Design the service interface so Redis can replace Caffeine later by changing one Spring profile.

---

## Cross-Cutting: Test Data Strategy

Test data is needed across milestones. Maintain a single seed data set that grows:

| Milestone | Keycloak | PostgreSQL |
|-----------|----------|------------|
| M0 | 2 users (admin + regular) | Basic: 1 dataspace, 1 dataset, 1 group, standard roles/permissions |
| M0.5 | Same | Same (Playwright uses same data) |
| M2 | Same | Same |
| M3 | Same | Expanded: multiple users, groups (including hierarchical), varied assignments at all scope levels |
| M4-M5 | Same + users matching DB test scenarios | Same as M3 + assignments designed for specific allow/deny scenarios |

Seed scripts:
- `dev-environment/keycloak/`: realm export with test users (or script to create them)
- `authz/test-data/seed.sql`: PostgreSQL seed data (idempotent, runnable multiple times)

---

## External Dependencies Tracker

| Dependency | Owner | Needed By | Status | Workaround |
|-----------|-------|-----------|--------|------------|
| HTTP-to-permission operation mappings (Portal) | Team 2 | M4 | **Delivered** | `backends/portal_backend/data.json` complete |
| HTTP-to-permission operation mappings (FROST) | Team 2 | F-005 | Not started | Stub provider denies all; OData parsing needed first |
| Keycloak group claims | User (sprint) | M4 | **Done** | Groups included in JWT via protocol mapper |
| Backend URL conventions for APISIX | Backend teams | M1 | **Done** | `/v2/{resource}` pattern established |
| Portal backend API completeness | Team 2 | M0 | **Done** | Swagger verified, all CRUD endpoints working |
| APISIX X-Authz-Backend header | Team 1 | M5 | Pending | H-002 handoff doc created; dev config has header |
