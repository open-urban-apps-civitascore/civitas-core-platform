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

# Kill locally running development services
echo "Stopping locally running services..."

# Kills all processes listening on a given port (including child processes)
stop_service_on_port() {
    local name=$1
    local port=$2
    local pids
    pids=$(lsof -ti :"$port" 2>/dev/null)
    if [ -z "$pids" ]; then
        # Fallback: extract PIDs from ss (works when lsof can't see the process)
        pids=$(ss -tlnp "sport = :$port" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | sort -u)
    fi
    if [ -n "$pids" ]; then
        echo "$pids" | xargs kill 2>/dev/null && echo "  $name stopped" || true
    else
        echo "  $name not running"
    fi
}

stop_service_on_port "Portal Backend" 8089
stop_service_on_port "Config Adapter" 8088
stop_service_on_port "Portal Frontend" 3000

echo
