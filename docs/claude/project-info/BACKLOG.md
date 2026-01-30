# BACKLOG

Technical debt, feature ideas, and known bugs not currently implemented.

## Technical Debt

| ID | Description | Location | Priority |
|----|-------------|----------|----------|
| TD-001 | Skipped unit test: `should reset pageIndex if it exceeds totalPages` | `portal-frontend/src/hooks/use-query-params.test.ts:123` | Medium |
| TD-002 | Skipped unit test: `should map users correctly with matching authority and department` | `portal-frontend/src/utils/users.test.ts:58` | Medium |
| TD-003 | Skipped E2E tests: EditUser, UserList, CreateUser (entire describe blocks) | `portal-frontend/e2e/users/` | High |
| TD-004 | CSP `connect-src` production API domains not configured | `portal-frontend/src/middleware.ts:100` | Medium |
| TD-005 | `useGetAuthorities` hook marked for removal (not part of v2) | `portal-frontend/src/app/services/api/users/clientRequests.ts:12` | Low |
| TD-006 | UML modeler: relationship validation incomplete | `portal-frontend/src/app/(main)/uml-modeler/services/diagramService.ts:226` | Low |
| TD-007 | UML modeler: save/export buttons are no-ops (console.log only) | `portal-frontend/src/app/(main)/uml-modeler/components/layout/MultiSessionLayout.tsx:59,67` | Medium |
| TD-008 | UML modeler: unsaved changes uses `window.confirm()` instead of proper dialog | `portal-frontend/src/app/(main)/uml-modeler/components/layout/MultiSessionLayout.tsx:30` | Low |
| TD-009 | BaseAssembler missing generic type enforcement for PATCH support | `portal-backend/src/main/java/.../BaseAssembler.java:17` | Low |
| TD-010 | SBOM license name resolution broken, scan reports disabled in CI | `.gitlab/ci/{backend,frontend,config-adapter}.yml` (issue #728) | Medium |
| TD-011 | Dataset E2E tests failing: form elements not rendering | `portal-frontend/e2e/datasets/CreateDataset.spec.ts` | High |
| TD-012 | DatasetList E2E tests failing: "Failed to create new dataset" error | `portal-frontend/e2e/datasets/DatasetList.spec.ts` | High |
| ~~TD-013~~ | ~~E2E tests: auth token not passed to backend, causing "Error while loading data"~~ | ~~Fixed: Keycloak KC_HOSTNAME config~~ | ~~Resolved~~ |
| TD-014 | APISIX uses static public_key instead of dynamic JWKS (dev-only) | Keycloak (dev mode + HTTP) ignores hostname config for security reasons, returns localhost URLs that APISIX can't reach. Production with HTTPS uses dynamic JWKS. | Low |
| TD-015 | Rego: HEAD→EXISTS operation not mapped | `authz/rego/policy/resource_mapping.rego` - HEAD method falls through to UNKNOWN. Add if backend uses HEAD requests. | Low |
| TD-017 | Rego: No schema validation for backend data files | `authz/rego/backends/*/data.json` - Malformed JSON causes runtime errors, not startup failures. Consider OPA bundle with schema. | Low |
| ~~TD-018~~ | ~~Rego: URL normalization trust assumption~~ | ~~Resolved: APISIX OPA plugin uses `ctx.var.uri` (normalized). See helper.lua#L41. Rego validation is defense-in-depth.~~ | ~~Resolved~~ |
| ~~TD-016~~ | ~~Rego: permissions computed dynamically instead of using mappings file~~ | ~~Fixed 2026-02-02: Rego now reads from `data.backends.{backend}.endpoints`~~ | ~~Resolved~~ |

## Known Bugs

| ID | Description | Location | Priority |
|----|-------------|----------|----------|
| ~~B-001~~ | ~~User creation fails: `active` column has NOT NULL constraint but API doesn't set default~~ | ~~Fixed 2026-01-28~~ | ~~Resolved~~ |
| ~~B-002~~ | ~~BFF forwards cookie header causing Tomcat 400 error (header too large)~~ | ~~Fixed 2026-01-28~~ | ~~Resolved~~ |

## Review Comments

| ID | Description | Location | Status |
|----|-------------|----------|--------|
| ~~R-001~~ | ~~APISIX: Remove client_id/client_secret~~ | ~~`apisix.yaml:20`~~ | Done - replaced with "unused" + comment |
| ~~R-002~~ | ~~APISIX: Remove public_key fallback~~ | ~~`apisix.yaml:28`~~ | Done - removed |
| ~~R-003~~ | ~~Move E2E tests to authz folder~~ | ~~`portal-frontend/e2e/`~~ | Done - moved to `e2e/authz/` |
| ~~R-004~~ | ~~Clarify APISIX routing in BFF~~ | ~~`route.ts`, `.env.local.template`~~ | Done - added comments explaining gateway flow |
| ~~R-005~~ | ~~Rename "adapter" → "repository"~~ | ~~`authz/adapter/`~~ | Done - renamed to `authz/repository/` |
| ~~R-006~~ | ~~Reuse portal-backend persistence?~~ | - | Declined - keep separate for security boundary |
| ~~R-007~~ | ~~Lombok usage~~ | - | Kept - no security concerns, reduces boilerplate |
| ~~R-008~~ | ~~Remove @Setter~~ | ~~All entities~~ | Done - kept for JPA/testing, read-only enforced at DB level |
| ~~R-009~~ | ~~Specify authz table names~~ | ~~`application.yaml:9`~~ | Done - added table list in comment |
| ~~R-010~~ | ~~Rego: Remove health/liveness/readiness from is_special_segment~~ | ~~`resource_mapping.rego:91-93`~~ | Done - dead code (health endpoints are public, handled at APISIX) |
| ~~R-011~~ | ~~Rego: Remove LEGACY EXPORTS section~~ | ~~`resource_mapping.rego:95-121`~~ | Done - dead code (was used by deleted public_endpoints.rego) |
| ~~R-012~~ | ~~Rego: Clarify header comments in permission_eval.rego~~ | ~~`permission_eval.rego:1-12`~~ | Done - reworded to accurately describe what this module handles (null-permission IS here, decisions in main.rego) |
| ~~R-013~~ | ~~Rego: Consider fail-secure defaults for endpoint_config~~ | ~~`permission_eval.rego:23-26`~~ | Done - added comment explaining Rego's undefined semantics provide fail-secure behavior |
| ~~R-014~~ | ~~Rego: Review if debug helpers are actually used~~ | ~~`permission_eval.rego:118-129`~~ | Done - removed unused `lookup_info` (duplicates main.rego's `request_info`), kept tested `all_user_permissions` |
| ~~R-015~~ | ~~Rego: Path traversal concern with backend_data_key~~ | ~~`resource_mapping.rego:20-30`~~ | Done - Not a real risk (in-memory lookup, not filesystem). Added backend ID validation (alphanumeric only) + explanatory comment |
| ~~R-016~~ | ~~Rego: Path parsing bypass attacks (UTF encoding etc.)~~ | ~~`resource_mapping.rego:44-67`~~ | Done - Added is_valid_path with defense-in-depth checks (no .., no null bytes, no backslashes, length limit). 8 new security tests |
| ~~R-017~~ | ~~Rego: Backend-specific provider architecture~~ | ~~`resource_mapping.rego:72-77,108-115`~~ | Done - Added design notes explaining exact-match-first pattern. Backends can add explicit patterns to their data files |

## Pending Handoffs

| ID | Description | Handoff To | Document |
|----|-------------|------------|----------|
| ~~H-001~~ | ~~Extract portal-model module for shared entities~~ | ~~Team 2~~ | ~~Done 2026-02-02 - authz/repository now uses portal-model~~ |
| H-002 | APISIX Config Adapter: Set X-Authz-Backend header on routes | Team 1 | [TEAM1-APISIX-CONFIG-ADAPTER.md](../handoff/TEAM1-APISIX-CONFIG-ADAPTER.md) |
| H-003 | AuthZ system deployment (OPA, AuthZ Repository, APISIX plugin) | Team 3 | [TEAM3-AUTHZ-DEPLOYMENT.md](../handoff/TEAM3-AUTHZ-DEPLOYMENT.md) |

## To Be Clarified with Stakeholders

| ID | Question | Stakeholder | Status |
|----|----------|-------------|--------|
| Q-001 | TENANT scope: confirms portal-wide management but NOT data access in other backends (FROST, etc.)? | PO | **ANSWERED**: Scope inheritance hierarchy: TENANT → DATASPACE → DATASET. **UPDATE 2026-02-03**: Inheritance likely cut from v2 release. |
| Q-002 | Dataset→Dataspace mapping: is this info available in the DB? How to query it for scope enforcement? | Team 2 | **ANSWERED**: Yes. `dataset_dataspaces` M:N join table exists (V1 migration lines 32-37). Query: `SELECT dataspace_id FROM dataset_dataspaces WHERE dataset_id = ?` |
| Q-003 | Rego: RELEASE/USE operations - which endpoints use these? ADM spec includes them (e.g., release authorization for datasets). Expected pattern: `POST /datasets/{id}/release` | Team 2 | **ANSWERED**: Deferred to later implementation. See F-002. |
| Q-004 | Rego: DataSource, DataStructure, Tag resources - are these in portal-backend API? Not in current mappings. | Team 2 | **ANSWERED**: No. V1 schema has no tables for DataSource, DataStructure, or Tag. These may be FROST-server specific or deferred to later milestone. |

## Feature Ideas

| ID | Description | Notes |
|----|-------------|-------|
| ~~F-001~~ | ~~Use shared portal-model entities in authz-repository~~ | ~~Done 2026-02-02~~ |
| F-002 | Implement RELEASE/USE permission operations | ADM spec includes these ops (e.g., `POST /datasets/{id}/release`). Add to backend mappings when endpoints are implemented. |
| ~~F-003~~ | ~~Rego provider architecture with genericrestmapper~~ | ~~Done 2026-02-03 - M4.6 implemented. See `lib/genericrestmapper.rego`, `providers/portal_backend.rego`, `providers/frost_server.rego`~~ |
| F-004 | M4.5 scope enforcement (if inheritance returns post-v2) | Would need AuthZ Repository endpoint for "which dataspace does this dataset belong to?" inheritance lookup. Currently deferred - inheritance likely cut from v2. |
| F-005 | FROST Server OData URL parsing | Implement OData path parsing in `providers/frost_server.rego`. OData uses `Things(123)` syntax vs REST `/things/123`. Required for FROST Server integration. |
