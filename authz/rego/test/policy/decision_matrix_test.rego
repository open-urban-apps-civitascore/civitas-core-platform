# Parametrized decision matrix for the portal-backend scope model.
#
# Table-driven coverage of the allow/deny decision AND the emitted scope header across
# all data-entity resource types. Complements the behaviour-specific tests in
# main_test.rego / main_security_test.rego (which stay as named tests for readable
# failures); this matrix asserts the full tuple (allow + reason + X-Allowed-Scope-Ids)
# uniformly so a regression in header computation for any resource type is caught.
#
# The per-case user context is injected via `with data.fixture.ctx` and returned by a
# single mock (Rego cannot select a mock function from a table row).

package civitas.authz.decision_matrix_test

import rego.v1

import data.civitas.authz
import data.test.helpers.mock_http

mock_ctx(req) := {"status_code": 200, "body": {"poolIds": ["pool-1"], "usableInAllPools": false}} if {
	contains(req.url, "datasource-pools")
}

mock_ctx(req) := {"status_code": 200, "body": data.fixture.ctx} if {
	not contains(req.url, "datasource-pools")
}

request(method, path) := {
	"request": {"method": method, "path": path, "headers": {"x-userinfo": mock_http.encode_userinfo("u")}},
	"service": {"name": "portal-backend"},
}

# --- ALLOW cases: {name, ctx, method, path, scope} — scope = expected X-Allowed-Scope-Ids ---
allow_cases := [
	# Resource endpoint reached by the matching specific scope → header carries that id.
	{
		"name": "dataset resource, matching DATASET scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", "dataset-1"),
		"method": "GET", "path": "/v1/datasets/dataset-1", "scope": "dataset-1",
	},
	{
		"name": "datasource resource, matching DATASOURCE scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATASOURCE", "ds-1"),
		"method": "GET", "path": "/v1/datasources/ds-1", "scope": "ds-1",
	},
	{
		"name": "datastructure resource, matching DATASTRUCTURE scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASTRUCTURE_READ"], "DATASTRUCTURE", "dst-1"),
		"method": "GET", "path": "/v1/datastructures/dst-1", "scope": "dst-1",
	},
	{
		"name": "datapool resource, matching DATAPOOL scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATAPOOL_READ"], "DATAPOOL", "pool-1"),
		"method": "GET", "path": "/v1/datapools/pool-1", "scope": "pool-1",
	},
	# Collection endpoint with a TENANT grant → wildcard.
	{
		"name": "dataset collection, TENANT scope",
		"ctx": mock_http.user_with_permissions(["DATASET_READ"]),
		"method": "GET", "path": "/v1/datasets", "scope": "*",
	},
	{
		"name": "user collection, TENANT scope",
		"ctx": mock_http.user_with_permissions(["USER_READ"]),
		"method": "GET", "path": "/v1/users", "scope": "*",
	},
	# Collection endpoint with a specific scope → that id in the header.
	{
		"name": "datasource collection, specific DATASOURCE scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATASOURCE", "ds-1"),
		"method": "GET", "path": "/v1/datasources", "scope": "ds-1",
	},
	# Resource endpoint on a TENANT resource with a TENANT grant → wildcard.
	{
		"name": "user resource, TENANT scope",
		"ctx": mock_http.user_with_permissions(["USER_READ"]),
		"method": "GET", "path": "/v1/users/u-1", "scope": "*",
	},
	# Unscoped (scopeType=null) grant is tenant-wide → wildcard.
	{
		"name": "dataset collection, unscoped grant",
		"ctx": mock_http.user_with_unscoped_permissions(["DATASET_READ"]),
		"method": "GET", "path": "/v1/datasets", "scope": "*",
	},
	# Datapool inheritance: a DATAPOOL grant conveys READ on the pool's data sources.
	# No direct scope id exists, so the header is empty and the pool set carries the filter.
	{
		"name": "datasource resource, inherited from DATAPOOL scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATAPOOL", "pool-1"),
		"method": "GET", "path": "/v1/datasources/ds-1", "scope": "", "pool": "pool-1",
	},
	{
		"name": "datasource collection, inherited from DATAPOOL scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATAPOOL", "pool-1"),
		"method": "GET", "path": "/v1/datasources", "scope": "", "pool": "pool-1",
	},
]

test_matrix_allow if {
	every case in allow_cases {
		result := authz.decision with http.send as mock_ctx
			with data.fixture.ctx as case.ctx
			with data.config as mock_http.mock_config
			with input as request(case.method, case.path)
		result.allow == true
		result.reason == "permission_granted"
		result.headers["X-Allowed-Scope-Ids"] == case.scope
		expected_pool_header(case, result)
	}
}

# A case declaring "pool" must emit exactly that X-Allowed-Pool-Ids; a case without it
# must emit none, so an unexpected pool grant cannot slip past the matrix.
expected_pool_header(case, result) if {
	result.headers["X-Allowed-Pool-Ids"] == case.pool
}

expected_pool_header(case, result) if {
	not case.pool
	not result.headers["X-Allowed-Pool-Ids"]
}

# --- DENY cases: {name, ctx, method, path} → permission_denied, no scope header ---
deny_cases := [
	{
		"name": "dataset resource, wrong DATASET scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", "dataset-2"),
		"method": "GET", "path": "/v1/datasets/dataset-1",
	},
	{
		"name": "datasource resource, wrong DATASOURCE scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATASOURCE", "other"),
		"method": "GET", "path": "/v1/datasources/ds-1",
	},
	{
		"name": "datastructure resource, wrong DATASTRUCTURE scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASTRUCTURE_READ"], "DATASTRUCTURE", "other"),
		"method": "GET", "path": "/v1/datastructures/dst-1",
	},
	{
		"name": "datapool resource, wrong DATAPOOL scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATAPOOL_READ"], "DATAPOOL", "other"),
		"method": "GET", "path": "/v1/datapools/pool-1",
	},
	{
		"name": "dataset collection, unrelated permission",
		"ctx": mock_http.user_with_permissions(["USER_READ"]),
		"method": "GET", "path": "/v1/datasets",
	},
	{
		# Q-006 fail-secure: DATASET scope does not satisfy a DATASOURCE collection.
		"name": "datasource collection, scope-type mismatch",
		"ctx": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATASET", "dataset-1"),
		"method": "GET", "path": "/v1/datasources",
	},
	{
		# Datapool inheritance is read-only: a write is not conveyed even when the pool
		# grant carries the write permission.
		"name": "datasource update, not inherited from DATAPOOL scope",
		"ctx": mock_http.user_with_scoped_permissions(["DATASOURCE_READ", "DATASOURCE_UPDATE"], "DATAPOOL", "pool-1"),
		"method": "PUT", "path": "/v1/datasources/ds-1",
	},
	{
		# A pool grant without DATASOURCE_READ conveys nothing on data sources.
		"name": "datasource resource, DATAPOOL grant lacking DATASOURCE_READ",
		"ctx": mock_http.user_with_scoped_permissions(["DATAPOOL_READ"], "DATAPOOL", "pool-1"),
		"method": "GET", "path": "/v1/datasources/ds-1",
	},
]

test_matrix_deny if {
	every case in deny_cases {
		result := authz.decision with http.send as mock_ctx
			with data.fixture.ctx as case.ctx
			with data.config as mock_http.mock_config
			with input as request(case.method, case.path)
		result.allow == false
		result.reason == "permission_denied"
		not result.headers
	}
}
