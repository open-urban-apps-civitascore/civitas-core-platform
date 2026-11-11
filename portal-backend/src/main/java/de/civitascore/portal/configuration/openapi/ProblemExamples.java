package de.civitascore.portal.configuration.openapi;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.problem.FieldError;
import de.civitascore.portal.problem.ProblemDetails;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.service.removal.UnconfirmedRemoval;
import de.civitascore.portal.service.removal.UnconfirmedRemoval.RemovedApi;
import de.civitascore.portal.service.removal.UnconfirmedRemoval.RemovedDataSink;
import de.civitascore.portal.service.removal.UnconfirmedRemoval.RemovedLayer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** The sample answer of each problem type in the OpenAPI description. */
@Component
class ProblemExamples {

  private static final String EXAMPLE_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
  private static final String EXAMPLE_INSTANCE = "/v1/datasets/" + EXAMPLE_ID;

  private final ProblemDetails problemDetails;
  private final MessageSource messageSource;

  ProblemExamples(ProblemDetails problemDetails, MessageSource messageSource) {
    this.problemDetails = problemDetails;
    this.messageSource = messageSource;
  }

  /** A free-form type shows its {@code problem.<CODE>.example} as detail. */
  Map<String, Object> example(ProblemType type) {
    ProblemDetail problem = problemDetails.of(type);
    String sampleDetail =
        messageSource.getMessage(
            "problem." + type.name() + ".example", null, null, LocaleContextHolder.getLocale());
    Map<String, Object> answer = new LinkedHashMap<>();
    answer.put("type", type.type().toString());
    answer.put("title", problem.getTitle());
    answer.put("status", problem.getStatus());
    answer.put("detail", sampleDetail != null ? sampleDetail : problem.getDetail());
    answer.put("instance", EXAMPLE_INSTANCE);
    answer.putAll(exampleProperties(type));
    return answer;
  }

  private static Map<String, Object> exampleProperties(ProblemType type) {
    return switch (type) {
      case VALIDATION_FAILED ->
          Map.of("fieldErrors", List.of(new FieldError("name", "must not be blank")));
      case MODEL_MEMBER_UNREACHABLE -> Map.of("memberNames", List.of("Orphan"));
      case SAGA_IN_FLIGHT -> Map.of("pendingSagaType", "CREATE");
      case REMOVAL_NOT_CONFIRMED -> Map.of("removal", exampleRemoval());
      case DATASOURCE_SCOPE_VIOLATION -> Map.of("offendingDataSourceIds", List.of(EXAMPLE_ID));
      case PIPELINE_CLOSURE_INVALID -> Map.of("offendingPipelineIds", List.of(EXAMPLE_ID));
      default -> Map.of();
    };
  }

  private static UnconfirmedRemoval exampleRemoval() {
    UUID id = UUID.fromString(EXAMPLE_ID);
    return new UnconfirmedRemoval(
        List.of(new RemovedDataSink(id, DataSinkType.POSTGIS, true)),
        List.of(new RemovedLayer(id, "roads", "Roads")),
        List.of(new RemovedApi(id, "roads", "Roads", ApiStandard.OWS)));
  }
}
