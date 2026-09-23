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

# Stop application Docker containers (started by start-portal-dev.sh in Docker mode)
echo "Stopping application services (Docker)..."
cd "$SCRIPT_DIR/apps"
$COMPOSE_DOWN 2>/dev/null && echo "  Application services stopped (Docker)" || true

# Stop application processes (started by IDE mode or frontend in background)
# Kill by port — this is reliable regardless of how the process was started
# (Maven forks child JVMs that don't match pkill patterns)
echo "Stopping application processes (local)..."
docker rm -f civitas-portal-frontend 2>/dev/null || true
for port_info in "8088:Config Adapter" "8089:Portal Backend" "3000:Portal Frontend" "8092:Model Forge Admin UI"; do
    port="${port_info%%:*}"
    name="${port_info##*:}"
    if [ "$(uname -s)" = "Darwin" ]; then
        pid=$(lsof -ti :"$port" 2>/dev/null | head -1)
    else
        pid=$(fuser "$port/tcp" 2>/dev/null | awk '{print $1}')
    fi
    if [ -n "$pid" ]; then
        kill "$pid" 2>/dev/null
        sleep 1
        # Force-kill if still running
        kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
        echo "  $name stopped (port $port, PID $pid)"
    fi
done
# Also catch any stragglers by pattern (belt + suspenders)
pkill -f "spring-boot:run.*portal-backend" 2>/dev/null || true
pkill -f "config-adapter-application" 2>/dev/null || true
sleep 1

echo
echo "Stopping Docker services..."

cd "$SCRIPT_DIR/nifi"
$COMPOSE_DOWN 2>/dev/null && echo "  Apache NiFi stopped" || true

cd "$SCRIPT_DIR/geoserver"
$COMPOSE_DOWN 2>/dev/null && echo "  GeoServer stopped" || true

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
