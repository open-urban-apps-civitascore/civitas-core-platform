# CIVITAS CORE AuthZ Policy - DataSource Datapool-Usability Fetcher
#
# To decide a single data source request under datapool inheritance OPA needs one
# fact that only the AuthZ Repository knows: which datapools that data source is
# assigned to. The endpoint — "tell me about data source X" — returns a small
# object { "poolIds": ["<uuid>", ...] }.
#
# The response answers the authorization question rather than exposing the storage
# model, so this policy needs no knowledge of the datapool-scope enum: poolIds lists the
# pools a data source is assigned to, and is empty when it is assigned to none.
#
# Design notes:
#   - Mirrors dataset_pool_fetcher (config URL, timeout, optional cross-evaluation
#     caching), and OPA memoizes http.send within an evaluation, so repeated
#     assigned_to_pool() checks for one data source share a single round-trip.
#   - raise_error=false: a fetch outage must NOT abort the decision. The lookup
#     yields undefined, so direct/TENANT grants keep working and only the
#     pool-inheritance branch is unavailable (fail-secure).
#   - Callers reach attributes() only after finding a DATAPOOL-scoped assignment,
#     so users with direct/TENANT grants incur no extra request.

package civitas.authz.datasource_pool_fetcher

import rego.v1

# Base URL of the data source attributes endpoint, e.g.
#   http://authz-repository:8091/api/v1/datasource-pools
# The data source id is appended: {url}/{dataSourceId}
datasource_pools_url := data.config.datasource_pools_url if {
	data.config.datasource_pools_url
}

# Request timeout for http.send() (shared knob with the other fetchers).
default request_timeout := "5s"

request_timeout := data.config.authz_request_timeout if {
	data.config.authz_request_timeout
}

# Cache duration for responses (seconds). Default 0 = disabled.
# Shares the same config knob as the user-context fetch.
#
# Non-zero trades revocation latency for round-trips: OPA caches the 200 and the 404 alike,
# so unassigning a data source from a pool keeps granting read until the entry expires.
default cache_duration_seconds := 0

cache_duration_seconds := data.config.authz_cache_duration_seconds if {
	data.config.authz_cache_duration_seconds
}

# attributes(data_source_id) → the data source's datapool assignment.
# Undefined when: no URL configured, empty id, or the lookup failed/non-200 —
# undefined (not false) keeps every caller fail-secure.

# Uncached path (default): fresh lookup every evaluation.
attributes(data_source_id) := body if {
	cache_duration_seconds == 0
	datasource_pools_url
	data_source_id != ""
	response := http.send({
		"method": "GET",
		"url": concat("", [datasource_pools_url, "/", data_source_id]),
		"timeout": request_timeout,
		"headers": {"Accept": "application/json"},
		"raise_error": false,
	})
	response.status_code == 200
	body := response.body
}

# Cached path: reuse the response across evaluations for cache_duration_seconds.
attributes(data_source_id) := body if {
	cache_duration_seconds > 0
	datasource_pools_url
	data_source_id != ""
	response := http.send({
		"method": "GET",
		"url": concat("", [datasource_pools_url, "/", data_source_id]),
		"timeout": request_timeout,
		"headers": {"Accept": "application/json"},
		"raise_error": false,
		"force_cache": true,
		"force_cache_duration_seconds": cache_duration_seconds,
	})
	response.status_code == 200
	body := response.body
}

# assigned_to_pool(data_source_id, pool_id) → true iff the data source is assigned to
# that datapool. Undefined (fail-secure) when the lookup failed. An unrestricted data
# source is assigned to no pool, so a pool grant conveys nothing on it.
assigned_to_pool(data_source_id, pool_id) if {
	pool_id in attributes(data_source_id).poolIds
}
