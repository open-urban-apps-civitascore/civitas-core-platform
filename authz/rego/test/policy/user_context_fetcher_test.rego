# Tests for user_context_fetcher.rego - User context resolution
#
# These tests verify the user_context_fetcher module correctly:
#   - Decodes base64url X-Userinfo header
#   - Fetches user context from AuthZ Repository via http.send()
#   - Handles missing/invalid data gracefully
#
# Tests mock http.send() using OPA's `with http.send as mock_fn` syntax.

package civitas.authz.user_context_fetcher_test

import rego.v1

import data.civitas.authz.user_context_fetcher
import data.test.helpers.mock_http

# =============================================================================
# TEST DATA
# =============================================================================

# Sample user context for testing
sample_user_context := {
    "userId": "user-123",
    "externalId": "keycloak-sub-123",
    "groups": [{
        "id": "group-1",
        "name": "Test Group",
        "assignments": [{
            "roleId": "role-1",
            "roleName": "DataArchitect",
            "roleType": "DATA",
            "scopeType": "TENANT",
            "scopeId": "tenant-1",
            "permissions": ["READ_DATASET", "CREATE_DATASET"]
        }]
    }]
}

# Minimal user context (only externalId)
minimal_user_context := {
    "externalId": "keycloak-sub-456",
    "groups": []
}

# =============================================================================
# HTTP.SEND MOCK FUNCTIONS
# =============================================================================

# Mock that returns sample_user_context
mock_http_send_success(_) := {"status_code": 200, "body": sample_user_context}

# Mock that returns minimal_user_context
mock_http_send_minimal(_) := {"status_code": 200, "body": minimal_user_context}

# Mock that returns 404 (user not found)
mock_http_send_not_found(_) := {"status_code": 404, "body": {"error": "User not found"}}

# Mock that returns 500 (server error)
mock_http_send_error(_) := {"status_code": 500, "body": {"error": "Internal server error"}}

# Mock that returns empty body
mock_http_send_empty(_) := {"status_code": 200, "body": {}}

# Mock that returns null ids
mock_http_send_null_ids(_) := {"status_code": 200, "body": {"userId": null, "externalId": null, "groups": []}}

# =============================================================================
# USER CONTEXT FETCH TESTS
# =============================================================================

# Test: user_context is fetched when X-Userinfo header is present
test_user_context_fetched_with_header if {
    ctx := user_context_fetcher.user_context
        with http.send as mock_http_send_success
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "method": "GET",
                "path": "/v2/users",
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("keycloak-sub-123")
                }
            }
        }
    ctx.userId == "user-123"
    ctx.externalId == "keycloak-sub-123"
    count(ctx.groups) == 1
}

# Test: user_context with only externalId is valid
test_user_context_with_only_external_id if {
    ctx := user_context_fetcher.user_context
        with http.send as mock_http_send_minimal
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "method": "GET",
                "path": "/v2/users",
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("keycloak-sub-456")
                }
            }
        }
    ctx.externalId == "keycloak-sub-456"
}

# Test: user_context source is "fetched" when fetched from AuthZ Repository
test_user_context_source_is_fetched if {
    source := user_context_fetcher.user_context_source
        with http.send as mock_http_send_success
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "method": "GET",
                "path": "/v2/users",
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("keycloak-sub-123")
                }
            }
        }
    source == "fetched"
}

# Test: user_context is undefined when no X-Userinfo header
test_user_context_undefined_without_header if {
    not user_context_fetcher.user_context with input as {
        "request": {
            "method": "GET",
            "path": "/v2/users",
            "headers": {}
        }
    }
}

# Test: user_context is undefined when http.send returns 404
test_user_context_undefined_on_404 if {
    not user_context_fetcher.user_context
        with http.send as mock_http_send_not_found
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("unknown-user")
                }
            }
        }
}

# Test: user_context is undefined when http.send returns 500
test_user_context_undefined_on_error if {
    not user_context_fetcher.user_context
        with http.send as mock_http_send_error
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("some-user")
                }
            }
        }
}

# Test: Empty response body is not valid (no userId or externalId)
test_empty_response_invalid if {
    not user_context_fetcher.user_context
        with http.send as mock_http_send_empty
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("some-user")
                }
            }
        }
}

# Test: user_context with null userId and null externalId is not valid
test_null_ids_invalid if {
    not user_context_fetcher.user_context
        with http.send as mock_http_send_null_ids
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("some-user")
                }
            }
        }
}

# =============================================================================
# X-USERINFO HEADER DECODING TESTS
# =============================================================================

# Test: raw_userinfo_header extracts the header correctly (lowercase)
test_raw_userinfo_header_extraction if {
    raw := user_context_fetcher.raw_userinfo_header with input as {
        "request": {
            "headers": {
                "x-userinfo": "eyJzdWIiOiJ0ZXN0LXN1YiJ9"
            }
        }
    }
    raw == "eyJzdWIiOiJ0ZXN0LXN1YiJ9"
}

