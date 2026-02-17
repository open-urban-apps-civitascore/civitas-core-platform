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

# Maven
if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven is not installed. Please install Maven 3.6+."
    exit 1
fi
echo "✓ Maven found"

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

echo "Starting all backend services with Docker Compose..."
echo "This includes:"
echo "  - Infrastructure: Zookeeper, Kafka, Kafka UI"
echo "  - Databases: PostgreSQL (Portal), PostgreSQL (Keycloak)"
echo "  - Security: Keycloak"
echo "  - API Gateway: APISIX + etcd"
echo "  - Applications: Portal Backend, Config Adapter"
echo

$DOCKER_COMPOSE up --build

echo
echo "Docker Compose stopped."
echo
echo "To clean up, run:"
echo "  cd dev-environment/backend && docker compose down -v"


