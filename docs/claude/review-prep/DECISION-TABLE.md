# Authorization Decision Table

Scenario matrix showing expected outcomes for all request/user/scope combinations.

## Legend

- **Allow**: Request reaches backend, response 200
- **Deny**: OPA blocks, APISIX returns 403
- **Filtered**: Request reaches backend, results filtered by scope
- **Unauth**: No valid JWT, APISIX returns 401
- **Empty**: Request reaches backend, returns empty result set (0 rows)

## OPA Gateway Decisions

| # | Endpoint | User | Permissions | Scope | OPA Decision | Reason |
|---|----------|------|-------------|-------|-------------|--------|
| 1 | `GET /v2/datasets` | admin | READ_DATASET | TENANT | **Allow** + header `*` | TENANT = wildcard |
| 2 | `GET /v2/datasets` | analyst | READ_DATASET | DATASPACE(ds-1) | **Allow** + header `ds-1` | Specific scope passed |
| 3 | `GET /v2/datasets` | analyst | READ_DATASET | DATASPACE(ds-1,ds-2) | **Allow** + header `ds-1,ds-2` | Multiple scopes |
| 4 | `GET /v2/datasets` | viewer | _(none)_ | _(none)_ | **Deny** | No READ_DATASET permission |
| 5 | `GET /v2/datasets/{id}` | admin | READ_DATASET | TENANT | **Allow** | TENANT covers all |
| 6 | `GET /v2/datasets/{id}` | analyst | READ_DATASET | DATASET(id) | **Allow** | Scope matches resource |
| 7 | `GET /v2/datasets/{id}` | analyst | READ_DATASET | DATASET(other-id) | **Deny** | Scope doesn't match resource |
| 8 | `POST /v2/datasets` | creator | CREATE_DATASET | DATASPACE(ds-1) | **Allow** | Has create permission |
| 9 | `POST /v2/datasets` | reader | READ_DATASET | DATASPACE(ds-1) | **Deny** | Wrong operation |
| 10 | `DELETE /v2/datasets/{id}` | admin | DELETE_DATASET | TENANT | **Allow** | TENANT + correct op |
| 11 | `DELETE /v2/datasets/{id}` | reader | READ_DATASET | DATASPACE(ds-1) | **Deny** | Wrong operation |
| 12 | `GET /v2/users/me` | any-auth | _(any)_ | _(any)_ | **Allow** (null-perm) | Null-permission endpoint |
| 13 | `GET /v2/users` | admin | READ_USER | TENANT | **Allow** + header `*` | TENANT-scoped resource |
| 14 | `GET /v2/users` | analyst | READ_USER | DATASPACE(ds-1) | **Deny** | Users require TENANT scope |
| 15 | Any endpoint | _(none)_ | _(n/a)_ | _(n/a)_ | **Unauth** (401) | No JWT |
| 16 | `GET /health` | _(none)_ | _(n/a)_ | _(n/a)_ | **Allow** (public) | Health check, no auth |

## Backend Collection Filtering

What happens AFTER OPA allows the request (header present):

| # | `X-Allowed-Scope-Ids` | Endpoint | Backend Behavior | Result |
|---|-----------------------|----------|-----------------|--------|
| F-1 | `*` | `GET /datasets` | Skip filtering (`isWildcard=true`) | All datasets returned |
| F-2 | `ds-1` | `GET /datasets` | JOIN dataset_dataspaces WHERE id IN (ds-1) | Only datasets in ds-1 |
| F-3 | `ds-1,ds-2` | `GET /datasets` | JOIN ... WHERE id IN (ds-1, ds-2) | Datasets in ds-1 or ds-2 |
| F-4 | _(empty string)_ | `GET /datasets` | `cb.disjunction()` (always-false) | **Empty** result set |
| F-5 | _(missing)_ | `GET /datasets` | Skip filtering (`isActive=false`) | All datasets (direct access) |
| F-6 | `*` | `GET /dataspaces` | Skip filtering | All dataspaces |
| F-7 | `ds-1,ds-2` | `GET /dataspaces` | WHERE id IN (ds-1, ds-2) | Only ds-1 and ds-2 |
| F-8 | `invalid-uuid,ds-1` | `GET /datasets` | Invalid UUID skipped; filter by ds-1 only | Datasets in ds-1 |
| F-9 | `invalid-uuid` | `GET /datasets` | All UUIDs invalid; empty scope set | **Empty** result set |

## Edge Cases & Failure Modes

| # | Scenario | Behavior | Fail Mode |
|---|---------|----------|-----------|
| X-1 | AuthZ Repository down | OPA denies (missing user context) | **Secure** (deny) |
| X-2 | OPA down | APISIX returns 503 | **Secure** (deny) |
| X-3 | Malformed X-Userinfo header | OPA can't extract `sub` claim | **Secure** (deny) |
| X-4 | User exists in Keycloak but not in AuthZ DB | AuthZ Repository returns 404 | **Secure** (deny) |
| X-5 | User has permission but in wrong scope type | Scope type mismatch in permission_eval | **Secure** (deny) |
| X-6 | Dataset in multiple dataspaces, user has 1 | JOIN returns dataset (DISTINCT prevents dupes) | **Correct** |
| X-7 | Header exceeds 32KB | APISIX/Tomcat reject request | Config limit; ~850 UUIDs max |
| X-8 | Same scope ID repeated in header | Parsed into Set<UUID>; deduped | **Correct** |

## Spec Assumption: Resource-to-Scope-Type Mapping

| Resource | Scope Type | Rationale |
|----------|-----------|-----------|
| users | TENANT | System-wide management |
| groups | TENANT | System-wide management |
| roles | TENANT | System-wide management |
| permissions | TENANT | System-wide management |
| assignments | TENANT | System-wide management |
| dataspaces | DATASPACE | Scoped by dataspace ID |
| datasets | DATASET (via dataspace) | Scoped by parent dataspace |
| catalogs | _(read-only, no scope check)_ | GET only, no write operations |

**Note**: This mapping is in `portal_backend.rego:resource_scope_type`. Changes here require Rego redeployment.
