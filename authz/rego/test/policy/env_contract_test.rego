# Tests for policy/env_contract.rego - runtime environment contract

package civitas.authz.env_contract_test

import rego.v1

import data.civitas.authz.env_contract

test_frost_api_host_is_required if {
	"FROST_API_HOST" in env_contract.required_env
}

test_configured_when_all_present if {
	env_contract.configured with opa.runtime as {"env": {"FROST_API_HOST": "api.example.test"}}
	count(env_contract.missing_env) == 0 with opa.runtime as {"env": {"FROST_API_HOST": "api.example.test"}}
}

test_missing_env_reported_when_unset if {
	env_contract.missing_env == {"FROST_API_HOST"} with opa.runtime as {"env": {}}
	not env_contract.configured with opa.runtime as {"env": {}}
}

test_empty_env_counts_as_missing if {
	env_contract.missing_env == {"FROST_API_HOST"} with opa.runtime as {"env": {"FROST_API_HOST": ""}}
}
