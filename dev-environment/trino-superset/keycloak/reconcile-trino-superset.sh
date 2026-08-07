#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

extract_first_id() {
  grep -o '"id" : "[^"]*"' | head -n 1 | cut -d '"' -f 4 || true
}

wait_for_keycloak() {
  local max_attempts=60
  local attempt=1

  while [ "$attempt" -le "$max_attempts" ]; do
    if /opt/keycloak/bin/kcadm.sh config credentials \
      --server "$KEYCLOAK_INTERNAL_URL" \
      --realm master \
      --user "$KEYCLOAK_ADMIN_USERNAME" \
      --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; then
      return 0
    fi

    sleep 2
    attempt=$((attempt + 1))
  done

  echo "Keycloak did not become ready in time" >&2
  exit 1
}

get_group_id() {
  /opt/keycloak/bin/kcadm.sh get groups -r "$KEYCLOAK_REALM" -q search="$1" | extract_first_id
}

ensure_group() {
  local group_name="$1"
  local group_id

  group_id="$(get_group_id "$group_name")"
  if [ -z "$group_id" ]; then
    /opt/keycloak/bin/kcadm.sh create groups -r "$KEYCLOAK_REALM" -s "name=$group_name" >/dev/null
    group_id="$(get_group_id "$group_name")"
  fi

  echo "$group_id"
}

get_client_id() {
  /opt/keycloak/bin/kcadm.sh get clients -r "$KEYCLOAK_REALM" -q clientId="$1" | extract_first_id
}

get_client_mapper_id() {
  /opt/keycloak/bin/kcadm.sh get "clients/$1/protocol-mappers/models" -r "$KEYCLOAK_REALM" | grep -B 1 '"name" : "groups"' | extract_first_id || true
}

write_trino_client_file() {
  cat > /tmp/civitas-trino-client.json <<EOF
{
  "clientId": "${KEYCLOAK_TRINO_CLIENT_ID}",
  "name": "CIVITAS Trino",
  "protocol": "openid-connect",
  "enabled": true,
  "clientAuthenticatorType": "client-secret",
  "secret": "${KEYCLOAK_TRINO_CLIENT_SECRET}",
  "publicClient": false,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "rootUrl": "${TRINO_PUBLIC_URL}",
  "baseUrl": "${TRINO_PUBLIC_URL}",
  "redirectUris": [
    "${TRINO_PUBLIC_URL}/oauth2/callback"
  ],
  "webOrigins": [
    "${TRINO_PUBLIC_URL}"
  ],
  "attributes": {
    "post.logout.redirect.uris": "${TRINO_PUBLIC_URL}/*"
  }
}
EOF
}

write_superset_client_file() {
  cat > /tmp/civitas-superset-client.json <<EOF
{
  "clientId": "${KEYCLOAK_SUPERSET_CLIENT_ID}",
  "name": "CIVITAS Superset",
  "protocol": "openid-connect",
  "enabled": true,
  "clientAuthenticatorType": "client-secret",
  "secret": "${KEYCLOAK_SUPERSET_CLIENT_SECRET}",
  "publicClient": false,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "rootUrl": "${SUPERSET_PUBLIC_URL}",
  "baseUrl": "${SUPERSET_PUBLIC_URL}",
  "redirectUris": [
    "${SUPERSET_PUBLIC_URL}/oauth-authorized/keycloak"
  ],
  "webOrigins": [
    "${SUPERSET_PUBLIC_URL}"
  ],
  "attributes": {
    "post.logout.redirect.uris": "${SUPERSET_PUBLIC_URL}/*"
  }
}
EOF
}

write_groups_mapper_file() {
  cat > /tmp/civitas-groups-mapper.json <<'EOF'
{
  "name": "groups",
  "protocol": "openid-connect",
  "protocolMapper": "oidc-group-membership-mapper",
  "consentRequired": false,
  "config": {
    "full.path": "false",
    "id.token.claim": "true",
    "access.token.claim": "true",
    "userinfo.token.claim": "true",
    "introspection.token.claim": "true",
    "multivalued": "true",
    "claim.name": "groups",
    "jsonType.label": "String"
  }
}
EOF
}

ensure_client() {
  local client_id_var="$1"
  local client_file="$2"
  local client_id

  client_id="$(get_client_id "${!client_id_var}")"
  if [ -z "$client_id" ]; then
    /opt/keycloak/bin/kcadm.sh create clients -r "$KEYCLOAK_REALM" -f "$client_file" >/dev/null
    client_id="$(get_client_id "${!client_id_var}")"
  else
    /opt/keycloak/bin/kcadm.sh update "clients/$client_id" -r "$KEYCLOAK_REALM" -f "$client_file" >/dev/null
  fi

  echo "$client_id"
}

ensure_groups_mapper() {
  local client_id="$1"
  local mapper_id

  mapper_id="$(get_client_mapper_id "$client_id")"
  if [ -z "$mapper_id" ]; then
    /opt/keycloak/bin/kcadm.sh create "clients/$client_id/protocol-mappers/models" -r "$KEYCLOAK_REALM" -f /tmp/civitas-groups-mapper.json >/dev/null
  fi
}

get_user_id() {
  /opt/keycloak/bin/kcadm.sh get users -r "$KEYCLOAK_REALM" -q username="$1" | extract_first_id
}

ensure_group_membership() {
  local username="$1"
  local group_id="$2"
  local user_id

  user_id="$(get_user_id "$username")"
  if [ -n "$user_id" ]; then
    /opt/keycloak/bin/kcadm.sh update "users/$user_id/groups/$group_id" -r "$KEYCLOAK_REALM" >/dev/null || true
  else
    echo "Keycloak user '$username' not found in realm '$KEYCLOAK_REALM' yet; skipping group assignment" >&2
  fi
}

require_env KEYCLOAK_INTERNAL_URL
require_env KEYCLOAK_REALM
require_env KEYCLOAK_ADMIN_USERNAME
require_env KEYCLOAK_ADMIN_PASSWORD
require_env KEYCLOAK_TRINO_CLIENT_ID
require_env KEYCLOAK_TRINO_CLIENT_SECRET
require_env KEYCLOAK_TRINO_GROUP
require_env KEYCLOAK_SUPERSET_CLIENT_ID
require_env KEYCLOAK_SUPERSET_CLIENT_SECRET
require_env KEYCLOAK_SUPERSET_GROUP
require_env TRINO_PUBLIC_URL
require_env SUPERSET_PUBLIC_URL

wait_for_keycloak

write_trino_client_file
write_superset_client_file
write_groups_mapper_file

trino_group_id="$(ensure_group "$KEYCLOAK_TRINO_GROUP")"
superset_group_id="$(ensure_group "$KEYCLOAK_SUPERSET_GROUP")"

trino_client_id="$(ensure_client KEYCLOAK_TRINO_CLIENT_ID /tmp/civitas-trino-client.json)"
superset_client_id="$(ensure_client KEYCLOAK_SUPERSET_CLIENT_ID /tmp/civitas-superset-client.json)"

ensure_groups_mapper "$trino_client_id"
ensure_groups_mapper "$superset_client_id"

if [ -n "${KEYCLOAK_DEFAULT_REALM_USER:-}" ]; then
  ensure_group_membership "$KEYCLOAK_DEFAULT_REALM_USER" "$trino_group_id"
  ensure_group_membership "$KEYCLOAK_DEFAULT_REALM_USER" "$superset_group_id"
fi

echo "Keycloak Trino/Superset reconcile completed"
exit 0
