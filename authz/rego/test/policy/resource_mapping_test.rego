# Tests for resource_mapping.rego - Backend detection and path pattern matching

package civitas.authz.resource_mapping_test

import rego.v1

import data.civitas.authz.resource_mapping

# =============================================================================
# TEST HELPERS
# =============================================================================

portal_request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {},
	},
	"service": {"name": "portal-backend"},
}

# =============================================================================
# BACKEND DETECTION TESTS
# =============================================================================

test_backend_from_service if {
	result := resource_mapping.backend with input as portal_request("GET", "/v1/users")
	result == "portal-backend"
}

test_backend_unknown_without_service if {
	result := resource_mapping.backend with input as {"request": {"path": "/v1/users", "headers": {}}}
	result == "unknown"
}

test_backend_unknown_empty_service_name if {
	result := resource_mapping.backend with input as {
		"request": {"path": "/v1/users", "headers": {}},
		"service": {"name": ""},
	}
	result == "unknown"
}

test_backend_data_key_conversion if {
	result := resource_mapping.backend_data_key with input as portal_request("GET", "/v1/users")
	result == "portal_backend"
}

# FROST data-plane dispatch (#1368): the per-named-API APISIX route carries
# service_id=svc-frost-server, whose Service name is "frost-server" (see
# dev-environment/apisix/seed-routes.sh). With with_service=true APISIX forwards that Service, so
# OPA reads input.service.name == "frost-server" and routes to the frost_server backend. These
# tests pin that contract — the route-level service_id is what selects the frost_server policy.
frost_request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {},
	},
	"service": {"name": "frost-server"},
}

test_backend_frost_server_from_service if {
	result := resource_mapping.backend with input as frost_request("GET", "/v1/datasets/abc-123/Things")
	result == "frost-server"
}

test_backend_data_key_frost_conversion if {
	# "frost-server" → "frost_server" → data.backends.frost_server
	result := resource_mapping.backend_data_key with input as frost_request("GET", "/v1/datasets/abc-123/Things")
	result == "frost_server"
}

# Without the FROST service (route missing its service_id, or wrong service), the same FROST
# data-plane path does NOT dispatch to frost_server — proving the service binding is load-bearing.
test_frost_dataplane_without_service_not_frost if {
	result := resource_mapping.backend with input as {"request": {"method": "GET", "path": "/v1/datasets/abc-123/Things", "headers": {}}}
	result == "unknown"
}

test_frost_dataplane_wrong_service_not_frost if {
	result := resource_mapping.backend with input as {
		"request": {"method": "GET", "path": "/v1/datasets/abc-123/Things", "headers": {}},
		"service": {"name": "portal-backend"},
	}
	result != "frost-server"
}

# =============================================================================
# PATH PATTERN MATCHING TESTS
# =============================================================================

# Collection endpoints (no ID) - exact match
test_path_pattern_collection if {
	result := resource_mapping.path_pattern with input as portal_request("GET", "/v1/users")
	result == "/v1/users"
}

test_path_pattern_datasets_collection if {
	result := resource_mapping.path_pattern with input as portal_request("GET", "/v1/datasets")
	result == "/v1/datasets"
}

# Resource endpoints (with ID) - pattern match
test_path_pattern_with_id if {
	result := resource_mapping.path_pattern with input as portal_request("GET", "/v1/users/abc-123")
	result == "/v1/users/{id}"
}

test_path_pattern_with_numeric_id if {
	result := resource_mapping.path_pattern with input as portal_request("GET", "/v1/datasets/12345")
	result == "/v1/datasets/{id}"
}

test_path_pattern_with_uuid if {
	result := resource_mapping.path_pattern with input as portal_request("DELETE", "/v1/groups/550e8400-e29b-41d4-a716-446655440000")
	result == "/v1/groups/{id}"
}

# Special endpoint - /users/me (not an ID)
test_path_pattern_users_me if {
	result := resource_mapping.path_pattern with input as portal_request("GET", "/v1/users/me")
	result == "/v1/users/me"
}

# Unknown path - not in mappings
test_path_pattern_unknown if {
	result := resource_mapping.path_pattern with input as portal_request("GET", "/v1/foobar")
	result == ""
}

# =============================================================================
# REQUEST METHOD/PATH EXTRACTION TESTS
# =============================================================================

test_request_path if {
	result := resource_mapping.request_path with input as portal_request("GET", "/v1/users/123")
	result == "/v1/users/123"
}

test_request_method if {
	result := resource_mapping.request_method with input as portal_request("POST", "/v1/users")
	result == "POST"
}

# =============================================================================
# PATH VALIDATION SECURITY TESTS
# =============================================================================

# Valid paths should be allowed
test_valid_path if {
	result := resource_mapping.is_valid_path with input as portal_request("GET", "/v1/users")
	result == true
}

# Path traversal attempts should be rejected
test_path_traversal_rejected if {
	result := resource_mapping.is_valid_path with input as portal_request("GET", "/v1/users/../admin")
	result == false
}

test_path_traversal_encoded_rejected if {
	# Even if somehow URL-encoded .. gets through, reject it
	result := resource_mapping.request_path with input as portal_request("GET", "/v1/users/../admin")
	result == ""
}

