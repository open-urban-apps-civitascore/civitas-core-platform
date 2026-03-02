# CIVITAS CORE AuthZ - FROST Server Provider
# Maps HTTP requests for the FROST Server (OGC SensorThings API proxy).
#
# FROST Server is accessed via a dataset-scoped proxy path:
#   /api/v1/{dataset_id}/sta  →  DATASET_READ
#
# The {dataset_id} is the same dataset ID from portal_backend's PostgreSQL.
# APISIX routes to the correct FROST instance based on the dataset ID;
# OPA only checks that the user has DATASET_READ for that specific dataset.
#
# Note: Native OData endpoints (/v1.1/Things etc.) are NOT exposed through
# APISIX. All FROST access goes through the /api/v1/{id}/sta proxy path.

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

# Match request path against FROST endpoints using generic REST mapper.
# The 4-segment rule handles /api/v1/{id}/sta pattern matching.
path_pattern := restmapper.match_pattern(input.request.path, endpoints)

# =============================================================================
# REQUEST ACCESSORS
# =============================================================================

# HTTP method from request
request_method := input.request.method

# Request path (validated)
request_path := input.request.path if {
	restmapper.is_valid_path(input.request.path)
}

request_path := "" if {
	not restmapper.is_valid_path(input.request.path)
}

# Path parts for debugging/logging
path_parts := restmapper.parse_path(input.request.path)

# =============================================================================
# SCOPE ENFORCEMENT
# =============================================================================
# FROST paths are /api/v1/{dataset_id}/sta — the {id} is always a dataset ID.
# Scope type is always DATASET since all FROST access is dataset-scoped.

# Extract dataset ID from path (third segment: /api/v1/{id}/sta)
resource_id := path_parts[2] if {
	count(path_parts) == 4
	path_parts[2] != ""
	not restmapper.is_reserved_segment(path_parts[2])
}

# All FROST resources are dataset-scoped
expected_scope_type := "DATASET" if {
	resource_id
}

# FROST endpoints are always resource endpoints (dataset-specific)
is_resource_endpoint if {
	resource_id
}

# Collection endpoint if marked in data (currently none — FROST is always dataset-scoped).
is_collection_endpoint if {
	restmapper.is_collection_pattern(path_pattern, endpoints)
}
