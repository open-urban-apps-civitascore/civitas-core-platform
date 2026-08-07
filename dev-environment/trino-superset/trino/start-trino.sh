#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

generate_password_file_entry() {
  cat > /tmp/GenerateTrinoPassword.java <<'EOF'
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class GenerateTrinoPassword {
  public static void main(String[] args) throws Exception {
    String username = args[0];
    String password = args[1];
    int iterations = 100000;
    byte[] salt = new byte[16];
    SecureRandom.getInstanceStrong().nextBytes(salt);
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
    System.out.println(username + ":" + iterations + ":" + toHex(salt) + ":" + toHex(hash));
  }

  private static String toHex(byte[] bytes) {
    StringBuilder out = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      out.append(String.format("%02x", b));
    }
    return out.toString();
  }
}
EOF

  javac /tmp/GenerateTrinoPassword.java
  java -cp /tmp GenerateTrinoPassword "$TRINO_SUPERSET_SERVICE_USER" "$TRINO_SUPERSET_SERVICE_PASSWORD"
}

require_env TRINO_PUBLIC_URL
require_env TRINO_INTERNAL_SHARED_SECRET
require_env TRINO_TLS_KEYSTORE_PASSWORD
require_env TRINO_SUPERSET_SERVICE_USER
require_env TRINO_SUPERSET_SERVICE_PASSWORD
require_env KEYCLOAK_INTERNAL_URL
require_env KEYCLOAK_PUBLIC_URL
require_env KEYCLOAK_REALM
require_env KEYCLOAK_TRINO_CLIENT_ID
require_env KEYCLOAK_TRINO_CLIENT_SECRET
require_env FROST_DB_HOST
require_env FROST_DB_PORT
require_env FROST_DB_NAME
require_env FROST_TRINO_READER_PASSWORD
require_env CIVITAS_DB_HOST
require_env CIVITAS_DB_PORT
require_env CIVITAS_DB_NAME
require_env CIVITAS_DB_USER
require_env CIVITAS_DB_PASSWORD
require_env CIVITAS_DB_PASSWORD

TRINO_PUBLIC_HOST_PORT="${TRINO_PUBLIC_URL#*://}"
TRINO_PUBLIC_HOST_PORT="${TRINO_PUBLIC_HOST_PORT%%/*}"
TRINO_PUBLIC_HOST="${TRINO_PUBLIC_HOST_PORT%%:*}"

mkdir -p /data/trino/tls /etc/trino/catalog

if [ ! -f /data/trino/tls/trino.p12 ]; then
  keytool -genkeypair \
    -alias trino \
    -keyalg RSA \
    -keysize 2048 \
    -validity 3650 \
    -storetype PKCS12 \
    -keystore /data/trino/tls/trino.p12 \
    -storepass "$TRINO_TLS_KEYSTORE_PASSWORD" \
    -keypass "$TRINO_TLS_KEYSTORE_PASSWORD" \
    -dname "CN=${TRINO_PUBLIC_HOST}, OU=Development, O=CIVITAS, L=Local, ST=Local, C=DE" \
    -ext "SAN=dns:${TRINO_PUBLIC_HOST},dns:localhost,dns:trino,ip:127.0.0.1"
fi

cat > /etc/trino/node.properties <<EOF
node.environment=civitas_core_dev
node.data-dir=/data/trino
EOF

cat > /etc/trino/config.properties <<EOF
coordinator=true
node-scheduler.include-coordinator=true
http-server.http.enabled=false
http-server.https.enabled=true
http-server.https.port=8446
http-server.https.keystore.path=/data/trino/tls/trino.p12
http-server.https.keystore.key=${TRINO_TLS_KEYSTORE_PASSWORD}
http-server.authentication.type=password,oauth2
http-server.authentication.oauth2.issuer=${KEYCLOAK_PUBLIC_URL}/realms/${KEYCLOAK_REALM}
http-server.authentication.oauth2.auth-url=${KEYCLOAK_PUBLIC_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth
http-server.authentication.oauth2.token-url=${KEYCLOAK_INTERNAL_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token
http-server.authentication.oauth2.jwks-url=${KEYCLOAK_INTERNAL_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/certs
http-server.authentication.oauth2.end-session-url=${KEYCLOAK_PUBLIC_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout
http-server.authentication.oauth2.client-id=${KEYCLOAK_TRINO_CLIENT_ID}
http-server.authentication.oauth2.client-secret=${KEYCLOAK_TRINO_CLIENT_SECRET}
http-server.authentication.oauth2.state-key=${TRINO_INTERNAL_SHARED_SECRET}
http-server.authentication.oauth2.principal-field=preferred_username
http-server.authentication.oauth2.scopes=openid
http-server.authentication.oauth2.oidc.discovery=false
web-ui.authentication.type=oauth2
internal-communication.https.required=true
internal-communication.shared-secret=${TRINO_INTERNAL_SHARED_SECRET}
discovery.uri=https://127.0.0.1:8446
EOF

cat > /etc/trino/password-authenticator.properties <<EOF
password-authenticator.name=file
file.password-file=/etc/trino/password.db
EOF

cat > /etc/trino/access-control.properties <<EOF
access-control.name=opa
opa.policy.uri=http://civitas-opa:8181/v1/data/civitas/trino/allow
# Verified against the actual Trino 481 OPA plugin jar (io.trino.plugin.opa.OpaConfig's @Config
# annotations) rather than assumed from docs — the real property is opa.policy.row-filters-uri,
# not opa.row-filters.uri. Response contract (OpaRowFiltersQueryResult/OpaViewExpression) confirmed
# to match trino_row_filters.rego's {"result": [{"expression": "..."}]} shape as-is.
opa.policy.row-filters-uri=http://civitas-opa:8181/v1/data/civitas/trino/row_filters/result
opa.log-requests=true
EOF

generate_password_file_entry > /etc/trino/password.db

# Connects as frost_trino_reader, a least-privilege role scoped to the frost_scoped.* views only
# (see dev-environment/frost/migrations/01-create-scoped-views.sql) — not FROST-Server's own app
# user, which has full read-write access to every project's raw tables. Row-level scoping by
# project/dataset is enforced by the row-filter policy above, not by this connection's own grants.
cat > /etc/trino/catalog/frost.properties <<EOF
connector.name=postgresql
connection-url=jdbc:postgresql://${FROST_DB_HOST}:${FROST_DB_PORT}/${FROST_DB_NAME}
connection-user=frost_trino_reader
connection-password=${FROST_TRINO_READER_PASSWORD}
case-insensitive-name-matching=true
unsupported-type-handling=CONVERT_TO_VARCHAR
EOF

cat > /etc/trino/catalog/civitas.properties <<EOF
connector.name=postgresql
connection-url=jdbc:postgresql://${CIVITAS_DB_HOST}:${CIVITAS_DB_PORT}/${CIVITAS_DB_NAME}
connection-user=${CIVITAS_DB_USER}
connection-password=${CIVITAS_DB_PASSWORD}
case-insensitive-name-matching=true
unsupported-type-handling=CONVERT_TO_VARCHAR
EOF

exec /usr/lib/trino/bin/run-trino
