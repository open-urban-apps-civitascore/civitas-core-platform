# Tests for datasource_pool_fetcher.rego - DataSource→Datapool assignment lookup
#
# Mocks http.send() the same way the rest of the suite does.

package civitas.authz.datasource_pool_fetcher_test

import rego.v1

import data.civitas.authz.datasource_pool_fetcher
import data.test.helpers.mock_http

# Both the unrestricted and the unusable data source report no pools — the endpoint answers
# the assignment question, so the DatapoolScopeType distinction never reaches this layer.
mock_no_pools(_) := {"status_code": 200, "body": {"poolIds": []}}

mock_assigned(_) := {"status_code": 200, "body": {"poolIds": ["pool-1", "pool-2"]}}

mock_404(_) := {"status_code": 404, "body": {}}

mock_503(_) := {"status_code": 503, "body": {}}

# A 200 whose body lacks poolIds entirely (stale contract)
mock_missing_field(_) := {"status_code": 200, "body": {}}

# A non-JSON response body — OPA sets body to null
mock_non_json(_) := {"status_code": 200, "body": null}

# A data source assigned to no pool — unrestricted or unusable alike — is reached by no pool grant
test_source_without_pools_is_assigned_to_no_pool if {
	not datasource_pool_fetcher.assigned_to_pool("src-1", "pool-99") with http.send as mock_no_pools
		with data.config as mock_http.mock_config
}

# An assigned data source is reached through each pool it names
test_assigned_source_reached_through_its_pool if {
	datasource_pool_fetcher.assigned_to_pool("src-1", "pool-2") with http.send as mock_assigned
		with data.config as mock_http.mock_config
}

# ... and through no other
test_assigned_source_not_reached_through_other_pool if {
	not datasource_pool_fetcher.assigned_to_pool("src-1", "pool-99") with http.send as mock_assigned
		with data.config as mock_http.mock_config
}

# Unknown data source → undefined, never a grant
test_unknown_source_is_undefined if {
	not datasource_pool_fetcher.assigned_to_pool("src-1", "pool-1") with http.send as mock_404
		with data.config as mock_http.mock_config
}

# Lookup outage → undefined, never a grant (fail-secure)
test_outage_is_undefined if {
	not datasource_pool_fetcher.assigned_to_pool("src-1", "pool-1") with http.send as mock_503
		with data.config as mock_http.mock_config
}

# No configured URL → undefined, never a grant
test_missing_config_is_undefined if {
	not datasource_pool_fetcher.assigned_to_pool("src-1", "pool-1") with http.send as mock_no_pools
		with data.config as {}
}

# Empty data source id is never looked up
test_empty_id_is_undefined if {
	not datasource_pool_fetcher.assigned_to_pool("", "pool-1") with http.send as mock_no_pools
		with data.config as mock_http.mock_config
}

# A 200 that no longer carries poolIds denies rather than granting on an absent field
test_missing_pool_ids_field_is_undefined if {
	not datasource_pool_fetcher.assigned_to_pool("src-1", "pool-1") with http.send as mock_missing_field
		with data.config as mock_http.mock_config
}

# A non-JSON body denies
test_non_json_body_is_undefined if {
	not datasource_pool_fetcher.assigned_to_pool("src-1", "pool-1") with http.send as mock_non_json
		with data.config as mock_http.mock_config
}

# =============================================================================
# Cached path (authz_cache_duration_seconds > 0) — exercises the force_cache branch
# =============================================================================

cached_config := object.union(mock_http.mock_config, {"authz_cache_duration_seconds": 60})

test_assigned_source_reached_on_cached_path if {
	datasource_pool_fetcher.assigned_to_pool("src-1", "pool-2") with http.send as mock_assigned
		with data.config as cached_config
}

test_outage_is_undefined_on_cached_path if {
	not datasource_pool_fetcher.assigned_to_pool("src-1", "pool-1") with http.send as mock_503
		with data.config as cached_config
}
