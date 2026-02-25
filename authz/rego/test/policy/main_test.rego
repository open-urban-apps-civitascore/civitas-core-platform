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
		"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")},
	},
	"service": {"name": "portal-backend"},
}

# Request without userinfo header (no auth)
portal_request_no_auth(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {},
	},
	"service": {"name": "portal-backend"},
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
			"permissions": perms,
		}],
	}],
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
			"permissions": perms,
		}],
	}],
}

# Mock http.send functions for TENANT-scoped resources (users, groups, roles, etc.)
mock_send_admin(_) := {"status_code": 200, "body": user_context_tenant_scoped(["USER_READ", "USER_CREATE", "USER_DELETE"])}
mock_send_reader(_) := {"status_code": 200, "body": user_context_tenant_scoped(["USER_READ"])}

# Mock for collection endpoints (any scope works for collections)
mock_send_dataset_creator(_) := {"status_code": 200, "body": user_context_tenant_scoped(["DATASET_CREATE"])}

# Mock for DATASOURCE resource endpoint - must be scoped to the specific datasource ID
mock_send_datasource_updater(_) := {"status_code": 200, "body": user_context_with_scope(["DATASOURCE_UPDATE"], "DATASOURCE", "abc-123")}

# Mock for collection GET (any scope works)
mock_send_all_read(_) := {"status_code": 200, "body": user_context_tenant_scoped(["USER_READ", "DATASET_READ"])}

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
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"USER_READ"}
}

# Test: Authenticated user without required permission is denied
test_permission_denied if {
	result := authz.decision with http.send as mock_send_reader
		with data.config as mock_http.mock_config
		with input as portal_request("DELETE", "/v2/users/123")
	result.allow == false
	result.reason == "permission_denied"
	result.required_permissions == {"USER_DELETE"}
}

# Test: POST to collection requires CREATE permission
test_create_permission if {
	result := authz.decision with http.send as mock_send_dataset_creator
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v2/datasets")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_CREATE"}
}

# Test: PUT to resource requires UPDATE permission
test_update_permission if {
	result := authz.decision with http.send as mock_send_datasource_updater
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
	result := authz.decision with http.send as mock_send_authenticated_no_groups
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
	result := authz.decision with http.send as mock_send_empty
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
	result := authz.decision with http.send as mock_send_null_ids
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasets")
	result.allow == false
	result.reason == "missing_user_context"
}

# Test: Malformed user context (missing groups) still allows null-permission endpoints
test_malformed_user_context_allows_null_permission if {
	result := authz.decision with http.send as mock_send_authenticated_no_groups_field
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users/me")
	result.allow == true
	result.reason == "authenticated_endpoint"
}

# Test: Known path but unsupported HTTP method is denied (e.g., POST /v2/permissions — only GET defined)
# Path matches but method has no permission mapping → is_known_endpoint=false → "unknown_endpoint"
test_unsupported_method_on_known_path_denied if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v2/permissions")
	result.allow == false
	result.reason == "unknown_endpoint"
}

# Test: DELETE on permissions endpoint (only GET defined) is also denied
test_delete_on_readonly_endpoint_denied if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("DELETE", "/v2/permissions/perm-123")
	result.allow == false
	result.reason == "unknown_endpoint"
}

# Test: Unknown endpoint path results in denial
test_unknown_endpoint_denied if {
	result := authz.decision with http.send as mock_send_all_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/unknown-resource")
	result.allow == false
	result.reason == "unknown_endpoint"
}

# Test: Missing service metadata results in denial
test_unknown_backend_denied if {
	result := authz.decision with http.send as mock_send_reader
		with data.config as mock_http.mock_config
		with input as {"request": {
			"method": "GET",
			"path": "/v2/users",
			"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")},
		}}
	result.allow == false
	result.reason == "unknown_backend"
}

