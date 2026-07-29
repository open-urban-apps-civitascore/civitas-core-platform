# CIVITAS CORE AuthZ Policy - Dataset Authorization-Attributes Fetcher
#
# To decide a single dataset request OPA needs two concrete facts about that
# dataset that only the AuthZ Repository knows:
#   1. Which datapool it belongs to (Epic 1 union inheritance), and
#   2. Whether it is flagged for open data access (anonymous payload read, ABAC).
#
# Both come from ONE endpoint — "tell me about dataset X" — returning a small
# object { "poolId": "<uuid>"|null, "openDataAccess": true|false }. OPA memoizes
# http.send within an evaluation, so pool_of() and is_open_data() share a single
# network round-trip even though they are separate questions.
#
# Design notes:
#   - Mirrors user_context_fetcher's http.send pattern (config URL, timeout,
#     optional cross-evaluation caching).
#   - raise_error=false: a fetch outage must NOT abort the decision. The lookup
#     simply yields undefined, so direct/TENANT grants keep working and only the
#     pool-union and open-data branches are unavailable (fail-secure).
#   - Callers reach pool_of() only after finding a DATAPOOL-scoped assignment, and
#     is_open_data() only on an open-data-eligible read where the caller does NOT
#     already hold the permission (open_data.rego checks `not has_permission`
#     first), so users with direct/TENANT grants incur no extra request.
#
# The module keeps its historical name (dataset_pool_fetcher) to avoid churn in
# its many importers; it now answers both questions from the same response.

package civitas.authz.dataset_pool_fetcher

import rego.v1

# Base URL of the dataset attributes endpoint, e.g.
#   http://authz-repository:8091/api/v1/dataset-pool
# The dataset id is appended: {url}/{datasetId}
#   → { "poolId": "<uuid>"|null, "openDataAccess": true|false }
dataset_pool_url := data.config.dataset_pool_membership_url if {
	data.config.dataset_pool_membership_url
}

# Request timeout for http.send() (shared knob with user_context_fetcher).
default request_timeout := "5s"

request_timeout := data.config.authz_request_timeout if {
	data.config.authz_request_timeout
}

# Cache duration for responses (seconds). Default 0 = disabled.
# Shares the same config knob as the user-context fetch.
default cache_duration_seconds := 0

cache_duration_seconds := data.config.authz_cache_duration_seconds if {
	data.config.authz_cache_duration_seconds
}

# attributes(dataset_id) → the dataset's authorization attributes object.
# Single source of truth for both pool_of() and is_open_data(). Undefined when:
# no URL configured, empty id, or the lookup failed/non-200 — undefined (not
# false) keeps every caller fail-secure.

# Uncached path (default): fresh lookup every evaluation.
attributes(dataset_id) := body if {
	cache_duration_seconds == 0
	dataset_pool_url
	dataset_id != ""
	response := http.send({
		"method": "GET",
		"url": concat("", [dataset_pool_url, "/", dataset_id]),
		"timeout": request_timeout,
		"headers": {"Accept": "application/json"},
		"raise_error": false,
	})
	response.status_code == 200
	body := response.body
}

# Cached path: reuse the response across evaluations for cache_duration_seconds.
attributes(dataset_id) := body if {
	cache_duration_seconds > 0
	dataset_pool_url
	dataset_id != ""
	response := http.send({
		"method": "GET",
		"url": concat("", [dataset_pool_url, "/", dataset_id]),
		"timeout": request_timeout,
		"headers": {"Accept": "application/json"},
		"raise_error": false,
		"force_cache": true,
		"force_cache_duration_seconds": cache_duration_seconds,
	})
	response.status_code == 200
	body := response.body
}

# pool_of(dataset_id) → the pool id the dataset belongs to.
# Undefined when the lookup failed or the dataset has no pool (poolId null).
pool_of(dataset_id) := pool_id if {
	pool_id := attributes(dataset_id).poolId
	pool_id != null
}

# is_open_data(dataset_id) → true iff the dataset is flagged for open data access.
# Undefined (fail-secure) when the lookup failed or the flag is absent/false.
is_open_data(dataset_id) if {
	attributes(dataset_id).openDataAccess == true
}
