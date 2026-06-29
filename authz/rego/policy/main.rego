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

import data.civitas.authz.permission_eval
import data.civitas.authz.resource_mapping
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

# IMPORTANT: These rules use Rego incremental definitions for the same variable
# (evaluate_request). They MUST be mutually exclusive — if two rules fire with
# different values, OPA raises a runtime conflict error, not a priority-based
# resolution. The "priority" numbering below is for human readability only.
#
# Mutual exclusivity is guaranteed by these conditions:
#   - Rules 1,2 require is_null_permission_endpoint; rules 3-6 require NOT
#   - Rules 1 vs 2: is_authenticated vs not is_authenticated
#   - Rules 4 vs 5: not is_unscoped_only vs is_unscoped_only
#   - Rules 4,5 vs 6: has_permission vs not has_permission
#   - Rules 3 vs 4,5,6: not has_user_context vs has_user_context
#   - Rule 7: backend == "unknown" (rules 3,8 require backend != "unknown";
#     rules 1,2,4,5,6 require is_known_endpoint which is false when backend is unknown)
#   - Rule 8: not is_known_endpoint (rules 1-6 require is_known_endpoint or
#     is_null_permission_endpoint, which implies is_known_endpoint)
#
# If adding new rules or providers, verify mutual exclusivity is preserved.
# Consider refactoring to an `else` chain if the conditions become harder to
# reason about — that would give true priority ordering enforced by OPA.

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

# 4. Scoped access granted (TENANT or specific scopes)
# Includes scope header for backend collection filtering (M5.5)
evaluate_request := result if {
	permission_eval.is_known_endpoint
	not permission_eval.is_null_permission_endpoint
	has_user_context
	permission_eval.has_permission
	not is_unscoped_only
	result := {
		"allow": true,
		"reason": "permission_granted",
		"required_permissions": permission_eval.required_permissions,
		"headers": object.union(
			{"X-Allowed-Scope-Ids": allowed_scope_ids_header},
			pool_ids_header,
		),
	}
}

# 5. Permission granted but with no expressible scope (no wildcard / specific / pool)
# — no scope header is emitted. Tenant-wide & unscoped grants do NOT take this path;
# they emit "X-Allowed-Scope-Ids: *" via rule 4 (the backend 403s on a missing header).
evaluate_request := result if {
	permission_eval.is_known_endpoint
	not permission_eval.is_null_permission_endpoint
	has_user_context
	permission_eval.has_permission
	is_unscoped_only
	result := {
		"allow": true,
		"reason": "permission_granted",
		"required_permissions": permission_eval.required_permissions,
	}
}

# 6. Permission denied (user lacks required permission)
# Is a separate case for more specific error message
evaluate_request := {"allow": false, "reason": "permission_denied", "required_permissions": permission_eval.required_permissions} if {
	permission_eval.is_known_endpoint
	not permission_eval.is_null_permission_endpoint
	has_user_context
	not permission_eval.has_permission
}

# 7. Unknown backend (no APISIX service metadata or unknown service name)
evaluate_request := {"allow": false, "reason": "unknown_backend"} if {
	resource_mapping.backend == "unknown"
}

# 8. Unknown endpoint (path not in backend's mappings) - fail secure
evaluate_request := {"allow": false, "reason": "unknown_endpoint"} if {
	resource_mapping.backend != "unknown"
	not permission_eval.is_known_endpoint
}

# =============================================================================
# SCOPE HEADER GENERATION (M5.5 - Collection Endpoint Filtering)
# =============================================================================
# Generate X-Allowed-Scope-Ids header for backend collection filtering.
# Backend uses this to filter list endpoints (e.g., GET /v1/datasets).
#
# Header values:
#   - "*"              : User has TENANT scope for all required permissions (collection-level wildcard)
#   - "id1,id2,..."    : Comma-separated UUIDs for specific scope access
#   - (no header)      : Unscoped access (scopeType=null) — rule 5 omits the header entirely
#
# Note: For resource endpoints, backend uses existing scope enforcement (M5.1).
# This header enables efficient filtering for collection queries.
#
# AND-permission support: For endpoints requiring multiple permissions,
# TENANT scope is granted only if ALL required permissions have TENANT scope.
# Specific scope IDs are included only if ALL required permissions are available
# for that specific scope ID.

