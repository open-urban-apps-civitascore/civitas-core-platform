# Decision Log Masking Policy
#
# OPA's decision_logs.console dumps full input to stdout. Without masking,
# this includes raw JWTs and encoded user claims — tokens that MUST NOT
# appear in logs (TR-03187, OWASP Logging Cheat Sheet).
#
# This policy removes sensitive authentication headers from decision log
# entries before they are written. The mask paths use JSON Pointer notation
# into the decision log entry structure.
#
# See: https://www.openpolicyagent.org/docs/latest/management-decision-logs/#masking

package system.log

import rego.v1

# Raw JWT bearer token — never needed in logs
mask contains "/input/request/headers/X-Access-Token"
mask contains "/input/request/headers/x-access-token"

# Base64-encoded user claims (contains sub, email, name) — PII
mask contains "/input/request/headers/X-Userinfo"
mask contains "/input/request/headers/x-userinfo"

# Belt-and-suspenders: mask Authorization header if APISIX ever forwards it
mask contains "/input/request/headers/authorization"
mask contains "/input/request/headers/Authorization"
