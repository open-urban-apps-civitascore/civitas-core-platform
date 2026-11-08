# CIVITAS CORE AuthZ Policy - Dashboard-Dataset Resolution Fetcher
#
# Resolves which Civitas dataset a rendering-engine dashboard belongs to, by the engine's own
# dashboard id (Superset calls it externalDashboardId) — the platform's own Dashboard.dataSet
# relationship is the system of record, not a copy of the dataset id stored in the dashboard's
# own metadata (e.g. Superset's json_metadata, which is only as reliable as that engine's own
# write path and schema validation).
#
# Two shapes, both against the same authz/repository resource:
#   - civitas_dataset_id_of(id)   → GET  {url}/{id}      → {"civitasDatasetId": "<uuid>"|absent}
#   - civitas_dataset_ids_of(ids) → POST {url}/batch      → [{"externalDashboardId","civitasDatasetId"}, ...]
#
# The batch form exists specifically so a caller resolving a whole dashboard list (e.g.
# civitas_security.py's DashboardAccessFilter override, via superset.rego's dashboard_list_access)
# can do it in one round-trip. OPA's http.send memoizes identical calls within one evaluation, but
# that only dedupes repeated calls to the SAME url — looping the single-id lookup over N
# dashboards would still cost N sequential round-trips. Calling the batch endpoint once and
# matching against the result in Rego is the only way to keep that O(1) in dashboard count.
#
# Design notes (mirrors dataset_pool_fetcher.rego):
#   - raise_error=false: a fetch outage must NOT abort the decision — the lookup simply yields
#     undefined, so the caller's permission check fails closed (fail-secure).
#   - Single-id lookups support cross-evaluation caching (same config knobs as
#     dataset_pool_fetcher/user_context_fetcher); the batch lookup does not — the requested id set
#     differs per call, so per-id caching doesn't apply and whole-batch caching would rarely hit.
package civitas.authz.dashboard_dataset_fetcher

import rego.v1

# Base URL of the dashboard-dataset endpoint, e.g.
#   http://authz-repository:8091/api/v1/dashboard-dataset
dashboard_dataset_url := data.config.dashboard_dataset_url if {
	data.config.dashboard_dataset_url
}

# Shares the same config knobs as dataset_pool_fetcher/user_context_fetcher.
default request_timeout := "5s"

request_timeout := data.config.authz_request_timeout if {
	data.config.authz_request_timeout
}

default cache_duration_seconds := 0

cache_duration_seconds := data.config.authz_cache_duration_seconds if {
	data.config.authz_cache_duration_seconds
}

# attributes(external_dashboard_id) → the single dashboard's resolution response.
# Undefined when: no URL configured, empty id, or the lookup failed/non-200 (fail-secure).

# Uncached path (default): fresh lookup every evaluation.
attributes(external_dashboard_id) := body if {
	cache_duration_seconds == 0
	dashboard_dataset_url
	external_dashboard_id != ""
	response := http.send({
		"method": "GET",
		"url": concat("", [dashboard_dataset_url, "/", external_dashboard_id]),
		"timeout": request_timeout,
		"headers": {"Accept": "application/json"},
		"raise_error": false,
	})
	response.status_code == 200
	body := response.body
}

# Cached path: reuse the response across evaluations for cache_duration_seconds.
attributes(external_dashboard_id) := body if {
	cache_duration_seconds > 0
	dashboard_dataset_url
	external_dashboard_id != ""
	response := http.send({
		"method": "GET",
		"url": concat("", [dashboard_dataset_url, "/", external_dashboard_id]),
		"timeout": request_timeout,
		"headers": {"Accept": "application/json"},
		"raise_error": false,
		"force_cache": true,
		"force_cache_duration_seconds": cache_duration_seconds,
	})
	response.status_code == 200
	body := response.body
}

# civitas_dataset_id_of(external_dashboard_id) → the dataset id the dashboard belongs to.
# Undefined when the lookup failed or the dashboard is unknown.
civitas_dataset_id_of(external_dashboard_id) := dataset_id if {
	dataset_id := attributes(external_dashboard_id).civitasDatasetId
	dataset_id != null
}

# civitas_dataset_ids_of(external_dashboard_ids) → {externalDashboardId: civitasDatasetId, ...}
# for every id that resolved to a known dashboard. Ids with no matching dashboard are simply
# absent from the result, same as the single lookup's "undefined" for an unknown id.
civitas_dataset_ids_of(external_dashboard_ids) := result if {
	dashboard_dataset_url
	count(external_dashboard_ids) > 0
	response := http.send({
		"method": "POST",
		"url": concat("", [dashboard_dataset_url, "/batch"]),
		"timeout": request_timeout,
		"headers": {"Accept": "application/json", "Content-Type": "application/json"},
		"body": {"externalDashboardIds": external_dashboard_ids},
		"raise_error": false,
	})
	response.status_code == 200
	result := {item.externalDashboardId: item.civitasDatasetId |
		some item in response.body
	}
}
