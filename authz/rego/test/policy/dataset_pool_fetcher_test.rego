# Tests for dataset_pool_fetcher.rego - Dataset→Datapool membership lookup
#
# Mocks http.send() the same way the rest of the suite does.

package civitas.authz.dataset_pool_fetcher_test

import rego.v1

import data.civitas.authz.dataset_pool_fetcher
import data.test.helpers.mock_http

mock_pool(_) := {"status_code": 200, "body": {"poolId": "pool-1"}}

mock_pool_null(_) := {"status_code": 200, "body": {"poolId": null}}

mock_pool_404(_) := {"status_code": 404, "body": {}}

# Returns the pool id for a dataset that belongs to a pool
test_pool_of_returns_pool if {
	result := dataset_pool_fetcher.pool_of("ds-1") with http.send as mock_pool
		with data.config as mock_http.mock_config
	result == "pool-1"
}

# Undefined when the dataset has no pool (poolId == null)
test_pool_of_undefined_on_null if {
	not dataset_pool_fetcher.pool_of("ds-1") with http.send as mock_pool_null
		with data.config as mock_http.mock_config
}

# Undefined when the lookup fails (non-200) — fail-secure, never grants on error
test_pool_of_undefined_on_error if {
	not dataset_pool_fetcher.pool_of("ds-1") with http.send as mock_pool_404
		with data.config as mock_http.mock_config
}

# Undefined when no membership URL is configured
test_pool_of_undefined_without_url if {
	not dataset_pool_fetcher.pool_of("ds-1") with http.send as mock_pool
		with data.config as {}
}

# Undefined for an empty dataset id
test_pool_of_undefined_on_empty_id if {
	not dataset_pool_fetcher.pool_of("") with http.send as mock_pool
		with data.config as mock_http.mock_config
}

# =============================================================================
# is_open_data() — open-data flag from the same attributes response
# =============================================================================

mock_attrs_open(_) := {"status_code": 200, "body": {"poolId": null, "openDataAccess": true}}

mock_attrs_not_open(_) := {"status_code": 200, "body": {"poolId": "pool-1", "openDataAccess": false}}

mock_attrs_no_flag(_) := {"status_code": 200, "body": {"poolId": null}}

mock_attrs_503(_) := {"status_code": 503, "body": {}}

# True when the dataset is flagged open
test_is_open_data_true_when_flagged if {
	dataset_pool_fetcher.is_open_data("ds-1") with http.send as mock_attrs_open
		with data.config as mock_http.mock_config
}

# Undefined (not true) when the flag is explicitly false
test_is_open_data_undefined_when_flag_false if {
	not dataset_pool_fetcher.is_open_data("ds-1") with http.send as mock_attrs_not_open
		with data.config as mock_http.mock_config
}

# Undefined when the attributes response omits the flag (fail-secure)
test_is_open_data_undefined_when_flag_absent if {
	not dataset_pool_fetcher.is_open_data("ds-1") with http.send as mock_attrs_no_flag
		with data.config as mock_http.mock_config
}

# Undefined on a fetch outage (fail-secure — never opens on error)
test_is_open_data_undefined_on_outage if {
	not dataset_pool_fetcher.is_open_data("ds-1") with http.send as mock_attrs_503
		with data.config as mock_http.mock_config
}

# pool_of from the same response is independent of the open-data flag
test_pool_of_from_attributes_with_flag if {
	result := dataset_pool_fetcher.pool_of("ds-1") with http.send as mock_attrs_not_open
		with data.config as mock_http.mock_config
	result == "pool-1"
}

# =============================================================================
# Cached path (authz_cache_duration_seconds > 0) — exercises the force_cache branch
# =============================================================================

cached_config := object.union(mock_http.mock_config, {"authz_cache_duration_seconds": 60})

test_pool_of_cached_path if {
	result := dataset_pool_fetcher.pool_of("ds-1") with http.send as mock_pool
		with data.config as cached_config
	result == "pool-1"
}

test_is_open_data_cached_path if {
	dataset_pool_fetcher.is_open_data("ds-1") with http.send as mock_attrs_open
		with data.config as cached_config
}
