# CIVITAS CORE AuthZ - Superset Entry Point
# The active Superset security manager calls schema_access, user_groups, can_access_schema, and
# can_access_datasource. Dashboard/chart visibility and editorship now use Superset's native
# Subject assignments. The legacy dashboard/chart rules below are no longer called by the active
# Superset integration.
#
# Fail-secure design: when the AuthZ Repository is unreachable or the caller's
# sub has no valid user context, user_context stays undefined, so every
# has_permission()/has_tenant_permission() branch below simply fails to match —
# no explicit "deny" branch is needed for that case.
package civitas.superset

import rego.v1

import data.civitas.authz.dashboard_dataset_fetcher
import data.civitas.authz.dataset_pool_fetcher
import data.civitas.authz.user_context_fetcher

# =============================================================================
# BACKEND ENABLEMENT SWITCH
# =============================================================================
# backends-allowall data (data_permissions == null) → allow every read.
# real backends data (data_permissions is a concrete array) → real permission
# checks below apply.
# missing/unmounted backend data → deny (fail-secure).
default _allow_all := false

_allow_all if {
	is_null(data.backends.superset.data_permissions)
}

# =============================================================================
# USER CONTEXT
# =============================================================================
# Superset already resolves the caller to a Keycloak sub (civitas_security.py's
# get_oauth_user_info maps username -> sub) and sends it as input.user, so there
# is no X-Userinfo header to decode here — call fetch_context directly with it,
# exactly as user_context_fetcher.rego's own comment anticipates for non-APISIX
# callers (it names the Trino path as the other example).
user_context := user_context_fetcher.fetch_context(input.user) if {
	input.user
	user_context_fetcher.fetch_context(input.user).userId != null
}

# TENANT-scoped assignments cascade to every resource, regardless of kind. Deliberately excludes
# scopeType == null (a SYSTEM role assignment, e.g. Tenant Admin) — unlike permission_eval.rego's
# REST API scope inheritance, where an unscoped SYSTEM role is meant to cascade to every resource.
# A SYSTEM role can never hold a DATA permission (RoleService rejects it), so this permission set
# would never contain one anyway; but not filtering scopeType here would leave open a silent
# tenant-wide leak the moment that invariant is violated (e.g. by RoleInitializer's seed path,
# which bypasses that check).
has_tenant_permission(permission) if {
	some group in user_context.groups
	some a in group.assignments
	permission in a.permissions
	a.scopeType == "TENANT"
}

# Parameterized by an explicit dataset_id, rather than reading input.civitasDatasetId directly,
# so a caller that resolves its dataset id some other way (e.g. can_read_dashboard/
# can_write_dashboard, via dashboard_dataset_fetcher) can reuse the same permission logic.
# has_permission(permission) below is the input.civitasDatasetId-reading convenience wrapper
# every other caller here (schema/datasource/dataset/chart checks) still uses unchanged.

# DATASET-scoped: matches dataset_id directly.
has_permission_for(permission, _) if {
	has_tenant_permission(permission)
}

has_permission_for(permission, dataset_id) if {
	dataset_id
	some group in user_context.groups
	some a in group.assignments
	permission in a.permissions
	a.scopeType == "DATASET"
	a.scopeId == dataset_id
}

# DATAPOOL union inheritance (Epic 1): a DATAPOOL grant covers every dataset in
# that pool.
has_permission_for(permission, dataset_id) if {
	dataset_id
	some group in user_context.groups
	some a in group.assignments
	permission in a.permissions
	a.scopeType == "DATAPOOL"
	a.scopeId == dataset_pool_fetcher.pool_of(dataset_id)
}

has_permission(permission) if {
	has_permission_for(permission, input.civitasDatasetId)
}

# =============================================================================
# SCHEMA ACCESS (SupersetSecurityManager.user_view_menu_names)
# =============================================================================

default is_tenant_reader := false

is_tenant_reader if {
	_allow_all
}

is_tenant_reader if {
	has_tenant_permission("DATASET_PAYLOAD_READ")
}

# Direct DATASET-scoped grants.
allowed_schemas contains dataset_id if {
	some group in user_context.groups
	some a in group.assignments
	"DATASET_PAYLOAD_READ" in a.permissions
	a.scopeType == "DATASET"
	dataset_id := a.scopeId
}

# DATAPOOL union inheritance (Epic 1): every dataset in a pool the caller holds
# DATASET_PAYLOAD_READ on. Resolved via ONE batch call for every qualifying pool, not one
# pool_of()-style call per dataset — the caller doesn't know the dataset ids to ask about until
# this resolves them.
_qualifying_datapool_ids contains pool_id if {
	some group in user_context.groups
	some a in group.assignments
	"DATASET_PAYLOAD_READ" in a.permissions
	a.scopeType == "DATAPOOL"
	pool_id := a.scopeId
}

allowed_schemas contains dataset_id if {
	count(_qualifying_datapool_ids) > 0
	some dataset_id in dataset_pool_fetcher.datasets_in_pools(_qualifying_datapool_ids)
}

