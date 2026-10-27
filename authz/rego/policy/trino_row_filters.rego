# CIVITAS CORE AuthZ - Trino Row-Filter Entry Point
#
# Trino is configured with a SEPARATE endpoint from the base allow/deny policy:
#   opa.policy.row-filters-uri=http://civitas-opa:8181/v1/data/civitas/trino/row_filters/result
#
# Trino's OPA plugin uses `opa.policy.row-filters-uri` and returns the
# {"result": [{"expression": "..."}]} response shape. The resource fields consumed here match
# the OPA request contract used by trino.rego's base allow policy.
#
# Why this policy exists, separate from trino.rego's table/schema-level allow check:
# PostGIS datasets each get their own schema. FROST uses dataset-specific schemas that contain
# PostgreSQL views filtered to one FROST project. Trino still needs row filters for information_schema
# so it lists only the CORE dataset schemas the caller can read. The shared frost_scoped source
# views are denied to the Trino reader role.
package civitas.trino.row_filters

import rego.v1

import data.civitas.authz.dataset_pool_fetcher
import data.civitas.authz.user_context_fetcher
import data.civitas.trino

# =============================================================================
# BACKEND ENABLEMENT SWITCH
# =============================================================================
# Mirrors trino.rego: allow-all mode (dev/test) means no restriction at all, not just "everything
# passes the base allow check" — a row filter of "true" is the row-filter equivalent of _allow_all.
default _allow_all := false

_allow_all if {
	is_null(data.backends.trino.data_permissions)
}

# =============================================================================
# USER CONTEXT (effective, post-impersonation identity)
# =============================================================================
# Mirrors trino.rego: Trino has already resolved the caller to a Keycloak sub, so no X-Userinfo
# header to decode here.
user_context := user_context_fetcher.fetch_context(input.context.identity.user) if {
	input.context.identity.user
	user_context_fetcher.fetch_context(input.context.identity.user).userId != null
}

# The config-adapter must find a newly provisioned PostGIS table before the
# dataset has end-user DATASET_PAYLOAD_READ assignments. Keep this exception
# limited to the internal Superset automation identity.
default local_admin_user := "civitas-config-adapter"

local_admin_user := data.config.trino_local_admin_user if {
	data.config.trino_local_admin_user
}

is_local_admin if {
	local_admin_user != ""
	input.context.identity.user == local_admin_user
}

_geodata_metadata_bypass if {
	_allow_all
}

_geodata_metadata_bypass if {
	is_local_admin
}

# Deliberately excludes scopeType == null (a SYSTEM role assignment, e.g. Tenant Admin) — same
# reasoning as trino.rego's has_tenant_permission.
has_tenant_permission(permission) if {
	some group in user_context.groups
	some a in group.assignments
	permission in a.permissions
	a.scopeType == "TENANT"
}

# =============================================================================
# TABLE CLASSIFICATION
# =============================================================================
# Dataset tables are gated by their dataset schemas in trino.rego. The information_schema listings
# need row filters to avoid disclosing other dataset schemas.
_is_frost_scoped_table if {
	input.action.resource.table.schemaName == "frost_scoped"
}

# Superset's schema picker queries information_schema.schemata directly. This
# metadata table is not covered by the normal FROST project row filter, so
# expose only the schema that the CORE connection is meant to show.
_is_frost_schema_listing if {
	input.action.resource.table.catalogName == "sensordata"
	input.action.resource.table.schemaName == "information_schema"
	input.action.resource.table.tableName == "schemata"
}

_is_frost_table_listing if {
	input.action.resource.table.catalogName == "sensordata"
	input.action.resource.table.schemaName == "information_schema"
	input.action.resource.table.tableName == "tables"
}

_is_frost_column_listing if {
	input.action.resource.table.catalogName == "sensordata"
	input.action.resource.table.schemaName == "information_schema"
	input.action.resource.table.tableName == "columns"
}

_is_geodata_schema_listing if {
	input.action.resource.table.catalogName == "geodata"
	input.action.resource.table.schemaName == "information_schema"
	input.action.resource.table.tableName == "schemata"
}

_is_geodata_table_listing if {
	input.action.resource.table.catalogName == "geodata"
	input.action.resource.table.schemaName == "information_schema"
	input.action.resource.table.tableName == "tables"
}

_is_geodata_column_listing if {
	input.action.resource.table.catalogName == "geodata"
	input.action.resource.table.schemaName == "information_schema"
	input.action.resource.table.tableName == "columns"
}

_is_table_comments_metadata if {
	input.action.resource.table.catalogName == "system"
	input.action.resource.table.schemaName == "metadata"
	input.action.resource.table.tableName == "table_comments"
}

# =============================================================================
# DATASET SCHEMAS VISIBLE TO AN AUTHENTICATED USER
# =============================================================================

