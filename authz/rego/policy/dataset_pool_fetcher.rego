# CIVITAS CORE AuthZ Policy - Dataset→Datapool Membership Fetcher
#
# Epic 1 union inheritance: a DATAPOOL-scoped grant applies to every dataset in
# that pool, in addition to any direct dataset assignment. To decide a single
# dataset request OPA needs to know which pool that dataset belongs to.
#
# This module asks the AuthZ Repository a CONCRETE question — "which pool is
# dataset X in?" — and gets back a single pool id (or none). It never fetches a
# list, so cost is independent of pool size (no per-request enumeration).
#
# Design notes:
#   - Mirrors user_context_fetcher's http.send pattern (config URL, timeout,
#     optional cross-evaluation caching).
#   - raise_error=false: a pool-service outage must NOT abort the decision. The
#     lookup simply yields undefined, so direct/TENANT grants keep working and
#     only the pool-based union grant is unavailable (fail-secure).
#   - Callers only reach pool_of() after finding a DATAPOOL-scoped assignment
#     carrying the required permission, so users with only direct/TENANT grants
#     incur no extra request.

package civitas.authz.dataset_pool_fetcher

import rego.v1

# Base URL of the dataset→datapool membership endpoint, e.g.
#   http://authz-repository:8091/api/v1/dataset-pool
# The dataset id is appended: {url}/{datasetId} → { "poolId": "<uuid>" | null }
dataset_pool_url := data.config.dataset_pool_membership_url if {
	data.config.dataset_pool_membership_url
}

# Request timeout for http.send() (shared knob with user_context_fetcher).
default request_timeout := "5s"

request_timeout := data.config.authz_request_timeout if {
	data.config.authz_request_timeout
}

# Cache duration for membership responses (seconds). Default 0 = disabled.
# Shares the same config knob as the user-context fetch.
default cache_duration_seconds := 0

cache_duration_seconds := data.config.authz_cache_duration_seconds if {
	data.config.authz_cache_duration_seconds
}

# pool_of(dataset_id) → the pool id the dataset belongs to.
# Undefined when: no URL configured, empty id, lookup failed/non-200, or the
# dataset has no pool. Undefined (not false) keeps callers fail-secure.

# Uncached path (default): fresh lookup every evaluation.
pool_of(dataset_id) := pool_id if {
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
	pool_id := response.body.poolId
	pool_id != null
}

# Cached path: reuse membership across evaluations for cache_duration_seconds.
pool_of(dataset_id) := pool_id if {
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
	pool_id := response.body.poolId
	pool_id != null
}
