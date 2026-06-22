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
