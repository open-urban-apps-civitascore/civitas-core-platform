# Security-hardening tests for main.rego.
#
# These pin authorization invariants: null-permission endpoints emit the scope
# wildcard, a client cannot spoof the scope header, denies never leak a scope
# header, deny status codes are correct, a permission split across two datapools
# does not grant, and a malformed X-Userinfo fails secure.

package civitas.authz.security_test

import rego.v1

import data.civitas.authz
import data.test.helpers.mock_http

# =============================================================================
# HELPERS
# =============================================================================

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

# Request that additionally carries client-supplied scope headers (spoofing attempt).
portal_request_spoofed(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {
			"x-userinfo": mock_http.encode_userinfo("test-user"),
			"x-allowed-scope-ids": "*",
			"x-allowed-pool-ids": "evil-pool",
		},
	},
	"service": {"name": "portal-backend"},
}

mock_send_authenticated_no_groups(_) := {"status_code": 200, "body": {"userId": "123", "externalId": "ext-123", "groups": []}}
mock_send_ds123_reader(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATASOURCE", "ds-123")}
mock_send_wrong_scope(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASOURCE_READ"], "DATASOURCE", "other-ds")}
mock_send_empty(_) := {"status_code": 200, "body": {}}

# =============================================================================
# 1. NULL-PERMISSION ENDPOINTS EMIT NO SCOPE HEADER (FAIL-CLOSED)
# =============================================================================
# A null-permission endpoint imposes no scope filtering and emits no scope header.
# This is fail-closed: the backend rejects a scope-guarded request that arrives without
# the header, so a misconfigured null-permission data endpoint is denied rather than
# served unfiltered.
test_null_permission_endpoint_emits_no_scope_header if {
	result := authz.decision with http.send as mock_send_authenticated_no_groups
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/users/me")
	result.allow == true
	result.reason == "authenticated_endpoint"
	not result.headers
}

# =============================================================================
# 2. CLIENT CANNOT SPOOF THE SCOPE HEADER
# =============================================================================
# OPA recomputes X-Allowed-Scope-Ids / X-Allowed-Pool-Ids from the fetched user
# context and never reads them from the inbound request. A client sending
# "X-Allowed-Scope-Ids: *" must NOT widen its computed scope. This is the
# defense-in-depth invariant behind the gateway header-strip.

test_client_supplied_scope_header_is_ignored if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request_spoofed("GET", "/v1/datasources")
	result.allow == true

	# The spoofed "*" must be overridden by the OPA-computed narrow scope.
	result.headers["X-Allowed-Scope-Ids"] == "ds-123"

	# The spoofed pool header must not appear (user has no pool grant).
	not result.headers["X-Allowed-Pool-Ids"]
}

# A client-supplied scope header must not turn a deny into an allow.
test_client_supplied_scope_header_does_not_grant_on_deny if {
	result := authz.decision with http.send as mock_send_wrong_scope
		with data.config as mock_http.mock_config
		with input as portal_request_spoofed("GET", "/v1/datasources/ds-123")
	result.allow == false
	result.reason == "permission_denied"
}

# =============================================================================
# 3. DENIES NEVER LEAK A SCOPE HEADER
# =============================================================================
# A deny decision must carry no X-Allowed-Scope-Ids / X-Allowed-Pool-Ids — a stale
# header the backend acted on would be a scope-confusion bug.

test_permission_denied_carries_no_scope_header if {
	result := authz.decision with http.send as mock_send_wrong_scope
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources/ds-123")
	result.allow == false
	not result.headers
}

test_missing_user_context_carries_no_scope_header if {
	result := authz.decision with http.send as mock_send_empty
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result.allow == false
	result.reason == "missing_user_context"
	not result.headers
}

test_unknown_endpoint_carries_no_scope_header if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/nope-not-a-route")
	result.allow == false
	not result.headers
}

test_authentication_required_carries_no_scope_header if {
	result := authz.decision with input as portal_request_no_auth("GET", "/v1/users/me")
	result.allow == false
	not result.headers
}

# =============================================================================
# 4. DENY STATUS CODES (401 vs 403)
# =============================================================================
# Anonymous (no X-Userinfo) → 401; authenticated-but-forbidden → 403.

test_unauthenticated_deny_is_401 if {
	result := authz.decision with input as portal_request_no_auth("GET", "/v1/users/me")
	result.allow == false
	result.reason == "authentication_required"
	result.status_code == 401
}

test_forbidden_deny_is_403 if {
	result := authz.decision with http.send as mock_send_wrong_scope
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasources/ds-123")
	result.allow == false
	result.reason == "permission_denied"
	result.status_code == 403
}

