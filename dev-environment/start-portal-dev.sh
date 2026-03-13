#!/bin/bash
# CIVITAS CORE Platform - Portal Development Start Script
#
# This script starts the infrastructure services required for portal development
# and optionally starts the config-adapter and portal-backend either via command
# line or allows manual startup in an IDE for debugging.
#
# Structure:
#   Phase 1: Prerequisites check
#   Phase 2: Interactive questions (collected up front, before any Docker output)
#   Phase 3: Infrastructure startup (Docker services)
#   Phase 4: Application build & start
#   Phase 5: Smoke test
#   Phase 6: Service URLs & info

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OS_TYPE=$(uname -s)

# ---- CLI Arguments ---------------------------------------------------

authz_arg=""
config_adapter_arg=""
backend_arg=""
frontend_arg=""
keycloak_secret_arg="dev-only-portal-frontend-secret"

usage() {
    echo "Usage: $(basename "$0") [OPTIONS]"
    echo
    echo "Options:"
    echo "  --authz=full|allowall        AuthZ mode (default: prompt, default answer: allowall)"
    echo "  --config-adapter=auto|ide    Config Adapter startup (default: prompt)"
    echo "  --backend=auto|ide           Portal Backend startup (default: prompt)"
    echo "  --frontend=auto|manual|skip  Portal Frontend startup (default: prompt)"
    echo "  --keycloak-secret=SECRET     Keycloak client secret for portal-frontend"
    echo "  -h, --help                   Show this help message"
    echo
    echo "Examples:"
    echo "  $0 --authz=allowall --config-adapter=auto --backend=auto --frontend=skip"
    echo "  $0 --config-adapter=ide --backend=auto --frontend=skip"
    echo "  $0 --backend=auto --keycloak-secret=abc123"
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        --authz=*)
            val="${1#*=}"
            case "$val" in
                full)     authz_arg="1" ;;
                allowall) authz_arg="2" ;;
                *) echo "ERROR: --authz must be 'full' or 'allowall'"; exit 1 ;;
            esac ;;
        --config-adapter=*)
            val="${1#*=}"
            case "$val" in
                auto) config_adapter_arg="1" ;;
                ide)  config_adapter_arg="2" ;;
                *) echo "ERROR: --config-adapter must be 'auto' or 'ide'"; exit 1 ;;
            esac ;;
        --backend=*)
            val="${1#*=}"
            case "$val" in
                auto) backend_arg="1" ;;
                ide)  backend_arg="2" ;;
                *) echo "ERROR: --backend must be 'auto' or 'ide'"; exit 1 ;;
            esac ;;
        --frontend=*)
            val="${1#*=}"
            case "$val" in
                auto)   frontend_arg="1" ;;
                manual) frontend_arg="2" ;;
                skip)   frontend_arg="3" ;;
                *) echo "ERROR: --frontend must be 'auto', 'manual', or 'skip'"; exit 1 ;;
            esac ;;
        --keycloak-secret=*)
            keycloak_secret_arg="${1#*=}" ;;
        -h|--help) usage ;;
        *)
            echo "ERROR: Unknown option: $1"
            echo "Run with --help for usage."
            exit 1 ;;
    esac
    shift
done

echo "======================================================"
echo "CIVITAS CORE Platform - Portal Development Setup"
echo "======================================================"
echo

# ---- Phase 1: Prerequisites -----------------------------------------

echo "Checking prerequisites..."

# Java
if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: Java is not installed. Please install Java 21+."
    exit 1
fi

