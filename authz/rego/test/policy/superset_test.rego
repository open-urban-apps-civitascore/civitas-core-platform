# Tests for superset.rego — Superset dataset/dashboard access-control hooks.
#
# The real per-dataset/per-dashboard permission model isn't implemented yet
# (see policy/superset.rego); coverage here is limited to the allow-all/deny-all
# gate driven by data.backends.superset.data_permissions, plus the two rules
# that are unconditionally true by design.

package civitas.superset_test

import rego.v1

import data.civitas.superset
import data.test.helpers.mock_http

_allow_all_data := {"data_permissions": null}

_enforced_data := {"data_permissions": ["DATASET_PAYLOAD_READ"]}

mock_public_dashboard(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-1"}} if {
	contains(req.url, "dashboard-dataset")
}

mock_public_dashboard(req) := {"status_code": 200, "body": {"openDataAccess": true, "available": true}} if {
	contains(req.url, "dataset-pool")
}

mock_private_dashboard(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-1"}} if {
	contains(req.url, "dashboard-dataset")
}

mock_private_dashboard(req) := {"status_code": 200, "body": {"openDataAccess": false}} if {
	contains(req.url, "dataset-pool")
}

mock_unreleased_dashboard(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-1"}} if {
	contains(req.url, "dashboard-dataset")
}

mock_unreleased_dashboard(req) := {"status_code": 200, "body": {"openDataAccess": true, "available": false}} if {
	contains(req.url, "dataset-pool")
}

mock_core_dashboard_editor(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_UPDATE"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_core_dashboard_editor(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-1"}} if {
	contains(req.url, "dashboard-dataset")
}

mock_core_dashboard_reader(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_core_dashboard_reader(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-1"}} if {
	contains(req.url, "dashboard-dataset")
}

test_core_dashboard_editor_can_edit_and_read_draft if {
	superset.can_edit_core_dashboard with http.send as mock_core_dashboard_editor
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "7", "published": false}
	superset.can_read_core_dashboard with http.send as mock_core_dashboard_editor
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "7", "published": false}
}

test_core_dashboard_reader_cannot_edit_or_read_draft if {
	not superset.can_edit_core_dashboard with http.send as mock_core_dashboard_reader
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "7", "published": false}
	not superset.can_read_core_dashboard with http.send as mock_core_dashboard_reader
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "7", "published": false}
	superset.can_read_core_dashboard with http.send as mock_core_dashboard_reader
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "7", "published": true}
}

test_public_dashboard_requires_core_open_data if {
	superset.can_view_public_dashboard with http.send as mock_public_dashboard
		with data.config as mock_http.mock_config
		with input as {"externalDashboardId": "7"}
	not superset.can_view_public_dashboard with http.send as mock_private_dashboard
		with data.config as mock_http.mock_config
		with input as {"externalDashboardId": "7"}
	not superset.can_view_public_dashboard with http.send as mock_unreleased_dashboard
		with data.config as mock_http.mock_config
		with input as {"externalDashboardId": "7"}
}

# =============================================================================
# ALLOW-ALL GATE
# =============================================================================

test_tenant_reader_when_allowall if {
	superset.is_tenant_reader with data.backends.superset as _allow_all_data
}

test_not_tenant_reader_when_enforced if {
	not superset.is_tenant_reader with data.backends.superset as _enforced_data
}

test_schema_access_result_when_allowall if {
	superset.schema_access == {"allowed_schemas": set(), "is_tenant_reader": true}
		with data.backends.superset as _allow_all_data
}

test_can_access_schema_when_allowall if {
	superset.can_access_schema with data.backends.superset as _allow_all_data
}

test_can_access_schema_denied_when_enforced if {
	not superset.can_access_schema with data.backends.superset as _enforced_data
}

test_can_write_dataset_denied_when_allowall if {
	not superset.can_write_dataset with data.backends.superset as _allow_all_data
}

test_can_write_dataset_denied_when_enforced if {
	not superset.can_write_dataset with data.backends.superset as _enforced_data
}

test_can_access_datasource_when_allowall if {
	superset.can_access_datasource with data.backends.superset as _allow_all_data
}

test_can_write_dashboard_when_allowall if {
	superset.can_write_dashboard with data.backends.superset as _allow_all_data
}

