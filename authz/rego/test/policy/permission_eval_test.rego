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

# Helper: user with permissions scoped to dataset-1 (used throughout this file)
dataset_user(perms) := mock_http.user_with_scoped_permissions(perms, "DATASET", "dataset-1")

mock_send_read_dataset(_) := {"status_code": 200, "body": dataset_user(["DATASET_READ", "DATASET_CREATE"])}
mock_send_read_only(_) := {"status_code": 200, "body": dataset_user(["DATASET_READ"])}
mock_send_no_perms(_) := {"status_code": 200, "body": dataset_user([])}
mock_send_no_groups(_) := {"status_code": 200, "body": mock_http.user_no_permissions}
mock_send_user_id_only(_) := {"status_code": 200, "body": {"userId": "123"}}
mock_send_external_id_only(_) := {"status_code": 200, "body": {"externalId": "ext-123"}}
mock_send_empty(_) := {"status_code": 200, "body": {}}
mock_send_multiple_groups(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASET_READ"], "scope_type": "DATASET", "scope_id": "dataset-1"},
	{"perms": ["DATASET_CREATE"], "scope_type": "DATASET", "scope_id": "dataset-2"},
])}

mock_send_multi_role(_) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["USER_READ", "USER_CREATE"], "scope_type": "TENANT", "scope_id": "t-1"},
	{"perms": ["DATASET_READ"], "scope_type": "DATASPACE", "scope_id": "ds-1"},
])}

# Mock for AND-permission tests
mock_send_and_both(_) := {"status_code": 200, "body": dataset_user(["DATASET_UPDATE", "DATASET_RELEASE"])}
mock_send_and_missing_one(_) := {"status_code": 200, "body": dataset_user(["DATASET_UPDATE"])}

# =============================================================================
# PERMISSION LOOKUP TESTS
# =============================================================================

# Test: Permission lookup from mappings - GET collection
test_required_permissions_read_user if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("GET", "/v1/users")
	result == {"USER_READ"}
}

# Test: Permission lookup from mappings - POST collection
test_required_permissions_create_dataset if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("POST", "/v1/datasets")
	result == {"DATASET_CREATE"}
}

# Test: Permission lookup from mappings - PUT resource
test_required_permissions_update_datasource if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("PUT", "/v1/datasources/123")
	result == {"DATASOURCE_UPDATE"}
}

# Test: Permission lookup from mappings - DELETE resource
test_required_permissions_delete_group if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("DELETE", "/v1/groups/abc")
	result == {"GROUP_DELETE"}
}

# Test: a Mapping is addressed under its dataset, so its permissions are the dataset's. Each verb
# must require exactly the permission its effect implies — PUT versions an existing artifact and
# POST creates one, so PUT must not carry create rights.
test_required_permissions_read_dataset_mapping if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("GET", "/v1/datasets/dataset-1/mappings")
	result == {"DATASET_READ"}
}

test_required_permissions_create_dataset_mapping if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("POST", "/v1/datasets/dataset-1/mappings")
	result == {"DATASET_CREATE"}
}

test_required_permissions_version_dataset_mapping if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("PUT", "/v1/datasets/dataset-1/mappings")
	result == {"DATASET_UPDATE"}
}

test_required_permissions_delete_dataset_mapping if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("DELETE", "/v1/datasets/dataset-1/mappings")
	result == {"DATASET_DELETE"}
}

# Test: Permission lookup from mappings - PATCH resource
test_required_permissions_patch_role if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("PATCH", "/v1/roles/r1")
	result == {"ROLE_UPDATE"}
}

# Test: AND-permission array → multi-element set
test_required_permissions_and_array if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("PUT", "/v1/datasets/abc/released/meta")
	result == {"DATASET_UPDATE", "DATASET_RELEASE"}
}

# =============================================================================
# NULL-PERMISSION ENDPOINT TESTS
# =============================================================================

# Test: /users/me has null permission (no permission required)
test_users_me_null_permission if {
	result := permission_eval.is_null_permission_endpoint with input as portal_request_no_auth("GET", "/v1/users/me")
	result == true
}

# Test: /users/me required_permissions is empty set
test_users_me_no_required_permissions if {
	result := permission_eval.required_permissions with input as portal_request_no_auth("GET", "/v1/users/me")
	count(result) == 0
}

