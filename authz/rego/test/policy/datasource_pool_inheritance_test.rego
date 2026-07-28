# Tests for DATAPOOL → DATASOURCE inheritance decisions.
#
# A DATAPOOL grant carrying DATASOURCE_READ authorizes the data source read routes and
# emits X-Allowed-Pool-Ids; writes stay behind a DATASOURCE-scoped or tenant-wide grant.
# Single reads are decided per data source, against the assignment answer from the AuthZ
# Repository; the collection is narrowed by the pool header the backend filters with.

package civitas.authz.datasource_pool_inheritance_test

import rego.v1

import data.civitas.authz
import data.test.helpers.mock_http

request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {"x-userinfo": mock_http.encode_userinfo("steward")},
	},
	"service": {"name": "portal-backend"},
}

steward_context := mock_http.user_with_scoped_permissions(
	[
		"DATASET_READ", "DATASET_UPDATE",
		"DATASOURCE_READ", "DATASOURCE_CREATE", "DATASOURCE_UPDATE", "DATASOURCE_DELETE",
		"DATAPOOL_READ",
	],
	"DATAPOOL", "pool-1",
)

# A Data Steward scoped only to pool-1; the requested data source is confined to pool-1.
mock_pool_steward(req) := {"status_code": 200, "body": {"poolIds": ["pool-1"]}} if {
	contains(req.url, "datasource-pools")
}

mock_pool_steward(req) := {"status_code": 200, "body": steward_context} if {
	contains(req.url, "user-context")
}

# A pool grant that does not carry DATASOURCE_READ at all.
mock_pool_without_read(req) := {"status_code": 200, "body": {"poolIds": []}} if {
	contains(req.url, "datasource-pools")
}

mock_pool_without_read(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ", "DATAPOOL_READ"], "DATAPOOL", "pool-1")} if {
	contains(req.url, "user-context")
}

# =============================================================================
# READS ARE INHERITED, AND CARRY THE POOL SET THE BACKEND FILTERS BY
# =============================================================================

test_reads_datasource_resource if {
	result := authz.decision with http.send as mock_pool_steward
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == true
	result.reason == "permission_granted"
}

test_lists_datasources if {
	result := authz.decision with http.send as mock_pool_steward
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources")
	result.allow == true
}

# Both read routes carry the pool set: the collection filter needs it, and a resource read
# reached purely through inheritance must not arrive without it either.
test_resource_read_emits_pool_header if {
	result := authz.decision with http.send as mock_pool_steward
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.headers["X-Allowed-Pool-Ids"] == "pool-1"
	result.headers["X-Allowed-Scope-Ids"] == ""
}

test_collection_read_emits_pool_header if {
	result := authz.decision with http.send as mock_pool_steward
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources")
	result.headers["X-Allowed-Pool-Ids"] == "pool-1"
	result.headers["X-Allowed-Scope-Ids"] == ""
}

test_reads_datasource_assignments if {
	result := authz.decision with http.send as mock_pool_steward
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1/assignments")
	result.allow == true
	result.headers["X-Allowed-Pool-Ids"] == "pool-1"
}

# =============================================================================
# THE SINGLE READ IS DECIDED PER DATA SOURCE
# =============================================================================
# Same steward, same pool grant — only the data source's assignment differs.

mock_other_pool(req) := {"status_code": 200, "body": {"poolIds": ["pool-2"]}} if {
	contains(req.url, "datasource-pools")
}

mock_other_pool(req) := {"status_code": 200, "body": steward_context} if {
	contains(req.url, "user-context")
}

mock_unrestricted(req) := {"status_code": 200, "body": {"poolIds": []}} if {
	contains(req.url, "datasource-pools")
}

mock_unrestricted(req) := {"status_code": 200, "body": steward_context} if {
	contains(req.url, "user-context")
}

mock_unusable(req) := {"status_code": 200, "body": {"poolIds": []}} if {
	contains(req.url, "datasource-pools")
}

mock_unusable(req) := {"status_code": 200, "body": steward_context} if {
	contains(req.url, "user-context")
}

mock_lookup_down(req) := {"status_code": 503, "body": {}} if {
	contains(req.url, "datasource-pools")
}

mock_lookup_down(req) := {"status_code": 200, "body": steward_context} if {
	contains(req.url, "user-context")
}

# An unrestricted data source is assigned to no pool, so a pool grant conveys nothing on
# it. Without this, every data source nobody has scoped yet would be readable through any
# datapool grant — and unrestricted is the entity default.
test_denies_unrestricted_datasource if {
	result := authz.decision with http.send as mock_unrestricted
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == false
	result.reason == "permission_denied"
}

test_denies_datasource_assigned_to_other_pool if {
	result := authz.decision with http.send as mock_other_pool
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == false
	result.reason == "permission_denied"
}

