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

# Java
if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: Java is not installed. Please install Java 21+."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
    echo "ERROR: Java 21 or higher is required. Found Java $JAVA_VERSION."
    exit 1
fi
echo "  Java $JAVA_VERSION found"

# Maven
if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven is not installed. Please install Maven 3.6+."
    exit 1
fi

MVN_VERSION=$(mvn -version 2>&1 | head -1 | awk '{print $3}')
MVN_MAJOR=$(echo "$MVN_VERSION" | cut -d'.' -f1)
MVN_MINOR=$(echo "$MVN_VERSION" | cut -d'.' -f2)
if [ "$MVN_MAJOR" -lt 3 ] || ([ "$MVN_MAJOR" -eq 3 ] && [ "$MVN_MINOR" -lt 6 ]); then
    echo "ERROR: Maven 3.6 or higher is required. Found Maven $MVN_VERSION."
    exit 1
fi
echo "  Maven $MVN_VERSION found"

# Node.js (for frontend)
if ! command -v node >/dev/null 2>&1; then
    echo "WARNING: Node.js is not installed. Frontend cannot be started."
    NODE_AVAILABLE=false
else
    NODE_VERSION=$(node -v | sed 's/v//' | cut -d'.' -f1)
    if [ "$NODE_VERSION" -lt 18 ] 2>/dev/null; then
        echo "WARNING: Node.js 18+ recommended. Found Node.js $NODE_VERSION."
    fi
    echo "  Node.js $(node -v) found"
    NODE_AVAILABLE=true
fi

# pnpm (for frontend)
if ! command -v pnpm >/dev/null 2>&1; then
    echo "WARNING: pnpm is not installed. Frontend cannot be started."
    PNPM_AVAILABLE=false
else
    echo "  pnpm $(pnpm -v) found"
    PNPM_AVAILABLE=true
fi

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

DEV_VERSION="1.0.0-dev"

echo "======================================================"
echo "Config Adapter Startup"
echo "======================================================"
echo
echo "How would you like to start the Config Adapter?"
echo
echo "  1) Automatic (command line: build & run)"
echo "  2) Manual / IDE (for debugging)"
echo
read -p "Select option [1/2]: " config_adapter_option

echo
echo "======================================================"
echo "Portal Backend Startup"
echo "======================================================"
echo
echo "How would you like to start the Portal Backend?"
echo
echo "  1) Automatic (command line: build & run)"
echo "  2) Manual / IDE (for debugging)"
echo
read -p "Select option [1/2]: " backend_option

echo

# ---- Build Phase ---------------------------------------------------

# Build config-adapter if command line option selected
if [ "$config_adapter_option" = "1" ] || [ "$backend_option" = "1" ]; then
    echo "Building Config Adapter (version: $DEV_VERSION)..."
    cd "$SCRIPT_DIR/../config-adapter"
    mvn clean install -DskipTests -Drevision=$DEV_VERSION
    if [ $? -ne 0 ]; then
        echo "ERROR: Config Adapter build failed"
        exit 1
    fi
    echo "  Config Adapter built successfully"
    echo
fi

# Build portal-backend if command line option selected
if [ "$backend_option" = "1" ]; then
    echo "Building Portal Backend (config-adapter version: $DEV_VERSION)..."
    cd "$SCRIPT_DIR/../portal-backend"
    mvn clean package -DskipTests -Dconfig-adapter.version=$DEV_VERSION
    if [ $? -ne 0 ]; then
        echo "ERROR: Portal Backend build failed"
        exit 1
    fi
    echo "  Portal Backend built successfully"
    echo
fi

# ---- Config Adapter Startup ----------------------------------------

if [ "$config_adapter_option" = "1" ]; then
    echo "Starting Config Adapter..."
    cd "$SCRIPT_DIR/../config-adapter"

    CONFIG_ADAPTER_JAR="$(pwd)/config-adapter-application/target/config-adapter-application-$DEV_VERSION.jar"

    # Create a startup script with environment variables
    cat > /tmp/start-config-adapter.sh << 'SCRIPT_EOF'
