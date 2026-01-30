# Tests for permission_eval.rego - Permission lookup and evaluation

package civitas.authz.permission_eval_test

import rego.v1

import data.civitas.authz.permission_eval

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

user_with_permissions(perms) := {
    "userId": "user-1",
    "externalId": "ext-1",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "Test Role",
            "roleType": "DATA",
            "scopeType": "DATASPACE",
            "scopeId": "ds-1",
            "permissions": perms
        }]
    }]
}

# =============================================================================
# PERMISSION LOOKUP TESTS
# =============================================================================

# Test: Permission lookup from mappings - GET collection
test_required_permission_read_user if {
    result := permission_eval.required_permission with input as portal_request("GET", "/v2/users")
    result == "READ_USER"
}

# Test: Permission lookup from mappings - POST collection
test_required_permission_create_dataset if {
    result := permission_eval.required_permission with input as portal_request("POST", "/v2/datasets")
    result == "CREATE_DATASET"
}

# Test: Permission lookup from mappings - PUT resource
test_required_permission_update_dataspace if {
    result := permission_eval.required_permission with input as portal_request("PUT", "/v2/dataspaces/123")
    result == "UPDATE_DATASPACE"
}

# Test: Permission lookup from mappings - DELETE resource
test_required_permission_delete_group if {
    result := permission_eval.required_permission with input as portal_request("DELETE", "/v2/groups/abc")
    result == "DELETE_GROUP"
}

# Test: Permission lookup from mappings - PATCH resource
test_required_permission_patch_role if {
    result := permission_eval.required_permission with input as portal_request("PATCH", "/v2/roles/r1")
    result == "UPDATE_ROLE"
}

# =============================================================================
# NULL-PERMISSION ENDPOINT TESTS
# =============================================================================

# Test: /users/me has null permission (no permission required)
test_users_me_null_permission if {
    result := permission_eval.is_null_permission_endpoint with input as portal_request("GET", "/v2/users/me")
    result == true
}

# Test: /users/me required_permission is empty string
test_users_me_no_required_permission if {
    result := permission_eval.required_permission with input as portal_request("GET", "/v2/users/me")
    result == ""
}

# Test: Regular endpoint is not null-permission
test_regular_endpoint_not_null_permission if {
    result := permission_eval.is_null_permission_endpoint with input as portal_request("GET", "/v2/users")
    result == false
}

# =============================================================================
# KNOWN ENDPOINT TESTS (fail-secure: unknown endpoints denied)
# =============================================================================

# Test: Endpoint with permission is known
test_is_known_endpoint_with_permission if {
    result := permission_eval.is_known_endpoint with input as portal_request("GET", "/v2/users")
    result == true
}

# Test: Null-permission endpoint is known
test_is_known_endpoint_null_permission if {
    result := permission_eval.is_known_endpoint with input as portal_request("GET", "/v2/users/me")
    result == true
}

# Test: Unknown endpoint is not known (fail-secure)
test_is_known_endpoint_unknown if {
    result := permission_eval.is_known_endpoint with input as portal_request("GET", "/v2/foobar")
    result == false
}

# =============================================================================
# PERMISSION EVALUATION TESTS
# =============================================================================

# Test: User with matching permission
test_has_permission_matching if {
    input_data := object.union(portal_request("GET", "/v2/datasets"), {
        "user_context": user_with_permissions(["READ_DATASET", "CREATE_DATASET"])
    })
    result := permission_eval.has_permission with input as input_data
    result == true
}

# Test: User without matching permission
test_has_permission_not_matching if {
    input_data := object.union(portal_request("DELETE", "/v2/datasets/123"), {
        "user_context": user_with_permissions(["READ_DATASET"])
    })
    result := permission_eval.has_permission with input as input_data
    result == false
}

# Test: User with no permissions
test_has_permission_empty if {
    input_data := object.union(portal_request("GET", "/v2/users"), {
        "user_context": user_with_permissions([])
    })
    result := permission_eval.has_permission with input as input_data
    result == false
}

# Test: User with permission in different group
test_has_permission_multiple_groups if {
    input_data := object.union(portal_request("POST", "/v2/datasets"), {
        "user_context": {
            "userId": "user-1",
            "externalId": "ext-1",
            "groups": [
                {
                    "id": "group-1",
                    "name": "Group 1",
                    "assignments": [{
                        "roleId": "role-1",
                        "roleName": "Reader",
                        "roleType": "DATA",
                        "scopeType": "DATASPACE",
                        "scopeId": "ds-1",
                        "permissions": ["READ_DATASET"]
                    }]
                },
                {
                    "id": "group-2",
                    "name": "Group 2",
                    "assignments": [{
                        "roleId": "role-2",
                        "roleName": "Creator",
                        "roleType": "DATA",
                        "scopeType": "DATASPACE",
                        "scopeId": "ds-2",
                        "permissions": ["CREATE_DATASET"]
                    }]
                }
            ]
        }
    })
    result := permission_eval.has_permission with input as input_data
    result == true
}

# Test: /users/me allowed for authenticated user without explicit permission
test_users_me_allowed_authenticated if {
    input_data := object.union(portal_request("GET", "/v2/users/me"), {
        "user_context": {
            "userId": "user-1",
            "externalId": "ext-1",
            "groups": []
        }
    })
    result := permission_eval.has_permission with input as input_data
    result == true
}

# Test: User with no groups
test_has_permission_no_groups if {
    input_data := object.union(portal_request("GET", "/v2/users"), {
        "user_context": {
            "userId": "user-1",
            "externalId": "ext-1",
            "groups": []
        }
    })
    result := permission_eval.has_permission with input as input_data
    result == false
}

# =============================================================================
# PERMISSION COLLECTION TESTS
# =============================================================================

test_all_user_permissions if {
    input_data := object.union(portal_request("GET", "/v2/users"), {
        "user_context": {
            "userId": "user-1",
            "externalId": "ext-1",
            "groups": [{
                "id": "group-1",
                "name": "Test",
                "assignments": [
                    {
                        "roleId": "role-1",
                        "roleName": "Role1",
                        "roleType": "DATA",
                        "scopeType": "TENANT",
                        "scopeId": "t-1",
                        "permissions": ["READ_USER", "CREATE_USER"]
                    },
                    {
                        "roleId": "role-2",
                        "roleName": "Role2",
                        "roleType": "DATA",
                        "scopeType": "DATASPACE",
                        "scopeId": "ds-1",
                        "permissions": ["READ_DATASET"]
                    }
                ]
            }]
        }
    })
    result := permission_eval.all_user_permissions with input as input_data
    result == {"READ_USER", "CREATE_USER", "READ_DATASET"}
}

# =============================================================================
# AUTHENTICATION TESTS
# =============================================================================

test_is_authenticated_with_user_id if {
    result := permission_eval.is_authenticated with input as {
        "user_context": {"userId": "123"}
    }
    result == true
}

test_is_authenticated_with_external_id if {
    result := permission_eval.is_authenticated with input as {
        "user_context": {"externalId": "ext-123"}
    }
    result == true
}

test_is_not_authenticated_empty_context if {
    result := permission_eval.is_authenticated with input as {
        "user_context": {}
    }
    result == false
}
