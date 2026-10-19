# CIVITAS CORE AuthZ - Portal Backend Provider
# Maps HTTP requests for the Portal Backend API.
#
# This provider uses the generic REST mapper library to handle
# /v1/resource/{id} style paths used by the Portal Backend.
#
# Endpoint configuration is loaded from data.backends.portal_backend.endpoints

package civitas.authz.providers.portal_backend

import rego.v1

import data.civitas.authz.lib.restmapper

# =============================================================================
# ENDPOINT CONFIGURATION
# =============================================================================

# Endpoints map from data file
# The data file is at data/backends/portal_backend/data.json
endpoints := data.backends.portal_backend.endpoints if {
	data.backends.portal_backend.endpoints
}

endpoints := {} if {
	not data.backends.portal_backend.endpoints
}

# =============================================================================
# PATH PATTERN MATCHING
# =============================================================================

# Match the request path against our endpoints
# Input is expected to have input.request.path
path_pattern := restmapper.match_pattern(input.request.path, endpoints)

# =============================================================================
# SCOPE ENFORCEMENT (M5.1)
# =============================================================================
# Maps resources to their expected scope types and extracts resource IDs
# for scope enforcement on resource endpoints.
#
# Scope model (with TENANT inheritance per ADM spec):
#   - TENANT resources: users, groups, roles, permissions, assignments
#   - DATASET resources: datasets (TENANT scope inherits down)
#   - DATASOURCE resources: datasources (TENANT scope inherits down)
#   - DATASTRUCTURE resources: datastructures (TENANT scope inherits down)
#   - DATAPOOL resources: datapools (TENANT scope inherits down)
#
# For resource endpoints (/v1/resource/{id}), the {id} IS the scopeId.
# TENANT scope cascades to all resource endpoints (Q-005 resolved).

# Map resource name to expected scope type
resource_scope_type := {
	"users": "TENANT",
	"groups": "TENANT",
	"roles": "TENANT",
	"permissions": "TENANT",
	"assignments": "TENANT",
	"datasets": "DATASET",
	"datasources": "DATASOURCE",
	"datastructures": "DATASTRUCTURE",
	"datapools": "DATAPOOL",
}

# Path parts for internal use (scope extraction, collection detection)
path_parts := restmapper.parse_path(input.request.path)

# Extract resource name from path (second segment: /v1/{resource}/...)
resource_name := path_parts[1] if {
	count(path_parts) >= 2
}

# Extract resource ID from path (third segment: /v1/resource/{id})
# Covers both direct resources (/v1/users/{id}) and sub-resources (/v1/datasets/{id}/release)
# Only defined for resource endpoints, not collection endpoints
resource_id := path_parts[2] if {
	count(path_parts) >= 3
	path_parts[2] != ""
	not restmapper.is_reserved_segment(path_parts[2])
}

# Expected scope type for the resource being accessed
expected_scope_type := resource_scope_type[resource_name] if {
	resource_scope_type[resource_name]
}

# Is this a resource endpoint (has an ID) vs collection endpoint?
is_resource_endpoint if {
	resource_id
}

# Is this a collection endpoint?
# Data-driven: reads _collection flag from endpoint data.json instead of
# counting path segments.
is_collection_endpoint if {
	restmapper.is_collection_pattern(path_pattern, endpoints)
}
