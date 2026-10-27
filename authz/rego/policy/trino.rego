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
# Trino instance. start-trino.sh already sets opa.log-requests=true —
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

# CORE payload-read permissions for per-dataset geodata schemas.
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
# The schema and catalog names are both required. A schema name alone is not
# an access boundary because catalogs may contain the same schema names.
# FilterSchemas/ShowCreateSchema use action.resource.schema.
resource_schema_name := input.action.resource.schema.schemaName if {
	input.action.resource.schema.schemaName
}

# FilterTables/SelectFromColumns/ShowColumns/ShowCreateTable shape:
# action.resource.table.schemaName.
resource_schema_name := input.action.resource.table.schemaName if {
	input.action.resource.table.schemaName
}

resource_catalog_name := input.action.resource.schema.catalogName if {
	input.action.resource.schema.catalogName
}

resource_catalog_name := input.action.resource.table.catalogName if {
	input.action.resource.table.catalogName
}

resource_catalog_name := input.action.resource.catalog.name if {
	input.action.resource.catalog.name
}

_uuid_pattern := `^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`

civitas_dataset_id(schema) := id if {
	startswith(schema, "ds_")
	candidate := regex.replace(substring(schema, 3, -1), "_", "-")
	regex.match(_uuid_pattern, candidate)
	id := candidate
}

civitas_dataset_id(schema) := id if {
	not startswith(schema, "ds_")
	candidate := regex.replace(schema, "_", "-")
	regex.match(_uuid_pattern, candidate)
	id := candidate
}

resolved_dataset_id := dataset_pool_fetcher.dataset_id_for_schema(resource_schema_name) if {
	resource_catalog_name in {"sensordata", "geodata"}
	resource_schema_name
}

# Keep access stable while an existing FROST project is being migrated from the legacy UUID schema.
resolved_dataset_id := civitas_dataset_id(resource_schema_name) if {
	resource_catalog_name == "sensordata"
	resource_schema_name
	not dataset_pool_fetcher.dataset_id_for_schema(resource_schema_name)
}

resolved_dataset_id := civitas_dataset_id(resource_schema_name) if {
	resource_catalog_name == "geodata"
	resource_schema_name
	not dataset_pool_fetcher.dataset_id_for_schema(resource_schema_name)
}

is_geodata_dataset_schema if {
	resource_catalog_name == "geodata"
	resolved_dataset_id
	resource_schema_name == dataset_pool_fetcher.attributes(resolved_dataset_id).schemaName
}

# Retain permission-checked access for already registered Superset datasets that still point to
# the physical PostGIS UUID schema. Schema discovery exposes only the readable alias schema.
is_geodata_legacy_dataset_schema if {
	resource_catalog_name == "geodata"
	resource_schema_name
	dataset_id := civitas_dataset_id(resource_schema_name)
	dataset_pool_fetcher.attributes(dataset_id).datasetId == dataset_id
	resource_schema_name != dataset_pool_fetcher.attributes(dataset_id).schemaName
}

is_geodata_readable_dataset_schema if {
	is_geodata_dataset_schema
}

is_geodata_readable_dataset_schema if {
	is_geodata_legacy_dataset_schema
}

is_frost_dataset_schema if {
	resource_catalog_name == "sensordata"
	resolved_dataset_id
	resource_schema_name == dataset_pool_fetcher.attributes(resolved_dataset_id).schemaName
}

# Existing saved Superset datasets may still query the UUID schema during migration. Keep that
# access path permission-checked, but never expose it through schema discovery.
is_frost_legacy_dataset_schema if {
	resource_catalog_name == "sensordata"
	resource_schema_name
	dataset_id := civitas_dataset_id(resource_schema_name)
	dataset_pool_fetcher.attributes(dataset_id).datasetId == dataset_id
	resource_schema_name != dataset_pool_fetcher.attributes(dataset_id).schemaName
}

is_frost_readable_dataset_schema if {
	is_frost_dataset_schema
}

is_frost_readable_dataset_schema if {
	is_frost_legacy_dataset_schema
}

is_geodata_metadata_table if {
	input.action.resource.table.catalogName == "geodata"
	input.action.resource.table.schemaName == "information_schema"
	input.action.resource.table.tableName in {"schemata", "tables", "columns"}
}

# The Trino SQLAlchemy dialect reads comments from system.metadata.table_comments
# while reflecting a dataset. Keep access to that system table separate from
# ordinary system-catalog data and row-filter its results below.
is_table_comments_metadata_table if {
	input.action.resource.table.catalogName == "system"
	input.action.resource.table.schemaName == "metadata"
	input.action.resource.table.tableName == "table_comments"
}

# Superset discovers schemas by querying Trino's information_schema. The row
# filter restricts FROST and geodata metadata to the caller's readable dataset schemas.
is_frost_schema_metadata_table if {
	input.action.resource.table.catalogName == "sensordata"
	input.action.resource.table.schemaName == "information_schema"
	input.action.resource.table.tableName in {"schemata", "tables", "columns"}
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
	input.action.operation in {"ExecuteQuery", "ShowSchemas"}
	is_authenticated
}

