# Tests for lib/genericrestmapper.rego - Reusable REST path utilities

package civitas.authz.lib.restmapper_test

import rego.v1

import data.civitas.authz.lib.restmapper

# =============================================================================
# TEST DATA
# =============================================================================

# Sample endpoints map for testing (includes new patterns)
mock_endpoints := {
	"/v1/users": {"GET": "USER_READ", "POST": "USER_CREATE"},
	"/v1/users/{id}": {"GET": "USER_READ", "PUT": "USER_UPDATE"},
	"/v1/users/me": {"GET": null},
	"/v1/datasets": {"GET": "DATASET_READ"},
	"/v1/datasets/{id}": {"GET": "DATASET_READ", "DELETE": "DATASET_DELETE"},
	"/v1/datasets/{id}/stage": {"POST": "DATASET_UPDATE"},
	"/v1/datasets/{id}/assignments": {"GET": "DATASET_READ"},
	"/v1/datasets/{id}/pipelines/{id}": {"GET": "DATASET_READ", "PUT": "DATASET_UPDATE"},
	"/v1/datasets/{id}/released/meta": {"PUT": ["DATASET_UPDATE", "DATASET_RELEASE"]},
	"/v1/datastructures/{id}/versions/{id}/release": {"POST": "DATASTRUCTURE_RELEASE"},
	"/v1/datastructures/{id}/versions/{id}/unrelease": {"POST": "DATASTRUCTURE_UPDATE"},
	"/v1/datastructures/{id}/versions/{id}/released/meta": {"PUT": ["DATASTRUCTURE_UPDATE", "DATASTRUCTURE_RELEASE"]},
}

# =============================================================================
# PATH VALIDATION TESTS
# =============================================================================

test_is_valid_path_normal if {
	restmapper.is_valid_path("/v1/users")
}

test_is_valid_path_with_id if {
	restmapper.is_valid_path("/v1/users/123")
}

test_is_valid_path_root if {
	restmapper.is_valid_path("/")
}

test_is_valid_path_uuid if {
	restmapper.is_valid_path("/v1/datasets/550e8400-e29b-41d4-a716-446655440000")
}

test_is_valid_path_rejects_path_traversal if {
	not restmapper.is_valid_path("/v1/users/../admin")
}

test_is_valid_path_rejects_double_dot_start if {
	not restmapper.is_valid_path("../etc/passwd")
}

test_is_valid_path_rejects_null_byte if {
	not restmapper.is_valid_path("/v1/users\u0000/admin")
}

test_is_valid_path_rejects_backslash if {
	not restmapper.is_valid_path("/v1/users\\admin")
}

test_is_valid_path_rejects_no_leading_slash if {
	not restmapper.is_valid_path("v1/users")
}

# =============================================================================
# PATH PARSING TESTS
# =============================================================================

test_parse_path_collection if {
	result := restmapper.parse_path("/v1/users")
	result == ["v1", "users"]
}

test_parse_path_with_id if {
	result := restmapper.parse_path("/v1/users/123")
	result == ["v1", "users", "123"]
}

test_parse_path_root if {
	result := restmapper.parse_path("/")
	result == [""]
}

test_parse_path_invalid_returns_empty if {
	result := restmapper.parse_path("/v1/users/../admin")
	result == []
}

