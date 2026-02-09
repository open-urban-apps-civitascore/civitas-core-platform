#!/bin/bash
# Shared functions and configuration for AuthZ stack scripts.
# Source this file, do not execute it directly.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration (override via environment)
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${KEYCLOAK_REALM:-civitas-core}"
TEST_PASSWORD="${TEST_USER_PASSWORD:-test123}"
CLIENT_SECRET="${CLIENT_SECRET:-dev-only-portal-frontend-secret}"

ok()   { echo -e "  ${GREEN}$1${NC}"; }
warn() { echo -e "  ${YELLOW}$1${NC}"; }
fail() { echo -e "  ${RED}$1${NC}"; }

# ---- Prerequisite checks ------------------------------------------------
# Call check_prerequisites from scripts that need build tools (Java, Maven).
# Scripts that only manage Docker services can skip this.

check_prerequisites() {
  echo "--- Prerequisites ---"

  # Java 21+
  if ! command -v java >/dev/null 2>&1; then
    fail "Java is not installed. Please install Java 21."; exit 1
  fi
  JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
  if [ "$JAVA_VERSION" -lt 21 ]; then
    fail "Java 21 or higher is required. Found Java $JAVA_VERSION."; exit 1
  fi
  ok "Java $JAVA_VERSION"

  # Ensure JAVA_HOME points to a full JDK (not JRE)
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
      fail "No JDK found (JRE is not sufficient). Install a Java 21 JDK."; exit 1
    fi
    export JAVA_HOME
    ok "JAVA_HOME set to $JAVA_HOME"
  fi

  # Maven
  if ! command -v mvn >/dev/null 2>&1; then
    fail "Maven is not installed. Please install Maven 3.9+."; exit 1
  fi
  ok "Maven found"

  # Docker
  if ! command -v docker >/dev/null 2>&1; then
    fail "Docker is not installed."; exit 1
  fi
  ok "Docker found"

  # Docker Compose v2
  if ! docker compose version >/dev/null 2>&1; then
    fail "Docker Compose v2 is required (docker compose)."; exit 1
  fi
  ok "Docker Compose v2"

  # jq (needed for JSON parsing in seed scripts)
  if ! command -v jq >/dev/null 2>&1; then
    fail "jq is not installed. Please install jq."; exit 1
  fi
  ok "jq found"

  # /etc/hosts — civitas-keycloak must resolve to 127.0.0.1
  # Use getent on Linux, ping as cross-platform fallback (works on macOS)
  resolve_host() {
    if command -v getent >/dev/null 2>&1; then
      getent hosts "$1" 2>/dev/null | awk '{print $1}'
    elif [ "$(uname)" = "Darwin" ]; then
      # macOS: ping uses -t for timeout (not -W)
      ping -c 1 -t 1 "$1" 2>/dev/null | head -1 | sed -n 's/^PING [^ ]* (\([0-9.]*\)).*/\1/p'
    else
      ping -c 1 -W 1 "$1" 2>/dev/null | head -1 | sed -n 's/^PING [^ ]* (\([0-9.]*\)).*/\1/p'
    fi
  }
  KEYCLOAK_IP=$(resolve_host civitas-keycloak)
  if [ -z "$KEYCLOAK_IP" ]; then
    fail "'civitas-keycloak' does not resolve."
    echo "       Add this line to /etc/hosts:"
    echo "         127.0.0.1 civitas-keycloak"
    echo "       Required for JWT issuer alignment across all services."
    exit 1
  fi
  ok "civitas-keycloak resolves ($KEYCLOAK_IP)"

  echo ""
}

wait_for() {
  local name=$1 url=$2 max=$3
  echo -n "  Waiting for $name"
  for i in $(seq 1 "$max"); do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo -e " ${GREEN}ready${NC} (${i}s)"
      return 0
    fi
    sleep 1
    echo -n "."
  done
  echo -e " ${RED}timeout${NC}"
  return 1
}

# Like wait_for but succeeds on any HTTP response (including 401/403).
# Useful for checking services that have no unauthenticated endpoints.
wait_for_any_response() {
  local name=$1 url=$2 max=$3
  echo -n "  Waiting for $name"
  for i in $(seq 1 "$max"); do
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)
    if [ "$status" != "000" ]; then
      echo -e " ${GREEN}ready${NC} (${i}s)"
      return 0
    fi
    sleep 1
    echo -n "."
  done
  echo -e " ${RED}timeout${NC}"
  return 1
}
