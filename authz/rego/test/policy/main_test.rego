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

# --- Permission-based access mocks ---
mock_send_admin(_) := {"status_code": 200, "body": mock_http.user_with_permissions(["USER_READ", "USER_CREATE", "USER_DELETE"])}
mock_send_reader(_) := {"status_code": 200, "body": mock_http.user_with_permissions(["USER_READ"])}
mock_send_dataset_creator(_) := {"status_code": 200, "body": mock_http.user_with_permissions(["DATASET_CREATE"])}
mock_send_datasource_updater(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASOURCE_UPDATE"], "DATASOURCE", "abc-123")}
mock_send_all_read(_) := {"status_code": 200, "body": mock_http.user_with_permissions(["USER_READ", "DATASET_READ"])}

# --- Edge case mocks (minimal/malformed user contexts) ---
mock_send_authenticated_no_groups(_) := {"status_code": 200, "body": {"userId": "123", "externalId": "keycloak-sub-123", "groups": []}}
mock_send_authenticated_no_groups_field(_) := {"status_code": 200, "body": {"userId": "123", "externalId": "ext-123"}}
mock_send_empty(_) := {"status_code": 200, "body": {}}
mock_send_null_ids(_) := {"status_code": 200, "body": {"userId": null, "externalId": null, "groups": []}}
mock_send_error(_) := {"status_code": 500, "body": {"error": "Internal server error"}}

# --- Scope enforcement mocks ---
mock_send_ds123_reader(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATASOURCE", "ds-123")}
mock_send_dataset_abc_reader(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", "dataset-abc")}
mock_send_wrong_scope(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATASOURCE", "other-ds")}

# --- Scope header mocks ---
mock_send_multi_datasource(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASOURCE_READ"], "scope_type": "DATASOURCE", "scope_id": "ds-aaa"},
	{"perms": ["DATASOURCE_READ"], "scope_type": "DATASOURCE", "scope_id": "ds-zzz"},
])}

mock_send_tenant_and_datasource(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASOURCE_READ"], "scope_type": "TENANT", "scope_id": "tenant-1"},
	{"perms": ["DATASOURCE_READ"], "scope_type": "DATASOURCE", "scope_id": "ds-123"},
])}

mock_send_multi_groups_datasource(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASOURCE_READ"], "scope_type": "DATASOURCE", "scope_id": "ds-from-group1"},
	{"perms": ["DATASOURCE_READ"], "scope_type": "DATASOURCE", "scope_id": "ds-from-group2"},
])}

mock_send_dataset_scope(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", "dataset-123")}
mock_send_no_matching_permission(_) := {"status_code": 200, "body": mock_http.user_with_permissions(["OTHER_PERMISSION"])}
mock_send_dataset_scope_with_datasource_perm(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASOURCE_READ", "DATASET_READ"], "DATASET", "dataset-123")}

# --- AND-permission scope header mocks ---
mock_send_tenant_and_perms(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASET_UPDATE", "DATASET_RELEASE", "DATASET_READ"], "scope_type": "TENANT", "scope_id": "tenant-1"},
	{"perms": ["DATASET_UPDATE", "DATASET_RELEASE"], "scope_type": "DATASET", "scope_id": "abc"},
])}

mock_send_tenant_missing_one(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_UPDATE"], "TENANT", "tenant-1")}
mock_send_specific_and_perms(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_UPDATE", "DATASET_RELEASE"], "DATASET", "dataset-abc")}
mock_send_specific_partial(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_UPDATE"], "DATASET", "dataset-abc")}

# --- AND-permission cross-group mocks ---
mock_send_and_cross_group(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASET_UPDATE"], "scope_type": "DATASET", "scope_id": "dataset-abc"},
	{"perms": ["DATASET_RELEASE"], "scope_type": "DATASET", "scope_id": "dataset-abc"},
])}

mock_send_and_partial_overlap(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASET_UPDATE"], "scope_type": "DATASET", "scope_id": "ds-1"},
	{"perms": ["DATASET_UPDATE"], "scope_type": "DATASET", "scope_id": "ds-2"},
	{"perms": ["DATASET_RELEASE"], "scope_type": "DATASET", "scope_id": "ds-2"},
	{"perms": ["DATASET_RELEASE"], "scope_type": "DATASET", "scope_id": "ds-3"},
])}

