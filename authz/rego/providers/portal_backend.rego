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