# Null byte injection should be rejected
test_null_byte_rejected if {
	result := resource_mapping.is_valid_path with input as {
		"request": {"path": "/v1/users\u0000/admin", "method": "GET", "headers": {}},
		"service": {"name": "portal-backend"},
	}
	result == false
}

# Backslash injection should be rejected
test_backslash_rejected if {
	result := resource_mapping.is_valid_path with input as portal_request("GET", "/v1/users\\admin")
	result == false
}

# Backend ID validation - valid IDs
test_valid_backend_id if {
	result := resource_mapping.backend with input as {
		"request": {"path": "/v1/users", "method": "GET", "headers": {}},
		"service": {"name": "portal-backend"},
	}
	result == "portal-backend"
}

# Backend ID with special characters should be rejected
test_invalid_backend_id_traversal if {
	result := resource_mapping.backend with input as {
		"request": {"path": "/v1/users", "method": "GET", "headers": {}},
		"service": {"name": "../etc/passwd"},
	}
	result == "unknown"
}

test_invalid_backend_id_spaces if {
	result := resource_mapping.backend with input as {
		"request": {"path": "/v1/users", "method": "GET", "headers": {}},
		"service": {"name": "portal backend"},
	}
	result == "unknown"
}

# =============================================================================
# ALL RESOURCE TYPES TESTS
# =============================================================================

test_all_resource_paths if {
	# Test all endpoints in portal_backend/data.json resolve correctly
	patterns := [
		["/v1/users", "/v1/users"],
		["/v1/users/123", "/v1/users/{id}"],
		["/v1/datasets", "/v1/datasets"],
		["/v1/datasets/xyz", "/v1/datasets/{id}"],
		["/v1/datasources", "/v1/datasources"],
		["/v1/datasources/ds1", "/v1/datasources/{id}"],
		["/v1/datastructures", "/v1/datastructures"],
		["/v1/datastructures/dstr1", "/v1/datastructures/{id}"],
		["/v1/groups", "/v1/groups"],
		["/v1/groups/g1", "/v1/groups/{id}"],
		["/v1/roles", "/v1/roles"],
		["/v1/roles/r1", "/v1/roles/{id}"],
		["/v1/permissions", "/v1/permissions"],
		["/v1/permissions/p1", "/v1/permissions/{id}"],
		["/v1/assignments", "/v1/assignments"],
		["/v1/assignments/a1", "/v1/assignments/{id}"],
	]
	every pattern in patterns {
		actual := resource_mapping.path_pattern with input as portal_request("GET", pattern[0])
		actual == pattern[1]
	}
}

# Test: Sub-resource paths resolve correctly
test_sub_resource_paths if {
	patterns := [
		["/v1/datasets/abc/stage", "/v1/datasets/{id}/stage"],
		["/v1/datasets/abc/unstage", "/v1/datasets/{id}/unstage"],
		["/v1/datasets/abc/assignments", "/v1/datasets/{id}/assignments"],
		["/v1/datasets/abc/pipelines", "/v1/datasets/{id}/pipelines"],
		["/v1/datasources/abc/release", "/v1/datasources/{id}/release"],
		["/v1/datasources/abc/unrelease", "/v1/datasources/{id}/unrelease"],
		["/v1/datastructures/abc/release", "/v1/datastructures/{id}/release"],
		["/v1/datastructures/abc/unrelease", "/v1/datastructures/{id}/unrelease"],
		["/v1/datastructures/abc/versions", "/v1/datastructures/{id}/versions"],
	]
	every pattern in patterns {
		actual := resource_mapping.path_pattern with input as portal_request("POST", pattern[0])
		actual == pattern[1]
	}
}

# Test: 5-segment paths resolve correctly (both literal-tail and both-{id} variants)
test_5_segment_paths if {
	# Literal tail: released/meta
	result1 := resource_mapping.path_pattern with input as portal_request("PUT", "/v1/datasets/abc/released/meta")
	result1 == "/v1/datasets/{id}/released/meta"

	# Both-{id}: pipelines/{id}
	result2 := resource_mapping.path_pattern with input as portal_request("GET", "/v1/datasets/abc/pipelines/pipe-1")
	result2 == "/v1/datasets/{id}/pipelines/{id}"

	# Both-{id}: versions/{id}
	result3 := resource_mapping.path_pattern with input as portal_request("GET", "/v1/datastructures/abc/versions/v1")
	result3 == "/v1/datastructures/{id}/versions/{id}"
}

# Test: 6-segment paths resolve correctly
test_6_segment_paths if {
	result1 := resource_mapping.path_pattern with input as portal_request("POST", "/v1/datastructures/abc/versions/xyz/release")
	result1 == "/v1/datastructures/{id}/versions/{id}/release"

	result2 := resource_mapping.path_pattern with input as portal_request("POST", "/v1/datastructures/abc/versions/xyz/unrelease")
	result2 == "/v1/datastructures/{id}/versions/{id}/unrelease"
}

# Test: Dataspaces and catalogs do NOT resolve (removed from v2.0, see #989)
test_dataspaces_do_not_resolve if {
	patterns := ["/v1/dataspaces", "/v1/dataspaces/abc", "/v1/catalogs", "/v1/catalogs/c1"]
	every path in patterns {
		actual := resource_mapping.path_pattern with input as portal_request("GET", path)
		actual == ""
	}
}