#!/bin/bash
# Config Adapter environment variables (from application.properties)
export HEALTHCHECK_PORT=8088
export ADAPTERS=keycloak,apisix,frost
export EVENTHANDLER_NAME=kafka
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_GROUP_ID=config-adapter-group
export KAFKA_RETRY_MAX_ATTEMPTS=3
export KAFKA_RETRY_INITIAL_BACKOFF_MS=1000
export KAFKA_DLQ_TOPIC=core.civitas.idm.dlq
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_REALM=master
export KEYCLOAK_USERNAME=admin
export KEYCLOAK_PASSWORD=admin
export KEYCLOAK_CLIENT_ID=admin-cli
export KEYCLOAK_TOPICS=core.civitas.idm.user.created,core.civitas.idm.user.updated,core.civitas.idm.user.deleted,core.civitas.idm.group.created,core.civitas.idm.group.updated,core.civitas.idm.group.deleted
export APISIX_ADMIN_URL=http://localhost:9180
export APISIX_ADMIN_KEY=edd1c9f034335f136f87ad84b625c8f1
export APISIX_TOPICS=core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted
export FROST_URL=http://localhost:9080/FROST-Server/v1.1
export FROST_API_KEY=dev-frost-api-key
export FROST_API_KEY_HEADER=X-API-Key
export FROST_TOPICS=core.civitas.data.thing.created,core.civitas.data.thing.updated,core.civitas.data.thing.deleted,core.civitas.data.location.created,core.civitas.data.location.updated,core.civitas.data.location.deleted,core.civitas.data.sensor.created,core.civitas.data.sensor.updated,core.civitas.data.sensor.deleted,core.civitas.data.observedproperty.created,core.civitas.data.observedproperty.updated,core.civitas.data.observedproperty.deleted,core.civitas.data.datastream.created,core.civitas.data.datastream.updated,core.civitas.data.datastream.deleted

java -jar "$1"
exec bash
SCRIPT_EOF
    chmod +x /tmp/start-config-adapter.sh

    gnome-terminal --title="Config Adapter" -- /tmp/start-config-adapter.sh "$CONFIG_ADAPTER_JAR" 2>/dev/null || \
    xterm -T "Config Adapter" -e /tmp/start-config-adapter.sh "$CONFIG_ADAPTER_JAR" 2>/dev/null || \
    {
        echo "Could not open new terminal. Starting in background..."
        /tmp/start-config-adapter.sh "$CONFIG_ADAPTER_JAR" &
    }

    echo "Waiting for Config Adapter to start..."
    sleep 10
    echo
else
    echo "======================================================"
    echo "Config Adapter - Manual Setup"
    echo "======================================================"
    echo
    echo "Build first (if not already done):"
    echo "  cd config-adapter"
    echo "  mvn clean install -DskipTests -Drevision=$DEV_VERSION"
    echo
    echo "Then start in your IDE:"
    echo "  Project: config-adapter/config-adapter-application"
    echo "  Main class: de.civitascore.configadapter.ConfigAdapterApplication"
    echo
    echo "Environment variables to set in IDE:"
    echo "  HEALTHCHECK_PORT=8088"
    echo "  KAFKA_BOOTSTRAP_SERVERS=localhost:9092"
    echo "  KEYCLOAK_URL=http://localhost:8080"
    echo "  KEYCLOAK_REALM=master"
    echo "  KEYCLOAK_USERNAME=admin"
    echo "  KEYCLOAK_PASSWORD=admin"
    echo "  KEYCLOAK_CLIENT_ID=admin-cli"
    echo "  APISIX_ADMIN_URL=http://localhost:9180"
    echo "  FROST_URL=http://localhost:9080/FROST-Server/v1.1"
    echo "  FROST_API_KEY=dev-frost-api-key"
    echo
fi

# ---- Portal Backend Startup ----------------------------------------

if [ "$backend_option" = "1" ]; then
    echo "Starting Portal Backend..."
    cd "$SCRIPT_DIR/../portal-backend"
    gnome-terminal --title="Portal Backend" -- bash -c "mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres -Dconfig-adapter.version=$DEV_VERSION; exec bash" 2>/dev/null || \
    xterm -T "Portal Backend" -e "mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres -Dconfig-adapter.version=$DEV_VERSION; bash" 2>/dev/null || \
    {
        echo "Could not open new terminal. Starting in background..."
        mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres -Dconfig-adapter.version=$DEV_VERSION &
    }
    echo
