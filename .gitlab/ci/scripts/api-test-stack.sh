# The API test stack (dev-environment/ci/docker-compose.api-test.yml) for CI jobs. Source this file;
# it defines functions and runs nothing but a check of the jars it finds. The job needs a Docker
# daemon (.dind) and the docker CLI (.docker-cli).
#
#   . .gitlab/ci/scripts/api-test-stack.sh
#   api_test_up                 # build, start, wait, seed APISIX and the NiFi parameter context
#   api_test_compose exec ...   # docker compose against the stack
#   api_test_collect_logs       # service logs into *-logs.txt, for the artifacts
#   api_test_down               # in after_script
#
# A component that this pipeline built (its jar is in the working tree) is built from source; any
# other is pulled from the registry as the image of ${IMAGE_TAG:-develop}.

API_TEST_PROJECT=api-test
API_TEST_NETWORK=api-test-net
API_TEST_COMPOSE_FILES="-f dev-environment/ci/docker-compose.api-test.yml"

if ls portal-backend/target/*.jar >/dev/null 2>&1; then
  echo "Portal-backend JAR found — building from source"
  API_TEST_COMPOSE_FILES="$API_TEST_COMPOSE_FILES -f dev-environment/ci/docker-compose.api-test.build-portal.yml"
else
  echo "Portal-backend JAR not available — using registry image"
fi
if ls config-adapter/config-adapter-application/target/*.jar >/dev/null 2>&1; then
  echo "Config-adapter JARs found — building from source"
  API_TEST_COMPOSE_FILES="$API_TEST_COMPOSE_FILES -f dev-environment/ci/docker-compose.api-test.build-ca.yml"
else
  echo "Config-adapter JARs not available — using registry image"
fi
if ls authz/repository/target/*.jar >/dev/null 2>&1; then
  echo "AuthZ JARs found — building from source"
  API_TEST_COMPOSE_FILES="$API_TEST_COMPOSE_FILES -f dev-environment/ci/docker-compose.api-test.build-authz.yml"
else
  echo "AuthZ JARs not available — using registry image"
fi

api_test_compose() {
  # shellcheck disable=SC2086
  docker compose -p "$API_TEST_PROJECT" $API_TEST_COMPOSE_FILES "$@"
}

api_test_up() {
  # Login to the registry in case any service still needs a pre-built image
  printf '%s' "$CI_REGISTRY_PASSWORD" | docker login -u "$CI_REGISTRY_USER" --password-stdin "$CI_REGISTRY"
  # Start the full stack, which blocks until all services are healthy
  if ! api_test_compose up -d --build --wait --wait-timeout 600; then
    echo "--- Container health status ---"
    api_test_compose ps -a
    echo "--- Unhealthy container logs ---"
    for c in $(api_test_compose ps -a --format '{{.Name}}'); do
      STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$c" 2>/dev/null)
      if [ "$STATUS" != "healthy" ] && [ -n "$STATUS" ]; then
        echo "=== $c ($STATUS) ==="
        docker logs "$c" --tail 50 2>&1
      fi
    done
    return 1
  fi
  # Seed APISIX routes (services, plugin config, portal-backend route)
  api_test_compose run --rm apisix-seed
  # Provision the NiFi parameter context the MQTT-TLS flows resolve their truststore password from
  api_test_compose run --rm nifi-parameter-contexts
}

api_test_collect_logs() {
  for svc in portal-backend config-adapter connection-tester keycloak kafka apisix frost nifi wiremock; do
    echo "=== $svc logs (last 300 lines) ==="
    api_test_compose logs --tail=300 --no-color "$svc" 2>&1 | tee "${svc}-logs.txt" || true
  done
}

api_test_down() {
  api_test_compose down -v 2>/dev/null || true
}
