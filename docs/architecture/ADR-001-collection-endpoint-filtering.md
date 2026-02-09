# ADR 001: Collection Endpoint Authorization Filtering

Date: 2026-02-04
Status: **Accepted** (Implemented in M5.5)

Decision Makers: Architecture Team

## Context

M5.1 implemented scope enforcement for **resource endpoints** (e.g., `GET /v2/datasets/{id}`), where OPA verifies the user's permission scope matches the specific resource ID. However, **collection endpoints** (e.g., `GET /v2/datasets`) still return all resources regardless of the user's scope.

The question: How should we filter collection results to only include resources the user is authorized to see?

Key constraints:
- OPA must remain the sole Policy Decision Point (PDP)
- The backend should not contain authorization logic
- Solution must work with existing APISIX + OPA + AuthZ Repository architecture
- Performance: avoid duplicate HTTP calls where possible

Example scenario:
- User has `READ_DATASET` permission scoped to `dataspace-A` and `dataspace-B`
- `GET /v2/datasets` should only return datasets in those two dataspaces
- User should NOT see datasets in `dataspace-C`

## Checked [Architecture Principles](https://docs.core.civitasconnect.digital/docs_v2/Architecture/Architecture_General/Architecture_Principles)

- [full] Model-centric data flow
- [full] Distributed architecture with unified user experience
- [full] Modular design
- [partial] Integration capability through defined interfaces - Requires new OPA endpoint and header contract
- [full] Open source as the default - Uses APISIX forward-auth plugin, OPA
- [full] Cloud-native architecture
- [full] Prefer standard solutions over custom development - Uses existing APISIX plugin
- [full] Self-contained deployment
- [full] Technological consistency to ensure maintainability - Extends existing OPA/Rego patterns
- [full] Multi-tenancy - Scope filtering is essential for multi-tenant isolation
- [full] Security by design - Centralized authorization in OPA, no authz logic in backend

## Decision

Use APISIX OPA plugin's `send_headers_upstream` feature to pass allowed scope IDs to the backend via HTTP headers. This approach leverages the existing OPA plugin configuration without requiring an additional forward-auth plugin.

