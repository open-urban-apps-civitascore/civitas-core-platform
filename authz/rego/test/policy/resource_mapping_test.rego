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
        "headers": {"x-authz-backend": "portal-backend"}
    }
}

# =============================================================================
# BACKEND DETECTION TESTS
# =============================================================================

test_backend_from_header if {
    result := resource_mapping.backend with input as portal_request("GET", "/v2/users")
    result == "portal-backend"
}

test_backend_unknown_without_header if {
    result := resource_mapping.backend with input as {
        "request": {"path": "/v2/users", "headers": {}}
    }
    result == "unknown"
}

test_backend_data_key_conversion if {
    result := resource_mapping.backend_data_key with input as portal_request("GET", "/v2/users")
    result == "portal_backend"
}

# =============================================================================
# PATH PATTERN MATCHING TESTS
# =============================================================================

# Collection endpoints (no ID) - exact match
test_path_pattern_collection if {
    result := resource_mapping.path_pattern with input as portal_request("GET", "/v2/users")
    result == "/v2/users"
}

test_path_pattern_datasets_collection if {
    result := resource_mapping.path_pattern with input as portal_request("GET", "/v2/datasets")
    result == "/v2/datasets"
}

# Resource endpoints (with ID) - pattern match
test_path_pattern_with_id if {
    result := resource_mapping.path_pattern with input as portal_request("GET", "/v2/users/abc-123")
    result == "/v2/users/{id}"
}

test_path_pattern_with_numeric_id if {
    result := resource_mapping.path_pattern with input as portal_request("GET", "/v2/datasets/12345")
    result == "/v2/datasets/{id}"
}

test_path_pattern_with_uuid if {
    result := resource_mapping.path_pattern with input as portal_request("DELETE", "/v2/groups/550e8400-e29b-41d4-a716-446655440000")
    result == "/v2/groups/{id}"
}

# Special endpoint - /users/me (not an ID)
test_path_pattern_users_me if {
    result := resource_mapping.path_pattern with input as portal_request("GET", "/v2/users/me")
    result == "/v2/users/me"
}

# Unknown path - not in mappings
test_path_pattern_unknown if {
    result := resource_mapping.path_pattern with input as portal_request("GET", "/v2/foobar")
    result == ""
}

# =============================================================================
# REQUEST METHOD/PATH EXTRACTION TESTS
# =============================================================================

test_request_path if {
    result := resource_mapping.request_path with input as portal_request("GET", "/v2/users/123")
    result == "/v2/users/123"
}

test_request_method if {
    result := resource_mapping.request_method with input as portal_request("POST", "/v2/users")
    result == "POST"
}

# =============================================================================
# PATH VALIDATION SECURITY TESTS
# =============================================================================

# Valid paths should be allowed
test_valid_path if {
    result := resource_mapping.is_valid_path with input as portal_request("GET", "/v2/users")
    result == true
}

# Path traversal attempts should be rejected
test_path_traversal_rejected if {
    result := resource_mapping.is_valid_path with input as portal_request("GET", "/v2/users/../admin")
    result == false
}

test_path_traversal_encoded_rejected if {
    # Even if somehow URL-encoded .. gets through, reject it
    result := resource_mapping.request_path with input as portal_request("GET", "/v2/users/../admin")
    result == ""
}

# Null byte injection should be rejected
test_null_byte_rejected if {
    result := resource_mapping.is_valid_path with input as {
        "request": {"path": "/v2/users\u0000/admin", "method": "GET", "headers": {"x-authz-backend": "portal-backend"}}
    }
    result == false
}

# Backslash injection should be rejected
test_backslash_rejected if {
    result := resource_mapping.is_valid_path with input as portal_request("GET", "/v2/users\\admin")
    result == false
}

# Backend ID validation - valid IDs
test_valid_backend_id if {
    result := resource_mapping.backend with input as {
        "request": {"path": "/v2/users", "method": "GET", "headers": {"x-authz-backend": "portal-backend"}}
    }
    result == "portal-backend"
}

# Backend ID with special characters should be rejected
test_invalid_backend_id_traversal if {
    result := resource_mapping.backend with input as {
        "request": {"path": "/v2/users", "method": "GET", "headers": {"x-authz-backend": "../etc/passwd"}}
    }
    result == "unknown"
}

test_invalid_backend_id_spaces if {
    result := resource_mapping.backend with input as {
        "request": {"path": "/v2/users", "method": "GET", "headers": {"x-authz-backend": "portal backend"}}
    }
    result == "unknown"
}

# =============================================================================
# ALL RESOURCE TYPES TESTS
# =============================================================================

test_all_resource_paths if {
    # Test all endpoints in portal_backend.json resolve correctly
    patterns := [
        ["/v2/users", "/v2/users"],
        ["/v2/users/123", "/v2/users/{id}"],
        ["/v2/dataspaces", "/v2/dataspaces"],
        ["/v2/dataspaces/abc", "/v2/dataspaces/{id}"],
        ["/v2/datasets", "/v2/datasets"],
        ["/v2/datasets/xyz", "/v2/datasets/{id}"],
        ["/v2/groups", "/v2/groups"],
        ["/v2/groups/g1", "/v2/groups/{id}"],
        ["/v2/roles", "/v2/roles"],
        ["/v2/roles/r1", "/v2/roles/{id}"],
        ["/v2/permissions", "/v2/permissions"],
        ["/v2/permissions/p1", "/v2/permissions/{id}"],
        ["/v2/assignments", "/v2/assignments"],
        ["/v2/assignments/a1", "/v2/assignments/{id}"],
        ["/v2/catalogs", "/v2/catalogs"],
        ["/v2/catalogs/c1", "/v2/catalogs/{id}"],
    ]
    every pattern in patterns {
        actual := resource_mapping.path_pattern with input as portal_request("GET", pattern[0])
        actual == pattern[1]
    }
}
