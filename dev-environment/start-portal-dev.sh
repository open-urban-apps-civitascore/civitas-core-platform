#!/bin/bash
# CIVITAS CORE Platform - Portal Development Start Script
#
# This script starts the infrastructure services required for portal development
# and optionally starts the config-adapter and portal-backend either via command
# line or allows manual startup in an IDE for debugging.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================================"
echo "CIVITAS CORE Platform - Portal Development Setup"
echo "======================================================"
echo

# ---- Prerequisites -------------------------------------------------

echo "Checking prerequisites..."

# Docker
if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: Docker is not installed."
    exit 1
fi
echo "  Docker found"

# Docker Compose v2
if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
    echo "  Docker Compose v2 found"
else
    echo "ERROR: Docker Compose v2 is required (docker compose)."
    exit 1
fi

echo

# ---- Create Docker Network ----------------------------------------

echo "Ensuring Docker network exists..."
docker network create civitas-network 2>/dev/null && \
    echo "  Created civitas-network" || \
    echo "  civitas-network already exists"

echo

# ---- Start Infrastructure Services --------------------------------

echo "Starting infrastructure services..."
echo "  - Kafka + Zookeeper + Kafka UI"
echo "  - PostgreSQL (Portal + Keycloak)"
echo "  - Keycloak"
echo "  - APISIX + etcd"
echo "  - FROST Server"
echo

cd "$SCRIPT_DIR/postgres"
$DOCKER_COMPOSE up -d
echo "  PostgreSQL started"

cd "$SCRIPT_DIR/kafka"
$DOCKER_COMPOSE up -d
echo "  Kafka started"

cd "$SCRIPT_DIR/keycloak"
$DOCKER_COMPOSE up -d
echo "  Keycloak started"

cd "$SCRIPT_DIR/apisix"
$DOCKER_COMPOSE up -d
echo "  APISIX started"

cd "$SCRIPT_DIR/frost"
$DOCKER_COMPOSE up -d
echo "  FROST Server started"

cd "$SCRIPT_DIR"

echo
echo "Infrastructure services started."
echo

# ---- Setup APISIX Routes ------------------------------------------

echo "Setting up APISIX routes..."

# Wait a moment for APISIX to be fully ready
sleep 5

if [ -x "$SCRIPT_DIR/frost/setup-apisix-route.sh" ]; then
    "$SCRIPT_DIR/frost/setup-apisix-route.sh" >/dev/null 2>&1 && \
        echo "  FROST route configured" || \
        echo "  WARNING: Could not configure FROST route (run manually: frost/setup-apisix-route.sh)"
fi

echo

# ---- Wait for services to be healthy ------------------------------

echo "Waiting for services to be healthy..."

wait_for_service() {
    local name=$1
    local url=$2
    local max_attempts=${3:-30}
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$url" >/dev/null 2>&1; then
            echo "  $name is ready"
            return 0
        fi
        sleep 2
        attempt=$((attempt + 1))
    done
    echo "  WARNING: $name may not be ready yet (timeout after $max_attempts attempts)"
    return 1
}

wait_for_service "Keycloak" "http://localhost:8080/realms/master" 60
wait_for_service "Kafka UI" "http://localhost:8090" 30

echo

# ---- Backend Services Selection -----------------------------------

echo "======================================================"
echo "Backend Services Startup Options"
echo "======================================================"
echo
echo "How would you like to start the backend services?"
echo
echo "  1) Command line (mvn spring-boot:run)"
echo "  2) Manual / IDE (for debugging)"
echo
read -p "Select option [1/2]: " backend_option

case $backend_option in
    1)
        echo
        echo "Starting backend services via command line..."
        echo

        # Start config-adapter
        echo "Starting Config Adapter..."
        cd "$SCRIPT_DIR/../config-adapter"
        gnome-terminal --title="Config Adapter" -- bash -c "mvn -pl config-adapter-application spring-boot:run -Dspring-boot.run.profiles=local; exec bash" 2>/dev/null || \
        xterm -T "Config Adapter" -e "mvn -pl config-adapter-application spring-boot:run -Dspring-boot.run.profiles=local; bash" 2>/dev/null || \
        {
            echo "Could not open new terminal. Starting in background..."
            mvn -pl config-adapter-application spring-boot:run -Dspring-boot.run.profiles=local &
        }

        echo "Waiting for Config Adapter to start..."
        sleep 10

        # Start portal-backend
        echo "Starting Portal Backend..."
        cd "$SCRIPT_DIR/../portal-backend"
        gnome-terminal --title="Portal Backend" -- bash -c "mvn spring-boot:run -Dspring-boot.run.profiles=local; exec bash" 2>/dev/null || \
        xterm -T "Portal Backend" -e "mvn spring-boot:run -Dspring-boot.run.profiles=local; bash" 2>/dev/null || \
        {
            echo "Could not open new terminal. Starting in background..."
            mvn spring-boot:run -Dspring-boot.run.profiles=local &
        }

        cd "$SCRIPT_DIR"
        ;;
    2)
        echo
        echo "Please start the following services manually in your IDE:"
        echo
        echo "  1. Config Adapter"
        echo "     Project: config-adapter/config-adapter-application"
        echo "     Main class: de.civitascore.configadapter.ConfigAdapterApplication"
        echo "     Profile: local"
        echo
        echo "  2. Portal Backend"
        echo "     Project: portal-backend"
        echo "     Main class: de.civitascore.portal.PortalBackendApplication"
        echo "     Profile: local"
        echo
        ;;
    *)
        echo "Invalid option. Please start backend services manually."
        ;;
esac

# ---- Frontend Instructions ----------------------------------------

echo
echo "======================================================"
echo "Frontend Setup"
echo "======================================================"
echo
echo "To start the frontend, run in a new terminal:"
echo
echo "  cd portal-frontend"
echo "  pnpm install    # if not done yet"
echo "  pnpm dev"
echo
echo "======================================================"
echo "Service URLs"
echo "======================================================"
echo
echo "  Portal Frontend:  http://localhost:3000"
echo "  Portal Backend:   http://localhost:8089"
echo "  Config Adapter:   http://localhost:8088"
echo "  Keycloak Admin:   http://localhost:8080 (admin/admin)"
echo "  Kafka UI:         http://localhost:8090"
echo "  FROST Server:     http://localhost:1883"
echo "  APISIX Gateway:   http://localhost:9080"
echo
echo "======================================================"
echo "Default Development User"
echo "======================================================"
echo
echo "  Email:    dev@civitas.local"
echo "  Password: dev123"
echo
echo "======================================================"
echo "Cleanup"
echo "======================================================"
echo
echo "To stop all services, run:"
echo "  $SCRIPT_DIR/stop-portal-dev.sh"
echo
echo "Or manually:"
echo "  cd dev-environment/postgres && docker compose down"
echo "  cd dev-environment/kafka && docker compose down"
echo "  cd dev-environment/keycloak && docker compose down"
echo "  cd dev-environment/apisix && docker compose down"
echo "  cd dev-environment/frost && docker compose down"
echo