**Key simplification**: APISIX OPA plugin supports `send_headers_upstream` ([PR #9710](https://github.com/apache/apisix/pull/9710), merged July 2023). This means a single OPA call handles both authorization AND scope header passing.

### Architecture

```
ANY /v2/* request
       │
       ▼
    APISIX (port 9080)
       │ 1. proxy-rewrite: X-Authz-Backend header
       │ 2. openid-connect: JWT validation, X-Userinfo header
       │ 3. opa: calls OPA, passes X-Allowed-Scope-Ids to backend
       ▼
      OPA (port 8181)
       │ GET /v1/data/civitas/authz/decision
       │ Returns: {"result": {"allow": true, "headers": {"X-Allowed-Scope-Ids": "ds-1,ds-2"}}}
       ▼
    APISIX
       │ allow=true → forward + pass header
       │ allow=false → 403
       ▼
   Portal Backend (port 8089)
       │ AllowedScopesFilter: parses header, stores in @RequestScope bean
       │ DataSetService.preProcessQuery(): applies JPA filter (collection only)
       ▼
   Filtered results (or single resource)
```

### Key Components

1. **Updated OPA main.rego** (scope header generation added to decision):
   ```rego
   # Check if user has TENANT-level access (wildcard)
   has_tenant_scope if {
       permission_eval.required_permission != ""
       some group in user_context_fetcher.user_context.groups
       some assignment in group.assignments
       permission_eval.required_permission in assignment.permissions
       assignment.scopeType == "TENANT"
   }

   # Collect specific scope IDs (non-TENANT)
   specific_scope_ids contains scope_id if {
       permission_eval.required_permission != ""
       resource_mapping.expected_scope_type != ""
       some group in user_context_fetcher.user_context.groups
       some assignment in group.assignments
       permission_eval.required_permission in assignment.permissions
       assignment.scopeType == resource_mapping.expected_scope_type
       scope_id := assignment.scopeId
       scope_id != null
   }

   # Header value: "*" for TENANT scope, comma-separated IDs otherwise
   default allowed_scope_ids_header := ""
   allowed_scope_ids_header := "*" if { has_tenant_scope }
   allowed_scope_ids_header := concat(",", sort(specific_scope_ids)) if {
       not has_tenant_scope
       count(specific_scope_ids) > 0
   }

   # Decision includes headers for APISIX to pass upstream
   evaluate_request := result if {
       permission_eval.is_known_endpoint
       not permission_eval.is_null_permission_endpoint
       has_user_context
       permission_eval.has_permission
       result := {
           "allow": true,
           "reason": "permission_granted",
           "permission": permission_eval.required_permission,
           "headers": { "X-Allowed-Scope-Ids": allowed_scope_ids_header }
       }
   }
   ```

2. **APISIX Route Configuration** (updated OPA plugin):
   ```yaml
   plugins:
     opa:
       host: "http://civitas-opa:8181"
       policy: "civitas/authz/decision"
       with_route: true
       send_headers_upstream:
         - "X-Allowed-Scope-Ids"  # Pass OPA headers to backend
   ```

3. **Backend Components**:
   - `AllowedScopes.java`: @RequestScope bean holding parsed scope IDs
   - `AllowedScopesFilter.java`: Servlet filter parsing X-Allowed-Scope-Ids header
   - `ScopeFilteringSpecification.java`: JPA specifications for scope filtering
   - `DataSetService`/`DataSpaceService`: Override `preProcessQuery()` to apply filters

---

## Java Implementation Details (for Team 2)

This section explains exactly how the backend handles scope filtering. **No authorization logic lives in the backend** — OPA makes all authz decisions. The backend simply applies the filter that OPA tells it to use.

### Request Flow in Backend

```
HTTP Request with X-Allowed-Scope-Ids header
       │
       ▼
AllowedScopesFilter (Servlet Filter)
       │ Parses header value
       │ Stores in AllowedScopes bean
       ▼
Controller (e.g., DataSetController)
       │ Calls service.findAll(spec, pageable)
       ▼
Service (e.g., DataSetService)
       │ preProcessQuery() adds scope filter to specification
       ▼
Repository
       │ Executes JPA query with combined specification
       ▼
Filtered results returned to client
```

### Component 1: AllowedScopes (Request-Scoped Bean)

**Location**: `portal-backend/src/main/java/de/civitascore/portal/security/AllowedScopes.java`

This bean stores the parsed scope information for the current request. Spring's `@RequestScope` ensures each HTTP request gets its own instance — no manual cleanup needed.

```java
@Component
@RequestScope
@Getter
public class AllowedScopes {
    public static final String WILDCARD = "*";

    private boolean active = false;      // Was header present?
    private boolean wildcard = false;    // Is this TENANT scope?
    private Set<UUID> scopeIds = Set.of(); // Specific scope IDs

    public void setWildcard() {
        this.active = true;
        this.wildcard = true;
    }

    public void setScopeIds(Set<UUID> ids) {
        this.active = true;
        this.wildcard = false;
        this.scopeIds = ids != null ? ids : Set.of();
    }
}
```

**Key states**:
| `active` | `wildcard` | `scopeIds` | Meaning |
|----------|------------|------------|---------|
| `false` | - | - | No header (direct backend access, no APISIX) |
| `true` | `true` | - | TENANT scope, skip filtering |
| `true` | `false` | `{uuid1, uuid2}` | Filter by these scope IDs |
| `true` | `false` | `{}` (empty) | No scopes, return empty results |

### Component 2: AllowedScopesFilter (Servlet Filter)

**Location**: `portal-backend/src/main/java/de/civitascore/portal/security/AllowedScopesFilter.java`

This filter runs early in the request chain and parses the `X-Allowed-Scope-Ids` header into the `AllowedScopes` bean.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)  // Run early
@RequiredArgsConstructor
public class AllowedScopesFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Allowed-Scope-Ids";

    private final ObjectProvider<AllowedScopes> allowedScopesProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER_NAME);

        if (header != null && !header.isBlank()) {
            AllowedScopes scopes = allowedScopesProvider.getObject();

            if (AllowedScopes.WILDCARD.equals(header)) {
                scopes.setWildcard();  // TENANT scope
            } else {
                // Parse comma-separated UUIDs
                Set<UUID> ids = Arrays.stream(header.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(this::parseUuidSafely)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
                scopes.setScopeIds(ids);
            }
        }
        // If header missing/blank, AllowedScopes stays inactive (active=false)

        filterChain.doFilter(request, response);
    }
}
```

**Header parsing rules**:
- `"*"` → wildcard (TENANT scope)
- `"uuid1,uuid2,uuid3"` → parsed into Set<UUID>
- Invalid UUIDs are logged and skipped (not a fatal error)
- Missing/blank header → `active=false`, no filtering applied

### Component 3: ScopeFilteringSpecification (JPA Specifications)

**Location**: `portal-backend/src/main/java/de/civitascore/portal/repository/specification/ScopeFilteringSpecification.java`

Static factory methods that create JPA Specifications for filtering entities by scope.

```java
public final class ScopeFilteringSpecification {

