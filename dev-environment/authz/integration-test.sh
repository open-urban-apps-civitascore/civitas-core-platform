#!/bin/bash
# M5: Integration tests for AuthZ stack
#
# Tests the full authorization chain: APISIX -> OPA -> AuthZ Repository
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
# Prerequisites:
#   - Full authz stack running (start-portal-dev.sh --authz=full)
#   - Portal Backend running on port 8089
#   - Keycloak test users exist (pre-provisioned in realm-export.json)
#
# Usage:
#   ./integration-test.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Configuration
APISIX_URL="${APISIX_URL:-http://localhost:9080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://civitas-keycloak:8080}"
REALM="${KEYCLOAK_REALM:-civitas-core}"
CLIENT_ID="${CLIENT_ID:-portal-frontend}"
CLIENT_SECRET="${CLIENT_SECRET:-dev-only-portal-frontend-secret}"
TEST_PASSWORD="${TEST_PASSWORD:-test123}"

# Test users
ADMIN_EMAIL="authz.admin@e2e.civitas.dev"
READER_EMAIL="authz.reader@e2e.civitas.dev"
NONE_EMAIL="authz.none@e2e.civitas.dev"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
PASSED=0
FAILED=0

echo "=== M5: AuthZ Integration Tests ==="
echo "APISIX URL: $APISIX_URL"
echo "Keycloak URL: $KEYCLOAK_URL"
echo ""

# Function to get JWT token for a user
get_token() {
    local email=$1
    local token=$(curl -sf -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=$CLIENT_ID" \
        -d "client_secret=$CLIENT_SECRET" \
        -d "username=$email" \
        -d "password=$TEST_PASSWORD" \
        | jq -r '.access_token // empty')
    echo "$token"
}

# Function to make API request and check response
# Usage: test_endpoint "Test Name" "METHOD" "PATH" "TOKEN" "EXPECTED_STATUS"
test_endpoint() {
    local name=$1
    local method=$2
    local path=$3
    local token=$4
    local expected_status=$5

    echo -n "  Testing: $name ... "

    # Make request (use -s without -f to capture HTTP status codes for errors)
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
    # Handle connection failures
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

# Function to check service health
check_health() {
    local name=$1
    local url=$2
    echo -n "  Checking $name ... "
    if curl -sf "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}OK${NC}"
        return 0
    else
        echo -e "${RED}UNAVAILABLE${NC}"
        return 1
    fi
}

# =============================================================================
# SEED TEST DATA (idempotent — ON CONFLICT DO NOTHING)
# =============================================================================
echo "=== Seeding Test Data ==="
if docker exec -i civitas-postgres-portal psql -U admin -d portal_backend < "$SCRIPT_DIR/seed-authz-data.sql" > /dev/null 2>&1; then
    echo -e "  ${GREEN}OK${NC}"
else
    echo -e "  ${RED}FAILED${NC} (is PostgreSQL running?)"
    exit 1
fi
echo ""

# =============================================================================
# HEALTH CHECKS
# =============================================================================
echo "=== Health Checks ==="
check_health "Backend (direct)" "http://localhost:8089/v1/actuator/health" || true
check_health "OPA" "http://localhost:8181/health" || true
check_health "AuthZ Repository" "http://localhost:8091/actuator/health" || true
echo ""

# =============================================================================
# GET TOKENS
# =============================================================================
echo "=== Authenticating Test Users ==="

echo -n "  Getting token for admin... "
ADMIN_TOKEN=$(get_token "$ADMIN_EMAIL")
if [ -n "$ADMIN_TOKEN" ]; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${RED}FAILED${NC}"
    echo "ERROR: Could not get admin token. Check Keycloak users."
    exit 1
fi

echo -n "  Getting token for reader... "
READER_TOKEN=$(get_token "$READER_EMAIL")
if [ -n "$READER_TOKEN" ]; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${RED}FAILED${NC}"
fi

echo -n "  Getting token for no-perms user... "
NONE_TOKEN=$(get_token "$NONE_EMAIL")
if [ -n "$NONE_TOKEN" ]; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${RED}FAILED${NC}"
fi
echo ""

# =============================================================================
# AUTHORIZATION TESTS
# =============================================================================

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
# POST to create should be denied (lacks DATASET_CREATE)
test_endpoint "POST /v1/datasets (DATASET_CREATE - denied)" "POST" "/v1/datasets" "$READER_TOKEN" "403"
# DELETE should be denied (lacks USER_DELETE)
test_endpoint "DELETE /v1/users/fake-id (USER_DELETE - denied)" "DELETE" "/v1/users/fake-id" "$READER_TOKEN" "403"
echo ""

echo "=== Test 4: No-Perms User (No Permissions) ==="
test_endpoint "GET /v1/users (denied - no permissions)" "GET" "/v1/users" "$NONE_TOKEN" "403"
test_endpoint "GET /v1/datasets (denied - no permissions)" "GET" "/v1/datasets" "$NONE_TOKEN" "403"
# But /users/me should work (null-permission, any authenticated user)
test_endpoint "GET /v1/users/me (null-permission - allowed)" "GET" "/v1/users/me" "$NONE_TOKEN" "200"
echo ""

echo "=== Test 5: Unauthenticated Requests ==="
test_endpoint "GET /v1/users (no token - 401)" "GET" "/v1/users" "" "401"
test_endpoint "GET /v1/datasets (no token - 401)" "GET" "/v1/datasets" "" "401"
test_endpoint "GET /v1/users/me (no token - 401)" "GET" "/v1/users/me" "" "401"
echo ""

echo "=== Test 6: Resource Endpoints (Datasources/Datastructures) ==="
# Backend has controllers for these resources. OPA enforces permissions.
test_endpoint "GET /v1/datasources (DATASOURCE_READ - admin)" "GET" "/v1/datasources" "$ADMIN_TOKEN" "200"
test_endpoint "GET /v1/datastructures (DATASTRUCTURE_READ - admin)" "GET" "/v1/datastructures" "$ADMIN_TOKEN" "200"
test_endpoint "GET /v1/datasources (DATASOURCE_READ - reader)" "GET" "/v1/datasources" "$READER_TOKEN" "200"
test_endpoint "POST /v1/datasources (DATASOURCE_CREATE - reader denied)" "POST" "/v1/datasources" "$READER_TOKEN" "403"
echo ""

echo "=== Test 7: Removed Endpoints (Dataspaces/Catalogs) ==="
# Dataspaces and catalogs are not implemented in v2.0 (see #989).
# Removed from OPA data.json — OPA denies as unknown_endpoint (403).
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
    exit 0
else
    echo -e "${RED}Some tests failed.${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check OPA logs: docker logs civitas-opa --tail 50"
    echo "  2. Check AuthZ Repository logs: docker logs civitas-authz-repository --tail 50"
    echo "  3. Verify database has test data: psql -h localhost -U admin -d portal_backend -c \"SELECT * FROM users WHERE email LIKE 'authz.%'\""
    echo "  4. Test OPA directly:"
    echo "     curl -X POST http://localhost:8181/v1/data/civitas/authz/decision -d '{...}'"
    exit 1
fi
