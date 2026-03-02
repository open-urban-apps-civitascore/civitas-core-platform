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
	portal_backend.endpoints["/v2/users"]
}

test_endpoints_contains_users_id if {
	portal_backend.endpoints["/v2/users/{id}"]
}

test_endpoints_contains_users_me if {
	portal_backend.endpoints["/v2/users/me"]
}

# =============================================================================
# PATH PATTERN MATCHING TESTS
# =============================================================================

test_path_pattern_collection if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/users")
	result == "/v2/users"
}

test_path_pattern_with_id if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/users/abc-123")
	result == "/v2/users/{id}"
}

test_path_pattern_special_me if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/users/me")
	result == "/v2/users/me"
}

test_path_pattern_unknown if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/unknown")
	result == ""
}

# =============================================================================
# REQUEST ACCESSOR TESTS
# =============================================================================

test_request_method if {
	result := portal_backend.request_method with input as portal_request("POST", "/v2/users")
	result == "POST"
}

test_request_path_valid if {
	result := portal_backend.request_path with input as portal_request("GET", "/v2/users/123")
	result == "/v2/users/123"
}

test_request_path_invalid if {
	result := portal_backend.request_path with input as portal_request("GET", "/v2/users/../admin")
	result == ""
}

test_path_parts if {
	result := portal_backend.path_parts with input as portal_request("GET", "/v2/datasets/abc")
	result == ["v2", "datasets", "abc"]
}

# =============================================================================
# SUB-RESOURCE PATH PATTERN TESTS
# =============================================================================

test_path_pattern_4_segment_publish if {
	result := portal_backend.path_pattern with input as portal_request("POST", "/v2/datasets/abc-123/publish")
	result == "/v2/datasets/{id}/publish"
}

test_path_pattern_4_segment_assignments if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/datasets/abc-123/assignments")
	result == "/v2/datasets/{id}/assignments"
}

test_path_pattern_4_segment_pipelines if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/datasets/abc-123/pipelines")
	result == "/v2/datasets/{id}/pipelines"
}

test_path_pattern_datasource_publish if {
	result := portal_backend.path_pattern with input as portal_request("POST", "/v2/datasources/ds-123/publish")
	result == "/v2/datasources/{id}/publish"
}

test_path_pattern_datastructure_assignments if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/datastructures/dstr-123/assignments")
	result == "/v2/datastructures/{id}/assignments"
}

# =============================================================================
# 5-SEGMENT PATH TESTS
# =============================================================================

test_path_pattern_5_segment_published_meta if {
	result := portal_backend.path_pattern with input as portal_request("PUT", "/v2/datasets/abc-123/published/meta")
	result == "/v2/datasets/{id}/published/meta"
}

test_path_pattern_5_segment_pipelines_id if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/datasets/abc-123/pipelines/pipe-456")
	result == "/v2/datasets/{id}/pipelines/{id}"
}

test_path_pattern_5_segment_versions_id if {
	result := portal_backend.path_pattern with input as portal_request("GET", "/v2/datastructures/dstr-123/versions/v-456")
	result == "/v2/datastructures/{id}/versions/{id}"
}

# =============================================================================
# 6-SEGMENT PATH TESTS
# =============================================================================

test_path_pattern_6_segment_versions_publish if {
	result := portal_backend.path_pattern with input as portal_request("POST", "/v2/datastructures/dstr-123/versions/v-456/publish")
	result == "/v2/datastructures/{id}/versions/{id}/publish"
}

test_path_pattern_6_segment_versions_unpublish if {
	result := portal_backend.path_pattern with input as portal_request("POST", "/v2/datastructures/dstr-123/versions/v-456/unpublish")
	result == "/v2/datastructures/{id}/versions/{id}/unpublish"
}

# =============================================================================
# NEW RESOURCE ENDPOINT TESTS
# =============================================================================

test_endpoints_contains_datasources if {
	portal_backend.endpoints["/v2/datasources"]
}

test_endpoints_contains_datasources_id if {
	portal_backend.endpoints["/v2/datasources/{id}"]
}

test_endpoints_contains_datastructures if {
	portal_backend.endpoints["/v2/datastructures"]
}

test_endpoints_contains_datastructures_id if {
	portal_backend.endpoints["/v2/datastructures/{id}"]
}

# =============================================================================
# DATASPACES AND CATALOGS TESTS (null-permission endpoints)
# =============================================================================

test_endpoints_contains_dataspaces if {
	portal_backend.endpoints["/v2/dataspaces"]
}

test_endpoints_contains_dataspaces_id if {
	portal_backend.endpoints["/v2/dataspaces/{id}"]
}

test_endpoints_contains_catalogs if {
	portal_backend.endpoints["/v2/catalogs"]
}

test_endpoints_contains_catalogs_id if {
	portal_backend.endpoints["/v2/catalogs/{id}"]
}

# =============================================================================
# SCOPE TYPE TESTS FOR NEW RESOURCES
# =============================================================================

test_scope_type_datasources if {
	result := portal_backend.expected_scope_type with input as portal_request("GET", "/v2/datasources")
	result == "DATASOURCE"
}

test_scope_type_datastructures if {
	result := portal_backend.expected_scope_type with input as portal_request("GET", "/v2/datastructures")
	result == "DATASTRUCTURE"
}

# =============================================================================
# SUB-RESOURCE SCOPE ENFORCEMENT TESTS
# =============================================================================

# Sub-resource resource_id should be the parent resource ID (parts[2])
test_resource_id_4_segment if {
	result := portal_backend.resource_id with input as portal_request("POST", "/v2/datasets/abc-123/publish")
	result == "abc-123"
}

test_resource_id_5_segment if {
	result := portal_backend.resource_id with input as portal_request("DELETE", "/v2/datasets/abc-123/assignments/assign-456")
	result == "abc-123"
}

test_resource_id_6_segment if {
	result := portal_backend.resource_id with input as portal_request("POST", "/v2/datastructures/dstr-123/versions/v-456/publish")
	result == "dstr-123"
}

# Sub-resource paths are resource endpoints (not collection)
test_is_resource_endpoint_4_segment if {
	portal_backend.is_resource_endpoint with input as portal_request("POST", "/v2/datasets/abc-123/publish")
}

test_is_resource_endpoint_5_segment if {
	portal_backend.is_resource_endpoint with input as portal_request("DELETE", "/v2/datasets/abc-123/assignments/assign-456")
}

test_is_resource_endpoint_6_segment if {
	portal_backend.is_resource_endpoint with input as portal_request("POST", "/v2/datastructures/dstr-123/versions/v-456/publish")
}

# =============================================================================
# COLLECTION ENDPOINT CLASSIFICATION TESTS (data-driven)
# =============================================================================

test_is_collection_endpoint if {
	portal_backend.is_collection_endpoint with input as portal_request("GET", "/v2/datasets")
}

test_not_collection_endpoint_resource if {
	not portal_backend.is_collection_endpoint with input as portal_request("GET", "/v2/datasets/abc-123")
}

test_not_collection_endpoint_sub_resource if {
	not portal_backend.is_collection_endpoint with input as portal_request("POST", "/v2/datasets/abc-123/publish")
}

test_not_collection_endpoint_users_me if {
	not portal_backend.is_collection_endpoint with input as portal_request("GET", "/v2/users/me")
}
