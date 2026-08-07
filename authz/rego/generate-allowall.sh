#!/bin/bash
# Generate backends-allowall data from real backend endpoint definitions.
#
# Reads each data/backends/<backend>/data.json and produces a matching
# data/backends-allowall/<backend>/data.json with all permissions set to null
# and internal flags (like _collection) stripped.
#
# Two source formats are supported:
#   - "endpoints" format (e.g. portal_backend, frost_server): a map of
#     path -> method -> permission (string, array, or null).
#   - "data_permissions" format (e.g. trino, superset): a single top-level
#     array that gates the whole backend allow-all/deny-all (see
#     policy/trino.rego, policy/superset.rego) — no per-operation model.
#
# Requires: jq

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKENDS_DIR="$SCRIPT_DIR/data/backends"
ALLOWALL_DIR="$SCRIPT_DIR/data/backends-allowall"

if ! command -v jq &> /dev/null; then
    echo "ERROR: jq is required but not installed." >&2
    exit 1
fi

for backend_dir in "$BACKENDS_DIR"/*/; do
    backend=$(basename "$backend_dir")
    src="$backend_dir/data.json"

    if [ ! -f "$src" ]; then
        continue
    fi

    dest_dir="$ALLOWALL_DIR/$backend"
    mkdir -p "$dest_dir"

    if jq -e 'has("data_permissions")' "$src" > /dev/null; then
        jq '{
            "_comment": "\(._backend_id) — ALLOW-ALL mode. data_permissions nulled. DEV-ONLY.",
            "_version": ._version,
            "_backend_id": ._backend_id,
            "data_permissions": null
        }' "$src" > "$dest_dir/data.json"
    else
        jq '{
            "_comment": "\(._backend_id) — ALLOW-ALL mode. All endpoints null-permission. DEV-ONLY.",
            "_version": ._version,
            "_backend_id": ._backend_id,
            "endpoints": ((.endpoints // {}) | to_entries
                | map(select(.value | (type == "object") or (. == null)))
                | map(
                    if .value == null then .
                    else {
                        key: .key,
                        value: (.value | to_entries
                            | map(select(.key | startswith("_") | not))
                            | map(.value = null)
                            | from_entries)
                    }
                    end
                ) | from_entries)
        }' "$src" > "$dest_dir/data.json"
    fi

    echo "Generated $dest_dir/data.json"
done
