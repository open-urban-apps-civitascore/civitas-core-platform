# Tests for providers/portal_backend.rego - Portal Backend Provider

package civitas.authz.providers.portal_backend_test

import rego.v1

import data.civitas.authz.providers.portal_backend

# =============================================================================
# TEST HELPERS
# =============================================================================

portal_request(method, path) := {"request": {
	"method": method,
	"path": path,
	"headers": {},
}}

# =============================================================================
# ENDPOINT CONFIGURATION TESTS
# =============================================================================

test_endpoints_loaded if {
	# Verify endpoints are loaded from data file
	portal_backend.endpoints["/v1/users"]
}

test_endpoints_contains_users_id if {
	portal_backend.endpoints["/v1/users/{id}"]
}

test_endpoints_contains_users_me if {
	portal_backend.endpoints["/v1/users/me"]
}

# =============================================================================
# PATH PATTERN MATCHING TESTS
# =============================================================================

test_path_pattern_collection if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/users")
	result == "/v1/users"
}

test_path_pattern_with_id if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/users/abc-123")
	result == "/v1/users/{id}"
}

test_path_pattern_special_me if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/users/me")
	result == "/v1/users/me"
}

test_path_pattern_unknown if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/unknown")
	result == ""
}

# =============================================================================
# SUB-RESOURCE PATH PATTERN TESTS
# =============================================================================

test_path_pattern_4_segment_markReady if {
	result := portal_backend.path_pattern with input as portal_request("POST", "/v1/datasets/abc-123/markReady")
	result == "/v1/datasets/{id}/markReady"
}

test_path_pattern_4_segment_assignments if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/datasets/abc-123/assignments")
	result == "/v1/datasets/{id}/assignments"
}

test_path_pattern_4_segment_pipelines if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/datasets/abc-123/pipelines")
	result == "/v1/datasets/{id}/pipelines"
}

test_path_pattern_datasource_release if {
	result := portal_backend.path_pattern with input as portal_request("POST", "/v1/datasources/ds-123/release")
	result == "/v1/datasources/{id}/release"
}

test_path_pattern_datastructure_assignments if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/datastructures/dstr-123/assignments")
	result == "/v1/datastructures/{id}/assignments"
}

# =============================================================================
# 5-SEGMENT PATH TESTS
# =============================================================================

test_path_pattern_5_segment_released_meta if {
	result := portal_backend.path_pattern with input as portal_request("PUT", "/v1/datasets/abc-123/released/meta")
	result == "/v1/datasets/{id}/released/meta"
}

test_path_pattern_5_segment_pipelines_id if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/datasets/abc-123/pipelines/pipe-456")
	result == "/v1/datasets/{id}/pipelines/{id}"
}

test_path_pattern_5_segment_versions_id if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v1/datastructures/dstr-123/versions/v-456")
	result == "/v1/datastructures/{id}/versions/{id}"
}

# =============================================================================
# 6-SEGMENT PATH TESTS
# =============================================================================

test_path_pattern_6_segment_versions_release if {
	result := portal_backend.path_pattern with input as portal_request("POST", "/v1/datastructures/dstr-123/versions/v-456/release")
	result == "/v1/datastructures/{id}/versions/{id}/release"
}

test_path_pattern_6_segment_versions_unrelease if {
	result := portal_backend.path_pattern with input as portal_request("POST", "/v1/datastructures/dstr-123/versions/v-456/unrelease")
	result == "/v1/datastructures/{id}/versions/{id}/unrelease"
}

test_path_pattern_7_segment_versions_released_meta if {
	result := portal_backend.path_pattern with input as portal_request("PUT", "/v1/datastructures/dstr-123/versions/v-456/released/meta")
	result == "/v1/datastructures/{id}/versions/{id}/released/meta"
}

# =============================================================================
# NEW RESOURCE ENDPOINT TESTS
# =============================================================================

test_endpoints_contains_datasources if {
	portal_backend.endpoints["/v1/datasources"]
}

test_endpoints_contains_datasources_id if {
	portal_backend.endpoints["/v1/datasources/{id}"]
}

test_endpoints_contains_datastructures if {
	portal_backend.endpoints["/v1/datastructures"]
}

test_endpoints_contains_datastructures_id if {
	portal_backend.endpoints["/v1/datastructures/{id}"]
}

# =============================================================================
# DATASPACES AND CATALOGS — removed from v2.0 (see #989)
# OPA should deny these as unknown_endpoint.
# =============================================================================

test_endpoints_does_not_contain_dataspaces if {
	not portal_backend.endpoints["/v1/dataspaces"]
}

test_endpoints_does_not_contain_dataspaces_id if {
	not portal_backend.endpoints["/v1/dataspaces/{id}"]
}

test_endpoints_does_not_contain_catalogs if {
	not portal_backend.endpoints["/v1/catalogs"]
}

test_endpoints_does_not_contain_catalogs_id if {
	not portal_backend.endpoints["/v1/catalogs/{id}"]
}

# =============================================================================
# SCOPE TYPE TESTS FOR NEW RESOURCES
# =============================================================================

test_scope_type_datasources if {
	result := portal_backend.expected_scope_type with input as portal_request("GET", "/v1/datasources")
	result == "DATASOURCE"
}

test_scope_type_datastructures if {
	result := portal_backend.expected_scope_type with input as portal_request("GET", "/v1/datastructures")
	result == "DATASTRUCTURE"
}

# =============================================================================
# SUB-RESOURCE SCOPE ENFORCEMENT TESTS
# =============================================================================

# Sub-resource resource_id should be the parent resource ID (parts[2])
test_resource_id_4_segment if {
	result := portal_backend.resource_id with input as portal_request("POST", "/v1/datasets/abc-123/markReady")
	result == "abc-123"
}

test_resource_id_5_segment if {
	result := portal_backend.resource_id with input as portal_request("DELETE", "/v1/datasets/abc-123/assignments/assign-456")
	result == "abc-123"
}

test_resource_id_6_segment if {
	result := portal_backend.resource_id with input as portal_request("POST", "/v1/datastructures/dstr-123/versions/v-456/release")
	result == "dstr-123"
}

# Sub-resource paths are resource endpoints (not collection)
test_is_resource_endpoint_4_segment if {
	portal_backend.is_resource_endpoint with input as portal_request("POST", "/v1/datasets/abc-123/markReady")
}

test_is_resource_endpoint_5_segment if {
	portal_backend.is_resource_endpoint with input as portal_request("DELETE", "/v1/datasets/abc-123/assignments/assign-456")
}

test_is_resource_endpoint_6_segment if {
	portal_backend.is_resource_endpoint with input as portal_request("POST", "/v1/datastructures/dstr-123/versions/v-456/release")
}

# =============================================================================
# COLLECTION ENDPOINT CLASSIFICATION TESTS (data-driven)
# =============================================================================

test_is_collection_endpoint if {
	portal_backend.is_collection_endpoint with input as portal_request("GET", "/v1/datasets")
}

test_not_collection_endpoint_resource if {
	not portal_backend.is_collection_endpoint with input as portal_request("GET", "/v1/datasets/abc-123")
}

test_not_collection_endpoint_sub_resource if {
	not portal_backend.is_collection_endpoint with input as portal_request("POST", "/v1/datasets/abc-123/markReady")
}

test_not_collection_endpoint_users_me if {
	not portal_backend.is_collection_endpoint with input as portal_request("GET", "/v1/users/me")
}
