#!/usr/bin/env bash
set -euo pipefail

wait_for_tcp() {
  local host="$1"
  local port="$2"
  local max_attempts="${3:-60}"
  local attempt=1

  while [ "$attempt" -le "$max_attempts" ]; do
    if bash -lc "exec 3<>/dev/tcp/${host}/${port}" >/dev/null 2>&1; then
      return 0
    fi

    sleep 2
    attempt=$((attempt + 1))
  done

  echo "${host}:${port} did not become reachable in time" >&2
  exit 1
}

wait_for_tcp "${SUPERSET_DB_HOST}" "${SUPERSET_DB_PORT}"

superset db upgrade
superset init
python /app/civitas/bootstrap-superset-ci.py

exec gunicorn --bind 0.0.0.0:8088 --workers 2 --timeout 120 "superset.app:create_app()"
