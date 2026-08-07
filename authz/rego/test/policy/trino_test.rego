# Tests for trino.rego — Trino system access control entry point.

package civitas.trino_test

import rego.v1

import data.civitas.trino
import data.test.helpers.mock_http

_allow_all_data := {"data_permissions": null}

_enforced_data := {"data_permissions": ["DATASET_READ", "DATASET_PAYLOAD_READ"]}

# Digit-first UUID: WorkspaceNames.fromDatasetId adds the ds_ prefix (keeps
# the name a valid XML NCName).
_digit_first_dataset_id := "5b3f7428-9004-4b76-becc-1538eb3b8388"

_digit_first_schema := "ds_5b3f7428_9004_4b76_becc_1538eb3b8388"

# Letter-first UUID: no prefix needed.
_letter_first_dataset_id := "af3f7428-9004-4b76-becc-1538eb3b8388"

_letter_first_schema := "af3f7428_9004_4b76_becc_1538eb3b8388"

# =============================================================================
# ALLOW-ALL GATE
# =============================================================================

test_allowed_when_allowall_data_mounted if {
	trino.allow with data.backends.trino as _allow_all_data
		with input as {"action": {"operation": "ExecuteQuery"}}
}

test_denied_when_real_data_mounted_no_identity if {
	not trino.allow with data.backends.trino as _enforced_data
		with input as {"action": {"operation": "ExecuteQuery"}}
}

test_denied_when_backend_data_missing if {
	not trino.allow with data.backends as {}
		with input as {"action": {"operation": "ExecuteQuery"}}
}

# =============================================================================
# SCHEMA NAME DECODE — civitas_dataset_id()
# =============================================================================
# Pure/local, no http.send involved.

test_decode_digit_first_uuid if {
	trino.civitas_dataset_id(_digit_first_schema) == _digit_first_dataset_id
}

test_decode_letter_first_uuid if {
	trino.civitas_dataset_id(_letter_first_schema) == _letter_first_dataset_id
}

test_decode_undefined_for_information_schema if {
	not trino.civitas_dataset_id("information_schema")
}

test_decode_undefined_for_wrong_length if {
	not trino.civitas_dataset_id("ds_5b3f7428_9004_4b76_becc_1538eb3b83")
}

test_decode_undefined_for_ds_prefixed_but_not_uuid_shaped if {
	not trino.civitas_dataset_id("ds_not_a_uuid_at_all")
}

# "ds" can never be organic decoded UUID content ("s" isn't hex), so a
# genuinely unprefixed schema that happens to start with "ds_" is impossible
# to construct from a real UUID — this documents why civitas_dataset_id()
# doesn't need to disambiguate the two startswith branches any further.
test_decode_undefined_for_malformed_ds_prefix if {
	not trino.civitas_dataset_id("ds_zzzzzzzz_9004_4b76_becc_1538eb3b8388")
}

# =============================================================================
# MOCKS — dispatch by URL; a single evaluation can touch the user-context and
# (for DATAPOOL union) dataset-pool endpoints.
# =============================================================================

mock_send_dataset_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", _digit_first_dataset_id)}

mock_send_tenant_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "TENANT", "tenant-1")}

mock_send_no_permissions(_) := {"status_code": 200, "body": mock_http.user_no_permissions}

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

mock_send_valid_target(_) := {"status_code": 200, "body": {"userId": "u2", "externalId": "u2", "groups": []}}

mock_send_unresolvable_target(_) := {"status_code": 404, "body": {}}

mock_send_open_dataset_attrs(_) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true}}

mock_send_closed_dataset_attrs(_) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": false}}

# =============================================================================
# IMPERSONATE_USER
# =============================================================================

test_impersonate_allowed_from_trusted_account if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_valid_target
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "superset_trino"}},
			"action": {"operation": "ImpersonateUser", "resource": {"user": {"user": "u2"}}},
		}
}

test_impersonate_denied_from_untrusted_account if {
	not trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_valid_target
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "some_random_user"}},
			"action": {"operation": "ImpersonateUser", "resource": {"user": {"user": "u2"}}},
		}
}