test_can_write_dashboard_denied_when_enforced if {
	not superset.can_write_dashboard with data.backends.superset as _enforced_data
}

test_can_read_dashboard_when_allowall if {
	superset.can_read_dashboard with data.backends.superset as _allow_all_data
}

test_can_read_dashboard_denied_when_enforced if {
	not superset.can_read_dashboard with data.backends.superset as _enforced_data
}

test_can_write_chart_when_allowall if {
	superset.can_write_chart with data.backends.superset as _allow_all_data
}

test_can_write_chart_denied_when_enforced if {
	not superset.can_write_chart with data.backends.superset as _enforced_data
}

test_denied_when_backend_data_missing if {
	not superset.can_access_schema with data.backends as {}
}

# =============================================================================
# UNCONDITIONAL RULES (not part of the allow-all gate)
# =============================================================================

test_can_access_database_always_true if {
	superset.can_access_database with data.backends.superset as _enforced_data
}

# =============================================================================
# REAL PERMISSION EVALUATION (enforced mode, AuthZ Repository mocked)
# =============================================================================
# CivitasSupersetSecurityManager posts {"user": <sub>, ...} directly — no
# X-Userinfo header to decode — so these mocks only need to answer the
# fetch_context(input.user) and dataset_pool_fetcher.pool_of() http.send calls.

mock_send_dataset_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", "ds-1")}

mock_send_tenant_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "TENANT", "tenant-1")}

mock_send_dashboard_dataset_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", "ds-1")}

mock_send_dashboard_pool_update(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_UPDATE"], "DATAPOOL", "pool-1")}

test_dashboard_users_with_dataset_read if {
	superset.dashboard_users
		with data.config as mock_http.mock_config
		with input as {"user": "u1"}
		with http.send as mock_send_dashboard_dataset_read
}

test_dashboard_users_with_pool_update if {
	superset.dashboard_users
		with data.config as mock_http.mock_config
		with input as {"user": "u1"}
		with http.send as mock_send_dashboard_pool_update
}

test_dashboard_users_denied_without_dataset_right if {
	not superset.dashboard_users
		with data.config as mock_http.mock_config
		with input as {"user": "u1"}
		with http.send as mock_send_dataset_read
}

test_dashboard_users_denied_without_identity if {
	not superset.dashboard_users with input as {}
}

mock_send_dashboard_write(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_UPDATE"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dashboard_write(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-1"}} if {
	req.method == "GET"
	contains(req.url, "dashboard-dataset")
}

# Same grant as mock_send_dashboard_write (DATASET_DASHBOARD_UPDATE for ds-1), but the dashboard
# itself resolves to a different dataset — for the "denied, wrong dataset" tests.
mock_send_dashboard_write_wrong_dataset(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_UPDATE"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dashboard_write_wrong_dataset(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-2"}} if {
	req.method == "GET"
	contains(req.url, "dashboard-dataset")
}

mock_send_dashboard_read(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_READ"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dashboard_read(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-1"}} if {
	req.method == "GET"
	contains(req.url, "dashboard-dataset")
}

# Same grant as mock_send_dashboard_read (DATASET_DASHBOARD_READ for ds-1), but the dashboard
# itself resolves to a different dataset — for the "denied, wrong dataset" test.
mock_send_dashboard_read_wrong_dataset(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_READ"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dashboard_read_wrong_dataset(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-2"}} if {
	req.method == "GET"
	contains(req.url, "dashboard-dataset")
}

mock_send_dataset_write(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_UPDATE"], "DATASET", "ds-1")}

mock_send_open_dataset(_) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true, "available": true}}

mock_send_open_draft_dataset(_) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true, "available": false}}

mock_send_closed_dataset(_) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": false}}

mock_send_open_data_no_permissions(req) := {"status_code": 200, "body": mock_http.user_no_permissions} if {
	contains(req.url, "user-context")
}

mock_send_open_data_no_permissions(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true, "available": true}} if {
	contains(req.url, "dataset-pool")
}

mock_send_dashboard_all_open(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true, "available": true}} if {
	contains(req.url, "dataset-pool")
}

mock_send_dashboard_mixed(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true, "available": true}} if {
	contains(req.url, "ds-open")
}

mock_send_dashboard_mixed(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": false}} if {
	contains(req.url, "ds-closed")
}