    private ScopeFilteringSpecification() {}

    /**
     * Filter DataSets to only those belonging to allowed dataspaces.
     * Uses JOIN on dataset_dataspaces table.
     */
    public static Specification<DataSet> dataSetInDataSpaces(Set<UUID> allowedDataSpaceIds) {
        return (root, query, cb) -> {
            if (allowedDataSpaceIds == null || allowedDataSpaceIds.isEmpty()) {
                return cb.disjunction();  // No scopes = no results
            }
            query.distinct(true);  // Avoid duplicates from join
            var join = root.join("dataSpaces", JoinType.INNER);
            return join.get("id").in(allowedDataSpaceIds);
        };
    }

    /**
     * Filter DataSpaces to only those with allowed IDs.
     */
    public static Specification<DataSpace> dataSpaceById(Set<UUID> allowedDataSpaceIds) {
        return (root, query, cb) -> {
            if (allowedDataSpaceIds == null || allowedDataSpaceIds.isEmpty()) {
                return cb.disjunction();  // No scopes = no results
            }
            return root.get("id").in(allowedDataSpaceIds);
        };
    }
}
```

**Generated SQL** (conceptual):
```sql
-- DataSet filtering (via dataSpaces relationship)
SELECT DISTINCT d.* FROM datasets d
INNER JOIN dataset_dataspaces dd ON d.id = dd.dataset_id
WHERE dd.dataspace_id IN ('uuid1', 'uuid2', 'uuid3')

-- DataSpace filtering (direct ID match)
SELECT * FROM data_spaces WHERE id IN ('uuid1', 'uuid2', 'uuid3')
```

### Component 4: Service Integration (preProcessQuery Override)

**Location**: Each service that needs filtering (e.g., `DataSetService.java`, `DataSpaceService.java`)

Services override `BaseService.preProcessQuery()` to apply the scope filter before executing queries.

```java
@Slf4j
@Service
public class DataSetService extends BaseService<DataSet, DataSetInputDTO> {

    private final ObjectProvider<AllowedScopes> allowedScopesProvider;

    // Constructor injection...

    @Override
    protected Specification<DataSet> preProcessQuery(
            Specification<DataSet> spec, Pageable pageable) {

        AllowedScopes scopes = allowedScopesProvider.getObject();

        // Case 1: No filtering needed
        if (!scopes.isActive() || scopes.isWildcard()) {
            return spec;  // Pass through unchanged
        }

        // Case 2: Apply scope filter
        log.debug("Filtering datasets by {} allowed dataspaces",
                  scopes.getScopeIds().size());
        Specification<DataSet> scopeFilter =
            ScopeFilteringSpecification.dataSetInDataSpaces(scopes.getScopeIds());

        // Combine with existing specification (e.g., search filters)
        return spec == null ? scopeFilter : spec.and(scopeFilter);
    }
}
```

**When filtering is applied**:
| Condition | Action |
|-----------|--------|
| `!scopes.isActive()` | No header present, skip filtering (direct backend access) |
| `scopes.isWildcard()` | TENANT scope, user sees all, skip filtering |
| Otherwise | Apply scope filter to query |

### Adding Filtering to a New Entity

To add scope filtering to a new entity type:

1. **Add a static method to `ScopeFilteringSpecification`**:
   ```java
   public static Specification<NewEntity> newEntityByScope(Set<UUID> allowedIds) {
       return (root, query, cb) -> {
           if (allowedIds == null || allowedIds.isEmpty()) {
               return cb.disjunction();
           }
           // Adapt the filter based on entity's relationship to scopes
           return root.get("scopeField").in(allowedIds);
       };
   }
   ```

2. **Override `preProcessQuery` in the service**:
   ```java
   @Override
   protected Specification<NewEntity> preProcessQuery(
           Specification<NewEntity> spec, Pageable pageable) {
       AllowedScopes scopes = allowedScopesProvider.getObject();
       if (!scopes.isActive() || scopes.isWildcard()) {
           return spec;
       }
       Specification<NewEntity> scopeFilter =
           ScopeFilteringSpecification.newEntityByScope(scopes.getScopeIds());
       return spec == null ? scopeFilter : spec.and(scopeFilter);
   }
   ```

3. **Add the `ObjectProvider<AllowedScopes>` to the service constructor**.

### Important: What NOT to Do

**DO NOT** add authorization logic to the backend. The following patterns are incorrect:

```java
// WRONG: Backend making authz decisions
if (currentUser.hasRole("ADMIN")) {
    return allResults;
} else {
    return filteredResults;
}

