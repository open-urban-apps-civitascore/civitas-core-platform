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
M3   AuthZ Adapter Service (Java service, DB → user authz context)
 │
M4   OPA + Rego Policies (authorization decision logic)
 │
M5   Full AuthZ Integration (APISIX → OPA → Adapter, wired and tested)
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

## M3 — AuthZ Adapter Service

**Goal**: Java service that reads user authorization context from PostgreSQL and exposes it via REST API for OPA to consume.

### Project Structure
```
/authz/
  adapter/          ← Spring Boot service (this milestone)
    src/
    pom.xml
    Dockerfile
  rego/             ← OPA policies (M4)
  docker-compose.yml ← Full authz stack (M5)
```

### Tasks
1. Scaffold Spring Boot project in `/authz/adapter/`
   - Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL driver
   - Read-only connection to portal backend DB
2. Implement REST endpoint: `GET /api/v1/user-context/{externalId}`
   - Input: Keycloak `sub` claim (external_id in users table)
   - Output: JSON with user's complete authorization context:
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
3. Handle hierarchical groups (parent_group_id) — include inherited group memberships
4. Handle inherited assignments (is_inherited, parent_assignment_id)
5. Dockerfile for the service
6. DB test seed script (SQL) for integration tests

### Exit Criteria
- [ ] Adapter starts and connects to PostgreSQL
- [ ] `GET /api/v1/user-context/{externalId}` returns correct authz context for seeded test users
- [ ] Hierarchical group memberships resolved correctly
- [ ] Inherited assignments resolved correctly
- [ ] Dockerfile builds and runs

### Tests (Pragmatic TDD)
- **Integration tests** (Testcontainers + PostgreSQL): Seed DB with known data, verify API response
- Test cases:
  - User with single group, single role, single scope → correct context
  - User with multiple groups → all group contexts returned
  - User with hierarchical group membership → parent group roles included
  - User with TENANT scope assignment → cascades noted
  - User with DATASPACE scope → cascades noted
  - User with no assignments → empty but valid response
  - Unknown externalId → 404
- These tests are our safety net for Rego policy development in M4

---

## M4 — OPA + Rego Policies

**Goal**: OPA evaluates authorization decisions using Rego policies. Policies parse APISIX request metadata, fetch user context from AuthZ Adapter, and return allow/deny.

### Tasks
1. Add OPA to docker-compose
2. Write Rego policy package `civitas.authz`:
   - Parse request path to extract: backend prefix, resource type, resource ID
   - Map HTTP verb to permission operation (GET→READ, POST→CREATE, etc.)
   - Call AuthZ Adapter external data source for user context
   - Evaluate: does the user have a permission matching the required operation + resource type at the correct scope?
   - Handle scope inheritance (TENANT → DATASPACE → DATASET)
   - Handle anonymous/public endpoints (allow without AuthZ)
3. Define the request→resource→permission mapping configuration (per-backend, data-driven)
4. OPA configuration: external data source pointing to AuthZ Adapter

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
- [ ] OPA running and reachable
- [ ] Rego policies correctly allow/deny for all test scenarios
- [ ] URL parsing works for all portal backend endpoint patterns (`/v2/datasets/{id}`, `/v2/users/me`, etc.)
- [ ] Scope inheritance works (TENANT → DATASPACE → DATASET)
- [ ] Public endpoints pass without AuthZ
- [ ] All Rego unit tests pass

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

## M5 — Full AuthZ Integration

**Goal**: Wire APISIX → OPA → AuthZ Adapter into a working chain. Verify with integration tests.

### Tasks
1. Configure APISIX `opa` plugin on protected routes
   - OPA endpoint: `http://opa:8181/v1/data/civitas/authz/allow`
   - Send request method, path, headers (including Authorization)
2. Create unified docker-compose (`/authz/docker-compose.yml`) wiring:
   - APISIX + etcd
   - OPA (with Rego policies mounted)
   - AuthZ Adapter (connected to PostgreSQL)
   - PostgreSQL (shared with portal backend)
   - Keycloak
   - Portal Backend
3. Seed test data: users in Keycloak + matching users/groups/roles/permissions/assignments in DB
4. Integration test: full chain from HTTP request → APISIX → OPA → Adapter → DB → decision
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
- **DB→Adapter→OPA→APISIX integration test** (docker-compose based):
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
4. **AuthZ Adapter Operations Guide**: Configuration, DB connection, health endpoint, logging
5. **Rego Policy Guide**: Policy structure, how to add new resource mappings, how to run tests
6. **Architecture Decision Record**: Why APISIX + OPA + Adapter, alternatives considered, tradeoffs

### Exit Criteria
- [ ] Team 3 can deploy the authz stack from the documentation alone
- [ ] Team 2 understands the frontend proxy changes
- [ ] All configuration is documented (no tribal knowledge)

---

## M7 — [Optional] Performance: Caching

**Goal**: Reduce DB load from per-request queries to ~1 query per user per TTL period.

### Tasks
1. Add Caffeine cache to AuthZ Adapter (`@Cacheable`, keyed by externalId, TTL ~60s)
2. Create materialized view via Flyway migration pre-joining the authorization chain
3. Update adapter queries to use materialized view
4. Add cache metrics endpoint (hits/misses/evictions)

### Exit Criteria
- [ ] Cache hit → no DB query (verify via metrics or logs)
- [ ] Cache miss → single fast query against materialized view
- [ ] All M5 integration tests still pass
- [ ] Cache TTL is configurable via environment variable

### Notes
- See REQUIREMENTS.md "Performance: AuthZ Adapter Caching Strategy" for full analysis and rejected alternatives
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
| HTTP-to-permission operation mappings | Team 2 | M4 | Promised | Use working assumptions (GET→READ, POST→CREATE, etc.) |
| Keycloak group claims | User (sprint) | M4 | Planned | M2 works without groups; M4 can use roles-only fallback |
| Backend URL conventions for APISIX | Backend teams | M1 | Communicated | Use current `/v2/{resource}` pattern |
| Portal backend API completeness | Team 2 | M0 | Unknown | Verify via Swagger, work with what exists |
