# CIVITAS CORE AuthZ Policy - Permission Evaluation
# M4: Resource-level permission checks (no scope enforcement - see M4.5)
#
# This module handles:
#   - Permission lookup from backend endpoint mappings
#   - Null-permission endpoints (auth required, no specific permission needed)
#   - Authentication checks (user context has identity)
#   - Permission evaluation (user has required permission in assignments)
#
# main.rego uses these rules to produce final allow/deny decisions with reasons.
# Public endpoints are handled at APISIX level (never reach OPA). 

package civitas.authz.permission_eval

import rego.v1

import data.civitas.authz.resource_mapping

# =============================================================================
# PERMISSION LOOKUP FROM BACKEND MAPPINGS
# =============================================================================

# Look up required permission from backend's endpoint mappings.
# Returns the permission string, null (no permission needed), or "" (not found).
#
# Fail-secure design: if endpoint_config is undefined (no match), dependent rules
# don't fire → is_known_endpoint=false → main.rego denies with "unknown_endpoint".
# No explicit default needed; Rego's undefined semantics provide fail-secure behavior.

# Get the endpoint config for the matched path pattern
endpoint_config := resource_mapping.backend_endpoints[resource_mapping.path_pattern] if {
    resource_mapping.path_pattern != ""
}

# Get permission for the specific HTTP method
# Note: JSON null becomes rego null, missing key returns undefined
method_permission := endpoint_config[resource_mapping.request_method] if {
    endpoint_config
    endpoint_config[resource_mapping.request_method] != null
}

# Check if this is a no-permission-required endpoint (null in mappings)
is_null_permission if {
    endpoint_config
    resource_mapping.request_method in object.keys(endpoint_config)
    endpoint_config[resource_mapping.request_method] == null
}

# Required permission: the permission string from mappings, or "" if not found
default required_permission := ""

required_permission := method_permission if {
    method_permission
}

# =============================================================================
# ENDPOINT CLASSIFICATION
# =============================================================================

# Endpoint exists in backend mappings (fail-secure: unknown endpoints are denied)
default is_known_endpoint := false

is_known_endpoint := true if {
    required_permission != ""
}

is_known_endpoint := true if {
    is_null_permission
}

# Endpoints that require authentication but no specific permission
# (e.g., /users/me - any authenticated user can access their own data)
default is_null_permission_endpoint := false

is_null_permission_endpoint := true if {
    is_null_permission
}

# =============================================================================
# AUTHENTICATION CHECK
# =============================================================================

# User is authenticated if we have user context with identity
default is_authenticated := false

is_authenticated := true if {
    input.user_context.userId != null
}

is_authenticated := true if {
    input.user_context.externalId != null
}

# =============================================================================
# PERMISSION EVALUATION
# =============================================================================

# Check if user has the required permission
default has_permission := false

# Null-permission endpoints: allowed for any authenticated user
has_permission := true if {
    is_null_permission_endpoint
    is_authenticated
}

# User has permission if it's in their assignments (any scope - M4)
has_permission := true if {
    required_permission != ""
    user_has_permission(required_permission)
}

# Check if user has a specific permission in any of their assignments
user_has_permission(permission) if {
    some group in input.user_context.groups
    some assignment in group.assignments
    permission in assignment.permissions
}

# =============================================================================
# DEBUGGING HELPERS
# =============================================================================

# Collect all user permissions (for debugging/logging)
# Used by tests; useful for troubleshooting permission issues
all_user_permissions contains permission if {
    some group in input.user_context.groups
    some assignment in group.assignments
    some permission in assignment.permissions
}
