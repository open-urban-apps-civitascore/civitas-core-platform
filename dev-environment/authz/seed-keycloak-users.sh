#!/bin/bash
# M5: Create Keycloak test users for AuthZ integration testing
#
# Creates three test users in Keycloak with matching external_id values
# that correspond to the seed-authz-data.sql entries.
#
# Test Users:
#   - authz.admin@e2e.civitas.dev: Full permissions (DataArchitect)
#   - authz.reader@e2e.civitas.dev: Read-only permissions (DataConsumer)
#   - authz.none@e2e.civitas.dev: No permissions
#
# Prerequisites:
#   - Keycloak must be running at localhost:8080
#   - civitas-core realm must exist
#
# Usage:
#   ./seed-keycloak-users.sh
#
# The script outputs the Keycloak user IDs which must be updated in
# seed-authz-data.sql for the external_id values.

set -e

# Configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${KEYCLOAK_REALM:-civitas-core}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
TEST_PASSWORD="${TEST_USER_PASSWORD:-test123}"

echo "=== M5: Keycloak Test Users Setup ==="
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Realm: $REALM"
echo ""

# Get admin token
echo "Authenticating as admin..."
ADMIN_TOKEN=$(curl -sf -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=admin-cli" \
    -d "username=$ADMIN_USER" \
    -d "password=$ADMIN_PASSWORD" \
    | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" == "null" ]; then
    echo "ERROR: Failed to authenticate as admin"
    exit 1
fi
echo "Admin authentication successful"
echo ""

# Function to create or update a user
create_user() {
    local email=$1
    local first_name=$2
    local last_name=$3
    local var_name=$4

    echo "Creating user: $email"

    # Check if user exists
    EXISTING_USER=$(curl -sf -X GET "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$email" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        | jq -r '.[0].id // empty')

    if [ -n "$EXISTING_USER" ]; then
        echo "  User already exists with ID: $EXISTING_USER"
        eval "$var_name='$EXISTING_USER'"
        return
    fi

    # Create user
    HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"email\": \"$email\",
            \"username\": \"$email\",
            \"firstName\": \"$first_name\",
            \"lastName\": \"$last_name\",
            \"enabled\": true,
            \"emailVerified\": true,
            \"credentials\": [{
                \"type\": \"password\",
                \"value\": \"$TEST_PASSWORD\",
                \"temporary\": false
            }]
        }")

    if [ "$HTTP_CODE" != "201" ] && [ "$HTTP_CODE" != "409" ]; then
        echo "  ERROR: Failed to create user (HTTP $HTTP_CODE)"
        return 1
    fi

    # Get the created user's ID
    USER_ID=$(curl -sf -X GET "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$email" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        | jq -r '.[0].id')

    echo "  Created with ID: $USER_ID"
    eval "$var_name='$USER_ID'"
}

# Create test users
echo "=== Creating Test Users ==="
create_user "authz.admin@e2e.civitas.dev" "Authz" "Admin" "ADMIN_USER_ID"
create_user "authz.reader@e2e.civitas.dev" "Authz" "Reader" "READER_USER_ID"
create_user "authz.none@e2e.civitas.dev" "Authz" "NoPerms" "NONE_USER_ID"
echo ""

# Output SQL update statements
echo "=== Update seed-authz-data.sql with these external_id values ==="
echo ""
echo "Run the following SQL to update external_id values:"
echo ""
cat << EOF
UPDATE users SET external_id = '$ADMIN_USER_ID' WHERE email = 'authz.admin@e2e.civitas.dev';
UPDATE users SET external_id = '$READER_USER_ID' WHERE email = 'authz.reader@e2e.civitas.dev';
UPDATE users SET external_id = '$NONE_USER_ID' WHERE email = 'authz.none@e2e.civitas.dev';
EOF
echo ""

# Create a file with the IDs for automation
cat > /tmp/authz-keycloak-ids.env << EOF
AUTHZ_ADMIN_KC_ID=$ADMIN_USER_ID
AUTHZ_READER_KC_ID=$READER_USER_ID
AUTHZ_NONE_KC_ID=$NONE_USER_ID
EOF
echo "Keycloak IDs saved to /tmp/authz-keycloak-ids.env"
echo ""

# Verify users can authenticate
echo "=== Verifying User Authentication ==="
CLIENT_SECRET="${CLIENT_SECRET:-dev-only-portal-frontend-secret}"
for email in "authz.admin@e2e.civitas.dev" "authz.reader@e2e.civitas.dev" "authz.none@e2e.civitas.dev"; do
    TOKEN=$(curl -sf -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=portal-frontend" \
        -d "client_secret=$CLIENT_SECRET" \
        -d "username=$email" \
        -d "password=$TEST_PASSWORD" \
        | jq -r '.access_token // empty')

    if [ -n "$TOKEN" ]; then
        echo "  $email: OK"
    else
        echo "  $email: FAILED (may need client_id adjustment)"
    fi
done
echo ""

echo "=== Setup Complete ==="
echo ""
echo "Test users created with password: $TEST_PASSWORD"
echo ""
echo "Next steps:"
echo "1. Update external_id values in seed-authz-data.sql (or run the SQL above)"
echo "2. Run seed-authz-data.sql against the database"
echo "3. Start the authz stack: docker compose up -d"
echo "4. Run integration tests: ./integration-test.sh"
