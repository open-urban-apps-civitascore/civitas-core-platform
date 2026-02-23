# Tests for lib/genericrestmapper.rego - Reusable REST path utilities

package civitas.authz.lib.restmapper_test

import rego.v1

import data.civitas.authz.lib.restmapper

# =============================================================================
# TEST DATA
# =============================================================================

# Sample endpoints map for testing (includes new patterns)
mock_endpoints := {
	"/v2/users": {"GET": "USER_READ", "POST": "USER_CREATE"},
	"/v2/users/{id}": {"GET": "USER_READ", "PUT": "USER_UPDATE"},
	"/v2/users/me": {"GET": null},
	"/v2/datasets": {"GET": "DATASET_READ"},
	"/v2/datasets/{id}": {"GET": "DATASET_READ", "DELETE": "DATASET_DELETE"},
	"/v2/datasets/{id}/publish": {"POST": ["DATASET_UPDATE", "DATASET_PUBLISH"]},
	"/v2/datasets/{id}/assignments": {"POST": "DATASET_UPDATE"},
	"/v2/datasets/{id}/assignments/{id}": {"DELETE": "DATASET_UPDATE"},
	"/v2/datasets/{id}/published/meta": {"GET": "DATASET_READ"},
	"/v2/datastructures/{id}/versions/{id}/publish": {"POST": ["DATASTRUCTURE_UPDATE", "DATASTRUCTURE_PUBLISH"]},
}

# =============================================================================
# PATH VALIDATION TESTS
# =============================================================================

test_is_valid_path_normal if {
	restmapper.is_valid_path("/v2/users")
}

test_is_valid_path_with_id if {
	restmapper.is_valid_path("/v2/users/123")
}

test_is_valid_path_root if {
	restmapper.is_valid_path("/")
}

test_is_valid_path_uuid if {
	restmapper.is_valid_path("/v2/datasets/550e8400-e29b-41d4-a716-446655440000")
}

test_is_valid_path_rejects_path_traversal if {
	not restmapper.is_valid_path("/v2/users/../admin")
}

test_is_valid_path_rejects_double_dot_start if {
	not restmapper.is_valid_path("../etc/passwd")
}

test_is_valid_path_rejects_null_byte if {
	not restmapper.is_valid_path("/v2/users\u0000/admin")
}

test_is_valid_path_rejects_backslash if {
	not restmapper.is_valid_path("/v2/users\\admin")
}

test_is_valid_path_rejects_no_leading_slash if {
	not restmapper.is_valid_path("v2/users")
}

# =============================================================================
# PATH PARSING TESTS
# =============================================================================

test_parse_path_collection if {
	result := restmapper.parse_path("/v2/users")
	result == ["v2", "users"]
}

test_parse_path_with_id if {
	result := restmapper.parse_path("/v2/users/123")
	result == ["v2", "users", "123"]
}

test_parse_path_root if {
	result := restmapper.parse_path("/")
	result == [""]
}

test_parse_path_invalid_returns_empty if {
	result := restmapper.parse_path("/v2/users/../admin")
	result == []
}

test_parse_path_double_slash if {
	result := restmapper.parse_path("/v2//users")
	result == ["v2", "", "users"]
}

# =============================================================================
# RESERVED SEGMENT TESTS
# =============================================================================

test_is_reserved_segment_me if {
	restmapper.is_reserved_segment("me")
}

test_is_reserved_segment_regular_id if {
	not restmapper.is_reserved_segment("123")
}

test_is_reserved_segment_uuid if {
	not restmapper.is_reserved_segment("550e8400-e29b-41d4-a716-446655440000")
}

# =============================================================================
# PATTERN MATCHING TESTS
# =============================================================================

# Exact match - collection endpoint
test_match_pattern_exact_collection if {
	result := restmapper.match_pattern("/v2/users", mock_endpoints)
	result == "/v2/users"
}

# Exact match - special endpoint (users/me)
test_match_pattern_exact_special if {
	result := restmapper.match_pattern("/v2/users/me", mock_endpoints)
	result == "/v2/users/me"
}

# Pattern match - resource with ID
test_match_pattern_with_id if {
	result := restmapper.match_pattern("/v2/users/abc-123", mock_endpoints)
	result == "/v2/users/{id}"
}

# Pattern match - resource with numeric ID
test_match_pattern_with_numeric_id if {
	result := restmapper.match_pattern("/v2/datasets/12345", mock_endpoints)
	result == "/v2/datasets/{id}"
}

# Pattern match - resource with UUID
test_match_pattern_with_uuid if {
	result := restmapper.match_pattern("/v2/datasets/550e8400-e29b-41d4-a716-446655440000", mock_endpoints)
	result == "/v2/datasets/{id}"
}

# No match - unknown endpoint
test_match_pattern_unknown_endpoint if {
	result := restmapper.match_pattern("/v2/foobar", mock_endpoints)
	result == ""
}

# No match - invalid path (path traversal)
test_match_pattern_invalid_path if {
	result := restmapper.match_pattern("/v2/users/../admin", mock_endpoints)
	result == ""
}

