# Tests for providers/frost_server.rego - FROST Server Provider

package civitas.authz.providers.frost_server_test

import rego.v1

import data.civitas.authz.providers.frost_server

# =============================================================================
# TEST HELPERS
# =============================================================================

# Legacy helper (no host) — retained only for tests that assert missing-host behaviour.
frost_request(method, path) := {"request": {
	"method": method,
	"path": path,
	"headers": {},
}}

# Canonical helper for published-data requests reaching FROST through APISIX.
# All real-world requests carry a Host header set by APISIX; the provider must enforce it.
frost_request_host(method, path, host) := {"request": {
	"method": method,
	"path": path,
	"headers": {"host": host},
}}

api_host := "api.example.test"

# =============================================================================
# ENDPOINT CONFIGURATION TESTS
# =============================================================================

test_endpoints_loaded if {
	count(frost_server.endpoints) == 1
}

# =============================================================================
# PATH PATTERN MATCHING TESTS (issue #1368 — host- and /v1-isolated)
# =============================================================================

# Base dataset path should match on the configured API host
test_path_pattern_dataset_root if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "/v1/datasets/{id}"
}

test_path_pattern_dataset_uuid if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/550e8400-e29b-41d4-a716-446655440000", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "/v1/datasets/{id}"
}

# STA sub-resource paths should also match (prefix matching)
test_path_pattern_sta_things if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123/Things", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "/v1/datasets/{id}"
}

test_path_pattern_sta_datastreams if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123/Datastreams", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "/v1/datasets/{id}"
}

test_path_pattern_sta_deep_path if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123/Things(1)/Datastreams", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "/v1/datasets/{id}"
}

# Unknown top-level paths return empty (fail-secure)
test_path_pattern_unknown_prefix if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/other/abc-123", api_host)
		with data.backends.frost_server.api_host as api_host
	result == ""
}

# Single segment should not match (even on the api host)
test_path_pattern_no_id if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets", api_host)
		with data.backends.frost_server.api_host as api_host
	result == ""
}

# Legacy /datasets path (without /v1 prefix) must be rejected — this is the original bug.
test_path_pattern_legacy_shape_rejected if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/datasets/abc-123", api_host)
		with data.backends.frost_server.api_host as api_host
	result == ""
}

test_path_pattern_legacy_shape_with_subresource_rejected if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/datasets/abc-123/Things", api_host)
		with data.backends.frost_server.api_host as api_host
	result == ""
}

# Requests on the wrong virtual host must not match the FROST provider.
test_path_pattern_wrong_host_rejected if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", "other.example.test")
		with data.backends.frost_server.api_host as api_host
	result == ""
}

# Requests without a Host header must be rejected (sanity check).
test_path_pattern_missing_host_rejected if {
	result := frost_server.path_pattern with input as frost_request("GET", "/v1/datasets/abc-123")
		with data.backends.frost_server.api_host as api_host
	result == ""
}

# Host matching must be case-insensitive.
test_path_pattern_case_insensitive_host if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", "Api.Example.Test")
		with data.backends.frost_server.api_host as api_host
	result == "/v1/datasets/{id}"
}

# Host matching must ignore the port (APISIX does the same).
test_path_pattern_host_with_port_accepted if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", "api.example.test:9080")
		with data.backends.frost_server.api_host as api_host
	result == "/v1/datasets/{id}"
}

test_path_pattern_host_with_port_and_mixed_case_accepted if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", "Api.Example.Test:443")
		with data.backends.frost_server.api_host as api_host
	result == "/v1/datasets/{id}"
}

# Bracketed IPv6 literals with a port must be parsed correctly (review F3).
test_path_pattern_ipv6_literal_with_port_accepted if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", "[::1]:9080")
		with data.backends.frost_server.api_host as "[::1]"
	result == "/v1/datasets/{id}"
}

# Bracketed IPv6 literals without a port must also be accepted.
test_path_pattern_ipv6_literal_without_port_accepted if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", "[2001:db8::1]")
		with data.backends.frost_server.api_host as "[2001:db8::1]"
	result == "/v1/datasets/{id}"
}

# IPv6 address on the wrong host must still be rejected (sanity check).
test_path_pattern_ipv6_literal_wrong_host_rejected if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", "[::1]:9080")
		with data.backends.frost_server.api_host as "[fe80::1]"
	result == ""
}

# When no api_host is configured, everything must fail-closed.
test_path_pattern_unconfigured_api_host_rejected if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/abc-123", api_host)
		with data.backends.frost_server.api_host as ""
	result == ""
}

# =============================================================================
# SCOPE ENFORCEMENT TESTS
# =============================================================================

# Resource ID is the dataset ID (third path segment now: /v1/datasets/{id})
test_resource_id if {
	result := frost_server.resource_id with input as frost_request_host("GET", "/v1/datasets/dataset-42", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "dataset-42"
}

test_resource_id_uuid if {
	result := frost_server.resource_id with input as frost_request_host("GET", "/v1/datasets/550e8400-e29b-41d4-a716-446655440000", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "550e8400-e29b-41d4-a716-446655440000"
}

# Resource ID consistent for sub-paths
test_resource_id_with_subpath if {
	result := frost_server.resource_id with input as frost_request_host("GET", "/v1/datasets/dataset-42/Things", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "dataset-42"
}

# Scope type is always DATASET
test_expected_scope_type if {
	result := frost_server.expected_scope_type with input as frost_request_host("GET", "/v1/datasets/dataset-42", api_host)
		with data.backends.frost_server.api_host as api_host
	result == "DATASET"
}

# Always a resource endpoint
test_is_resource_endpoint if {
	frost_server.is_resource_endpoint with input as frost_request_host("GET", "/v1/datasets/dataset-42", api_host)
		with data.backends.frost_server.api_host as api_host
}

# Never a collection endpoint
test_not_collection_endpoint if {
	not frost_server.is_collection_endpoint with input as frost_request_host("GET", "/v1/datasets/dataset-42", api_host)
		with data.backends.frost_server.api_host as api_host
}

# =============================================================================
# PATH VALIDATION SECURITY TESTS
# =============================================================================

test_path_traversal_rejected if {
	result := frost_server.path_pattern with input as frost_request_host("GET", "/v1/datasets/../admin", api_host)
		with data.backends.frost_server.api_host as api_host
	result == ""
}
