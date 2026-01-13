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

# Check Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker Compose found${NC}"

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
echo -e "${YELLOW}🚀 Starting Docker Compose...${NC}"
echo ""

# Start Docker Compose
docker-compose up --build

# This will only run if user stops docker-compose with Ctrl+C
echo ""
echo -e "${YELLOW}👋 Docker Compose stopped.${NC}"
echo -e "${BLUE}To clean up, run: ${NC}docker-compose down -v"

