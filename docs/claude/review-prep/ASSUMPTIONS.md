# AuthZ Business Logic Assumptions

What we assumed during implementation. Items marked **[CONFIRM]** need explicit PO sign-off.

## Scope Model

| # | Assumption | Current Behavior | If Wrong... |
|---|-----------|-----------------|-------------|
| **S-1** | **TENANT scope = wildcard** [CONFIRM] | User with TENANT-scoped permission sees ALL resources of that type (datasets, dataspaces, users, etc.) | If TENANT only covers system resources (users/groups/roles), collection filtering must distinguish by resource type |
| S-2 | No scope inheritance | TENANT does NOT auto-grant DATASPACE access; DATASPACE does NOT auto-grant DATASET access | If inheritance is added: ~30 Rego tests + backend filtering changes |
| S-3 | Scope types are exactly three: TENANT, DATASPACE, DATASET | Permission eval has rules for each type | New types (e.g., PROJECT) require new Rego rules + backend spec methods |
| S-4 | Scopes are union semantics | If user has permission via ANY group assignment, access is granted | If intersection semantics are needed (ALL groups must agree), permission eval breaks |

## Permission Model

| # | Assumption | Current Behavior | If Wrong... |
|---|-----------|-----------------|-------------|
| P-1 | Roles pre-flattened by AuthZ Repository | Rego receives `permissions[]`, never sees role names | If roles stay in response, Rego needs role-to-permission iteration |
| **P-2** | **Null-permission = any authenticated user** [CONFIRM] | `/users/me` requires auth but no specific permission | If this should return scope-filtered data, null-permission semantics change |
| P-3 | HTTP method maps 1:1 to permission operation | GET=READ, POST=CREATE, PUT/PATCH=UPDATE, DELETE=DELETE | Custom operations (RELEASE, USE) need explicit endpoint mappings |
| **P-4** | **RELEASE/USE operations deferred** [CONFIRM] | Not in current data.json; endpoints like `POST /datasets/{id}/release` would be denied | When added: new permissions needed + Rego data.json update |
| P-5 | HEAD method not mapped | HEAD requests denied (falls through to UNKNOWN) | Add HEAD mapping if backend uses conditional requests |

## Endpoint & Resource Model

| # | Assumption | Current Behavior | If Wrong... |
|---|-----------|-----------------|-------------|
| E-1 | All REST paths follow `/v2/{resource}/{id}` | Path parser extracts resource from segment[1], ID from segment[2] | Nested resources (`/dataspaces/{id}/datasets`) not supported |
| E-2 | Collection = 2 segments, Resource = 3 segments | `/v2/datasets` = collection; `/v2/datasets/{id}` = resource | Mixed patterns need different classification |
| E-3 | "me" is only reserved segment | `/users/me` treated as null-permission endpoint, not a resource ID | Other reserved names (self, profile) need explicit handling |
| E-4 | Backend ID must be alphanumeric | `X-Authz-Backend` header validated against `^[a-zA-Z0-9_-]+$` | Security: prevents data lookup injection |

## Backend Filtering (portal-backend)

| # | Assumption | Current Behavior | If Wrong... |
|---|-----------|-----------------|-------------|
| **B-1** | **Collection filtering only; single-resource access is OPA's job** [CONFIRM] | `preProcessQuery()` filters `GET /datasets`, but `GET /datasets/{id}` trusts OPA | If OPA bypassed: backend serves unfiltered single resources. Defense-in-depth argument applies. |
| B-2 | Dataset scope = dataspace membership | Filtering joins `dataset_dataspaces` table | If datasets can be scoped independently of dataspaces, join logic changes |
| B-3 | No header = no filtering (direct backend access) | `AllowedScopes.isActive()=false` when header missing | Correct for dev/testing; in production, all requests go through APISIX/OPA |
| B-4 | Empty scopes = empty results | `cb.disjunction()` returns zero rows | Fail-secure: user with no matching scopes sees nothing |

## Architecture

| # | Assumption | Current Behavior | If Wrong... |
|---|-----------|-----------------|-------------|
| A-1 | OPA is sole PDP | Backend never makes authz decisions; only filters by scope | Adding backend-side authz checks creates dual-PDP risk |
| A-2 | APISIX sets X-Authz-Backend header | Required for OPA to dispatch to correct provider | H-002 handoff to Team 1 pending |
| A-3 | AuthZ Repository is single source for user context | OPA fetches via http.send(); no input fallback | If repo is down: fail-secure deny (not fail-open) |

## Open Questions (for PO)

1. **Q-005 (CRITICAL)**: Does TENANT scope grant access to data resources (datasets, dataspaces), or only system resources (users, groups, roles)? Current implementation: wildcard for everything.
2. **Q-003**: When are RELEASE/USE operations expected? Do they need new permissions or reuse CREATE/UPDATE?
3. **Q-004**: Are DataSource, DataStructure, Tag resources planned for portal-backend API?
