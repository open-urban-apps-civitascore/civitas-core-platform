# CHANGELOG

Running log of changes made to the codebase by Claude Code.

For the latest change only, see `../CHANGELOG-LASTCHANGE.md`.

## 2026-02-05 (session 2 — idle maintenance + upstream monitoring)

- **chore**: E2E test ownership separation
  - Moved `oauth-flow.spec.ts` from `portal-frontend/e2e/` to `authz/e2e/tests/`
  - Our E2E tests now run independently from the frontend dataset/user E2E suite
  - Adapted credentials to use authz admin user

- **feat**: `/upstream` skill for GitLab API upstream monitoring
  - Two-tier MR flagging: Tier 1 (authz conflict risk), Tier 2 (security review)
  - Conflict detection: cross-reference our branch against upstream MR file changes
  - Security checklist gap detection (unchecked Secure Development Guide boxes)
  - ADR tracking, pipeline health, deployment repo monitoring
  - Skill subsequently moved to private cc-watchtower repo (see below)

- **feat**: Watchtower architecture for private upstream intelligence
  - Created cc-watchtower as separate private repo (control plane)
  - Workstream-based digests: authz, security, dx
  - Symlink integration: authz digest flows into feature repo via gitignored symlink
  - Project config (`config/projects.yaml`) defines monitored repos + filter criteria

- **chore**: Preflight improvements
  - Added automated test count verification (parse counts, compare to TESTING.md)
  - Added `pnpm audit` vulnerability scan step
  - Updated E2E command to `authz/e2e/` only

- **docs**: Handoff note for Next.js vulnerability (H-004/TD-020)
- **docs**: Backlog updates: TD-020/21/22, F-007–010, TD-011/12 status
- **docs**: Fixed stale test counts (backend 17→32, frontend E2E split)
- **docs**: New gotchas: OPA arm64 binary, JUnit nested classes, E2E ownership

## 2026-02-05

- **feat(M5.5)**: Collection endpoint filtering - COMPLETE
  - Filter collection endpoints based on user's permission scopes
  - OPA returns `X-Allowed-Scope-Ids` header via `send_headers_upstream`
  - Backend `AllowedScopesFilter` parses header into `@RequestScope` bean
  - Services use `preProcessQuery()` to apply JPA specification filtering
  - TENANT-scoped users get wildcard (`*`), others get specific scope IDs
  - Header size limits: 32KB in APISIX and Spring Boot

- **fix**: RoleService LazyInitializationException
  - Fixed `LazyInitializationException` when listing roles with permissions
  - Added `findByIdsWithPermissions()` to RoleRepository with JOIN FETCH
  - Override `findAll()` in RoleService to eagerly fetch permissions

- **docs**: Updated ADR-001 with Java implementation details for Team 2

## 2026-02-04 (continued from CHANGELOG-LASTCHANGE.md)

- **docs(M5.5)**: ADR for collection endpoint authorization filtering
  - Created ADR for F-004 (scope enforcement for collection endpoints)
  - Decision: Use APISIX OPA plugin's `send_headers_upstream` to pass allowed scope IDs via `X-Allowed-Scope-Ids` header
  - Wildcard (`*`) for TENANT-scoped users to skip filtering
  - Header size limits: 32KB (configurable in both APISIX and Spring Boot)
  - Alternatives evaluated: OPA Partial Evaluation (too complex), Backend calls AuthZ Repository (violates PDP principle)

- **feat(M5.1)**: Scope enforcement for resource endpoints
  - OPA now verifies permission scope matches the resource being accessed
  - Resource endpoints (`/v2/datasets/{id}`) require scopeId to match the resource ID
  - TENANT-scoped resources (users, groups, roles) require scopeType=TENANT
  - Collection endpoints allow any scope (backend filters results)
  - Added `resource_id`, `expected_scope_type`, `is_resource_endpoint` to portal_backend provider
  - Updated `permission_eval.rego` with three scope-aware `user_has_permission` rules
  - Added scope enforcement tests (6 new tests, 142 total)
  - Added Q-005 to backlog: clarify TENANT scope semantics with PO
  - Security fix: Previously user with permission for resource-A could access resource-B