# Test: http.send error results in denial (fail-secure)
test_http_send_error_denied if {
	result := authz.decision with http.send as mock_send_error
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
mock_send_ds123_reader(_) := {"status_code": 200, "body": user_context_with_scope(["DATASOURCE_READ"], "DATASOURCE", "ds-123")}

# Mock for user with DATASET-scoped permission to dataset-abc
mock_send_dataset_abc_reader(_) := {"status_code": 200, "body": user_context_with_scope(["DATASET_READ"], "DATASET", "dataset-abc")}

# Mock for user with wrong scope (has permission but for different resource)
mock_send_wrong_scope(_) := {"status_code": 200, "body": user_context_with_scope(["DATASOURCE_READ"], "DATASOURCE", "other-ds")}

# Test: User with correct DATASOURCE scope can access that datasource
test_scope_datasource_correct if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources/ds-123")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: User with wrong DATASOURCE scope is denied
test_scope_datasource_wrong if {
	result := authz.decision with http.send as mock_send_wrong_scope
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources/ds-123")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: User with correct DATASET scope can access that dataset
test_scope_dataset_correct if {
	result := authz.decision with http.send as mock_send_dataset_abc_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasets/dataset-abc")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: User with DATASET scope for one dataset cannot access another
test_scope_dataset_wrong if {
	result := authz.decision with http.send as mock_send_dataset_abc_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasets/other-dataset")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: TENANT-scoped user can access tenant-level resources (users)
test_scope_tenant_user_access if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users/user-123")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: Collection endpoints work with matching scope type (Q-006: fail-secure)
test_scope_collection_matching_scope if {
	result := authz.decision with http.send as mock_send_ds123_reader
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
				"permissions": perms,
			},
			{
				"roleId": "role-2",
				"roleName": "Reader",
				"roleType": "DATA",
				"scopeType": "DATASOURCE",
				"scopeId": "ds-zzz",
				"permissions": perms,
			},
		],
	}],
}

# Helper: User with both TENANT and DATASOURCE scopes (TENANT takes priority)
user_context_tenant_and_datasource(perms) := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [
		{
			"id": "group-1",
			"name": "Admin Group",
			"assignments": [{
				"roleId": "role-admin",
				"roleName": "Admin",
				"roleType": "SYSTEM",
				"scopeType": "TENANT",
				"scopeId": "tenant-1",
				"permissions": perms,
			}],
		},
		{
			"id": "group-2",
			"name": "Reader Group",
			"assignments": [{
				"roleId": "role-reader",
				"roleName": "Reader",
				"roleType": "DATA",
				"scopeType": "DATASOURCE",
				"scopeId": "ds-123",
				"permissions": perms,
			}],
		},
	],
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
				"permissions": perms,
			}],
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
				"permissions": perms,
			}],
		},
	],
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
			"permissions": perms,
		}],
	}],
}

# Mock functions for header tests
mock_send_multi_datasource(_) := {"status_code": 200, "body": user_context_multi_datasource(["DATASOURCE_READ"])}
mock_send_tenant_and_datasource(_) := {"status_code": 200, "body": user_context_tenant_and_datasource(["DATASOURCE_READ"])}
mock_send_multi_groups_datasource(_) := {"status_code": 200, "body": user_context_multiple_groups_datasource(["DATASOURCE_READ"])}
mock_send_dataset_scope(_) := {"status_code": 200, "body": user_context_dataset_scope(["DATASET_READ"])}
mock_send_no_matching_permission(_) := {"status_code": 200, "body": user_context_tenant_scoped(["OTHER_PERMISSION"])}

# Test: TENANT scope returns wildcard "*" header
test_scope_header_tenant_wildcard if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "*"
}

# Test: Multiple DATASOURCE scopes return comma-separated sorted IDs
test_scope_header_multiple_datasources if {
	result := authz.decision with http.send as mock_send_multi_datasource
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources")
	result.allow == true

	# IDs are sorted alphabetically: ds-aaa, ds-zzz
	result.headers["X-Allowed-Scope-Ids"] == "ds-aaa,ds-zzz"
}

# Test: TENANT scope takes priority over DATASOURCE (returns wildcard)
test_scope_header_tenant_priority if {
	result := authz.decision with http.send as mock_send_tenant_and_datasource
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "*"
}

# Test: Scopes collected across multiple groups
test_scope_header_multiple_groups if {
	result := authz.decision with http.send as mock_send_multi_groups_datasource
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources")
	result.allow == true

	# IDs from both groups, sorted
	result.headers["X-Allowed-Scope-Ids"] == "ds-from-group1,ds-from-group2"
}

# Test: Single DATASOURCE scope returns single ID
test_scope_header_single_datasource if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "ds-123"
}

