package de.civitascore.portal.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.JsonNode;

/**
 * Compares recorded answers with an OpenAPI description. An answer is undocumented when the
 * description has no operation for its method and path, when the operation does not document its
 * status, or when the response of that status has no example with its problem type.
 */
public final class UndocumentedAnswers {

  private static final String CONTEXT_PATH = "/v1";
  private static final String PROBLEM_JSON = "application/problem+json";

  /**
   * The security filter chain answers an unauthenticated request, and Spring MVC an unknown route
   * or method, before any operation is chosen.
   */
  private static final Set<Integer> ROUTE_LEVEL_STATUSES = Set.of(401, 404, 405);

  private UndocumentedAnswers() {}

  /** One line per distinct undocumented answer, sorted. */
  public static List<String> report(JsonNode spec, Collection<RecordedAnswer> answers) {
    Map<String, PathPattern> templates =
        spec.path("paths").propertyNames().stream()
            .collect(
                Collectors.toMap(template -> template, PathPatternParser.defaultInstance::parse));
    return answers.stream()
        .map(answer -> undocumented(spec, templates, answer))
        .flatMap(Optional::stream)
        .distinct()
        .sorted()
        .toList();
  }

  private static Optional<String> undocumented(
      JsonNode spec, Map<String, PathPattern> templates, RecordedAnswer answer) {
    String method = answer.method().toLowerCase(Locale.ROOT);
    PathContainer path = PathContainer.parsePath(withoutContextPath(answer.path()));
    Optional<String> template =
        templates.entrySet().stream()
            .filter(entry -> entry.getValue().matches(path))
            .filter(entry -> spec.path("paths").path(entry.getKey()).has(method))
            .min(Map.Entry.comparingByValue(PathPattern.SPECIFICITY_COMPARATOR))
            .map(Map.Entry::getKey);
    if (template.isEmpty()) {
      return ROUTE_LEVEL_STATUSES.contains(answer.status())
          ? Optional.empty()
          : Optional.of(line(answer, answer.method() + " " + answer.path(), "no operation"));
    }
    String operation = answer.method() + " " + template.get();
    JsonNode response =
        spec.path("paths")
            .path(template.get())
            .path(method)
            .path("responses")
            .path(String.valueOf(answer.status()));
    if (response.isMissingNode()) {
      return Optional.of(line(answer, operation, "status not documented"));
    }
    if (answer.problemType() != null
        && !exampleTypes(spec, resolve(spec, response)).contains(answer.problemType())) {
      return Optional.of(line(answer, operation, "problem type not documented for this status"));
    }
    return Optional.empty();
  }

  private static Set<String> exampleTypes(JsonNode spec, JsonNode response) {
    return response.path("content").path(PROBLEM_JSON).path("examples").values().stream()
        .map(example -> resolve(spec, example).path("value").path("type").asString(""))
        .collect(Collectors.toSet());
  }

  private static JsonNode resolve(JsonNode spec, JsonNode nodeOrRef) {
    JsonNode ref = nodeOrRef.path("$ref");
    if (!ref.isString()) {
      return nodeOrRef;
    }
    JsonNode node = spec;
    for (String segment : ref.asString().substring(2).split("/")) {
      node = node.path(segment);
    }
    return node;
  }

  private static String withoutContextPath(String path) {
    return path.startsWith(CONTEXT_PATH) ? path.substring(CONTEXT_PATH.length()) : path;
  }

  private static String line(RecordedAnswer answer, String operation, String reason) {
    return "%s %d %s: %s"
        .formatted(
            operation,
            answer.status(),
            answer.problemType() != null ? answer.problemType() : "-",
            reason);
  }
}