# Test: Regular endpoint is not null-permission
test_regular_endpoint_not_null_permission if {
	result := permission_eval.is_null_permission_endpoint with input as portal_request_no_auth("GET", "/v1/users")
	result == false
}

# =============================================================================
# KNOWN ENDPOINT TESTS (fail-secure: unknown endpoints denied)
# =============================================================================

# Test: Endpoint with permission is known
test_is_known_endpoint_with_permission if {
	result := permission_eval.is_known_endpoint with input as portal_request_no_auth("GET", "/v1/users")
	result == true
}

# Test: Null-permission endpoint is known
test_is_known_endpoint_null_permission if {
	result := permission_eval.is_known_endpoint with input as portal_request_no_auth("GET", "/v1/users/me")
	result == true
}

# Test: Unknown endpoint is not known (fail-secure)
test_is_known_endpoint_unknown if {
	result := permission_eval.is_known_endpoint with input as portal_request_no_auth("GET", "/v1/foobar")
	result == false
}

# Test: AND-permission endpoint is known
test_is_known_endpoint_and_permission if {
	result := permission_eval.is_known_endpoint with input as portal_request_no_auth("PUT", "/v1/datasets/abc/released/meta")
	result == true
}

# =============================================================================
# PERMISSION EVALUATION TESTS
# =============================================================================

# Test: User with matching permission
test_has_permission_matching if {
	result := permission_eval.has_permission with http.send as mock_send_read_dataset
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result == true
}

# Test: User without matching permission
test_has_permission_not_matching if {
	result := permission_eval.has_permission with http.send as mock_send_read_only
		with data.config as mock_http.mock_config
		with input as portal_request("DELETE", "/v1/datasets/123")
	result == false
}

# Test: User with no permissions
test_has_permission_empty if {
	result := permission_eval.has_permission with http.send as mock_send_no_perms
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
	result == false
}

# Test: User with permission in different group
test_has_permission_multiple_groups if {
	result := permission_eval.has_permission with http.send as mock_send_multiple_groups
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets")
	result == true
}

# Test: /users/me allowed for authenticated user without explicit permission
test_users_me_allowed_authenticated if {
	result := permission_eval.has_permission with http.send as mock_send_no_groups
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users/me")
	result == true
}

# Test: User with no groups
test_has_permission_no_groups if {
	result := permission_eval.has_permission with http.send as mock_send_no_groups
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
	result == false
}

# Test: AND-permission - user has both required permissions
test_has_permission_and_both_present if {
	result := permission_eval.has_permission with http.send as mock_send_and_both
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/dataset-1/released/meta")
	result == true
}

# Test: AND-permission - user missing one of required permissions
test_has_permission_and_missing_one if {
	result := permission_eval.has_permission with http.send as mock_send_and_missing_one
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/dataset-1/released/meta")
	result == false
}

# =============================================================================
# PERMISSION COLLECTION TESTS
# =============================================================================

test_all_user_permissions if {
	result := permission_eval.all_user_permissions with http.send as mock_send_multi_role
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users")
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

# =============================================================================
# SCOPE INHERITANCE TESTS
# =============================================================================

mock_send_tenant_dataset_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "TENANT", "tenant-1")}

# Test: TENANT scope cascades to DATASET resource endpoint (direct unit test of inheritance rule)
test_user_has_permission_tenant_cascades_to_dataset if {
	result := permission_eval.has_permission with http.send as mock_send_tenant_dataset_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/some-dataset-id")
	result == true
}

# =============================================================================
# DATAPOOL SCOPE ENFORCEMENT TESTS
# =============================================================================

# DATAPOOL-scoped user, granted on pool-1
mock_send_datapool_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATAPOOL_READ"], "DATAPOOL", "pool-1")}

