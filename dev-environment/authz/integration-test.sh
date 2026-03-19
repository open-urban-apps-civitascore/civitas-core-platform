#!/bin/bash
# AuthZ Integration Tests — Self-Contained Test Harness
#
# Spins up a complete Docker stack, seeds test data, runs authorization
# tests, and tears down. No external dependencies beyond Docker.
#
# Test Scenarios:
#   1. Admin reads users -> 200 (has USER_READ)
#   2. Reader reads datasets -> 200 (has DATASET_READ)
#   3. Reader creates dataset -> 403 (lacks DATASET_CREATE)
#   4. NoPerms user reads datasets -> 403 (no permissions)
#   5. No token -> 401 (unauthenticated)
#   6. Datasources/datastructures -> 200 for admin/reader with permissions
#   7. Dataspaces/catalogs -> 403 (removed from v2.0, OPA denies as unknown_endpoint)
#
# Usage:
#   ./integration-test.sh                  # Full run (build + test + teardown)
#   ./integration-test.sh --skip-build     # Skip Maven/Docker build
#   ./integration-test.sh --no-teardown    # Keep stack running after tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.authz-e2e.yml"
PROJECT_ROOT="$SCRIPT_DIR/../.."

# Configuration
APISIX_URL="${APISIX_URL:-http://localhost:9080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${KEYCLOAK_REALM:-civitas-core}"
CLIENT_ID="${CLIENT_ID:-portal-frontend}"
CLIENT_SECRET="${CLIENT_SECRET:-dev-only-portal-frontend-secret}"
TEST_PASSWORD="${TEST_PASSWORD:-test123}"

# Test users
ADMIN_EMAIL="authz.admin@e2e.civitas.dev"
READER_EMAIL="authz.reader@e2e.civitas.dev"
NONE_EMAIL="authz.none@e2e.civitas.dev"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Counters
PASSED=0
FAILED=0

# Flags
NO_TEARDOWN=false
SKIP_BUILD=false

# =============================================================================
# PARSE ARGUMENTS
# =============================================================================
while [ $# -gt 0 ]; do
    case "$1" in
        --no-teardown) NO_TEARDOWN=true ;;
        --skip-build) SKIP_BUILD=true ;;
        -h|--help)
            echo "Usage: $(basename "$0") [OPTIONS]"
            echo
            echo "Options:"
            echo "  --skip-build     Skip Maven and Docker image builds"
            echo "  --no-teardown    Keep the Docker stack running after tests"
            echo "  -h, --help       Show this help message"
            exit 0 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
    shift
done

# =============================================================================
# HELPER FUNCTIONS
# =============================================================================

dc() {
    docker compose -f "$COMPOSE_FILE" "$@"
}

wait_for_healthy() {
    local service=$1
    local max_attempts=${2:-60}
    local interval=${3:-2}
    echo -n "  Waiting for $service..."
    for i in $(seq 1 "$max_attempts"); do
        if dc ps "$service" --format '{{.Health}}' 2>/dev/null | grep -q "healthy"; then
            echo -e " ${GREEN}ready${NC}"
            return 0
        fi
        sleep "$interval"
    done
    echo -e " ${RED}TIMEOUT${NC}"
    echo "  Logs for $service:"
    dc logs --tail 20 "$service"
    return 1
}

wait_for_url() {
    local name=$1
    local url=$2
    local max_attempts=${3:-60}
    local interval=${4:-2}
    echo -n "  Waiting for $name..."
    for i in $(seq 1 "$max_attempts"); do
        if curl -sf "$url" > /dev/null 2>&1; then
            echo -e " ${GREEN}ready${NC}"
            return 0
        fi
        sleep "$interval"
    done
    echo -e " ${RED}TIMEOUT${NC}"
    return 1
}

get_token() {
    local email=$1
    curl -sf -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=$CLIENT_ID" \
        -d "client_secret=$CLIENT_SECRET" \
        -d "username=$email" \
        -d "password=$TEST_PASSWORD" \
        | jq -r '.access_token // empty'
}

test_endpoint() {
    local name=$1
    local method=$2
    local path=$3
    local token=$4
    local expected_status=$5

    echo -n "  Testing: $name ... "

    local response
    if [ -n "$token" ]; then
        response=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$APISIX_URL$path" \
            -H "Authorization: Bearer $token" \
            -H "Content-Type: application/json" \
            2>/dev/null)
    else
        response=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$APISIX_URL$path" \
            -H "Content-Type: application/json" \
            2>/dev/null)
    fi

    if [ -z "$response" ]; then
        response="000"
    fi

    if [ "$response" = "$expected_status" ]; then
        echo -e "${GREEN}PASS${NC} (HTTP $response)"
        PASSED=$((PASSED + 1))
    else
        echo -e "${RED}FAIL${NC} (expected $expected_status, got $response)"
        FAILED=$((FAILED + 1))
    fi
}

