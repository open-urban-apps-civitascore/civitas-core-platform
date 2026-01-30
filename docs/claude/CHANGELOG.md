# Changelog

## 2026-02-03

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
