package de.civitascore.portal.configuration.openapi;

import de.civitascore.portal.problem.ProblemType;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Adds a catalog example of every problem type to {@code components/examples}, and the error
 * responses that {@link ProblemResponsesCustomizer} shares between operations to {@code
 * components/responses}.
 */
@Component
public class ProblemComponentsCustomizer implements OpenApiCustomizer {

  private final ProblemExamples problemExamples;

  ProblemComponentsCustomizer(ProblemExamples problemExamples) {
    this.problemExamples = problemExamples;
  }

  @Override
  public void customise(OpenAPI openApi) {
    Components components = openApi.getComponents();
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
