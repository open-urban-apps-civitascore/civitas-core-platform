# CIVITAS CORE AuthZ Policy - Open Data Access (ABAC)
#
# Attribute-Based Access Control for "open data" datasets: when a dataset is
# flagged openDataAccess=true, ANYONE — including anonymous (unauthenticated)
# callers — may READ it. This replaces the previous gateway-level bypass (APISIX
# stripping the auth plugin + making the FROST project public); the decision now
# lives here in OPA, the single PDP, and applies uniformly to the routable named-API
# kinds (STA/FROST and OWS/GeoServer) because they all arrive as /v1/datasets/{id}/...
# on the payload backend. (CUSTOM is intentionally not routable — the APISIX saga handler
# rejects it — so it never reaches this rule.)
#
# This module is deliberately separate from permission_eval.rego: that module
# answers "does this user hold the required permission?" (RBAC), while this one
# answers "is this resource public for reading?" (ABAC). Keeping them apart is
# SRP and keeps each rule set easy to reason about for review.
#
# Security — this grant is intentionally NARROW:
#   - GET only (reads; writes are never open).
#   - DATASET-scoped resource endpoints only.
#   - Only endpoints explicitly marked `_open_data: true` in the backend data
#     files. Today that is the PAYLOAD only (FROST `/v1/datasets/{id}`), which the
#     routable STA and OWS data routes map to (CUSTOM is out of scope — not routable).
#     Portal-backend metadata/discovery is NOT
#     marked: those DTOs carry internal fields (pipelines, createdBy, pendingSagaType)
#     and stay authenticated at both layers (portal-backend security AND OPA). A
#     curated public catalog, if ever wanted, is a separate endpoint with a trimmed DTO.
#   - Only when the normal permission check did NOT already grant, so authenticated
#     users keep their regular decision path (incl. scope headers) and this rule is
#     mutually exclusive with the permission_granted rules in main.rego.
#   - Fail-secure: is_open_data() is undefined if the AuthZ Repository lookup fails,
#     so an outage never opens a dataset.

package civitas.authz.open_data

import rego.v1

import data.civitas.authz.dataset_pool_fetcher
import data.civitas.authz.permission_eval
import data.civitas.authz.resource_mapping

# True when the request is an anonymous-eligible open-data read that should be
# allowed regardless of authentication.
default is_open_data_grant := false

is_open_data_grant if {
	resource_mapping.request_method == "GET"
	resource_mapping.is_resource_endpoint
	resource_mapping.expected_scope_type == "DATASET"
	is_open_data_eligible_endpoint

	# Evaluate the cheap RBAC check before the AuthZ-Repository fetch: a user who
	# already holds the permission takes the normal permission_granted path (rule
	# 4/5 in main.rego), so the open-data attribute fetch is skipped for them.
	# (OPA does not guarantee conjunct order, but source order is the common case
	# and this keeps the fetch off the authorized hot path.)
	not permission_eval.has_permission
	dataset_pool_fetcher.is_open_data(resource_mapping.resource_id)
}

# The matched endpoint is marked open-data-readable in its backend data file.
# Data-driven allowlist: only `_open_data: true` endpoints qualify — today the dataset
# PAYLOAD only. Everything else (metadata, discovery, management sub-resources) stays
# authenticated.
is_open_data_eligible_endpoint if {
	permission_eval.endpoint_config._open_data == true
}
