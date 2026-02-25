# Tests for providers/frost_server.rego - FROST Server Provider

package civitas.authz.providers.frost_server_test

import rego.v1

import data.civitas.authz.providers.frost_server

# =============================================================================
# TEST HELPERS
# =============================================================================

frost_request(method, path) := {"request": {
	"method": method,
	"path": path,
	"headers": {},
}}

# =============================================================================
# ENDPOINT CONFIGURATION TESTS
# =============================================================================

test_endpoints_loaded if {
	count(frost_server.endpoints) == 1
}

# =============================================================================
# PATH PATTERN MATCHING TESTS
# =============================================================================

# Valid FROST path should match
test_path_pattern_sta_endpoint if {
	result := frost_server.path_pattern with input as frost_request("GET", "/api/v1/abc-123/sta")
	result == "/api/v1/{id}/sta"
}

test_path_pattern_sta_uuid if {
	result := frost_server.path_pattern with input as frost_request("GET", "/api/v1/550e8400-e29b-41d4-a716-446655440000/sta")
	result == "/api/v1/{id}/sta"
}

# Unknown paths return empty (fail-secure)
test_path_pattern_unknown if {
	result := frost_server.path_pattern with input as frost_request("GET", "/api/v1/abc-123/unknown")
	result == ""
}

# Native OData paths are not mapped
test_path_pattern_odata_not_mapped if {
	result := frost_server.path_pattern with input as frost_request("GET", "/v1.1/Things")
	result == ""
}

# =============================================================================
# REQUEST ACCESSOR TESTS
# =============================================================================

test_request_method if {
	result := frost_server.request_method with input as frost_request("GET", "/api/v1/abc-123/sta")
	result == "GET"
}

test_request_path_valid if {
	result := frost_server.request_path with input as frost_request("GET", "/api/v1/abc-123/sta")
	result == "/api/v1/abc-123/sta"
}

# =============================================================================
# SCOPE ENFORCEMENT TESTS
# =============================================================================

# Resource ID is the dataset ID (path segment 2)
test_resource_id if {
	result := frost_server.resource_id with input as frost_request("GET", "/api/v1/dataset-42/sta")
	result == "dataset-42"
}

test_resource_id_uuid if {
	result := frost_server.resource_id with input as frost_request("GET", "/api/v1/550e8400-e29b-41d4-a716-446655440000/sta")
	result == "550e8400-e29b-41d4-a716-446655440000"
}

# Scope type is always DATASET
test_expected_scope_type if {
	result := frost_server.expected_scope_type with input as frost_request("GET", "/api/v1/dataset-42/sta")
	result == "DATASET"
}

# Always a resource endpoint
test_is_resource_endpoint if {
	frost_server.is_resource_endpoint with input as frost_request("GET", "/api/v1/dataset-42/sta")
}

# Never a collection endpoint
test_not_collection_endpoint if {
	not frost_server.is_collection_endpoint with input as frost_request("GET", "/api/v1/dataset-42/sta")
}

# =============================================================================
# PATH VALIDATION SECURITY TESTS
# =============================================================================

test_path_traversal_rejected if {
	result := frost_server.path_pattern with input as frost_request("GET", "/api/v1/../admin/sta")
	result == ""
}

test_invalid_path_empty_request_path if {
	result := frost_server.request_path with input as frost_request("GET", "/api/v1/../admin/sta")
	result == ""
}
