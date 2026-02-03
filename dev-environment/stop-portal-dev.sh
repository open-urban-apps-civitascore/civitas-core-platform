#!/bin/bash
# CIVITAS CORE Platform - Portal Development Stop Script
#
# Stops all infrastructure services started by start-portal-dev.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "======================================================"
echo "CIVITAS CORE Platform - Stopping Services"
echo "======================================================"
echo

cd "$SCRIPT_DIR/frost"
docker compose down 2>/dev/null && echo "  FROST Server stopped" || true

cd "$SCRIPT_DIR/apisix"
docker compose down 2>/dev/null && echo "  APISIX stopped" || true

cd "$SCRIPT_DIR/keycloak"
docker compose down 2>/dev/null && echo "  Keycloak stopped" || true

cd "$SCRIPT_DIR/kafka"
docker compose down 2>/dev/null && echo "  Kafka stopped" || true

cd "$SCRIPT_DIR/postgres"
docker compose down 2>/dev/null && echo "  PostgreSQL stopped" || true

echo
echo "All infrastructure services stopped."
echo

# Optionally remove the network
read -p "Remove Docker network 'civitas-network'? [y/N]: " remove_network
if [[ "$remove_network" =~ ^[Yy]$ ]]; then
    docker network rm civitas-network 2>/dev/null && \
        echo "  Network removed" || \
        echo "  Could not remove network (may still be in use)"
fi

echo
echo "Note: If you started backend services via command line,"
echo "      please stop them manually (Ctrl+C in their terminals)."
echo