# TENANT-scoped user with a datapool permission
mock_send_tenant_datapool_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATAPOOL_READ"], "TENANT", "tenant-1")}
mock_send_tenant_datapool_create(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATAPOOL_CREATE"], "TENANT", "tenant-1")}

# DATAPOOL-scoped access to the SAME pool is allowed (scopeId matches resource_id)
test_datapool_scoped_allows_matching_pool if {
	result := permission_eval.has_permission with http.send as mock_send_datapool_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datapools/pool-1")
	result == true
}

# DATAPOOL-scoped access to a DIFFERENT pool is denied (scopeId mismatch — fail-secure)
test_datapool_scoped_denies_other_pool if {
	result := permission_eval.has_permission with http.send as mock_send_datapool_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datapools/pool-2")
	result == false
}

# DATAPOOL-scoped read also covers the pool's sub-resource (/{id}/assignments)
test_datapool_scoped_allows_assignments_subresource if {
	result := permission_eval.has_permission with http.send as mock_send_datapool_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datapools/pool-1/assignments")
	result == true
}

# TENANT scope cascades down to a DATAPOOL resource endpoint (inheritance)
test_tenant_cascades_to_datapool if {
	result := permission_eval.has_permission with http.send as mock_send_tenant_datapool_read
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datapools/pool-1")
	result == true
}

# Collection create on /v1/datapools is allowed via TENANT-scoped DATAPOOL_CREATE
test_tenant_allows_datapool_collection_create if {
	result := permission_eval.has_permission with http.send as mock_send_tenant_datapool_create
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datapools")
	result == true
}

# A DATAPOOL/TENANT user with DATAPOOL_UPDATE
mock_send_datapool_update(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATAPOOL_UPDATE"], "DATAPOOL", "pool-1")}
mock_send_tenant_datapool_update(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATAPOOL_UPDATE"], "TENANT", "tenant-1")}

# Scenario 2 regression: a DATAPOOL-scoped user can UPDATE their pool (PATCH /datapools/{id}).
# In develop the "datapools" resource_scope_type mapping was MISSING → expected_scope_type was
# undefined → the resource scope rules (incl. the TENANT/unscoped cascade, which require
# expected_scope_type != "TENANT") never fired → has_permission=false → OPA 403. With the
# mapping ("datapools":"DATAPOOL") the DATAPOOL-scope match grants the update.
test_datapool_scoped_allows_update_matching_pool if {
	result := permission_eval.has_permission with http.send as mock_send_datapool_update
		with data.config as mock_http.mock_config
		with input as portal_request("PATCH", "/v1/datapools/pool-1")
	result == true
}

# Scenario 2 regression: TENANT cascade also grants the datapool UPDATE (was 403 in develop).
test_tenant_cascades_to_datapool_update if {
	result := permission_eval.has_permission with http.send as mock_send_tenant_datapool_update
		with data.config as mock_http.mock_config
		with input as portal_request("PATCH", "/v1/datapools/pool-1")
	result == true
}

# =============================================================================
# DATAPOOL → DATASET UNION INHERITANCE TESTS (Epic 1)
# =============================================================================
# User holds DATASET_READ via a DATAPOOL-scoped grant on pool-1.
# Two http.send targets are mocked by URL: the user-context fetch and the
# dataset→pool membership lookup.

# Membership says the requested dataset is in pool-1 (the user's granted pool)
mock_union_in_pool(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

mock_union_in_pool(req) := {"status_code": 200, "body": {"poolId": "pool-1"}} if {
	contains(req.url, "dataset-pool")
}

# Membership says the requested dataset is in a DIFFERENT pool (pool-2)
mock_union_other_pool(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

mock_union_other_pool(req) := {"status_code": 200, "body": {"poolId": "pool-2"}} if {
	contains(req.url, "dataset-pool")
}

# Single dataset in the user's granted pool → access granted via union
test_union_allows_dataset_in_granted_pool if {
	result := permission_eval.has_permission with http.send as mock_union_in_pool
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/ds-99")
	result == true
}

# Single dataset in a different pool → denied (fail-secure)
test_union_denies_dataset_in_other_pool if {
	result := permission_eval.has_permission with http.send as mock_union_other_pool
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/ds-99")
	result == false
}

# Dataset collection: a DATAPOOL grant grants list access (filtering via header)
test_union_allows_dataset_collection if {
	result := permission_eval.has_permission with http.send as mock_union_in_pool
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result == true
}

# No pool lookup is needed/used for users without DATAPOOL grants:
# a direct DATASET grant still works and never depends on the membership service.
test_union_does_not_break_direct_dataset_grant if {
	result := permission_eval.has_permission with http.send as mock_send_read_dataset
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/dataset-1")
	result == true
}
