package de.civitascore.portal.configuration.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

class ProblemResponsesCustomizerTest {

  private final ProblemResponsesCustomizer customizer = new ProblemResponsesCustomizer();

  @Test
  void declaredProblems_areGroupedByStatus_withOneExampleRefPerType() {
    ApiResponses responses = customize("declaredConflicts", new ApiResponses());

    assertThat(exampleRefs(responses.get("409")))
        .containsOnlyKeys("RESOURCE_IN_USE", "SAGA_IN_FLIGHT")
        .containsEntry("SAGA_IN_FLIGHT", "#/components/examples/SAGA_IN_FLIGHT");
    assertThat(responses.get("409").getContent())
        .containsOnlyKeys(ProblemResponsesCustomizer.PROBLEM_JSON);
    assertThat(exampleRefs(responses.get("404"))).containsOnlyKeys("NOT_FOUND");
    assertThat(responses.get("404").getDescription()).isEqualTo("Not Found");
  }

  @Test
  void pathVariable_addsInvalidInput() {
    assertThat(exampleRefs(customize("pathVariable", new ApiResponses()).get("400")))
        .containsOnlyKeys("INVALID_INPUT");
  }

  @Test
  void requestBody_addsMalformedRequestAndUnsupportedMediaType() {
    ApiResponses responses = customize("body", new ApiResponses());

    assertThat(exampleRefs(responses.get("400"))).containsOnlyKeys("MALFORMED_REQUEST");
    assertThat(exampleRefs(responses.get("415"))).containsOnlyKeys("UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void validatedBody_addsValidationFailed() {
    assertThat(exampleRefs(customize("validatedBody", new ApiResponses()).get("400")))
        .containsOnlyKeys("MALFORMED_REQUEST", "VALIDATION_FAILED");
  }

  @Test
  void everyOperation_refersToTheShared401_403_500() {
    ApiResponses responses = customize("noParameters", new ApiResponses());

    assertThat(responses)
        .containsOnlyKeys("401", "403", "500")
        .allSatisfy(
            (code, response) ->
                assertThat(response.get$ref())
                    .isIn(
                        "#/components/responses/Unauthorized",
                        "#/components/responses/Forbidden",
                        "#/components/responses/InternalServerError"));
  }

  @Test
  void declaredProblemsWithHandWrittenErrorResponse_failSpecGeneration() {
    ApiResponses handWritten =
        new ApiResponses().addApiResponse("404", new ApiResponse().description("Not found"));

    assertThatThrownBy(() -> customize("declaredConflicts", handWritten))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("404");
  }

  @Test
  void handWrittenErrorResponse_withoutDeclaredProblems_listsOnlyTheFirstCatalogTypeOfItsStatus() {
    ApiResponses handWritten =
        new ApiResponses().addApiResponse("409", new ApiResponse().description("Conflict"));

    ApiResponse conflict = customize("noParameters", handWritten).get("409");

    assertThat(conflict.getDescription()).isEqualTo("Conflict");
    assertThat(exampleRefs(conflict)).containsOnlyKeys("UNIQUE_CONSTRAINT_VIOLATION");
  }

  @Test
  void handWrittenErrorResponse_withoutDeclaredProblems_keepsTheImplicitTypesOfItsStatus() {
    ApiResponses handWritten =
        new ApiResponses().addApiResponse("400", new ApiResponse().description("Bad request"));

    ApiResponse badRequest = customize("validatedBody", handWritten).get("400");

    assertThat(badRequest.getDescription()).isEqualTo("Bad request");
    assertThat(exampleRefs(badRequest)).containsOnlyKeys("MALFORMED_REQUEST", "VALIDATION_FAILED");
  }

  private ApiResponses customize(String fixtureMethod, ApiResponses responses) {
    HandlerMethod handlerMethod =
        Arrays.stream(Fixture.class.getDeclaredMethods())
            .filter(method -> method.getName().equals(fixtureMethod))
            .findFirst()
            .map(method -> new HandlerMethod(new Fixture(), method))
            .orElseThrow();
    return customizer.customize(new Operation().responses(responses), handlerMethod).getResponses();
  }

  private static Map<String, String> exampleRefs(ApiResponse response) {
    Map<String, Example> examples =
        response.getContent().get(ProblemResponsesCustomizer.PROBLEM_JSON).getExamples();
    return examples.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get$ref()));
  }

  @SuppressWarnings("unused")
  static class Fixture {

    @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE, ProblemType.SAGA_IN_FLIGHT})
    void declaredConflicts() {}

    void pathVariable(@PathVariable UUID id) {}

    void body(@RequestBody Object body) {}

    void validatedBody(@Valid @RequestBody Object body) {}

    void noParameters() {}
  }
}
