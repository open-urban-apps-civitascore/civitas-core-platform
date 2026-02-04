# CIVITAS CORE AuthZ Policy - Resource Mapping (Dispatcher)
# Maps HTTP request to backend, path pattern, and method for permission lookup.
#
# This module acts as a dispatcher, routing requests to the appropriate backend
# provider based on the X-Authz-Backend header. Each backend provider handles
# its own path pattern matching.
#
# Backend identification:
#   APISIX sets X-Authz-Backend header per route (source of truth for routing).
#   Each backend provides its own mappings file in backends/{backend_id}/data.json
#
# Provider architecture:
#   - lib/genericrestmapper.rego: Shared path validation and pattern matching
#   - providers/{backend}.rego: Backend-specific path matching logic
#   - This file: Dispatcher that routes to appropriate provider

package civitas.authz.resource_mapping

import rego.v1

import data.civitas.authz.lib.restmapper
import data.civitas.authz.providers.frost_server
import data.civitas.authz.providers.portal_backend

# =============================================================================
# BACKEND IDENTIFICATION
# =============================================================================

# Backend is determined by X-Authz-Backend header set by APISIX.
# This header is the contract between APISIX (routing) and OPA (authorization).
#
# SECURITY NOTE: backend_data_key is used as a key in data.backends[key], which
# is an in-memory object lookup, NOT a filesystem read. OPA loads all data at
# startup; there's no dynamic file access. Path traversal is not possible here.
#
# NOTE: APISIX may send headers with mixed case (X-Authz-Backend) or lowercase
# (x-authz-backend) depending on version. We check both.
default backend := "unknown"

backend := header_value if {
	header_value := input.request.headers["x-authz-backend"]
	header_value != ""
	is_valid_backend_id(header_value)
}

# Handle mixed-case header from APISIX OPA plugin
backend := header_value if {
	not input.request.headers["x-authz-backend"]
	header_value := input.request.headers["X-Authz-Backend"]
	header_value != ""
	is_valid_backend_id(header_value)
}

# Backend ID validation: alphanumeric, dashes, underscores only
# Rejects any attempt to inject path traversal or special characters
is_valid_backend_id(id) if {
	regex.match(`^[a-zA-Z0-9_-]+$`, id)
}

# Normalize backend ID to match data file naming (dashes to underscores)
# e.g., "portal-backend" -> "portal_backend" for data.backends.portal_backend
backend_data_key := replace(backend, "-", "_")

# =============================================================================
# PATH VALIDATION (delegated to library)
# =============================================================================

# SECURITY: Path validation - reject suspicious patterns before any processing.
# Delegated to restmapper library for consistency across providers.
default is_valid_path := false

is_valid_path if {
	restmapper.is_valid_path(input.request.path)
}

# =============================================================================
# PATH PARSING (delegated to library)
# =============================================================================

# Extract path components (only if path is valid)
path_parts := restmapper.parse_path(input.request.path)

# Raw path from request (validated)
request_path := input.request.path if {
	is_valid_path
}

request_path := "" if {
	not is_valid_path
}

# HTTP method from request
request_method := input.request.method

# =============================================================================
# PATH PATTERN MATCHING (dispatched to providers)
# =============================================================================

# Dispatch to the appropriate provider based on backend.
# Each provider returns its path_pattern for the request.
default path_pattern := ""

# Portal Backend provider
path_pattern := portal_backend.path_pattern if {
	backend_data_key == "portal_backend"
}

# FROST Server provider (stub - returns empty, denying all requests)
path_pattern := frost_server.path_pattern if {
	backend_data_key == "frost_server"
}

# =============================================================================
# ENDPOINT DATA (dispatched to providers)
# =============================================================================

# Get the endpoints map for the current backend
# This is used by permission_eval.rego to look up permissions
default backend_endpoints := {}

backend_endpoints := portal_backend.endpoints if {
	backend_data_key == "portal_backend"
}

backend_endpoints := frost_server.endpoints if {
	backend_data_key == "frost_server"
}

# =============================================================================
# RESERVED SEGMENTS (delegated to library)
# =============================================================================

# Special path segments that are NOT resource IDs.
# Delegated to restmapper library for consistency.
is_special_segment(segment) if {
	restmapper.is_reserved_segment(segment)
}

# =============================================================================
# SCOPE ENFORCEMENT (M5.1 - dispatched to providers)
# =============================================================================
# Each provider exposes scope information for resource endpoints.
# This enables permission_eval.rego to verify that user's permission scope
# matches the resource being accessed.

# Resource ID extracted from path (e.g., /v2/datasets/{id} -> id)
# Undefined for collection endpoints
resource_id := portal_backend.resource_id if {
	backend_data_key == "portal_backend"
}

resource_id := frost_server.resource_id if {
	backend_data_key == "frost_server"
}

# Expected scope type for the resource (TENANT, DATASPACE, or DATASET)
expected_scope_type := portal_backend.expected_scope_type if {
	backend_data_key == "portal_backend"
}

expected_scope_type := frost_server.expected_scope_type if {
	backend_data_key == "frost_server"
}

# Is this a resource endpoint (has specific ID)?
default is_resource_endpoint := false

is_resource_endpoint if {
	backend_data_key == "portal_backend"
	portal_backend.is_resource_endpoint
}

is_resource_endpoint if {
	backend_data_key == "frost_server"
	frost_server.is_resource_endpoint
}

# Is this a collection endpoint (list/create)?
default is_collection_endpoint := false

is_collection_endpoint if {
	backend_data_key == "portal_backend"
	portal_backend.is_collection_endpoint
}

is_collection_endpoint if {
	backend_data_key == "frost_server"
	frost_server.is_collection_endpoint
}
