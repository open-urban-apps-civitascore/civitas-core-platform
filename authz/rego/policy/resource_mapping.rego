# CIVITAS CORE AuthZ Policy - Resource Mapping (Dispatcher)
# Maps HTTP request to backend, path pattern, and method for permission lookup.
#
# This module acts as a dispatcher, routing requests to the appropriate backend
# provider based on APISIX service metadata. Each backend provider handles
# its own path pattern matching.
#
# Backend identification:
#   APISIX sends service metadata to OPA via with_service=true in the OPA plugin.
#   OPA reads input.service.name to determine the backend (source of truth).
#   Each backend provides its own mappings file in data/backends/{backend_id}/data.json
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

# Backend is determined by APISIX Service name, sent via with_service=true.
# This is the contract between APISIX (routing) and OPA (authorization).
#
# APISIX sends the full Service object in input.service when with_service=true.
# The service name is the canonical backend identifier (e.g., "portal-backend").
#
# SECURITY NOTE: backend_data_key is used as a key in data.backends[key], which
# is an in-memory object lookup, NOT a filesystem read. OPA loads all data at
# startup; there's no dynamic file access. Path traversal is not possible here.
# Validation is defense-in-depth.
default backend := "unknown"

backend := service_name if {
	service_name := input.service.name
	service_name != ""
	is_valid_backend_id(service_name)
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

# FROST Server provider
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
# SCOPE ENFORCEMENT (M5.1 - dispatched to providers)
# =============================================================================
# Each provider exposes scope information for resource endpoints.
# This enables permission_eval.rego to verify that user's permission scope
# matches the resource being accessed.

# Resource ID extracted from path (e.g., /v1/datasets/{id} -> id)
# Undefined for collection endpoints
resource_id := portal_backend.resource_id if {
	backend_data_key == "portal_backend"
}

resource_id := frost_server.resource_id if {
	backend_data_key == "frost_server"
}

# Expected scope type for the resource (TENANT, DATASET, DATASOURCE, DATASTRUCTURE, or DATAPOOL)
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
