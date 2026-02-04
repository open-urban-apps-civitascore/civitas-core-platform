Run a full preflight check. Do these in order, do not stop on first failure — collect all results and report at the end:

1. Check git status (clean working tree?)
2. Run formatters: `mvn spotless:apply` (portal-backend), `pnpm format` (portal-frontend)
3. Run all test suites in parallel where possible:
   - Frontend unit tests: `cd portal-frontend && pnpm test`
   - Backend unit tests: `cd portal-backend && mvn test`
   - Rego tests: `cd authz/rego && opa test . -v`
4. Run E2E tests (our authz tests only): `cd authz/e2e && npx playwright test`
   Note: Our E2E tests live in `authz/e2e/`, NOT in `portal-frontend/e2e/`. Team 2's dataset/user E2E tests in portal-frontend are broken (TD-011/TD-012) and waste ~10min on retries. Only run `portal-frontend/e2e/` when explicitly asked for Team 2's full suite.
5. Doc staleness check: verify test counts in TESTING.md match actual counts.
   Automated verification — run each suite and compare count to TESTING.md:
   - Frontend unit: parse "Tests" line from `pnpm test --run` output
   - Backend unit: parse "Tests run:" from `mvn test` output
   - Rego: parse PASS count from `opa test . -v` output
   - E2E: parse "Total:" from `npx playwright test --list` output
   If any count diverges, update TESTING.md and MEMORY.md before reporting.
6. Check backlog for any items that should be updated based on recent work
7. Run `pnpm audit` (portal-frontend) and flag any new HIGH/CRITICAL vulnerabilities not already in backlog

Report a summary table at the end: component | status | details