# DATAPOOL union: two http.send targets mocked by URL, mirroring
# permission_eval_test.rego's test_union_allows_dataset_in_granted_pool.
mock_send_datapool_in_pool(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

mock_send_datapool_in_pool(req) := {"status_code": 200, "body": {"poolId": "pool-1"}} if {
	contains(req.url, "dataset-pool")
}

mock_send_datapool_other_pool(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

mock_send_datapool_other_pool(req) := {"status_code": 200, "body": {"poolId": "pool-2"}} if {
	contains(req.url, "dataset-pool")
}

mock_send_tenant_read_dashboard_write(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_UPDATE"], "TENANT", "tenant-1")} if {
	contains(req.url, "user-context")
}

# Tenant-wide grants cover every dataset, so which dataset the dashboard actually resolves to
# doesn't matter for these tests — any value works, mirroring the old direct
# "civitasDatasetId": "ds-anything" input.
mock_send_tenant_read_dashboard_write(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-anything"}} if {
	req.method == "GET"
	contains(req.url, "dashboard-dataset")
}

mock_send_datapool_dashboard_write(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_UPDATE"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

mock_send_datapool_dashboard_write(req) := {"status_code": 200, "body": {"poolId": "pool-1"}} if {
	contains(req.url, "dataset-pool")
}

mock_send_datapool_dashboard_write(req) := {"status_code": 200, "body": {"civitasDatasetId": "ds-99"}} if {
	req.method == "GET"
	contains(req.url, "dashboard-dataset")
}

test_can_access_schema_granted_via_matching_dataset if {
	superset.can_access_schema with data.backends.superset as _enforced_data
		with http.send as mock_send_dataset_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-1"}
}

test_can_access_schema_denied_wrong_dataset if {
	not superset.can_access_schema with data.backends.superset as _enforced_data
		with http.send as mock_send_dataset_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-2"}
}

test_can_access_datasource_granted_via_tenant_cascade if {
	superset.can_access_datasource with data.backends.superset as _enforced_data
		with http.send as mock_send_tenant_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-anything"}
}

# An unscoped (scopeType=null) assignment is a SYSTEM role, e.g. Tenant Admin — distinct from a
# TENANT-scoped DATA role — and must NOT cascade to dataset-scoped permissions here, unlike
# permission_eval.rego's REST API scope inheritance (see has_tenant_permission's comment).
mock_send_unscoped_read(_) := {"status_code": 200, "body": mock_http.user_with_unscoped_permissions(["DATASET_PAYLOAD_READ"])}

test_can_access_datasource_denied_via_unscoped_system_role if {
	not superset.can_access_datasource with data.backends.superset as _enforced_data
		with http.send as mock_send_unscoped_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-anything"}
}

test_can_access_datasource_granted_via_datapool_union if {
	superset.can_access_datasource with data.backends.superset as _enforced_data
		with http.send as mock_send_datapool_in_pool
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-99"}
}

test_can_access_datasource_denied_different_pool if {
	not superset.can_access_datasource with data.backends.superset as _enforced_data
		with http.send as mock_send_datapool_other_pool
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-99"}
}

test_can_access_schema_granted_for_open_data_anonymous if {
	superset.can_access_schema with data.backends.superset as _enforced_data
		with http.send as mock_send_open_dataset
		with data.config as mock_http.mock_config
		with input as {"civitasDatasetId": "ds-open"}
}

test_can_access_schema_denied_for_open_draft_anonymous if {
	not superset.can_access_schema with data.backends.superset as _enforced_data
		with http.send as mock_send_open_draft_dataset
		with data.config as mock_http.mock_config
		with input as {"civitasDatasetId": "ds-open"}
}

test_can_access_datasource_granted_for_open_data_authenticated_without_grant if {
	superset.can_access_datasource with data.backends.superset as _enforced_data
		with http.send as mock_send_open_data_no_permissions
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-open"}
}

test_can_access_datasource_denied_for_closed_data_anonymous if {
	not superset.can_access_datasource with data.backends.superset as _enforced_data
		with http.send as mock_send_closed_dataset
		with data.config as mock_http.mock_config
		with input as {"civitasDatasetId": "ds-closed"}
}

test_can_access_dashboard_granted_when_all_datasets_open if {
	superset.can_access_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_all_open
		with data.config as mock_http.mock_config
		with input as {"dashboard_dataset_ids": ["ds-open-1", "ds-open-2"]}
}

test_can_access_dashboard_denied_when_any_dataset_closed if {
	not superset.can_access_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_mixed
		with data.config as mock_http.mock_config
		with input as {"dashboard_dataset_ids": ["ds-open", "ds-closed"]}
}

test_can_access_dashboard_denied_when_dataset_list_empty if {
	not superset.can_access_dashboard with data.backends.superset as _enforced_data
		with input as {"dashboard_dataset_ids": []}
}

test_can_write_dashboard_granted_via_matching_dataset if {
	superset.can_write_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6"}
}

test_can_write_dashboard_denied_wrong_dataset if {
	not superset.can_write_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_write_wrong_dataset
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6"}
}

# Fail-secure: an unknown/never-synced dashboard (no matching Dashboard row in the platform DB,
# e.g. the resolution lookup 404s) must deny even a real, otherwise-valid grant — there's no
# dataset to check the grant against.
mock_send_dashboard_unresolvable(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_UPDATE"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dashboard_unresolvable(req) := {"status_code": 404, "body": {}} if {
	req.method == "GET"
	contains(req.url, "dashboard-dataset")
}

test_can_write_dashboard_denied_when_dashboard_unresolvable if {
	not superset.can_write_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_unresolvable
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "999"}
}

test_can_read_dashboard_granted_via_matching_dataset if {
	superset.can_read_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6", "published": true}
}

test_can_read_dashboard_denied_wrong_dataset if {
	not superset.can_read_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_read_wrong_dataset
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6", "published": true}
}

# A draft (unpublished) dashboard is visible only to a writer — see can_read_dashboard's comment.
# A read-only grant is not enough while the dashboard is unpublished.
test_can_read_dashboard_denied_for_unpublished_draft_with_read_only if {
	not superset.can_read_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6", "published": false}
}

# Write implies read: a user with only DATASET_DASHBOARD_UPDATE (no explicit READ grant) can still
# read — editing a dashboard already requires opening it. Unconditional on published state: a
# writer must be able to see their own draft.
test_can_read_dashboard_granted_via_write_permission if {
	superset.can_read_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6", "published": false}
}

test_can_read_dashboard_granted_via_tenant_cascade if {
	superset.can_read_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_tenant_read_dashboard_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6", "published": false}
}

test_can_write_dashboard_granted_via_tenant_cascade if {
	superset.can_write_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_tenant_read_dashboard_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6"}
}

test_can_write_dashboard_granted_via_datapool_union if {
	superset.can_write_dashboard with data.backends.superset as _enforced_data
		with http.send as mock_send_datapool_dashboard_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "externalDashboardId": "6"}
}

test_can_write_chart_granted_via_matching_dataset if {
	superset.can_write_chart with data.backends.superset as _enforced_data
		with http.send as mock_send_dataset_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-1"}
}

# Service-account-only rule: even a user with a matching DATASET_UPDATE grant
# (and even a TENANT-wide one) must never be allowed to write a dataset —
# only the config-adapter service account may, and that path never reaches
# OPA (see the comment on superset.can_write_dataset).
test_can_write_dataset_denied_despite_valid_grant if {
	not superset.can_write_dataset with data.backends.superset as _enforced_data
		with http.send as mock_send_dataset_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-1"}
}

test_can_write_dataset_denied_despite_tenant_grant if {
	not superset.can_write_dataset with data.backends.superset as _enforced_data
		with http.send as mock_send_tenant_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "civitasDatasetId": "ds-1"}
}