_candidate_dataset_ids contains dataset_id if {
	some group in user_context.groups
	some a in group.assignments
	"DATASET_PAYLOAD_READ" in a.permissions
	a.scopeType == "DATASET"
	dataset_id := a.scopeId
}

_qualifying_pool_ids contains pool_id if {
	some group in user_context.groups
	some a in group.assignments
	"DATASET_PAYLOAD_READ" in a.permissions
	a.scopeType == "DATAPOOL"
	pool_id := a.scopeId
}

_candidate_dataset_ids contains dataset_id if {
	count(_qualifying_pool_ids) > 0
	some dataset_id in dataset_pool_fetcher.datasets_in_pools(_qualifying_pool_ids)
}

_dataset_schema_name(dataset_id) := name if {
	normalized := regex.replace(lower(dataset_id), "-", "_")
	regex.match(`^[0-9]`, normalized)
	name := concat("", ["ds_", normalized])
}

_dataset_schema_name(dataset_id) := name if {
	normalized := regex.replace(lower(dataset_id), "-", "_")
	not regex.match(`^[0-9]`, normalized)
	name := normalized
}

_readable_geodata_schemas contains schema_name if {
	some dataset_id in _candidate_dataset_ids
	schema_name := dataset_pool_fetcher.attributes(dataset_id).schemaName
}

_readable_geodata_schemas contains schema_name if {
	some dataset_id in _candidate_dataset_ids
	not dataset_pool_fetcher.attributes(dataset_id).schemaName
	schema_name := _dataset_schema_name(dataset_id)
}

_readable_frost_schemas contains schema_name if {
	some dataset_id in _candidate_dataset_ids
	schema_name := dataset_pool_fetcher.attributes(dataset_id).schemaName
}

_readable_frost_schemas contains schema_name if {
	some dataset_id in _candidate_dataset_ids
	not dataset_pool_fetcher.attributes(dataset_id).schemaName
	schema_name := _dataset_schema_name(dataset_id)
}

_all_frost_schemas contains schema_name if {
	some dataset in dataset_pool_fetcher.schemas()
	schema_name := dataset.schemaName
}

_all_geodata_schemas contains schema_name if {
	some dataset in dataset_pool_fetcher.schemas()
	schema_name := dataset.schemaName
}

_tenant_dataset_schema_regex := `^(ds_[0-9a-f]{8}_[0-9a-f]{4}_[0-9a-f]{4}_[0-9a-f]{4}_[0-9a-f]{12}|[a-f][0-9a-f]{7}_[0-9a-f]{4}_[0-9a-f]{4}_[0-9a-f]{4}_[0-9a-f]{12})$`

_geodata_schema_listing_filter := expr if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_all_geodata_schemas) > 0
	quoted := [concat("", ["'", schema_name, "'"]) | some schema_name in _all_geodata_schemas]
	expr := concat("", ["catalog_name = 'geodata' AND schema_name IN (", concat(", ", quoted), ")"])
}

_geodata_schema_listing_filter := "false" if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_all_geodata_schemas) == 0
}

_geodata_schema_listing_filter := expr if {
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_geodata_schemas) > 0
	quoted := [concat("", ["'", schema_name, "'"]) | some schema_name in _readable_geodata_schemas]
	expr := concat("", ["catalog_name = 'geodata' AND schema_name IN (", concat(", ", quoted), ")"])
}

_geodata_schema_listing_filter := "false" if {
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_geodata_schemas) == 0
}

_geodata_table_listing_filter := expr if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_all_geodata_schemas) > 0
	quoted := [concat("", ["'", schema_name, "'"]) | some schema_name in _all_geodata_schemas]
	expr := concat("", ["table_catalog = 'geodata' AND table_schema IN (", concat(", ", quoted), ")"])
}

_geodata_table_listing_filter := "false" if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_all_geodata_schemas) == 0
}

_geodata_table_listing_filter := expr if {
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_geodata_schemas) > 0
	quoted := [concat("", ["'", schema_name, "'"]) | some schema_name in _readable_geodata_schemas]
	expr := concat("", ["table_catalog = 'geodata' AND table_schema IN (", concat(", ", quoted), ")"])
}

_geodata_table_listing_filter := "false" if {
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_geodata_schemas) == 0
}

_frost_schema_listing_filter := expr if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_all_frost_schemas) > 0
	quoted := [concat("", ["'", schema_name, "'"]) | some schema_name in _all_frost_schemas]
	expr := concat("", ["catalog_name = 'sensordata' AND schema_name IN (", concat(", ", quoted), ")"])
}

_frost_schema_listing_filter := concat("", ["catalog_name = 'sensordata' AND regexp_like(schema_name, '", _tenant_dataset_schema_regex, "')"]) if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_all_frost_schemas) == 0
}

_frost_schema_listing_filter := expr if {
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_frost_schemas) > 0
	quoted := [concat("", ["'", schema_name, "'"]) | some schema_name in _readable_frost_schemas]
	expr := concat("", ["catalog_name = 'sensordata' AND schema_name IN (", concat(", ", quoted), ")"])
}