## 2026-02-03

- **docs**: Added Keycloak JWKS requirements to Team 3 handoff
  - New section: APISIX JWT Validation with dynamic JWKS from Keycloak
  - Documented production openid-connect config (no static public_key)
  - Added network requirements for APISIX→Keycloak connectivity
  - Troubleshooting guide for JWKS issues
  - Updated architecture diagram to show Keycloak dependency

- **docs**: Clarified M5 user context flow
  - Added User Context Flow diagram to MILESTONES.md M5 section
  - Explained that OPA fetches user_context via http.send() to AuthZ Repository
  - Clarified Keycloak JWT provides externalId (sub), AuthZ Repository does the mapping
  - Updated authz/rego/README.md with User Context section

- **fix**: Pre-M5 code review fixes
  - Added fail-secure handling for missing user context in OPA (new `missing_user_context` reason)
  - Added null safety guards in `UserContextService.java` for null groups/assignments/roles
  - Fixed handoff doc API endpoint: `/api/v1/user-context/{externalId}` (was wrong path)
  - Added security note to handoff doc: network policies + Linkerd mTLS required
  - Fixed OPA health check to use `/opa eval` (static image has no wget)
  - Added 3 new tests for missing user context scenarios (114 total tests)

- **docs**: Created Team 3 handoff for AuthZ deployment
  - OPA deployment (container, bundles, volumes)
  - AuthZ Repository deployment (env vars, DB requirements)
  - APISIX OPA plugin configuration
  - Security note on URL normalization (APISIX handles it)
  - Verification steps and troubleshooting

- **feat(M4.6)**: Provider architecture and OPA bundle packaging
  - Created `lib/genericrestmapper.rego` with reusable path validation and pattern matching
  - Created `providers/portal_backend.rego` wrapping genericrestmapper for Portal Backend API
  - Created `providers/frost_server.rego` stub (OData parsing deferred to F-005)
  - Refactored `resource_mapping.rego` as dispatcher routing to providers
  - Added FROST Server data stub (`backends/frost_server/data.json`)
  - Created OPA bundle infrastructure (`.manifest`, `build-bundle.sh`)
  - Added GitLab CI pipeline (`.gitlab/ci/authz.yml`): lint-authz, check-authz, test-authz, bundle-authz
  - Updated docker-compose.yml for new directory structure
  - Reorganized tests into `test/lib/`, `test/providers/`, `test/policy/`
  - All 111 Rego tests pass (was 76, added 35 new tests)
  - Bundle verified working with OPA eval

- **fix(M4.6)**: Build script and CI cleanup
  - Fixed bundle directory permissions for OPA container (non-root user)
  - Fixed bundle structure (backends/ not data/backends/) for correct data paths
  - Removed hardcoded platform (linux/arm64) from scripts for portability
  - Fixed CI job naming to match convention (lint-authz, check-authz, etc.)
  - Removed incorrect Cobertura coverage claim from CI

### M4: Security Hardening & Review Comments
- **R-012**: Clarified permission_eval.rego header comments (module provides primitives, main.rego makes decisions)
- **R-013**: Added fail-secure design explanation (Rego undefined semantics provide fail-secure behavior)
- **R-014**: Removed unused `lookup_info` debug helper, kept tested `all_user_permissions`
- **R-015**: Added backend ID validation (alphanumeric, dashes, underscores only) - prevents injection
- **R-016**: Added path validation with defense-in-depth checks:
  - Reject path traversal (`..`)
  - Reject null bytes
  - Reject backslashes
  - Path length limit (2048 chars)