// WRONG: Backend calling AuthZ Repository
List<UUID> allowedScopes = authzClient.getScopesForUser(userId);

// WRONG: Backend interpreting JWT claims for authorization
String[] groups = jwt.getClaim("groups");
if (Arrays.contains(groups, "admin-group")) { ... }
```

**CORRECT**: Backend only reads `X-Allowed-Scope-Ids` header and applies the filter mechanically. OPA made the decision; backend just executes it.

### Testing

**Unit tests** verify the filter and specification logic:
- `AllowedScopesFilterTest`: Header parsing, wildcard detection, UUID validation
- `ScopeFilteringSpecificationTest`: JPA specification generation

**Integration testing** (manual or E2E):
```bash
# TENANT user sees all
curl -H "X-Allowed-Scope-Ids: *" http://localhost:8089/v2/datasets
# Returns: all datasets

# DATASPACE-scoped user sees filtered results
curl -H "X-Allowed-Scope-Ids: uuid1,uuid2" http://localhost:8089/v2/datasets
# Returns: only datasets in dataspace uuid1 or uuid2

# No scopes = empty results
curl -H "X-Allowed-Scope-Ids: " http://localhost:8089/v2/datasets
# Returns: empty list (or no X-Allowed-Scope-Ids header at all)
```

---

### Header Size Considerations

HTTP headers have size limits that vary by server implementation. Since scope IDs are UUIDs (36 chars each), the header size grows with the number of scopes.

**Size estimates:**
- 100 UUIDs ≈ 3.7KB
- 500 UUIDs ≈ 18.5KB
- 850 UUIDs ≈ 32KB

**Mitigations:**

1. **Wildcard for TENANT scope**: Users with TENANT-scoped permissions (system admins) receive `*` instead of enumerating all IDs:
   ```
   X-Allowed-Scope-Ids: *           # TENANT scope → no filtering
   X-Allowed-Scope-Ids: ds-1,ds-2   # specific scopes → filter applied
   ```

2. **Increase header size limits**: Configure both APISIX and Spring Boot to accept larger headers (32KB handles ~850 UUIDs, far exceeding realistic usage):

   **APISIX** (`config.yaml`):
   ```yaml
   nginx_config:
     http:
       proxy_buffer_size: 32k
   ```

   **Spring Boot** (`application.yaml`):
   ```yaml
   server:
     max-http-header-size: 32KB
   ```

**Why this is sufficient:**
- Users typically have 1-10 group memberships with 1-20 assignments each
- Maximum realistic scope count: ~200 (well under 32KB limit)
- Users with broad access have TENANT scope → wildcard
- No complex fallback mechanisms needed

### Why This Approach

1. **OPA remains the sole PDP**: The authorization decision (which scopes) is made by OPA, not the backend
2. **Clean separation**: OPA decides, backend filters
3. **Uses existing APISIX OPA plugin**: No additional plugins needed; `send_headers_upstream` is built-in
4. **Single OPA call**: One request handles both authorization AND scope extraction (no forward-auth overhead)
5. **Header-based contract**: Simple, debuggable, no complex data structures
6. **Extensible**: Same pattern works for dataspaces, users, groups, etc.

### Consequences

**Affected Components**:
- `authz/rego/policy/main.rego`: Added scope header generation rules
- `authz/rego/test/policy/main_test.rego`: Added ~10 header tests
- `dev-environment/authz/apisix-routes.yaml`: Added `send_headers_upstream` to OPA plugin
- `dev-environment/authz/apisix-config.yaml`: Added `proxy_buffer_size: 32k`
- `portal-backend/src/main/resources/application.yaml`: Added `server.max-http-request-header-size: 32KB`
- `portal-backend/.../security/AllowedScopes.java`: New @RequestScope bean
- `portal-backend/.../security/AllowedScopesFilter.java`: New servlet filter
- `portal-backend/.../specification/ScopeFilteringSpecification.java`: New JPA specifications
- `portal-backend/.../service/DataSetService.java`: Added `preProcessQuery()` override
- `portal-backend/.../service/DataSpaceService.java`: Added `preProcessQuery()` override
- Tests: New Rego tests (9 cases), backend unit tests (15 cases)

**Effects**:
- Collection endpoints will respect scope boundaries
- Users only see resources they're authorized to access
- No additional latency: scope header included in existing OPA authorization call
- Backend query complexity increases (dynamic WHERE clause via JPA Specification)

**Special Cases**:
- TENANT-scoped users (system admins): Return `X-Allowed-Scope-Ids: *` (wildcard), backend skips filtering
- No matching scopes: Return empty header or omit, backend returns empty results
- Resource endpoints: Continue using existing OPA plugin (M5.1), not forward-auth
- Mixed scopes: If user has both TENANT and specific scopes for a resource type, TENANT wins → wildcard

### Alternatives

- **Backend calls AuthZ Repository directly**: Rejected because it moves authorization logic into the backend, violating the "OPA as sole PDP" principle. The backend would be making authz decisions about which scopes apply.

- **OPA Partial Evaluation / Compile API**: Rejected as overly complex. Requires parsing OPA's AST response and converting to SQL. The "allowed scope IDs" pattern is simpler and sufficient for our scope model.

- **Backend calls OPA directly**: Rejected because it bypasses APISIX, complicating the request flow and losing gateway-level observability.

- **Shared cache (Redis)**: Rejected for v2 due to added infrastructure complexity. OPA already fetches user_context; caching can be added later (M7) if needed.

- **Return all data, filter in application layer**: Rejected for security (data leakage risk) and performance (fetching unnecessary data).

## Pattern Name

This approach is known as **"Authorization Scope Filtering"** or **"Policy-Enforced Query Filtering"**. It's a variant of Row-Level Security (RLS) where:

1. The **Policy Decision Point (OPA)** determines the allowed scope IDs
2. The **Policy Enforcement Point (APISIX)** passes this as a header
3. The **Backend** applies the filter mechanically without making authorization decisions

Related patterns:
- **Predicate Pushdown**: Database optimization term for pushing WHERE clauses down the query tree
- **Row-Level Security (RLS)**: Database-native filtering based on user context
- **Attribute-Based Access Control (ABAC)**: Policy decisions based on attributes (scopes are an attribute)

This is NOT "query plan pushing" — that term refers to distributed database optimizations. Our pattern is closer to **"Authorization Context Propagation"** where OPA's decision context (allowed scopes) flows through the request chain.

---

## Limitation: Requires Backend Control

**IMPORTANT**: This solution only works when we control the backend implementation.

### Why Backend Control is Required

The filtering mechanism requires the backend to:
1. Parse the `X-Allowed-Scope-Ids` header
2. Inject scope constraints into database queries
3. Ensure all collection endpoints respect the filter

This is only possible when we can modify the backend code.

### External Backends (FROST Server, Stellio, etc.)

For external/third-party backends like **FROST Server** or **Stellio** (NGSI-LD), we **cannot** apply this pattern because:

1. **No code modification**: We can't add `AllowedScopesFilter` or modify their query logic
2. **Different query languages**: FROST uses OData, Stellio uses NGSI-LD — neither understands our header
3. **No `preProcessQuery()` hook**: These backends don't have a mechanism to inject scope filters

### Implications for External Backends

For external backends, we have limited options:

| Approach | Viability | Notes |
|----------|-----------|-------|
| **Block collection endpoints** | ✅ Recommended | OPA denies all `GET /v2/things` requests; only resource endpoints (`GET /v2/things/{id}`) are allowed |
| **Proxy with query rewriting** | ⚠️ Complex | Build a proxy that intercepts requests, adds OData/NGSI-LD filters based on header |
| **Accept data leakage** | ❌ Not acceptable | Users see all data regardless of scope — violates security requirements |

**Current recommendation**: For FROST, Stellio, and other external backends, **do not expose collection endpoints**. Configure OPA to deny requests to collection patterns for these backends. Users must query by specific resource ID, where scope validation still works.

Example OPA rule for FROST:
```rego
# Deny collection endpoints for external backends
evaluate_request := {
    "allow": false,
    "reason": "collection_endpoints_not_supported"
} if {
    resource_mapping.backend == "frost-server"
    resource_mapping.is_collection_endpoint
}
```

### Future Considerations (F-006)

A future milestone could implement a **filtering proxy** that:
1. Receives the `X-Allowed-Scope-Ids` header from APISIX
2. Translates scope IDs to backend-native query syntax (OData $filter, NGSI-LD query)
3. Forwards the modified request to the external backend

This would require:
- Understanding each backend's query language
- Mapping scope IDs to the backend's entity model
- Handling pagination with filtered results

**Added to backlog**: F-006 - Collection endpoint filtering for external backends

---

## See also

- F-004 in BACKLOG.md: Scope enforcement for collection endpoints
- F-006 in BACKLOG.md: Collection filtering for external backends (future)
- Q-005 in BACKLOG.md: TENANT scope semantics (pending PO clarification)
- M5.1: Scope enforcement for resource endpoints (completed)
- [APISIX forward-auth plugin](https://apisix.apache.org/docs/apisix/plugins/forward-auth/)
- [OPA Data Filtering docs](https://www.openpolicyagent.org/docs/filtering)
