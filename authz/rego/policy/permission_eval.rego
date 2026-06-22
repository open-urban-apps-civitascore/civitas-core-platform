# CIVITAS CORE AuthZ Policy - Permission Evaluation
# M5: Resource-level permission checks with user_context_fetcher integration
#
# This module handles:
#   - Permission lookup from backend endpoint mappings
#   - Null-permission endpoints (auth required, no specific permission needed)
#   - Authentication checks (user context has identity)
#   - Permission evaluation (user has required permission in assignments)
#   - AND-permission support (endpoints requiring multiple permissions)
#
# User context is obtained via user_context_fetcher, which handles:
#   - Decoding X-Userinfo header from APISIX
#   - Fetching from AuthZ Repository via http.send()
#   - Tests mock http.send() using OPA's `with http.send as mock_fn` syntax
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

import data.civitas.authz.dataset_pool_fetcher
import data.civitas.authz.resource_mapping
import data.civitas.authz.user_context_fetcher

# =============================================================================
# PERMISSION LOOKUP FROM BACKEND MAPPINGS
# =============================================================================

# Look up required permissions from backend's endpoint mappings.
# Returns a set of permission strings. For single permissions (string in data.json),
# the set has one element. For AND-permissions (array in data.json), the set has
# multiple elements — ALL must be satisfied.
#
# Fail-secure design: if endpoint_config is undefined (no match), dependent rules
# don't fire → is_known_endpoint=false → main.rego denies with "unknown_endpoint".
# No explicit default needed; Rego's undefined semantics provide fail-secure behavior.

# Get the endpoint config for the matched path pattern
endpoint_config := resource_mapping.backend_endpoints[resource_mapping.path_pattern] if {
	resource_mapping.path_pattern != ""
}

# Check if this is a no-permission-required endpoint (null in mappings)
is_null_permission if {
	endpoint_config
	resource_mapping.request_method in object.keys(endpoint_config)
	endpoint_config[resource_mapping.request_method] == null
}

# Required permissions as a set.
# String permission → 1-element set. Array permission → multi-element set (AND).
required_permissions contains perm if {
	endpoint_config
	val := endpoint_config[resource_mapping.request_method]
	val != null
	is_string(val)
	perm := val
}

required_permissions contains perm if {
	endpoint_config
	val := endpoint_config[resource_mapping.request_method]
	val != null
	is_array(val)
	some perm in val
}

# =============================================================================
# ENDPOINT CLASSIFICATION
# =============================================================================

# Endpoint exists in backend mappings (fail-secure: unknown endpoints are denied)
default is_known_endpoint := false

is_known_endpoint if {
	count(required_permissions) > 0
}

is_known_endpoint if {
	is_null_permission
}

# Endpoints that require authentication but no specific permission
# (e.g., /users/me - any authenticated user can access their own data)
default is_null_permission_endpoint := false