mock_send_and_disjoint(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASET_UPDATE"], "scope_type": "DATASET", "scope_id": "ds-1"},
	{"perms": ["DATASET_RELEASE"], "scope_type": "DATASET", "scope_id": "ds-2"},
])}

# --- Tenant scope inheritance mocks ---
mock_send_tenant_dataset_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "TENANT", "tenant-1")}
mock_send_tenant_datasource_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "TENANT", "tenant-1")}
mock_send_tenant_datastructure_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASTRUCTURE_READ"], "TENANT", "tenant-1")}
mock_send_dataset_scoped_user_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["USER_READ"], "DATASET", "dataset-1")}
mock_send_and_mixed_scopes(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASET_UPDATE"], "scope_type": "TENANT", "scope_id": "tenant-1"},
	{"perms": ["DATASET_RELEASE"], "scope_type": "DATASET", "scope_id": "ds-1"},
])}

mock_send_tenant_both_and_perms(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_UPDATE", "DATASET_RELEASE"], "TENANT", "tenant-1")}

# --- Unscoped SYSTEM role mocks ---
mock_send_unscoped_admin(_) := {"status_code": 200, "body": mock_http.user_with_unscoped_permissions(["USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE"])}
mock_send_unscoped_dataset_read(_) := {"status_code": 200, "body": mock_http.user_with_unscoped_permissions(["DATASET_READ"])}
mock_send_unscoped_and_data(_) := {"status_code": 200, "body": {
	"userId": "test-user-123",
	"externalId": "test-user-123",
	"groups": [
		{
			"id": "group-sys",
			"name": "System Group",
			"assignments": [{
				"roleId": "role-sys",
				"roleName": "Tenant Admin",
				"roleType": "SYSTEM",
				"scopeType": null,
				"scopeId": null,
				"permissions": ["USER_READ", "USER_CREATE"],
			}],
		},
		{
			"id": "group-data",
			"name": "Data Group",
			"assignments": [{
				"roleId": "role-data",
				"roleName": "Data Owner",
				"roleType": "DATA",
				"scopeType": "TENANT",
				"scopeId": null,
				"permissions": ["DATASET_READ", "DATASET_CREATE"],
			}],
		},
	],
}}

# =============================================================================
# PERMISSION-BASED ACCESS TESTS
# =============================================================================

# Test: Authenticated user with correct permission is allowed
test_permission_granted if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"USER_READ"}
}

# Test: Authenticated user without required permission is denied
test_permission_denied if {
	result := authz.decision with http.send as mock_send_reader
		with data.config as mock_http.mock_config
		with input as portal_request("DELETE", "/v1/users/123")
	result.allow == false
	result.reason == "permission_denied"
	result.required_permissions == {"USER_DELETE"}
}

# Test: POST to collection requires CREATE permission
test_create_permission if {
	result := authz.decision with http.send as mock_send_dataset_creator
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_CREATE"}
}

# Test: PUT to resource requires UPDATE permission
test_update_permission if {
	result := authz.decision with http.send as mock_send_datasource_updater
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasources/abc-123")
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
		with input as portal_request("GET", "/v1/users/me")
	result.allow == true
	result.reason == "authenticated_endpoint"
}

# Test: /users/me denied for unauthenticated request (no X-Userinfo header)
test_users_me_denied_without_auth if {
	result := authz.decision with input as portal_request_no_auth("GET", "/v1/users/me")
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
		with input as portal_request("GET", "/v1/datasets")
	result.allow == false
	result.reason == "missing_user_context"
}

# Test: Missing X-Userinfo header results in denial (AuthZ Repository not called)
test_missing_userinfo_header_denied if {
	result := authz.decision with input as portal_request_no_auth("GET", "/v1/users")
	result.allow == false
	result.reason == "missing_user_context"
}

# Test: User context with null userId/externalId results in denial
test_null_user_ids_denied if {
	result := authz.decision with http.send as mock_send_null_ids
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result.allow == false
	result.reason == "missing_user_context"
}

# Test: Malformed user context (missing groups) still allows null-permission endpoints
test_malformed_user_context_allows_null_permission if {
	result := authz.decision with http.send as mock_send_authenticated_no_groups_field
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users/me")
	result.allow == true
	result.reason == "authenticated_endpoint"
}

