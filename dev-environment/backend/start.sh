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
    echo "ERROR: Java is not installed. Please install a Java 21+ JDK."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
    echo "ERROR: Java 21 or higher is required. Found Java $JAVA_VERSION."
    exit 1
fi
echo "✓ Java $JAVA_VERSION found"

OS_TYPE=$(uname -s)

# Ensure JAVA_HOME points to a full JDK (not JRE) — needed for maven-compiler-plugin --release flag.
# Supports Temurin, OpenJDK, Oracle, GraalVM, SDKMAN-installed JDKs (21+).
if [ -z "$JAVA_HOME" ] || [ ! -f "$JAVA_HOME/lib/ct.sym" ]; then
    JAVA_HOME=""
    if [ "$OS_TYPE" = "Darwin" ]; then
        # macOS: java_home returns the highest installed JDK
        if [ -x /usr/libexec/java_home ]; then
            candidate=$(/usr/libexec/java_home 2>/dev/null || true)
            if [ -n "$candidate" ] && [ -f "$candidate/lib/ct.sym" ]; then
                JAVA_HOME="$candidate"
            fi
        fi
    else
        # Linux / WSL: search common JDK locations, pick newest >= 21 with ct.sym
        best_ver=0
        for jdk_dir in /usr/lib/jvm/temurin-*-jdk-* \
                        /usr/lib/jvm/java-*-openjdk-* \
                        /usr/lib/jvm/jdk-* \
                        /usr/lib/jvm/graalvm-* \
                        "$HOME/.sdkman/candidates/java"/*/; do
            if [ -f "$jdk_dir/lib/ct.sym" ]; then
                ver=$("$jdk_dir/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
                if [ "$ver" -ge 21 ] 2>/dev/null && [ "$ver" -gt "$best_ver" ]; then
                    best_ver=$ver
                    JAVA_HOME="$jdk_dir"
                fi
            fi
        done
    fi
    if [ -z "$JAVA_HOME" ] || [ ! -f "$JAVA_HOME/lib/ct.sym" ]; then
        echo "ERROR: No JDK found (JRE is not sufficient)."
        echo "       Install any Java 21+ JDK (Temurin, OpenJDK, Oracle, GraalVM) or set JAVA_HOME."
        exit 1
    fi
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "✓ JAVA_HOME set to $JAVA_HOME"
else
    # JAVA_HOME was already set — ensure PATH is consistent
    export PATH="$JAVA_HOME/bin:$PATH"
fi

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

DEV_VERSION="1.0.0-dev"

echo "Building Config Adapter..."
cd ../../config-adapter
mvn clean install -DskipTests -Drevision=$DEV_VERSION
cd ../dev-environment/backend

echo "Building Portal Model..."
cd ../../portal-model
mvn clean install -DskipTests -Drevision=$DEV_VERSION
cd ../dev-environment/backend

echo "Building Portal Backend..."
cd ../../portal-backend
mvn clean package -DskipTests -Dconfig-adapter.version=$DEV_VERSION -Dportal-model.version=$DEV_VERSION
cd ../dev-environment/backend

echo "Building AuthZ Repository..."
cd ../../authz/repository
mvn clean package -DskipTests -Dportal-model.version=$DEV_VERSION
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


