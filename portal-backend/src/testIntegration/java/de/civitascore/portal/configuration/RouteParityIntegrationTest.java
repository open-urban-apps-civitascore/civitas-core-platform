package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.util.ControllerRoute;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The gateway authorizes a request only for a route in the OPA route table, and clients learn the
 * routes from the spec. A route in only one of the three is unreachable or undocumented.
 */
class RouteParityIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final Path OPA_ROUTE_TABLE =
      Path.of("..", "authz", "rego", "data", "backends", "portal_backend", "data.json");
  private static final String OPA_PATH_PREFIX = "/v1";
  private static final List<String> SPEC_METHODS = List.of("get", "put", "post", "patch", "delete");
  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]+}");

  @Autowired
  @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void routes_ofBackendSpecAndOpaRouteTable_areTheSame() {
    Set<String> backend = backendRoutes();
    Set<String> spec = specRoutes();
    Set<String> opa = opaRoutes();

    assertEqualRoutes("backend", backend, "spec", spec);
    assertEqualRoutes("backend", backend, "OPA route table", opa);
    assertEqualRoutes("spec", spec, "OPA route table", opa);
  }

  private Set<String> backendRoutes() {
    List<ControllerRoute> routes = ControllerRoute.all(handlerMapping);
    assertThat(routes)
        .as("every controller mapping names its HTTP methods")
        .allSatisfy(route -> assertThat(route.method()).as(route.pattern()).isNotNull());
    return routes.stream()
        .map(route -> route(route.method().name(), route.pattern()))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private Set<String> specRoutes() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api-docs", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    Set<String> routes = new TreeSet<>();
    for (Map.Entry<String, JsonNode> path : response.getBody().path("paths").properties()) {
      for (String method : SPEC_METHODS) {
        if (path.getValue().has(method)) {
          routes.add(route(method, path.getKey()));
        }
      }
    }
    return routes;
  }

  private Set<String> opaRoutes() {
    JsonNode endpoints = objectMapper.readTree(OPA_ROUTE_TABLE.toFile()).path("endpoints");
    Set<String> routes = new TreeSet<>();
    for (Map.Entry<String, JsonNode> endpoint : endpoints.properties()) {
      if (endpoint.getKey().startsWith("_")) {
        continue;
      }
      assertThat(endpoint.getKey()).startsWith(OPA_PATH_PREFIX);
      String path = endpoint.getKey().substring(OPA_PATH_PREFIX.length());
      for (String method : endpoint.getValue().propertyNames()) {
        if (!method.startsWith("_")) {
          routes.add(route(method, path));
        }
      }
    }
    return routes;
  }

  /**
   * OPA names every path variable {@code {id}}; the backend and the spec name them specifically.
   */
  private static String route(String method, String path) {
    return method.toUpperCase() + " " + PATH_VARIABLE.matcher(path).replaceAll("{id}");
  }

  private static void assertEqualRoutes(
      String leftName, Set<String> left, String rightName, Set<String> right) {
    Set<String> onlyLeft = new TreeSet<>(left);
    onlyLeft.removeAll(right);
    Set<String> onlyRight = new TreeSet<>(right);
    onlyRight.removeAll(left);

    assertThat(onlyLeft).as("routes in the %s but not in the %s", leftName, rightName).isEmpty();
    assertThat(onlyRight).as("routes in the %s but not in the %s", rightName, leftName).isEmpty();
  }
}
