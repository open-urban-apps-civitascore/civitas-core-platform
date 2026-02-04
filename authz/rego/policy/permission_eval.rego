# CIVITAS CORE AuthZ Policy - Permission Evaluation
# M5: Resource-level permission checks with user_context_fetcher integration
#
# This module handles:
#   - Permission lookup from backend endpoint mappings
#   - Null-permission endpoints (auth required, no specific permission needed)
#   - Authentication checks (user context has identity)
#   - Permission evaluation (user has required permission in assignments)
#
# User context is obtained via user_context_fetcher, which handles:
#   - Decoding X-Userinfo header from APISIX
#   - Fetching from AuthZ Repository via http.send()
#   - Fallback to input.user_context for testing
#
# main.rego uses these rules to produce final allow/deny decisions with reasons.
# Public endpoints are handled at APISIX level (never reach OPA).

# [R-021] Why no roles in this code?
# The AuthZ Repository's /user-context/{externalId} endpoint returns PRE-FLATTENED
# data with permissions already resolved. Role→permission mapping happens in the
# AuthZ Repository (Java), not in Rego. Benefits:
#   - Simpler Rego (no role lookup logic)
#   - More efficient (one API call returns everything)
#   - Immediate updates (role changes don't require Rego redeployment)
# The response structure: groups[].assignments[].permissions[] (roles resolved)

package civitas.authz.permission_eval

import rego.v1

import data.civitas.authz.resource_mapping
import data.civitas.authz.user_context_fetcher

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
# [R-022] is_authenticated vs main.rego's has_user_context:
# - has_user_context (main.rego): Used to fail-secure when AuthZ Repository is down
# - is_authenticated (here): Used specifically for null-permission endpoints
#
# The null-permission flow (e.g., /users/me):
#   is_null_permission_endpoint=true + is_authenticated=true → has_permission=true
# This check enables "any authenticated user can access" endpoints without
# requiring a specific permission. Both checks verify "user_context exists with
# identity" but serve different decision branches.
#
# User is authenticated if we have user context with identity
# Uses user_context_fetcher to get user context (fetched or from input)
default is_authenticated := false

is_authenticated := true if {
    user_context_fetcher.user_context.userId != null
}

is_authenticated := true if {
    user_context_fetcher.user_context.externalId != null
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

# User has permission if it's in their assignments with matching scope (M5.1)
has_permission := true if {
    required_permission != ""
    user_has_permission(required_permission)
}

# =============================================================================
# SCOPE ENFORCEMENT (M5.1)
# =============================================================================
# Check if user has a specific permission with matching scope.
#
# Scope matching rules:
#   - Resource endpoints: assignment.scopeId must match resource ID from path
#   - TENANT-scoped resources: assignment.scopeType must be TENANT (no scopeId check)
#   - Collection endpoints: any matching permission (filtering is backend's job)
#
# See Q-005 in BACKLOG.md: Pending PO clarification on whether TENANT scope
# should act as wildcard (access all) or only for tenant-level resources.

# For collection endpoints: permission in any scope is sufficient
# (Backend filters results based on user's scopes)
user_has_permission(permission) if {
    resource_mapping.is_collection_endpoint
    some group in user_context_fetcher.user_context.groups
    some assignment in group.assignments
    permission in assignment.permissions
}

# For resource endpoints with TENANT-scoped resources (users, groups, roles, etc.):
# User must have permission with scopeType=TENANT
user_has_permission(permission) if {
    resource_mapping.is_resource_endpoint
    resource_mapping.expected_scope_type == "TENANT"
    some group in user_context_fetcher.user_context.groups
    some assignment in group.assignments
    permission in assignment.permissions
    assignment.scopeType == "TENANT"
}

# For resource endpoints with DATASPACE/DATASET resources:
# User must have permission with matching scopeType AND scopeId
user_has_permission(permission) if {
    resource_mapping.is_resource_endpoint
    resource_mapping.expected_scope_type != "TENANT"
    some group in user_context_fetcher.user_context.groups
    some assignment in group.assignments
    permission in assignment.permissions
    assignment.scopeType == resource_mapping.expected_scope_type
    assignment.scopeId == resource_mapping.resource_id
}

# =============================================================================
# DEBUGGING HELPERS
# =============================================================================

# Collect all user permissions (for debugging/logging)
# Used by tests; useful for troubleshooting permission issues
# Uses user_context_fetcher to get user context (fetched or from input)
all_user_permissions contains permission if {
    some group in user_context_fetcher.user_context.groups
    some assignment in group.assignments
    some permission in assignment.permissions
}