- **R-017**: Added design notes explaining exact-match-first pattern for backend-specific routes
- Added 8 new security tests (66 total, all passing)
- Fixed OPA data loading path: `backends/portal_backend/data.json` (OPA drops filenames from data path)

### APISIX Security Documentation
- Researched APISIX OPA plugin: uses `ctx.var.uri` (normalized path) - safe
- Added security note to `apisix.yaml` explaining URL normalization for Team 5

### Documentation Updates
- Updated REGO_REQUIREMENTS_TRACEABILITY.md with new security requirements (R-SEC-1 through R-SEC-4)
- Updated test coverage: 66 tests across 4 modules
- Resolved TD-018 (URL normalization trust assumption)
- Added TD-017 (data file schema validation), F-003 (provider architecture), F-004 (scope enforcement)

## 2026-02-02

- **refactor**: Extract `is_known_endpoint` rule for fail-secure clarity
  - Unknown endpoints now explicitly checked via named rule
  - Replaces inline `required_permission != ""` checks in main.rego
  - 58 tests pass (added 3 tests for is_known_endpoint)

- **refactor**: Remove more dead code from resource_mapping.rego (R-010, R-011)
  - Removed `is_special_segment` for health/liveness/readiness (public endpoints handled at APISIX)
  - Removed LEGACY EXPORTS section (`resource_path`, `has_resource_id`, `resource_id`)
  - 55 tests pass (was 60)

- **docs**: Answer backlog questions from DB schema and stakeholder input
  - Q-001: Scope inheritance confirmed: TENANT → DATASPACE → DATASET (hierarchical)
  - Q-002: Dataset→Dataspace mapping exists via `dataset_dataspaces` M:N join table
  - Q-003: RELEASE/USE operations deferred to later implementation (F-002)
  - Q-004: DataSource, DataStructure, Tag tables don't exist in current schema (deferred/FROST-specific)

- **refactor**: Remove public_endpoints.rego (dead code)
  - Public endpoints handled at APISIX level (routes without auth plugins)
  - Removed `public_endpoints.rego`, `public_endpoints_test.rego`
  - Removed `public_endpoints` from backend data files
  - OPA only sees requests that require authorization
  - 60 tests pass (was 62, removed 16 public endpoint tests, added 14 edge case tests)

- **fix**: Path parsing edge cases
  - Trailing slash `/v2/users/` no longer matches `/v2/users/{id}` (empty ID rejected)
  - Sub-resource paths `/v2/users/123/groups` no longer match (only 3 segments supported)
  - Added `edge_cases_test.rego` documenting behavior for malformed paths

- **docs**: OPA decision logging
  - Verified `decision_logs.console=true` is configured in docker-compose.yml
  - Logs all decisions to stdout in JSON format
  - Added documentation to traceability doc

- **refactor**: Rego policies use backend mappings file (TD-016)
  - Backend identified via `X-Authz-Backend` header (set by APISIX)
  - Permission lookup from `data/backends/{backend}.json` instead of dynamic computation
  - Path pattern matching: `/v2/users/123` → `/v2/users/{id}` for endpoint lookup
  - Supports multi-backend (portal-backend now, frost-server next)
  - Updated APISIX dev config to set `X-Authz-Backend: portal-backend`
  - All 62 Rego tests pass
  - Created handoff doc for Team 1 (Config Adapter header requirement)

- **docs**: Rego requirements traceability review
  - Created `docs/claude/task-info/REGO_REQUIREMENTS_TRACEABILITY.md`
  - Mapped all Rego code to requirements from REQUIREMENTS.md and Authorization_Data_Model
  - Verified implementation matches spec intent
  - **Critical gap found** (TD-016): Rego computes permissions dynamically instead of reading from backend mappings file - blocks multi-backend (FROST) and non-CRUD ops (RELEASE/USE)
  - Minor gaps: HEAD→EXISTS (TD-015), RELEASE/USE clarification (Q-003)
  - Core M4 requirements confirmed as met; M4.5 scope enforcement correctly deferred

