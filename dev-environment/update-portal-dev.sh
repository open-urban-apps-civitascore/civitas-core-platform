#!/usr/bin/env bash
# ============================================================================
# update-portal-dev.sh – FOR DOCKER!
# ----------------------------------------------------------------------------
# Rebuild + hot-reload individual application containers (portal-frontend,
# portal-backend, config-adapter) WITHOUT re-running the full start-portal-dev.sh.
#
# The dev stack runs production builds inside containers (no hot reload), so a
# source change only shows up after the image is rebuilt AND the container is
# recreated. This script does exactly that for the component(s) you name:
#
#   - portal-frontend : the Next.js production build happens inside the Docker
#                       image (Dockerfile.frontend), so we just rebuild the image.
#   - portal-backend  : the Dockerfile copies a pre-built fat JAR, so we build the
#                       JAR first (containerized Maven, same .m2 volume as the
#                       start script) — including the embedded model-forge — then
#                       rebuild the image.
#   - config-adapter  : same as the backend (pre-built fat JAR via -Pdist).
#
# In every case the container is recreated with --force-recreate, because a
# plain `docker compose up -d` keeps the old container when the image tag is
# unchanged and would silently run the old code.
#
# Usage:
#   ./update-portal-dev.sh                 # rebuild + reload all three
#   ./update-portal-dev.sh -f              # only the frontend
#   ./update-portal-dev.sh -b -c           # backend + config-adapter
#   ./update-portal-dev.sh -b --skip-model-forge   # backend, reuse the cached model-forge
#   ./update-portal-dev.sh --no-clean      # incremental Maven build (drop 'clean')
#
# Prerequisite: the dev stack was started at least once (start-portal-dev.sh) so
# the shared Maven cache holds portal-model / config-adapter / model-forge.
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OS_TYPE=$(uname -s)

# ---- Docker-on-Windows path helpers (identical to start-portal-dev.sh) ------
# Git Bash / MSYS rewrites a lone "/foo" arg into a Windows path, corrupting
# container-internal paths passed to `docker run`. A leading "//" survives MSYS
# and still resolves to "/foo" inside Linux. Bind-mount HOST paths need real
# Windows paths (cygpath). On Linux/macOS CP="/" and winhost() is a plain echo.
CP="/"
case "$OS_TYPE" in MINGW*|MSYS*|CYGWIN*) CP="//" ;; esac
winhost() { case "$OS_TYPE" in MINGW*|MSYS*|CYGWIN*) cygpath -m "$1" ;; *) printf '%s' "$1" ;; esac; }

PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APPS_DIR="$SCRIPT_DIR/apps"
MVN_CACHE_VOLUME="civitas-dev-mvn-cache"   # same shared .m2 volume the start script uses
DEV_VERSION="1.0.0-dev"                     # revision for portal-model / config-adapter / backend

# ---- CLI ---------------------------------------------------------------------
DO_FRONTEND=false
DO_BACKEND=false
DO_CONFIG=false
NO_CLEAN=false
SKIP_MODEL_FORGE=false
component_selected=false

usage() {
    cat <<EOF
Usage: $(basename "$0") [COMPONENTS] [OPTIONS]

Rebuild and reload selected dev-stack app containers. With no component given,
all three are rebuilt.

Components:
  -f, --frontend         portal-frontend
  -b, --backend          portal-backend (rebuilds embedded model-forge too)
  -c, --config-adapter   config-adapter
  -a, --all              all three (explicit)

Options:
  --skip-model-forge     with --backend, do NOT rebuild model-forge (reuse the
                         cached 0.1.0-SNAPSHOT) — use when only backend code changed
  --no-clean             incremental Maven build (drop the 'clean' goal)
  -h, --help             show this help
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        -f|--frontend)        DO_FRONTEND=true; component_selected=true ;;
        -b|--backend)         DO_BACKEND=true;  component_selected=true ;;
        -c|--config-adapter)  DO_CONFIG=true;   component_selected=true ;;
        -a|--all)             DO_FRONTEND=true; DO_BACKEND=true; DO_CONFIG=true; component_selected=true ;;
        --skip-model-forge)   SKIP_MODEL_FORGE=true ;;
        --no-clean)           NO_CLEAN=true ;;
        -h|--help)            usage; exit 0 ;;
        *) echo "Unknown option: $1"; echo; usage; exit 1 ;;
    esac
    shift
done

# Default: rebuild everything when no component was named.
if [ "$component_selected" = "false" ]; then
    DO_FRONTEND=true; DO_BACKEND=true; DO_CONFIG=true
fi

if [ "$NO_CLEAN" = "true" ]; then MVN_CLEAN=""; else MVN_CLEAN="clean"; fi

# ---- Prerequisites -----------------------------------------------------------
command -v docker >/dev/null 2>&1 || { echo "ERROR: Docker is not installed."; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "ERROR: Docker Compose v2 is required."; exit 1; }
[ -f "$APPS_DIR/docker-compose.yml" ] || { echo "ERROR: $APPS_DIR/docker-compose.yml not found."; exit 1; }

