# Contract for runtime env vars read via opa.runtime().env. build-bundle.sh fails
# the build if a used key is missing from required_env.

package civitas.authz.env_contract

import rego.v1

# Runtime env vars the bundle requires.
required_env := {"API_HOST"}

# Required env vars that are unset or empty at runtime.
missing_env contains key if {
	some key in required_env
	val := object.get(opa.runtime().env, key, "")
	val == ""
}

# True when every required env var is set.
default configured := false

configured if {
	count(missing_env) == 0
}
