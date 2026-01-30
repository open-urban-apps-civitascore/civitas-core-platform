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
	"headers": {"x-authz-backend": "portal-backend"},
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