# Auto-detect JDK if JAVA_HOME not set or invalid.
# Supports Temurin, OpenJDK, Oracle, GraalVM, SDKMAN-installed JDKs (21+).
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    JAVA_HOME=""
    if [ "$OS_TYPE" = "Darwin" ]; then
        # macOS: java_home returns the highest installed JDK
        if [ -x /usr/libexec/java_home ]; then
            JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
        fi
    else
        # Linux / WSL: search common JDK locations, pick newest >= 21
        best_ver=0
        for jdk_dir in /usr/lib/jvm/temurin-*-jdk-* \
                        /usr/lib/jvm/java-*-openjdk-* \
                        /usr/lib/jvm/jdk-* \
                        /usr/lib/jvm/graalvm-* \
                        "$HOME/.sdkman/candidates/java"/*/; do
            if [ -x "$jdk_dir/bin/java" ]; then
                ver=$("$jdk_dir/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
                if [ "$ver" -ge 21 ] 2>/dev/null && [ "$ver" -gt "$best_ver" ]; then
                    best_ver=$ver
                    JAVA_HOME="$jdk_dir"
                fi
            fi
        done
    fi
    if [ -n "$JAVA_HOME" ]; then
        export JAVA_HOME
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
else
    # JAVA_HOME was already set — ensure PATH is consistent
    export PATH="$JAVA_HOME/bin:$PATH"
fi
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
    echo "ERROR: Java 21 or higher is required. Found Java $JAVA_VERSION."
    echo "       Install any JDK >= 21 (Temurin, OpenJDK, Oracle, GraalVM) or set JAVA_HOME."
    exit 1
fi
echo "  Java $JAVA_VERSION found (JAVA_HOME=${JAVA_HOME:-system default})"

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

# ---- Terminal Helper -----------------------------------------------
# Opens a command in a new terminal window, with platform-specific support.
# Falls back to background execution with log redirection.

start_in_new_terminal() {
    local title="$1"
    local script="$2"
    local logfile="$3"  # optional: log file for background fallback
    if [ "$OS_TYPE" = "Darwin" ]; then
        osascript -e "tell application \"Terminal\" to do script \"bash -l '$script'\"" 2>/dev/null && return 0
    else
        if command -v ptyxis >/dev/null 2>&1; then
            ptyxis -- bash -l "$script" >/dev/null 2>&1 & disown
            return 0
        fi
        gnome-terminal --title="$title" -- bash -l "$script" 2>/dev/null && return 0
        xterm -T "$title" -e "bash -l '$script'" 2>/dev/null && return 0
    fi
    # Fallback: run in background with log redirection
    if [ -n "$logfile" ]; then
        echo "  No terminal emulator available. Starting in background (log: $logfile)..."
        bash -l "$script" > "$logfile" 2>&1 &
    else
        echo "  No terminal emulator available. Starting in background..."
        bash -l "$script" &
    fi
}

# ---- Phase 2: Interactive Questions (all up front) -------------------

echo "======================================================"
echo "Configuration"
echo "======================================================"
echo

# Q1: AuthZ mode
if [ -n "$authz_arg" ]; then
    authz_option="$authz_arg"
    echo "Authorization: $([ "$authz_option" = "1" ] && echo 'full' || echo 'allow-all') (--authz)"
else
    echo "How should authorization work?"
    echo
    echo "  1) Full AuthZ (enforce permissions per endpoint)"
    echo "  2) Allow-all (any logged-in user can do anything)  [default]"
    echo
    read -p "Select option [1/2] (default: 2): " authz_option
    authz_option="${authz_option:-2}"
fi

echo

# Q2: Config Adapter mode
if [ -n "$config_adapter_arg" ]; then
    config_adapter_option="$config_adapter_arg"
    echo "Config Adapter: $([ "$config_adapter_option" = "1" ] && echo 'auto' || echo 'ide') (--config-adapter)"
else
    echo "How would you like to start the Config Adapter?"
    echo
    echo "  1) Automatic (command line: build & run)"
    echo "  2) Manual / IDE (for debugging)"
    echo
    read -p "Select option [1/2]: " config_adapter_option
fi

echo

# Q3: Backend mode
if [ -n "$backend_arg" ]; then
    backend_option="$backend_arg"
    echo "Portal Backend: $([ "$backend_option" = "1" ] && echo 'auto' || echo 'ide') (--backend)"
else
    echo "How would you like to start the Portal Backend?"
    echo
    echo "  1) Automatic (command line: build & run)"
    echo "  2) Manual / IDE (for debugging)"
    echo
    read -p "Select option [1/2]: " backend_option
fi

echo

# Q4: Frontend — .env.local setup + Keycloak secret + mode
FRONTEND_DIR="$SCRIPT_DIR/../portal-frontend"
keycloak_secret=""

if [ ! -f "$FRONTEND_DIR/.env.local" ]; then
    if [ -f "$FRONTEND_DIR/.env.local.template" ]; then
        echo "Creating .env.local from template..."
        cp "$FRONTEND_DIR/.env.local.template" "$FRONTEND_DIR/.env.local"
        echo "  .env.local created"
    else
        echo "WARNING: .env.local.template not found in portal-frontend/"
    fi
fi

if [ -f "$FRONTEND_DIR/.env.local" ]; then
    CURRENT_SECRET=$(grep '^KEYCLOAK_CLIENT_SECRET=' "$FRONTEND_DIR/.env.local" | cut -d'=' -f2)
    if [ "$CURRENT_SECRET" = "dev-only-portal-frontend-secret" ] || [ -z "$CURRENT_SECRET" ]; then
        if [ -n "$keycloak_secret_arg" ]; then
            keycloak_secret="$keycloak_secret_arg"
        else
            echo
            echo "The Keycloak client secret is not configured in .env.local."
            echo "You can find it in Keycloak Admin (http://localhost:8080):"
            echo "  Realm: civitas-core > Clients > portal-frontend > Credentials"
            echo
            read -p "Enter Keycloak client secret (or press Enter to skip): " keycloak_secret
        fi
        if [ -n "$keycloak_secret" ]; then
            perl -i -pe "s|^KEYCLOAK_CLIENT_SECRET=.*|KEYCLOAK_CLIENT_SECRET=$keycloak_secret|" "$FRONTEND_DIR/.env.local"
            echo "  Keycloak client secret updated in .env.local"
        else
            echo "  Skipped. Update KEYCLOAK_CLIENT_SECRET in portal-frontend/.env.local before using the frontend."
        fi
    fi
fi

echo

frontend_option=""
if [ "$NODE_AVAILABLE" = true ] && [ "$PNPM_AVAILABLE" = true ]; then
    if [ -n "$frontend_arg" ]; then
        frontend_option="$frontend_arg"
        label=$([ "$frontend_option" = "1" ] && echo 'auto' || { [ "$frontend_option" = "2" ] && echo 'manual' || echo 'skip'; })
        echo "Portal Frontend: $label (--frontend)"
    else
        echo "How would you like to start the Portal Frontend?"
        echo
        echo "  1) Command line (pnpm dev)"
        echo "  2) Manual (start later)"
        echo "  3) Skip (not needed)"
        echo
        read -p "Select option [1/2/3]: " frontend_option
    fi
else
    frontend_option="skip_unavailable"
fi

# Print configuration summary
echo
echo "------------------------------------------------------"
echo "Configuration Summary"
echo "------------------------------------------------------"
echo "  AuthZ mode:      $([ "$authz_option" = "1" ] && echo 'Full AuthZ' || echo 'Allow-all')"
echo "  Config Adapter:  $([ "$config_adapter_option" = "1" ] && echo 'Auto' || echo 'Manual/IDE')"
echo "  Portal Backend:  $([ "$backend_option" = "1" ] && echo 'Auto' || echo 'Manual/IDE')"
if [ "$frontend_option" = "skip_unavailable" ]; then
    echo "  Portal Frontend: N/A (Node.js/pnpm not available)"
elif [ "$frontend_option" = "1" ]; then
    echo "  Portal Frontend: Auto"
elif [ "$frontend_option" = "2" ]; then
    echo "  Portal Frontend: Manual"
else
    echo "  Portal Frontend: Skip"
fi
echo "------------------------------------------------------"
echo

DEV_VERSION="1.0.0-dev"

# ---- Phase 3: Infrastructure Startup --------------------------------

echo "Ensuring Docker network exists..."
docker network create civitas-network 2>/dev/null && \
    echo "  Created civitas-network" || \
    echo "  civitas-network already exists"

echo
echo "Starting infrastructure services..."
echo "  - Kafka + Zookeeper + Kafka UI"
echo "  - PostgreSQL (Portal + Keycloak) + Flyway migrations"
echo "  - Keycloak"
echo "  - APISIX + etcd"
echo "  - OPA + AuthZ Repository"
echo "  - FROST Server"
echo "  - Redpanda Connect"
echo "  - Model Atlas + Apicurio Registry"
echo

cd "$SCRIPT_DIR/postgres"
$DOCKER_COMPOSE up -d
echo "  PostgreSQL started"

# Run Flyway migrations to ensure database schema exists.
# AuthZ Repository shares the portal_backend database but doesn't own the schema —
# portal-backend's Flyway migrations create the tables. Running them here ensures
# AuthZ services can query the database on a cold start (before the backend runs).
# Flyway is idempotent — already-applied migrations are skipped automatically.
PG_READY=false
for i in $(seq 1 30); do
    if docker exec civitas-postgres-portal pg_isready -U admin -d portal_backend -q 2>/dev/null; then
        PG_READY=true
        break
    fi
    sleep 1
done
if [ "$PG_READY" = false ]; then
    echo "  ERROR: PostgreSQL not ready after 30s"
    exit 1
fi

echo "  Running database migrations..."
FLYWAY_MIGRATIONS="$SCRIPT_DIR/../portal-backend/src/main/resources/db/migration"
if docker run --rm --network civitas-network \
    -v "$FLYWAY_MIGRATIONS:/flyway/sql:ro" \
    flyway/flyway:11-alpine \
    -url=jdbc:postgresql://postgres-portal:5432/portal_backend \
    -user=admin -password=admin \
    -locations=filesystem:/flyway/sql \
    migrate; then
    echo "  Database migrations complete"
else
    echo "  WARNING: Database migrations failed (AuthZ services may not work until backend starts)"
fi

cd "$SCRIPT_DIR/kafka"
$DOCKER_COMPOSE up -d
echo "  Kafka started"

cd "$SCRIPT_DIR/keycloak"
$DOCKER_COMPOSE up -d
echo "  Keycloak started"

# Apply AuthZ mode configuration
if [ "$authz_option" = "2" ]; then
    echo "  Configuring ALLOW-ALL mode (wildcard scope, null-permission data)"
    bash "$SCRIPT_DIR/../authz/rego/generate-allowall.sh"
    export OPA_DATA_DIR="../../authz/rego/data/backends-allowall"
    SEED_ROUTES_ALLOWALL="--allowall"
else
    SEED_ROUTES_ALLOWALL=""
    echo "  Configuring FULL AUTHZ mode (enforce permissions)"
fi

# Build AuthZ Repository JAR (required by its Dockerfile)
echo "Building AuthZ Repository..."
cd "$SCRIPT_DIR/../portal-model"
if ! mvn clean install -DskipTests -Drevision=$DEV_VERSION -q; then
    echo "ERROR: Portal Model build failed"
    exit 1
fi
cd "$SCRIPT_DIR/../authz/repository"
if ! mvn clean package -DskipTests -Dportal-model.version=$DEV_VERSION -q; then
    echo "ERROR: AuthZ Repository build failed"
    exit 1
fi
echo "  AuthZ Repository built successfully"

# Start AuthZ services (OPA + AuthZ Repository)
cd "$SCRIPT_DIR/apisix"
$DOCKER_COMPOSE -f docker-compose.authz.yml up -d --build
echo "  AuthZ services started (OPA + AuthZ Repository)"

# Start APISIX gateway
cd "$SCRIPT_DIR/apisix"
$DOCKER_COMPOSE up -d
echo "  APISIX started"

cd "$SCRIPT_DIR/frost"
if [ ! -f .env ] && [ -f .env.example ]; then
    cp .env.example .env
    echo "  Created frost/.env from .env.example (set FROST_DB_PASSWORD to change the password)"
fi
if $DOCKER_COMPOSE up -d 2>&1; then
    echo "  FROST Server started"
else
    echo "  WARNING: FROST Server failed to start (may not support this architecture)"
    echo "           Portal development works fine without it."
fi

cd "$SCRIPT_DIR/redpanda-connect"
if $DOCKER_COMPOSE up -d 2>&1; then
    echo "  Redpanda Connect started"
else
    echo "  WARNING: Redpanda Connect failed to start"
    echo "           Dataset saga pipeline deployment will not work."
fi

cd "$SCRIPT_DIR/modelatlas"
$DOCKER_COMPOSE up -d
echo "  Model Atlas + Apicurio Registry started"

cd "$SCRIPT_DIR"

echo
echo "Infrastructure services started."
echo

# ---- Setup APISIX Routes ------------------------------------------

echo "Seeding APISIX routes..."

if [ -x "$SCRIPT_DIR/apisix/seed-routes.sh" ]; then
    if "$SCRIPT_DIR/apisix/seed-routes.sh" $SEED_ROUTES_ALLOWALL; then
        echo "  APISIX routes seeded successfully"
    else
        echo "  WARNING: Could not seed APISIX routes (run manually: apisix/seed-routes.sh $SEED_ROUTES_ALLOWALL)"
    fi
else
    echo "  WARNING: apisix/seed-routes.sh not found"
fi

echo

# ---- Wait for infrastructure to be healthy -------------------------

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
    echo "  ERROR: $name did not become ready (timeout after $max_attempts attempts)"
    exit 1
}

wait_for_service "Keycloak" "http://localhost:8080/realms/master" 60
wait_for_service "Kafka UI" "http://localhost:8090" 30
wait_for_service "OPA" "http://localhost:8181/health" 30
wait_for_service "AuthZ Repository" "http://localhost:8091/actuator/health" 60
# Model Atlas has no health endpoint — check that it responds (any HTTP status)
MA_READY=false
for i in $(seq 1 30); do
    if curl -s -o /dev/null "http://localhost:8086/" 2>/dev/null; then
        echo "  Model Atlas is ready"
        MA_READY=true
        break
    fi
    sleep 2
done
if [ "$MA_READY" = false ]; then
    echo "  WARNING: Model Atlas may not be ready yet (timeout)"
fi

# macOS: disable Keycloak https requirement on master realm on macos
if [ "$OS_TYPE" = "Darwin" ]; then
    docker exec -it civitas-keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password admin
    docker exec civitas-keycloak /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=NONE
fi

echo

# ---- Phase 4: Application Build & Start -----------------------------

# Kill any leftover processes from a previous run to avoid port conflicts.
# Without this, the health check may hit an old backend and falsely report success.
echo "Checking for leftover application processes..."
for port in 8088 8089 3000; do
    if [ "$OS_TYPE" = "Darwin" ]; then
        pid=$(lsof -ti :"$port" 2>/dev/null | head -1)
    else
        pid=$(fuser "$port/tcp" 2>/dev/null | awk '{print $1}')
    fi
    if [ -n "$pid" ]; then
        echo "  Killing leftover process on port $port (PID $pid)"
        kill "$pid" 2>/dev/null
        sleep 1
        # Force-kill if still alive
        kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
    fi
done
echo

# ---- Build Phase ---------------------------------------------------

# Build config-adapter if command line option selected
if [ "$config_adapter_option" = "1" ] || [ "$backend_option" = "1" ]; then
    echo "Building Config Adapter (version: $DEV_VERSION)..."
    cd "$SCRIPT_DIR/../config-adapter"
    if ! mvn clean install -DskipTests -Drevision=$DEV_VERSION; then
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
    if ! mvn clean package -DskipTests -Dconfig-adapter.version=$DEV_VERSION -Dportal-model.version=$DEV_VERSION; then
        echo "ERROR: Portal Backend build failed"
        exit 1
    fi
    echo "  Portal Backend built successfully"
    echo
fi

# ---- Start Config Adapter -------------------------------------------

if [ "$config_adapter_option" = "1" ]; then
    echo "Starting Config Adapter..."
    cd "$SCRIPT_DIR/../config-adapter"

    CONFIG_ADAPTER_JAR="$(pwd)/config-adapter-application/target/config-adapter-application-$DEV_VERSION.jar"

    # Create a startup script with environment variables
    cat > /tmp/start-config-adapter.sh << SCRIPT_EOF
#!/bin/bash
# Config Adapter environment variables (from application.properties)
export HEALTHCHECK_PORT=8088
export ADAPTERS=keycloak,apisix,frost,redpanda
export EVENTHANDLER_NAME=kafka
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_GROUP_ID=config-adapter-group
export KAFKA_RETRY_MAX_ATTEMPTS=3
export KAFKA_RETRY_INITIAL_BACKOFF_MS=1000
export KAFKA_DLQ_TOPIC=de.civitascore.idm.dlq
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_REALM=master
export KEYCLOAK_USERNAME=admin
export KEYCLOAK_PASSWORD=admin
export KEYCLOAK_CLIENT_ID=admin-cli
export KEYCLOAK_TOPICS=de.civitascore.idm.user.created,de.civitascore.idm.user.updated,de.civitascore.idm.user.deleted,de.civitascore.idm.group.created,de.civitascore.idm.group.updated,de.civitascore.idm.group.deleted
export APISIX_ADMIN_URL=http://localhost:9180
export APISIX_ADMIN_KEY=edd1c9f034335f136f87ad84b625c8f1
export APISIX_GATEWAY_URL=http://localhost:9080
export APISIX_PLUGIN_CONFIG_ID=1
export APISIX_SERVICE_ID=svc-frost-server
export APISIX_TOPICS=de.civitascore.api.backend.created,de.civitascore.api.backend.updated,de.civitascore.api.backend.deleted
export FROST_URL=http://localhost:8085/FROST-Server/v1.1
export FROST_PUBLIC_URL=http://civitas-frost:8080/FROST-Server/v1.1
export FROST_API_KEY=dev-frost-api-key
export FROST_API_KEY_HEADER=X-API-Key
export FROST_TOPICS=de.civitascore.data.thing.created,de.civitascore.data.thing.updated,de.civitascore.data.thing.deleted,de.civitascore.data.location.created,de.civitascore.data.location.updated,de.civitascore.data.location.deleted,de.civitascore.data.sensor.created,de.civitascore.data.sensor.updated,de.civitascore.data.sensor.deleted,de.civitascore.data.observedproperty.created,de.civitascore.data.observedproperty.updated,de.civitascore.data.observedproperty.deleted,de.civitascore.data.datastream.created,de.civitascore.data.datastream.updated,de.civitascore.data.datastream.deleted
export REDPANDA_URL=http://localhost:4195
export REDPANDA_TOPICS=de.civitascore.data.pipeline.created,de.civitascore.data.pipeline.updated,de.civitascore.data.pipeline.deleted

java -jar "$CONFIG_ADAPTER_JAR"
exec bash
SCRIPT_EOF
    chmod +x /tmp/start-config-adapter.sh

    start_in_new_terminal "Config Adapter" "/tmp/start-config-adapter.sh" "/tmp/config-adapter.log"

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
    echo "  APISIX_ADMIN_KEY=edd1c9f034335f136f87ad84b625c8f1"
    echo "  APISIX_GATEWAY_URL=http://localhost:9080"
    echo "  FROST_URL=http://localhost:8085/FROST-Server/v1.1"
    echo "  FROST_PUBLIC_URL=http://civitas-frost:8080/FROST-Server/v1.1"
    echo "  FROST_API_KEY=dev-frost-api-key"
    echo "  REDPANDA_URL=http://localhost:4195"
    echo
fi

# ---- Start Portal Backend -------------------------------------------

if [ "$backend_option" = "1" ]; then
    echo "Starting Portal Backend..."
    cd "$SCRIPT_DIR/../portal-backend"

    BACKEND_DIR="$(pwd)"
    cat > /tmp/start-portal-backend.sh << SCRIPT_EOF
#!/bin/bash
cd "$BACKEND_DIR"
export SPRING_DATASOURCE_USERNAME=admin
export SPRING_DATASOURCE_PASSWORD=admin
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/portal_backend?sslmode=disable&gssEncMode=disable"
export MODEL_ATLAS_BASE_URL=http://localhost:8086
mvn clean spring-boot:run -Dspring-boot.run.profiles=local,local-init,postgres -Dconfig-adapter.version=$DEV_VERSION -Dportal-model.version=$DEV_VERSION
exec bash
SCRIPT_EOF
    chmod +x /tmp/start-portal-backend.sh

    start_in_new_terminal "Portal Backend" "/tmp/start-portal-backend.sh" "/tmp/portal-backend.log"
    echo
else
    echo "======================================================"
    echo "Portal Backend - Manual Setup"
    echo "======================================================"
    echo
    echo "Build first (if not already done):"
    echo "  cd portal-model && mvn clean install -DskipTests && cd .."
    echo "  cd portal-backend"
    echo "  mvn clean package -DskipTests -Dconfig-adapter.version=$DEV_VERSION"
    echo
    echo "Then start in your IDE:"
    echo "  Project: portal-backend"
    echo "  Main class: de.civitascore.portal.PortalBackendApplication"
    echo "  Profiles: local,local-init,postgres"
    echo
    echo "Environment variables to set in IDE:"
    echo "  SPRING_DATASOURCE_USERNAME=admin"
    echo "  SPRING_DATASOURCE_PASSWORD=admin"
    echo "  SPRING_DATASOURCE_URL=\"jdbc:postgresql://localhost:5432/portal_backend?sslmode=disable&gssEncMode=disable\""
    echo "  MODEL_ATLAS_BASE_URL=http://localhost:8086"
    echo
fi

cd "$SCRIPT_DIR"

# ---- Wait for Backend Health (both authz modes) ---------------------
# LocalUserInitializer must complete before OPA's is_authenticated check
# can find the dev user. Without this wait, requests get 403 in allow-all mode.

if [ "$backend_option" = "1" ]; then
    echo "Waiting for Portal Backend to be healthy..."
    BACKEND_READY=false
    for i in $(seq 1 60); do
        if curl -s -f "http://localhost:8089/v1/actuator/health" >/dev/null 2>&1; then
            echo "  Portal Backend is ready"
            BACKEND_READY=true
            break
        fi
        sleep 2
    done
    if [ "$BACKEND_READY" = false ]; then
        echo "  WARNING: Portal Backend may not be ready yet (timeout after 120s)"
        echo "           Check /tmp/portal-backend.log if running in background"
    fi
    echo
fi

# ---- Seed Dev Admin Data -------------------------------------------
# Must run AFTER backend starts because PermissionRoleInitializer creates
# the permissions table rows. The seed links DevAdmin role to those permissions.

if [ "$authz_option" = "1" ]; then
    echo "Seeding dev admin data (full authz mode)..."

    SEED_SQL="$SCRIPT_DIR/apisix/seed-dev-admin.sql"
    if [ -f "$SEED_SQL" ]; then
        docker exec -i civitas-postgres-portal psql -U admin -d portal_backend -f /dev/stdin < "$SEED_SQL" 2>&1 | tail -5
        echo "  Dev admin seeding complete"
    else
        echo "  WARNING: seed-dev-admin.sql not found"
    fi
    echo
fi
# Check if Keycloak client secret needs to be configured
if [ -f "$FRONTEND_DIR/.env.local" ]; then
    CURRENT_SECRET=$(grep '^KEYCLOAK_CLIENT_SECRET=' "$FRONTEND_DIR/.env.local" | cut -d'=' -f2)
    if [ "$CURRENT_SECRET" = "XXXXXXXXXXXXXXXXXXX" ] || [ -z "$CURRENT_SECRET" ]; then
        if [ -n "$keycloak_secret_arg" ]; then
            keycloak_secret="$keycloak_secret_arg"
        else
            echo
            echo "The Keycloak client secret is not configured in .env.local."
            echo "You can find it in Keycloak Admin (http://localhost:8080):"
            echo "  Realm: civitas-core > Clients > portal-frontend > Credentials"
            echo
            read -p "Enter Keycloak client secret (or press Enter to skip): " keycloak_secret
        fi
        if [ -n "$keycloak_secret" ]; then
            perl -i -pe "s|^KEYCLOAK_CLIENT_SECRET=.*|KEYCLOAK_CLIENT_SECRET=$keycloak_secret|" "$FRONTEND_DIR/.env.local"
            echo "  Keycloak client secret updated in .env.local"
        else
            echo "  Skipped. Update KEYCLOAK_CLIENT_SECRET in portal-frontend/.env.local before using the frontend."
        fi
    fi
    echo
fi

# ---- Start Frontend --------------------------------------------------

if [ "$frontend_option" = "1" ]; then
    echo "Starting Portal Frontend..."
    cd "$SCRIPT_DIR/../portal-frontend"

    # Install dependencies if node_modules doesn't exist
    if [ ! -d "node_modules" ]; then
        echo "Installing dependencies (pnpm install)..."
        pnpm install
    fi

    FRONTEND_START_DIR="$(pwd)"
    cat > /tmp/start-portal-frontend.sh << SCRIPT_EOF
#!/bin/bash
cd "$FRONTEND_START_DIR"
pnpm dev
exec bash
SCRIPT_EOF
    chmod +x /tmp/start-portal-frontend.sh

    start_in_new_terminal "Portal Frontend" "/tmp/start-portal-frontend.sh" "/tmp/portal-frontend.log"
    echo "  Frontend started on http://localhost:3000"
    cd "$SCRIPT_DIR"
elif [ "$frontend_option" = "2" ]; then
    echo "To start the frontend later, run:"
    echo "  cd portal-frontend"
    echo "  pnpm install    # if not done yet"
    echo "  pnpm dev"
elif [ "$frontend_option" = "skip_unavailable" ]; then
    echo "Node.js/pnpm not available. To start the frontend manually:"
    echo "  cd portal-frontend"
    echo "  pnpm install    # if not done yet"
    echo "  pnpm dev"
else
    echo "  Frontend skipped"
fi

echo

# ---- Phase 5: Smoke Test --------------------------------------------
# Quick verification that the backend responds correctly.
# Only runs when backend was auto-started. Non-fatal (warnings only).

if [ "$backend_option" = "1" ] && [ "$BACKEND_READY" = true ]; then
    echo "======================================================"
    echo "Smoke Test"
    echo "======================================================"
    echo

    SMOKE_PASS=0
    SMOKE_FAIL=0
    SMOKE_SKIP=0

    smoke_test() {
        local description=$1
        local expected=$2
        local actual=$3

        if [ "$actual" = "$expected" ]; then
            echo "  PASS  $description (HTTP $actual)"
            SMOKE_PASS=$((SMOKE_PASS + 1))
        else
            echo "  FAIL  $description (expected $expected, got $actual)"
            SMOKE_FAIL=$((SMOKE_FAIL + 1))
        fi
    }

    # Get a token from Keycloak using resource owner password grant.
    # MUST use civitas-keycloak:8080 (not localhost:8080) so the JWT issuer claim
    # matches what APISIX expects from its OIDC discovery URL.
    # Requires /etc/hosts: 127.0.0.1 civitas-keycloak
    TOKEN_RESPONSE=$(curl -s -X POST "http://civitas-keycloak:8080/realms/civitas-core/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=portal-frontend" \
        -d "client_secret=dev-only-portal-frontend-secret" \
        -d "username=dev@civitas.local" \
        -d "password=dev123" 2>/dev/null) || true

    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4) || true

    if [ -z "$ACCESS_TOKEN" ]; then
        echo "  SKIP  Could not obtain token from Keycloak (is civitas-core realm configured?)"
        echo "        Token endpoint response: $(echo "$TOKEN_RESPONSE" | head -c 200)"
        SMOKE_SKIP=3
    else
        # Test 1: Unauthenticated request should be rejected
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:9080/v1/users/me" 2>/dev/null) || true
        smoke_test "Unauthenticated /v1/users/me -> 401" "401" "$STATUS"

        # Test 2: Authenticated /users/me should succeed
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            "http://localhost:9080/v1/users/me" 2>/dev/null) || true
        smoke_test "Authenticated /v1/users/me -> 200" "200" "$STATUS"

        # Test 3: Authenticated /users list should succeed
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            "http://localhost:9080/v1/users" 2>/dev/null) || true
        smoke_test "Authenticated /v1/users -> 200" "200" "$STATUS"
    fi

    echo
    echo "  Results: $SMOKE_PASS passed, $SMOKE_FAIL failed, $SMOKE_SKIP skipped"
    if [ "$SMOKE_FAIL" -gt 0 ]; then
        echo "  WARNING: Some smoke tests failed. The backend may need more time to initialize."
        echo "           Check /tmp/portal-backend.log or OPA logs for details."
    fi
    echo
elif [ "$backend_option" != "1" ]; then
    echo "Smoke test skipped (backend started manually)."
    echo
fi

# ---- Phase 6: Service URLs & Info -----------------------------------

echo "======================================================"
echo "Service URLs"
echo "======================================================"
echo
echo "  Portal Frontend:  http://localhost:3000"
echo "  Portal Backend:   http://localhost:8089"
echo "  Config Adapter:   http://localhost:8088"
echo "  Keycloak Admin:   http://localhost:8080 (admin/admin)"
echo "  Kafka UI:         http://localhost:8090"
echo "  FROST Server:     http://localhost:8085/FROST-Server/v1.1 (HTTP)"
echo "  FROST MQTT:       mqtt://localhost:1883"
echo "  APISIX Gateway:   http://localhost:9080"
echo "  APISIX Admin API: http://localhost:9180"
echo "  Redpanda Connect: http://localhost:4195"
echo "  OPA:              http://localhost:8181"
echo "  AuthZ Repository: http://localhost:8091"
echo "  Model Atlas:      http://localhost:8086"
echo "  Apicurio Registry UI: http://localhost:8888"
echo
echo "======================================================"
echo "Default Development User"
echo "======================================================"
echo
echo "  Email:    dev@civitas.local"
echo "  Password: dev123"
echo
if [ "$backend_option" = "1" ]; then
    echo "======================================================"
    echo "Application Logs (background mode)"
    echo "======================================================"
    echo
    [ "$config_adapter_option" = "1" ] && echo "  Config Adapter:  /tmp/config-adapter.log"
    echo "  Portal Backend:  /tmp/portal-backend.log"
    [ "$frontend_option" = "1" ] && echo "  Portal Frontend: /tmp/portal-frontend.log"
    echo
fi
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
echo "  cd dev-environment/redpanda-connect && docker compose down"
echo "  cd dev-environment/modelatlas && docker compose down"
echo
