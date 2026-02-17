#!/bin/bash
# Build OPA bundle for CIVITAS AuthZ policies
#
# This script performs pre-flight checks and builds a deployable OPA bundle.
# The bundle includes all policies, providers, libraries, and backend data.
#
# Usage:
#   ./build-bundle.sh              # Build with auto-generated revision
#   ./build-bundle.sh v1.0.0       # Build with explicit revision
#
# Output: bundle.tar.gz

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# OPA image version (keep in sync with docker-compose.yml and CI)
OPA_IMAGE="openpolicyagent/opa:1.4.2-static"

# Determine revision (from argument, git SHA, or timestamp)
if [ -n "$1" ]; then
    REVISION="$1"
elif git rev-parse --git-dir > /dev/null 2>&1; then
    REVISION=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
else
    REVISION="dev-$(date +%Y%m%d%H%M%S)"
fi

echo "Building OPA bundle (revision: ${REVISION})..."
echo ""

# Create temporary directory for bundle contents
# chmod 755 ensures OPA container (non-root user) can read the files
BUNDLE_DIR=$(mktemp -d)
chmod 755 "${BUNDLE_DIR}"
trap 'rm -rf "${BUNDLE_DIR}"' EXIT

# =============================================================================
# PRE-FLIGHT CHECKS
# =============================================================================

echo "=== Pre-flight checks ==="

# Check 1: Format verification
echo -n "  Checking Rego formatting... "
if docker run --rm \
  -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
  -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
  -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
  ${OPA_IMAGE} \
  fmt --diff /rego/policy /rego/lib /rego/providers > /dev/null 2>&1; then
    echo "OK"
else
    echo "FAILED"
    echo ""
    echo "Formatting issues found. Run 'opa fmt --write' to fix:"
    docker run --rm \
      -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
      -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
      -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
      ${OPA_IMAGE} \
      fmt --diff /rego/policy /rego/lib /rego/providers
    exit 1
fi

# Check 2: Type checking (strict mode)
echo -n "  Checking Rego types (strict)... "
if docker run --rm \
  -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
  -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
  -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
  -v "${SCRIPT_DIR}/data/backends:/rego/data/backends:ro" \
  ${OPA_IMAGE} \
  check --strict /rego/policy /rego/lib /rego/providers > /dev/null 2>&1; then
    echo "OK"
else
    echo "FAILED"
    echo ""
    docker run --rm \
      -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
      -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
      -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
      -v "${SCRIPT_DIR}/data/backends:/rego/data/backends:ro" \
      ${OPA_IMAGE} \
      check --strict /rego/policy /rego/lib /rego/providers
    exit 1
fi

# Check 3: Run unit tests
echo -n "  Running unit tests... "
if docker run --rm \
  -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
  -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
  -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
  -v "${SCRIPT_DIR}/data/backends:/rego/data/backends:ro" \
  -v "${SCRIPT_DIR}/test:/rego/test:ro" \
  ${OPA_IMAGE} \
  test /rego/policy /rego/lib /rego/providers /rego/test /rego/data > /dev/null 2>&1; then
    echo "OK"
else
    echo "FAILED"
    echo ""
    docker run --rm \
      -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
      -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
      -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
      -v "${SCRIPT_DIR}/data/backends:/rego/data/backends:ro" \
      -v "${SCRIPT_DIR}/test:/rego/test:ro" \
      ${OPA_IMAGE} \
      test /rego/policy /rego/lib /rego/providers /rego/test /rego/data -v
    exit 1
fi

echo ""

# =============================================================================
# BUILD BUNDLE
# =============================================================================

echo "=== Building bundle ==="

# Copy source files to bundle directory
# Bundle structure:
#   policy/*.rego, lib/*.rego, providers/*.rego -> Rego policies (package civitas.authz.*)
#   data/backends/*/data.json -> Data files (data.backends.*)
mkdir -p "${BUNDLE_DIR}/policy"
mkdir -p "${BUNDLE_DIR}/lib"
mkdir -p "${BUNDLE_DIR}/providers"
mkdir -p "${BUNDLE_DIR}/backends"

cp policy/*.rego "${BUNDLE_DIR}/policy/"
cp lib/*.rego "${BUNDLE_DIR}/lib/"
cp providers/*.rego "${BUNDLE_DIR}/providers/"
cp -r data/backends/* "${BUNDLE_DIR}/backends/"

# Ensure OPA container (non-root user) can read all files
chmod -R a+rX "${BUNDLE_DIR}"

# Create manifest with revision
cat > "${BUNDLE_DIR}/.manifest" << EOF
{
  "revision": "${REVISION}",
  "roots": ["civitas", "backends"]
}
EOF

# Build the bundle
echo "  Creating bundle.tar.gz..."
docker run --rm \
  -v "${BUNDLE_DIR}:/bundle:ro" \
  -v "${SCRIPT_DIR}:/output" \
  ${OPA_IMAGE} \
  build -b /bundle -o /output/bundle.tar.gz

echo ""
echo "=== Bundle built successfully ==="
echo "  Output: ${SCRIPT_DIR}/bundle.tar.gz"
echo "  Revision: ${REVISION}"
echo ""

# Show bundle contents
echo "Bundle contents:"
tar -tzf "${SCRIPT_DIR}/bundle.tar.gz" | head -20
TOTAL=$(tar -tzf "${SCRIPT_DIR}/bundle.tar.gz" | wc -l)
if [ "$TOTAL" -gt 20 ]; then
    echo "  ... and $((TOTAL - 20)) more files"
fi