# Test: Known path but unsupported HTTP method is denied (e.g., POST /v1/permissions — only GET defined)
# Path matches but method has no permission mapping → is_known_endpoint=false → "unknown_endpoint"
test_unsupported_method_on_known_path_denied if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/permissions")
	result.allow == false
	result.reason == "unknown_endpoint"
}

# Test: DELETE on permissions endpoint (only GET defined) is also denied
test_delete_on_readonly_endpoint_denied if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("DELETE", "/v1/permissions/perm-123")
	result.allow == false
	result.reason == "unknown_endpoint"
}

# Test: Unknown endpoint path results in denial
test_unknown_endpoint_denied if {
	result := authz.decision with http.send as mock_send_all_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/unknown-resource")
	result.allow == false
	result.reason == "unknown_endpoint"
}

# Test: Missing service metadata results in denial
test_unknown_backend_denied if {
	result := authz.decision with http.send as mock_send_reader
		with data.config as mock_http.mock_config
		with input as {"request": {
			"method": "GET",
			"path": "/v1/users",
			"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")},
		}}
	result.allow == false
	result.reason == "unknown_backend"
}

# Test: http.send error results in denial (fail-secure)
test_http_send_error_denied if {
	result := authz.decision with http.send as mock_send_error
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
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

# Test: User with correct DATASOURCE scope can access that datasource
test_scope_datasource_correct if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources/ds-123")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: User with wrong DATASOURCE scope is denied
test_scope_datasource_wrong if {
	result := authz.decision with http.send as mock_send_wrong_scope
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources/ds-123")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: User with correct DATASET scope can access that dataset
test_scope_dataset_correct if {
	result := authz.decision with http.send as mock_send_dataset_abc_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/dataset-abc")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: User with DATASET scope for one dataset cannot access another
test_scope_dataset_wrong if {
	result := authz.decision with http.send as mock_send_dataset_abc_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/other-dataset")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: TENANT-scoped user can access tenant-level resources (users)
test_scope_tenant_user_access if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users/user-123")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: Collection endpoints work with matching scope type (Q-006: fail-secure)
test_scope_collection_matching_scope if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources")
	result.allow == true
	result.reason == "permission_granted"
}

# =============================================================================
# SCOPE HEADER TESTS (M5.5 - Collection Endpoint Filtering)
# =============================================================================
# These tests verify that the decision includes X-Allowed-Scope-Ids header
# for backend collection filtering.

# Test: TENANT scope returns wildcard "*" header
test_scope_header_tenant_wildcard if {
	result := authz.decision with http.send as mock_send_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "*"
}

# Test: Multiple DATASOURCE scopes return comma-separated sorted IDs
test_scope_header_multiple_datasources if {
	result := authz.decision with http.send as mock_send_multi_datasource
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources")
	result.allow == true

	# IDs are sorted alphabetically: ds-aaa, ds-zzz
	result.headers["X-Allowed-Scope-Ids"] == "ds-aaa,ds-zzz"
}

# Test: TENANT scope takes priority over DATASOURCE (returns wildcard)
test_scope_header_tenant_priority if {
	result := authz.decision with http.send as mock_send_tenant_and_datasource
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "*"
}

# Test: Scopes collected across multiple groups
test_scope_header_multiple_groups if {
	result := authz.decision with http.send as mock_send_multi_groups_datasource
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources")
	result.allow == true

	# IDs from both groups, sorted
	result.headers["X-Allowed-Scope-Ids"] == "ds-from-group1,ds-from-group2"
}

# Test: Single DATASOURCE scope returns single ID
test_scope_header_single_datasource if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "ds-123"
}

# Test: Resource endpoint (not collection) still includes header
test_scope_header_resource_endpoint if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources/ds-123")
	result.allow == true

	# Resource endpoint allowed (scope matches), header still present
	result.headers["X-Allowed-Scope-Ids"] == "ds-123"
}

# Test: DATASET endpoint with DATASET scope returns DATASET IDs
test_scope_header_dataset_endpoint if {
	result := authz.decision with http.send as mock_send_dataset_scope
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "dataset-123"
}

# Test: Q-006 fail-secure — scope type mismatch on collection endpoint denies access
# User has DATASET scope with DATASOURCE_READ permission, but accessing /v1/datasources
# which expects DATASOURCE scope type. Neither TENANT nor DATASOURCE match → denied.
test_scope_header_wrong_scope_type if {
	result := authz.decision with http.send as mock_send_dataset_scope_with_datasource_perm
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources")

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
		with input as portal_request("GET", "/v1/users")
	result.headers != null
	object.keys(result.headers) == {"X-Allowed-Scope-Ids"}
}

# =============================================================================
# AND-PERMISSION SCOPE HEADER TESTS
# =============================================================================
# Tests that AND-permissions interact correctly with scope header generation.
# TENANT wildcard and specific scope IDs must satisfy ALL required permissions.

# Test: AND-permission with TENANT scope returns wildcard header
# User has both TENANT and DATASET scoped assignments with both AND-permissions.
# Permission check passes via DATASET scope (matching resource), but scope header
# shows "*" because has_tenant_scope finds all required perms at TENANT scope.
test_scope_header_and_tenant_wildcard if {
	result := authz.decision with http.send as mock_send_tenant_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/abc/released/meta")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "*"
}

# Test: AND-permission with TENANT scope but missing one permission is denied
test_scope_header_and_tenant_missing_one if {
	result := authz.decision with http.send as mock_send_tenant_missing_one
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/abc/released/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: AND-permission with specific scope - both permissions present
test_scope_header_and_specific_both if {
	result := authz.decision with http.send as mock_send_specific_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/dataset-abc/released/meta")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "dataset-abc"
}

# Test: AND-permission with specific scope - partial permissions denied
test_scope_header_and_specific_partial if {
	result := authz.decision with http.send as mock_send_specific_partial
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/dataset-abc/released/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# =============================================================================
# AND-PERMISSION CROSS-GROUP TESTS
# =============================================================================
# Tests that AND-permissions work when required permissions come from different
# groups/assignments, and that scope intersection is correct.

# Test: AND-permission satisfied across different groups for same scope
test_and_cross_group_allowed if {
	result := authz.decision with http.send as mock_send_and_cross_group
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/dataset-abc/released/meta")
	result.allow == true
	result.reason == "permission_granted"
	result.headers["X-Allowed-Scope-Ids"] == "dataset-abc"
}

# Test: AND-permission scope intersection — only ds-2 has both permissions
test_and_scope_intersection if {
	result := authz.decision with http.send as mock_send_and_partial_overlap
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/ds-2/released/meta")
	result.allow == true
	result.reason == "permission_granted"
	result.headers["X-Allowed-Scope-Ids"] == "ds-2"
}

# Test: AND-permission scope intersection — ds-1 only has UPDATE, not RELEASE
test_and_scope_intersection_denied_missing_release if {
	result := authz.decision with http.send as mock_send_and_partial_overlap
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/ds-1/released/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: AND-permission scope intersection — ds-3 only has RELEASE, not UPDATE
test_and_scope_intersection_denied_missing_update if {
	result := authz.decision with http.send as mock_send_and_partial_overlap
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/ds-3/released/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: AND-permission with completely disjoint scopes — denied everywhere
test_and_disjoint_scopes_denied if {
	result := authz.decision with http.send as mock_send_and_disjoint
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/ds-1/released/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# =============================================================================
# NEW ENDPOINT INTEGRATION TESTS
# =============================================================================
# End-to-end tests for stage, unstage, release, unrelease endpoints.

# Test: POST /datasets/{id}/unstage requires only DATASET_UPDATE
test_unstage_allowed if {
	result := authz.decision with http.send as mock_send_specific_partial
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets/dataset-abc/unstage")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE"}
}

# Test: POST /datasets/{id}/stage requires DATASET_UPDATE
test_stage_single_perm if {
	result := authz.decision with http.send as mock_send_specific_partial
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets/dataset-abc/stage")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE"}
}

# Test: POST /datasets/{id}/release requires DATASET_RELEASE
test_release_allowed if {
	result := authz.decision with http.send as mock_send_specific_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets/dataset-abc/release")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_RELEASE"}
}

# Test: POST /datasets/{id}/unrelease requires DATASET_RELEASE
test_unrelease_allowed if {
	result := authz.decision with http.send as mock_send_specific_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets/dataset-abc/unrelease")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_RELEASE"}
}

# Test: PUT /datasets/{id}/released/meta requires AND-permission
test_released_meta_and_perm if {
	result := authz.decision with http.send as mock_send_specific_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/dataset-abc/released/meta")
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

# Test: TENANT-scoped DATASET_READ grants access to a specific dataset resource
# (TENANT cascades to DATASET resource endpoints per ADM spec)
test_tenant_scope_cascades_to_dataset_resource if {
	result := authz.decision with http.send as mock_send_tenant_dataset_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/some-dataset-id")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: TENANT-scoped DATASET_READ DOES grant access to dataset collection
# (collection endpoints allow TENANT scope — list filtering via header)
test_tenant_scope_allows_dataset_collection if {
	result := authz.decision with http.send as mock_send_tenant_dataset_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: TENANT-scoped DATASOURCE_READ grants access to a specific datasource resource
test_tenant_scope_cascades_to_datasource_resource if {
	result := authz.decision with http.send as mock_send_tenant_datasource_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources/some-datasource-id")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: TENANT-scoped DATASTRUCTURE_READ grants access to a specific datastructure resource
test_tenant_scope_cascades_to_datastructure_resource if {
	result := authz.decision with http.send as mock_send_tenant_datastructure_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datastructures/some-ds-id")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: No upward inheritance — DATASET-scoped USER_READ does NOT grant access
# to tenant-level resource endpoints. Inheritance is downward only.
test_no_upward_inheritance if {
	result := authz.decision with http.send as mock_send_dataset_scoped_user_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users/user-123")
	result.allow == false
	result.reason == "permission_denied"
}

# Test: AND-permission with TENANT UPDATE + DATASET RELEASE → allowed
# TENANT-scoped DATASET_UPDATE inherits to resource endpoint, DATASET RELEASE
# matches directly. Both permissions satisfied.
test_and_mixed_scopes_allowed if {
	result := authz.decision with http.send as mock_send_and_mixed_scopes
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/ds-1/released/meta")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: AND-permission with both permissions at TENANT scope → allowed
# TENANT inheritance satisfies both UPDATE and RELEASE for resource endpoint
test_tenant_and_permission_both_tenant if {
	result := authz.decision with http.send as mock_send_tenant_both_and_perms
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/abc/released/meta")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: AND-permission with only UPDATE at TENANT, no RELEASE anywhere → denied
test_tenant_and_permission_one_missing if {
	result := authz.decision with http.send as mock_send_tenant_missing_one
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/abc/released/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# =============================================================================
# UNSCOPED SYSTEM ROLE TESTS
# =============================================================================
# SYSTEM roles have scopeType=null (unscoped) but grant tenant-wide access.
# These tests verify that unscoped assignments are evaluated correctly.

# Test: Unscoped SYSTEM role grants access to collection endpoints
test_unscoped_system_role_collection if {
	result := authz.decision with http.send as mock_send_unscoped_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: Unscoped SYSTEM role grants access to resource endpoints
test_unscoped_system_role_resource if {
	result := authz.decision with http.send as mock_send_unscoped_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users/abc-123")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: Unscoped SYSTEM role does not produce a scope header
# SYSTEM roles operate on TENANT-scoped resources (users, groups, roles) which
# don't use scope filtering, so no header is set.
test_unscoped_system_role_no_scope_header if {
	result := authz.decision with http.send as mock_send_unscoped_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
	not result.headers
}

# Test: Mixed groups — unscoped SYSTEM + scoped DATA — both work
# SYSTEM role handles /v1/users, DATA role handles /v1/datasets
test_mixed_unscoped_and_scoped_system_endpoint if {
	result := authz.decision with http.send as mock_send_unscoped_and_data
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: Mixed user accessing unscoped endpoint omits scope header
test_mixed_unscoped_and_scoped_system_endpoint_no_header if {
	result := authz.decision with http.send as mock_send_unscoped_and_data
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
	not result.headers
}

test_mixed_unscoped_and_scoped_data_endpoint if {
	result := authz.decision with http.send as mock_send_unscoped_and_data
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: Unscoped SYSTEM role cascades to non-TENANT resource endpoints (same as TENANT inheritance)
test_unscoped_system_role_cascades_to_dataset_resource if {
	result := authz.decision with http.send as mock_send_unscoped_dataset_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/some-dataset-id")
	result.allow == true
	result.reason == "permission_granted"
}

# Test: Unscoped SYSTEM role denied for permission it doesn't have
test_unscoped_system_role_wrong_permission if {
	result := authz.decision with http.send as mock_send_unscoped_admin
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result.allow == false
	result.reason == "permission_denied"
}
