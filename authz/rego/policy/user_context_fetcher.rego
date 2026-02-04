# CIVITAS CORE AuthZ Policy - User Context Fetcher
# M5: Fetches user context from AuthZ Repository via http.send()
#
# This module handles the integration between APISIX (JWT validation) and
# AuthZ Repository (permission data). When a request arrives:
#
#   1. APISIX validates JWT, sets X-Userinfo header (base64-encoded claims)
#   2. OPA decodes X-Userinfo to extract the subject (external user ID)
#   3. OPA calls AuthZ Repository to get full user context (groups, permissions)
#   4. Permission evaluation proceeds with the fetched context
#
# For testing, user_context can also be provided directly via input.user_context.
# This allows unit tests to run without an actual AuthZ Repository.
#
# Fail-secure: If AuthZ Repository is unavailable or returns an error,
# user_context remains undefined, and main.rego denies with "missing_user_context".
#
# [R-018] Also fail-secure for malformed responses: If AuthZ Repository returns
# invalid data (wrong types, missing fields), Rego's undefined semantics cause
# permission checks to not match → default deny. For example, if `groups` is not
# an array, iteration fails silently → no permission match → deny.

package civitas.authz.user_context_fetcher

import rego.v1

# =============================================================================
# CONFIGURATION
# =============================================================================

# AuthZ Repository URL - configurable via external data or default
# Default URL for development (AuthZ Repository running on host)
# In production, this should be overridden via data.config.authz_repository_url
# to point to the containerized AuthZ Repository service.
default authz_repository_url := "http://host.docker.internal:8091/api/v1/user-context"

authz_repository_url := data.config.authz_repository_url if {
    data.config.authz_repository_url
}

# Request timeout for http.send()
default request_timeout := "5s"

request_timeout := data.config.authz_request_timeout if {
    data.config.authz_request_timeout
}

# =============================================================================
# X-USERINFO HEADER DECODING
# =============================================================================

# The openid-connect plugin sets X-Userinfo as base64url-encoded JSON claims.
# We need to decode it to extract the subject (sub) claim.
#
# Note: Header names are lowercased by APISIX before reaching OPA.

# Get the raw X-Userinfo header value
# NOTE: APISIX may send headers with mixed case (X-Userinfo) or lowercase
# (x-userinfo) depending on version. We check both.
raw_userinfo_header := input.request.headers["x-userinfo"] if {
    input.request.headers["x-userinfo"]
}

# Handle mixed-case header from APISIX OPA plugin
raw_userinfo_header := input.request.headers["X-Userinfo"] if {
    not input.request.headers["x-userinfo"]
    input.request.headers["X-Userinfo"]
}

# Decode the base64url-encoded userinfo to JSON
# Returns the decoded claims object, or undefined if decoding fails
decoded_userinfo := json.unmarshal(base64url.decode(raw_userinfo_header)) if {
    raw_userinfo_header
}

# Extract the subject (externalId) from decoded JWT claims
# The 'sub' claim is the Keycloak user ID (UUID)
external_id := decoded_userinfo.sub if {
    decoded_userinfo.sub
}

# =============================================================================
# AUTHZ REPOSITORY INTEGRATION
# =============================================================================

# Fetch user context from AuthZ Repository using the external ID
# Returns the response body (user_context) if successful
#
# The http.send() call is memoized by OPA - multiple evaluations in the same
# request will reuse the cached response.
fetched_user_context := response.body if {
    external_id
    url := concat("", [authz_repository_url, "/", external_id])
    response := http.send({
        "method": "GET",
        "url": url,
        "timeout": request_timeout,
        "headers": {
            "Accept": "application/json"
        }
    })
    response.status_code == 200
}

# =============================================================================
# USER CONTEXT RESOLUTION
# =============================================================================

# [R-019] TCB minimization: Removed input.user_context fallback.
# All user context MUST come from AuthZ Repository via http.send().
# Tests mock http.send() using OPA's `with http.send as mock_fn` syntax.
# This eliminates any code path that could bypass the fetch logic.

# Use fetched user_context when userId is non-null
user_context := fetched_user_context if {
    fetched_user_context.userId != null
}

# Alternative: externalId is non-null (even if userId is null/missing)
# Note: Both rules can fire if both IDs are non-null, but they produce the
# same value (fetched_user_context) so Rego allows this.
user_context := fetched_user_context if {
    fetched_user_context.externalId != null
}

# =============================================================================
# DEBUGGING HELPERS
# =============================================================================

# Helper to check if a value is defined (not undefined)
default has_raw_userinfo_header := false
has_raw_userinfo_header if { raw_userinfo_header }

default has_external_id := false
has_external_id if { external_id }

# Expose intermediate values for debugging (visible in decision logs)
# Uses helper rules to avoid evaluation failures when values are undefined
debug_info := {
    "has_userinfo_header": has_raw_userinfo_header,
    "external_id": external_id_or_null,
    "fetch_attempted": has_external_id,
    "user_context_source": user_context_source,
}

# Safe accessor for external_id that returns null if undefined
external_id_or_null := external_id if { external_id }
default external_id_or_null := null

# [R-020] Single source: All user_context comes from AuthZ Repository.
# Tests mock http.send() to provide test data.
# Determine source of user_context for debugging
user_context_source := "fetched" if {
    fetched_user_context.userId != null
}

user_context_source := "fetched" if {
    fetched_user_context.externalId != null
}

default user_context_source := "none"
