# Endpoint scope-coverage tests for portal-backend sub-resources.
#
# Covers the nested dataset sub-resources (datasinks, layers, styles, pipelines)
# with the same allow / wrong-scope-deny / unauthenticated-deny checks the top-level
# resources get, and the AND-permission group-assignments endpoint. These are gated
# by DATASET_READ / DATASET_UPDATE on the parent dataset (the {id} is the scopeId).

package civitas.authz.endpoint_coverage_test

import rego.v1

import data.civitas.authz
import data.test.helpers.mock_http

portal_request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")},
	},
	"service": {"name": "portal-backend"},
}

portal_request_no_auth(method, path) := {
	"request": {"method": method, "path": path, "headers": {}},
	"service": {"name": "portal-backend"},
}

mock_send_dataset_abc_reader(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", "dataset-abc")}
mock_send_dataset_abc_editor(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_UPDATE"], "DATASET", "dataset-abc")}
mock_send_dataset_other_reader(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", "other-dataset")}

# GET sub-resource paths under /datasets/{id}, all gated by DATASET_READ on the dataset.
# Visualizations' GET is included here too: unlike its own write side (POST/PUT/DELETE, gated by
# the separate DATASET_DASHBOARD_UPDATE — see the VISUALIZATIONS SUB-RESOURCE section below), its
# read side was deliberately made consistent with every other sub-resource so that anyone who can
# see the dataset sees all of its content, rather than needing a second, independent grant just to
# view (not build) the dashboards it has.
dataset_read_subresource_paths := {
	"/v1/datasets/dataset-abc/datasinks",
	"/v1/datasets/dataset-abc/datasinks/sink-1",
	"/v1/datasets/dataset-abc/layers",
	"/v1/datasets/dataset-abc/layers/layer-1",
	"/v1/datasets/dataset-abc/styles",
	"/v1/datasets/dataset-abc/styles/style-1",
	"/v1/datasets/dataset-abc/pipelines",
	"/v1/datasets/dataset-abc/pipelines/pipe-1",
	"/v1/datasets/dataset-abc/visualizations",
	"/v1/datasets/dataset-abc/visualizations/vis-1",
}

# The matching DATASET scope grants read and carries the dataset id as the scope header.
test_dataset_subresources_allowed_with_dataset_scope if {
	every path in dataset_read_subresource_paths {
		result := authz.decision with http.send as mock_send_dataset_abc_reader
			with data.config as mock_http.mock_config
			with input as portal_request("GET", path)
		result.allow == true
		result.reason == "permission_granted"
		result.headers["X-Allowed-Scope-Ids"] == "dataset-abc"
	}
}

# A DATASET scope for a different dataset must not grant access to these sub-resources.
test_dataset_subresources_denied_wrong_scope if {
	every path in dataset_read_subresource_paths {
		result := authz.decision with http.send as mock_send_dataset_other_reader
			with data.config as mock_http.mock_config
			with input as portal_request("GET", path)
		result.allow == false
		result.reason == "permission_denied"
	}
}

# Unauthenticated requests to the sub-resources are denied.
test_dataset_subresources_denied_unauthenticated if {
	every path in dataset_read_subresource_paths {
		result := authz.decision with input as portal_request_no_auth("GET", path)
		result.allow == false
	}
}

# Writing a sub-resource requires DATASET_UPDATE, not merely DATASET_READ.
test_dataset_subresource_write_requires_update if {
	result := authz.decision with http.send as mock_send_dataset_abc_reader
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets/dataset-abc/datasinks")
	result.allow == false
	result.reason == "permission_denied"
}

test_dataset_subresource_write_allowed_with_update if {
	result := authz.decision with http.send as mock_send_dataset_abc_editor
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets/dataset-abc/datasinks")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE"}
}

# =============================================================================
# GROUP-ASSIGNMENTS AND-PERMISSION ENDPOINT
# =============================================================================
# PUT /v1/groups/{id}/assignments requires BOTH ASSIGNMENT_CREATE and
# ASSIGNMENT_DELETE (a tenant-scoped resource).

mock_send_assignment_both(_) := {"status_code": 200, "body": mock_http.user_with_permissions(["ASSIGNMENT_CREATE", "ASSIGNMENT_DELETE"])}
mock_send_assignment_create_only(_) := {"status_code": 200, "body": mock_http.user_with_permissions(["ASSIGNMENT_CREATE"])}

test_group_assignments_and_permission_allowed if {
	result := authz.decision with http.send as mock_send_assignment_both
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/groups/group-1/assignments")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"ASSIGNMENT_CREATE", "ASSIGNMENT_DELETE"}
}

test_group_assignments_and_permission_partial_denied if {
	result := authz.decision with http.send as mock_send_assignment_create_only
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/groups/group-1/assignments")
	result.allow == false
	result.reason == "permission_denied"
}

# =============================================================================
# VISUALIZATIONS SUB-RESOURCE — WRITE SIDE
# =============================================================================
# /v1/datasets/{id}/visualizations(/{id})'s GET is covered above, alongside every other
# sub-resource (DATASET_READ). Its write side (POST/PUT/DELETE) now uses DATASET_UPDATE, same as
# every other dataset sub-resource (see mock_send_dataset_abc_editor above) — previously it stayed
# on its own DATASET_DASHBOARD_UPDATE permission so dashboard authoring could be granted
# independently of general dataset editing, but that split was reversed: DATASET_DASHBOARD_UPDATE
# now governs only Superset dashboard read/write (civitas_security.py/superset.rego), not this
# portal-backend resource.

mock_send_dataset_abc_dashboard_reader(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_READ"], "DATASET", "dataset-abc")}

mock_send_dataset_abc_dashboard_writer(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_DASHBOARD_UPDATE"], "DATASET", "dataset-abc")}

# Regression guard: DATASET_DASHBOARD_READ alone (without DATASET_READ) must NOT grant GET —
# reading a visualization list is DATASET_READ's job now, not this permission's.
test_visualizations_read_denied_with_only_dashboard_read_permission if {
	result := authz.decision with http.send as mock_send_dataset_abc_dashboard_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets/dataset-abc/visualizations")
	result.allow == false
	result.reason == "permission_denied"
}

# Regression guard: DATASET_DASHBOARD_UPDATE alone (without DATASET_UPDATE) must NOT grant writes
# here anymore — that permission only governs Superset dashboard read/write, not this resource.
test_visualizations_write_denied_with_only_dashboard_update_permission if {
	result := authz.decision with http.send as mock_send_dataset_abc_dashboard_writer
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets/dataset-abc/visualizations")
	result.allow == false
	result.reason == "permission_denied"
}

test_visualizations_write_allowed_with_dataset_update if {
	result := authz.decision with http.send as mock_send_dataset_abc_editor
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/datasets/dataset-abc/visualizations")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE"}
}

test_visualizations_delete_allowed_with_dataset_update if {
	result := authz.decision with http.send as mock_send_dataset_abc_editor
		with data.config as mock_http.mock_config
		with input as portal_request("DELETE", "/v1/datasets/dataset-abc/visualizations/vis-1")
	result.allow == true
	result.reason == "permission_granted"
}
