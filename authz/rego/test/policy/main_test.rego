# Tests for main.rego - Entry point authorization decisions
#
# Tests mock http.send() using OPA's `with http.send as mock_fn` syntax.

package civitas.authz_test

import rego.v1

import data.civitas.authz
import data.test.helpers.mock_http

# =============================================================================
# TEST HELPERS
# =============================================================================

# Standard request with service metadata and userinfo
portal_request(method, path) := {
    "request": {
        "method": method,
        "path": path,
        "headers": {
            "x-userinfo": mock_http.encode_userinfo("test-user")
        }
    },
    "service": {"name": "portal-backend"}
}

# Request without userinfo header (no auth)
portal_request_no_auth(method, path) := {
    "request": {
        "method": method,
        "path": path,
        "headers": {}
    },
    "service": {"name": "portal-backend"}
}

# User context with TENANT-scoped permissions (for tenant-level resources)
user_context_tenant_scoped(perms) := {
    "userId": "user-1",
    "externalId": "keycloak-sub-1",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "Test Role",
            "roleType": "SYSTEM",
            "scopeType": "TENANT",
            "scopeId": "tenant-1",
            "permissions": perms
        }]
    }]
}

# User context with permissions scoped to a specific resource (M5.1)
user_context_with_scope(perms, scope_type, scope_id) := {
    "userId": "user-1",
    "externalId": "keycloak-sub-1",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "Test Role",
            "roleType": "DATA",
            "scopeType": scope_type,
            "scopeId": scope_id,
            "permissions": perms
        }]
    }]
}

# Mock http.send functions for TENANT-scoped resources (users, groups, roles, etc.)
mock_send_admin(_) := {"status_code": 200, "body": user_context_tenant_scoped(["READ_USER", "CREATE_USER", "DELETE_USER"])}
mock_send_reader(_) := {"status_code": 200, "body": user_context_tenant_scoped(["READ_USER"])}

# Mock for collection endpoints (any scope works for collections)
mock_send_dataset_creator(_) := {"status_code": 200, "body": user_context_tenant_scoped(["CREATE_DATASET"])}

# Mock for DATASOURCE resource endpoint - must be scoped to the specific datasource ID
mock_send_datasource_updater(_) := {"status_code": 200, "body": user_context_with_scope(["UPDATE_DATASOURCE"], "DATASOURCE", "abc-123")}

# Mock for collection GET (any scope works)
mock_send_all_read(_) := {"status_code": 200, "body": user_context_tenant_scoped(["READ_USER", "READ_DATASET"])}

mock_send_authenticated_no_groups(_) := {"status_code": 200, "body": {"userId": "123", "externalId": "keycloak-sub-123", "groups": []}}
mock_send_authenticated_no_groups_field(_) := {"status_code": 200, "body": {"userId": "123", "externalId": "ext-123"}}
mock_send_empty(_) := {"status_code": 200, "body": {}}
mock_send_null_ids(_) := {"status_code": 200, "body": {"userId": null, "externalId": null, "groups": []}}
mock_send_error(_) := {"status_code": 500, "body": {"error": "Internal server error"}}

# =============================================================================
# PERMISSION-BASED ACCESS TESTS
# =============================================================================

# Test: Authenticated user with correct permission is allowed
test_permission_granted if {
    result := authz.decision
        with http.send as mock_send_admin
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/users")
    result.allow == true
    result.reason == "permission_granted"
    result.permission == "READ_USER"
}

# Test: Authenticated user without required permission is denied
test_permission_denied if {
    result := authz.decision
        with http.send as mock_send_reader
        with data.config as mock_http.mock_config
        with input as portal_request("DELETE", "/v2/users/123")
    result.allow == false
    result.reason == "permission_denied"
    result.required == "DELETE_USER"
}

# Test: POST to collection requires CREATE permission
test_create_permission if {
    result := authz.decision
        with http.send as mock_send_dataset_creator
        with data.config as mock_http.mock_config
        with input as portal_request("POST", "/v2/datasets")
    result.allow == true
    result.reason == "permission_granted"
    result.permission == "CREATE_DATASET"
}

# Test: PUT to resource requires UPDATE permission
test_update_permission if {
    result := authz.decision
        with http.send as mock_send_datasource_updater
        with data.config as mock_http.mock_config
        with input as portal_request("PUT", "/v2/datasources/abc-123")
    result.allow == true
    result.reason == "permission_granted"
}

