# CIVITAS CORE AuthZ - Superset Entry Point
# Evaluates Superset's dataset/dashboard access-control hooks (civitas_security.py
# calls these rules by name; the field names below — user, civitasDatasetId,
# dashboard_dataset_ids — come straight from CivitasSupersetSecurityManager._opa_can_access,
# not from any URL/path parsing).
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

# =============================================================================
# DASHBOARD LIST ACCESS (civitas_security.py's DashboardAccessFilter override)
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
# CHART LIST ACCESS (civitas_security.py's EXTRA_ACCESS_QUERY_FILTERS["charts"])
# =============================================================================
# Unlike dashboard_list_access, no fetcher round-trip is needed here: civitas_security.py
# already knows every chart's civitasDatasetId locally (SqlaTable.extra lives in Superset's own
# DB, not Civitas's), so the caller passes the deduplicated set of dataset ids directly rather
# than external ids needing resolution — one OPA call per request, keyed by dataset id, not one
# per chart.
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
# Only the config-adapter service account may create/update/delete Superset
# datasets (see SupersetSagaHandler.java) — that path never reaches OPA at all,
# since CivitasSupersetSecurityManager._is_local_admin() short-circuits to
# True/return before is_editor()/raise_for_editorship() ever query this rule. Any
# real user reaching this rule is therefore never authorized — no _allow_all or
# permission-based branch, just a hard deny.
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
# DASHBOARD READ ACCESS (open-data path)
# =============================================================================
# Identity-agnostic: civitas_security.py's raise_for_access calls this rule for both the
# anonymous branch and, as an open-data fallback, the authenticated branch (via
# _opa_dashboard_is_open_data) when the caller has no DASHBOARD grant — so a logged-in user
# without a grant gets the same public-dashboard access an anonymous visitor would. Keyed on
# every dataset backing the dashboard's charts (dashboard_dataset_ids) rather than a single
# civitasDatasetId, since ABAC open-data eligibility must hold for all of them, not just the
# dashboard's own home dataset. The grant-based authenticated case is can_read_dashboard below.
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
# DASHBOARD READ ACCESS (authenticated path)
# =============================================================================
# Mirrors can_write_dashboard's civitasDatasetId-scoped model — a write grant implies read, since
# editing a dashboard already requires opening it. Replaces the previous design, where authenticated
# dashboard-container visibility deferred entirely to Superset's own native RBAC
# (DASHBOARD_RBAC/dashboard.roles) — see civitas_security.py's raise_for_access.
#
# A draft (unpublished) dashboard is visible only to a writer — a plain reader shouldn't see a
# dashboard that's still being built, only the finished, published ones. Write still implies read
# unconditionally, published or not, since a writer needs to see their own drafts.
#
# Keyed by civitasDatasetId, not an independent per-dashboard scope: the platform's Dashboard
# entity always belongs to exactly one dataset (a required FK), so there is nothing an
# independent DASHBOARD scope would add over reusing the dataset-scoped model every other check
# here already uses. Resolved from input.externalDashboardId via dashboard_dataset_fetcher — the
# platform's own Dashboard.dataSet relationship — rather than trusting a copy of the id stored in
# the Superset dashboard's own json_metadata (civitas_security.py no longer reads that field for
# authorization; it's still stamped there for display purposes only).
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
# DASHBOARD WRITE ACCESS
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
# CHART WRITE ACCESS (WriteChartInDashboard)
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
