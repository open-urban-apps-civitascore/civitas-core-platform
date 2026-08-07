#!/usr/bin/env bash
set -euo pipefail

wait_for_tcp() {
  local name="$1"
  local host="$2"
  local port="$3"
  local max_attempts="${4:-60}"
  local attempt=1

  while [ "$attempt" -le "$max_attempts" ]; do
    if bash -lc "exec 3<>/dev/tcp/${host}/${port}" >/dev/null 2>&1; then
      return 0
    fi

    sleep 2
    attempt=$((attempt + 1))
  done

  echo "${name} did not become reachable in time" >&2
  exit 1
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local max_attempts="${3:-60}"
  local attempt=1

  while [ "$attempt" -le "$max_attempts" ]; do
    if curl -skf "$url" >/dev/null 2>&1; then
      return 0
    fi

    sleep 2
    attempt=$((attempt + 1))
  done

  echo "${name} did not become ready in time" >&2
  exit 1
}

wait_for_tcp "Superset metadata database" "${SUPERSET_DB_HOST}" "${SUPERSET_DB_PORT}" 60
wait_for_http "Trino" "https://trino:8446/ui/" 90

/app/.venv/bin/superset db upgrade
/app/.venv/bin/superset init
/app/.venv/bin/python /app/civitas/bootstrap_superset.py

exec /app/docker/entrypoints/run-server.sh
