# Tests for main.rego - Entry point authorization decisions

package civitas.authz_test

import rego.v1

import data.civitas.authz

# =============================================================================
# TEST HELPERS
# =============================================================================

# Standard request with backend header
portal_request(method, path) := {
    "request": {
        "method": method,
        "path": path,
        "headers": {"x-authz-backend": "portal-backend"}
    }
}

# User context with specific permissions
user_context_with_permissions(perms) := {
    "userId": "user-1",
    "externalId": "keycloak-sub-1",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "Test Role",
            "roleType": "DATA",
            "scopeType": "TENANT",
            "scopeId": "tenant-1",
            "permissions": perms
        }]
    }]
}

# =============================================================================
# PERMISSION-BASED ACCESS TESTS
# =============================================================================

# Test: Authenticated user with correct permission is allowed
test_permission_granted if {
    input_data := object.union(portal_request("GET", "/v2/users"), {
        "user_context": user_context_with_permissions(["READ_USER", "CREATE_USER"])
    })
    result := authz.decision with input as input_data
    result.allowed == true
    result.reason == "permission_granted"
    result.permission == "READ_USER"
}

# Test: Authenticated user without required permission is denied
test_permission_denied if {
    input_data := object.union(portal_request("DELETE", "/v2/users/123"), {
        "user_context": user_context_with_permissions(["READ_USER"])
    })
    result := authz.decision with input as input_data
    result.allowed == false
    result.reason == "permission_denied"
    result.required == "DELETE_USER"
}

# Test: POST to collection requires CREATE permission
test_create_permission if {
    input_data := object.union(portal_request("POST", "/v2/datasets"), {
        "user_context": user_context_with_permissions(["CREATE_DATASET"])
    })
    result := authz.decision with input as input_data
    result.allowed == true
    result.reason == "permission_granted"
    result.permission == "CREATE_DATASET"
}

# Test: PUT to resource requires UPDATE permission
test_update_permission if {
    input_data := object.union(portal_request("PUT", "/v2/dataspaces/abc-123"), {
        "user_context": user_context_with_permissions(["UPDATE_DATASPACE"])
    })
    result := authz.decision with input as input_data
    result.allowed == true
    result.reason == "permission_granted"
}

# =============================================================================
# NULL-PERMISSION (SELF-ACCESS) TESTS
# =============================================================================

# Test: /users/me allowed for any authenticated user
test_users_me_allowed_for_authenticated if {
    input_data := object.union(portal_request("GET", "/v2/users/me"), {
        "user_context": {
            "userId": "123",
            "externalId": "keycloak-sub-123",
            "groups": []
        }
    })
    result := authz.decision with input as input_data
    result.allowed == true
    result.reason == "authenticated_endpoint"
}

# Test: /users/me denied for unauthenticated request
test_users_me_denied_without_auth if {
    result := authz.decision with input as object.union(portal_request("GET", "/v2/users/me"), {
        "user_context": {}
    })
    result.allowed == false
    result.reason == "authentication_required"
}

# =============================================================================
# ERROR CASE TESTS
# =============================================================================

# Test: No user context results in denial
test_no_user_context_denied if {
    input_data := object.union(portal_request("GET", "/v2/datasets"), {
        "user_context": {}
    })
    result := authz.decision with input as input_data
    result.allowed == false
}

# Test: Unknown endpoint path results in denial
test_unknown_endpoint_denied if {
    input_data := object.union(portal_request("GET", "/v2/unknown-resource"), {
        "user_context": user_context_with_permissions(["READ_USER", "READ_DATASET"])
    })
    result := authz.decision with input as input_data
    result.allowed == false
    result.reason == "unknown_endpoint"
}

# Test: Missing backend header results in denial
test_unknown_backend_denied if {
    result := authz.decision with input as {
        "request": {
            "method": "GET",
            "path": "/v2/users",
            "headers": {}
        },
        "user_context": user_context_with_permissions(["READ_USER"])
    }
    result.allowed == false
    result.reason == "unknown_backend"
}

# Test: Default deny when no rules match
test_default_deny if {
    authz.allow == false with input as {}
}
