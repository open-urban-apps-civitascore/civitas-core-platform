# CIVITAS CORE AuthZ - Generic REST Mapper Library
# Reusable path validation and pattern matching for REST APIs.
#
# This library provides backend-agnostic path parsing that can be used by
# any provider implementing REST-style /version/resource/{id} patterns.
#
# Providers import this library and call match_pattern with their specific
# endpoint configuration.

package civitas.authz.lib.restmapper

import rego.v1

# =============================================================================
# PATH VALIDATION
# =============================================================================
# SECURITY: Path validation - reject suspicious patterns before any processing.
# Unknown/invalid paths are denied by default (fail-secure), but explicit
# validation provides defense-in-depth and clearer error attribution.

# Validate a path string for security issues
# Returns true if path is safe to process
is_valid_path(path) if {
	# Must start with /
	startswith(path, "/")

	# No path traversal sequences (even URL-encoded, APISIX should normalize)
	not contains(path, "..")

	# No null bytes
	not contains(path, "\u0000")

	# No backslashes (Windows path separator injection)
	not contains(path, "\\")

	# Reasonable length (prevent DoS via regex)
	count(path) < 2048
}

# Default: invalid path
default is_valid_path(_) := false

# =============================================================================
# PATH PARSING
# =============================================================================

# Parse a path into its component parts (segments)
# Returns array of path segments, or empty array if invalid
parse_path(path) := parts if {
	is_valid_path(path)
	parts := split(trim_prefix(path, "/"), "/")
}

parse_path(path) := [] if {
	not is_valid_path(path)
}

# =============================================================================
# RESERVED SEGMENTS
# =============================================================================

# Check if a segment is reserved (not a resource ID)
# Reserved segments get exact-match handling rather than {id} substitution
# Default reserved segments - providers can extend this list
default is_reserved_segment(_) := false

is_reserved_segment("me")

# =============================================================================
# COLLECTION CLASSIFICATION
# =============================================================================

# Check if a matched pattern is marked as a collection endpoint in endpoint data.
# Collection endpoints have relaxed scope enforcement (no specific resource ID).
# The _collection flag is set in data.json for each endpoint pattern.
default is_collection_pattern(_, _) := false

is_collection_pattern(pattern, endpoints) if {
	endpoints[pattern]._collection == true
}

# =============================================================================
# PATTERN MATCHING
# =============================================================================

# Match a path against an endpoints map, returning the matched pattern.
# Tries exact match first, then pattern with {id} substitution.
#
# Args:
#   path: The request path (e.g., "/v2/users/123")
#   endpoints: Map of pattern -> method config (e.g., {"/v2/users/{id}": {...}})
#
# Returns:
#   The matched pattern string, or "" if no match

# 1. Exact match - path exists directly in endpoints
match_pattern(path, endpoints) := path if {
	is_valid_path(path)
	endpoints[path]
}

# 2. Pattern match - replace third segment with {id}
# Only matches paths with exactly 3 non-empty segments: /version/resource/id
match_pattern(path, endpoints) := pattern if {
	is_valid_path(path)
	not endpoints[path]

	parts := parse_path(path)
	count(parts) == 3
	parts[2] != "" # Not empty (trailing slash)
	not is_reserved_segment(parts[2])

	pattern := concat("/", ["", parts[0], parts[1], "{id}"])
	endpoints[pattern]
}

# 3. Pattern match - 4-segment sub-resource: /version/resource/id/action
match_pattern(path, endpoints) := pattern if {
	is_valid_path(path)
	not endpoints[path]

	parts := parse_path(path)
	count(parts) == 4
	parts[2] != ""
	not is_reserved_segment(parts[2])

	pattern := concat("/", ["", parts[0], parts[1], "{id}", parts[3]])
	endpoints[pattern]
}

# 4. Pattern match - 5-segment sub-resource with two IDs: /version/resource/id/sub/id
match_pattern(path, endpoints) := pattern if {
	is_valid_path(path)
	not endpoints[path]

	parts := parse_path(path)
	count(parts) == 5
	parts[2] != ""
	not is_reserved_segment(parts[2])
	parts[4] != ""
	not is_reserved_segment(parts[4])

	pattern := concat("/", ["", parts[0], parts[1], "{id}", parts[3], "{id}"])
	endpoints[pattern]
}

# 5. Pattern match - 5-segment with literal tail: /version/resource/id/literal/literal
# e.g., /v2/datasets/{id}/published/meta (only parts[2] is {id})
# Guard: only fires if the both-{id} variant does NOT exist in endpoints,
# preventing conflict with rule 4.
#
# KNOWN LIMITATION (TD-024): Rules 4 and 5 are mutually exclusive per prefix.
# If data.json contains BOTH /v2/foo/{id}/bar/{id} AND /v2/foo/{id}/bar/literal,
# the guard suppresses rule 5 and rule 4 treats the literal as an {id}.
# Fix: replace positional heuristics with pattern-iterating matcher (iterate
# all patterns, match {id} as wildcard, literals as exact). See BACKLOG.md.
match_pattern(path, endpoints) := pattern if {
	is_valid_path(path)
	not endpoints[path]

	parts := parse_path(path)
	count(parts) == 5
	parts[2] != ""
	not is_reserved_segment(parts[2])

	# Guard: the both-{id} variant must NOT exist (prevents conflict with rule 4)
	both_id_pattern := concat("/", ["", parts[0], parts[1], "{id}", parts[3], "{id}"])
	not endpoints[both_id_pattern]

	pattern := concat("/", ["", parts[0], parts[1], "{id}", parts[3], parts[4]])
	endpoints[pattern]
}

# 6. Pattern match - 6-segment sub-resource: /version/resource/id/sub/id/action
# e.g., /v2/datastructures/{id}/versions/{id}/publish
match_pattern(path, endpoints) := pattern if {
	is_valid_path(path)
	not endpoints[path]

	parts := parse_path(path)
	count(parts) == 6
	parts[2] != ""
	not is_reserved_segment(parts[2])
	parts[4] != ""
	not is_reserved_segment(parts[4])

	pattern := concat("/", ["", parts[0], parts[1], "{id}", parts[3], "{id}", parts[5]])
	endpoints[pattern]
}

# 7. Pattern match - 7-segment with literal tail: /version/resource/id/sub/id/literal/literal
# e.g., /v2/datastructures/{id}/versions/{id}/published/meta
match_pattern(path, endpoints) := pattern if {
	is_valid_path(path)
	not endpoints[path]

	parts := parse_path(path)
	count(parts) == 7
	parts[2] != ""
	not is_reserved_segment(parts[2])
	parts[4] != ""
	not is_reserved_segment(parts[4])

	pattern := concat("/", ["", parts[0], parts[1], "{id}", parts[3], "{id}", parts[5], parts[6]])
	endpoints[pattern]
}

# 8. No match - return empty string
default match_pattern(_, _) := ""
