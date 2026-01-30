# Tests for providers/frost_server.rego - FROST Server Provider (Stub)

package civitas.authz.providers.frost_server_test

import rego.v1

import data.civitas.authz.providers.frost_server

# =============================================================================
# TEST HELPERS
# =============================================================================

frost_request(method, path) := {"request": {
	"method": method,
	"path": path,
	"headers": {"x-authz-backend": "frost-server"},
}}

# =============================================================================
# STUB BEHAVIOR TESTS
# =============================================================================

# Stub should have empty endpoints
test_endpoints_empty if {
	count(frost_server.endpoints) == 0
}

# All paths should return empty pattern (denied by fail-secure)
test_path_pattern_odata_things if {
	result := frost_server.path_pattern with input as frost_request("GET", "/v1.1/Things")
	result == ""
}

test_path_pattern_odata_entity if {
	result := frost_server.path_pattern with input as frost_request("GET", "/v1.1/Things(123)")
	result == ""
}

test_path_pattern_odata_nested if {
	result := frost_server.path_pattern with input as frost_request("GET", "/v1.1/Things(123)/Datastreams")
	result == ""
}

# =============================================================================
# REQUEST ACCESSOR TESTS
# =============================================================================

test_request_method if {
	result := frost_server.request_method with input as frost_request("GET", "/v1.1/Things")
	result == "GET"
}

test_request_path_valid if {
	result := frost_server.request_path with input as frost_request("GET", "/v1.1/Things")
	result == "/v1.1/Things"
}