cleanup() {
    if [ "$NO_TEARDOWN" = true ]; then
        echo ""
        echo -e "${YELLOW}Stack left running (--no-teardown). Tear down manually:${NC}"
        echo "  cd $SCRIPT_DIR && docker compose -f docker-compose.authz-e2e.yml down -v"
    else
        echo ""
        echo "=== Tearing Down ==="
        dc down -v 2>/dev/null
        echo "  Stack removed."
    fi
}

# Register cleanup on exit (runs even on error due to set -e)
trap cleanup EXIT

echo "======================================================"
echo "AuthZ Integration Tests — Self-Contained Harness"
echo "======================================================"
echo

# =============================================================================
# PHASE 1: PRE-FLIGHT CHECKS
# =============================================================================
echo "=== Phase 1: Pre-flight Checks ==="

# Check Docker
if ! docker info > /dev/null 2>&1; then
    echo -e "  ${RED}ERROR: Docker is not running${NC}"
    exit 1
fi
echo -e "  Docker: ${GREEN}OK${NC}"

# Check port conflicts
PORTS_TO_CHECK="5432 5433 8080 8088 8089 8091 8181 9080 9092 9180"
PORT_CONFLICT=false
for port in $PORTS_TO_CHECK; do
    if ss -tlnp 2>/dev/null | grep -q ":$port " || \
       lsof -i ":$port" > /dev/null 2>&1; then
        echo -e "  Port $port: ${RED}IN USE${NC}"
        PORT_CONFLICT=true
    fi
done
if [ "$PORT_CONFLICT" = true ]; then
    echo -e "  ${RED}ERROR: Port conflicts detected. Stop the dev stack first.${NC}"
    exit 1
fi
echo -e "  Ports: ${GREEN}OK${NC}"

# Ensure civitas-network exists (included compose files expect it as external)
docker network create civitas-network 2>/dev/null || true
echo -e "  Network: ${GREEN}OK${NC}"
echo ""

# =============================================================================
# PHASE 2: BUILD
# =============================================================================
if [ "$SKIP_BUILD" = false ]; then
    echo "=== Phase 2: Building JARs and Docker Images ==="

    DEV_VERSION="0.0.0-e2e"

    echo "  Building portal-model..."
    (cd "$PROJECT_ROOT/portal-model" && mvn clean install -DskipTests -Drevision="$DEV_VERSION" -q) || {
        echo -e "  ${RED}portal-model build failed${NC}"; exit 1;
    }

    echo "  Building config-adapter..."
    (cd "$PROJECT_ROOT/config-adapter" && mvn clean install -DskipTests -Drevision="$DEV_VERSION" -q) || {
        echo -e "  ${RED}config-adapter build failed${NC}"; exit 1;
    }

    echo "  Building authz-repository..."
    (cd "$PROJECT_ROOT/authz/repository" && mvn clean package -DskipTests -Dportal-model.version="$DEV_VERSION" -q) || {
        echo -e "  ${RED}authz-repository build failed${NC}"; exit 1;
    }

    echo "  Building portal-backend..."
    (cd "$PROJECT_ROOT/portal-backend" && mvn clean package -DskipTests \
        -Dconfig-adapter.version="$DEV_VERSION" \
        -Dportal-model.version="$DEV_VERSION" -q) || {
        echo -e "  ${RED}portal-backend build failed${NC}"; exit 1;
    }

    echo "  Building Docker images..."
    dc build --quiet

    echo -e "  ${GREEN}Build complete${NC}"
    echo ""
else
    echo "=== Phase 2: Build (skipped) ==="
    echo ""
fi

# =============================================================================
# PHASE 3: STARTUP (staged for ordering)
# =============================================================================
echo "=== Phase 3: Starting Services ==="

# Step 1: Databases
echo "  Starting databases..."
dc up -d postgres-portal postgres-keycloak
wait_for_healthy postgres-portal 30 1 || exit 1
wait_for_healthy postgres-keycloak 30 1 || exit 1