# Test: Resource endpoint (not collection) still includes header
test_scope_header_resource_endpoint if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources/ds-123")
	result.allow == true

	# Resource endpoint allowed (scope matches), header still present
	result.headers["X-Allowed-Scope-Ids"] == "ds-123"
}

# Test: DATASET endpoint with DATASET scope returns DATASET IDs
test_scope_header_dataset_endpoint if {
	result := authz.decision with http.send as mock_send_dataset_scope
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasets")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "dataset-123"
}

# Test: Q-006 fail-secure — scope type mismatch on collection endpoint denies access
# User has DATASET scope with DATASOURCE_READ permission, but accessing /v2/datasources
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
			"permissions": ["DATASOURCE_READ", "DATASET_READ"],
		}],
	}],
}

mock_send_dataset_scope_with_datasource_perm(_) := {"status_code": 200, "body": user_context_dataset_scope_with_datasource_perm}

test_scope_header_wrong_scope_type if {
	result := authz.decision with http.send as mock_send_dataset_scope_with_datasource_perm
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources")

	# Q-006: Fail-secure — scope type mismatch on collection endpoint denies access.
	# User has DATASOURCE_READ but only via DATASET scope (not DATASOURCE or TENANT),
	# so access is denied rather than returning empty results.
	result.allow == false
	result.reason == "permission_denied"
}

# Test: Decision has headers object structure
test_scope_header_structure if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users")
	result.headers != null
	object.keys(result.headers) == {"X-Allowed-Scope-Ids"}
}

# =============================================================================
# AND-PERMISSION SCOPE HEADER TESTS
# =============================================================================
# Tests that AND-permissions interact correctly with scope header generation.
# TENANT wildcard and specific scope IDs must satisfy ALL required permissions.

# Helper: User with TENANT scope and both AND-permissions, plus DATASET scope
# for the specific resource (TENANT alone doesn't grant DATASET resource access per S-2)
user_context_tenant_and_perms := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Admin Group",
		"assignments": [
			{
				"roleId": "role-1",
				"roleName": "Admin",
				"roleType": "DATA",
				"scopeType": "TENANT",
				"scopeId": "tenant-1",
				"permissions": ["DATASET_UPDATE", "DATASET_RELEASE", "DATASET_READ"],
			},
			{
				"roleId": "role-2",
				"roleName": "DataSteward",
				"roleType": "DATA",
				"scopeType": "DATASET",
				"scopeId": "abc",
				"permissions": ["DATASET_UPDATE", "DATASET_RELEASE"],
			},
		],
	}],
}

mock_send_tenant_and_perms(_) := {"status_code": 200, "body": user_context_tenant_and_perms}

# Test: AND-permission with TENANT scope returns wildcard header
# User has both TENANT and DATASET scoped assignments with both AND-permissions.
# Permission check passes via DATASET scope (matching resource), but scope header
# shows "*" because has_tenant_scope finds all required perms at TENANT scope.
test_scope_header_and_tenant_wildcard if {
	result := authz.decision with http.send as mock_send_tenant_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/abc/published/meta")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "*"
}

# Helper: User with TENANT scope but missing one AND-permission
user_context_tenant_missing_one := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Role",
			"roleType": "DATA",
			"scopeType": "TENANT",
			"scopeId": "tenant-1",
			"permissions": ["DATASET_UPDATE"],
		}],
	}],
}

mock_send_tenant_missing_one(_) := {"status_code": 200, "body": user_context_tenant_missing_one}

# Test: AND-permission with TENANT scope but missing one permission is denied
test_scope_header_and_tenant_missing_one if {
	result := authz.decision with http.send as mock_send_tenant_missing_one
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/abc/published/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# Helper: User with specific scope and both AND-permissions
user_context_specific_and_perms := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Role",
			"roleType": "DATA",
			"scopeType": "DATASET",
			"scopeId": "dataset-abc",
			"permissions": ["DATASET_UPDATE", "DATASET_RELEASE"],
		}],
	}],
}

mock_send_specific_and_perms(_) := {"status_code": 200, "body": user_context_specific_and_perms}

# Test: AND-permission with specific scope - both permissions present
test_scope_header_and_specific_both if {
	result := authz.decision with http.send as mock_send_specific_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/dataset-abc/published/meta")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "dataset-abc"
}

