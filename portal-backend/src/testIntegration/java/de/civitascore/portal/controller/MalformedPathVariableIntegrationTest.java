package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.util.ControllerRoute;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MalformedPathVariableIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)}");
  private static final Set<HttpMethod> WITH_BODY =
      Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
  private static final String MALFORMED = "not-a-uuid";

  @Autowired
  @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

  Stream<Arguments> uuidPathVariables() {
    return ControllerRoute.all(handlerMapping).stream()
        .flatMap(
            route ->
                PATH_VARIABLE
                    .matcher(route.pattern())
                    .results()
                    .map(match -> match.group(1))
                    .filter(variable -> !boundToNonUuidParameter(route.handler(), variable))
                    .map(variable -> Arguments.of(route.method(), route.pattern(), variable)));
  }

  /** A variable that no parameter binds is read raw from the path and must be a UUID too. */
  private static boolean boundToNonUuidParameter(HandlerMethod handler, String variable) {
    return Arrays.stream(handler.getMethod().getParameters())
        .filter(parameter -> variable.equals(pathVariableName(parameter).orElse(null)))
        .anyMatch(parameter -> !parameter.getType().equals(UUID.class));
  }

  private static Optional<String> pathVariableName(Parameter parameter) {
    PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
    if (pathVariable == null) {
      return Optional.empty();
    }
    return Optional.of(
        !pathVariable.name().isEmpty()
            ? pathVariable.name()
            : !pathVariable.value().isEmpty() ? pathVariable.value() : parameter.getName());
  }

  @ParameterizedTest(name = "{0} {1} with malformed {2}")
  @MethodSource("uuidPathVariables")
  void everyUuidPathVariable_whenMalformed_answers400InvalidInput(
      HttpMethod method, String pattern, String variable) {
    String path = withMalformed(pattern, variable);
    ResponseEntity<ProblemDetail> response =
        restTemplate.exchange(
            path,
            method,
            new HttpEntity<>(WITH_BODY.contains(method) ? Map.of() : null, adminHeaders()),
            ProblemDetail.class);

    assertThat(response.getStatusCode())
        .as("%s %s", method, path)
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getType()).hasToString("urn:civitas:error:INVALID_INPUT");
  }

  private static String withMalformed(String pattern, String malformedVariable) {
    Matcher matcher = PATH_VARIABLE.matcher(pattern);
    StringBuilder path = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(
          path,
          matcher.group(1).equals(malformedVariable) ? MALFORMED : UUID.randomUUID().toString());
    }
    return matcher.appendTail(path).toString();
  }

  private HttpHeaders adminHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    return headers;
  }
}
