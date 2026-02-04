# Rego Policy Requirements Traceability

Requirements-to-code traceability for the CIVITAS CORE AuthZ Rego policies (M4).

**Source**: `docs/claude/additional-info/Authorization_Data_Model.md` (CIVITAS CORE official spec)

**Last reviewed**: 2026-02-04

---

## Summary

| Category | Implemented | Deferred | Gaps/Issues |
|----------|-------------|----------|-------------|
| Core Decision Flow | 5/5 | 0 | 0 |
| Resource Mapping | 6/6 | 0 | 1 (see R-MAP-7) |
| Permission Operations | 4/7 | 0 | 3 (see R-OP-*) |
| Permission Evaluation | 4/6 | 1 (scope) | 0 |
| Security Hardening | 4/4 | 0 | 0 |
| Data Model Alignment | 4/5 | 1 (scope) | 0 |
| User Context Fetcher | 5/5 | 0 | 0 |

**Overall**: Core M4 requirements are met. M5 integration complete. Scope enforcement (M4.5) is correctly deferred.

**Test Coverage**: 136 tests (was 66 after M4, added 70 for M5 user_context_fetcher + http.send mocking)

---

## Requirement Sources

| ID | Source | Description |
|----|--------|-------------|
| REQ | `docs/claude/task-info/REQUIREMENTS.md` | Local requirements doc from Q&A |
| ADM | CIVITAS Authorization_Data_Model.md | Official architecture doc (external) |
| MIL | `docs/claude/project-info/MILESTONES.md` | Milestone definitions and scope |
| MAP | `authz/rego/backends/portal_backend/data.json` | Team 2 permission mappings |

---

## 1. Core Decision Flow (main.rego)

### R-CORE-1: Default Deny (Fail Secure)

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "OPA is the sole PDP", Security best practice |
| **Code** | `main.rego:22-26` |
| **Implementation** | `default allow := false` and `default decision := {"allowed": false, "reason": "default_deny"}` |
| **Test** | `main_test.rego` - `test_default_deny` |
| **Status** | ✅ Implemented |

```rego
# main.rego:22-26
default allow := false
default decision := {"allowed": false, "reason": "default_deny"}
```

### R-CORE-2: Public Endpoint Bypass

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "Endpoints without AuthZ - Some endpoints skip AuthZ or both AuthN and AuthZ" |
| **Implementation** | **Handled at APISIX level, not OPA** |
| **Status** | ✅ N/A for OPA (APISIX routes without auth plugins) |

**Note**: Public endpoints are configured as separate APISIX routes without the `openid-connect` or `opa` plugins. OPA never sees these requests. The `public_endpoints.rego` file was removed as dead code (2026-02-02).

### R-CORE-3: Self-Access Endpoints (Null Permission)

| Attribute | Value |
|-----------|-------|
| **Source** | MAP: `/v2/users/me` has `GET: null` (no permission required) |
| **Code** | `main.rego:44-48`, `permission_eval.rego:42-47,71-77` |
| **Implementation** | Endpoints with `null` permission allow any authenticated user |
| **Test** | `main_test.rego` - `test_users_me_allowed_for_authenticated` |
| **Status** | ✅ Implemented |

```rego
# main.rego:44-48 - Decision for null-permission endpoints
evaluate_request := {"allowed": true, "reason": "authenticated_endpoint"} if {
    permission_eval.is_null_permission_endpoint
    permission_eval.is_authenticated
}
```

### R-CORE-4: Permission-Based Access

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "Evaluate: does the user have a permission matching the required operation + resource type?" |
| **Code** | `main.rego:34-36`, `main.rego:57-62` |
| **Implementation** | Permission check via `permission_eval.has_permission` |
| **Test** | `main_test.rego` - `test_permission_granted` |
| **Status** | ✅ Implemented |

### R-CORE-5: Decision with Reason (Debugging/Logging)

| Attribute | Value |
|-----------|-------|
| **Source** | MIL M5: "OPA decision logs show correct evaluation" |
| **Code** | `main.rego:26`, `main.rego:87-95` |
| **Implementation** | All decision paths return `{allowed, reason}` object; `request_info` exposed for debugging |
| **Test** | All `main_test.rego` tests check `result.reason` |
| **Status** | ✅ Implemented |

---

## 2. Resource Mapping (resource_mapping.rego)

### R-MAP-1: URL Path Parsing

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "Rego policies parse the URL path and HTTP verb from the request metadata" |
| **Code** | `resource_mapping.rego:69-85` |
| **Implementation** | `path_parts := split(...)` with security validation |
| **Test** | `resource_mapping_test.rego`, `edge_cases_test.rego` |
| **Status** | ✅ Implemented |