_frost_schema_listing_filter := "false" if {
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_frost_schemas) == 0
}

_frost_table_listing_filter := expr if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_all_frost_schemas) > 0
	quoted := [concat("", ["'", schema_name, "'"]) | some schema_name in _all_frost_schemas]
	expr := concat("", ["table_catalog = 'sensordata' AND table_schema IN (", concat(", ", quoted), ")"])
}

_frost_table_listing_filter := concat("", ["table_catalog = 'sensordata' AND regexp_like(table_schema, '", _tenant_dataset_schema_regex, "')"]) if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_all_frost_schemas) == 0
}

_frost_table_listing_filter := expr if {
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_frost_schemas) > 0
	quoted := [concat("", ["'", schema_name, "'"]) | some schema_name in _readable_frost_schemas]
	expr := concat("", ["table_catalog = 'sensordata' AND table_schema IN (", concat(", ", quoted), ")"])
}

_frost_table_listing_filter := "false" if {
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_frost_schemas) == 0
}

_is_frost_dataset_table if {
	input.action.resource.table.catalogName == "sensordata"
	dataset_pool_fetcher.dataset_id_for_schema(input.action.resource.table.schemaName)
}

_is_frost_dataset_table if {
	input.action.resource.table.catalogName == "sensordata"
	trino.civitas_dataset_id(input.action.resource.table.schemaName)
}

_frost_dataset_id := dataset_pool_fetcher.dataset_id_for_schema(input.action.resource.table.schemaName) if {
	_is_frost_dataset_table
	dataset_pool_fetcher.dataset_id_for_schema(input.action.resource.table.schemaName)
}

_frost_dataset_id := trino.civitas_dataset_id(input.action.resource.table.schemaName) if {
	_is_frost_dataset_table
	trino.civitas_dataset_id(input.action.resource.table.schemaName)
}

_can_read_frost_dataset if {
	is_local_admin
}

_can_read_frost_dataset if {
	trino.is_authenticated
	trino.has_permission("DATASET_PAYLOAD_READ", _frost_dataset_id)
}

_can_read_frost_dataset if {
	dataset_pool_fetcher.is_open_and_available(_frost_dataset_id)
}

# =============================================================================
# ROW-FILTER RESULT
# =============================================================================
# Per-dataset FROST views are already filtered in PostgreSQL. OPA checks their CORE dataset schema
# directly; the shared source views are denied to Trino's reader identity.
default result := []

result := [{"expression": "true"}] if {
	_is_frost_scoped_table
	_allow_all
}

result := [{"expression": _frost_schema_listing_filter}] if {
	_is_frost_schema_listing
	not _allow_all
}

result := [{"expression": _frost_table_listing_filter}] if {
	_is_frost_table_listing
	not _allow_all
}

result := [{"expression": _frost_table_listing_filter}] if {
	_is_frost_column_listing
	not _allow_all
}

result := [{"expression": "true"}] if {
	_geodata_metadata_bypass
	_is_geodata_schema_listing
}

result := [{"expression": "true"}] if {
	_geodata_metadata_bypass
	_is_geodata_table_listing
}

result := [{"expression": "true"}] if {
	_geodata_metadata_bypass
	_is_geodata_column_listing
}

result := [{"expression": _geodata_schema_listing_filter}] if {
	_is_geodata_schema_listing
	not _geodata_metadata_bypass
}

result := [{"expression": _geodata_table_listing_filter}] if {
	_is_geodata_table_listing
	not _geodata_metadata_bypass
}

result := [{"expression": _geodata_table_listing_filter}] if {
	_is_geodata_column_listing
	not _geodata_metadata_bypass
}

result := [{"expression": "false"}] if {
	_is_frost_scoped_table
	not _allow_all
}

result := [{"expression": "true"}] if {
	_is_frost_dataset_table
	_can_read_frost_dataset
}

result := [{"expression": "false"}] if {
	_is_frost_dataset_table
	not _can_read_frost_dataset
}

# Trino's SQLAlchemy dialect reads system.metadata.table_comments during table
# reflection. Limit returned rows to the user's readable dataset schemas in
# the two CORE catalogs. Translate the information_schema column names used by
# the existing filters to the names in table_comments.
_table_comments_metadata_filter := "true" if {
	_geodata_metadata_bypass
}

_table_comments_metadata_filter := concat("", [
	"(", replace(replace(_geodata_table_listing_filter, "table_catalog", "catalog_name"), "table_schema", "schema_name"), ") OR (",
	replace(replace(_frost_table_listing_filter, "table_catalog", "catalog_name"), "table_schema", "schema_name"), ")",
]) if {
	not _geodata_metadata_bypass
}

result := [{"expression": _table_comments_metadata_filter}] if {
	_is_table_comments_metadata
	not _allow_all
}

result := [{"expression": "true"}] if {
	_is_table_comments_metadata
	_allow_all
}