else
    echo "======================================================"
    echo "Portal Backend - Manual Setup"
    echo "======================================================"
    echo
    echo "Build first (if not already done):"
    echo "  cd portal-backend"
    echo "  mvn clean package -DskipTests -Dconfig-adapter.version=$DEV_VERSION"
    echo
    echo "Then start in your IDE:"
    echo "  Project: portal-backend"
    echo "  Main class: de.civitascore.portal.PortalBackendApplication"
    echo "  Profiles: local,postgres"
    echo
fi

cd "$SCRIPT_DIR"

# ---- Frontend Startup ----------------------------------------------

echo
echo "======================================================"
echo "Portal Frontend Startup"
echo "======================================================"
echo

FRONTEND_DIR="$SCRIPT_DIR/../portal-frontend"

# Ensure .env.local exists
if [ ! -f "$FRONTEND_DIR/.env.local" ]; then
    if [ -f "$FRONTEND_DIR/.env.local.template" ]; then
        echo "Creating .env.local from template..."
        cp "$FRONTEND_DIR/.env.local.template" "$FRONTEND_DIR/.env.local"
        echo "  .env.local created"
    else
        echo "WARNING: .env.local.template not found in portal-frontend/"
    fi
fi

# Check if Keycloak client secret needs to be configured
if [ -f "$FRONTEND_DIR/.env.local" ]; then
    CURRENT_SECRET=$(grep '^KEYCLOAK_CLIENT_SECRET=' "$FRONTEND_DIR/.env.local" | cut -d'=' -f2)
    if [ "$CURRENT_SECRET" = "XXXXXXXXXXXXXXXXXXX" ] || [ -z "$CURRENT_SECRET" ]; then
        echo
        echo "The Keycloak client secret is not configured in .env.local."
        echo "You can find it in Keycloak Admin (http://localhost:8080):"
        echo "  Realm: civitas-core > Clients > portal-frontend > Credentials"
        echo
        read -p "Enter Keycloak client secret (or press Enter to skip): " keycloak_secret
        if [ -n "$keycloak_secret" ]; then
            sed -i "s|^KEYCLOAK_CLIENT_SECRET=.*|KEYCLOAK_CLIENT_SECRET=$keycloak_secret|" "$FRONTEND_DIR/.env.local"
            echo "  Keycloak client secret updated in .env.local"
        else
            echo "  Skipped. Update KEYCLOAK_CLIENT_SECRET in portal-frontend/.env.local before using the frontend."
        fi
    fi
fi

echo

if [ "$NODE_AVAILABLE" = true ] && [ "$PNPM_AVAILABLE" = true ]; then
    echo "How would you like to start the Portal Frontend?"
    echo
    echo "  1) Command line (pnpm dev)"
    echo "  2) Manual (start later)"
    echo "  3) Skip (not needed)"
    echo
    read -p "Select option [1/2/3]: " frontend_option

    if [ "$frontend_option" = "1" ]; then
        echo
        echo "Starting Portal Frontend..."
        cd "$SCRIPT_DIR/../portal-frontend"

        # Install dependencies if node_modules doesn't exist
        if [ ! -d "node_modules" ]; then
            echo "Installing dependencies (pnpm install)..."
            pnpm install
        fi

        gnome-terminal --title="Portal Frontend" -- bash -c "pnpm dev; exec bash" 2>/dev/null || \
        xterm -T "Portal Frontend" -e "pnpm dev; bash" 2>/dev/null || \
        {
            echo "Could not open new terminal. Starting in background..."
            pnpm dev &
        }
        echo "  Frontend started on http://localhost:3000"
        cd "$SCRIPT_DIR"
    elif [ "$frontend_option" = "2" ]; then
        echo
        echo "To start the frontend later, run:"
        echo "  cd portal-frontend"
        echo "  pnpm install    # if not done yet"
        echo "  pnpm dev"
    else
        echo "  Frontend skipped"
    fi
else
    echo "Node.js/pnpm not available. To start the frontend manually:"
    echo "  cd portal-frontend"
    echo "  pnpm install    # if not done yet"
    echo "  pnpm dev"
fi

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