# Test: raw_userinfo_header extracts mixed-case header (X-Userinfo)
test_raw_userinfo_header_mixed_case if {
    raw := user_context_fetcher.raw_userinfo_header with input as {
        "request": {
            "headers": {
                "X-Userinfo": "eyJzdWIiOiJ0ZXN0LXN1YiJ9"
            }
        }
    }
    raw == "eyJzdWIiOiJ0ZXN0LXN1YiJ9"
}

# Test: raw_userinfo_header is undefined when header missing
test_raw_userinfo_header_missing if {
    not user_context_fetcher.raw_userinfo_header with input as {
        "request": {
            "headers": {}
        }
    }
}

# Test: decoded_userinfo correctly decodes base64url JSON
test_decoded_userinfo if {
    encoded := base64url.encode_no_pad(json.marshal({
        "sub": "test-user-id",
        "email": "test@example.com"
    }))
    decoded := user_context_fetcher.decoded_userinfo with input as {
        "request": {
            "headers": {
                "x-userinfo": encoded
            }
        }
    }
    decoded.sub == "test-user-id"
    decoded.email == "test@example.com"
}

# Test: external_id extracts sub claim from decoded userinfo
test_external_id_extraction if {
    encoded := base64url.encode_no_pad(json.marshal({
        "sub": "keycloak-user-uuid",
        "preferred_username": "testuser"
    }))
    ext_id := user_context_fetcher.external_id with input as {
        "request": {
            "headers": {
                "x-userinfo": encoded
            }
        }
    }
    ext_id == "keycloak-user-uuid"
}

# Test: external_id is undefined when sub claim missing
test_external_id_missing_sub if {
    encoded := base64url.encode_no_pad(json.marshal({
        "email": "test@example.com"
    }))
    not user_context_fetcher.external_id with input as {
        "request": {
            "headers": {
                "x-userinfo": encoded
            }
        }
    }
}

# =============================================================================
# CONFIGURATION TESTS
# =============================================================================

# Test: authz_repository_url is undefined when not configured (fail-secure)
test_no_default_authz_repository_url if {
    not user_context_fetcher.authz_repository_url with input as {} with data.config as {}
}

# Test: Default request_timeout
test_default_request_timeout if {
    timeout := user_context_fetcher.request_timeout with input as {}
    timeout == "5s"
}

# Test: Custom authz_repository_url from data.config
test_custom_authz_repository_url if {
    url := user_context_fetcher.authz_repository_url with input as {} with data.config as {
        "authz_repository_url": "http://custom-host:9999/api/v1/user-context"
    }
    url == "http://custom-host:9999/api/v1/user-context"
}

# Test: Custom request_timeout from data.config
test_custom_request_timeout if {
    timeout := user_context_fetcher.request_timeout with input as {} with data.config as {
        "authz_request_timeout": "10s"
    }
    timeout == "10s"
}

# Test: Default cache_duration_seconds is 0 (disabled)
test_default_cache_duration_seconds if {
    dur := user_context_fetcher.cache_duration_seconds with input as {}
    dur == 0
}

# Test: Custom cache_duration_seconds from data.config
test_custom_cache_duration_seconds if {
    dur := user_context_fetcher.cache_duration_seconds with input as {} with data.config as {
        "authz_cache_duration_seconds": 30
    }
    dur == 30
}

# Test: Fetch works with caching disabled (default, cache_duration_seconds == 0)
test_fetch_uncached_path if {
    ctx := user_context_fetcher.user_context
        with http.send as mock_http_send_success
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "method": "GET",
                "path": "/v2/users",
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("keycloak-sub-123")
                }
            }
        }
    ctx.userId == "user-123"
}

# Test: Fetch works with caching enabled (cache_duration_seconds > 0)
test_fetch_cached_path if {
    cached_config := object.union(mock_http.mock_config, {"authz_cache_duration_seconds": 30})
    ctx := user_context_fetcher.user_context
        with http.send as mock_http_send_success
        with data.config as cached_config
        with input as {
            "request": {
                "method": "GET",
                "path": "/v2/users",
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("keycloak-sub-123")
                }
            }
        }
    ctx.userId == "user-123"
}

# =============================================================================
# DEBUG INFO TESTS
# =============================================================================

# Test: debug_info shows no header when header missing
test_debug_info_no_header if {
    info := user_context_fetcher.debug_info with input as {
        "request": {
            "headers": {}
        }
    }
    info.has_userinfo_header == false
    info.user_context_source == "none"
}

# Test: debug_info shows header present and fetched source
test_debug_info_with_header if {
    info := user_context_fetcher.debug_info
        with http.send as mock_http_send_success
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("test-sub")
                }
            }
        }
    info.has_userinfo_header == true
    info.external_id == "test-sub"
    info.fetch_attempted == true
    info.user_context_source == "fetched"
}

# Test: debug_info shows none source when fetch fails
test_debug_info_fetch_failed if {
    info := user_context_fetcher.debug_info
        with http.send as mock_http_send_error
        with data.config as mock_http.mock_config
        with input as {
            "request": {
                "headers": {
                    "x-userinfo": mock_http.encode_userinfo("test-sub")
                }
            }
        }
    info.has_userinfo_header == true
    info.fetch_attempted == true
    info.user_context_source == "none"
}