test_impersonate_denied_for_unresolvable_target if {
	not trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_unresolvable_target
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "superset_trino"}},
			"action": {"operation": "ImpersonateUser", "resource": {"user": {"user": "nobody"}}},
		}
}

test_impersonators_configurable_via_data_config if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_valid_target
		with data.config as object.union(mock_http.mock_config, {"trino_impersonator_users": ["custom_service_account"]})
		with input as {
			"context": {"identity": {"user": "custom_service_account"}},
			"action": {"operation": "ImpersonateUser", "resource": {"user": {"user": "u2"}}},
		}
}

# =============================================================================
# CATALOG/QUERY-LEVEL ACTIONS (no dataset-scoped resource)
# =============================================================================

test_execute_query_allowed_when_authenticated if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_no_permissions
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "ExecuteQuery"},
		}
}

test_access_catalog_allowed_when_authenticated if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_no_permissions
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "AccessCatalog", "resource": {"catalog": {"name": "civitas"}}},
		}
}

test_execute_query_denied_when_unresolvable_identity if {
	not trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_unresolvable_target
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "ExecuteQuery"},
		}
}

test_catalog_actions_allowed_for_trusted_raw_superset_identity if {
	every op in {"ExecuteQuery", "AccessCatalog", "FilterCatalogs", "ShowSchemas", "ShowCreateSchema"} {
		trino.allow with data.backends.trino as _enforced_data
			with input as {
				"context": {"identity": {"user": "superset_trino"}},
				"action": {"operation": op},
			}
	}
}

# =============================================================================
# SCHEMA/TABLE ACTIONS — non-Civitas schema
# =============================================================================

test_filter_schemas_allowed_for_information_schema if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_no_permissions
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "FilterSchemas", "resource": {"schema": {"catalogName": "civitas", "schemaName": "information_schema"}}},
		}
}

# =============================================================================
# SCHEMA/TABLE ACTIONS — real Civitas dataset schema
# =============================================================================

test_select_from_columns_granted_via_matching_dataset if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_dataset_read
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "SelectFromColumns", "resource": {"table": {
				"catalogName": "civitas",
				"schemaName": _digit_first_schema,
				"tableName": "baeume",
			}}},
		}
}

test_filter_tables_denied_no_permissions if {
	not trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_no_permissions
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "FilterTables", "resource": {"table": {
				"catalogName": "civitas",
				"schemaName": _digit_first_schema,
				"tableName": "baeume",
			}}},
		}
}

test_filter_schemas_granted_via_tenant_cascade if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_tenant_read
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "FilterSchemas", "resource": {"schema": {"catalogName": "civitas", "schemaName": _digit_first_schema}}},
		}
}

# An unscoped (scopeType=null) assignment is a SYSTEM role, e.g. Tenant Admin — distinct from a
# TENANT-scoped DATA role — and must NOT cascade to dataset-scoped permissions here, unlike
# permission_eval.rego's REST API scope inheritance (see has_tenant_permission's comment).
mock_send_unscoped_read(_) := {"status_code": 200, "body": mock_http.user_with_unscoped_permissions(["DATASET_PAYLOAD_READ"])}

test_filter_schemas_denied_via_unscoped_system_role if {
	not trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_unscoped_read
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "FilterSchemas", "resource": {"schema": {"catalogName": "civitas", "schemaName": _digit_first_schema}}},
		}
}

test_show_columns_granted_via_datapool_union if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_datapool_in_pool
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "ShowColumns", "resource": {"table": {
				"catalogName": "civitas",
				"schemaName": _digit_first_schema,
				"tableName": "baeume",
			}}},
		}
}

test_show_create_table_denied_different_pool if {
	not trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_datapool_other_pool
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "ShowCreateTable", "resource": {"table": {
				"catalogName": "civitas",
				"schemaName": _digit_first_schema,
				"tableName": "baeume",
			}}},
		}
}

test_select_from_columns_granted_for_open_data_without_identity if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_open_dataset_attrs
		with data.config as mock_http.mock_config
		with input as {
			"action": {"operation": "SelectFromColumns", "resource": {"table": {
				"catalogName": "civitas",
				"schemaName": _digit_first_schema,
				"tableName": "baeume",
			}}},
		}
}

