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

# AuthZ Repository URL — REQUIRED, no default.
# If not configured, all authorization decisions will deny (fail-secure).
#
# HOW TO CONFIGURE:
# OPA reads configuration from JSON files mounted into its /data/ directory.
# Any file at /data/<name>.json becomes accessible in Rego as data.<name>.
# So /data/config.json → data.config, and we read data.config.authz_repository_url.
#
# Step 1: Create a JSON file with the URL:
#
#   {
#     "config": {
#       "authz_repository_url": "http://authz-repository:8091/api/v1/user-context"
#     }
#   }
#
# Step 2: Mount it into the OPA container at /data/config.json:
#
#   # Docker Compose example:
#   volumes:
#     - ./opa-config.json:/data/config.json:ro
#
#   # Kubernetes example:
#   volumeMounts:
#     - name: opa-config
#       mountPath: /data/config.json
#       subPath: config.json
#       readOnly: true
#
# For a working example, see: dev-environment/authz/docker-compose.yml
# and dev-environment/authz/opa-config.json
authz_repository_url := data.config.authz_repository_url if {
	data.config.authz_repository_url
}

# Request timeout for http.send()
default request_timeout := "5s"

request_timeout := data.config.authz_request_timeout if {
	data.config.authz_request_timeout
}

# Cache duration for AuthZ Repository responses (seconds).
# Default 0 = disabled (every OPA evaluation fetches fresh data).
# When > 0, OPA's http.send() caches the response for this duration.
# This is a production lifeline — enable by setting authz_cache_duration_seconds
# in opa-config.json and restarting OPA. No code change required.
default cache_duration_seconds := 0

cache_duration_seconds := data.config.authz_cache_duration_seconds if {
	data.config.authz_cache_duration_seconds
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
# The http.send() call is memoized by OPA within a single evaluation.
# When cache_duration_seconds > 0, OPA also caches across evaluations
# using force_cache/force_cache_duration_seconds.

# Uncached path (default): fresh fetch every evaluation
fetched_user_context := response.body if {
	cache_duration_seconds == 0
	external_id
	url := concat("", [authz_repository_url, "/", external_id])
	response := http.send({
		"method": "GET",
		"url": url,
		"timeout": request_timeout,
		"headers": {"Accept": "application/json"},
	})
	response.status_code == 200
}

# Cached path: reuse response across evaluations for cache_duration_seconds
fetched_user_context := response.body if {
	cache_duration_seconds > 0
	external_id
	url := concat("", [authz_repository_url, "/", external_id])
	response := http.send({
		"method": "GET",
		"url": url,
		"timeout": request_timeout,
		"headers": {"Accept": "application/json"},
		"force_cache": true,
		"force_cache_duration_seconds": cache_duration_seconds,
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

# Shared identity check: fetched response has a non-null userId or externalId.
# Used by user_context assignment (below), main.rego's has_user_context,
# and permission_eval.rego's is_authenticated.
default has_valid_identity := false

has_valid_identity if {
	fetched_user_context.userId != null
}

has_valid_identity if {
	fetched_user_context.externalId != null
}

# Use fetched user_context when identity is valid
user_context := fetched_user_context if {
	has_valid_identity
}

# =============================================================================
# DEBUGGING HELPERS
# =============================================================================

# Helper to check if a value is defined (not undefined)
default has_raw_userinfo_header := false

has_raw_userinfo_header if raw_userinfo_header

default has_external_id := false

has_external_id if external_id

# Expose intermediate values for debugging (visible in decision logs)
# Uses helper rules to avoid evaluation failures when values are undefined
debug_info := {
	"has_userinfo_header": has_raw_userinfo_header,
	"external_id": external_id_or_null,
	"fetch_attempted": has_external_id,
	"user_context_source": user_context_source,
}

# Safe accessor for external_id that returns null if undefined
external_id_or_null := external_id if external_id

default external_id_or_null := null

# [R-020] Single source: All user_context comes from AuthZ Repository.
# Tests mock http.send() to provide test data.
# Determine source of user_context for debugging
user_context_source := "fetched" if {
	has_valid_identity
}

default user_context_source := "none"
