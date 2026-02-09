#!/bin/bash
# CIVITAS CORE Platform - Backend Services Start Script

set -e

echo "======================================================"
echo "CIVITAS CORE Platform - Backend Services Easy Start"
echo "======================================================"
echo

# ---- Prerequisites -------------------------------------------------

echo "Checking prerequisites..."

# Java
if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: Java is not installed. Please install Java 21."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "ERROR: Java 21 or higher is required. Found Java $JAVA_VERSION."
    exit 1
fi
echo "✓ Java $JAVA_VERSION found"

# Ensure JAVA_HOME points to a full JDK (not JRE) — needed for maven-compiler-plugin --release flag
if [ -z "$JAVA_HOME" ] || [ ! -f "$JAVA_HOME/lib/ct.sym" ]; then
    # macOS: use /usr/libexec/java_home if available
    if [ -x /usr/libexec/java_home ]; then
        JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    fi
    # Linux: search common JDK locations
    if [ -z "$JAVA_HOME" ] || [ ! -f "$JAVA_HOME/lib/ct.sym" ]; then
        for jdk_dir in /usr/lib/jvm/temurin-*-jdk-* /usr/lib/jvm/java-21-openjdk-*; do
            if [ -f "$jdk_dir/lib/ct.sym" ]; then
                JAVA_HOME="$jdk_dir"
                break
            fi
        done
    fi
    if [ -z "$JAVA_HOME" ] || [ ! -f "$JAVA_HOME/lib/ct.sym" ]; then
        echo "ERROR: No JDK found (JRE is not sufficient). Install a Java 21 JDK."
        exit 1
    fi
    export JAVA_HOME
    echo "✓ JAVA_HOME set to $JAVA_HOME"
fi

# Maven
if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven is not installed. Please install Maven 3.6+."
    exit 1
fi
echo "✓ Maven found"

# /etc/hosts — civitas-keycloak must resolve to 127.0.0.1
# Required because Keycloak dev mode uses the request hostname as the JWT issuer.
# Docker, APISIX, the backend, and the browser all need to agree on the hostname.
if command -v getent >/dev/null 2>&1; then
    KEYCLOAK_IP=$(getent hosts civitas-keycloak 2>/dev/null | awk '{print $1}')
else
    if [ "$(uname)" = "Darwin" ]; then
        KEYCLOAK_IP=$(ping -c 1 -t 1 civitas-keycloak 2>/dev/null | head -1 | sed -n 's/^PING [^ ]* (\([0-9.]*\)).*/\1/p')
    else
        KEYCLOAK_IP=$(ping -c 1 -W 1 civitas-keycloak 2>/dev/null | head -1 | sed -n 's/^PING [^ ]* (\([0-9.]*\)).*/\1/p')
    fi
fi
if [ -z "$KEYCLOAK_IP" ]; then
    echo "ERROR: 'civitas-keycloak' does not resolve."
    echo "       Add this line to /etc/hosts:"
    echo "         127.0.0.1 civitas-keycloak"
    echo "       This is required for JWT issuer alignment across all services."
    exit 1
fi
echo "✓ civitas-keycloak resolves ($KEYCLOAK_IP)"

# Docker
if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: Docker is not installed."
    exit 1
fi
echo "✓ Docker found"

# Docker Compose (v2 only)
if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
    echo "✓ Docker Compose v2 found"
else
    echo "ERROR: Docker Compose v2 is required (docker compose)."
    exit 1
fi

echo
echo "Building backend services..."
echo

# ---- Build ---------------------------------------------------------

echo "Building Config Adapter..."
cd ../../config-adapter
mvn clean install -DskipTests -Drevision=1.0.1
cd ../dev-environment/backend

echo "Building Portal Model..."
cd ../../portal-model
mvn clean install -DskipTests -Drevision=1.0.0-SNAPSHOT
cd ../dev-environment/backend

echo "Building Portal Backend..."
cd ../../portal-backend
mvn clean package -DskipTests -Dconfig-adapter.version=1.0.1
cd ../dev-environment/backend

echo "Building AuthZ Repository..."
cd ../../authz/repository
mvn clean package -DskipTests
cd ../../dev-environment/backend

echo
echo "✓ Build completed successfully."
echo

# ---- Cleanup -------------------------------------------------------

echo "Cleaning up old containers and images..."

$DOCKER_COMPOSE down -v >/dev/null 2>&1 || true

docker rm -f \
    civitas-portal-backend \
    civitas-config-adapter \
    civitas-kafka \
    civitas-zookeeper \
    civitas-kafka-ui \
    civitas-postgres-portal \
    civitas-postgres-keycloak \
    civitas-keycloak \
    civitas-etcd \
    civitas-apisix \
    civitas-authz-repository \
    civitas-opa \
    civitas-authz-seed \
    >/dev/null 2>&1 || true

docker rmi \
    portal-backend_portal-backend \
    portal-backend-portal-backend \
    civitas-portal-backend \
    config-adapter_config-adapter \
    portal-backend_config-adapter \
    civitas-config-adapter \
    backend-portal-backend \
    backend-config-adapter \
    dev-environment-backend-portal-backend \
    dev-environment-backend-config-adapter \
    >/dev/null 2>&1 || true

docker image prune -f --filter "label=civitas=true" >/dev/null 2>&1

echo "✓ Cleanup completed."
echo

# ---- Start ---------------------------------------------------------

# Ensure shared Docker network exists (all compose files use external: true)
docker network create civitas-network 2>/dev/null || true

echo "Starting all backend services with Docker Compose..."
echo "This includes:"
echo "  - Infrastructure: Zookeeper, Kafka, Kafka UI"
echo "  - Databases: PostgreSQL (Portal), PostgreSQL (Keycloak)"
echo "  - Security: Keycloak"
echo "  - API Gateway: APISIX + etcd"
echo "  - Authorization: APISIX, OPA, AuthZ Repository"
echo "  - Applications: Portal Backend, Config Adapter"
echo

$DOCKER_COMPOSE up --build

echo
echo "Docker Compose stopped."
echo
echo "To clean up, run:"
echo "  cd dev-environment/backend && docker compose down -v"


