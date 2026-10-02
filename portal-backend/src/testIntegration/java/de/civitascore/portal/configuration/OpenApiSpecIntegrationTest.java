package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

@DisplayName("OpenAPI Spec Integration Tests")
class OpenApiSpecIntegrationTest extends BaseKeycloakIntegrationTest {

  private JsonNode spec;

  @BeforeEach
  void fetchSpec() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api-docs", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    spec = response.getBody();
  }

  @Test
  @DisplayName("Should resolve every schema reference to a component")
  void shouldResolveEverySchemaReference() {
    List<String> dangling = new ArrayList<>();
    collectDanglingReferences(spec, dangling);

    assertThat(dangling).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "/datasets/{id}/released/meta, name",
    "/datasets/{id}/ready/meta, openDataAccess",
    "/datasources/{id}/released/meta, datapoolScope",
    "/datastructures/{id}/released/meta, name",
    "/datastructures/{dataStructureId}/versions/{versionId}/released/meta, description"
  })
  @DisplayName("Should describe the metadata patch body with only optional metadata fields")
  void shouldDescribeMetadataPatchBody(String path, String metadataField) {
    JsonNode schema = requestBodySchema(path);

    assertThat(schema.path("properties").has(metadataField)).isTrue();
    assertThat(schema.path("properties").has("namedApis")).isFalse();
    assertThat(schema.path("properties").has("connectorType")).isFalse();
    assertThat(schema.path("properties").has("model")).isFalse();
    assertThat(schema.has("required")).isFalse();
  }

  private JsonNode requestBodySchema(String path) {
    JsonNode ref =
        spec.path("paths")
            .path(path)
            .path("patch")
            .path("requestBody")
            .path("content")
            .path("application/json")
            .path("schema")
            .path("$ref");
    assertThat(ref.isString()).as("request body $ref of PATCH %s", path).isTrue();
    return resolve(ref.asString());
  }

  private JsonNode resolve(String ref) {
    JsonNode node = spec;
    for (String segment : ref.substring(2).split("/")) {
      node = node.path(segment);
    }
    return node;
  }

  private void collectDanglingReferences(JsonNode node, List<String> dangling) {
    if (node.isObject()) {
      JsonNode ref = node.get("$ref");
      if (ref != null && ref.isString() && resolve(ref.asString()).isMissingNode()) {
        dangling.add(ref.asString());
      }
      node.values().forEach(child -> collectDanglingReferences(child, dangling));
    } else if (node.isArray()) {
      node.values().forEach(child -> collectDanglingReferences(child, dangling));
    }
  }
}