test_denies_datasource_assigned_to_no_pool if {
	result := authz.decision with http.send as mock_unusable
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == false
}

# Fail-secure: an unavailable assignment lookup must not grant.
test_denies_when_assignment_lookup_is_down if {
	result := authz.decision with http.send as mock_lookup_down
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == false
}

# The collection needs no per-data-source lookup, so it survives the same outage — the
# backend narrows it by the pool header instead.
test_lists_datasources_during_lookup_outage if {
	result := authz.decision with http.send as mock_lookup_down
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources")
	result.allow == true
	result.headers["X-Allowed-Pool-Ids"] == "pool-1"
}

# =============================================================================
# A POOL GRANT WITHOUT DATASOURCE_READ CONVEYS NOTHING
# =============================================================================

test_pool_grant_without_read_denies_resource if {
	result := authz.decision with http.send as mock_pool_without_read
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == false
	result.reason == "permission_denied"
}

test_pool_grant_without_read_denies_collection if {
	result := authz.decision with http.send as mock_pool_without_read
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources")
	result.allow == false
}

# Two pool grants, only one carrying DATASOURCE_READ. The decision and the emitted pool
# set must both come from that pool alone — a non-qualifying pool must not widen the
# filter the backend applies.
mock_two_pools(req) := {"status_code": 200, "body": {"poolIds": ["pool-1"]}} if {
	contains(req.url, "datasource-pools")
}

mock_two_pools(req) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASOURCE_READ"], "scope_type": "DATAPOOL", "scope_id": "pool-1"},
	{"perms": ["DATAPOOL_READ"], "scope_type": "DATAPOOL", "scope_id": "pool-2"},
])} if {
	contains(req.url, "user-context")
}

test_only_the_qualifying_pool_reaches_the_header if {
	result := authz.decision with http.send as mock_two_pools
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == true
	result.headers["X-Allowed-Pool-Ids"] == "pool-1"
}

# A DATAPOOL assignment without a scope id cannot identify a pool. It must not enter the
# qualifying set, where it would end up in the header the backend filters by.
mock_null_scope_id(req) := {"status_code": 200, "body": {"poolIds": []}} if {
	contains(req.url, "datasource-pools")
}

mock_null_scope_id(req) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([{
	"perms": ["DATASOURCE_READ"],
	"scope_type": "DATAPOOL",
	"scope_id": null,
}])} if {
	contains(req.url, "user-context")
}

test_pool_grant_without_scope_id_denies if {
	result := authz.decision with http.send as mock_null_scope_id
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == false
	result.reason == "permission_denied"
}

test_only_the_qualifying_pool_reaches_the_collection_header if {
	result := authz.decision with http.send as mock_two_pools
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources")
	result.allow == true
	result.headers["X-Allowed-Pool-Ids"] == "pool-1"
}

# =============================================================================
# ROLE MATRIX: WHICH DATA ROLES INHERIT WHAT, HELD AT DATAPOOL SCOPE
# =============================================================================
# The permission sets below mirror RoleDefault (portal-model) — keep them in sync.
# Only the DATASOURCE-relevant permissions of each role are listed; the others do
# not influence a data source decision.
#
# Each case is a separate evaluation (one role per request), so the expectations are
# expressed as SETS of role names: a mismatch reports exactly which role drifted. The
# per-case user context is injected via `with data.fixture.ctx`, the idiom
# decision_matrix_test.rego uses (Rego cannot select a mock function from a table row).

role_permissions := {
	"DATA_ARCHITECT": [
		"DATASOURCE_CREATE", "DATASOURCE_READ", "DATASOURCE_UPDATE", "DATASOURCE_DELETE",
		"DATAPOOL_READ", "DATAPOOL_CREATE", "DATAPOOL_UPDATE", "DATAPOOL_DELETE",
	],
	"DATA_CONSUMER": ["DATASET_READ", "DATASET_PAYLOAD_READ", "DATAPOOL_READ"],
	"DATA_STEWARD": [
		"DATASOURCE_CREATE", "DATASOURCE_READ", "DATASOURCE_UPDATE", "DATASOURCE_DELETE",
		"DATAPOOL_READ", "DATAPOOL_UPDATE",
	],
	"DATA_OWNER": [
		"DATASOURCE_CREATE", "DATASOURCE_READ", "DATASOURCE_UPDATE", "DATASOURCE_DELETE",
		"DATASOURCE_RELEASE", "DATAPOOL_READ", "DATAPOOL_UPDATE",
	],
	"DATA_GATEKEEPER": ["DATASOURCE_READ", "DATASOURCE_RELEASE", "DATAPOOL_READ"],
}

