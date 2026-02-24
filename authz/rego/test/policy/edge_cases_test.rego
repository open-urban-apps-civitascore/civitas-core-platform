# Tests for edge cases in path parsing
# These document known behaviors and edge cases.

package civitas.authz.edge_cases_test

import rego.v1

import data.civitas.authz.resource_mapping

# =============================================================================
# EDGE CASE HELPERS
# =============================================================================

portal_request(path) := {
	"request": {
		"method": "GET",
		"path": path,
		"headers": {},
	},
	"service": {"name": "portal-backend"},
}

# =============================================================================
# PATH PARSING EDGE CASES
# =============================================================================

# Double slashes - split creates empty strings
# EXPECTED: No match (malformed path)
test_double_slash if {
	parts := resource_mapping.path_parts with input as portal_request("/v2//users")

	# ["v2", "", "users"] - empty string between slashes
	parts[0] == "v2"
	parts[1] == ""
	parts[2] == "users"
}

test_double_slash_no_match if {
	pattern := resource_mapping.path_pattern with input as portal_request("/v2//users")

	# Malformed path - no match
	pattern == ""
}

# Trailing slash - should NOT match {id} pattern
test_trailing_slash_no_match if {
	pattern := resource_mapping.path_pattern with input as portal_request("/v2/users/")

	# Empty third segment should not match /v2/users/{id}
	pattern == ""
}

# Case sensitivity - paths are case-sensitive
# EXPECTED: No match (endpoints are lowercase)
test_case_sensitivity if {
	pattern := resource_mapping.path_pattern with input as portal_request("/V2/Users")
	pattern == ""
}

# Query string handling
# NOTE: APISIX typically strips query strings before path matching.
# If it doesn't, the query string becomes part of the path and won't match.
test_query_string_in_parts if {
	parts := resource_mapping.path_parts with input as portal_request("/v2/users?filter=active")

	# Query string is part of last segment
	parts[1] == "users?filter=active"
}

test_query_string_no_match if {
	pattern := resource_mapping.path_pattern with input as portal_request("/v2/users?filter=active")

	# "/v2/users?filter=active" != "/v2/users"
	pattern == ""
}

# URL encoding - not decoded by OPA
# EXPECTED: No match (encoded characters not decoded)
test_url_encoding if {
	parts := resource_mapping.path_parts with input as portal_request("/v2/users/%7Bid%7D")

	# %7B = {, %7D = } - NOT decoded
	parts[2] == "%7Bid%7D"
}

# Empty path
test_empty_path if {
	parts := resource_mapping.path_parts with input as portal_request("/")
	count(parts) == 1
	parts[0] == ""
}

# Backend still works with malformed paths (comes from service metadata)
test_backend_from_service_regardless_of_path if {
	backend := resource_mapping.backend with input as portal_request("/")
	backend == "portal-backend"
}

# =============================================================================
# VALID PATH PATTERNS
# =============================================================================

# Normal collection path
test_valid_collection if {
	pattern := resource_mapping.path_pattern with input as portal_request("/v2/users")
	pattern == "/v2/users"
}

# Normal resource path
test_valid_resource if {
	pattern := resource_mapping.path_pattern with input as portal_request("/v2/users/123")
	pattern == "/v2/users/{id}"
}

# UUID as resource ID
test_valid_uuid if {
	pattern := resource_mapping.path_pattern with input as portal_request("/v2/datasets/550e8400-e29b-41d4-a716-446655440000")
	pattern == "/v2/datasets/{id}"
}

# Special endpoint /users/me
test_valid_special_me if {
	pattern := resource_mapping.path_pattern with input as portal_request("/v2/users/me")
	pattern == "/v2/users/me"
}