- **fix**: Resolve E2E test JWT validation failure (TD-013)
  - Root cause: Keycloak returns localhost-based URLs in discovery, APISIX (inside Docker) can't reach them
  - Keycloak ignores hostname config without HTTPS (security measure against hostname spoofing)
  - Solution: Static public_key in APISIX for dev (TD-014) - same RSA validation, just pre-configured
  - Production with HTTPS: remove public_key, use dynamic JWKS (auto key rotation)
  - All E2E smoke tests pass

- **docs**: Update testing documentation
  - Added comprehensive test types overview to TESTING.md
  - Added quick reference commands for running all test suites
  - Documented E2E infrastructure requirements
  - Added TD-013 to BACKLOG.md for E2E authentication issue

- **refactor**: Integrate `portal-model` into `authz/repository`
  - Removed 5 duplicated entity classes (User, Group, Role, Permission, Assignment) from authz/repository
  - Added portal-model dependency to authz/repository/pom.xml
  - Updated service layer to use portal-model enums (RoleType, ScopeType, PermissionType)
  - Added `@EnableJpaAuditing` and `@EntityScan` to AuthzRepositoryApplication
  - Added `assignments` OneToMany relationship to portal-model Group entity
  - Updated integration test schema and seed data for portal-model entity fields
  - All unit and integration tests pass

- **refactor**: Extract `portal-model` as standalone Maven module
  - Moved 21 files (16 entities + 5 embedded types) from `portal-backend`
  - Enables AuthZ repository to share JPA entity definitions
  - No import changes required (package names preserved)

## 2026-01-30

### M4: OPA + Rego Policies — COMPLETE
- Added OPA to dev-environment (`dev-environment/opa/docker-compose.yml`)
- Created Rego policy structure in `authz/rego/`:
  - `policy/main.rego` - Entry point with allow/deny decision and detailed reason
  - `policy/resource_mapping.rego` - URL path → resource type + operation mapping
  - `policy/permission_eval.rego` - Permission evaluation (resource-level, no scope checks yet)
  - `policy/public_endpoints.rego` - Public endpoints that bypass authorization
  - `data/portal_backend_mappings.json` - Permission mappings from portal-backend API
- Created comprehensive Rego test suite (49 tests, all passing):
  - `test/main_test.rego` - Integration tests for decision logic
  - `test/resource_mapping_test.rego` - URL parsing tests
  - `test/permission_eval_test.rego` - Permission checking tests
  - `test/public_endpoints_test.rego` - Public endpoint detection tests
- OPA running and verified via HTTP API:
  - Public endpoint → `{"allowed": true, "reason": "public_endpoint"}`
  - Permission granted → `{"allowed": true, "reason": "permission_granted", "permission": "READ_USER"}`
  - Permission denied → `{"allowed": false, "reason": "permission_denied", "required": "DELETE_USER"}`
  - Self-access (/users/me) → `{"allowed": true, "reason": "self_access"}`
- Note: Scope enforcement (TENANT → DATASPACE → DATASET) deferred to M4.5
- Note: AuthZ Repository integration (external data source) deferred to M5

### Handoff: portal-model Extraktion
- Created handoff document for Team 2: `docs/claude/handoff/TEAM2-PORTAL-MODEL-EXTRAKTION.md`
- Proposal to extract shared JPA entities into separate `portal-model` module
- Enables authz-repository to use shared entities without code duplication
- Blocked on Team 2 completing the extraction

### Code Review Processing
- **R-001/R-002**: Simplified APISIX openid-connect config
  - Replaced client_id/client_secret with "unused" (required by plugin syntax but not used)
  - Removed public_key fallback (trust network reliability)
- **R-003**: Moved E2E smoke tests to `authz/e2e/` to keep authz code together
- **R-004**: Clarified APISIX gateway routing
  - Added comments in `.env.local.template` explaining gateway URL config for Team 3
  - Added comments in `route.ts` explaining the BFF → APISIX → Backend flow
  - Verified traffic flows through APISIX (confirmed via access logs)
