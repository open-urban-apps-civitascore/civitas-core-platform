#!/bin/sh
# Provisions the parameter contexts the config-adapter's flows reference but never carry a value
# for. Mirrors what the Helm deployment does in a cluster: create-if-missing, so a redeploy keeps
# the existing value. Without this the config-adapter's snapshot import creates the context empty,
# the MQTT SSL Context Service fails validation on an unrelated-looking truststore-password error,
# and the empty context survives the rollback.
set -eu

NIFI_HOST="${NIFI_HOST:-civitas-nifi}"
KEYCLOAK_HOST="${KEYCLOAK_HOST:-civitas-keycloak}"
# NiFi rejects a request whose Host header is not in NIFI_WEB_PROXY_HOST, and each compose file
# whitelists a different set of names.
NIFI_HOST_HEADER="${NIFI_HOST_HEADER:-localhost:8443}"
CONTEXT_NAME="NiFi Node Truststore"
PARAMETER_NAME="TRUSTSTORE_PASSWORD"

token() {
  curl -sf -X POST \
    -d "grant_type=client_credentials" \
    -d "client_id=nifi" \
    -d "client_secret=${NIFI_SECURITY_USER_OIDC_CLIENT_SECRET}" \
    "http://${KEYCLOAK_HOST}:8080/realms/civitas-core/protocol/openid-connect/token" |
    sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

api() {
  method="$1"
  path="$2"
  shift 2
  curl -k -sf -X "$method" \
    -H "Host: ${NIFI_HOST_HEADER}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    "https://${NIFI_HOST}:8443/nifi-api${path}" "$@"
}

TOKEN="$(token)"
if [ -z "${TOKEN}" ]; then
  echo "provision-parameter-contexts: could not obtain a NiFi token" >&2
  exit 1
fi

# Keep the listing in a variable: piping the request straight into grep would treat an unreachable
# NiFi as "context absent" and create a second one behind the first's back.
EXISTING="$(api GET "/flow/parameter-contexts")"
if printf '%s' "${EXISTING}" | grep -q "\"name\":\"${CONTEXT_NAME}\""; then
  echo "provision-parameter-contexts: '${CONTEXT_NAME}' already exists, leaving it untouched"
  exit 0
fi

api POST "/parameter-contexts" -d "{
  \"revision\": {\"version\": 0},
  \"component\": {
    \"name\": \"${CONTEXT_NAME}\",
    \"description\": \"Truststore of the NiFi node, for outbound TLS connections.\",
    \"parameters\": [
      {
        \"parameter\": {
          \"name\": \"${PARAMETER_NAME}\",
          \"description\": \"Password of the NiFi node truststore\",
          \"sensitive\": true,
          \"value\": \"${TRUSTSTORE_PASSWORD}\"
        }
      }
    ]
  }
}" >/dev/null

echo "provision-parameter-contexts: created '${CONTEXT_NAME}' with a value for ${PARAMETER_NAME}"