**Path Parsing Robustness** (tested in `edge_cases_test.rego`):

| Edge Case | Behavior | Security Impact |
|-----------|----------|-----------------|
| Double slash (`/v2//users`) | No match (empty segment) | ✅ Safe - denied |
| Trailing slash (`/v2/users/`) | No match (empty ID) | ✅ Safe - denied |
| Case sensitivity (`/V2/Users`) | No match (case-sensitive) | ✅ Safe - denied |
| Query string (`/v2/users?foo=bar`) | No match (query in path) | ✅ Safe - denied |
| URL encoding (`/v2/users/%7Bid%7D`) | No match (not decoded) | ✅ Safe - denied |
| Path traversal (`/v2/users/../admin`) | Rejected by `is_valid_path` | ✅ Safe - denied |
| Null bytes (`/v2/users\0admin`) | Rejected by `is_valid_path` | ✅ Safe - denied |

### R-MAP-2: Backend Detection (Multi-Backend Support)

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "APISIX routes will use path prefixes to distinguish backends" |
| **Code** | `resource_mapping.rego:26-42` |
| **Implementation** | Backend from `X-Authz-Backend` header (set by APISIX) |
| **Test** | `resource_mapping_test.rego:25-40` |
| **Status** | ✅ Implemented |

```rego
# resource_mapping.rego:26-42
default backend := "unknown"

backend := header_value if {
    header_value := input.request.headers["x-authz-backend"]
    header_value != ""
    is_valid_backend_id(header_value)
}

backend_data_key := replace(backend, "-", "_")  # portal-backend → portal_backend
```

**Multi-backend data structure**:
```
authz/rego/backends/
  portal_backend/
    data.json          → data.backends.portal_backend.endpoints
  frost_server/        (future)
    data.json          → data.backends.frost_server.endpoints
```

### R-MAP-3: Path Pattern Matching

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "Parse request path to extract: backend prefix, resource type, resource ID" |
| **Code** | `resource_mapping.rego:108-136` |
| **Implementation** | Exact match first, then `{id}` substitution |
| **Test** | `resource_mapping_test.rego:46-83` |
| **Status** | ✅ Implemented |

**Pattern Matching Priority**:
1. Exact match: `/v2/users/me` → `/v2/users/me`
2. ID substitution: `/v2/users/123` → `/v2/users/{id}`
3. No match → `path_pattern = ""` → denied

### R-MAP-4: HTTP Method Passthrough

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "GET → READ, POST → CREATE, etc." |
| **Code** | `resource_mapping.rego:88` |
| **Implementation** | Method passed directly; permission lookup in mappings file |
| **Test** | `resource_mapping_test.rego:94-97` |
| **Status** | ✅ Implemented |

**Note**: HTTP method → operation mapping is now in the backend data file, not hardcoded:
```json
"/v2/users": {
  "GET": "READ_USER",
  "POST": "CREATE_USER"
}
```

### R-MAP-5: Special Endpoint Handling (/users/me)

| Attribute | Value |
|-----------|-------|
| **Source** | MAP: `/v2/users/me` with `GET: null` |
| **Code** | `resource_mapping.rego:138-146` |
| **Implementation** | `is_special_segment("me")` prevents `{id}` matching |
| **Test** | `resource_mapping_test.rego:73-77` |
| **Status** | ✅ Implemented |

### R-MAP-6: Unknown Endpoint Denial

| Attribute | Value |
|-----------|-------|
| **Source** | Security best practice (fail-secure) |
| **Code** | `main.rego:77-81`, `permission_eval.rego:60-69` |
| **Implementation** | `is_known_endpoint = false` → denied with "unknown_endpoint" |
| **Test** | `main_test.rego` - `test_unknown_endpoint_denied` |
| **Status** | ✅ Implemented |

### R-MAP-7: HEAD → EXISTS Mapping

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "GET → READ (HEAD → EXISTS)" |
| **Code** | **Not implemented** |
| **Status** | ⚠️ **GAP** (TD-015) |

**Impact**: Low. HEAD requests fall through to unknown and are denied (fail-secure).

**Recommendation**: Add to backend mappings file if needed:
```json
"/v2/datasets/{id}": { "HEAD": "EXISTS_DATASET", ... }
```

---

## 3. Security Hardening (Added 2026-02-03)

### R-SEC-1: Backend ID Validation

| Attribute | Value |
|-----------|-------|
| **Source** | R-015 review comment |
| **Code** | `resource_mapping.rego:34-38` |
| **Implementation** | Regex validation: alphanumeric, dashes, underscores only |
| **Test** | `resource_mapping_test.rego` - `test_valid_backend_id`, `test_invalid_backend_id_*` |
| **Status** | ✅ Implemented |

