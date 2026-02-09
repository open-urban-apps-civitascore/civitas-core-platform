#!/usr/bin/env bash
# seed-test-users.sh — Insert E2E test users into PostgreSQL and Keycloak.
#
# Prerequisites: curl, jq, and one of: psql (local) OR docker/sudo docker
#                (falls back to docker exec into civitas-postgres-portal)
# Usage:         bash e2e/scripts/seed-test-users.sh
#
# Idempotent: safe to run multiple times (uses ON CONFLICT DO NOTHING for PG,
# checks 409 for Keycloak).

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5432}"
PG_DB="${PG_DB:-portal_backend}"
PG_USER="${PG_USER:-admin}"
PG_PASSWORD="${PG_PASSWORD:-admin}"
PG_CONTAINER="${PG_CONTAINER:-civitas-postgres-portal}"

KC_URL="${KC_URL:-http://localhost:8080}"
KC_REALM="${KC_REALM:-civitas-core}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-admin}"

KC_TEST_PASSWORD="${KC_TEST_PASSWORD:-e2eTestPass1!}"

# ---------------------------------------------------------------------------
# Test users (fixed UUIDs for determinism)
# ---------------------------------------------------------------------------
# Fields: UUID | first_name | last_name | email | title | active | phone
USERS=(
  "a0000001-e2e0-4000-8000-000000000001|Anna|Schmidt|anna.schmidt@e2e.civitas.dev|MS|true|+49 170 1000001"
  "a0000002-e2e0-4000-8000-000000000002|Kenji|Tanaka|kenji.tanaka@e2e.civitas.dev|MR|true|"
  "a0000003-e2e0-4000-8000-000000000003|Amira|Okafor|amira.okafor@e2e.civitas.dev|MS|false|"
  "a0000004-e2e0-4000-8000-000000000004|Carlos|Rivera|carlos.rivera@e2e.civitas.dev|OTHER|true|+49 170 1000004"
  "a0000005-e2e0-4000-8000-000000000005|Priya|Sharma|priya.sharma@e2e.civitas.dev|MS|true|"
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() { printf "\033[1;34m▸ %s\033[0m\n" "$*"; }
ok()  { printf "\033[1;32m  ✓ %s\033[0m\n" "$*"; }
skip(){ printf "\033[1;33m  – %s\033[0m\n" "$*"; }
err() { printf "\033[1;31m  ✗ %s\033[0m\n" "$*"; }

# Detect how to run psql: local binary, docker, or sudo docker
detect_psql() {
  if command -v psql &>/dev/null; then
    PSQL_CMD="psql"
    PSQL_MODE="local"
    export PGPASSWORD="$PG_PASSWORD"
  elif command -v docker &>/dev/null && docker exec "$PG_CONTAINER" true &>/dev/null; then
    PSQL_CMD="docker exec -e PGPASSWORD=$PG_PASSWORD $PG_CONTAINER psql"
    PSQL_MODE="docker"
  elif command -v sudo &>/dev/null && sudo docker exec "$PG_CONTAINER" true &>/dev/null; then
    PSQL_CMD="sudo docker exec -e PGPASSWORD=$PG_PASSWORD $PG_CONTAINER psql"
    PSQL_MODE="sudo-docker"
  else
    err "No psql access: install psql locally or ensure Docker container '${PG_CONTAINER}' is running"
    exit 1
  fi
  log "Using psql via ${PSQL_MODE}"
}

# Run a psql command. For local mode, pass host/port; for docker, connect locally inside container.
run_psql() {
  local sql="$1"
  if [ "$PSQL_MODE" = "local" ]; then
    $PSQL_CMD -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -q -c "$sql" 2>/dev/null
  else
    $PSQL_CMD -U "$PG_USER" -d "$PG_DB" -q -c "$sql" 2>/dev/null
  fi
}

detect_psql

get_kc_admin_token() {
  curl -sf -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=admin-cli&username=${KC_ADMIN_USER}&password=${KC_ADMIN_PASS}" \
    | jq -r '.access_token'
}

# ---------------------------------------------------------------------------
# 1. Seed PostgreSQL
# ---------------------------------------------------------------------------
log "Seeding PostgreSQL (${PG_HOST}:${PG_PORT}/${PG_DB})"

for entry in "${USERS[@]}"; do
  IFS='|' read -r uuid first last email title active phone <<< "$entry"

  # Build phone value (NULL if empty)
  if [ -z "$phone" ]; then
    phone_val="NULL"
  else
    phone_val="'${phone}'"
  fi

  run_psql "
    INSERT INTO users (id, first_name, last_name, email, title, active, phone, created_at)
    VALUES ('${uuid}', '${first}', '${last}', '${email}', '${title}', ${active}, ${phone_val}, NOW())
    ON CONFLICT (email) DO NOTHING;
  " && ok "PG: ${first} ${last} (${email})" \
    || err "PG: failed to insert ${email}"
done

# ---------------------------------------------------------------------------
# 2. Seed Keycloak
# ---------------------------------------------------------------------------
log "Seeding Keycloak (${KC_URL}, realm: ${KC_REALM})"

KC_TOKEN=$(get_kc_admin_token)
if [ -z "$KC_TOKEN" ] || [ "$KC_TOKEN" = "null" ]; then
  err "Could not obtain Keycloak admin token"
  exit 1
fi

for entry in "${USERS[@]}"; do
  IFS='|' read -r uuid first last email title active phone <<< "$entry"

  kc_enabled="true"
  [ "$active" = "false" ] && kc_enabled="false"

  # Create user in Keycloak
  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${KC_URL}/admin/realms/${KC_REALM}/users" \
    -H "Authorization: Bearer ${KC_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${email}\",
      \"email\": \"${email}\",
      \"emailVerified\": true,
      \"enabled\": ${kc_enabled},
      \"firstName\": \"${first}\",
      \"lastName\": \"${last}\",
      \"credentials\": [{
        \"type\": \"password\",
        \"value\": \"${KC_TEST_PASSWORD}\",
        \"temporary\": false
      }]
    }")

  if [ "$http_code" = "201" ]; then
    ok "KC: ${first} ${last} (${email})"
  elif [ "$http_code" = "409" ]; then
    skip "KC: ${email} already exists"
  else
    err "KC: ${email} — HTTP ${http_code}"
  fi

  # Fetch the Keycloak user ID and store it as external_id in PG
  kc_user_id=$(curl -sf \
    "${KC_URL}/admin/realms/${KC_REALM}/users?email=${email}&exact=true" \
    -H "Authorization: Bearer ${KC_TOKEN}" \
    | jq -r '.[0].id // empty')

  if [ -n "$kc_user_id" ]; then
    run_psql "
      UPDATE users SET external_id = '${kc_user_id}' WHERE id = '${uuid}';
    " && ok "Link: ${email} → KC ${kc_user_id}"
  fi
done

# ---------------------------------------------------------------------------
log "Done. Seeded ${#USERS[@]} test users."