# WILDCARD scope: the user holds tenant-wide access for ALL required permissions,
# via an explicit TENANT scope OR an unscoped (scopeType=null) assignment. Both grant
# tenant-wide access (see permission_eval scope classification), so OPA emits
# "X-Allowed-Scope-Ids: *" and the backend skips filtering. This DOMINATES any narrower
# DATASET/DATAPOOL grant the user also happens to hold.
#
# Emitted as an EXPLICIT "*", never an omitted header: the backend rejects a DataEntity
# request that arrives WITHOUT X-Allowed-Scope-Ids with 403, so a tenant-wide reader
# (e.g. unscoped DATASET_READ + an incidental pool grant) needs the wildcard, not an
# absent header. Also cascades to resource endpoints via scope inheritance (Q-005).
has_wildcard_scope if {
	count(permission_eval.required_permissions) > 0
	every perm in permission_eval.required_permissions {
		has_wildcard_scoped_permission(perm)
	}
}

# Helper: a single permission granted tenant-wide via an explicit TENANT scope ...
has_wildcard_scoped_permission(perm) if {
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	perm in assignment.permissions
	permission_eval.is_tenant_scoped(assignment)
}

# ... or via an unscoped (scopeType=null) assignment, which is likewise tenant-wide.
has_wildcard_scoped_permission(perm) if {
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	perm in assignment.permissions
	permission_eval.is_unscoped(assignment)
}

# User has permission but NO expressible scope: not wildcard, no specific scope IDs,
# and no pool grant. Only then is no scope header emitted. This stays deliberately
# narrow — tenant-wide/unscoped access takes the has_wildcard_scope ("*") path above,
# so the headerless branch is never hit for a legitimate tenant-wide reader (which the
# backend would otherwise 403 on a missing X-Allowed-Scope-Ids).
is_unscoped_only if {
	not has_wildcard_scope
	count(specific_scope_ids) == 0
	count(allowed_pool_ids) == 0
}

# Collect specific scope IDs where user has ALL required permissions (AND semantics)
# Only includes scopes matching the expected scope type for the resource
specific_scope_ids contains scope_id if {
	count(permission_eval.required_permissions) > 0
	resource_mapping.expected_scope_type != ""
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	assignment.scopeType == resource_mapping.expected_scope_type
	scope_id := assignment.scopeId
	scope_id != null

	# Only include this scope_id if ALL required permissions are available for it
	every perm in permission_eval.required_permissions {
		scope_has_permission(perm, scope_id)
	}
}

# Helper: check if a single permission exists for a specific scope ID
scope_has_permission(perm, target_scope_id) if {
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	perm in assignment.permissions
	assignment.scopeType == resource_mapping.expected_scope_type
	assignment.scopeId == target_scope_id
}

# DATAPOOL scope IDs the user may use for the dataset COLLECTION (Epic 1 union).
# Single source of truth: permission_eval.qualifying_datapool_ids (a pool must
# carry ALL required permissions), so the X-Allowed-Pool-Ids header and the
# allow-decision can never diverge. The backend ORs these into its list filter
# (datapool_id IN (...)), so OPA passes only the small set of granted pool ids —
# never an enumerated list of dataset ids.
allowed_pool_ids := permission_eval.qualifying_datapool_ids

# Generate the header value based on user's scopes
default allowed_scope_ids_header := ""

# Wildcard (TENANT or unscoped tenant-wide) = "*"; backend skips filtering.
allowed_scope_ids_header := "*" if {
	has_wildcard_scope
}

# Specific scopes = comma-separated sorted IDs
allowed_scope_ids_header := concat(",", sort(specific_scope_ids)) if {
	not has_wildcard_scope
	count(specific_scope_ids) > 0
}

# X-Allowed-Pool-Ids header (Epic 1 union, collection filtering).
# Present only when the user has dataset-relevant DATAPOOL grants; the backend
# ORs it into the collection filter as `datapool_id IN (<ids>)`. Built as a
# separate object so the header is OMITTED ENTIRELY when there are no pool grants.
# This omission is security-relevant: emitting an empty "X-Allowed-Pool-Ids: ""
# instead would cause AllowedScopesFilter to mark the request scoped, flipping a
# pool-less user from unscoped to scoped-with-no-pools. Keep the omit semantics.
pool_ids_header := {"X-Allowed-Pool-Ids": concat(",", sort(allowed_pool_ids))} if {
	count(allowed_pool_ids) > 0
}

pool_ids_header := {} if {
	count(allowed_pool_ids) == 0
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
	user_context_fetcher.has_valid_identity
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
	"required_permissions": permission_eval.required_permissions,
	"is_null_permission": permission_eval.is_null_permission_endpoint,
	"user_context_source": user_context_fetcher.user_context_source,
}