test_select_from_columns_denied_for_closed_data_without_identity if {
	not trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_closed_dataset_attrs
		with data.config as mock_http.mock_config
		with input as {
			"action": {"operation": "SelectFromColumns", "resource": {"table": {
				"catalogName": "civitas",
				"schemaName": _digit_first_schema,
				"tableName": "baeume",
			}}},
		}
}

test_select_from_columns_granted_via_matching_dataset_letter_first if {
	trino.allow with data.backends.trino as _enforced_data
		with http.send as mock_send_letter_first_dataset_read
		with data.config as mock_http.mock_config
		with input as {
			"context": {"identity": {"user": "u1"}},
			"action": {"operation": "SelectFromColumns", "resource": {"table": {
				"catalogName": "civitas",
				"schemaName": _letter_first_schema,
				"tableName": "baeume",
			}}},
		}
}

mock_send_letter_first_dataset_read(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", _letter_first_dataset_id)}

# =============================================================================
# LOCAL ADMIN BYPASS (config-adapter's own Superset identity)
# =============================================================================
# No http.send/data.config mocking needed for the default-identity cases: without
# data.config.authz_repository_url mounted, fetch_context's own url binding is undefined, so the
# normal permission paths are simply undefined (not an error) — exactly like
# test_catalog_actions_allowed_for_trusted_raw_superset_identity above relies on. Only the
# local-admin-specific allow branch can fire.

test_impersonate_allowed_for_local_admin_target if {
	trino.allow with data.backends.trino as _enforced_data
		with input as {
			"context": {"identity": {"user": "superset_trino"}},
			"action": {"operation": "ImpersonateUser", "resource": {"user": {"user": "civitas-config-adapter"}}},
		}
}

test_impersonate_local_admin_target_denied_from_untrusted_account if {
	not trino.allow with data.backends.trino as _enforced_data
		with input as {
			"context": {"identity": {"user": "some_random_user"}},
			"action": {"operation": "ImpersonateUser", "resource": {"user": {"user": "civitas-config-adapter"}}},
		}
}

test_catalog_actions_allowed_for_local_admin_identity if {
	every op in {"ExecuteQuery", "AccessCatalog", "FilterCatalogs", "ShowSchemas", "ShowCreateSchema"} {
		trino.allow with data.backends.trino as _enforced_data
			with input as {
				"context": {"identity": {"user": "civitas-config-adapter"}},
				"action": {"operation": op},
			}
	}
}

test_schema_scoped_actions_allowed_for_local_admin_identity if {
	every op in {"FilterSchemas", "ShowTables"} {
		trino.allow with data.backends.trino as _enforced_data
			with input as {
				"context": {"identity": {"user": "civitas-config-adapter"}},
				"action": {"operation": op, "resource": {"schema": {"catalogName": "civitas", "schemaName": _digit_first_schema}}},
			}
	}
}

test_table_scoped_actions_allowed_for_local_admin_identity if {
	every op in {"FilterColumns", "FilterTables", "SelectFromColumns", "ShowColumns", "ShowCreateTable"} {
		trino.allow with data.backends.trino as _enforced_data
			with input as {
				"context": {"identity": {"user": "civitas-config-adapter"}},
				"action": {"operation": op, "resource": {"table": {
					"catalogName": "civitas",
					"schemaName": _digit_first_schema,
					"tableName": "baeume",
				}}},
			}
	}
}

test_local_admin_bypass_configurable_via_data_config if {
	trino.allow with data.backends.trino as _enforced_data
		with data.config as {"trino_local_admin_user": "custom-adapter"}
		with input as {
			"context": {"identity": {"user": "custom-adapter"}},
			"action": {"operation": "ExecuteQuery"},
		}
}

test_default_local_admin_user_denied_once_overridden if {
	not trino.allow with data.backends.trino as _enforced_data
		with data.config as {"trino_local_admin_user": "custom-adapter"}
		with input as {
			"context": {"identity": {"user": "civitas-config-adapter"}},
			"action": {"operation": "ExecuteQuery"},
		}
}