- **R-005**: Renamed `authz/adapter` → `authz/repository`
  - Package: `de.civitascore.authz.adapter` → `de.civitascore.authz.repository`
  - Inner package: `.repository` → `.data` (avoid double "repository")
  - Application: `AuthzAdapterApplication` → `AuthzRepositoryApplication`
  - All 19 tests passing
- **R-006**: Decided to keep persistence separate from portal-backend (security boundary)
- **R-007**: Kept Lombok (no security concerns, compile-time only)
- **R-008**: Kept @Setter on entities (needed for JPA/tests), read-only enforced at DB level
- **R-009**: Added table names list in application.yaml comment

## 2026-01-28

### M2: APISIX JWT Validation — COMPLETE
- Added `openid-connect` plugin to `dev-environment/apisix/apisix_conf/apisix.yaml`
- Configured local JWT validation with Keycloak public key (extracted from JWKS)
- Settings: `bearer_only: true`, `set_userinfo_header: true` (for OPA in M4)
- Public health endpoint `/v2/actuator/health` remains unauthenticated
- Verified: no token → 401, valid token → 200, invalid token → 401
- Added `groups` protocol mapper to portal-frontend client in Keycloak
- Created `test-users` group and added testuser to it
- Verified: JWT now contains `"groups": ["test-users"]`

### M3: AuthZ Adapter Service — COMPLETE
- Created `/authz/adapter/` Spring Boot project (Java 21, Spring Boot 3.5.0, Spring Data JPA)
- Implemented JPA entities: User, Group, Assignment, Role, Permission (matching portal-backend schema)
- Implemented `GET /api/v1/user-context/{externalId}` endpoint
  - Single query with JOIN FETCH for users → groups → assignments → roles → permissions
  - Returns nested JSON with groups, assignments (role + scope), and permissions
- Created Dockerfile: multi-stage build, non-root user, healthcheck on `/actuator/health`
- Integration tests with Testcontainers (9 tests, ~8s)
  - Core: single group, multiple groups, no groups, unknown user (404), health endpoint
  - Edge cases: group with no assignments, role with no permissions, 10+ permissions sorted, same role at different scopes
- Unit tests for UserContextService (10 tests): mapping logic, null handling, sorting
- Fixed Docker 29+ compatibility: added `docker-java.properties` with `api.version=1.44`

### M3 Scope Update
- Removed hierarchical groups and inherited assignments from M3 requirements (out of scope for this release)
- Updated MILESTONES.md to reflect flat group membership and direct assignments only

### B-002: BFF Cookie Forwarding Causes 400 Errors
- **Root Cause**: BFF proxy route forwarded ALL headers including the huge NextAuth session cookie to backend
- Large cookie header (several KB) exceeded Tomcat's default max header size, causing HTTP 400
- **Fix**: Added `headers.delete('cookie')` in `src/app/api/[...path]/route.ts` after extracting session token
- The cookie is only needed for NextAuth session extraction; backend only needs the Bearer token
- All 5 smoke tests now pass

### Spring Clean
- Deleted `.DS_Store` files from repo root and `test-results/`
- Added `**/.DS_Store` to `.gitignore` to prevent future tracking
- Removed `.DS_Store` from git index
- Checked Docker container logs: found B-001 bug (user creation fails due to missing `active` default)
- Updated documentation (CHANGELOG, BACKLOG with 10 tech debt items, MILESTONES)
- Fixed lint errors: prettier formatting in `e2e/base-test.ts`, import sort in `playwright/auth.setup.ts`, eslint-disable for Playwright `use()` fixture
- Fixed B-001: user creation failing due to null `active` field — added default `true` in `UserInputDTO.java`

