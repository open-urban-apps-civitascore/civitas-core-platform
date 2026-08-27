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
OPA_IMAGE="openpolicyagent/opa:1.19.1-static"

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

# Check 4: Runtime env contract
# Every opa.runtime().env.<KEY> read must be declared in policy/env_contract.rego.
echo -n "  Checking runtime env contract... "

# Env keys read in the rego source.
USED_ENV=$(grep -rhoE 'opa\.runtime\(\)\.env\.[A-Za-z_][A-Za-z0-9_]*' \
  "${SCRIPT_DIR}/policy" "${SCRIPT_DIR}/lib" "${SCRIPT_DIR}/providers" 2>/dev/null \
  | sed -E 's/.*\.env\.//' | sort -u)

# Env keys declared in the contract.
DECLARED_ENV=$(docker run --rm \
  -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
  -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
  -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
  ${OPA_IMAGE} \
  eval -f raw -d /rego/policy -d /rego/lib -d /rego/providers \
  'data.civitas.authz.env_contract.required_env[_]' 2>/dev/null | sort -u)

UNDECLARED=""
for key in ${USED_ENV}; do
    if ! printf '%s\n' "${DECLARED_ENV}" | grep -qx "${key}"; then
        UNDECLARED="${UNDECLARED} ${key}"
    fi
done

if [ -z "${UNDECLARED}" ]; then
    echo "OK"
else
    echo "FAILED"
    echo ""
    echo "Env vars read but NOT declared in policy/env_contract.rego:${UNDECLARED}"
    echo "Add them to required_env."
    exit 1
fi

# Check 5: Bundle config contract
# Every data.config.<key> read must be declared in policy/config_contract.rego, and
# every REQUIRED key must exist in the config.json baked into the published OPA image
# (../Dockerfile). A required key that only reaches the dev/CI configs ships a policy
# the released image cannot activate — fail-secure, but silently inert.
echo -n "  Checking bundle config contract... "

USED_CONFIG=$(grep -rhoE 'data\.config\.[a-z_][a-z0-9_]*' \
  "${SCRIPT_DIR}/policy" "${SCRIPT_DIR}/lib" "${SCRIPT_DIR}/providers" 2>/dev/null \
  | sed -E 's/.*\.config\.//' | sort -u)

eval_config_set() {
    docker run --rm \
      -v "${SCRIPT_DIR}/policy:/rego/policy:ro" \
      -v "${SCRIPT_DIR}/lib:/rego/lib:ro" \
      -v "${SCRIPT_DIR}/providers:/rego/providers:ro" \
      ${OPA_IMAGE} \
      eval -f raw -d /rego/policy -d /rego/lib -d /rego/providers \
      "data.civitas.authz.config_contract.$1[_]" 2>/dev/null | sort -u
}

if [ -z "${USED_CONFIG}" ]; then
    echo "FAILED"
    echo ""
    echo "No data.config.* reads found at all — the contract scan matched nothing."
    echo "Either the read idiom changed or a policy directory moved; the check cannot verify anything."
    exit 1
fi

REQUIRED_CONFIG=$(eval_config_set required_config)
OPTIONAL_CONFIG=$(eval_config_set optional_config)

CONFIG_ERRORS=""
for key in ${USED_CONFIG}; do
    if printf '%s\n' "${REQUIRED_CONFIG}" | grep -qx "${key}"; then
        if ! grep -q "\"${key}\"" "${SCRIPT_DIR}/../Dockerfile"; then
            CONFIG_ERRORS="${CONFIG_ERRORS}\n  ${key}: required, but missing from the image-baked config.json (authz/Dockerfile)"
        fi
    elif ! printf '%s\n' "${OPTIONAL_CONFIG}" | grep -qx "${key}"; then
        CONFIG_ERRORS="${CONFIG_ERRORS}\n  ${key}: read by a policy, but declared in neither required_config nor optional_config"
    fi
done

if [ -z "${CONFIG_ERRORS}" ]; then
    echo "OK"
else
    echo "FAILED"
    echo ""
    echo "Bundle config contract violations (policy/config_contract.rego):"
    printf '%b\n' "${CONFIG_ERRORS}"
    echo ""
    echo "A new required key also belongs in dev-environment/apisix/opa-config/data.json"
    echo "and dev-environment/ci/docker-compose.api-test.yml."
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
  "roots": ["civitas", "backends", "system"]
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
