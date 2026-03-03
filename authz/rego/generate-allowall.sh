#!/bin/bash
# Generate backends-allowall data from real backend endpoint definitions.
#
# Reads each data/backends/<backend>/data.json and produces a matching
# data/backends-allowall/<backend>/data.json with all permissions set to null
# and internal flags (like _collection) stripped.
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
        "endpoints": (.endpoints | to_entries | map({
            key: .key,
            value: (.value | to_entries
                | map(select(.key | startswith("_") | not))
                | map(.value = null)
                | from_entries)
        }) | from_entries)
    }' "$src" > "$dest_dir/data.json"

    echo "Generated $dest_dir/data.json"
done
