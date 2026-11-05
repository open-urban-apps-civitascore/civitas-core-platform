#!/bin/bash
# Generate backends-allowall data from real backend endpoint definitions.
#
# Reads each data/backends/<backend>/data.json and produces a matching
# data/backends-allowall/<backend>/data.json in which every permission is null.
# Endpoint flags (keys starting with _, such as _collection or _open_data) are
# copied unchanged: allow-all mode relaxes permissions only, and the policy must
# still see how each endpoint is shaped and whether it is open data.
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

    jq '{
        "_comment": "\(._backend_id) — ALLOW-ALL mode. All endpoints null-permission. DEV-ONLY.",
        "_version": ._version,
        "_backend_id": ._backend_id,
        "endpoints": (.endpoints | to_entries
            | map(select(.value | type == "object"))
            | map(.value |= with_entries(
                if (.key | startswith("_")) then . else .value = null end))
            | from_entries)
    }' "$src" > "$dest_dir/data.json"

    echo "Generated $dest_dir/data.json"
done
