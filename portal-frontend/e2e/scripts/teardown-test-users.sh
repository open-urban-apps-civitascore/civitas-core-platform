#!/usr/bin/env bash
# teardown-test-users.sh — Remove E2E test users from PostgreSQL and Keycloak.
#
# Prerequisites: curl, jq, and one of: psql (local) OR docker/sudo docker
#                (falls back to docker exec into civitas-postgres-portal)
# Usage:         bash e2e/scripts/teardown-test-users.sh
#
# Idempotent: safe to run even if users have already been removed.

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

# ---------------------------------------------------------------------------
# Test user identifiers (must match seed-test-users.sh)
# ---------------------------------------------------------------------------
UUIDS=(
  "a0000001-e2e0-4000-8000-000000000001"
  "a0000002-e2e0-4000-8000-000000000002"
  "a0000003-e2e0-4000-8000-000000000003"
  "a0000004-e2e0-4000-8000-000000000004"
  "a0000005-e2e0-4000-8000-000000000005"
)

EMAILS=(
  "anna.schmidt@e2e.civitas.dev"
  "kenji.tanaka@e2e.civitas.dev"
  "amira.okafor@e2e.civitas.dev"
  "carlos.rivera@e2e.civitas.dev"
  "priya.sharma@e2e.civitas.dev"
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

# Run a psql command, returning raw output. For local mode, pass host/port.
run_psql() {
  local sql="$1"
  if [ "$PSQL_MODE" = "local" ]; then
    $PSQL_CMD -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tAc "$sql" 2>/dev/null
  else
    $PSQL_CMD -U "$PG_USER" -d "$PG_DB" -tAc "$sql" 2>/dev/null
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
# 1. Remove from Keycloak (do this first — DB has the external_id reference)
# ---------------------------------------------------------------------------
log "Removing test users from Keycloak (${KC_URL}, realm: ${KC_REALM})"

KC_TOKEN=$(get_kc_admin_token)
if [ -z "$KC_TOKEN" ] || [ "$KC_TOKEN" = "null" ]; then
  err "Could not obtain Keycloak admin token — skipping Keycloak cleanup"
else
  for email in "${EMAILS[@]}"; do
    kc_user_id=$(curl -sf \
      "${KC_URL}/admin/realms/${KC_REALM}/users?email=${email}&exact=true" \
      -H "Authorization: Bearer ${KC_TOKEN}" \
      | jq -r '.[0].id // empty')

    if [ -n "$kc_user_id" ]; then
      http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X DELETE "${KC_URL}/admin/realms/${KC_REALM}/users/${kc_user_id}" \
        -H "Authorization: Bearer ${KC_TOKEN}")

      if [ "$http_code" = "204" ]; then
        ok "KC: deleted ${email} (${kc_user_id})"
      else
        err "KC: failed to delete ${email} — HTTP ${http_code}"
      fi
    else
      skip "KC: ${email} not found"
    fi
  done
fi

# ---------------------------------------------------------------------------
# 2. Remove from PostgreSQL
# ---------------------------------------------------------------------------
log "Removing test users from PostgreSQL (${PG_HOST}:${PG_PORT}/${PG_DB})"

uuid_list=$(printf "'%s'," "${UUIDS[@]}")
uuid_list="${uuid_list%,}"  # trim trailing comma

deleted=$(run_psql "DELETE FROM users WHERE id IN (${uuid_list}) RETURNING id;" | wc -l)

if [ "$deleted" -gt 0 ]; then
  ok "PG: deleted ${deleted} user(s)"
else
  skip "PG: no test users found to delete"
fi

# ---------------------------------------------------------------------------
log "Done. Teardown complete."