### E2E Test Data: Seed & Teardown Scripts
- Added `e2e/scripts/seed-test-users.sh`: seeds 5 internationally diverse test users (Anna Schmidt, Kenji Tanaka, Amira Okafor, Carlos Rivera, Priya Sharma) into PostgreSQL + Keycloak
- Added `e2e/scripts/teardown-test-users.sh`: removes the same 5 users from both stores
- Both scripts are idempotent, use fixed UUIDs and `@e2e.civitas.dev` email domain
- Scripts auto-detect psql availability and fall back to `docker exec` when not installed locally
- Simplified smoke test to navigate to `/users` page and assert table renders with seeded data

## 2026-01-27

### Environment Setup (new VM with UTM Apple Virtualization backend)
- Installed Docker, Java 21 (Temurin), Maven 3.8.7, Node 22, pnpm (corepack)
- Started PostgreSQL, Keycloak (with civitas-core realm), Keycloak DB containers
- Next.js dev server starts in ~2.9s on VirtIO (9p workaround no longer needed)

### M0: E2E Integration Baseline — COMPLETE
- Fixed `portal-backend` config-adapter dependency version (`0.0.0-671-ge1279f8` → `1.0.2`) to match local build
- Fixed `application-local.yaml`: Keycloak URL `http://keycloak:8080` → `http://localhost:8080`, realm `master` → `civitas-core`
- Regenerated Keycloak client secret for `portal-frontend`, updated `.env.local`
- Added `API_BASE_URL` and `API_PORT` to `.env.local` (missing from previous setup)
- Created Keycloak test user (`testuser` / `testpass123`)
- Added `pnpm.onlyBuiltDependencies` to `portal-frontend/package.json` for non-interactive native builds
- Verified full E2E flow: browser → Next.js BFF → Keycloak PKCE login → session cookie → proxy → Spring Boot → PostgreSQL → JSON response

### M0.5: Playwright Smoke Test Harness — COMPLETE
- Fixed Playwright version mismatch (`playwright` 1.57→1.55.1 to match `@playwright/test`)
- Added `e2e/smoke.spec.ts`: authenticated dashboard load + BFF proxy→backend API call
- Added `e2e/smoke-unauth.spec.ts`: unauthenticated redirect to login + 401 on protected API
- Added `noAuth` project to `playwright.config.ts` for unauthenticated tests
- Added `testIgnore` for `smoke-unauth.spec.ts` on chromium/firefox/webkit projects
- All 5 smoke tests pass (auth setup + 2 authenticated + 2 unauthenticated)

### M1: APISIX Gateway (Passthrough) — COMPLETE
- Switched APISIX to standalone mode (no etcd dependency)
- Removed etcd service from `dev-environment/apisix/docker-compose.yml`
- Rewrote `apisix_conf/config.yaml`: `data_plane` role with `yaml` config provider, fixed ports (9080/9443)
- Created `apisix_conf/apisix.yaml`: declarative route `/v2/*` → `host.docker.internal:8089`
- Added `extra_hosts: host.docker.internal:host-gateway` for Linux Docker compatibility
- Updated `portal-frontend/.env.local` and `.env.local.template`: `API_PORT` 8089→9080
- Traffic flow: Browser → Next.js BFF (:3000) → APISIX (:9080) → Backend (:8089)
- Installed missing Playwright browsers (Firefox 141.0, WebKit 26.0) and system dependencies
- All 9 smoke tests pass across chromium/firefox/webkit (15.4s)

### Playwright: per-step screenshots & chromium-only
- Removed firefox/webkit projects from `playwright.config.ts` (chromium only, 5 tests, ~14s)
- Created `e2e/base-test.ts`: custom fixture auto-screenshots on every page load/navigation
- Updated smoke tests and auth setup to import from `base-test`
- Screenshots saved to repo-root `/test-results/` (numbered per step: `001-load_login.png`, etc.)
- Enabled `trace: 'on'` for full per-action traces alongside individual PNGs
- Added `/test-results/` to root `.gitignore`