schema_access := {
	"allowed_schemas": allowed_schemas,
	"is_tenant_reader": is_tenant_reader,
}

# Superset's FAB permissions need the actual Trino schema name, not the CORE dataset UUID.
# Dataset IDs remain the authorization keys above; CORE supplies the current readable schema.
allowed_geodata_schemas contains schema_name if {
	is_tenant_reader
	some dataset in dataset_pool_fetcher.schemas
	schema_name := dataset.schemaName
}

allowed_geodata_schemas contains schema_name if {
	some dataset_id in allowed_schemas
	schema_name := dataset_pool_fetcher.attributes(dataset_id).schemaName
}

# Group IDs and names are returned to Superset so it can mirror CORE membership into
# FAB Groups. Superset uses those Groups as native Subjects for editors/viewers; group
# membership itself remains owned by CORE and is reconciled at OAuth login.
user_groups := {"groups": groups} if {
	user_context.userId != null
	groups := [result |
		some i
		core_group := user_context.groups[i]
		core_group.id != null
		core_group.name != null
		result := {"id": core_group.id, "name": core_group.name}
	]
}

# Dashboard access is a CORE-derived entitlement, independent of whether a dashboard
# already exists. Superset maps it to its interactive role at OAuth login.
default dashboard_users := false

dashboard_users if {
	some group in user_context.groups
	some a in group.assignments
	a.scopeType in {"TENANT", "DATAPOOL", "DATASET"}
	some permission in {"DATASET_READ", "DATASET_UPDATE"}
	permission in a.permissions
}

# A Superset Subject alone must never turn a private CORE dashboard public.
default can_view_public_dashboard := false

can_view_public_dashboard if {
	dashboard_id := sprintf("%v", [input.externalDashboardId])
	dataset_id := dashboard_dataset_fetcher.civitas_dataset_id_of(dashboard_id)
	dataset_pool_fetcher.is_open_data(dataset_id)
	dataset_pool_fetcher.attributes(dataset_id).available == true
}

default can_edit_core_dashboard := false

can_edit_core_dashboard if {
	has_permission_for("DATASET_UPDATE", resolved_dashboard_dataset_id)
}

default can_read_core_dashboard := false

can_read_core_dashboard if {
	can_edit_core_dashboard
}

can_read_core_dashboard if {
	input.published == true
	has_permission_for("DATASET_READ", resolved_dashboard_dataset_id)
}

# =============================================================================
# LEGACY DASHBOARD LIST ACCESS (unused by the active Superset integration)
# =============================================================================
# Unlike schema_access above, this resolves AND decides entirely inside OPA rather than handing
# the caller a permission-derived dataset-id set to match locally: the caller passes every
# candidate dashboard's {externalDashboardId, published} (it still has to gather that much from
# Superset's own DB — nothing else knows which dashboards exist or their published state) and
# gets back exactly the ids it may see. A published dashboard is visible via either
# DATASET_DASHBOARD_READ or _WRITE; an unpublished (draft) one only via _WRITE — see
# can_read_dashboard's comment for why a plain reader shouldn't see a dashboard still being
# built.
#
# Resolves the whole candidate list via ONE call to dashboard_dataset_fetcher's batch endpoint,
# not one per dashboard — see that fetcher's own comment for why a per-dashboard call would cost
# N sequential round-trips instead of one.
_dashboard_civitas_ids := dashboard_dataset_fetcher.civitas_dataset_ids_of([
d.externalDashboardId |
	some d in input.dashboards
]) if {
	count(input.dashboards) > 0
}

allowed_dashboard_ids contains d.externalDashboardId if {
	some d in input.dashboards
	civitas_id := _dashboard_civitas_ids[d.externalDashboardId]
	d.published == true
	has_permission_for("DATASET_DASHBOARD_READ", civitas_id)
}

allowed_dashboard_ids contains d.externalDashboardId if {
	some d in input.dashboards
	civitas_id := _dashboard_civitas_ids[d.externalDashboardId]
	has_permission_for("DATASET_DASHBOARD_UPDATE", civitas_id)
}

# _allow_all unlocks every candidate regardless of dataset resolution — mirrors every other
# rule's _allow_all branch, just with no permission check to short-circuit into.
allowed_dashboard_ids contains d.externalDashboardId if {
	_allow_all
	some d in input.dashboards
}

dashboard_list_access := {"allowedDashboardIds": allowed_dashboard_ids}

# =============================================================================
# LEGACY CHART LIST ACCESS (unused by the active Superset integration)
# =============================================================================
# Legacy caller passed deduplicated dataset ids (SqlaTable.extra lives in Superset's own DB, not
# Civitas's), so no dataset-id resolution call was needed. This endpoint is no longer invoked.
#
# A dataset id is visible for chart-listing purposes via either DATASET_PAYLOAD_READ (can see the
# chart's data) or DATASET_UPDATE (can edit it, so must be able to find it in the list) — mirrors
# can_access_datasource's permission for the former, can_write_chart's for the latter.
allowed_chart_dataset_ids contains dataset_id if {
	some dataset_id in input.civitasDatasetIds
	has_permission_for("DATASET_PAYLOAD_READ", dataset_id)
}