# Helper: User with specific scope but only one AND-permission
user_context_specific_partial := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Role",
			"roleType": "DATA",
			"scopeType": "DATASET",
			"scopeId": "dataset-abc",
			"permissions": ["DATASET_UPDATE"],
		}],
	}],
}

mock_send_specific_partial(_) := {"status_code": 200, "body": user_context_specific_partial}

# Test: AND-permission with specific scope - partial permissions denied
test_scope_header_and_specific_partial if {
	result := authz.decision with http.send as mock_send_specific_partial
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/dataset-abc/published/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# =============================================================================
# AND-PERMISSION CROSS-GROUP TESTS
# =============================================================================
# Tests that AND-permissions work when required permissions come from different
# groups/assignments, and that scope intersection is correct.

# Helper: User with AND-permissions split across two groups, same scope ID
user_context_and_cross_group := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [
		{
			"id": "group-1",
			"name": "Editors",
			"assignments": [{
				"roleId": "role-1",
				"roleName": "Editor",
				"roleType": "DATA",
				"scopeType": "DATASET",
				"scopeId": "dataset-abc",
				"permissions": ["DATASET_UPDATE"],
			}],
		},
		{
			"id": "group-2",
			"name": "Releasers",
			"assignments": [{
				"roleId": "role-2",
				"roleName": "Releaser",
				"roleType": "DATA",
				"scopeType": "DATASET",
				"scopeId": "dataset-abc",
				"permissions": ["DATASET_RELEASE"],
			}],
		},
	],
}

mock_send_and_cross_group(_) := {"status_code": 200, "body": user_context_and_cross_group}

# Test: AND-permission satisfied across different groups for same scope
test_and_cross_group_allowed if {
	result := authz.decision with http.send as mock_send_and_cross_group
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/dataset-abc/published/meta")
	result.allow == true
	result.reason == "permission_granted"
	result.headers["X-Allowed-Scope-Ids"] == "dataset-abc"
}

# Helper: User with AND-permissions split across groups, different scope IDs
# DATASET_UPDATE for {ds-1, ds-2}, DATASET_RELEASE for {ds-2, ds-3}
# Expected intersection: {ds-2}
user_context_and_partial_overlap := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [
		{
			"id": "group-1",
			"name": "Editors",
			"assignments": [
				{
					"roleId": "role-1",
					"roleName": "Editor",
					"roleType": "DATA",
					"scopeType": "DATASET",
					"scopeId": "ds-1",
					"permissions": ["DATASET_UPDATE"],
				},
				{
					"roleId": "role-1",
					"roleName": "Editor",
					"roleType": "DATA",
					"scopeType": "DATASET",
					"scopeId": "ds-2",
					"permissions": ["DATASET_UPDATE"],
				},
			],
		},
		{
			"id": "group-2",
			"name": "Releasers",
			"assignments": [
				{
					"roleId": "role-2",
					"roleName": "Releaser",
					"roleType": "DATA",
					"scopeType": "DATASET",
					"scopeId": "ds-2",
					"permissions": ["DATASET_RELEASE"],
				},
				{
					"roleId": "role-2",
					"roleName": "Releaser",
					"roleType": "DATA",
					"scopeType": "DATASET",
					"scopeId": "ds-3",
					"permissions": ["DATASET_RELEASE"],
				},
			],
		},
	],
}

mock_send_and_partial_overlap(_) := {"status_code": 200, "body": user_context_and_partial_overlap}

# Test: AND-permission scope intersection — only ds-2 has both permissions
test_and_scope_intersection if {
	result := authz.decision with http.send as mock_send_and_partial_overlap
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/ds-2/published/meta")
	result.allow == true
	result.reason == "permission_granted"
	result.headers["X-Allowed-Scope-Ids"] == "ds-2"
}

