# Tests for permission_eval.rego - Permission lookup and evaluation
#
# Tests mock http.send() using OPA's `with http.send as mock_fn` syntax.

package civitas.authz.permission_eval_test

import rego.v1

import data.civitas.authz.permission_eval
import data.test.helpers.mock_http

# =============================================================================
# TEST HELPERS
# =============================================================================

portal_request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")},
	},
	"service": {"name": "portal-backend"},
}

# Request without userinfo header (for testing lookups only)
portal_request_no_auth(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {},
	},
	"service": {"name": "portal-backend"},
}

user_with_permissions(perms) := {
	"userId": "user-1",
	"externalId": "ext-1",
	"groups": [{
		"id": "group-1",
		"name": "Test Group",
		"assignments": [{
			"roleId": "role-1",
			"roleName": "Test Role",
			"roleType": "DATA",
			"scopeType": "DATASET",
			"scopeId": "dataset-1",
			"permissions": perms,
		}],
	}],
}

# Mock http.send functions
mock_send_user_with_perms(perms) := func if {
	func := {"status_code": 200, "body": user_with_permissions(perms)}
}

mock_send_read_dataset(_) := {"status_code": 200, "body": user_with_permissions(["DATASET_READ", "DATASET_CREATE"])}
mock_send_read_only(_) := {"status_code": 200, "body": user_with_permissions(["DATASET_READ"])}
mock_send_no_perms(_) := {"status_code": 200, "body": user_with_permissions([])}
mock_send_no_groups(_) := {"status_code": 200, "body": {"userId": "user-1", "externalId": "ext-1", "groups": []}}
mock_send_user_id_only(_) := {"status_code": 200, "body": {"userId": "123"}}
mock_send_external_id_only(_) := {"status_code": 200, "body": {"externalId": "ext-123"}}
mock_send_empty(_) := {"status_code": 200, "body": {}}

# Mock for multiple groups test
mock_send_multiple_groups(_) := {"status_code": 200, "body": {
	"userId": "user-1",
	"externalId": "ext-1",
	"groups": [
		{
			"id": "group-1",
			"name": "Group 1",
			"assignments": [{
				"roleId": "role-1",
				"roleName": "Reader",
				"roleType": "DATA",
				"scopeType": "DATASET",
				"scopeId": "dataset-1",
				"permissions": ["DATASET_READ"],
			}],
		},
		{
			"id": "group-2",
			"name": "Group 2",
			"assignments": [{
				"roleId": "role-2",
				"roleName": "Creator",
				"roleType": "DATA",
				"scopeType": "DATASET",
				"scopeId": "dataset-2",
				"permissions": ["DATASET_CREATE"],
			}],
		},
	],
}}

# Mock for all_user_permissions test
mock_send_multi_role(_) := {"status_code": 200, "body": {
	"userId": "user-1",
	"externalId": "ext-1",
	"groups": [{
		"id": "group-1",
		"name": "Test",
		"assignments": [
			{
				"roleId": "role-1",
				"roleName": "Role1",
				"roleType": "DATA",
				"scopeType": "TENANT",
				"scopeId": "t-1",
				"permissions": ["USER_READ", "USER_CREATE"],
			},
			{
				"roleId": "role-2",
				"roleName": "Role2",
				"roleType": "DATA",
				"scopeType": "DATASPACE",
				"scopeId": "ds-1",
				"permissions": ["DATASET_READ"],
			},
		],
	}],
}}

# Mock for AND-permission tests
mock_send_and_both(_) := {"status_code": 200, "body": user_with_permissions(["DATASET_UPDATE", "DATASET_RELEASE"])}
mock_send_and_missing_one(_) := {"status_code": 200, "body": user_with_permissions(["DATASET_UPDATE"])}

# =============================================================================
# PERMISSION LOOKUP TESTS
# =============================================================================

# Test: Permission lookup from mappings - GET collection
test_required_permissions_read_user if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("GET", "/v2/users")
	result == {"USER_READ"}
}

# Test: Permission lookup from mappings - POST collection
test_required_permissions_create_dataset if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("POST", "/v2/datasets")
	result == {"DATASET_CREATE"}
}

# Test: Permission lookup from mappings - PUT resource
test_required_permissions_update_datasource if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("PUT", "/v2/datasources/123")
	result == {"DATASOURCE_UPDATE"}
}

# Test: Permission lookup from mappings - DELETE resource
test_required_permissions_delete_group if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("DELETE", "/v2/groups/abc")
	result == {"GROUP_DELETE"}
}

# Test: Permission lookup from mappings - PATCH resource
test_required_permissions_patch_role if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("PATCH", "/v2/roles/r1")
	result == {"ROLE_UPDATE"}
}