# =============================================================================
# NULL-PERMISSION (SELF-ACCESS) TESTS
# =============================================================================

# Test: /users/me allowed for any authenticated user
test_users_me_allowed_for_authenticated if {
    result := authz.decision
        with http.send as mock_send_authenticated_no_groups
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/users/me")
    result.allow == true
    result.reason == "authenticated_endpoint"
}

# Test: /users/me denied for unauthenticated request (no X-Userinfo header)
test_users_me_denied_without_auth if {
    result := authz.decision with input as portal_request_no_auth("GET", "/v2/users/me")
    result.allow == false
    result.reason == "authentication_required"
}

# =============================================================================
# ERROR CASE TESTS
# =============================================================================

# Test: Empty user context from AuthZ Repository results in denial (fail-secure)
test_empty_user_context_denied if {
    result := authz.decision
        with http.send as mock_send_empty
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasets")
    result.allow == false
    result.reason == "missing_user_context"
}

# Test: Missing X-Userinfo header results in denial (AuthZ Repository not called)
test_missing_userinfo_header_denied if {
    result := authz.decision with input as portal_request_no_auth("GET", "/v2/users")
    result.allow == false
    result.reason == "missing_user_context"
}

# Test: User context with null userId/externalId results in denial
test_null_user_ids_denied if {
    result := authz.decision
        with http.send as mock_send_null_ids
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasets")
    result.allow == false
    result.reason == "missing_user_context"
}

# Test: Malformed user context (missing groups) still allows null-permission endpoints
test_malformed_user_context_allows_null_permission if {
    result := authz.decision
        with http.send as mock_send_authenticated_no_groups_field
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/users/me")
    result.allow == true
    result.reason == "authenticated_endpoint"
}

# Test: Known path but unsupported HTTP method is denied (e.g., POST /v2/permissions — only GET defined)
# Path matches but method has no permission mapping → is_known_endpoint=false → "unknown_endpoint"
test_unsupported_method_on_known_path_denied if {
    result := authz.decision
        with http.send as mock_send_admin
        with data.config as mock_http.mock_config
        with input as portal_request("POST", "/v2/permissions")
    result.allow == false
    result.reason == "unknown_endpoint"
}

# Test: DELETE on permissions endpoint (only GET defined) is also denied
test_delete_on_readonly_endpoint_denied if {
    result := authz.decision
        with http.send as mock_send_admin
        with data.config as mock_http.mock_config
        with input as portal_request("DELETE", "/v2/permissions/perm-123")
    result.allow == false
    result.reason == "unknown_endpoint"
}

# Test: Unknown endpoint path results in denial
test_unknown_endpoint_denied if {
    result := authz.decision
        with http.send as mock_send_all_read
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/unknown-resource")
    result.allow == false
    result.reason == "unknown_endpoint"
}

# Test: Missing service metadata results in denial
test_unknown_backend_denied if {
    result := authz.decision
        with http.send as mock_send_reader
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "method": "GET",
                "path": "/v2/users",
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("test-user")
                }
            }
        }
    result.allow == false
    result.reason == "unknown_backend"
}

# Test: http.send error results in denial (fail-secure)
test_http_send_error_denied if {
    result := authz.decision
        with http.send as mock_send_error
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/users")
    result.allow == false
    result.reason == "missing_user_context"
}

# Test: Default deny when no rules match
test_default_deny if {
    authz.allow == false with input as {}
}

# =============================================================================
# SCOPE ENFORCEMENT TESTS (M5.1)
# =============================================================================

# Mock for user with DATASOURCE-scoped permission to ds-123
mock_send_ds123_reader(_) := {"status_code": 200, "body": user_context_with_scope(["READ_DATASOURCE"], "DATASOURCE", "ds-123")}

# Mock for user with DATASET-scoped permission to dataset-abc
mock_send_dataset_abc_reader(_) := {"status_code": 200, "body": user_context_with_scope(["READ_DATASET"], "DATASET", "dataset-abc")}

# Mock for user with wrong scope (has permission but for different resource)
mock_send_wrong_scope(_) := {"status_code": 200, "body": user_context_with_scope(["READ_DATASOURCE"], "DATASOURCE", "other-ds")}

