package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  @Test
  @DisplayName("adds the saga-in-flight Problem Detail example to 409 responses")
  void addsSagaInFlightExample() {
    MediaType problemMediaType = new MediaType();
    ApiResponse conflict =
        new ApiResponse()
            .content(new Content().addMediaType("application/problem+json", problemMediaType));
    OpenAPI openApi =
        new OpenAPI()
            .paths(
                new Paths()
                    .addPathItem(
                        "/v1/datasets/{id}/ready/meta",
                        new PathItem()
                            .put(
                                new Operation()
                                    .responses(
                                        new ApiResponses().addApiResponse("409", conflict)))));
    OpenApiConfig config =
        new OpenApiConfig(new KeycloakProperties("iot", "http://keycloak", "iot", true, false));

    config.problemDetailExamplesCustomizer().customise(openApi);

    Example example = problemMediaType.getExamples().get("saga_in_flight");
    assertThat(example).isNotNull();
    assertThat(example.getValue())
        .asInstanceOf(MAP)
        .containsEntry("type", "urn:civitas:error:SAGA_IN_FLIGHT")
        .containsEntry("status", 409)
        .containsEntry("pendingSagaType", "CREATE")
        .containsEntry("instance", "/v1/datasets/{id}/ready/meta");
  }
}
