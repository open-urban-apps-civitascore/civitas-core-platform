# Patterns & Solutions

## Two-Phase Fetch (JPA Lazy Loading Fix)

**Problem**: `LazyInitializationException` when assemblers access lazy collections outside transaction.

**Solution**: Override `findAll()` in the service:
1. Execute paginated query to get IDs
2. Fetch entities with JOIN FETCH by those IDs
3. Re-order to match original pagination
4. Return new `PageImpl<>`

**Where used**: `RoleService.findAll()` — fetches permissions eagerly.

**Key**: Must add `@Transactional(readOnly = true)` to the override.

## Authorization Scope Filtering (M5.5 Pattern)

**Flow**: OPA → X-Allowed-Scope-Ids header → AllowedScopesFilter → AllowedScopes bean → preProcessQuery()

**Adding to new entity**:
1. Add static method to `ScopeFilteringSpecification`
2. Override `preProcessQuery()` in service
3. Inject `ObjectProvider<AllowedScopes>`

**Header values**: `*` = wildcard (TENANT), `uuid,uuid` = specific scopes, empty = no access

## Rego Testing with http.send Mocking

**Pattern**: Mock external calls in tests using OPA's `with` keyword:
```
test_something if {
    result := data.civitas.authz.evaluate_request with http.send as mock_send
}
mock_send(_) := {"status_code": 200, "body": {...}}
```

Never use input fallbacks for user_context — violates TCB minimization.

## BaseService Extension Points

- `preProcessQuery(spec, pageable)` — add filters before query execution
- `postProcessQueryResult(entity)` — transform after query (but session may be closed!)
- Always prefer preProcessQuery for filtering; postProcess is unreliable for lazy loading.

## Provider Architecture (Rego)

- `resource_mapping.rego` dispatches to providers based on `X-Authz-Backend` header
- Each provider wraps `genericrestmapper` with backend-specific config
- Data files live in `backends/{name}/data.json`, referenced as `data.backends.{name}.endpoints`

## Watchtower Architecture (Upstream Monitoring)

- **Control plane**: `cc-watchtower` is a standalone Claude Code project at `/mnt/shared/claude-tests/watchtowers/cc-watchtower`
- **Reads from** feature repos (git diff, branch info) — never writes to them
- **Config**: `config/projects.yaml` defines monitored projects, workstream filter criteria
- **Three workstreams**: authz (our code), security (TR-03187/product risk), dx (process/build/velocity)
- **Output**: Full brief in `briefs/YYYY-MM-DD.md`, per-workstream digests in `feed/{workstream}/`
- **Integration**: Symlink from feature repo → watchtower digest, gitignored in feature repo
- **Run**: `cd /mnt/shared/claude-tests/watchtowers/cc-watchtower && /upstream`
