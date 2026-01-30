# CIVITAS CORE AuthZ Policy - Entry Point
# M4: Resource-level permission evaluation (no scope checks - see M4.5)
#
# OPA is the sole PDP (Policy Decision Point) for the platform.
# All authorization decisions flow through this policy.
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

# Default deny - fail secure
default allow := false

# Decision result with reason for debugging
default decision := {"allowed": false, "reason": "default_deny"}

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
evaluate_request := {"allowed": true, "reason": "authenticated_endpoint"} if {
    permission_eval.is_null_permission_endpoint
    permission_eval.is_authenticated
}

# 2. Null-permission endpoint but not authenticated; 
# Is a seperate case for more specific error message, functionally redundant
evaluate_request := {"allowed": false, "reason": "authentication_required"} if {
    permission_eval.is_null_permission_endpoint
    not permission_eval.is_authenticated
}

# 3. Permission-based access (regular protected endpoints)
evaluate_request := {"allowed": true, "reason": "permission_granted", "permission": permission_eval.required_permission} if {
    permission_eval.is_known_endpoint
    not permission_eval.is_null_permission_endpoint
    permission_eval.has_permission
}

# 4. Permission denied (user lacks required permission)
# Is a seperate case for more specific error message, functionally redundant
evaluate_request := {"allowed": false, "reason": "permission_denied", "required": permission_eval.required_permission} if {
    permission_eval.is_known_endpoint
    not permission_eval.is_null_permission_endpoint
    not permission_eval.has_permission
}

# 5. Unknown backend (no X-Authz-Backend header)
evaluate_request := {"allowed": false, "reason": "unknown_backend"} if {
    resource_mapping.backend == "unknown"
}

# 6. Unknown endpoint (path not in backend's mappings) - fail secure
evaluate_request := {"allowed": false, "reason": "unknown_endpoint"} if {
    resource_mapping.backend != "unknown"
    not permission_eval.is_known_endpoint
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
}