# Test: AND-permission scope intersection — ds-1 only has UPDATE, not RELEASE
test_and_scope_intersection_denied_missing_release if {
	result := authz.decision with http.send as mock_send_and_partial_overlap
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/ds-1/published/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: AND-permission scope intersection — ds-3 only has RELEASE, not UPDATE
test_and_scope_intersection_denied_missing_update if {
	result := authz.decision with http.send as mock_send_and_partial_overlap
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/ds-3/published/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# Helper: User with disjoint scopes — no overlap at all
# DATASET_UPDATE for {ds-1}, DATASET_RELEASE for {ds-2}
user_context_and_disjoint := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [
		{
			"id": "group-1",
			"name": "Editors",
			"assignments": [{
				"roleId": "role-1",
				"roleName": "Editor",
				"roleType": "DATA",
				"scopeType": "DATASET",
				"scopeId": "ds-1",
				"permissions": ["DATASET_UPDATE"],
			}],
		},
		{
			"id": "group-2",
			"name": "Releasers",
			"assignments": [{
				"roleId": "role-2",
				"roleName": "Releaser",
				"roleType": "DATA",
				"scopeType": "DATASET",
				"scopeId": "ds-2",
				"permissions": ["DATASET_RELEASE"],
			}],
		},
	],
}

mock_send_and_disjoint(_) := {"status_code": 200, "body": user_context_and_disjoint}

# Test: AND-permission with completely disjoint scopes — denied everywhere
test_and_disjoint_scopes_denied if {
	result := authz.decision with http.send as mock_send_and_disjoint
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/ds-1/published/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# =============================================================================
# NEW ENDPOINT INTEGRATION TESTS
# =============================================================================
# End-to-end tests for unpublish, ready, unready endpoints.

# Test: POST /datasets/{id}/unpublish requires only DATASET_UPDATE
test_unpublish_allowed if {
	result := authz.decision with http.send as mock_send_specific_partial
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v2/datasets/dataset-abc/unpublish")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE"}
}

# Test: POST /datasets/{id}/publish requires DATASET_RELEASE (single-perm)
test_publish_single_perm if {
	result := authz.decision with http.send as mock_send_specific_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v2/datasets/dataset-abc/publish")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_RELEASE"}
}

# Test: POST /datasets/{id}/ready requires only DATASET_UPDATE
test_ready_allowed if {
	result := authz.decision with http.send as mock_send_specific_partial
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v2/datasets/dataset-abc/ready")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE"}
}

# Test: POST /datasets/{id}/unready requires only DATASET_UPDATE
test_unready_allowed if {
	result := authz.decision with http.send as mock_send_specific_partial
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v2/datasets/dataset-abc/unready")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE"}
}

# Test: PUT /datasets/{id}/published/meta requires AND-permission
test_published_meta_and_perm if {
	result := authz.decision with http.send as mock_send_specific_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/dataset-abc/published/meta")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE", "DATASET_RELEASE"}
}

# =============================================================================
# TENANT SCOPE INHERITANCE TESTS
# =============================================================================
# ADM spec: TENANT scope cascades to all resource endpoints (Q-005 resolved).
# DATASPACE → child resource inheritance is deferred (not in v2.0).
# These tests verify TENANT-scoped permissions grant access to resource endpoints
# for datasets, datasources, and datastructures, and that narrow scopes do NOT
# inherit upward.

# Helper: User with DATASET_READ at TENANT scope (not DATASET scope)
user_context_tenant_dataset_read := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Admin Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Admin",
			"roleType": "SYSTEM",
			"scopeType": "TENANT",
			"scopeId": "tenant-1",
			"permissions": ["DATASET_READ"],
		}],
	}],
}

mock_send_tenant_dataset_read(_) := {"status_code": 200, "body": user_context_tenant_dataset_read}

# Test: TENANT-scoped DATASET_READ grants access to a specific dataset resource
# (TENANT cascades to DATASET resource endpoints per ADM spec)
test_tenant_scope_cascades_to_dataset_resource if {
	result := authz.decision with http.send as mock_send_tenant_dataset_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasets/some-dataset-id")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: TENANT-scoped DATASET_READ DOES grant access to dataset collection
# (collection endpoints allow TENANT scope — list filtering via header)
test_tenant_scope_allows_dataset_collection if {
	result := authz.decision with http.send as mock_send_tenant_dataset_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasets")
	result.allow == true
	result.reason == "permission_granted"
}

# Helper: User with DATASOURCE_READ at TENANT scope
user_context_tenant_datasource_read := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Admin Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Admin",
			"roleType": "SYSTEM",
			"scopeType": "TENANT",
			"scopeId": "tenant-1",
			"permissions": ["DATASOURCE_READ"],
		}],
	}],
}

mock_send_tenant_datasource_read(_) := {"status_code": 200, "body": user_context_tenant_datasource_read}

