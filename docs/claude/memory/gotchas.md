# Gotchas & Debugging Notes

## Backend

### mvn compile is required after changes
Spring Boot devtools does NOT always pick up changes, especially to new classes or annotation-driven beans. If log statements don't appear or behavior doesn't change, run `mvn compile` explicitly before re-testing.

### Spotless formatting
Always run `mvn spotless:apply` before committing Java code. Spotless failures in CI are hard failures. Google Java Format is enforced.

### LazyInitializationException
If an assembler accesses a lazy collection, the Hibernate session is already closed. Don't try `Hibernate.initialize()` in postProcessQueryResult — the session is gone. Use the two-phase fetch pattern (see patterns.md).

### JUnit 5 @Nested test count reporting
When using `@Nested` test classes (e.g., `ScopeFilteringSpecificationTest`), Maven reports `Tests run: 0` for the outer class. The actual tests run under the nested class names. This is normal JUnit 5 behavior — don't be alarmed when grepping `mvn test` output for counts. The summary line at the end has the correct total.

### Integration tests need Docker
Backend integration tests use Testcontainers. If Docker isn't running, they silently skip or fail with confusing errors.

## Frontend

### Playwright browser matrix
Default config has chromium/firefox/webkit. For local dev, chromium-only is sufficient. Three-browser testing is for CI only. Was changed to chromium-only in config.

### json-server db.json
This file is gitignored. E2E tests write to it and pollute it. Clean seed data is in `db.seed.json`. Reset with `pnpm run json-server:reset`. Auto-created on `pnpm install` if missing.

### E2E tests need full stack
Playwright E2E requires: PostgreSQL + Keycloak + portal-backend + portal-frontend all running. Missing any one gives confusing failures.

### Our E2E tests live in authz/e2e/, not portal-frontend/e2e/
Run: `cd authz/e2e && npx playwright test`. This is our standalone Playwright project with oauth-flow and authz-integration tests. Do NOT run `portal-frontend/e2e/` for our work — that's Team 2's test suite with broken dataset/user tests (TD-011/TD-012) that waste ~10min on 30s timeouts x 6 retries.

## Rego / OPA

### Test structure
Tests are in `authz/rego/test/` mirroring `authz/rego/policy/`, `lib/`, `providers/`. Run with `opa test . -v` from `authz/rego/`.

### OPA not installed locally
OPA binary is not on PATH. The OPA container (`civitas-opa`) uses a static image with no shell. To run tests locally, download the arm64 binary: `curl -sSL -o /tmp/opa https://openpolicyagent.org/downloads/v1.4.2/opa_linux_arm64_static && chmod +x /tmp/opa` then run `/tmp/opa test . -v` from `authz/rego/`. **Note:** the binary lives in /tmp and may be cleaned between sessions (e.g., scratchpad cleanup). Re-download if missing.

### Bundle structure matters
OPA loads from `authz/rego/` as a bundle. The `.manifest` file defines roots. If files are in the wrong directory, OPA silently ignores them.

### APISIX OPA plugin
Uses `ctx.var.uri` (normalized path). Rego path validation is defense-in-depth, not primary security. The plugin calls `/v1/data/{policy_path}` and expects `{"result": {"allow": bool, ...}}`.

## Code Ownership

### Only touch what we own proactively
We own `/authz` and parts of `/dev-environment`. Other components have different owners: `config-adapter` is Team 1, `portal-frontend` and `portal-backend` are Team 2. Only touch them surgically, with good reason, and with explicit user confirmation. Never run formatters, refactors, or proactive cleanup on code we don't own. This applies especially during `/idle` autonomous work.

## Infrastructure

### Keycloak in dev mode
Dev-mode Keycloak uses HTTP and ignores hostname config for security. APISIX can't use dynamic JWKS because Keycloak returns localhost URLs from inside Docker. Workaround: static public_key in dev, dynamic JWKS in production (TD-014).

### Port assignments
- Frontend: 3000
- Backend: 8089
- APISIX: 9080
- Keycloak: 8080
- OPA: 8181
- AuthZ Repository: 8091
- PostgreSQL: 5432
