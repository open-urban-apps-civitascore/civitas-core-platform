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

## Pending Handoffs

| ID | Description | Handoff To | Document |
|----|-------------|------------|----------|
| H-001 | Extract portal-model module for shared entities | Team 2 | [TEAM2-PORTAL-MODEL-EXTRAKTION.md](../handoff/TEAM2-PORTAL-MODEL-EXTRAKTION.md) |

## Feature Ideas

| ID | Description | Notes |
|----|-------------|-------|
| F-001 | Use shared portal-model entities in authz-repository | Blocked on H-001 |
