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
dataset_read_subresource_paths := {
	"/v1/datasets/dataset-abc/datasinks",
	"/v1/datasets/dataset-abc/datasinks/sink-1",
	"/v1/datasets/dataset-abc/layers",
	"/v1/datasets/dataset-abc/layers/layer-1",
	"/v1/datasets/dataset-abc/styles",
	"/v1/datasets/dataset-abc/styles/style-1",
	"/v1/datasets/dataset-abc/pipelines",
	"/v1/datasets/dataset-abc/pipelines/pipe-1",
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

mock_send_dataset_abc_creator_deleter(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ", "DATASET_CREATE", "DATASET_DELETE"], "DATASET", "dataset-abc")}

pipeline_write_requests := {
	"POST": "/v1/datasets/dataset-abc/pipelines",
	"DELETE": "/v1/datasets/dataset-abc/pipelines/pipe-1",
}

test_pipeline_create_and_delete_allowed_with_update if {
	every method, path in pipeline_write_requests {
		result := authz.decision with http.send as mock_send_dataset_abc_editor
			with data.config as mock_http.mock_config
			with input as portal_request(method, path)
		result.allow == true
		result.required_permissions == {"DATASET_UPDATE"}
	}
}

test_pipeline_create_and_delete_denied_without_update if {
	every method, path in pipeline_write_requests {
		result := authz.decision with http.send as mock_send_dataset_abc_creator_deleter
			with data.config as mock_http.mock_config
			with input as portal_request(method, path)
		result.allow == false
		result.reason == "permission_denied"
	}
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

# The next two tests are a pair: the same DATASET_UPDATE-only caller must pass on
# /ready/meta and fail on /released/meta. Either test alone still passes if the
# /ready/meta mapping silently gains DATASET_RELEASE.

test_ready_meta_allowed_with_update_alone if {
	result := authz.decision with http.send as mock_send_dataset_abc_editor
		with data.config as mock_http.mock_config
		with input as portal_request("PATCH", "/v1/datasets/dataset-abc/ready/meta")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"DATASET_UPDATE"}
	result.headers["X-Allowed-Scope-Ids"] == "dataset-abc"
}

test_released_meta_denied_with_update_alone if {
	result := authz.decision with http.send as mock_send_dataset_abc_editor
		with data.config as mock_http.mock_config
		with input as portal_request("PATCH", "/v1/datasets/dataset-abc/released/meta")
	result.allow == false
	result.reason == "permission_denied"
}

test_ready_meta_denied_with_read_only if {
	result := authz.decision with http.send as mock_send_dataset_abc_reader
		with data.config as mock_http.mock_config
		with input as portal_request("PATCH", "/v1/datasets/dataset-abc/ready/meta")
	result.allow == false
	result.reason == "permission_denied"
}

test_ready_meta_denied_unauthenticated if {
	result := authz.decision with input as portal_request_no_auth("PATCH", "/v1/datasets/dataset-abc/ready/meta")
	result.allow == false
}

# =============================================================================
# INSTALLATIONS
# =============================================================================
# An installation is a resource of the whole tenant: it has no scope of its own.
# Only a tenant-wide grant gives access. An install creates the artifacts of a
# package: Data structures and their first versions, Data sources, a Dataset and
# the sub-resources of the Dataset. So POST /v1/installations needs all
# permissions in installer_permissions (an AND list in data.json): the
# permissions that the API needs for the same work.

installer_permissions := [
	"INSTALLATION_CREATE",
	"DATASTRUCTURE_CREATE",
	"DATASTRUCTURE_UPDATE",
	"DATASOURCE_CREATE",
	"DATASET_CREATE",
	"DATASET_UPDATE",
]

# The installer permissions without the given one.
installer_permissions_without(name) := [permission | some permission in installer_permissions; permission != name]

mock_send_installation_reader(_) := {"status_code": 200, "body": mock_http.user_with_permissions(["INSTALLATION_READ"])}
mock_send_installer(_) := {"status_code": 200, "body": mock_http.user_with_permissions(installer_permissions)}
mock_send_installer_without_installation_create(_) := {"status_code": 200, "body": mock_http.user_with_permissions(installer_permissions_without("INSTALLATION_CREATE"))}
mock_send_installer_without_datastructure_create(_) := {"status_code": 200, "body": mock_http.user_with_permissions(installer_permissions_without("DATASTRUCTURE_CREATE"))}
mock_send_installer_without_datastructure_update(_) := {"status_code": 200, "body": mock_http.user_with_permissions(installer_permissions_without("DATASTRUCTURE_UPDATE"))}
mock_send_installer_without_datasource_create(_) := {"status_code": 200, "body": mock_http.user_with_permissions(installer_permissions_without("DATASOURCE_CREATE"))}
mock_send_installer_without_dataset_create(_) := {"status_code": 200, "body": mock_http.user_with_permissions(installer_permissions_without("DATASET_CREATE"))}
mock_send_installer_without_dataset_update(_) := {"status_code": 200, "body": mock_http.user_with_permissions(installer_permissions_without("DATASET_UPDATE"))}
mock_send_pool_installer(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(installer_permissions, "DATAPOOL", "pool-1")}

test_installation_read_allowed_with_tenant_grant if {
	result := authz.decision with http.send as mock_send_installation_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/installations/installation-1")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {"INSTALLATION_READ"}
}

test_install_allowed_with_all_required_permissions if {
	result := authz.decision with http.send as mock_send_installer
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/installations")
	result.allow == true
	result.reason == "permission_granted"
	result.required_permissions == {permission | some permission in installer_permissions}
}

# The permission to install does not include the permission to create Data structures.
test_install_denied_without_datastructure_create if {
	result := authz.decision with http.send as mock_send_installer_without_datastructure_create
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/installations")
	result.allow == false
	result.reason == "permission_denied"
}

# The permission to create Data structures does not include the permission to create their versions.
test_install_denied_without_datastructure_update if {
	result := authz.decision with http.send as mock_send_installer_without_datastructure_update
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/installations")
	result.allow == false
	result.reason == "permission_denied"
}

# The permissions for the artifacts of a package do not include the permission to install.
test_install_denied_without_installation_create if {
	result := authz.decision with http.send as mock_send_installer_without_installation_create
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/installations")
	result.allow == false
	result.reason == "permission_denied"
}

# The permission to install does not include the permission to create Data sources.
test_install_denied_without_datasource_create if {
	result := authz.decision with http.send as mock_send_installer_without_datasource_create
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/installations")
	result.allow == false
	result.reason == "permission_denied"
}

# The permission to install does not include the permission to create Datasets.
test_install_denied_without_dataset_create if {
	result := authz.decision with http.send as mock_send_installer_without_dataset_create
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/installations")
	result.allow == false
	result.reason == "permission_denied"
}

# The permission to create a Dataset does not include the permission to change it.
# The install adds Mappings, Data sinks, Pipelines, Styles and Layers to the Dataset.
test_install_denied_without_dataset_update if {
	result := authz.decision with http.send as mock_send_installer_without_dataset_update
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/installations")
	result.allow == false
	result.reason == "permission_denied"
}

# A grant on one data pool is not tenant-wide, so it does not reach the installations.
test_install_denied_with_pool_grant if {
	result := authz.decision with http.send as mock_send_pool_installer
		with data.config as mock_http.mock_config
		with input as portal_request("POST", "/v1/installations")
	result.allow == false
	result.reason == "permission_denied"
}

test_install_denied_unauthenticated if {
	result := authz.decision with input as portal_request_no_auth("POST", "/v1/installations")
	result.allow == false
}