# Roles that carry DATASOURCE_READ, i.e. the ones the inheritance is meant to serve.
datasource_readers := {"DATA_ARCHITECT", "DATA_STEWARD", "DATA_OWNER", "DATA_GATEKEEPER"}

mock_role(req) := {"status_code": 200, "body": {"poolIds": ["pool-1"]}} if {
	contains(req.url, "datasource-pools")
}

mock_role(req) := {"status_code": 200, "body": data.fixture.ctx} if {
	contains(req.url, "user-context")
}

decision_for(perms, method, path) := d if {
	d := authz.decision with http.send as mock_role
		with data.fixture.ctx as mock_http.user_with_scoped_permissions(perms, "DATAPOOL", "pool-1")
		with data.config as mock_http.mock_config
		with input as request(method, path)
}

roles_allowed(method, path) := {role |
	some role, perms in role_permissions
	decision_for(perms, method, path).allow
}

# Reads: exactly the roles carrying DATASOURCE_READ inherit — notably NOT the Data
# Consumer, whose pool grant must not become data source visibility.
test_role_matrix_resource_read if {
	roles_allowed("GET", "/v1/datasources/src-1") == datasource_readers
}

test_role_matrix_collection_read if {
	roles_allowed("GET", "/v1/datasources") == datasource_readers
}

test_role_matrix_subresource_read if {
	roles_allowed("GET", "/v1/datasources/src-1/assignments") == datasource_readers
}

# Writes: no role inherits a write from a pool grant, however privileged it is at
# datapool scope — an unrestricted data source is shared across pools.
test_role_matrix_no_role_inherits_update if {
	roles_allowed("PUT", "/v1/datasources/src-1") == set()
}

test_role_matrix_no_role_inherits_patch if {
	roles_allowed("PATCH", "/v1/datasources/src-1") == set()
}

test_role_matrix_no_role_inherits_delete if {
	roles_allowed("DELETE", "/v1/datasources/src-1") == set()
}

# The collection rule is method-agnostic, so create must be excluded by the
# inheritable-permission guard rather than by the path classification.
test_role_matrix_no_role_inherits_create if {
	roles_allowed("POST", "/v1/datasources") == set()
}

test_role_matrix_no_role_inherits_release if {
	roles_allowed("POST", "/v1/datasources/src-1/release") == set()
}

# The AND-permission endpoint: [DATASOURCE_UPDATE, DATASOURCE_RELEASE]. Neither is
# inheritable, so no pool grant satisfies it — the data-source counterpart of the
# dataset AND-permission pool-split invariant in main_security_test.rego.
test_role_matrix_no_role_inherits_released_meta if {
	roles_allowed("PUT", "/v1/datasources/src-1/released/meta") == set()
}

# A write denial must not smuggle in a pool header either — the pool set is the
# backend's filter, and an inherited pool must never widen a write.
test_write_denial_emits_no_headers if {
	result := authz.decision with http.send as mock_pool_steward
		with data.config as mock_http.mock_config
		with input as request("PUT", "/v1/datasources/src-1")
	not result.headers
}

# =============================================================================
# NO REGRESSION FOR NON-POOL CALLERS
# =============================================================================

mock_direct_grant(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASOURCE_READ", "DATASOURCE_UPDATE"], "DATASOURCE", "src-1")}

test_direct_datasource_grant_still_reads if {
	result := authz.decision with http.send as mock_direct_grant
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-1")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "src-1"
}

test_direct_datasource_grant_still_writes if {
	result := authz.decision with http.send as mock_direct_grant
		with data.config as mock_http.mock_config
		with input as request("PUT", "/v1/datasources/src-1")
	result.allow == true
}

# A DATASOURCE-scoped caller reading a DIFFERENT data source is still refused by OPA
# (403), not passed through to the backend to be filtered into a 404. Only a caller
# holding a DATAPOOL grant reaches the backend's filter, so the inheritance does not
# turn pre-existing scope denials into not-found answers.
test_direct_grant_on_other_datasource_is_refused_by_opa if {
	result := authz.decision with http.send as mock_direct_grant
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources/src-2")
	result.allow == false
	result.reason == "permission_denied"
	result.status_code == 403
}

mock_tenant_grant(req) := {"status_code": 200, "body": {"poolIds": []}} if {
	contains(req.url, "datasource-pools")
}

mock_tenant_grant(req) := {"status_code": 200, "body": mock_http.user_with_permissions(["DATASOURCE_READ", "DATASOURCE_UPDATE"])} if {
	contains(req.url, "user-context")
}

test_tenant_grant_keeps_wildcard if {
	result := authz.decision with http.send as mock_tenant_grant
		with data.config as mock_http.mock_config
		with input as request("GET", "/v1/datasources")
	result.allow == true
	result.headers["X-Allowed-Scope-Ids"] == "*"
}