is_null_permission_endpoint if {
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

is_authenticated if {
	user_context_fetcher.has_valid_identity
}

# =============================================================================
# PERMISSION EVALUATION
# =============================================================================

# Check if user has the required permission(s)
# For AND-permissions (arrays in data.json), ALL permissions must be present.
default has_permission := false

# Null-permission endpoints: allowed for any authenticated user
has_permission if {
	is_null_permission_endpoint
	is_authenticated
}

# User has permission if EVERY required permission is satisfied (AND semantics)
has_permission if {
	count(required_permissions) > 0
	every perm in required_permissions {
		user_has_permission(perm)
	}
}

# =============================================================================
# SCOPE ENFORCEMENT (M5.1)
# =============================================================================
# Check if user has a specific permission with matching scope.
#
# Scope matching rules:
#   - Resource endpoints: assignment.scopeId must match resource ID from path
#   - TENANT-scoped resources: assignment.scopeType must be TENANT (no scopeId check)
#   - Collection endpoints: TENANT scope or matching scope type required (Q-006: fail-secure)
#
# SCOPE INHERITANCE (Q-005 resolved): TENANT scope cascades to all resource
# endpoints per ADM spec. DATASPACE → child resource inheritance is deferred
# (dataspaces not implemented in v2.0).

# For collection endpoints with TENANT-scoped assignments (DATA roles)
user_has_permission(permission) if {
	resource_mapping.is_collection_endpoint
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	permission in assignment.permissions
	is_tenant_scoped(assignment)
}

# For collection endpoints with unscoped assignments (scopeType=null)
user_has_permission(permission) if {
	resource_mapping.is_collection_endpoint
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	permission in assignment.permissions
	is_unscoped(assignment)
}

# For collection endpoints with matching scope type: allowed
# (User has permission via scope type matching the resource's expected scope type)
user_has_permission(permission) if {
	resource_mapping.is_collection_endpoint
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	permission in assignment.permissions
	assignment.scopeType == resource_mapping.expected_scope_type
}

# For resource endpoints with TENANT-scoped resources (users, groups, roles, etc.):
# User must have a TENANT-scoped assignment with the required permission
user_has_permission(permission) if {
	resource_mapping.is_resource_endpoint
	resource_mapping.expected_scope_type == "TENANT"
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	permission in assignment.permissions
	is_tenant_scoped(assignment)
}

# For resource endpoints with TENANT-scoped resources:
# User must have an unscoped assignment (scopeType=null) with the required permission
user_has_permission(permission) if {
	resource_mapping.is_resource_endpoint
	resource_mapping.expected_scope_type == "TENANT"
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	permission in assignment.permissions
	is_unscoped(assignment)
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

# SCOPE INHERITANCE: TENANT-scoped assignments cascade to all resource
# endpoints (ADM spec), regardless of the resource's expected scope type.
# No upward inheritance — narrow scopes cannot access broader resources.
user_has_permission(permission) if {
	resource_mapping.is_resource_endpoint
	resource_mapping.expected_scope_type != "TENANT"
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	permission in assignment.permissions
	is_tenant_scoped(assignment)
}

# SCOPE INHERITANCE: Unscoped assignments (scopeType=null) cascade to all resource
# endpoints (ADM spec), regardless of the resource's expected scope type.
user_has_permission(permission) if {
	resource_mapping.is_resource_endpoint
	resource_mapping.expected_scope_type != "TENANT"
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	permission in assignment.permissions
	is_unscoped(assignment)
}

# =============================================================================
# DATAPOOL UNION INHERITANCE (Epic 1)
# =============================================================================
# A DATAPOOL-scoped grant applies to every dataset in that pool, in addition to
# direct dataset assignments (effective permissions are the UNION of the two).
#
# pool_of() (an http.send) only binds for DATAPOOL-scoped assignments, so a user
# with no DATAPOOL assignment never triggers the lookup. NOTE: OPA does not
# guarantee conjunct evaluation order, and a user who holds ANY DATAPOOL
# assignment on a dataset resource endpoint may trigger the lookup even when that
# grant lacks the requested permission. The lookup returns a single pool id,
# never a list, so cost is independent of pool size.
#
# Fail-secure: if the dataset-pool membership service is unavailable, pool_of()
# is undefined and this branch does not grant — direct/TENANT grants still apply.
# main.rego reports the resulting deny as the generic "permission_denied", so an
# outage is indistinguishable from a real lack of grant in the decision (see the
# observability note in main.rego).
#
# SCOPE NOTE (Epic-faithful, see Epics 1+2): collection classification is
# path-based and therefore method-agnostic, so the collection rule below also
# grants writes (e.g. POST /v1/datasets) to a qualifying pool grant. Per Epic 2,
# dataset create/edit follows "existing product rules" and the target datapool is
# an editable field — there is currently NO target-pool authorization on writes.
# This mirrors the pre-existing DATASET-scoped collection-create behavior. If
# stricter write semantics are desired, that is a deliberate decision beyond the
# current Epic spec.

# Resource endpoint: access to a single dataset is granted if the user holds the
# permission on the dataset's pool.
user_has_permission(permission) if {
	resource_mapping.is_resource_endpoint
	resource_mapping.expected_scope_type == "DATASET"
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	permission in assignment.permissions
	assignment.scopeType == "DATAPOOL"
	assignment.scopeId == dataset_pool_fetcher.pool_of(resource_mapping.resource_id)
}

# Collection endpoint: granted when a single datapool carries ALL required
# permissions (qualifying_datapool_ids). WHICH datasets are visible is narrowed
# by the X-Allowed-Pool-Ids header (main.rego), built from the SAME set — so the
# allow decision is always faithfully filterable, including for AND-permission
# endpoints.
user_has_permission(permission) if {
	resource_mapping.is_collection_endpoint
	some pool_id in qualifying_datapool_ids
	datapool_has_permission(permission, pool_id)
}

# DATAPOOL scope IDs that carry ALL required permissions for the dataset
# collection (Epic 1 union). Single source of truth shared with main.rego's
# X-Allowed-Pool-Ids header, so the allow-decision and the collection filter can
# never diverge (e.g. a perm split across two pools must not grant unfiltered
# access).
qualifying_datapool_ids contains pool_id if {
	count(required_permissions) > 0
	resource_mapping.is_collection_endpoint
	resource_mapping.expected_scope_type == "DATASET"
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	assignment.scopeType == "DATAPOOL"
	pool_id := assignment.scopeId
	pool_id != null

	# Only include this pool if it alone carries ALL required permissions
	every perm in required_permissions {
		datapool_has_permission(perm, pool_id)
	}
}

# Helper: does a DATAPOOL-scoped assignment for target_pool_id carry perm?
datapool_has_permission(perm, target_pool_id) if {
	some group in user_context_fetcher.user_context.groups
	some assignment in group.assignments
	perm in assignment.permissions
	assignment.scopeType == "DATAPOOL"
	assignment.scopeId == target_pool_id
}

# =============================================================================
# SCOPE CLASSIFICATION HELPERS
# =============================================================================
# Assignments are either scoped (with a scopeType like TENANT, DATASET, etc.)
# or unscoped (scopeType=null). For permission and scope evaluation, unscoped
# assignments grant the same tenant-wide access as scopeType=TENANT.

# TENANT-scoped: assignment with explicit tenant-wide scope
is_tenant_scoped(assignment) if {
	assignment.scopeType == "TENANT"
}

# Unscoped: assignment with no scope (scopeType=null)
is_unscoped(assignment) if {
	assignment.scopeType == null
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