# =============================================================================
# 5. AND-PERMISSION SPLIT ACROSS TWO DATAPOOLS DOES NOT GRANT
# =============================================================================
# A single datapool must carry ALL required permissions. An endpoint requiring
# [DATASET_UPDATE, DATASET_RELEASE] must NOT be granted when the two permissions live
# in two different pools and the dataset belongs to only one of them.

# pool-a carries UPDATE, pool-b carries RELEASE; the dataset resolves to pool-a.
mock_pool_split(req) := {"status_code": 200, "body": mock_http.user_with_grouped_permissions([
	{"perms": ["DATASET_UPDATE"], "scope_type": "DATAPOOL", "scope_id": "pool-a"},
	{"perms": ["DATASET_RELEASE"], "scope_type": "DATAPOOL", "scope_id": "pool-b"},
])} if {
	contains(req.url, "user-context")
}

mock_pool_split(req) := {"status_code": 200, "body": {"poolId": "pool-a"}} if {
	contains(req.url, "dataset-pool")
}

test_and_permission_split_across_pools_denied if {
	result := authz.decision with http.send as mock_pool_split
		with data.config as mock_http.mock_config
		with input as portal_request("PUT", "/v1/datasets/ds-99/released/meta")
	result.allow == false
	result.reason == "permission_denied"
}

# =============================================================================
# 6. MALFORMED X-USERINFO FAILS SECURE
# =============================================================================
# A present-but-undecodable X-Userinfo must not be treated as authenticated. The
# AuthZ Repository is never queried (no external id) → missing_user_context deny.

malformed_request(userinfo_value) := {
	"request": {
		"method": "GET",
		"path": "/v1/datasets",
		"headers": {"x-userinfo": userinfo_value},
	},
	"service": {"name": "portal-backend"},
}

# Not valid base64url at all.
test_invalid_base64_userinfo_denied if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as malformed_request("!!! not base64 !!!")
	result.allow == false
	result.reason == "missing_user_context"
}

# Valid base64url, but the decoded bytes are not JSON.
test_non_json_userinfo_denied if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as malformed_request(base64url.encode_no_pad("{not-json"))
	result.allow == false
	result.reason == "missing_user_context"
}

# =============================================================================
# 7. DECISION-LEVEL DEFAULT DENY
# =============================================================================
# Empty or structurally incomplete input must deny and emit no scope header.

test_decision_default_deny_empty_input if {
	result := authz.decision with input as {}
	result.allow == false
	not result.headers
}

test_decision_deny_missing_method if {
	result := authz.decision with input as {
		"request": {"path": "/v1/datasets", "headers": {}},
		"service": {"name": "portal-backend"},
	}
	result.allow == false
	not result.headers
}

test_decision_deny_missing_path if {
	result := authz.decision with input as {
		"request": {"method": "GET", "headers": {}},
		"service": {"name": "portal-backend"},
	}
	result.allow == false
	not result.headers
}

# =============================================================================
# 8. REMAINING DENY STATUS CODES (authenticated caller → 403)
# =============================================================================

test_unknown_endpoint_deny_is_403_when_authenticated if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/nope-not-a-route")
	result.reason == "unknown_endpoint"
	result.status_code == 403
}

test_unknown_backend_deny_is_403_when_authenticated if {
	result := authz.decision with http.send as mock_send_ds123_reader
		with data.config as mock_http.mock_config
		with input as {"request": {
			"method": "GET",
			"path": "/v1/datasources",
			"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")},
		}}
	result.reason == "unknown_backend"
	result.status_code == 403
}

# =============================================================================
# 9. PERMISSION GRANTED WITH NO EXPRESSIBLE SCOPE (rule 5) OMITS THE HEADER
# =============================================================================
# A DATASET-scoped assignment with a null scopeId satisfies the collection
# permission but yields no wildcard, no specific id and no pool id, so the
# decision omits the scope header entirely. Confirms the branch is reachable.

mock_send_dataset_null_scope_id(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATASET", null)}

test_permission_granted_without_expressible_scope_omits_header if {
	result := authz.decision with http.send as mock_send_dataset_null_scope_id
		with data.config as mock_http.mock_config
		with input as portal_request("GET", "/v1/datasets")
	result.allow == true
	result.reason == "permission_granted"
	not result.headers
}

# =============================================================================
# 10. POOL-ID HEADER CANNOT BE SPOOFED
# =============================================================================
# A client-supplied X-Allowed-Pool-Ids must not override the OPA-computed pool set.

mock_send_pool_reader(_) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_READ"], "DATAPOOL", "pool-1")}

test_client_supplied_pool_header_is_ignored if {
	result := authz.decision with http.send as mock_send_pool_reader
		with data.config as mock_http.mock_config
		with input as portal_request_spoofed("GET", "/v1/datasets")
	result.allow == true
	result.headers["X-Allowed-Pool-Ids"] == "pool-1"
}
