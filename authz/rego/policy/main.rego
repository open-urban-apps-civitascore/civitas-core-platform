# CIVITAS CORE AuthZ Policy - Entry Point
# M5: Full AuthZ integration with APISIX → OPA → AuthZ Repository chain
#
# OPA is the sole PDP (Policy Decision Point) for the platform.
# All authorization decisions flow through this policy.
#
# Request flow:
#   1. APISIX validates JWT, sets X-Userinfo header (base64-encoded claims)
#   2. OPA decodes X-Userinfo to extract subject (external user ID)
#   3. OPA fetches user_context from AuthZ Repository via http.send()
#   4. OPA evaluates permission against user_context
#   5. OPA returns {allow: true/false, reason: "..."}
#
# NOTE: Public endpoints are handled at APISIX level (routes without auth plugins).
# OPA only sees requests that require authorization.
#
# Decision flow:
#   1. Null-permission endpoint? → allow if authenticated (e.g., /users/me)
#   2. Permission required? → allow if user has permission
#   3. Otherwise → deny

package civitas.authz

import rego.v1

import data.civitas.authz.resource_mapping
import data.civitas.authz.permission_eval
import data.civitas.authz.user_context_fetcher

# Default deny - fail secure
default allow := false

# Decision result with reason for debugging
default decision := {"allow": false, "reason": "default_deny"}

# Main decision rule
decision := result if {
    result := evaluate_request
}

# Allow if user has required permission (includes null-permission endpoints)
allow if {
    permission_eval.has_permission
}

# =============================================================================
# DECISION EVALUATION
# =============================================================================

# Priority order: null-permission → permission check → deny

# 1. Null-permission endpoints (auth required, no specific permission)
# No scope header needed - these endpoints don't have permission-based filtering
evaluate_request := {"allow": true, "reason": "authenticated_endpoint"} if {
    permission_eval.is_null_permission_endpoint
    permission_eval.is_authenticated
}

# 2. Null-permission endpoint but not authenticated; 
# Separate case for more specific error message
evaluate_request := {"allow": false, "reason": "authentication_required"} if {
    permission_eval.is_null_permission_endpoint
    not permission_eval.is_authenticated
}

# 3. Missing user context (AuthZ Repository unavailable or fetch failed) - fail secure
# Must be checked BEFORE permission evaluation to avoid conflicts
evaluate_request := {"allow": false, "reason": "missing_user_context"} if {
    resource_mapping.backend != "unknown"
    permission_eval.is_known_endpoint
    not permission_eval.is_null_permission_endpoint
    not has_user_context
}

# 4. Permission-based access (regular protected endpoints)
# Include scope header for collection filtering (M5.5)
evaluate_request := result if {
    permission_eval.is_known_endpoint
    not permission_eval.is_null_permission_endpoint
    has_user_context
    permission_eval.has_permission
    result := {
        "allow": true,
        "reason": "permission_granted",
        "permission": permission_eval.required_permission,
        "headers": {
            "X-Allowed-Scope-Ids": allowed_scope_ids_header
        }
    }
}

# 5. Permission denied (user lacks required permission)
# Is a separate case for more specific error message
evaluate_request := {"allow": false, "reason": "permission_denied", "required": permission_eval.required_permission} if {
    permission_eval.is_known_endpoint
    not permission_eval.is_null_permission_endpoint
    has_user_context
    not permission_eval.has_permission
}

# 6. Unknown backend (no X-Authz-Backend header)
evaluate_request := {"allow": false, "reason": "unknown_backend"} if {
    resource_mapping.backend == "unknown"
}

# 7. Unknown endpoint (path not in backend's mappings) - fail secure
evaluate_request := {"allow": false, "reason": "unknown_endpoint"} if {
    resource_mapping.backend != "unknown"
    not permission_eval.is_known_endpoint
}

# =============================================================================
# SCOPE HEADER GENERATION (M5.5 - Collection Endpoint Filtering)
# =============================================================================
# Generate X-Allowed-Scope-Ids header for backend collection filtering.
# Backend uses this to filter list endpoints (e.g., GET /v2/datasets).
#
# Header values:
#   - "*"              : User has TENANT scope (wildcard - no filtering needed)
#   - "id1,id2,..."    : Comma-separated UUIDs for specific scope access
#   - ""               : No scopes (backend returns empty results)
#
# Note: For resource endpoints, backend uses existing scope enforcement (M5.1).
# This header enables efficient filtering for collection queries.

# Check if user has TENANT scope for the required permission
# TENANT scope acts as wildcard - user can see all resources
has_tenant_scope if {
    permission_eval.required_permission != ""
    some group in user_context_fetcher.user_context.groups
    some assignment in group.assignments
    permission_eval.required_permission in assignment.permissions
    assignment.scopeType == "TENANT"
}

# Collect specific scope IDs where user has the required permission
# Only includes scopes matching the expected scope type for the resource
specific_scope_ids contains scope_id if {
    permission_eval.required_permission != ""
    resource_mapping.expected_scope_type != ""
    some group in user_context_fetcher.user_context.groups
    some assignment in group.assignments
    permission_eval.required_permission in assignment.permissions
    assignment.scopeType == resource_mapping.expected_scope_type
    scope_id := assignment.scopeId
    scope_id != null
}

# Generate the header value based on user's scopes
default allowed_scope_ids_header := ""

# TENANT scope = wildcard (user can see everything)
allowed_scope_ids_header := "*" if {
    has_tenant_scope
}

# Specific scopes = comma-separated sorted IDs
allowed_scope_ids_header := concat(",", sort(specific_scope_ids)) if {
    not has_tenant_scope
    count(specific_scope_ids) > 0
}

# =============================================================================
# USER CONTEXT VALIDATION
# =============================================================================

# Check if user context is available (fetched from AuthZ Repository or provided in input)
# Uses the user_context_fetcher module which handles:
#   - Decoding X-Userinfo header from APISIX
#   - Fetching from AuthZ Repository via http.send()
#   - No fallback to input.user_context (removed per R-019 for TCB minimization)
#   - Tests mock http.send() using OPA's `with http.send as mock_fn` syntax
default has_user_context := false

has_user_context if {
    user_context_fetcher.user_context
    user_context_fetcher.user_context.userId != null
}

has_user_context if {
    user_context_fetcher.user_context
    user_context_fetcher.user_context.externalId != null
}

# =============================================================================
# DEBUGGING / LOGGING
# =============================================================================

# Expose computed values for debugging/logging
request_info := {
    "method": input.request.method,
    "path": input.request.path,
    "backend": resource_mapping.backend,
    "path_pattern": resource_mapping.path_pattern,
    "required_permission": permission_eval.required_permission,
    "is_null_permission": permission_eval.is_null_permission_endpoint,
    "user_context_source": user_context_fetcher.user_context_source,
}