```rego
is_valid_backend_id(id) if {
    regex.match(`^[a-zA-Z0-9_-]+$`, id)
}
```

### R-SEC-2: Path Traversal Prevention

| Attribute | Value |
|-----------|-------|
| **Source** | R-016 review comment |
| **Code** | `resource_mapping.rego:53-67` |
| **Implementation** | Reject paths containing `..` |
| **Test** | `resource_mapping_test.rego` - `test_path_traversal_rejected` |
| **Status** | ✅ Implemented |

### R-SEC-3: Null Byte Injection Prevention

| Attribute | Value |
|-----------|-------|
| **Source** | R-016 review comment |
| **Code** | `resource_mapping.rego:62` |
| **Implementation** | Reject paths containing `\u0000` |
| **Test** | `resource_mapping_test.rego` - `test_null_byte_rejected` |
| **Status** | ✅ Implemented |

### R-SEC-4: Path Length Limit

| Attribute | Value |
|-----------|-------|
| **Source** | R-016 review comment (DoS prevention) |
| **Code** | `resource_mapping.rego:66` |
| **Implementation** | Reject paths > 2048 characters |
| **Test** | Implicit (part of `is_valid_path`) |
| **Status** | ✅ Implemented |

---

## 4. Permission Evaluation (permission_eval.rego)

### R-PERM-1: Permission Lookup from Mappings

| Attribute | Value |
|-----------|-------|
| **Source** | MAP: Backend data files define permissions per endpoint/method |
| **Code** | `permission_eval.rego:30-54` |
| **Implementation** | Reads from `data.backends.{backend}.endpoints[path][method]` |
| **Test** | `permission_eval_test.rego:43-70` |
| **Status** | ✅ Implemented |

```rego
# permission_eval.rego:30-33
endpoint_config := resource_mapping.backend_endpoints[resource_mapping.path_pattern] if {
    resource_mapping.path_pattern != ""
}

# permission_eval.rego:37-40
method_permission := endpoint_config[resource_mapping.request_method] if {
    endpoint_config
    endpoint_config[resource_mapping.request_method] != null
}
```

### R-PERM-2: User Context Structure

| Attribute | Value |
|-----------|-------|
| **Source** | MIL M3: AuthZ Repository response format |
| **Code** | `permission_eval.rego:114-118` |
| **Implementation** | Expects `input.user_context.groups[].assignments[].permissions[]` |
| **Test** | All `permission_eval_test.rego` tests use this structure |
| **Status** | ✅ Implemented |

**Expected Structure**:
```json
{
  "user_context": {
    "userId": "uuid",
    "externalId": "keycloak-sub",
    "groups": [{
      "id": "uuid",
      "name": "string",
      "assignments": [{
        "roleId": "uuid",
        "roleName": "string",
        "roleType": "DATA|SYSTEM|GOVERNANCE",
        "scopeType": "TENANT|DATASPACE|DATASET",
        "scopeId": "uuid",
        "permissions": ["READ_USER", "CREATE_DATASET", ...]
      }]
    }]
  }
}
```

### R-PERM-3: Permission Lookup in Any Assignment

| Attribute | Value |
|-----------|-------|
| **Source** | ADM: "Check if the required permission exists for the target resource scope" (M4: any scope) |
| **Code** | `permission_eval.rego:113-118` |
| **Implementation** | Iterates all groups → assignments → permissions |
| **Test** | `permission_eval_test.rego` - `test_has_permission_*` |
| **Status** | ✅ Implemented |

```rego
# permission_eval.rego:114-118
user_has_permission(permission) if {
    some group in input.user_context.groups
    some assignment in group.assignments
    permission in assignment.permissions
}
```

### R-PERM-4: Authentication Check

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "AuthN is token validation only" - OPA should verify user identity exists |
| **Code** | `permission_eval.rego:83-92` |
| **Implementation** | Checks for `userId` or `externalId` in user context |
| **Test** | `permission_eval_test.rego` - `test_is_authenticated_*` |
| **Status** | ✅ Implemented |

### R-PERM-5: RELEASE and USE Operations

| Attribute | Value |
|-----------|-------|
| **Source** | ADM: Permission operations include RELEASE, USE |
| **Code** | **Not implemented** |
| **Status** | ⚠️ **GAP / Clarification Needed** (Q-003) |

**Recommendation**: Add to backend mappings file when endpoints are defined:
```json
"/v2/datasets/{id}/release": { "POST": "RELEASE_DATASET" }
```

### R-PERM-6: Scope-Based Permission Filtering