test_schema_access_lists_direct_dataset_grants if {
	result := superset.schema_access with data.backends.superset as _enforced_data
		with http.send as mock_send_dataset_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1"}
	result == {"allowed_schemas": {"ds-1"}, "is_tenant_reader": false}
}

test_schema_access_tenant_reader_via_tenant_grant if {
	result := superset.schema_access with data.backends.superset as _enforced_data
		with http.send as mock_send_tenant_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1"}
	result == {"allowed_schemas": set(), "is_tenant_reader": true}
}

# DATAPOOL-scoped grant: resolved via ONE POST to the reverse (pool -> datasets) endpoint,
# disambiguated from the single dataset->pool GET by method, since both share the "dataset-pool"
# URL substring.
mock_send_datapool_schema_listing(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

mock_send_datapool_schema_listing(req) := {"status_code": 200, "body": {"datasetIds": ["ds-1", "ds-2"]}} if {
	req.method == "POST"
	contains(req.url, "dataset-pool")
}

test_schema_access_includes_datapool_union if {
	result := superset.schema_access with data.backends.superset as _enforced_data
		with http.send as mock_send_datapool_schema_listing
		with data.config as mock_http.mock_config
		with input as {"user": "u1"}
	result == {"allowed_schemas": {"ds-1", "ds-2"}, "is_tenant_reader": false}
}

# Fail-secure: a fetch outage on the reverse lookup must not surface as an error, and must not
# grant anything beyond whatever direct grants the caller separately holds.
mock_send_datapool_schema_listing_outage(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

mock_send_datapool_schema_listing_outage(req) := {"status_code": 500, "body": {}} if {
	req.method == "POST"
	contains(req.url, "dataset-pool")
}

test_schema_access_empty_when_reverse_lookup_fails if {
	result := superset.schema_access with data.backends.superset as _enforced_data
		with http.send as mock_send_datapool_schema_listing_outage
		with data.config as mock_http.mock_config
		with input as {"user": "u1"}
	result == {"allowed_schemas": set(), "is_tenant_reader": false}
}

# Two candidates shared by the tests below: "6" (dataset ds-1, published) and "7" (dataset ds-1,
# still a draft). Each mock resolves both via ONE batch call, then supplies whatever user-context
# grant that specific test needs.
_dashboard_list_candidates := [
	{"externalDashboardId": "6", "published": true},
	{"externalDashboardId": "7", "published": false},
]

_mock_dashboard_batch_ds1(req) := {"status_code": 200, "body": [
	{"externalDashboardId": "6", "civitasDatasetId": "ds-1"},
	{"externalDashboardId": "7", "civitasDatasetId": "ds-1"},
]} if {
	req.method == "POST"
	contains(req.url, "dashboard-dataset")
}

mock_send_dashboard_list_read(req) := _mock_dashboard_batch_ds1(req)

mock_send_dashboard_list_read(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_READ"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dashboard_list_write(req) := _mock_dashboard_batch_ds1(req)

mock_send_dashboard_list_write(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_UPDATE"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dashboard_list_tenant_write(req) := _mock_dashboard_batch_ds1(req)

mock_send_dashboard_list_tenant_write(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_UPDATE"], "TENANT", "tenant-1")} if {
	contains(req.url, "user-context")
}

mock_send_dashboard_list_unrelated(req) := _mock_dashboard_batch_ds1(req)

mock_send_dashboard_list_unrelated(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

test_dashboard_list_access_read_only_sees_published_not_draft if {
	result := superset.dashboard_list_access with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_list_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "dashboards": _dashboard_list_candidates}
	result == {"allowedDashboardIds": {"6"}}
}

# Write also sees the draft — see can_read_dashboard's comment for why a plain reader shouldn't.
test_dashboard_list_access_write_sees_both_published_and_draft if {
	result := superset.dashboard_list_access with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_list_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "dashboards": _dashboard_list_candidates}
	result == {"allowedDashboardIds": {"6", "7"}}
}

test_dashboard_list_access_tenant_writer_sees_both_published_and_draft if {
	result := superset.dashboard_list_access with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_list_tenant_write
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "dashboards": _dashboard_list_candidates}
	result == {"allowedDashboardIds": {"6", "7"}}
}

test_dashboard_list_access_empty_for_unrelated_grant if {
	result := superset.dashboard_list_access with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_list_unrelated
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "dashboards": _dashboard_list_candidates}
	result == {"allowedDashboardIds": set()}
}

test_dashboard_list_access_empty_when_no_candidates if {
	result := superset.dashboard_list_access with data.backends.superset as _enforced_data
		with http.send as mock_send_dashboard_list_read
		with data.config as mock_http.mock_config
		with input as {"user": "u1", "dashboards": []}
	result == {"allowedDashboardIds": set()}
}

test_dashboard_list_access_allow_all_includes_every_candidate if {
	result := superset.dashboard_list_access with data.backends.superset as _allow_all_data
		with input as {"user": "u1", "dashboards": _dashboard_list_candidates}
	result == {"allowedDashboardIds": {"6", "7"}}
}
