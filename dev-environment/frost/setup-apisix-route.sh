#!/bin/bash
# Setup APISIX route for FROST-Server with API key authentication
#
# Prerequisites:
#   - APISIX must be running (dev-environment/apisix)
#   - FROST must be running (dev-environment/frost)
#
# Usage: ./setup-apisix-route.sh

APISIX_ADMIN_URL="http://localhost:9180/apisix/admin"
APISIX_ADMIN_KEY="edd1c9f034335f136f87ad84b625c8f1"
FROST_API_KEY="dev-frost-api-key"

echo "Setting up APISIX route for FROST-Server..."

# Create consumer with API key
echo "Creating consumer with API key..."
curl -s -X PUT "${APISIX_ADMIN_URL}/consumers/frost-client" \
  -H "X-API-KEY: ${APISIX_ADMIN_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "frost-client",
    "plugins": {
      "key-auth": {
        "key": "'"${FROST_API_KEY}"'"
      }
    }
  }' | jq .

# Create route for FROST-Server
echo "Creating route for FROST-Server..."
curl -s -X PUT "${APISIX_ADMIN_URL}/routes/frost" \
  -H "X-API-KEY: ${APISIX_ADMIN_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "uri": "/FROST-Server/*",
    "name": "frost-server",
    "methods": ["GET", "POST", "PUT", "PATCH", "DELETE"],
    "upstream": {
      "type": "roundrobin",
      "nodes": {
        "civitas-frost:8080": 1
      }
    },
    "plugins": {
      "key-auth": {
        "header": "X-API-Key"
      }
    }
  }' | jq .

echo ""
echo "APISIX route setup complete!"
echo ""
echo "FROST-Server is now accessible at: http://localhost:9080/FROST-Server/v1.1"
echo "API Key header: X-API-Key"
echo "API Key value: ${FROST_API_KEY}"
echo ""
echo "Test with:"
echo "  curl -H 'X-API-Key: ${FROST_API_KEY}' http://localhost:9080/FROST-Server/v1.1"
