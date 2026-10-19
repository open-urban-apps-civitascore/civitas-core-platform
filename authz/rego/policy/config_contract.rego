# Contract for bundle config read via data.config.*. build-bundle.sh fails the build
# if a used key is declared in neither set, or if a required key is missing from the
# config.json baked into the published OPA image (authz/Dockerfile).

package civitas.authz.config_contract

import rego.v1

# Keys the bundle cannot work without. Each MUST be present in every environment's
# config, including the image-baked default — a missing one silently disables the
# policy branch that reads it (fail-secure, but inert and hard to diagnose).
required_config := {
	"authz_repository_url",
	"dataset_pool_membership_url",
}

# Keys with a policy-side default, safe to omit from an environment's config.
optional_config := {
	"authz_cache_duration_seconds",
	"authz_request_timeout",
}
