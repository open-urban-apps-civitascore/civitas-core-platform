# Tests for dataset-schema and metadata row filters in trino_row_filters.rego.

package civitas.trino_row_filters_test

import rego.v1

import data.civitas.trino.row_filters
import data.test.helpers.mock_http

_enforced_data := {"data_permissions": ["DATASET_PAYLOAD_READ"]}
_allow_all_data := {"data_permissions": null}

_dataset_id := "5b3f7428-9004-4b76-becc-1538eb3b8388"
_dataset_schema := "ds_5b3f7428_9004_4b76_becc_1538eb3b8388"

dataset_table_request(user) := {
	"context": {"identity": {"user": user}},
	"action": {
		"operation": "SelectFromColumns",
		"resource": {"table": {"catalogName": "sensordata", "schemaName": _dataset_schema, "tableName": "observations_scoped"}},
	},
}

shared_source_request(user) := {
	"context": {"identity": {"user": user}},
	"action": {
		"operation": "SelectFromColumns",
		"resource": {"table": {"catalogName": "sensordata", "schemaName": "frost_scoped", "tableName": "observations_scoped"}},
	},
}

mock_send_dataset_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", _dataset_id)}

mock_send_no_permissions(_) := {"status_code": 200, "body": mock_http.user_no_permissions}

mock_send_public_dataset(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true, "available": true}} if {
	contains(req.url, "dataset-pool")
}

test_allow_all_returns_true_for_shared_source if {
	row_filters.result == [{"expression": "true"}] with data.backends.trino as _allow_all_data
		with input as shared_source_request("u1")
}

test_dataset_read_permission_allows_own_frost_view if {
	row_filters.result == [{"expression": "true"}] with data.backends.trino as _enforced_data
		with http.send as mock_send_dataset_read
		with data.config as mock_http.mock_config
		with input as dataset_table_request("u1")
}

test_unreadable_frost_view_gets_false_row_filter if {
	row_filters.result == [{"expression": "false"}] with data.backends.trino as _enforced_data
		with http.send as mock_send_no_permissions
		with data.config as mock_http.mock_config
		with input as dataset_table_request("u1")
}

test_public_available_frost_view_allows_anonymous_query if {
	row_filters.result == [{"expression": "true"}] with data.backends.trino as _enforced_data
		with http.send as mock_send_public_dataset
		with data.config as mock_http.mock_config
		with input as dataset_table_request("")
}

test_shared_source_schema_is_denied_when_enforced if {
	row_filters.result == [{"expression": "false"}] with data.backends.trino as _enforced_data
		with input as shared_source_request("u1")
}
