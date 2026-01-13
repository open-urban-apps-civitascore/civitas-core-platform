#!/bin/bash

# CIVITAS CORE Platform - Easy Start Script
# This script builds and starts the complete platform

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║       CIVITAS CORE Platform Backend - Easy Start                 ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}🔍 Checking prerequisites...${NC}"

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java is not installed. Please install Java 21.${NC}"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${RED}❌ Java 21 or higher is required. Found: Java $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java $JAVA_VERSION found${NC}"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven is not installed. Please install Maven 3.6+.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Maven found${NC}"

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed. Please install Docker.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker found${NC}"

# Check Docker Compose (prefer V2)
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
    echo -e "${GREEN}✅ Docker Compose V2 found${NC}"
elif command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
    echo -e "${YELLOW}⚠️  Docker Compose V1 found (V2 recommended)${NC}"
else
    echo -e "${RED}❌ Docker Compose is not installed.${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}📦 Building backend services...${NC}"
echo ""

# Build Portal Backend
echo -e "${BLUE}Building Portal Backend...${NC}"
mvn clean package -DskipTests
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Portal Backend built successfully${NC}"
else
    echo -e "${RED}❌ Portal Backend build failed${NC}"
    exit 1
fi

echo ""

# Build Config Adapter
echo -e "${BLUE}Building Config Adapter...${NC}"
cd ../config-adapter
mvn clean package -DskipTests
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Config Adapter built successfully${NC}"
else
    echo -e "${RED}❌ Config Adapter build failed${NC}"
    exit 1
fi
cd ../portal-backend

echo ""
echo -e "${GREEN}🎉 All builds completed successfully!${NC}"
echo ""
echo -e "${YELLOW}🧹 Cleaning up old containers and images...${NC}"
echo ""

# Stop and remove existing containers
$DOCKER_COMPOSE down -v 2>/dev/null || true

# Force remove specific containers if they still exist
echo -e "${BLUE}Removing old containers...${NC}"
docker rm -f civitas-portal-backend 2>/dev/null || true
docker rm -f civitas-config-adapter 2>/dev/null || true

# Remove old images to force a clean rebuild
echo -e "${BLUE}Removing old portal-backend image...${NC}"
docker rmi portal-backend_portal-backend 2>/dev/null || true
docker rmi civitas-portal-backend 2>/dev/null || true
docker rmi portal-backend-portal-backend 2>/dev/null || true

echo -e "${BLUE}Removing old config-adapter image...${NC}"
docker rmi config-adapter_config-adapter 2>/dev/null || true
docker rmi civitas-config-adapter 2>/dev/null || true
docker rmi portal-backend_config-adapter 2>/dev/null || true

# Prune dangling images
echo -e "${BLUE}Pruning dangling images...${NC}"
docker image prune -f

echo ""
echo -e "${GREEN}✅ Cleanup completed!${NC}"
echo ""
echo -e "${YELLOW}🚀 Starting Docker Compose...${NC}"
echo ""

# Start Docker Compose with fresh build
$DOCKER_COMPOSE up --build

# This will only run if user stops docker-compose with Ctrl+C
echo ""
echo -e "${YELLOW}👋 Docker Compose stopped.${NC}"
echo -e "${BLUE}To clean up, run: ${NC}$DOCKER_COMPOSE down -v"

