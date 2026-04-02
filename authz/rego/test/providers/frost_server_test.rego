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

# Base dataset path should match
test_path_pattern_dataset_root if {
	result := frost_server.path_pattern with input as frost_request("GET", "/datasets/abc-123")
	result == "/datasets/{id}"
}

test_path_pattern_dataset_uuid if {
	result := frost_server.path_pattern with input as frost_request("GET", "/datasets/550e8400-e29b-41d4-a716-446655440000")
	result == "/datasets/{id}"
}

# STA sub-resource paths should also match (prefix matching)
test_path_pattern_sta_things if {
	result := frost_server.path_pattern with input as frost_request("GET", "/datasets/abc-123/Things")
	result == "/datasets/{id}"
}

test_path_pattern_sta_datastreams if {
	result := frost_server.path_pattern with input as frost_request("GET", "/datasets/abc-123/Datastreams")
	result == "/datasets/{id}"
}

test_path_pattern_sta_deep_path if {
	result := frost_server.path_pattern with input as frost_request("GET", "/datasets/abc-123/Things(1)/Datastreams")
	result == "/datasets/{id}"
}

# Unknown top-level paths return empty (fail-secure)
test_path_pattern_unknown_prefix if {
	result := frost_server.path_pattern with input as frost_request("GET", "/other/abc-123")
	result == ""
}

# Single segment should not match
test_path_pattern_no_id if {
	result := frost_server.path_pattern with input as frost_request("GET", "/datasets")
	result == ""
}

# =============================================================================
# SCOPE ENFORCEMENT TESTS
# =============================================================================

# Resource ID is the dataset ID (second path segment)
test_resource_id if {
	result := frost_server.resource_id with input as frost_request("GET", "/datasets/dataset-42")
	result == "dataset-42"
}

test_resource_id_uuid if {
	result := frost_server.resource_id with input as frost_request("GET", "/datasets/550e8400-e29b-41d4-a716-446655440000")
	result == "550e8400-e29b-41d4-a716-446655440000"
}

# Resource ID consistent for sub-paths
test_resource_id_with_subpath if {
	result := frost_server.resource_id with input as frost_request("GET", "/datasets/dataset-42/Things")
	result == "dataset-42"
}

# Scope type is always DATASET
test_expected_scope_type if {
	result := frost_server.expected_scope_type with input as frost_request("GET", "/datasets/dataset-42")
	result == "DATASET"
}

# Always a resource endpoint
test_is_resource_endpoint if {
	frost_server.is_resource_endpoint with input as frost_request("GET", "/datasets/dataset-42")
}

# Never a collection endpoint
test_not_collection_endpoint if {
	not frost_server.is_collection_endpoint with input as frost_request("GET", "/datasets/dataset-42")
}

# =============================================================================
# PATH VALIDATION SECURITY TESTS
# =============================================================================

test_path_traversal_rejected if {
	result := frost_server.path_pattern with input as frost_request("GET", "/datasets/../admin")
	result == ""
}