# ---- Containerized Maven (copied from start-portal-dev.sh) -------------------
# Builds inside maven:3.9-eclipse-temurin-25 against the shared .m2 volume, so the
# host needs no Java 25 and artifacts land in the same cache the dev stack uses.
mvn_in_container() {
    local work_dir="$1"
    shift
    local rel_path
    case "$work_dir" in
        "$PROJECT_ROOT"/*) rel_path="${work_dir#$PROJECT_ROOT/}" ;;
        "$PROJECT_ROOT")   rel_path="." ;;
        *)
            echo "ERROR: mvn_in_container work dir '$work_dir' is not under PROJECT_ROOT '$PROJECT_ROOT'" >&2
            exit 1
            ;;
    esac

    local host_uid host_gid
    host_uid=$(id -u)
    host_gid=$(id -g)

    docker run --rm \
        -v "$(winhost "$PROJECT_ROOT"):${CP}project" \
        -v "$MVN_CACHE_VOLUME:${CP}var/maven/.m2" \
        -e "MAVEN_CONFIG=${CP}var/maven/.m2" \
        -w "${CP}project/$rel_path" \
        maven:3.9-eclipse-temurin-25 \
        sh -c "
            chown -R $host_uid:$host_gid /var/maven/.m2 && \
            exec setpriv --reuid=$host_uid --regid=$host_gid --clear-groups \
                mvn -Duser.home=/var/maven \"\$@\"
        " -- "$@"
}

# ---- Build + recreate a docker compose service ------------------------------
# Runs from the apps dir so the compose project name is "apps" (matching the
# images/containers the start script created). --force-recreate is essential:
# without it compose keeps the old container when the image tag is unchanged.
rebuild_image_and_recreate() {
    local service="$1"
    local container="$2"
    echo "  Rebuilding image and recreating container ($service)..."
    ( cd "$APPS_DIR" && docker compose build "$service" && docker compose up -d --force-recreate --no-deps "$service" )
    wait_healthy "$container"
}

# Poll until the container reports healthy (or has no healthcheck / times out).
wait_healthy() {
    local container="$1"
    local status i=0
    printf "  waiting for %s" "$container"
    while true; do
        status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || echo "missing")
        case "$status" in
            healthy) echo " ... healthy ✓"; return 0 ;;
            none)    echo " ... started (no healthcheck) ✓"; return 0 ;;
            missing) echo " ... NOT FOUND ✗"; return 1 ;;
        esac
        i=$((i + 1))
        if [ "$i" -gt 120 ]; then echo " ... TIMEOUT (status=$status) ✗"; return 1; fi
        printf "."
        sleep 3
    done
}

echo "======================================================"
echo "CIVITAS/CORE Platform - Update dev containers"
echo "  frontend=$DO_FRONTEND  backend=$DO_BACKEND  config-adapter=$DO_CONFIG"
[ "$NO_CLEAN" = "true" ] && echo "  (incremental build: no 'clean')"
echo "======================================================"
echo

# ---- config-adapter (pre-built fat JAR via -Pdist) --------------------------
if [ "$DO_CONFIG" = "true" ]; then
    echo "== config-adapter =="
    echo "  Building config-adapter JAR (containerized Maven)..."
    mvn_in_container "$PROJECT_ROOT/config-adapter" $MVN_CLEAN install -DskipTests -Drevision=$DEV_VERSION -Pdist -q
    rebuild_image_and_recreate config-adapter civitas-config-adapter
    echo
fi

# ---- portal-backend (embedded model-forge + pre-built fat JAR) --------------
if [ "$DO_BACKEND" = "true" ]; then
    echo "== portal-backend =="
    if [ "$SKIP_MODEL_FORGE" = "false" ]; then
        # model-forge is embedded in the backend as core-model-forge-*:0.1.0-SNAPSHOT
        # (fixed version, no -Drevision). Rebuild it into the cache first so the backend
        # resolves it. Skip the npm types module — the backend only needs the Java jars.
        echo "  Building model-forge (0.1.0-SNAPSHOT)..."
        mvn_in_container "$PROJECT_ROOT/model-forge" $MVN_CLEAN install -DskipTests \
            -Dspotless.check.skip=true -Dspotbugs.skip=true -pl '!core-model-forge-types' -q
    else
        echo "  (--skip-model-forge) reusing the cached model-forge"
    fi
    echo "  Building portal-backend JAR (containerized Maven)..."
    mvn_in_container "$PROJECT_ROOT/portal-backend" $MVN_CLEAN package -DskipTests \
        -Dconfig-adapter.version=$DEV_VERSION -Dportal-model.version=$DEV_VERSION -q
    rebuild_image_and_recreate portal-backend civitas-portal-backend
    echo
fi

# ---- portal-frontend (build happens inside the Docker image) ----------------
if [ "$DO_FRONTEND" = "true" ]; then
    echo "== portal-frontend =="
    rebuild_image_and_recreate portal-frontend civitas-portal-frontend
    echo
fi

echo "======================================================"
echo "Update complete."
echo "  Logs: cd dev-environment/apps && docker compose logs -f <service>"
echo "======================================================"
