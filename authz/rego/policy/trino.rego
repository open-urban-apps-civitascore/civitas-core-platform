# CIVITAS CORE AuthZ - Trino Entry Point
# Evaluates Trino system access control requests.
#
# Trino is configured with:
#   access-control.name=opa
#   opa.policy.uri=http://civitas-opa:8181/v1/data/civitas/trino/allow
#
# Trino calls this single endpoint for every action, with the operation named
# in input.action.operation and the input.context.identity.user already
# resolved to the effective (post-impersonation) identity — see
# CivitasSupersetSecurityManager, which sets X-Trino-User to the Keycloak sub,
# not a display username, for exactly this reason (Superset's Trino connection
# has impersonate_user=True; see dev-environment/trino-superset/superset/
# bootstrap_superset.py).
#
# NOT covered here: a human logging into the Trino web UI directly (rather than
# via Superset) authenticates via Trino's own oauth2 config, whose
# principal-field is preferred_username, not sub — that identity would not
# resolve via user_context_fetcher (which expects a Keycloak sub) and is
# out of scope for this policy; Superset-mediated access is the only path this
# implements.
#
# EMPIRICAL VALIDATION NEEDED: the resource shapes below (e.g.
# action.resource.table.schemaName) reflect the standard Trino OPA
# access-control plugin contract, but have not been confirmed against a live
# Trino 481 instance. start-trino.sh already sets opa.log-requests=true —
# check civitas-opa's own logs for the raw request body on a real query and
# adjust the resource.* paths below if they don't match.
#
# ALLOW-ALL MODE: as with superset.rego, access is additionally gated on which
# backend data is mounted at data.backends.trino (see generate-allowall.sh and
# OPA_DATA_DIR in docker-compose.authz.yml):
#   - backends-allowall data (data_permissions == null) → allow every request.
#   - real backends data (data_permissions is a concrete array) → real checks
#     below apply.
#   - missing/unmounted backend data → deny (fail-secure).
package civitas.trino

import rego.v1

import data.civitas.authz.dataset_pool_fetcher
import data.civitas.authz.user_context_fetcher

# =============================================================================
# BACKEND ENABLEMENT SWITCH
# =============================================================================
default _allow_all := false

_allow_all if {
	is_null(data.backends.trino.data_permissions)
}

# =============================================================================
# TRUSTED IMPERSONATORS
# =============================================================================
# Service accounts allowed to impersonate any resolvable Civitas user via
# Trino's ImpersonateUser check. Today only Superset's own Trino connection
# user (TRINO_SUPERSET_SERVICE_USER in dev-environment/trino-superset/.env).
# Configurable so a deployment can add/rename accounts without a policy
# change — see user_context_fetcher.rego for the same data.config.* pattern.
default trusted_impersonators := {"superset_trino"}

trusted_impersonators := {u |
	some u in data.config.trino_impersonator_users
} if {
	data.config.trino_impersonator_users
}

# =============================================================================
# LOCAL SERVICE IDENTITY BYPASS (config-adapter's Trino principal)
# =============================================================================
# The config-adapter saga provisions every dataset itself (schema/table creation, then Superset
# dataset registration), so its Trino principal must be able to introspect the dataset before an
# end-user permission grant can exist. This is a Trino data-plane exception only; Superset
# dashboard/chart access is now managed through native Subjects.
# This identity is a Superset-local account, not a real Keycloak-backed Civitas user, so it can
# never resolve via user_context_fetcher/is_authenticated the normal way — it needs its own
# explicit bypass, exactly like trusted_impersonators.
default local_admin_user := "civitas-config-adapter"

local_admin_user := data.config.trino_local_admin_user if {
	data.config.trino_local_admin_user
}

is_local_admin if {
	local_admin_user != ""
	input.context.identity.user == local_admin_user
}

# =============================================================================
# USER CONTEXT (effective, post-impersonation identity)
# =============================================================================
# Mirrors superset.rego: Trino has already resolved the caller to a Keycloak
# sub, so no X-Userinfo header to decode — call fetch_context directly, as
# user_context_fetcher.rego's own comment anticipates for the Trino path.
user_context := user_context_fetcher.fetch_context(input.context.identity.user) if {
	input.context.identity.user
	user_context_fetcher.fetch_context(input.context.identity.user).userId != null
}

