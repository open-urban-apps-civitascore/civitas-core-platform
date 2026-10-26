package de.civitascore.portal.problem;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds every problem that portal-backend answers. Title and detail of a catalog type come from
 * the message bundle keys {@code problem.<CODE>.title} and {@code problem.<CODE>.detail}.
 */
@Component
public class ProblemDetails {

  private static final String EXAMPLE_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
  private static final String EXAMPLE_INSTANCE = "/v1/datasets/" + EXAMPLE_ID;

  private final MessageSource messageSource;

  public ProblemDetails(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * @param instance the request URI, or {@code null} to let Spring MVC fill it in when it writes a
   *     ProblemDetail returned by a handler
   * @param detailArguments the arguments of the type's detail pattern; a free-form type takes the
   *     detail text as its only argument, which must not reveal internal state
   */
  public ProblemDetail of(ProblemType type, @Nullable URI instance, Object... detailArguments) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(type.status(), message(type, "detail", detailArguments));
    stamp(problemDetail, type);
    problemDetail.setInstance(instance);
    return problemDetail;
  }

  public ProblemDetail of(ProblemException ex, @Nullable URI instance) {
    ProblemDetail problemDetail = of(ex.type(), instance, ex.detailArguments());
    ex.properties().forEach(problemDetail::setProperty);
    return problemDetail;
  }

  /** Sets type and title of the catalog type, and keeps status and detail. */
  public ProblemDetail stamp(ProblemDetail problemDetail, ProblemType type) {
    problemDetail.setType(type.type());
    problemDetail.setTitle(message(type, "title"));
    return problemDetail;
  }

  private String message(ProblemType type, String part, Object... arguments) {
    return messageSource.getMessage(
        "problem." + type.name() + "." + part, arguments, LocaleContextHolder.getLocale());
  }

  /**
   * A sample answer of the type for the OpenAPI description. A free-form type shows its {@code
   * problem.<CODE>.example} as detail.
   */
  public Map<String, Object> example(ProblemType type) {
    String example =
        messageSource.getMessage(
            "problem." + type.name() + ".example", null, null, LocaleContextHolder.getLocale());
    Map<String, Object> answer = new LinkedHashMap<>();
    answer.put("type", type.type().toString());
    answer.put("title", message(type, "title"));
    answer.put("status", type.status().value());
    answer.put("detail", example != null ? example : message(type, "detail"));
    answer.put("instance", EXAMPLE_INSTANCE);
    answer.putAll(exampleProperties(type));
    return answer;
  }

  private static Map<String, Object> exampleProperties(ProblemType type) {
    return switch (type) {
      case VALIDATION_FAILED ->
          Map.of("fieldErrors", List.of(Map.of("field", "name", "message", "must not be blank")));
      case MODEL_MEMBER_UNREACHABLE -> Map.of("memberNames", List.of("Orphan"));
      case SAGA_IN_FLIGHT -> Map.of("pendingSagaType", "CREATE");
      case DATASOURCE_SCOPE_VIOLATION -> Map.of("offendingDataSourceIds", List.of(EXAMPLE_ID));
      case PIPELINE_CLOSURE_INVALID -> Map.of("offendingPipelineIds", List.of(EXAMPLE_ID));
      default -> Map.of();
    };
  }
}