allow if {
	input.action.operation == "FilterCatalogs"
	is_authenticated
}

allow if {
	input.action.operation == "AccessCatalog"
	is_authenticated
	resource_catalog_name in {"sensordata", "geodata"}
}

# Superset's Trino dialect needs the system catalog only to read filtered
# table comments during dataset reflection. Other system tables remain denied.
allow if {
	input.action.operation == "AccessCatalog"
	is_authenticated
	resource_catalog_name == "system"
}

# Both Trino catalogs use a schema per CORE dataset. Access to a catalog does not
# grant access to its dataset schemas; each schema needs its CORE payload-read right.
allow if {
	input.action.operation in {"FilterSchemas", "ShowCreateSchema"}
	is_authenticated
	is_geodata_readable_dataset_schema
	has_permission("DATASET_PAYLOAD_READ", resolved_dataset_id)
}

allow if {
	input.action.operation in {"FilterSchemas", "ShowCreateSchema"}
	resolved_dataset_id
	is_geodata_readable_dataset_schema
	dataset_pool_fetcher.is_open_and_available(resolved_dataset_id)
}

allow if {
	input.action.operation in {"FilterSchemas", "ShowCreateSchema"}
	is_authenticated
	is_frost_dataset_schema
	has_permission("DATASET_PAYLOAD_READ", resolved_dataset_id)
}

allow if {
	input.action.operation in {"FilterSchemas", "ShowCreateSchema"}
	is_frost_dataset_schema
	dataset_pool_fetcher.is_open_and_available(resolved_dataset_id)
}

# Superset reads geodata schema metadata to populate its schema picker. The
# separate row-filter policy returns only dataset schemas the caller can read.
allow if {
	input.action.operation in {"FilterSchemas", "ShowCreateSchema"}
	is_authenticated
	resource_catalog_name == "geodata"
	resource_schema_name == "information_schema"
}

# A genuinely anonymous Superset session never reaches the ImpersonateUser check above —
# civitas_security.py's own get_username() returns None for it, so Superset's Trino engine
# spec skips impersonation entirely and the query runs under the raw connection principal,
# superset_trino. trusted_impersonators is reused here for a second, distinct purpose: not
# just "who may impersonate someone else" but "which service accounts may run these specific
# coarse, non-data-exposing actions on their own (non-impersonated) behalf." Real dataset
# visibility stays independently gated below regardless of this branch.
allow if {
	input.action.operation in {"ExecuteQuery", "ShowSchemas"}
	input.context.identity.user in trusted_impersonators
}

allow if {
	input.action.operation == "FilterCatalogs"
	input.context.identity.user in trusted_impersonators
}

allow if {
	input.action.operation == "AccessCatalog"
	input.context.identity.user in trusted_impersonators
	resource_catalog_name in {"sensordata", "geodata"}
}

# Once impersonated as the local-admin identity, the same coarse actions must keep working —
# is_authenticated is unreachable for it (see is_local_admin's comment above).
allow if {
	input.action.operation in {"ExecuteQuery", "AccessCatalog", "FilterCatalogs", "ShowSchemas", "ShowCreateSchema"}
	is_local_admin
}

# FROST table actions resolve the dataset through CORE; PostGIS keeps UUID-derived schemas.
allow if {
	input.action.operation in {"FilterColumns", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	is_authenticated
	is_frost_readable_dataset_schema
	has_permission("DATASET_PAYLOAD_READ", resolved_dataset_id)
}

allow if {
	input.action.operation in {"FilterColumns", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	is_frost_readable_dataset_schema
	dataset_pool_fetcher.is_open_and_available(resolved_dataset_id)
}

allow if {
	input.action.operation in {"FilterColumns", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	is_authenticated
	is_geodata_readable_dataset_schema
	has_permission("DATASET_PAYLOAD_READ", resolved_dataset_id)
}

allow if {
	input.action.operation in {"FilterColumns", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	resolved_dataset_id
	is_geodata_readable_dataset_schema
	dataset_pool_fetcher.is_open_and_available(resolved_dataset_id)
}

allow if {
	input.action.operation in {"FilterColumns", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowTables"}
	is_authenticated
	is_geodata_metadata_table
}

allow if {
	input.action.operation in {"FilterColumns", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowTables"}
	is_authenticated
	is_table_comments_metadata_table
}

# Superset's schema picker reads information_schema.schemata. Keep the grant
# limited to that metadata table; the row-filter endpoint only returns readable dataset schemas.
allow if {
	input.action.operation == "SelectFromColumns"
	is_authenticated
	is_frost_schema_metadata_table
}

# The local-admin identity bypasses dataset-scoped permission checks entirely, covering both
# Civitas dataset schemas and non-Civitas ones (information_schema, etc.) — see is_local_admin's
# comment above for why the config-adapter saga can't go through the normal permission path.
allow if {
	input.action.operation in {"FilterColumns", "FilterSchemas", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable", "ShowTables"}
	is_local_admin
}
