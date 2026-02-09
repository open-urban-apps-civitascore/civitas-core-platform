# Test Helpers - HTTP Mocking for OPA Tests
#
# Provides utilities for mocking http.send() in tests.
# Mock functions must have the same arity as http.send (1 argument).
#
# Usage in tests:
#   # Define mock in test file:
#   mock_send(_) := {"status_code": 200, "body": sample_user_context}
#
#   # Use in test:
#   test_something if {
#       result := some_rule
#           with http.send as mock_send
#           with input.request.headers["x-userinfo"] as encode_userinfo("user-123")
#   }

package test.helpers.mock_http

import rego.v1

# =============================================================================
# TEST CONFIGURATION
# =============================================================================

# All tests must provide data.config with authz_repository_url (no hardcoded default).
# Use: with data.config as mock_http.test_config
mock_config := {"authz_repository_url": "http://test-authz-repo:8091/api/v1/user-context"}

# =============================================================================
# USERINFO HEADER ENCODING
# =============================================================================

# Encode a sub (external_id) claim as base64url X-Userinfo header value
encode_userinfo(sub) := base64url.encode_no_pad(json.marshal({"sub": sub}))

# Encode full claims object as base64url X-Userinfo header value
encode_userinfo_full(claims) := base64url.encode_no_pad(json.marshal(claims))

# =============================================================================
# SAMPLE USER CONTEXTS FOR TESTING
# =============================================================================

# User with specific permissions at TENANT scope (for tenant-level resources)
user_with_permissions(perms) := {
    "userId": "test-user-123",
    "externalId": "test-user-123",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "TestRole",
            "roleType": "DATA",
            "scopeType": "TENANT",
            "scopeId": "tenant-1",
            "permissions": perms
        }]
    }]
}

# User with specific permissions at a specific scope (M5.1 scope enforcement)
user_with_scoped_permissions(perms, scope_type, scope_id) := {
    "userId": "test-user-123",
    "externalId": "test-user-123",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "TestRole",
            "roleType": "DATA",
            "scopeType": scope_type,
            "scopeId": scope_id,
            "permissions": perms
        }]
    }]
}

# User with no permissions (empty groups)
user_no_permissions := {
    "userId": "test-user-no-perms",
    "externalId": "test-user-no-perms",
    "groups": []
}

# Minimal user context (only externalId, no userId)
user_minimal := {
    "externalId": "test-user-minimal",
    "groups": []
}

# Admin user with TENANT-scoped permissions (for tenant-level resources like users)
user_admin := user_with_permissions([
    "READ_USER", "CREATE_USER", "UPDATE_USER", "DELETE_USER",
    "READ_GROUP", "CREATE_GROUP", "UPDATE_GROUP", "DELETE_GROUP",
    "READ_ROLE", "CREATE_ROLE", "UPDATE_ROLE", "DELETE_ROLE",
    "READ_PERMISSION", "CREATE_PERMISSION", "UPDATE_PERMISSION", "DELETE_PERMISSION",
    "READ_ASSIGNMENT", "CREATE_ASSIGNMENT", "UPDATE_ASSIGNMENT", "DELETE_ASSIGNMENT"
])

# Reader user with TENANT-scoped read permissions
user_reader := user_with_permissions([
    "READ_USER", "READ_GROUP", "READ_ROLE", "READ_PERMISSION", "READ_ASSIGNMENT"
])
