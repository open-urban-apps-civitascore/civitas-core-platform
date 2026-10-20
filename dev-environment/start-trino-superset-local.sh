#!/usr/bin/env bash
# Start the complete CIVITAS dev environment, then replace Superset with a build
# based on the locally built Superset 7.0rc2 image.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STACK_DIR="$SCRIPT_DIR/trino-superset"
SUPERSET_BASE_IMAGE="superset-local:latest"
START_LOCAL_DASHBOARD=true

for arg in "$@"; do
  if [ "$arg" = "--help" ] || [ "$arg" = "-h" ]; then
    exec "$SCRIPT_DIR/start-portal-dev.sh" "$@"
  fi
  if [ "$arg" = "--no-dashboard" ]; then
    START_LOCAL_DASHBOARD=false
  fi
done

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker is not installed or not on PATH." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Compose v2 is required (docker compose)." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker daemon is unavailable. Start Docker Desktop and retry." >&2
  exit 1
fi

if [ "$START_LOCAL_DASHBOARD" = "true" ] && ! docker image inspect "$SUPERSET_BASE_IMAGE" >/dev/null 2>&1; then
  echo "ERROR: Local image '$SUPERSET_BASE_IMAGE' was not found." >&2
  echo "       Build/tag the Superset 7.0rc2 image locally, then retry." >&2
  exit 1
fi

echo "Starting the complete CIVITAS dev environment..."
MAIN_ARGS=()
for arg in "$@"; do
  if [ "$arg" != "--no-dashboard" ]; then
    MAIN_ARGS+=("$arg")
  fi
done
if [ "$START_LOCAL_DASHBOARD" = "true" ]; then
  MAIN_ARGS+=("--no-dashboard")
fi
"$SCRIPT_DIR/start-portal-dev.sh" "${MAIN_ARGS[@]}"

if [ "$START_LOCAL_DASHBOARD" = "true" ]; then
  echo
  echo "Starting Trino + Superset using the local base '$SUPERSET_BASE_IMAGE'..."
  cd "$STACK_DIR"
  docker compose build --build-arg "SUPERSET_BASE_IMAGE=$SUPERSET_BASE_IMAGE" superset
  docker compose up -d --no-build trino superset

  echo
  echo "The complete dev environment is running with the local Superset image."
  echo "Trino UI: https://localhost:${TRINO_PORT:-8446}/ui/"
  echo "Superset: http://localhost:${SUPERSET_PORT:-8092}"
  echo "Logs:     cd dev-environment/trino-superset && docker compose logs -f trino superset"
else
  echo "The complete dev environment is running; Trino and Superset were skipped (--no-dashboard)."
fi