# Test: User with correct DATASOURCE scope can access that datasource
test_scope_datasource_correct if {
    result := authz.decision
        with http.send as mock_send_ds123_reader
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources/ds-123")
    result.allow == true
    result.reason == "permission_granted"
}

# Test: User with wrong DATASOURCE scope is denied
test_scope_datasource_wrong if {
    result := authz.decision
        with http.send as mock_send_wrong_scope
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources/ds-123")
    result.allow == false
    result.reason == "permission_denied"
}

# Test: User with correct DATASET scope can access that dataset
test_scope_dataset_correct if {
    result := authz.decision
        with http.send as mock_send_dataset_abc_reader
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasets/dataset-abc")
    result.allow == true
    result.reason == "permission_granted"
}

# Test: User with DATASET scope for one dataset cannot access another
test_scope_dataset_wrong if {
    result := authz.decision
        with http.send as mock_send_dataset_abc_reader
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasets/other-dataset")
    result.allow == false
    result.reason == "permission_denied"
}

# Test: TENANT-scoped user can access tenant-level resources (users)
test_scope_tenant_user_access if {
    result := authz.decision
        with http.send as mock_send_admin
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/users/user-123")
    result.allow == true
    result.reason == "permission_granted"
}

# Test: Collection endpoints work with matching scope type (Q-006: fail-secure)
test_scope_collection_matching_scope if {
    result := authz.decision
        with http.send as mock_send_ds123_reader
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources")
    result.allow == true
    result.reason == "permission_granted"
}

# =============================================================================
# SCOPE HEADER TESTS (M5.5 - Collection Endpoint Filtering)
# =============================================================================
# These tests verify that the decision includes X-Allowed-Scope-Ids header
# for backend collection filtering.

# Helper: User context with multiple DATASOURCE-scoped assignments
user_context_multi_datasource(perms) := {
    "userId": "user-1",
    "externalId": "keycloak-sub-1",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [
            {
                "roleId": "role-1",
                "roleName": "Reader",
                "roleType": "DATA",
                "scopeType": "DATASOURCE",
                "scopeId": "ds-aaa",
                "permissions": perms
            },
            {
                "roleId": "role-2",
                "roleName": "Reader",
                "roleType": "DATA",
                "scopeType": "DATASOURCE",
                "scopeId": "ds-zzz",
                "permissions": perms
            }
        ]
    }]
}

# Helper: User with both TENANT and DATASOURCE scopes (TENANT takes priority)
user_context_tenant_and_datasource(perms) := {
    "userId": "user-1",
    "externalId": "keycloak-sub-1",
    "groups": [{
        "id": "group-1",
        "name": "Admin Group",
        "assignments": [
            {
                "roleId": "role-admin",
                "roleName": "Admin",
                "roleType": "SYSTEM",
                "scopeType": "TENANT",
                "scopeId": "tenant-1",
                "permissions": perms
            }
        ]
    },
    {
        "id": "group-2",
        "name": "Reader Group",
        "assignments": [
            {
                "roleId": "role-reader",
                "roleName": "Reader",
                "roleType": "DATA",
                "scopeType": "DATASOURCE",
                "scopeId": "ds-123",
                "permissions": perms
            }
        ]
    }]
}

# Helper: User with multiple groups with different scopes
user_context_multiple_groups_datasource(perms) := {
    "userId": "user-1",
    "externalId": "keycloak-sub-1",
    "groups": [
        {
            "id": "group-1",
            "name": "Group A",
            "assignments": [{
                "roleId": "role-1",
                "roleName": "Reader",
                "roleType": "DATA",
                "scopeType": "DATASOURCE",
                "scopeId": "ds-from-group1",
                "permissions": perms
            }]
        },
        {
            "id": "group-2",
            "name": "Group B",
            "assignments": [{
                "roleId": "role-2",
                "roleName": "Reader",
                "roleType": "DATA",
                "scopeType": "DATASOURCE",
                "scopeId": "ds-from-group2",
                "permissions": perms
            }]
        }
    ]
}

# User context with DATASET scope (not DATASOURCE)
user_context_dataset_scope(perms) := {
    "userId": "user-1",
    "externalId": "keycloak-sub-1",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "Reader",
            "roleType": "DATA",
            "scopeType": "DATASET",
            "scopeId": "dataset-123",
            "permissions": perms
        }]
    }]
}