# Test: TENANT-scoped DATASOURCE_READ grants access to a specific datasource resource
test_tenant_scope_cascades_to_datasource_resource if {
	result := authz.decision with http.send as mock_send_tenant_datasource_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasources/some-datasource-id")
	result.allow == true
	result.reason == "permission_granted"
}

# Helper: User with DATASTRUCTURE_READ at TENANT scope
user_context_tenant_datastructure_read := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Admin Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Admin",
			"roleType": "SYSTEM",
			"scopeType": "TENANT",
			"scopeId": "tenant-1",
			"permissions": ["DATASTRUCTURE_READ"],
		}],
	}],
}

mock_send_tenant_datastructure_read(_) := {"status_code": 200, "body": user_context_tenant_datastructure_read}

# Test: TENANT-scoped DATASTRUCTURE_READ grants access to a specific datastructure resource
test_tenant_scope_cascades_to_datastructure_resource if {
	result := authz.decision with http.send as mock_send_tenant_datastructure_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datastructures/some-ds-id")
	result.allow == true
	result.reason == "permission_granted"
}

# Helper: User with USER_READ at DATASET scope (narrow scope, wrong direction)
user_context_dataset_scoped_user_read := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Role",
			"roleType": "DATA",
			"scopeType": "DATASET",
			"scopeId": "dataset-1",
			"permissions": ["USER_READ"],
		}],
	}],
}

mock_send_dataset_scoped_user_read(_) := {"status_code": 200, "body": user_context_dataset_scoped_user_read}

# Test: No upward inheritance — DATASET-scoped USER_READ does NOT grant access
# to tenant-level resource endpoints. Inheritance is downward only.
test_no_upward_inheritance if {
	result := authz.decision with http.send as mock_send_dataset_scoped_user_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users/user-123")
	result.allow == false
	result.reason == "permission_denied"
}

# Helper: AND-permission with mixed scopes — DATASET_UPDATE at TENANT, DATASET_RELEASE at DATASET
# Tests published/meta PUT which requires ["DATASET_UPDATE", "DATASET_RELEASE"]
user_context_and_mixed_scopes := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [
		{
			"id": "group-1",
			"name": "Admins",
			"assignments": [{
				"roleId": "role-1",
				"roleName": "Admin",
				"roleType": "SYSTEM",
				"scopeType": "TENANT",
				"scopeId": "tenant-1",
				"permissions": ["DATASET_UPDATE"],
			}],
		},
		{
			"id": "group-2",
			"name": "Publishers",
			"assignments": [{
				"roleId": "role-2",
				"roleName": "Publisher",
				"roleType": "DATA",
				"scopeType": "DATASET",
				"scopeId": "ds-1",
				"permissions": ["DATASET_RELEASE"],
			}],
		},
	],
}

mock_send_and_mixed_scopes(_) := {"status_code": 200, "body": user_context_and_mixed_scopes}

# Test: AND-permission with TENANT UPDATE + DATASET RELEASE → allowed
# TENANT-scoped DATASET_UPDATE inherits to resource endpoint, DATASET RELEASE
# matches directly. Both permissions satisfied.
test_and_mixed_scopes_allowed if {
	result := authz.decision with http.send as mock_send_and_mixed_scopes
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/ds-1/published/meta")
	result.allow == true
	result.reason == "permission_granted"
}

# Helper: User with both AND-permissions at TENANT scope
user_context_tenant_both_and_perms := {
	"userId": "user-1",
	"externalId": "keycloak-sub-1",
	"groups": [{
		"id": "group-1",
		"name": "Admin Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Admin",
			"roleType": "SYSTEM",
			"scopeType": "TENANT",
			"scopeId": "tenant-1",
			"permissions": ["DATASET_UPDATE", "DATASET_RELEASE"],
		}],
	}],
}

mock_send_tenant_both_and_perms(_) := {"status_code": 200, "body": user_context_tenant_both_and_perms}

# Test: AND-permission with both permissions at TENANT scope → allowed
# TENANT inheritance satisfies both UPDATE and RELEASE for resource endpoint
test_tenant_and_permission_both_tenant if {
	result := authz.decision with http.send as mock_send_tenant_both_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/abc/published/meta")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: AND-permission with only UPDATE at TENANT, no RELEASE anywhere → denied
test_tenant_and_permission_one_missing if {
	result := authz.decision with http.send as mock_send_tenant_missing_one
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/abc/published/meta")
	result.allow == false
	result.reason == "permission_denied"
}
