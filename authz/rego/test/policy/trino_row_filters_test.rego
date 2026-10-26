# Tests for trino_row_filters.rego — Trino's row-filter entry point for frost_scoped.* views.

package civitas.trino_row_filters_test

import rego.v1

import data.civitas.trino.row_filters
import data.test.helpers.mock_http

_enforced_data := {"data_permissions": ["DATASET_PAYLOAD_READ"]}

_allow_all_data := {"data_permissions": null}

frost_table_request(user) := {
	"context": {"identity": {"user": user}},
	"action": {
		"operation": "SelectFromColumns",
		"resource": {"table": {"catalogName": "sensordata", "schemaName": "frost_scoped", "tableName": "observations_scoped"}},
	},
}

non_frost_table_request(user) := {
	"context": {"identity": {"user": user}},
	"action": {
		"operation": "SelectFromColumns",
		"resource": {"table": {"catalogName": "geodata", "schemaName": "ds_5b3f7428-9004-4b76-becc-1538eb3b8388", "tableName": "baeume"}},
	},
}

test_allow_all_returns_true_for_frost_table if {
	row_filters.result == [{"expression": "true"}] with data.backends.trino as _allow_all_data
		with input as frost_table_request("u1")
}

test_non_frost_table_returns_empty_result if {
	row_filters.result == [] with data.backends.trino as _allow_all_data
		with input as non_frost_table_request("u1")
}

mock_send_tenant_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "TENANT", "tenant-1")}

test_tenant_scoped_reader_sees_all_rows if {
	row_filters.result == [{"expression": "true"}] with data.backends.trino as _enforced_data
		with http.send as mock_send_tenant_read
		with data.config as mock_http.mock_config
		with input as frost_table_request("u1")
}

mock_send_dataset_read(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dataset_read(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": false, "frostProjectId": "5"}} if {
	req.method == "GET"
	contains(req.url, "dataset-pool")
}

test_dataset_scoped_reader_sees_only_own_project if {
	row_filters.result == [{"expression": "frost_project_id IN ('5')"}] with data.backends.trino as _enforced_data
		with http.send as mock_send_dataset_read
		with data.config as mock_http.mock_config
		with input as frost_table_request("u1")
}

mock_send_dataset_no_frost(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

# A dataset the caller can read, but with no FROST sink (frostProjectId null) — contributes nothing
# to the readable-project set, same as if the lookup had failed.
mock_send_dataset_no_frost(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": false, "frostProjectId": null}} if {
	req.method == "GET"
	contains(req.url, "dataset-pool")
}

test_dataset_without_frost_sink_denies_frost_rows if {
	row_filters.result == [{"expression": "false"}] with data.backends.trino as _enforced_data
		with http.send as mock_send_dataset_no_frost
		with data.config as mock_http.mock_config
		with input as frost_table_request("u1")
}

# DATAPOOL grant spanning two datasets, each with its own FROST project — the caller should see the
# union of both, in one query. This is the scenario a static per-project view could never handle.
mock_send_datapool_two_datasets(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

mock_send_datapool_two_datasets(req) := {"status_code": 200, "body": {"datasetIds": ["ds-1", "ds-2"]}} if {
	req.method == "POST"
	contains(req.url, "dataset-pool")
}

mock_send_datapool_two_datasets(req) := {"status_code": 200, "body": {"poolId": "pool-1", "openDataAccess": false, "frostProjectId": "5"}} if {
	req.method == "GET"
	contains(req.url, "dataset-pool")
	contains(req.url, "ds-1")
}

mock_send_datapool_two_datasets(req) := {"status_code": 200, "body": {"poolId": "pool-1", "openDataAccess": false, "frostProjectId": "9"}} if {
	req.method == "GET"
	contains(req.url, "dataset-pool")
	contains(req.url, "ds-2")
}

test_datapool_scoped_reader_sees_union_of_projects if {
	result := row_filters.result with data.backends.trino as _enforced_data
		with http.send as mock_send_datapool_two_datasets
		with data.config as mock_http.mock_config
		with input as frost_table_request("u1")
	count(result) == 1
	expr := result[0].expression
	startswith(expr, "frost_project_id IN (")
	contains(expr, "'5'")
	contains(expr, "'9'")
}

mock_send_no_permissions(_) := {"status_code": 200, "body": mock_http.user_no_permissions}

test_no_grant_returns_false if {
	row_filters.result == [{"expression": "false"}] with data.backends.trino as _enforced_data
		with http.send as mock_send_no_permissions
		with data.config as mock_http.mock_config
		with input as frost_table_request("u1")
}

test_unauthenticated_returns_false if {
	row_filters.result == [{"expression": "false"}] with data.backends.trino as _enforced_data
		with input as frost_table_request("")
}

# Fail-secure: an AuthZ Repository outage on the dataset-pool lookup must not surface as an error
# and must not grant anything beyond whatever's otherwise established — here, nothing at all.
mock_send_dataset_pool_outage(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", "ds-1")} if {
	contains(req.url, "user-context")
}

mock_send_dataset_pool_outage(req) := {"status_code": 500, "body": {}} if {
	req.method == "GET"
	contains(req.url, "dataset-pool")
}

test_denies_when_dataset_pool_lookup_fails if {
	row_filters.result == [{"expression": "false"}] with data.backends.trino as _enforced_data
		with http.send as mock_send_dataset_pool_outage
		with data.config as mock_http.mock_config
		with input as frost_table_request("u1")
}
