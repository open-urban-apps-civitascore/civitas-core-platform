# CHANGELOG

Running log of changes made to the codebase by Claude Code.

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