# Step 2: Flyway migrations
echo "  Running Flyway migrations..."
FLYWAY_MIGRATIONS="$PROJECT_ROOT/portal-backend/src/main/resources/db/migration"
if docker run --rm --network civitas-network \
    -v "$FLYWAY_MIGRATIONS:/flyway/sql:ro" \
    flyway/flyway:11-alpine \
    -url=jdbc:postgresql://postgres-portal:5432/portal_backend \
    -user=admin -password=admin \
    -locations=filesystem:/flyway/sql \
    migrate; then
    echo -e "  Migrations: ${GREEN}OK${NC}"
else
    echo -e "  Migrations: ${RED}FAILED${NC}"
    exit 1
fi

# Step 3: Seed authz test roles (before backend, so UserInitializer can find them)
echo "  Seeding authz test roles..."
if docker exec -i civitas-postgres-portal psql -U admin -d portal_backend < "$SCRIPT_DIR/seed-authz-roles.sql" > /dev/null 2>&1; then
    echo -e "  Roles: ${GREEN}OK${NC}"
else
    echo -e "  Roles: ${RED}FAILED${NC}"
    exit 1
fi

# Step 4: Start remaining infrastructure
echo "  Starting Keycloak, Kafka, Config-Adapter, APISIX, OPA, AuthZ Repo..."
dc up -d keycloak zookeeper kafka
wait_for_healthy keycloak 60 2 || exit 1
wait_for_healthy kafka 30 2 || exit 1

dc up -d config-adapter etcd
wait_for_healthy config-adapter 60 2 || exit 1
wait_for_healthy etcd 30 1 || exit 1

dc up -d apisix authz-repository
wait_for_healthy authz-repository 60 2 || exit 1

dc up -d opa
wait_for_healthy opa 30 2 || exit 1

# Step 5: Start portal-backend
echo "  Starting portal-backend..."
dc up -d portal-backend
wait_for_url "Backend health" "http://localhost:8089/v1/actuator/health" 60 2 || exit 1

# Step 6: Seed role-permission mappings
# Must run AFTER backend start because PermissionRoleInitializer creates
# the standard permissions (USER_READ, DATASET_READ, etc.) at startup.
# The seed links the authz test roles to those permissions.
echo "  Seeding role-permission mappings..."
if docker exec -i civitas-postgres-portal psql -U admin -d portal_backend < "$SCRIPT_DIR/seed-authz-role-permissions.sql" > /dev/null 2>&1; then
    echo -e "  Role-permissions: ${GREEN}OK${NC}"
else
    echo -e "  Role-permissions: ${RED}FAILED${NC}"
    exit 1
fi

# Step 7: Verify UserInitializer completed (users synced to Keycloak)
echo "  Verifying user sync to Keycloak..."
SYNC_OK=true
for email in "$ADMIN_EMAIL" "$READER_EMAIL" "$NONE_EMAIL"; do
    echo -n "    Checking $email..."
    TOKEN_OK=false
    for attempt in $(seq 1 30); do
        if [ -n "$(get_token "$email")" ]; then
            TOKEN_OK=true
            echo -e " ${GREEN}OK${NC}"
            break
        fi
        sleep 2
    done
    if [ "$TOKEN_OK" = false ]; then
        echo -e " ${RED}FAILED${NC} (user not found in Keycloak after 60s)"
        SYNC_OK=false
    fi
done
if [ "$SYNC_OK" = false ]; then
    echo -e "  ${RED}ERROR: User sync to Keycloak failed. Check backend and config-adapter logs:${NC}"
    echo "    docker logs civitas-portal-backend --tail 50"
    echo "    docker logs civitas-config-adapter --tail 50"
    exit 1
fi
echo ""

# =============================================================================
# PHASE 4: SEED APISIX ROUTES
# =============================================================================
echo "=== Phase 4: Seeding APISIX Routes ==="
if "$SCRIPT_DIR/../apisix/seed-routes.sh"; then
    echo -e "  ${GREEN}Routes seeded${NC}"
else
    echo -e "  ${RED}Route seeding failed${NC}"
    exit 1
fi
echo ""

# =============================================================================
# PHASE 5: RUN TESTS
# =============================================================================
echo "=== Phase 5: Running Authorization Tests ==="
echo ""

# Get tokens
echo "=== Authenticating Test Users ==="
echo -n "  Getting token for admin... "
ADMIN_TOKEN=$(get_token "$ADMIN_EMAIL")
if [ -n "$ADMIN_TOKEN" ]; then echo -e "${GREEN}OK${NC}"; else echo -e "${RED}FAILED${NC}"; exit 1; fi

