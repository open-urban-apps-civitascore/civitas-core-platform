package de.civitascore.portal.configuration.openapi;

import de.civitascore.portal.problem.ProblemType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Adds the {@link ProblemDetail} schema to {@code components/schemas}, a catalog example of every
 * problem type to {@code components/examples}, and the error responses that {@link
 * ProblemResponsesCustomizer} shares between operations to {@code components/responses}.
 */
@Component
public class ProblemComponentsCustomizer implements OpenApiCustomizer {

  private final ProblemExamples problemExamples;
  private final SpringDocConfigProperties springDocConfigProperties;

  ProblemComponentsCustomizer(
      ProblemExamples problemExamples, SpringDocConfigProperties springDocConfigProperties) {
    this.problemExamples = problemExamples;
    this.springDocConfigProperties = springDocConfigProperties;
  }

  @Override
  public void customise(OpenAPI openApi) {
    Components components = openApi.getComponents();
    ModelConverters.getInstance(springDocConfigProperties.isOpenapi31())
        .readAll(ProblemDetail.class)
        .forEach(components::addSchemas);
    for (ProblemType type : ProblemType.values()) {
      components.addExamples(
          type.name(),
          new Example().summary(type.type().toString()).value(problemExamples.example(type)));
    }
    ProblemResponsesCustomizer.SHARED_RESPONSES.forEach(
        (status, shared) ->
            components.addResponses(
                shared.name(),
                ProblemResponsesCustomizer.problemResponse(
                    status.getReasonPhrase(), shared.types())));
  }
}