allowed_chart_dataset_ids contains dataset_id if {
	some dataset_id in input.civitasDatasetIds
	has_permission_for("DATASET_UPDATE", dataset_id)
}

allowed_chart_dataset_ids contains dataset_id if {
	_allow_all
	some dataset_id in input.civitasDatasetIds
}

chart_dataset_access := {"allowedDatasetIds": allowed_chart_dataset_ids}

# =============================================================================
# RESOURCE ACCESS
# =============================================================================

# Unconditional by design (not part of the allow-all gate): Superset's Trino
# connection is a single shared database, so there is nothing to scope here.
default can_access_database := true

default can_access_schema := false

can_access_schema if {
	_allow_all
}

can_access_schema if {
	has_permission("DATASET_PAYLOAD_READ")
}

# Open-data bypass: keyed only on the dataset's own flag, not on caller identity, so this
# uniformly covers an authenticated user with no explicit grant AND a genuinely anonymous caller
# (civitas_security.py's can_access_schema/can_access_datasource pass civitasDatasetId either way).
can_access_schema if {
	input.civitasDatasetId
	dataset_pool_fetcher.is_open_data(input.civitasDatasetId)
}

# =============================================================================
# DATASET WRITE ACCESS (UpdateDataset / DeleteDataset)
# =============================================================================
# Legacy dataset-write rule; the active Superset integration relies on native dataset
# editor Subjects and does not call this endpoint. Kept with the other legacy resource rules
# until those endpoint contracts and their policy tests can be removed together.
default can_write_dataset := false

default can_access_datasource := false

can_access_datasource if {
	_allow_all
}

can_access_datasource if {
	has_permission("DATASET_PAYLOAD_READ")
}

# Open-data bypass — see the matching comment on can_access_schema above.
can_access_datasource if {
	input.civitasDatasetId
	dataset_pool_fetcher.is_open_data(input.civitasDatasetId)
}

# =============================================================================
# LEGACY DASHBOARD READ ACCESS (open-data path; unused by the active integration)
# =============================================================================
# This legacy endpoint evaluated open-data access across every dataset backing a dashboard. The
# active Superset integration no longer calls it; open-data remains enforced at dataset-query time.
default can_access_dashboard := false

can_access_dashboard if {
	_allow_all
}

can_access_dashboard if {
	count(input.dashboard_dataset_ids) > 0
	every dataset_id in input.dashboard_dataset_ids {
		dataset_pool_fetcher.is_open_data(dataset_id)
	}
}

# =============================================================================
# LEGACY DASHBOARD READ ACCESS (authenticated path; unused by the active integration)
# =============================================================================
# Legacy dashboard-read endpoint. It modeled access with Civitas dataset grants; the active
# Superset integration now uses native dashboard Subject assignments.
#
# A draft (unpublished) dashboard is visible only to a writer — a plain reader shouldn't see a
# dashboard that's still being built, only the finished, published ones. Write still implies read
# unconditionally, published or not, since a writer needs to see their own drafts.
#
# The legacy endpoint resolves dataset ownership from the platform dashboard ID through
# dashboard_dataset_fetcher rather than trusting Superset metadata.
resolved_dashboard_dataset_id := dashboard_dataset_fetcher.civitas_dataset_id_of(input.externalDashboardId) if {
	input.externalDashboardId
}

default can_read_dashboard := false

can_read_dashboard if {
	_allow_all
}

can_read_dashboard if {
	input.published == true
	has_permission_for("DATASET_DASHBOARD_READ", resolved_dashboard_dataset_id)
}

can_read_dashboard if {
	has_permission_for("DATASET_DASHBOARD_UPDATE", resolved_dashboard_dataset_id)
}

# =============================================================================
# LEGACY DASHBOARD WRITE ACCESS (unused by the active integration)
# =============================================================================
# Unlike dataset writes, dashboard writes are a real end-user action: per
# SupersetSagaHandler.java, the saga provisions only the dataset and the
# initial dashboard — "users add their own charts to the provisioned
# dashboard in Superset" — so dashboard edits need a real permission check.
default can_write_dashboard := false

can_write_dashboard if {
	_allow_all
}

can_write_dashboard if {
	has_permission_for("DATASET_DASHBOARD_UPDATE", resolved_dashboard_dataset_id)
}

# =============================================================================
# LEGACY CHART WRITE ACCESS (unused by the active integration)
# =============================================================================
# Also a real end-user action (see can_write_dashboard above) — keyed off the
# civitasDatasetId of the chart's datasource, not the dashboard it lives on.
default can_write_chart := false

can_write_chart if {
	_allow_all
}

can_write_chart if {
	has_permission("DATASET_UPDATE")
}
