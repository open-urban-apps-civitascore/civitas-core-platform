#!/bin/bash
# CIVITAS CORE Platform — Dev Environment Start (with Authorization)
#
# One-command startup for the full development stack including authorization:
#   PostgreSQL -> Keycloak -> Portal Backend -> AuthZ Repository -> OPA -> APISIX
#
# Safe to run on an already-running stack (all operations are idempotent).
#
# Usage:
#   ./start-dev.sh              # Full stack from scratch
#   ./start-dev.sh --skip-build # Same, but skip Maven builds (use existing JARs)
#
# Individual scripts can also be run separately:
#   ./start-infra.sh             # Infrastructure only (PostgreSQL, Keycloak)
#   ./start-authz.sh             # AuthZ stack only (assumes infra is up)
#   ./start-authz.sh --skip-build
#
# Prerequisites:
#   - Docker and Docker Compose v2
#   - Java 21 JDK and Maven 3.9+
#   - jq (for JSON parsing)
#   - /etc/hosts: 127.0.0.1 civitas-keycloak

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_common.sh"

# Pass --skip-build through to start-authz.sh
AUTHZ_ARGS=""
for arg in "$@"; do
  case $arg in
    --skip-build) AUTHZ_ARGS="--skip-build" ;;
  esac
done

check_prerequisites

echo ""
echo "========================================"
echo "  CIVITAS CORE — Dev Environment Start"
echo "========================================"

# Step 1: Infrastructure
"$SCRIPT_DIR/start-infra.sh"

# Step 2: AuthZ application stack
"$SCRIPT_DIR/start-authz.sh" $AUTHZ_ARGS

# Summary
echo "========================================"
echo "  Stack Status"
echo "========================================"
echo ""
printf "  %-25s %s\n" "PostgreSQL" ":5432"
printf "  %-25s %s\n" "Keycloak" ":8080"
printf "  %-25s %s\n" "Portal Backend" ":8089"
printf "  %-25s %s\n" "AuthZ Repository" ":8091"
printf "  %-25s %s\n" "OPA" ":8181"
printf "  %-25s %s\n" "APISIX" ":9080"
echo ""
echo "  Test users: authz.{admin,reader,none}@e2e.civitas.dev / $TEST_PASSWORD"
echo "  Client: portal-frontend (secret: $CLIENT_SECRET)"
echo ""
echo "  Quick test:"
echo "    ./integration-test.sh"
echo ""
echo "  Manual test:"
echo '    TOKEN=$(curl -sf -X POST "http://civitas-keycloak:8080/realms/civitas-core/protocol/openid-connect/token" \'
echo '      -d "grant_type=password&client_id=portal-frontend&client_secret=dev-only-portal-frontend-secret'
echo '      &username=authz.admin@e2e.civitas.dev&password=test123" | jq -r .access_token)'
echo '    curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/v2/datasets'
echo ""
ok "Dev environment ready!"
echo ""