# Test: AND-permission array → multi-element set
test_required_permissions_and_array if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("PUT", "/v2/datasets/abc/published/meta")
	result == {"DATASET_UPDATE", "DATASET_RELEASE"}
}

# =============================================================================
# NULL-PERMISSION ENDPOINT TESTS
# =============================================================================

# Test: /users/me has null permission (no permission required)
test_users_me_null_permission if {
	result := permission_eval.is_null_permission_endpoint with input as portal_request_no_auth("GET", "/v2/users/me")
	result == true
}

# Test: /users/me required_permissions is empty set
test_users_me_no_required_permissions if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("GET", "/v2/users/me")
	count(result) == 0
}

# Test: Regular endpoint is not null-permission
test_regular_endpoint_not_null_permission if {
	result := permission_eval.is_null_permission_endpoint with input as portal_request_no_auth("GET", "/v2/users")
	result == false
}

# =============================================================================
# KNOWN ENDPOINT TESTS (fail-secure: unknown endpoints denied)
# =============================================================================

# Test: Endpoint with permission is known
test_is_known_endpoint_with_permission if {
	result := permission_eval.is_known_endpoint with input as portal_request_no_auth("GET", "/v2/users")
	result == true
}

# Test: Null-permission endpoint is known
test_is_known_endpoint_null_permission if {
	result := permission_eval.is_known_endpoint with input as portal_request_no_auth("GET", "/v2/users/me")
	result == true
}

# Test: Unknown endpoint is not known (fail-secure)
test_is_known_endpoint_unknown if {
	result := permission_eval.is_known_endpoint with input as portal_request_no_auth("GET", "/v2/foobar")
	result == false
}

# Test: AND-permission endpoint is known
test_is_known_endpoint_and_permission if {
	result := permission_eval.is_known_endpoint with input as portal_request_no_auth("PUT", "/v2/datasets/abc/published/meta")
	result == true
}

# =============================================================================
# PERMISSION EVALUATION TESTS
# =============================================================================

# Test: User with matching permission
test_has_permission_matching if {
	result := permission_eval.has_permission with http.send as mock_send_read_dataset
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/datasets")
	result == true
}

# Test: User without matching permission
test_has_permission_not_matching if {
	result := permission_eval.has_permission with http.send as mock_send_read_only
		with data.config as mock_http.mock_config
		with input as portal_request("DELETE", "/v2/datasets/123")
	result == false
}

# Test: User with no permissions
test_has_permission_empty if {
	result := permission_eval.has_permission with http.send as mock_send_no_perms
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users")
	result == false
}

# Test: User with permission in different group
test_has_permission_multiple_groups if {
	result := permission_eval.has_permission with http.send as mock_send_multiple_groups
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v2/datasets")
	result == true
}

# Test: /users/me allowed for authenticated user without explicit permission
test_users_me_allowed_authenticated if {
	result := permission_eval.has_permission with http.send as mock_send_no_groups
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users/me")
	result == true
}

# Test: User with no groups
test_has_permission_no_groups if {
	result := permission_eval.has_permission with http.send as mock_send_no_groups
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users")
	result == false
}

# Test: AND-permission - user has both required permissions
test_has_permission_and_both_present if {
	result := permission_eval.has_permission with http.send as mock_send_and_both
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/dataset-1/published/meta")
	result == true
}

# Test: AND-permission - user missing one of required permissions
test_has_permission_and_missing_one if {
	result := permission_eval.has_permission with http.send as mock_send_and_missing_one
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v2/datasets/dataset-1/published/meta")
	result == false
}

# =============================================================================
# PERMISSION COLLECTION TESTS
# =============================================================================

test_all_user_permissions if {
	result := permission_eval.all_user_permissions with http.send as mock_send_multi_role
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v2/users")
	result == {"USER_READ", "USER_CREATE", "DATASET_READ"}
}

# =============================================================================
# AUTHENTICATION TESTS
# =============================================================================

test_is_authenticated_with_user_id if {
	result := permission_eval.is_authenticated with http.send as mock_send_user_id_only
		with data.config as mock_http.mock_config
		with input as {"request": {"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")}}}
	result == true
}

test_is_authenticated_with_external_id if {
	result := permission_eval.is_authenticated with http.send as mock_send_external_id_only
		with data.config as mock_http.mock_config
		with input as {"request": {"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")}}}
	result == true
}

test_is_not_authenticated_empty_context if {
	result := permission_eval.is_authenticated with http.send as mock_send_empty
		with data.config as mock_http.mock_config
		with input as {"request": {"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")}}}
	result == false
}
