# Tests for open_data.rego + main.rego open-data ABAC decisions.
#
# The fetcher (dataset_pool_fetcher) and user_context_fetcher both call http.send,
# so the mocks branch on req.url: the dataset-attributes URL returns the
# openDataAccess flag, the user-context URL returns a user context.

package civitas.authz.open_data_test

import rego.v1

import data.civitas.authz
import data.civitas.authz.open_data
import data.test.helpers.mock_http

# =============================================================================
# REQUEST BUILDERS
# =============================================================================

# Anonymous FROST published-data request (no x-userinfo, gateway Host).
frost_anon_request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {"host": "api.localhost"},
	},
	"service": {"name": "frost-server"},
}

# Anonymous portal-backend request (no x-userinfo).
portal_anon_request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {},
	},
	"service": {"name": "portal-backend"},
}

# Authenticated portal-backend request (x-userinfo present).
portal_auth_request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {"x-userinfo": mock_http.encode_userinfo("test-user")},
	},
	"service": {"name": "portal-backend"},
}

# Authenticated FROST published-data request.
frost_auth_request(method, path) := {
	"request": {
		"method": method,
		"path": path,
		"headers": {
			"x-userinfo": mock_http.encode_userinfo("test-user"),
			"host": "api.localhost",
		},
	},
	"service": {"name": "frost-server"},
}

# =============================================================================
# HTTP MOCKS (branch on URL)
# =============================================================================

is_attrs_url(req) if contains(req.url, "dataset-pool")

# Dataset is flagged open; user-context (if asked) carries no permissions.
mock_open(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true}} if {
	is_attrs_url(req)
}

mock_open(req) := {"status_code": 200, "body": mock_http.user_no_permissions} if {
	not is_attrs_url(req)
}

# Dataset is NOT flagged open.
mock_not_open(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": false}} if {
	is_attrs_url(req)
}

mock_not_open(req) := {"status_code": 200, "body": mock_http.user_no_permissions} if {
	not is_attrs_url(req)
}

# Attributes lookup fails (fetcher outage) — open-data branch must fail-secure.
mock_attrs_outage(req) := {"status_code": 503, "body": {}} if {
	is_attrs_url(req)
}

mock_attrs_outage(req) := {"status_code": 200, "body": mock_http.user_no_permissions} if {
	not is_attrs_url(req)
}

# Open dataset, but the user-context carries the matching payload permission.
mock_open_with_perm(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true}} if {
	is_attrs_url(req)
}

mock_open_with_perm(req) := {"status_code": 200, "body": mock_http.user_with_scoped_permissions(["DATASET_PAYLOAD_READ"], "DATASET", "dataset-abc")} if {
	not is_attrs_url(req)
}

# =============================================================================
# is_open_data_grant — unit level
# =============================================================================

test_grant_true_for_anonymous_open_frost_payload if {
	open_data.is_open_data_grant with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc")
}

test_grant_false_when_dataset_not_open if {
	not open_data.is_open_data_grant with http.send as mock_not_open
		with data.config as mock_http.mock_config
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc")
}

test_grant_false_for_non_get_method if {
	not open_data.is_open_data_grant with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as portal_anon_request("PUT", "/v1/datasets/dataset-abc")
}

test_grant_false_for_non_eligible_endpoint if {
	not open_data.is_open_data_grant with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as portal_anon_request("GET", "/v1/datasets/dataset-abc/assignments")
}

test_grant_false_on_fetcher_outage if {
	not open_data.is_open_data_grant with http.send as mock_attrs_outage
		with data.config as mock_http.mock_config
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc")
}

# =============================================================================
# decision — anonymous open-data reads ALLOWED
# =============================================================================

test_anonymous_open_frost_payload_allowed if {
	result := authz.decision with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc")
	result.allow == true
	result.reason == "open_data"
}

# OWS/GeoServer is open-data eligible by the SAME rule as STA: every dataset route carries the same
# service_id (RouteAuthConfigurer sets it for all standards), so OPA dispatches OWS to the same
# path-based frost_server policy and the standard is invisible here. This pins that standard-agnostic
# contract — a regression that made the grant STA-specific would turn this red. (The OWS-distinct
# routing, i.e. the GeoServer upstream with no FROST credential, is covered by ApisixSagaHandler
# tests; an end-to-end OWS request is not exercised here as the api-test stack ships no GeoServer.)
test_anonymous_open_ows_payload_allowed if {
	result := authz.decision with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc/map")
	result.allow == true
	result.reason == "open_data"
}

# Open data is PAYLOAD-only. Anonymous reads of the portal-backend METADATA detail and the
# DISCOVERY (/apis) endpoint are NOT opened, even for an open dataset: those DTOs carry internal
# fields (pipelines, createdBy, pendingSagaType) that must not be exposed anonymously. They stay
# authenticated at BOTH layers — portal-backend's own security AND OPA (these endpoints are not
# marked `_open_data`, so the grant does not fire) → anonymous is denied with 401.
test_anonymous_metadata_denied if {
	result := authz.decision with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as portal_anon_request("GET", "/v1/datasets/dataset-abc")
	result.allow == false
	result.reason == "missing_user_context"
	result.status_code == 401
}

