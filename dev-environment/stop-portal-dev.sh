#!/bin/bash
# CIVITAS CORE Platform - Portal Development Stop Script
#
# Stops all infrastructure services started by start-portal-dev.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "======================================================"
echo "CIVITAS CORE Platform - Stopping Services"
echo "======================================================"
echo

# Ask about cleanup before stopping
read -p "Also remove volumes (deletes all data)? [y/N]: " remove_volumes
echo

if [[ "$remove_volumes" =~ ^[Yy]$ ]]; then
    COMPOSE_DOWN="docker compose down -v"
    echo "Stopping services and removing volumes..."
else
    COMPOSE_DOWN="docker compose down"
    echo "Stopping services (keeping volumes)..."
fi
echo

cd "$SCRIPT_DIR/frost"
$COMPOSE_DOWN 2>/dev/null && echo "  FROST Server stopped" || true

cd "$SCRIPT_DIR/apisix"
$COMPOSE_DOWN 2>/dev/null && echo "  APISIX stopped" || true

cd "$SCRIPT_DIR/apisix"
docker compose -f docker-compose.authz.yml down $([[ "$remove_volumes" =~ ^[Yy]$ ]] && echo "-v") 2>/dev/null && echo "  AuthZ services stopped" || true

cd "$SCRIPT_DIR/keycloak"
$COMPOSE_DOWN 2>/dev/null && echo "  Keycloak stopped" || true

cd "$SCRIPT_DIR/kafka"
$COMPOSE_DOWN 2>/dev/null && echo "  Kafka stopped" || true

cd "$SCRIPT_DIR/postgres"
$COMPOSE_DOWN 2>/dev/null && echo "  PostgreSQL stopped" || true

echo
echo "All infrastructure services stopped."

# Show what was cleaned up
if [[ "$remove_volumes" =~ ^[Yy]$ ]]; then
    echo "All volumes removed (databases, Keycloak data, Kafka data, etc.)"
fi
echo

# Optionally remove the network
read -p "Remove Docker network 'civitas-network'? [y/N]: " remove_network
if [[ "$remove_network" =~ ^[Yy]$ ]]; then
    docker network rm civitas-network 2>/dev/null && \
        echo "  Network removed" || \
        echo "  Could not remove network (may still be in use)"
fi

# Optionally prune unused images
read -p "Remove unused Docker images (docker image prune)? [y/N]: " prune_images
if [[ "$prune_images" =~ ^[Yy]$ ]]; then
    docker image prune -f && \
        echo "  Unused images removed" || \
        echo "  Could not prune images"
fi

echo
echo "Note: If you started backend services via command line,"
echo "      please stop them manually (Ctrl+C in their terminals)."
echo