| Attribute | Value |
|-----------|-------|
| **Source** | ADM: "Scope Inheritance: TENANT → DATASPACE → DATASET" |
| **Code** | **Not implemented (by design)** |
| **Status** | ⏳ Deferred to M4.5 |

---

## 5. Data Model Alignment

### R-DATA-1: Users Never Directly Hold Roles

| Attribute | Value |
|-----------|-------|
| **Source** | ADM: "Users never directly hold roles; only groups do" |
| **Code** | `permission_eval.rego:114-118` |
| **Implementation** | Permission lookup goes through `groups[].assignments[]` |
| **Status** | ✅ Implemented |

### R-DATA-2: Ternary Model (Group × Role × Scope)

| Attribute | Value |
|-----------|-------|
| **Source** | ADM: "The authorization model resolves: Group x Role x Scope" |
| **Code** | User context structure + `permission_eval.rego` |
| **Implementation** | Assignments contain roleId + scopeType + scopeId |
| **Status** | ✅ Implemented (structure present, scope enforcement in M4.5) |

### R-DATA-3: Role Types (SYSTEM, DATA, GOVERNANCE)

| Attribute | Value |
|-----------|-------|
| **Source** | ADM: Enums - RoleType |
| **Code** | User context structure |
| **Implementation** | `roleType` field in assignments |
| **Status** | ✅ Implemented (field present, not used in M4 decisions) |

### R-DATA-4: Backend Mappings File Usage

| Attribute | Value |
|-----------|-------|
| **Source** | REQ: "Define the request→resource→permission mapping configuration" |
| **Code** | `authz/rego/backends/portal_backend/data.json` |
| **Status** | ✅ Implemented |

**Data file structure** (OPA loads as `data.backends.portal_backend`):
```json
{
  "_comment": "Portal Backend API permission mappings",
  "_version": "1.0.0",
  "_backend_id": "portal-backend",
  "endpoints": {
    "/v2/users/me": { "GET": null },
    "/v2/users": { "GET": "READ_USER", "POST": "CREATE_USER" },
    "/v2/users/{id}": { "GET": "READ_USER", "PUT": "UPDATE_USER", ... }
  }
}
```

**Null permission handling**: `"GET": null` means authenticated but no specific permission required.

### R-DATA-5: Hierarchical Groups

| Attribute | Value |
|-----------|-------|
| **Source** | ADM: Groups have `parent_group_id` for hierarchy |
| **Code** | **Not implemented (by design)** |
| **Status** | ⏳ Out of scope for v2.0 |

---

## 6. Test Coverage Summary

| Module | Tests | Coverage Notes |
|--------|-------|----------------|
| `main.rego` | 24 | All decision paths including http.send mocking |
| `resource_mapping.rego` | 21 | Path parsing, backend detection, security |
| `permission_eval.rego` | 34 | Permission lookup, null permissions, auth, http.send mocking |
| `user_context_fetcher.rego` | 40 | Base64 decoding, http.send, error handling |
| `edge_cases.rego` | 17 | URL edge cases (encoding, slashes, etc.) |
| **Total** | **136** | |

---

## 7. Identified Gaps Summary

| ID | Description | Severity | Status |
|----|-------------|----------|--------|
| R-MAP-7 / TD-015 | HEAD → EXISTS not mapped | Low | Add to mappings if needed |
| R-OP-1 / Q-003 | RELEASE operation not mapped | Medium | Awaiting Team 2 endpoint definition |
| R-OP-2 / Q-003 | USE operation not mapped | Medium | Awaiting Team 2 endpoint definition |
| R-RES-1 / Q-004 | DataSource, DataStructure, Tag not in mappings | Low | May not be in portal-backend API |

---

## 8. Code-to-Requirement Matrix

| File:Line | Requirement IDs |
|-----------|-----------------|
| `main.rego:22-23` | R-CORE-1 |
| `main.rego:44-48` | R-CORE-3 |
| `main.rego:57-62` | R-CORE-4 |
| `main.rego:87-95` | R-CORE-5 |
| `main.rego:77-81` | R-MAP-6 |
| `resource_mapping.rego:26-42` | R-MAP-2 |
| `resource_mapping.rego:34-38` | R-SEC-1 |
| `resource_mapping.rego:53-67` | R-SEC-2, R-SEC-3, R-SEC-4 |
| `resource_mapping.rego:69-85` | R-MAP-1 |
| `resource_mapping.rego:108-136` | R-MAP-3 |
| `resource_mapping.rego:138-146` | R-MAP-5 |
| `permission_eval.rego:30-54` | R-PERM-1 |
| `permission_eval.rego:83-92` | R-PERM-4 |
| `permission_eval.rego:114-118` | R-PERM-3, R-DATA-1 |