default is_authenticated := false

is_authenticated if {
	user_context.userId != null
}

# =============================================================================
# PERMISSION EVALUATION
# =============================================================================
# Mirrors superset.rego's has_permission/has_tenant_permission shape (same
# civitasDatasetId-keyed model) rather than importing it: the two policies
# evaluate independent engines with no shared caller today. Extract to a
# shared lib if a third consumer needs the same shape.

# Deliberately excludes scopeType == null (a SYSTEM role assignment, e.g. Tenant Admin) — unlike
# permission_eval.rego's REST API scope inheritance, where an unscoped SYSTEM role is meant to
# cascade to every resource. A SYSTEM role can never hold a DATA permission (RoleService rejects
# it), so this permission set would never contain one anyway; but not filtering scopeType here
# would leave open a silent tenant-wide leak the moment that invariant is violated (e.g. by
# RoleInitializer's seed path, which bypasses that check).
has_tenant_permission(permission) if {
	some group in user_context.groups
	some a in group.assignments
	permission in a.permissions
	a.scopeType == "TENANT"
}

has_permission(permission, _) if {
	has_tenant_permission(permission)
}

has_permission(permission, dataset_id) if {
	dataset_id
	some group in user_context.groups
	some a in group.assignments
	permission in a.permissions
	a.scopeType == "DATASET"
	a.scopeId == dataset_id
}

# DATAPOOL union inheritance (Epic 1), mirroring superset.rego/permission_eval.rego.
has_permission(permission, dataset_id) if {
	dataset_id
	some group in user_context.groups
	some a in group.assignments
	permission in a.permissions
	a.scopeType == "DATAPOOL"
	a.scopeId == dataset_pool_fetcher.pool_of(dataset_id)
}

# =============================================================================
# SCHEMA/TABLE RESOURCE RESOLUTION
# =============================================================================
# The schema name Trino sends is the only handle we have on which dataset a
# request targets. Decoded locally rather than via the AuthZ Repository: this
# is safe specifically because Civitas dataset ids are always canonical UUIDs
# — unlike the general case WorkspaceNames.fromDatasetId's javadoc warns about
# ("distinct ids can collapse... callers must ensure uniqueness"), the
# transform only ever replaces the 4 fixed hyphen positions with underscores,
# so it is injective over this restricted domain. "ds_" as a literal prefix
# is also unambiguous: a UUID's first two hex characters are always
# [0-9a-f][0-9a-f], and "s" is never a valid hex digit, so a decoded UUID can
# never itself start with "ds" — there is no case where the prefix could be
# organic content rather than the added marker.

# FilterSchemas/ShowCreateSchema shape: action.resource.schema.schemaName.
resource_schema_name := input.action.resource.schema.schemaName if {
	input.action.resource.schema.schemaName
}

# FilterTables/SelectFromColumns/ShowColumns/ShowCreateTable shape:
# action.resource.table.schemaName.
resource_schema_name := input.action.resource.table.schemaName if {
	input.action.resource.table.schemaName
}

_uuid_pattern := `^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`

# Digit-first UUIDs get the ds_ prefix (WorkspaceNames.fromDatasetId keeps the
# name a valid XML NCName) — strip it before undoing the hyphen→underscore
# substitution.
civitas_dataset_id(schema) := id if {
	startswith(schema, "ds_")
	candidate := regex.replace(substring(schema, 3, -1), "_", "-")
	regex.match(_uuid_pattern, candidate)
	id := candidate
}

# Letter-first UUIDs (first hex char a-f) are used unprefixed.
civitas_dataset_id(schema) := id if {
	not startswith(schema, "ds_")
	candidate := regex.replace(schema, "_", "-")
	regex.match(_uuid_pattern, candidate)
	id := candidate
}

# Undefined for a schema that isn't shaped like ours at all (information_schema,
# etc.) — that's a definite "not ours", handled by the plain-authentication
# branch below with no permission check needed.
resolved_dataset_id := civitas_dataset_id(resource_schema_name) if {
	resource_schema_name
}

