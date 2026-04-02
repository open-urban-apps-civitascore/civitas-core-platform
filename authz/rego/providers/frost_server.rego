# CIVITAS CORE AuthZ - FROST Server Provider
# Maps HTTP requests for the FROST Server (OGC SensorThings API proxy).
#
# FROST is accessed via APISIX gateway at dataset-scoped proxy paths:
#   /datasets/{dataset_id}       →  DATASET_READ (STA service root)
#   /datasets/{dataset_id}/...   →  DATASET_READ (STA sub-resources)
#
# The {dataset_id} is the same dataset ID from portal_backend's PostgreSQL.
# APISIX proxy-rewrite maps /datasets/{id}/* to the upstream FROST path.
# OPA only checks that the user has DATASET_READ for that specific dataset.
#
# Path structure differs from portal_backend (/v1/resource/{id}):
# FROST uses /datasets/{id} directly (ID at position 1, no version prefix).
# This requires prefix-based matching instead of the generic REST mapper's
# segment-count rules.

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
# PATH PATTERN MATCHING
# =============================================================================

default path_pattern := ""

# Match any request path starting with /datasets/{uuid}[/*].
# All FROST sub-resources (Things, Datastreams, Observations, etc.) map to
# the same /datasets/{id} pattern — permission is always DATASET_READ.
#
# Uses prefix matching instead of genericrestmapper because FROST paths have
# the dataset ID at position 1 (not position 2 like /v1/resource/{id}).
path_pattern := "/datasets/{id}" if {
	parts := restmapper.parse_path(input.request.path)
	count(parts) >= 2
	parts[0] == "datasets"
	parts[1] != ""
	not restmapper.is_reserved_segment(parts[1])
}

# =============================================================================
# SCOPE ENFORCEMENT
# =============================================================================
# FROST paths are /datasets/{dataset_id}[/*] — the {id} is always a dataset ID.
# Scope type is always DATASET since all FROST access is dataset-scoped.

# Path parts for internal use (resource ID extraction)
path_parts := restmapper.parse_path(input.request.path)

# Extract dataset ID from path (second segment: /datasets/{id}[/*])
resource_id := path_parts[1] if {
	count(path_parts) >= 2
	path_parts[0] == "datasets"
	path_parts[1] != ""
	not restmapper.is_reserved_segment(path_parts[1])
}

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
