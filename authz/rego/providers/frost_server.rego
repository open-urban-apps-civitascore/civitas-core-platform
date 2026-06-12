# CIVITAS CORE AuthZ - FROST Server Provider
# Maps HTTP requests for the FROST Server (OGC SensorThings API proxy).
#
# FROST is accessed via APISIX gateway on the public API virtual host (issue #1368):
#
#   https://api.<domain>/v1/datasets/{dataset_id}       →  DATASET_PAYLOAD_READ (STA service root)
#   https://api.<domain>/v1/datasets/{dataset_id}/...   →  DATASET_PAYLOAD_READ (STA sub-resources)
#
# The {dataset_id} is the same dataset ID from portal_backend's PostgreSQL.
# APISIX proxy-rewrite maps /v1/datasets/{id}/* to the upstream FROST path.
# OPA checks that the user has DATASET_PAYLOAD_READ for that specific dataset — reading SensorThings
# *content* requires payload access, not the broader metadata-level DATASET_READ. (The /apis
# discovery endpoint is served by portal_backend and is gated separately.)
#
# Secondary sanity check: the provider enforces that the request arrived via the
# configured API gateway host (data.backends.frost_server.api_host). This catches
# unexpected paths (service-to-service calls, sidecar misconfigurations, lost
# `hosts` filters) and fails closed, preventing a stray `Host` header from
# silently slipping through.

package civitas.authz.providers.frost_server

import rego.v1

import data.civitas.authz.lib.restmapper

# =============================================================================
# ENDPOINT CONFIGURATION
# =============================================================================

# Endpoints map from data file
endpoints := data.backends.frost_server.endpoints if {
	data.backends.frost_server.endpoints
}

endpoints := {} if {
	not data.backends.frost_server.endpoints
}

# =============================================================================
# HOST MATCHING (sanity check for issue #1368)
# =============================================================================

# Shared normalisation for host comparison: lowercase, port stripped. APISIX does
# host matching on the name only (ignoring the port), so BOTH sides — the incoming
# Host header and the configured api_host — are normalised identically. Otherwise a
# port on either side (client sends `api.example.test:9080`, or an operator
# configures `api_host: api.example.test:9080`) would make the comparison fail and
# deny ALL FROST payload requests (fail-closed total outage).
#
# Handles two cases:
#   - IPv6 bracketed literals: `[::1]:9080` → `[::1]`
#     (cannot split on `:` because colons appear inside the address)
#   - Everything else: split on `:` and take the first component.
host_without_port(raw) := lower(substring(raw, 0, indexof(raw, "]") + 1)) if {
	startswith(raw, "[")
	indexof(raw, "]") > 0
}

host_without_port(raw) := lower(split(raw, ":")[0]) if {
	not startswith(raw, "[")
}

# Configured API host (normalised). Empty if unconfigured → fail-closed.
default api_host := ""

api_host := host_without_port(data.backends.frost_server.api_host) if {
	data.backends.frost_server.api_host
}

# Incoming request's Host header (normalised, empty if missing).
default request_host := ""

request_host := host_without_port(input.request.headers.host) if {
	input.request.headers.host
}

# True when the request targets the configured API host.
is_api_host if {
	api_host != ""
	request_host == api_host
}

# =============================================================================
# PATH VALIDATION
# =============================================================================
# Shared validation for the FROST published-data URI shape. Both `path_pattern`
# and `resource_id` build on this, so there is a single source of truth for the
# expected host + prefix + dataset-id layout (DRY, see review feedback).

# Parsed segments of the incoming request path, cached once for reuse below.
path_parts := restmapper.parse_path(input.request.path)

# Segments of a valid FROST published-data request: `["v1", "datasets", "<id>", ...]`
# Undefined when the request does not match — downstream rules using this helper
# inherit fail-closed semantics for free.
v1_dataset_parts := path_parts if {
	is_api_host
	count(path_parts) >= 3
	path_parts[0] == "v1"
	path_parts[1] == "datasets"
	path_parts[2] != ""
	not restmapper.is_reserved_segment(path_parts[2])
}

# =============================================================================
# PATH PATTERN MATCHING
# =============================================================================

default path_pattern := ""

# Match any request path starting with /v1/datasets/{uuid}[/*] ON THE API HOST.
# All FROST sub-resources (Things, Datastreams, Observations, etc.) map to the
# same /v1/datasets/{id} pattern — permission is always DATASET_PAYLOAD_READ (content access).
#
# Uses prefix matching instead of genericrestmapper because FROST paths have
# the dataset ID at position 2 (not at the generic REST position).
path_pattern := "/v1/datasets/{id}" if {
	v1_dataset_parts
}

# =============================================================================
# SCOPE ENFORCEMENT
# =============================================================================
# FROST paths are /v1/datasets/{dataset_id}[/*] — the {id} is always a dataset ID.
# Scope type is always DATASET since all FROST access is dataset-scoped.

# Extract dataset ID from path (third segment: /v1/datasets/{id}[/*]).
resource_id := v1_dataset_parts[2]

# All FROST endpoints are dataset-scoped
expected_scope_type := "DATASET" if {
	path_pattern != ""
}

# FROST endpoints are always resource endpoints (dataset-specific)
is_resource_endpoint if {
	resource_id
}

# Collection endpoint if marked in data (currently none — FROST is always dataset-scoped).
is_collection_endpoint if {
	restmapper.is_collection_pattern(path_pattern, endpoints)
}
