#!/bin/bash
# Run OPA Rego unit tests
# Uses Docker to run tests (no local OPA installation required)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Running OPA Rego tests..."
docker run --rm \
  -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
  -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
  -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
  -v "${SCRIPT_DIR}/data/backends:/rego/data/backends:ro" \
  -v "${SCRIPT_DIR}/test:/rego/test:ro" \
  openpolicyagent/opa:1.4.2-static \
  test /rego/policy /rego/lib /rego/providers /rego/test /rego/data -v

echo ""
echo "All tests passed!"