test_parse_path_double_slash if {
	result := restmapper.parse_path("/v1//users")
	result == ["v1", "", "users"]
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
# COLLECTION CLASSIFICATION TESTS
# =============================================================================

mock_endpoints_with_collection := {
	"/v1/users": {"_collection": true, "GET": "USER_READ"},
	"/v1/users/{id}": {"GET": "USER_READ", "PUT": "USER_UPDATE"},
}

test_is_collection_pattern_flagged if {
	restmapper.is_collection_pattern("/v1/users", mock_endpoints_with_collection)
}

test_is_not_collection_pattern_unflagged if {
	not restmapper.is_collection_pattern("/v1/users/{id}", mock_endpoints_with_collection)
}

test_is_not_collection_pattern_unknown if {
	not restmapper.is_collection_pattern("/v1/unknown", mock_endpoints_with_collection)
}

# =============================================================================
# PATTERN MATCHING TESTS
# =============================================================================

# Exact match - collection endpoint
test_match_pattern_exact_collection if {
	result := restmapper.match_pattern("/v1/users", mock_endpoints)
	result == "/v1/users"
}

# Exact match - special endpoint (users/me)
test_match_pattern_exact_special if {
	result := restmapper.match_pattern("/v1/users/me", mock_endpoints)
	result == "/v1/users/me"
}

# Pattern match - resource with ID
test_match_pattern_with_id if {
	result := restmapper.match_pattern("/v1/users/abc-123", mock_endpoints)
	result == "/v1/users/{id}"
}

# Pattern match - resource with numeric ID
test_match_pattern_with_numeric_id if {
	result := restmapper.match_pattern("/v1/datasets/12345", mock_endpoints)
	result == "/v1/datasets/{id}"
}

# Pattern match - resource with UUID
test_match_pattern_with_uuid if {
	result := restmapper.match_pattern("/v1/datasets/550e8400-e29b-41d4-a716-446655440000", mock_endpoints)
	result == "/v1/datasets/{id}"
}

# No match - unknown endpoint
test_match_pattern_unknown_endpoint if {
	result := restmapper.match_pattern("/v1/foobar", mock_endpoints)
	result == ""
}

# No match - invalid path (path traversal)
test_match_pattern_invalid_path if {
	result := restmapper.match_pattern("/v1/users/../admin", mock_endpoints)
	result == ""
}

# No match - empty endpoints
test_match_pattern_empty_endpoints if {
	result := restmapper.match_pattern("/v1/users", {})
	result == ""
}

# No match - trailing slash creates empty segment
test_match_pattern_trailing_slash if {
	result := restmapper.match_pattern("/v1/users/", mock_endpoints)
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

# Pattern match - 4-segment sub-resource (/v1/datasets/{id}/stage)
test_match_pattern_4_segment_subresource if {
	result := restmapper.match_pattern("/v1/datasets/abc-123/stage", mock_endpoints)
	result == "/v1/datasets/{id}/stage"
}

# Pattern match - 4-segment with UUID
test_match_pattern_4_segment_uuid if {
	result := restmapper.match_pattern("/v1/datasets/550e8400-e29b-41d4-a716-446655440000/stage", mock_endpoints)
	result == "/v1/datasets/{id}/stage"
}

# Pattern match - 4-segment assignments sub-resource
test_match_pattern_4_segment_assignments if {
	result := restmapper.match_pattern("/v1/datasets/abc-123/assignments", mock_endpoints)
	result == "/v1/datasets/{id}/assignments"
}

# No match - 4-segment with unknown sub-resource
test_match_pattern_4_segment_unknown_subresource if {
	result := restmapper.match_pattern("/v1/datasets/abc-123/unknown", mock_endpoints)
	result == ""
}

# No match - 4-segment with reserved segment as ID
test_match_pattern_4_segment_reserved_id if {
	result := restmapper.match_pattern("/v1/datasets/me/stage", mock_endpoints)
	result == ""
}

# =============================================================================
# 5-SEGMENT SUB-RESOURCE PATTERN MATCHING TESTS (both-{id} variant)
# =============================================================================

# Pattern match - 5-segment sub-resource (/v1/datasets/{id}/pipelines/{id})
test_match_pattern_5_segment_subresource if {
	result := restmapper.match_pattern("/v1/datasets/abc-123/pipelines/pipe-456", mock_endpoints)
	result == "/v1/datasets/{id}/pipelines/{id}"
}

# Pattern match - 5-segment with UUIDs
test_match_pattern_5_segment_uuids if {
	result := restmapper.match_pattern("/v1/datasets/550e8400-e29b-41d4-a716-446655440000/pipelines/660e8400-e29b-41d4-a716-446655440000", mock_endpoints)
	result == "/v1/datasets/{id}/pipelines/{id}"
}

# No match - 5-segment with empty sub-resource ID (trailing slash)
test_match_pattern_5_segment_trailing_slash if {
	result := restmapper.match_pattern("/v1/datasets/abc-123/pipelines/", mock_endpoints)
	result == ""
}

# No match - 5-segment with reserved segment as sub-resource ID
test_match_pattern_5_segment_reserved_subid if {
	result := restmapper.match_pattern("/v1/datasets/abc-123/pipelines/me", mock_endpoints)
	result == ""
}

# =============================================================================
# 5-SEGMENT LITERAL TAIL PATTERN MATCHING TESTS
# =============================================================================

# Pattern match - 5-segment literal tail (/v1/datasets/{id}/released/meta)
test_match_pattern_5_segment_literal_tail if {
	result := restmapper.match_pattern("/v1/datasets/abc-123/released/meta", mock_endpoints)
	result == "/v1/datasets/{id}/released/meta"
}

# Pattern match - 5-segment literal tail with UUID
test_match_pattern_5_segment_literal_tail_uuid if {
	result := restmapper.match_pattern("/v1/datasets/550e8400-e29b-41d4-a716-446655440000/released/meta", mock_endpoints)
	result == "/v1/datasets/{id}/released/meta"
}

# =============================================================================
# 6-SEGMENT PATTERN MATCHING TESTS
# =============================================================================

# Pattern match - 6-segment (/v1/datastructures/{id}/versions/{id}/release)
test_match_pattern_6_segment if {
	result := restmapper.match_pattern("/v1/datastructures/dstr-123/versions/v-456/release", mock_endpoints)
	result == "/v1/datastructures/{id}/versions/{id}/release"
}

# Pattern match - 6-segment with UUIDs
test_match_pattern_6_segment_uuids if {
	result := restmapper.match_pattern("/v1/datastructures/550e8400-e29b-41d4-a716-446655440000/versions/660e8400-e29b-41d4-a716-446655440000/release", mock_endpoints)
	result == "/v1/datastructures/{id}/versions/{id}/release"
}

# No match - 6-segment with unknown action
test_match_pattern_6_segment_unknown_action if {
	result := restmapper.match_pattern("/v1/datastructures/dstr-123/versions/v-456/unknown", mock_endpoints)
	result == ""
}

# No match - 6-segment with reserved ID
test_match_pattern_6_segment_reserved_id if {
	result := restmapper.match_pattern("/v1/datastructures/me/versions/v-456/release", mock_endpoints)
	result == ""
}

# Pattern match - 6-segment unrelease
test_match_pattern_6_segment_unrelease if {
	result := restmapper.match_pattern("/v1/datastructures/dstr-123/versions/v-456/unrelease", mock_endpoints)
	result == "/v1/datastructures/{id}/versions/{id}/unrelease"
}

# Pattern match - 7-segment literal tail (/v1/datastructures/{id}/versions/{id}/released/meta)
test_match_pattern_7_segment_literal_tail if {
	result := restmapper.match_pattern("/v1/datastructures/dstr-123/versions/v-456/released/meta", mock_endpoints)
	result == "/v1/datastructures/{id}/versions/{id}/released/meta"
}

test_match_pattern_7_segment_literal_tail_uuids if {
	result := restmapper.match_pattern("/v1/datastructures/550e8400-e29b-41d4-a716-446655440000/versions/660e8400-e29b-41d4-a716-446655440000/released/meta", mock_endpoints)
	result == "/v1/datastructures/{id}/versions/{id}/released/meta"
}

# No match - 7-segment unknown action
test_match_pattern_7_segment_unknown_action if {
	result := restmapper.match_pattern("/v1/datastructures/dstr-123/versions/v-456/released/unknown", mock_endpoints)
	result == ""
}

# No match - 7-segment with reserved ID
test_match_pattern_7_segment_reserved_id if {
	result := restmapper.match_pattern("/v1/datastructures/me/versions/v-456/released/meta", mock_endpoints)
	result == ""
}
