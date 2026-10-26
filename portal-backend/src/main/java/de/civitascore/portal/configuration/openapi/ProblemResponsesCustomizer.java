package de.civitascore.portal.configuration.openapi;

import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

/**
 * Documents the error responses of an operation from the problem types its handler can answer: the
 * ones every operation answers, the ones its parameters imply, and the ones it declares with {@link
 * Problems}. Each error response refers to the catalog example of each of its types.
 *
 * <p>A handler without {@link Problems} keeps its hand-written error responses, and each of them
 * lists every catalog type of its status.
 */
@Component
public class ProblemResponsesCustomizer implements OperationCustomizer {

  static final String PROBLEM_JSON = "application/problem+json";
  static final String EXAMPLES_REF = "#/components/examples/";

  /** The shared responses in {@code components/responses}, by status, with their problem types. */
  static final Map<HttpStatus, SharedResponse> SHARED_RESPONSES =
      Map.of(
          HttpStatus.UNAUTHORIZED,
          new SharedResponse(
              "Unauthorized", EnumSet.of(ProblemType.UNAUTHORIZED, ProblemType.INVALID_TOKEN)),
          HttpStatus.FORBIDDEN,
          new SharedResponse("Forbidden", EnumSet.of(ProblemType.ACCESS_DENIED)),
          HttpStatus.INTERNAL_SERVER_ERROR,
          new SharedResponse(
              "InternalServerError",
              EnumSet.of(
                  ProblemType.INTERNAL_ERROR,
                  ProblemType.DATA_INTEGRITY_ERROR,
                  ProblemType.PERSISTENCE_ERROR)));

  /** A response that several operations refer to, and the problem types it documents. */
  record SharedResponse(String name, Set<ProblemType> types) {}

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    Problems declared =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), Problems.class);
    ApiResponses responses = operation.getResponses();
    Map<String, ApiResponse> handWritten = errorResponses(responses);

    Set<ProblemType> problems = implicitProblems(handlerMethod);
    if (declared != null) {
      if (!handWritten.isEmpty()) {
        throw new IllegalStateException(
            "%s declares @Problems and the error @ApiResponse %s; declare problem types only"
                .formatted(handlerMethod.getShortLogMessage(), handWritten.keySet()));
      }
      problems.addAll(Arrays.asList(declared.value()));
    } else {
      handWritten.keySet().forEach(code -> problems.addAll(catalogTypesOf(code)));
    }

    byStatus(problems)
        .forEach(
            (status, types) ->
                responses.addApiResponse(
                    String.valueOf(status.value()),
                    response(status, types, handWritten.get(String.valueOf(status.value())))));
    return operation;
  }

  /** An error response that refers to the catalog example of each of its problem types. */
  static ApiResponse problemResponse(String description, Collection<ProblemType> types) {
    MediaType problemJson =
        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"));
    types.forEach(
        type ->
            problemJson.addExamples(type.name(), new Example().$ref(EXAMPLES_REF + type.name())));
    return new ApiResponse()
        .description(description)
        .content(new Content().addMediaType(PROBLEM_JSON, problemJson));
  }

  private static ApiResponse response(
      HttpStatus status, Set<ProblemType> types, ApiResponse handWritten) {
    SharedResponse shared = SHARED_RESPONSES.get(status);
    if (shared != null && shared.types().equals(types)) {
      return new ApiResponse().$ref(shared.name());
    }
    String description =
        handWritten != null && handWritten.getDescription() != null
            ? handWritten.getDescription()
            : status.getReasonPhrase();
    return problemResponse(description, types);
  }

  private static Set<ProblemType> implicitProblems(HandlerMethod handlerMethod) {
    Set<ProblemType> problems = EnumSet.noneOf(ProblemType.class);
    SHARED_RESPONSES.values().forEach(shared -> problems.addAll(shared.types()));
    for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
      if (parameter.hasParameterAnnotation(PathVariable.class)) {
        problems.add(ProblemType.INVALID_INPUT);
      }
      if (parameter.hasParameterAnnotation(RequestBody.class)) {
        problems.add(ProblemType.MALFORMED_REQUEST);
        problems.add(ProblemType.UNSUPPORTED_MEDIA_TYPE);
        if (parameter.hasParameterAnnotation(Valid.class)
            || parameter.hasParameterAnnotation(Validated.class)) {
          problems.add(ProblemType.VALIDATION_FAILED);
        }
      }
    }
    return problems;
  }

  private static Map<String, ApiResponse> errorResponses(ApiResponses responses) {
    return responses.entrySet().stream()
        .filter(response -> response.getKey().startsWith("4") || response.getKey().startsWith("5"))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static List<ProblemType> catalogTypesOf(String code) {
    return Arrays.stream(ProblemType.values())
        .filter(type -> String.valueOf(type.status().value()).equals(code))
        .toList();
  }

  private static SortedMap<HttpStatus, Set<ProblemType>> byStatus(Set<ProblemType> problems) {
    return problems.stream()
        .collect(
            Collectors.groupingBy(
                ProblemType::status,
                TreeMap::new,
                Collectors.toCollection(() -> EnumSet.noneOf(ProblemType.class))));
  }
}
