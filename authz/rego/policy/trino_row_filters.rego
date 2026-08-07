# CIVITAS CORE AuthZ - Trino Row-Filter Entry Point
#
# Trino is configured with a SEPARATE endpoint from the base allow/deny policy:
#   opa.policy.row-filters-uri=http://civitas-opa:8181/v1/data/civitas/trino/row_filters/result
#
# Verified against the actual Trino 481 OPA plugin jar (io.trino.plugin.opa.OpaConfig's @Config
# annotations), not assumed from docs: the property is opa.policy.row-filters-uri, and the plugin's
# OpaRowFiltersQueryResult/OpaViewExpression records confirm the response shape below
# ({"result": [{"expression": "..."}]}) and the request shape consumed here
# (input.action.resource.table.{catalogName,schemaName,tableName}, input.context.identity.user) —
# both match what trino.rego's own base allow policy already assumes for the same fields.
#
# Why this policy exists at all, separate from trino.rego's table/schema-level allow check:
# PostGIS datasets each get their own Postgres schema (one dataset = one schema), so trino.rego's
# existing schema-level DATASET_PAYLOAD_READ check already IS dataset-level access control. FROST's
# "Projects" plugin stores every dataset's sensor data in one shared schema (frost_scoped, backed by
# public.THINGS/OBSERVATIONS/etc. — see dev-environment/frost/migrations/01-create-scoped-views.sql)
# — there is no schema/table boundary between datasets there, so row-level filtering is the only way
# to scope access. trino.rego's base allow check still applies to frost_scoped.* (same as
# information_schema: any authenticated user may query the schema at all), but the actual dataset
# boundary is enforced here, per row, via the flattened frost_project_id column every
# frost_scoped.* view carries.
package civitas.trino.row_filters

import rego.v1

import data.civitas.authz.dataset_pool_fetcher
import data.civitas.authz.user_context_fetcher

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
# Only frost_scoped.* views carry a row filter at all — every other schema (Civitas per-dataset
# PostGIS schemas, information_schema, etc.) is already fully gated at the table/schema level by
# trino.rego's own allow check, so this policy has nothing to add there. Trino treats "no matching
# result rule" as "no filter" for those, which is correct: they were never meant to be filtered here.
_is_frost_scoped_table if {
	input.action.resource.table.schemaName == "frost_scoped"
}

# =============================================================================
# READABLE FROST PROJECT IDS
# =============================================================================
# Enumerates the FROST project ids backing every dataset the caller holds DATASET_PAYLOAD_READ on —
# mirrors superset.rego's allowed_schemas / permission_eval.rego's qualifying_datapool_ids "enumerate
# what I can see" pattern (direct DATASET grants + DATAPOOL union via the batch
# dataset_pool_fetcher.datasets_in_pools() reverse-lookup), then maps each dataset id to its FROST
# project id via the dataset's own projectId field (dataset_pool_fetcher.frost_project_of, backed by
# the same /api/v1/dataset-pool/{id} lookup already used for pool_of()/is_open_data() — no extra
# round-trip class of its own).

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

_readable_frost_project_ids contains project_id if {
	some dataset_id in _candidate_dataset_ids
	project_id := dataset_pool_fetcher.frost_project_of(dataset_id)
}

# =============================================================================
# ROW-FILTER RESULT
# =============================================================================
# One of three expressions, identical in shape across every frost_scoped.* view — the join
# complexity lives entirely in the views themselves (see the migration file), not here:
#   - "true"                              — allow-all mode, or a TENANT-scoped reader: no restriction
#   - "frost_project_id IN ('1', '2')"    — the caller's actual readable set
#   - "false"                             — nothing readable: deny every row, not "no filter" (Trino
#                                            treats an omitted result as unrestricted, the opposite
#                                            of fail-secure)
default result := []

result := [{"expression": "true"}] if {
	_is_frost_scoped_table
	_allow_all
}

result := [{"expression": "true"}] if {
	_is_frost_scoped_table
	not _allow_all
	has_tenant_permission("DATASET_PAYLOAD_READ")
}

result := [{"expression": expr}] if {
	_is_frost_scoped_table
	not _allow_all
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_frost_project_ids) > 0
	quoted := [concat("", ["'", id, "'"]) | some id in _readable_frost_project_ids]
	expr := concat("", ["frost_project_id IN (", concat(", ", quoted), ")"])
}

result := [{"expression": "false"}] if {
	_is_frost_scoped_table
	not _allow_all
	not has_tenant_permission("DATASET_PAYLOAD_READ")
	count(_readable_frost_project_ids) == 0
}
