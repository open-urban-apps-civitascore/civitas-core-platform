# CIVITAS CORE AuthZ - FROST Server Provider (Stub)
# Placeholder provider for OGC SensorThings API (FROST Server).
#
# FROST Server uses OData-style URLs which require different parsing than
# REST APIs. This stub denies all requests until proper OData parsing is
# implemented in a future milestone.
#
# OData URL examples:
#   /v1.1/Things
#   /v1.1/Things(123)
#   /v1.1/Things(123)/Datastreams
#   /v1.1/Things?$filter=name eq 'sensor1'
#
# See F-005 in backlog for OData parsing implementation

package civitas.authz.providers.frost_server

import rego.v1

import data.civitas.authz.lib.restmapper

# =============================================================================
# ENDPOINT CONFIGURATION
# =============================================================================

# Endpoints map from data file (empty for now)
endpoints := data.backends.frost_server.endpoints if {
	data.backends.frost_server.endpoints
}

endpoints := {} if {
	not data.backends.frost_server.endpoints
}

# =============================================================================
# PATH PATTERN MATCHING
# =============================================================================

# Stub: All FROST Server requests return empty pattern (denied by fail-secure)
# This is intentional - FROST Server support requires OData parsing
default path_pattern := ""

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