echo -n "  Getting token for reader... "
READER_TOKEN=$(get_token "$READER_EMAIL")
if [ -n "$READER_TOKEN" ]; then echo -e "${GREEN}OK${NC}"; else echo -e "${RED}FAILED${NC}"; fi

echo -n "  Getting token for no-perms user... "
NONE_TOKEN=$(get_token "$NONE_EMAIL")
if [ -n "$NONE_TOKEN" ]; then echo -e "${GREEN}OK${NC}"; else echo -e "${RED}FAILED${NC}"; fi
echo ""

echo "=== Test 1: Admin User (Full Permissions) ==="
test_endpoint "GET /v1/users (USER_READ)" "GET" "/v1/users" "$ADMIN_TOKEN" "200"
test_endpoint "GET /v1/datasets (DATASET_READ)" "GET" "/v1/datasets" "$ADMIN_TOKEN" "200"
test_endpoint "GET /v1/users/me (null-permission)" "GET" "/v1/users/me" "$ADMIN_TOKEN" "200"
echo ""

echo "=== Test 2: Reader User (Read-Only Permissions) ==="
test_endpoint "GET /v1/users (USER_READ)" "GET" "/v1/users" "$READER_TOKEN" "200"
test_endpoint "GET /v1/datasets (DATASET_READ)" "GET" "/v1/datasets" "$READER_TOKEN" "200"
test_endpoint "GET /v1/users/me (null-permission)" "GET" "/v1/users/me" "$READER_TOKEN" "200"
echo ""

echo "=== Test 3: Reader User Denied Write Operations ==="
test_endpoint "POST /v1/datasets (DATASET_CREATE - denied)" "POST" "/v1/datasets" "$READER_TOKEN" "403"
test_endpoint "DELETE /v1/users/fake-id (USER_DELETE - denied)" "DELETE" "/v1/users/fake-id" "$READER_TOKEN" "403"
echo ""

echo "=== Test 4: No-Perms User (No Permissions) ==="
test_endpoint "GET /v1/users (denied - no permissions)" "GET" "/v1/users" "$NONE_TOKEN" "403"
test_endpoint "GET /v1/datasets (denied - no permissions)" "GET" "/v1/datasets" "$NONE_TOKEN" "403"
test_endpoint "GET /v1/users/me (null-permission - allowed)" "GET" "/v1/users/me" "$NONE_TOKEN" "200"
echo ""

echo "=== Test 5: Unauthenticated Requests ==="
test_endpoint "GET /v1/users (no token - 401)" "GET" "/v1/users" "" "401"
test_endpoint "GET /v1/datasets (no token - 401)" "GET" "/v1/datasets" "" "401"
test_endpoint "GET /v1/users/me (no token - 401)" "GET" "/v1/users/me" "" "401"
echo ""

echo "=== Test 6: Resource Endpoints (Datasources/Datastructures) ==="
test_endpoint "GET /v1/datasources (DATASOURCE_READ - admin)" "GET" "/v1/datasources" "$ADMIN_TOKEN" "200"
test_endpoint "GET /v1/datastructures (DATASTRUCTURE_READ - admin)" "GET" "/v1/datastructures" "$ADMIN_TOKEN" "200"
test_endpoint "GET /v1/datasources (DATASOURCE_READ - reader)" "GET" "/v1/datasources" "$READER_TOKEN" "200"
test_endpoint "POST /v1/datasources (DATASOURCE_CREATE - reader denied)" "POST" "/v1/datasources" "$READER_TOKEN" "403"
echo ""

echo "=== Test 7: Removed Endpoints (Dataspaces/Catalogs) ==="
test_endpoint "GET /v1/dataspaces (removed - 403)" "GET" "/v1/dataspaces" "$ADMIN_TOKEN" "403"
test_endpoint "GET /v1/catalogs (removed - 403)" "GET" "/v1/catalogs" "$ADMIN_TOKEN" "403"
echo ""

# =============================================================================
# SUMMARY
# =============================================================================
echo "=== Test Summary ==="
echo -e "Passed: ${GREEN}$PASSED${NC}"
echo -e "Failed: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
else
    echo -e "${RED}Some tests failed.${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check OPA logs: docker logs civitas-opa --tail 50"
    echo "  2. Check AuthZ Repository logs: docker logs civitas-authz-repository --tail 50"
    echo "  3. Check Backend logs: docker logs civitas-portal-backend --tail 50"
    echo "  4. Check Config-Adapter logs: docker logs civitas-config-adapter --tail 50"
fi

# Exit with failure if any tests failed (cleanup runs via trap)
if [ $FAILED -gt 0 ]; then
    exit 1
fi
