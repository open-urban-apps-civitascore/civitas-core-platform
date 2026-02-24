#!/bin/bash
# Start AuthZ application stack: test users, DB seed, builds, Backend, AuthZ services
#
# Assumes infrastructure is already running (PostgreSQL + Keycloak).
# Run start-infra.sh first, or use start-dev.sh for everything.
#
# Idempotent — safe to run on an already-running stack.
#
# Usage:
#   ./start-authz.sh              # Full authz stack with builds
#   ./start-authz.sh --skip-build # Skip Maven builds (use existing JARs)
#
# Prerequisites:
#   - Docker and Docker Compose
#   - Java 21 and Maven 3.9+
#   - jq (for JSON parsing)
#   - Infrastructure running (PostgreSQL, Keycloak)

set -e

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

# Options
SKIP_BUILD=false
for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=true ;;
  esac
done

if [ "$SKIP_BUILD" = false ]; then
  check_prerequisites
fi

echo ""
echo "========================================"
echo "  AuthZ Application Stack"
echo "========================================"
echo ""

# ---------- Verify infrastructure ----------
echo "--- Checking infrastructure ---"
if ! docker exec civitas-postgres-portal pg_isready -U admin -q 2>/dev/null; then
  fail "PostgreSQL is not running. Run start-infra.sh first."
  exit 1
fi
ok "PostgreSQL: up"

if ! curl -sf "$KEYCLOAK_URL/realms/$REALM" >/dev/null 2>&1; then
  fail "Keycloak is not running. Run start-infra.sh first."
  exit 1
fi
ok "Keycloak: up"
echo ""

# ---------- Keycloak test users (idempotent) ----------
echo "--- Keycloak test users ---"

ADMIN_TOKEN=$(curl -sf -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
    | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  fail "Could not authenticate as Keycloak admin"
  exit 1
fi

create_user() {
  local email=$1 first=$2 last=$3
  local existing=$(curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$email" \
      -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id // empty')
  if [ -n "$existing" ]; then
    echo "$existing"
    return
  fi
  curl -sf -o /dev/null -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$email\",\"username\":\"$email\",\"firstName\":\"$first\",\"lastName\":\"$last\",\"enabled\":true,\"emailVerified\":true,\"credentials\":[{\"type\":\"password\",\"value\":\"$TEST_PASSWORD\",\"temporary\":false}]}"
  curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$email" \
      -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id'
}

ADMIN_KC_ID=$(create_user "authz.admin@e2e.civitas.dev" "Authz" "Admin")
READER_KC_ID=$(create_user "authz.reader@e2e.civitas.dev" "Authz" "Reader")
NONE_KC_ID=$(create_user "authz.none@e2e.civitas.dev" "Authz" "NoPerms")

ok "admin:  $ADMIN_KC_ID"
ok "reader: $READER_KC_ID"
ok "none:   $NONE_KC_ID"

for email in "authz.admin@e2e.civitas.dev" "authz.reader@e2e.civitas.dev" "authz.none@e2e.civitas.dev"; do
  TOKEN=$(curl -sf -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
      -d "grant_type=password&client_id=portal-frontend&client_secret=$CLIENT_SECRET&username=$email&password=$TEST_PASSWORD" \
      | jq -r '.access_token // empty')
  if [ -n "$TOKEN" ]; then
    ok "$email: auth OK"
  else
    fail "$email: auth FAILED"
  fi
done
echo ""

# ---------- Maven builds ----------
if [ "$SKIP_BUILD" = false ]; then
  echo "--- Maven builds ---"

  echo -n "  Building portal-model..."
  mvn -f "$PROJECT_ROOT/portal-model/pom.xml" install -DskipTests -q 2>/dev/null && ok " done" || fail " failed"

  echo -n "  Building portal-backend..."
  mvn -f "$PROJECT_ROOT/portal-backend/pom.xml" compile -q 2>/dev/null && ok " done" || fail " failed"

  echo -n "  Building authz-repository..."
  mvn -f "$PROJECT_ROOT/authz/repository/pom.xml" package -DskipTests -q 2>/dev/null && ok " done" || fail " failed"

  echo ""
fi

# ---------- Portal Backend ----------
echo "--- Portal Backend ---"
if curl -sf http://localhost:8089/v2/actuator/health >/dev/null 2>&1; then
  ok "Already running"
else
  echo -n "  Starting..."
  cd "$PROJECT_ROOT/portal-backend"
  nohup mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres > /tmp/portal-backend.log 2>&1 &
  wait_for "Portal Backend" "http://localhost:8089/v2/actuator/health" 90 || { fail "Backend did not start"; exit 1; }
fi
echo ""

# ---------- Database seed (after backend — Flyway creates tables on startup) ----------
echo "--- Database seed ---"

TEMP_SQL=$(mktemp)
sed "s/fcb661c1-eb24-434e-8952-9826df11c29b/$ADMIN_KC_ID/g
     s/81320fd4-3de4-41c7-8360-da7ae0ed7caa/$READER_KC_ID/g
     s/83029c07-282c-44ae-a675-9db7a00b1929/$NONE_KC_ID/g" \
  "$SCRIPT_DIR/seed-authz-data.sql" > "$TEMP_SQL"

docker exec -i civitas-postgres-portal psql -U admin -d portal_backend < "$TEMP_SQL" > /dev/null 2>&1
rm "$TEMP_SQL"

USER_COUNT=$(docker exec civitas-postgres-portal psql -U admin -d portal_backend -t -c \
  "SELECT COUNT(*) FROM users WHERE email LIKE 'authz.%'" 2>/dev/null | tr -d ' ')
ok "Database seeded ($USER_COUNT authz users)"
echo ""

# ---------- AuthZ Docker stack (AuthZ Repository + OPA + APISIX) ----------
echo "--- AuthZ Docker stack ---"
cd "$PROJECT_ROOT/dev-environment/apisix"

docker compose -f docker-compose.authz.yml build --quiet 2>/dev/null
docker compose -f docker-compose.authz.yml up -d 2>/dev/null

wait_for "AuthZ Repository" "http://localhost:8091/actuator/health" 60 || { fail "AuthZ Repository did not start"; exit 1; }
wait_for "OPA" "http://localhost:8181/health" 30 || { fail "OPA did not start"; exit 1; }

docker compose up -d 2>/dev/null
wait_for_any_response "APISIX" "http://localhost:9080/v2/users" 30 || { fail "APISIX did not start"; exit 1; }
echo ""

ok "AuthZ stack ready"
echo ""
