#!/bin/bash
# Start infrastructure services: Docker network, PostgreSQL, Keycloak
#
# Idempotent — safe to run on an already-running stack.
#
# Usage:
#   ./start-infra.sh
#
# Prerequisites:
#   - Docker and Docker Compose

set -e

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

echo ""
echo "========================================"
echo "  Infrastructure"
echo "========================================"
echo ""

# ---------- Docker Network ----------
# All services share this external network
docker network create civitas-network 2>/dev/null || true

# ---------- PostgreSQL ----------
echo "--- PostgreSQL ---"
cd "$PROJECT_ROOT/dev-environment/postgres"
docker compose up -d 2>/dev/null
echo -n "  Waiting for PostgreSQL"
for i in $(seq 1 30); do
  if docker exec civitas-postgres-portal pg_isready -U admin -q 2>/dev/null; then
    echo -e " ${GREEN}ready${NC} (${i}s)"
    break
  fi
  sleep 1
  echo -n "."
  if [ "$i" = 30 ]; then echo -e " ${RED}timeout${NC}"; exit 1; fi
done
echo ""

# ---------- Keycloak ----------
echo "--- Keycloak ---"
cd "$PROJECT_ROOT/dev-environment/keycloak"
docker compose up -d 2>/dev/null
wait_for "Keycloak" "$KEYCLOAK_URL/realms/$REALM" 90 || { fail "Keycloak did not start"; exit 1; }
echo ""

ok "Infrastructure ready"
echo ""
