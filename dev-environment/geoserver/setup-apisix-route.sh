#!/bin/bash
# Setup APISIX route for GeoServer OWS endpoints
#
# The GeoServer REST API is called directly by the config-adapter (not through APISIX).
# This route exposes only the OGC service endpoints (OWS) via the APISIX gateway.
#
# Prerequisites:
#   - APISIX must be running (dev-environment/apisix)
#   - GeoServer must be running (dev-environment/geoserver)
#
# Usage: ./setup-apisix-route.sh

APISIX_ADMIN_URL="http://localhost:9180/apisix/admin"
APISIX_ADMIN_KEY="edd1c9f034335f136f87ad84b625c8f1"

echo "Setting up APISIX route for GeoServer OGC services..."

curl -s -X PUT "${APISIX_ADMIN_URL}/routes/geoserver" \
  -H "X-API-KEY: ${APISIX_ADMIN_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "uri": "/geoserver/*",
    "name": "geoserver-ogc",
    "methods": ["GET", "POST"],
    "upstream": {
      "type": "roundrobin",
      "nodes": {
        "civitas-geoserver:8080": 1
      }
    }
  }' | jq .

echo ""
echo "APISIX route setup complete!"
echo ""
echo "GeoServer OGC services are now accessible via APISIX:"
echo "  OWS: http://localhost:9080/geoserver/{workspace}/ows"
echo ""
echo "GeoServer admin UI and REST API (direct access, not through APISIX):"
echo "  Admin UI:  http://localhost:8082/geoserver/web"
echo "  REST API:  http://localhost:8082/geoserver/rest"