# =============================================================================
# ACTION DISPATCH
# =============================================================================
default allow := false

allow if {
	_allow_all
}

# ImpersonateUser: resource.user.user is the target identity Superset asks to
# impersonate (the Keycloak sub). Allowed only from a trusted service account,
# and only for a sub that resolves to a real Civitas user — an unresolvable
# target (never logged in, no AuthZ Repository record) is refused rather than
# silently impersonated with no permissions.
allow if {
	input.action.operation == "ImpersonateUser"
	input.context.identity.user in trusted_impersonators
	target_user := input.action.resource.user.user
	target_user
	user_context_fetcher.fetch_context(target_user).userId != null
}

# The local-admin identity impersonating itself needs no AuthZ Repository record — see
# is_local_admin's comment above; it is never a real Civitas user.
allow if {
	input.action.operation == "ImpersonateUser"
	input.context.identity.user in trusted_impersonators
	input.action.resource.user.user == local_admin_user
	local_admin_user != ""
}

# Catalog/query-level actions carry no dataset-scoped resource of their own —
# authentication is the only thing to check; the schema/table actions below
# are what actually gate dataset visibility.
allow if {
	input.action.operation in {"ExecuteQuery", "AccessCatalog", "FilterCatalogs", "ShowSchemas", "ShowCreateSchema"}
	is_authenticated
}

# A genuinely anonymous Superset session never reaches the ImpersonateUser check above —
# civitas_security.py's own get_username() returns None for it, so Superset's Trino engine
# spec skips impersonation entirely and the query runs under the raw connection principal,
# superset_trino. trusted_impersonators is reused here for a second, distinct purpose: not
# just "who may impersonate someone else" but "which service accounts may run these specific
# coarse, non-data-exposing actions on their own (non-impersonated) behalf." Real dataset
# visibility stays independently gated below regardless of this branch.
allow if {
	input.action.operation in {"ExecuteQuery", "AccessCatalog", "FilterCatalogs", "ShowSchemas", "ShowCreateSchema"}
	input.context.identity.user in trusted_impersonators
}

# Once impersonated as the local-admin identity, the same coarse actions must keep working —
# is_authenticated is unreachable for it (see is_local_admin's comment above).
allow if {
	input.action.operation in {"ExecuteQuery", "AccessCatalog", "FilterCatalogs", "ShowSchemas", "ShowCreateSchema"}
	is_local_admin
}

# Schema/table-scoped actions against a schema that isn't a Civitas dataset at
# all (information_schema, etc.) — authentication is sufficient; there is no
# dataset-level permission to check.
allow if {
	input.action.operation in {"FilterColumns", "FilterSchemas", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	is_authenticated
	resource_schema_name
	not resolved_dataset_id
}

# Schema/table-scoped actions against a real Civitas dataset schema — gated on
# DATASET_PAYLOAD_READ, exactly the permission Superset's own schema_access/
# can_access_datasource checks require (see superset.rego), so a user sees the
# same datasets through Trino directly as through Superset.
allow if {
	input.action.operation in {"FilterColumns", "FilterSchemas", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	is_authenticated
	resolved_dataset_id
	has_permission("DATASET_PAYLOAD_READ", resolved_dataset_id)
}

# Open-data dataset schemas are readable independent of caller identity. This mirrors the
# Superset-side open-data branch: authenticated users without an explicit grant and genuinely
# anonymous Superset chart queries both pass here when the dataset itself is public.
allow if {
	input.action.operation in {"FilterColumns", "FilterSchemas", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	resolved_dataset_id
	dataset_pool_fetcher.is_open_data(resolved_dataset_id)
}

# The local-admin identity bypasses dataset-scoped permission checks entirely, covering both
# Civitas dataset schemas and non-Civitas ones (information_schema, etc.) — see is_local_admin's
# comment above for why the config-adapter saga can't go through the normal permission path.
allow if {
	input.action.operation in {"FilterColumns", "FilterSchemas", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	is_local_admin
}
