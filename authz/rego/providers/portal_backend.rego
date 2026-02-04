# CIVITAS CORE AuthZ - Portal Backend Provider
# Maps HTTP requests for the Portal Backend API.
#
# This provider uses the generic REST mapper library to handle
# /v2/resource/{id} style paths used by the Portal Backend.
#
# Endpoint configuration is loaded from data.backends.portal_backend.endpoints

package civitas.authz.providers.portal_backend

import rego.v1

import data.civitas.authz.lib.restmapper

# =============================================================================
# ENDPOINT CONFIGURATION
# =============================================================================

# Endpoints map from data file
# The data file is at backends/portal_backend/data.json
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
# SCOPE ENFORCEMENT (M5.1)
# =============================================================================
# Maps resources to their expected scope types and extracts resource IDs
# for scope enforcement on resource endpoints.
#
# Scope model (without inheritance):
#   - TENANT resources: users, groups, roles, permissions, assignments, catalogs
#   - DATASPACE resources: dataspaces
#   - DATASET resources: datasets
#
# For resource endpoints (/v2/resource/{id}), the {id} IS the scopeId.
# TENANT-scoped resources don't require specific scopeId matching (Q-005 pending).

# Map resource name to expected scope type
resource_scope_type := {
	"users": "TENANT",
	"groups": "TENANT",
	"roles": "TENANT",
	"permissions": "TENANT",
	"assignments": "TENANT",
	"catalogs": "TENANT",
	"dataspaces": "DATASPACE",
	"datasets": "DATASET",
}

# Extract resource name from path (second segment: /v2/{resource}/...)
resource_name := path_parts[1] if {
	count(path_parts) >= 2
}

# Extract resource ID from path (third segment: /v2/resource/{id})
# Only defined for resource endpoints, not collection endpoints
resource_id := path_parts[2] if {
	count(path_parts) == 3
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

# Is this a collection endpoint (no ID)?
is_collection_endpoint if {
	count(path_parts) == 2
}

is_collection_endpoint if {
	count(path_parts) == 3
	restmapper.is_reserved_segment(path_parts[2])
}