# Mock functions for header tests
mock_send_multi_datasource(_) := {"status_code": 200, "body": user_context_multi_datasource(["READ_DATASOURCE"])}
mock_send_tenant_and_datasource(_) := {"status_code": 200, "body": user_context_tenant_and_datasource(["READ_DATASOURCE"])}
mock_send_multi_groups_datasource(_) := {"status_code": 200, "body": user_context_multiple_groups_datasource(["READ_DATASOURCE"])}
mock_send_dataset_scope(_) := {"status_code": 200, "body": user_context_dataset_scope(["READ_DATASET"])}
mock_send_no_matching_permission(_) := {"status_code": 200, "body": user_context_tenant_scoped(["OTHER_PERMISSION"])}

# Test: TENANT scope returns wildcard "*" header
test_scope_header_tenant_wildcard if {
    result := authz.decision
        with http.send as mock_send_admin
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/users")
    result.allow == true
    result.headers["X-Allowed-Scope-Ids"] == "*"
}

# Test: Multiple DATASOURCE scopes return comma-separated sorted IDs
test_scope_header_multiple_datasources if {
    result := authz.decision
        with http.send as mock_send_multi_datasource
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources")
    result.allow == true
    # IDs are sorted alphabetically: ds-aaa, ds-zzz
    result.headers["X-Allowed-Scope-Ids"] == "ds-aaa,ds-zzz"
}

# Test: TENANT scope takes priority over DATASOURCE (returns wildcard)
test_scope_header_tenant_priority if {
    result := authz.decision
        with http.send as mock_send_tenant_and_datasource
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources")
    result.allow == true
    result.headers["X-Allowed-Scope-Ids"] == "*"
}

# Test: Scopes collected across multiple groups
test_scope_header_multiple_groups if {
    result := authz.decision
        with http.send as mock_send_multi_groups_datasource
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources")
    result.allow == true
    # IDs from both groups, sorted
    result.headers["X-Allowed-Scope-Ids"] == "ds-from-group1,ds-from-group2"
}

# Test: Single DATASOURCE scope returns single ID
test_scope_header_single_datasource if {
    result := authz.decision
        with http.send as mock_send_ds123_reader
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources")
    result.allow == true
    result.headers["X-Allowed-Scope-Ids"] == "ds-123"
}

# Test: Resource endpoint (not collection) still includes header
test_scope_header_resource_endpoint if {
    result := authz.decision
        with http.send as mock_send_ds123_reader
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources/ds-123")
    result.allow == true
    # Resource endpoint allowed (scope matches), header still present
    result.headers["X-Allowed-Scope-Ids"] == "ds-123"
}

# Test: DATASET endpoint with DATASET scope returns DATASET IDs
test_scope_header_dataset_endpoint if {
    result := authz.decision
        with http.send as mock_send_dataset_scope
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasets")
    result.allow == true
    result.headers["X-Allowed-Scope-Ids"] == "dataset-123"
}

# Test: Q-006 fail-secure — scope type mismatch on collection endpoint denies access
# User has DATASET scope with READ_DATASOURCE permission, but accessing /v2/datasources
# which expects DATASOURCE scope type. Neither TENANT nor DATASOURCE match → denied.
user_context_dataset_scope_with_datasource_perm := {
    "userId": "user-1",
    "externalId": "keycloak-sub-1",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "Reader",
            "roleType": "DATA",
            "scopeType": "DATASET",
            "scopeId": "dataset-123",
            "permissions": ["READ_DATASOURCE", "READ_DATASET"]
        }]
    }]
}

mock_send_dataset_scope_with_datasource_perm(_) := {"status_code": 200, "body": user_context_dataset_scope_with_datasource_perm}

test_scope_header_wrong_scope_type if {
    result := authz.decision
        with http.send as mock_send_dataset_scope_with_datasource_perm
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/datasources")
    # Q-006: Fail-secure — scope type mismatch on collection endpoint denies access.
    # User has READ_DATASOURCE but only via DATASET scope (not DATASOURCE or TENANT),
    # so access is denied rather than returning empty results.
    result.allow == false
    result.reason == "permission_denied"
}

# Test: Decision has headers object structure
test_scope_header_structure if {
    result := authz.decision
        with http.send as mock_send_admin
        with data.config as mock_http.mock_config
        with input as portal_request("GET", "/v2/users")
    result.headers != null
    object.keys(result.headers) == {"X-Allowed-Scope-Ids"}
}