# No match - empty endpoints
test_match_pattern_empty_endpoints if {
	result := restmapper.match_pattern("/v2/users", {})
	result == ""
}

# No match - trailing slash creates empty segment
test_match_pattern_trailing_slash if {
	result := restmapper.match_pattern("/v2/users/", mock_endpoints)
	result == ""
}

# No match - case sensitivity
test_match_pattern_case_sensitive if {
	result := restmapper.match_pattern("/V2/Users", mock_endpoints)
	result == ""
}

# =============================================================================
# 4-SEGMENT SUB-RESOURCE PATTERN MATCHING TESTS
# =============================================================================

# Pattern match - 4-segment sub-resource (/v2/datasets/{id}/publish)
test_match_pattern_4_segment_subresource if {
	result := restmapper.match_pattern("/v2/datasets/abc-123/publish", mock_endpoints)
	result == "/v2/datasets/{id}/publish"
}

# Pattern match - 4-segment with UUID
test_match_pattern_4_segment_uuid if {
	result := restmapper.match_pattern("/v2/datasets/550e8400-e29b-41d4-a716-446655440000/publish", mock_endpoints)
	result == "/v2/datasets/{id}/publish"
}

# Pattern match - 4-segment assignments sub-resource
test_match_pattern_4_segment_assignments if {
	result := restmapper.match_pattern("/v2/datasets/abc-123/assignments", mock_endpoints)
	result == "/v2/datasets/{id}/assignments"
}

# No match - 4-segment with unknown sub-resource
test_match_pattern_4_segment_unknown_subresource if {
	result := restmapper.match_pattern("/v2/datasets/abc-123/unknown", mock_endpoints)
	result == ""
}

# No match - 4-segment with reserved segment as ID
test_match_pattern_4_segment_reserved_id if {
	result := restmapper.match_pattern("/v2/datasets/me/publish", mock_endpoints)
	result == ""
}

# =============================================================================
# 5-SEGMENT SUB-RESOURCE PATTERN MATCHING TESTS (both-{id} variant)
# =============================================================================

# Pattern match - 5-segment sub-resource (/v2/datasets/{id}/assignments/{id})
test_match_pattern_5_segment_subresource if {
	result := restmapper.match_pattern("/v2/datasets/abc-123/assignments/assign-456", mock_endpoints)
	result == "/v2/datasets/{id}/assignments/{id}"
}

# Pattern match - 5-segment with UUIDs
test_match_pattern_5_segment_uuids if {
	result := restmapper.match_pattern("/v2/datasets/550e8400-e29b-41d4-a716-446655440000/assignments/660e8400-e29b-41d4-a716-446655440000", mock_endpoints)
	result == "/v2/datasets/{id}/assignments/{id}"
}

# No match - 5-segment with empty sub-resource ID (trailing slash)
test_match_pattern_5_segment_trailing_slash if {
	result := restmapper.match_pattern("/v2/datasets/abc-123/assignments/", mock_endpoints)
	result == ""
}

# No match - 5-segment with reserved segment as sub-resource ID
test_match_pattern_5_segment_reserved_subid if {
	result := restmapper.match_pattern("/v2/datasets/abc-123/assignments/me", mock_endpoints)
	result == ""
}

# =============================================================================
# 5-SEGMENT LITERAL TAIL PATTERN MATCHING TESTS
# =============================================================================

# Pattern match - 5-segment literal tail (/v2/datasets/{id}/published/meta)
test_match_pattern_5_segment_literal_tail if {
	result := restmapper.match_pattern("/v2/datasets/abc-123/published/meta", mock_endpoints)
	result == "/v2/datasets/{id}/published/meta"
}

# Pattern match - 5-segment literal tail with UUID
test_match_pattern_5_segment_literal_tail_uuid if {
	result := restmapper.match_pattern("/v2/datasets/550e8400-e29b-41d4-a716-446655440000/published/meta", mock_endpoints)
	result == "/v2/datasets/{id}/published/meta"
}

# =============================================================================
# 6-SEGMENT PATTERN MATCHING TESTS
# =============================================================================

# Pattern match - 6-segment (/v2/datastructures/{id}/versions/{id}/publish)
test_match_pattern_6_segment if {
	result := restmapper.match_pattern("/v2/datastructures/dstr-123/versions/v-456/publish", mock_endpoints)
	result == "/v2/datastructures/{id}/versions/{id}/publish"
}

# Pattern match - 6-segment with UUIDs
test_match_pattern_6_segment_uuids if {
	result := restmapper.match_pattern("/v2/datastructures/550e8400-e29b-41d4-a716-446655440000/versions/660e8400-e29b-41d4-a716-446655440000/publish", mock_endpoints)
	result == "/v2/datastructures/{id}/versions/{id}/publish"
}

# No match - 6-segment with unknown action
test_match_pattern_6_segment_unknown_action if {
	result := restmapper.match_pattern("/v2/datastructures/dstr-123/versions/v-456/unknown", mock_endpoints)
	result == ""
}

# No match - 6-segment with reserved ID
test_match_pattern_6_segment_reserved_id if {
	result := restmapper.match_pattern("/v2/datastructures/me/versions/v-456/publish", mock_endpoints)
	result == ""
}
