# BACKLOG

Technical debt, feature ideas, known bugs, and process friction not currently implemented.

## Technical Debt

| ID | Description | Location | Priority |
|----|-------------|----------|----------|
| TD-001 | Skipped unit test: `should reset pageIndex if it exceeds totalPages` | `portal-frontend/src/hooks/use-query-params.test.ts:125` | Medium |
| TD-002 | Skipped unit test: `should map users correctly with matching authority and department` | `portal-frontend/src/utils/users.test.ts:60` | Medium |
| TD-003 | Skipped E2E tests: EditUser, UserList, CreateUser (entire describe blocks) | `portal-frontend/e2e/users/` | High |
| TD-004 | CSP `connect-src` production API domains not configured | `portal-frontend/src/middleware.ts:100` | Medium |
| TD-005 | `useGetAuthorities` hook marked for removal (not part of v2) | `portal-frontend/src/app/services/api/users/clientRequests.ts:12` | Low |
| TD-006 | UML modeler: relationship validation incomplete | `portal-frontend/src/app/(main)/uml-modeler/services/diagramService.ts:226` | Low |
| TD-007 | UML modeler: save/export buttons are no-ops (console.log only) | `portal-frontend/src/app/(main)/uml-modeler/components/layout/MultiSessionLayout.tsx:59,67` | Medium |
| TD-008 | UML modeler: unsaved changes uses `window.confirm()` instead of proper dialog | `portal-frontend/src/app/(main)/uml-modeler/components/layout/MultiSessionLayout.tsx:30` | Low |
| TD-009 | BaseAssembler missing generic type enforcement for PATCH support | `portal-backend/src/main/java/.../BaseAssembler.java:17` | Low |
| TD-010 | SBOM license name resolution broken, scan reports disabled in CI | `.gitlab/ci/{backend,frontend,config-adapter}.yml` (issue #728) | Medium |
| TD-011 | Dataset E2E tests failing: form elements not rendering, 30s timeouts | `portal-frontend/e2e/datasets/CreateDataset.spec.ts` — `.skip` removed but still fails (verified 2026-02-07). All tests timeout waiting for form. Team 2's code. | High |
| TD-012 | DatasetList E2E tests failing | `portal-frontend/e2e/datasets/DatasetList.spec.ts` — `.skip` removed but likely still fails (same root cause as TD-011). Team 2's code. | High |
| ~~TD-013~~ | ~~E2E tests: auth token not passed to backend, causing "Error while loading data"~~ | ~~Fixed: Keycloak KC_HOSTNAME config~~ | ~~Resolved~~ |
| TD-014 | APISIX uses static public_key instead of dynamic JWKS (dev-only) | Keycloak (dev mode + HTTP) ignores hostname config for security reasons, returns localhost URLs that APISIX can't reach. Production with HTTPS uses dynamic JWKS. | Low |
| TD-019 | AuthZ E2E tests have UI login timeouts | Playwright tests can't find "Sign in with Keycloak" button reliably. Curl-based integration tests pass. Investigate session state/timing. | Medium |
| TD-015 | Rego: HEAD→EXISTS operation not mapped | `authz/rego/policy/resource_mapping.rego` - HEAD method falls through to UNKNOWN. Add if backend uses HEAD requests. | Low |
| TD-017 | Rego: No schema validation for backend data files | `authz/rego/backends/*/data.json` - Malformed JSON causes runtime errors, not startup failures. Consider OPA bundle with schema. | Low |
| ~~TD-018~~ | ~~Rego: URL normalization trust assumption~~ | ~~Resolved: APISIX OPA plugin uses `ctx.var.uri` (normalized). See helper.lua#L41. Rego validation is defense-in-depth.~~ | ~~Resolved~~ |
| ~~TD-016~~ | ~~Rego: permissions computed dynamically instead of using mappings file~~ | ~~Fixed 2026-02-02: Rego now reads from `data.backends.{backend}.endpoints`~~ | ~~Resolved~~ |
| TD-020 | Next.js 15.5.0 has 2 known DoS vulnerabilities (Image Optimizer + RSC deserialization) | `portal-frontend/package.json` — Patch available: 15.5.10. `pnpm audit` shows 2 moderate + 2 high for `next`. | High |
| TD-021 | Storybook 10.x env var exposure during build | `portal-frontend/package.json` — Dev dependency. Patch: >=10.1.10. Low production risk. | Low |
| TD-022 | json-server transitive deps (lodash, qs) have known vulns | Dev-only. `lodash` prototype pollution (>=4.17.23 patched), `qs` DoS (>=6.14.1 patched). No production impact. | Low |

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
| ~~R-018~~ | ~~Rego: Are we fail-secure for fishy user_context?~~ | ~~`user_context_fetcher.rego:17`~~ | Done - YES, fail-secure. Rego's undefined semantics cause malformed data → no match → deny. Added explanatory comment. |
| ~~R-019~~ | ~~Rego: input.user_context fallback - security risk?~~ | ~~`user_context_fetcher.rego:121,128`, `main.rego:110`~~ | Done - REMOVED fallback per TCB minimization. Tests now mock http.send() using OPA's `with http.send as mock_fn`. |
| ~~R-020~~ | ~~Rego: Why two sources (fetched vs input) for user_context?~~ | ~~`user_context_fetcher.rego:173`~~ | Done - REMOVED "input" source per TCB minimization. Now single source: AuthZ Repository via http.send(). Tests mock http.send(). |
| ~~R-021~~ | ~~Rego: Why no roles in permission_eval?~~ | ~~`permission_eval.rego:18`~~ | Done - By design. Role→permission resolution happens in AuthZ Repository. Rego receives pre-flattened permissions[]. Simpler, efficient, immediate updates. |
| ~~R-022~~ | ~~Rego: is_authenticated redundant with main.rego?~~ | ~~`permission_eval.rego:90`~~ | Done - Not redundant: is_authenticated serves null-permission endpoints specifically. has_user_context is for fail-secure on AuthZ Repository down. |

## Pending Handoffs

| ID | Description | Handoff To | Document |
|----|-------------|------------|----------|
| ~~H-001~~ | ~~Extract portal-model module for shared entities~~ | ~~Team 2~~ | ~~Done 2026-02-02 - authz/repository now uses portal-model~~ |
| H-002 | APISIX Config Adapter: Set X-Authz-Backend header on routes | Team 1 | [TEAM1-APISIX-CONFIG-ADAPTER.md](../handoff/TEAM1-APISIX-CONFIG-ADAPTER.md) |
| H-003 | AuthZ system deployment (OPA, AuthZ Repository, APISIX plugin) | Team 3 | [TEAM3-AUTHZ-DEPLOYMENT.md](../handoff/TEAM3-AUTHZ-DEPLOYMENT.md) |
| H-004 | Next.js 15.5.0 → 15.5.10 security update (2 DoS vulnerabilities) | Team 2 | [TEAM2-NEXTJS-VULNERABILITY.md](../handoff/TEAM2-NEXTJS-VULNERABILITY.md) |

## To Be Clarified with Stakeholders

| ID | Question | Stakeholder | Status |
|----|----------|-------------|--------|
| Q-001 | TENANT scope: confirms portal-wide management but NOT data access in other backends (FROST, etc.)? | PO | **ANSWERED**: Scope inheritance hierarchy: TENANT → DATASPACE → DATASET. **UPDATE 2026-02-03**: Inheritance likely cut from v2 release. |
| Q-002 | Dataset→Dataspace mapping: is this info available in the DB? How to query it for scope enforcement? | Team 2 | **ANSWERED**: Yes. `dataset_dataspaces` M:N join table exists (V1 migration lines 32-37). Query: `SELECT dataspace_id FROM dataset_dataspaces WHERE dataset_id = ?` |
| Q-003 | Rego: RELEASE/USE operations - which endpoints use these? ADM spec includes them (e.g., release authorization for datasets). Expected pattern: `POST /datasets/{id}/release` | Team 2 | **ANSWERED**: Deferred to later implementation. See F-002. |
| Q-004 | Rego: DataSource, DataStructure, Tag resources - are these in portal-backend API? Not in current mappings. | Team 2 | **ANSWERED**: No. V1 schema has no tables for DataSource, DataStructure, or Tag. These may be FROST-server specific or deferred to later milestone. |
| Q-005 | TENANT scope semantics: Does TENANT-scoped permission act as wildcard (access ALL resources of that type in tenant) or only tenant-level resources (users, groups, roles)? Currently assuming latter. | PO | Pending |

## Feature Ideas

| ID | Description | Notes |
|----|-------------|-------|
| ~~F-001~~ | ~~Use shared portal-model entities in authz-repository~~ | ~~Done 2026-02-02~~ |
| F-002 | Implement RELEASE/USE permission operations | ADM spec includes these ops (e.g., `POST /datasets/{id}/release`). Add to backend mappings when endpoints are implemented. |
| ~~F-003~~ | ~~Rego provider architecture with genericrestmapper~~ | ~~Done 2026-02-03 - M4.6 implemented. See `lib/genericrestmapper.rego`, `providers/portal_backend.rego`, `providers/frost_server.rego`~~ |
| ~~F-004~~ | ~~Scope enforcement for collection endpoints~~ | ~~Done 2026-02-05 - M5.5 implemented. See [ADR-001](../adrs/ADR-001-collection-endpoint-filtering.md). Backend uses `X-Allowed-Scope-Ids` header.~~ |
| F-005 | FROST Server OData URL parsing | Implement OData path parsing in `providers/frost_server.rego`. OData uses `Things(123)` syntax vs REST `/things/123`. Required for FROST Server integration. |
| F-006 | Collection filtering for external backends | Current M5.5 solution requires backend code control. For FROST, Stellio, etc., options: (1) block collection endpoints, (2) build filtering proxy, (3) modify backend code. See ADR-001 "Limitation: Requires Backend Control" section. |
| F-007 | ADR tracker: auto-sync open ADRs from GitLab issues | Pull issues with `type::adr` label from GitLab API, cross-ref with our local ADR docs. Flag new/updated ADRs. Integrate into watchtower `/upstream` skill. |
| F-008 | MR review awareness: flag MRs touching our code | Extend watchtower `/upstream` to highlight MRs where reviewers/authors overlap with our areas. Could auto-generate "you should look at MR !NNN" alerts. |
| F-009 | Pipeline health monitor in /idle | Add develop pipeline status check to `/idle` Tier 1. Quick green/red check before rebasing. Uses `pipelines?ref=develop` API endpoint. |
| F-010 | Team activity map for M6 handoff | Generate contributor-to-area mapping from GitLab commit data. Helps write targeted handoff docs (who to contact for what). One-time for M6, then archive. |

## Process Friction

Observations about workflow inefficiencies. Prefix: `FR-`. Logged continuously, reviewed at milestone retros.

| ID | Date | What happened | Category | Resolution |
|----|------|---------------|----------|------------|
| ~~FR-001~~ | 02-05 | `mvn compile` needed after Java changes, wasted 3 retries on LazyInitializationException fix | gotcha | Added to `docs/claude/memory/gotchas.md` |
| ~~FR-002~~ | 02-05 | Playwright ran 3 browsers locally, 2x wasted time | waste | Config changed to chromium-only locally |
| ~~FR-003~~ | 02-06 | Memory files in machine-local dir, can't sync between machines | tooling | Moved to `docs/claude/memory/` in git repo |
| ~~FR-004~~ | 02-06 | json-server db.json polluted by E2E tests, showed in git status | waste | Gitignored, seed file pattern |
| ~~FR-005~~ | 02-06 | No automated pre/post-conditions — user had to remember to ask for formatting, tests | process | Task lifecycle protocol + /preflight + /wrapup skills |
| ~~FR-006~~ | 02-06 | Empty MEMORY.md — re-learning project patterns every session | knowledge | Populated memory files, added to CLAUDE.md |
| FR-007 | 02-07 | Context window ran out mid-wrapup — lost state and had to resume from summary | context | Wrapup skill should be more concise; consider splitting into smaller steps that commit incrementally |
