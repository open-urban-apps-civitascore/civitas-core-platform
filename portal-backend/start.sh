#!/bin/bash
# CIVITAS CORE Platform - Easy Start Script

set -e

echo "CIVITAS CORE Platform Backend - Easy Start"
echo "========================================"
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
echo "Java $JAVA_VERSION found"

# Maven
if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven is not installed. Please install Maven 3.6+."
    exit 1
fi
echo "Maven found"

# Docker
if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: Docker is not installed."
    exit 1
fi
echo "Docker found"

# Docker Compose (v2 only)
if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
    echo "Docker Compose v2 found"
else
    echo "ERROR: Docker Compose v2 is required (docker compose)."
    exit 1
fi

echo
echo "Building backend services..."
echo

# ---- Build ---------------------------------------------------------

echo "Building Config Adapter..."
cd ../config-adapter
mvn clean install -DskipTests
cd ../portal-backend

echo "Building Portal Backend..."
mvn clean package -DskipTests

echo
echo "Build completed successfully."
echo

# ---- Cleanup -------------------------------------------------------

echo "Cleaning up old containers and images..."

$DOCKER_COMPOSE down -v >/dev/null 2>&1 || true

docker rm -f \
    civitas-portal-backend \
    civitas-config-adapter \
    >/dev/null 2>&1 || true

docker rmi \
    portal-backend_portal-backend \
    portal-backend-portal-backend \
    civitas-portal-backend \
    config-adapter_config-adapter \
    portal-backend_config-adapter \
    civitas-config-adapter \
    >/dev/null 2>&1 || true

docker image prune -f

echo "Cleanup completed."
echo

# ---- Start ---------------------------------------------------------

echo "Starting Docker Compose..."
echo

$DOCKER_COMPOSE up --build

echo
echo "Docker Compose stopped."
echo "To clean up, run: $DOCKER_COMPOSE down -v"