test_anonymous_discovery_denied if {
	result := authz.decision with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as portal_anon_request("GET", "/v1/datasets/dataset-abc/apis")
	result.allow == false
	result.reason == "missing_user_context"
	result.status_code == 401
}

# =============================================================================
# decision — anonymous reads DENIED (fail-secure / not eligible / not open)
# =============================================================================

# A non-open dataset denies anonymous payload reads with 401 (no credentials).
test_anonymous_non_open_frost_denied_401 if {
	result := authz.decision with http.send as mock_not_open
		with data.config as mock_http.mock_config
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc")
	result.allow == false
	result.reason == "missing_user_context"
	result.status_code == 401
}

# Management sub-resources are never open, even on an open dataset → 401 anonymous.
test_anonymous_assignments_denied_even_when_open if {
	result := authz.decision with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as portal_anon_request("GET", "/v1/datasets/dataset-abc/assignments")
	result.allow == false
	result.status_code == 401
}

# Writes are never open: anonymous PUT on an open dataset is denied with 401 (no credentials).
test_anonymous_write_on_open_dataset_denied if {
	result := authz.decision with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as portal_anon_request("PUT", "/v1/datasets/dataset-abc")
	result.allow == false
	result.status_code == 401
}

# Anonymous request to an unknown endpoint is denied with 401 (no credentials), not 403.
test_anonymous_unknown_endpoint_denied_401 if {
	result := authz.decision with http.send as mock_open
		with data.config as mock_http.mock_config
		with input as portal_anon_request("GET", "/v1/totally-unknown-resource")
	result.allow == false
	result.reason == "unknown_endpoint"
	result.status_code == 401
}

# Fetcher outage must not open a dataset (fail-secure deny).
test_anonymous_open_frost_failsecure_on_outage if {
	result := authz.decision with http.send as mock_attrs_outage
		with data.config as mock_http.mock_config
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc")
	result.allow == false
	result.reason == "missing_user_context"
}

# =============================================================================
# decision — authenticated users keep their regular (RBAC) path
# =============================================================================

# An authenticated user WITH the permission is granted via permission_granted,
# NOT open_data — so the scope-header path is preserved.
test_authenticated_with_permission_uses_permission_path if {
	result := authz.decision with http.send as mock_open_with_perm
		with data.config as mock_http.mock_config
		with input as frost_auth_request("GET", "/v1/datasets/dataset-abc")
	result.allow == true
	result.reason == "permission_granted"
}

# An authenticated user WITHOUT permission on a NON-open dataset is denied 403.
test_authenticated_without_permission_denied_403 if {
	result := authz.decision with http.send as mock_not_open
		with data.config as mock_http.mock_config
		with input as frost_auth_request("GET", "/v1/datasets/dataset-abc")
	result.allow == false
	result.reason == "permission_denied"
	result.status_code == 403
}

# AuthZ Repository unavailable for an AUTHENTICATED caller (X-Userinfo present, user-context fetch
# fails) → missing_user_context with 403 (credential present, so "forbidden", not "unauthorized").
# This is the explicitly documented purpose of the 401/403 deny_status split.
mock_user_context_down(req) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": false}} if {
	is_attrs_url(req)
}

mock_user_context_down(req) := {"status_code": 503, "body": {}} if {
	not is_attrs_url(req)
}

test_authenticated_repo_down_denied_403 if {
	result := authz.decision with http.send as mock_user_context_down
		with data.config as mock_http.mock_config
		with input as frost_auth_request("GET", "/v1/datasets/dataset-abc")
	result.allow == false
	result.reason == "missing_user_context"
	result.status_code == 403
}

# =============================================================================
# ALLOW-ALL / DEV MODE INTERACTION (null-permission endpoints)
# =============================================================================
# In allow-all mode the endpoint's permission is nulled (→ null-permission endpoint).
# An anonymous reader of an OPEN dataset must still be allowed: rule 0 (open_data)
# must win over the null-permission authentication_required rule.

test_anonymous_open_on_null_permission_endpoint_allowed if {
	result := authz.decision with http.send as mock_open
		with data.config as mock_http.mock_config
		with data.backends.frost_server.endpoints as {"/v1/datasets/{id}": {"GET": null, "_open_data": true}}
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc")
	result.allow == true
	result.reason == "open_data"
}

# Same null-permission endpoint, dataset NOT open → anonymous denied (401), not a conflict.
test_anonymous_non_open_on_null_permission_endpoint_denied if {
	result := authz.decision with http.send as mock_not_open
		with data.config as mock_http.mock_config
		with data.backends.frost_server.endpoints as {"/v1/datasets/{id}": {"GET": null, "_open_data": true}}
		with input as frost_anon_request("GET", "/v1/datasets/dataset-abc")
	result.allow == false
	result.reason == "authentication_required"
	result.status_code == 401
}
