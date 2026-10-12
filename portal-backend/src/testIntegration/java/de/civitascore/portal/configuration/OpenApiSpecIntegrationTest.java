package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

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

  @ParameterizedTest
  @CsvSource({"DataSourceInputDTO", "DataSourceOutputDTO"})
  @DisplayName("Should describe the connector as oneOf its types, each with its own connectorType")
  void shouldDescribeConnectorAsOneOfItsTypes(String dto) {
    JsonNode connector =
        schemaOf(
            spec.path("components").path("schemas").path(dto).path("properties").path("connector"));

    List<String> connectorTypes = new ArrayList<>();
    for (JsonNode variant : connector.path("oneOf")) {
      JsonNode variantSchema = schemaOf(variant);
      JsonNode properties = propertiesOf(variantSchema);
      properties.path("connectorType").path("enum").forEach(t -> connectorTypes.add(t.asString()));
      assertThat(variantSchema.path("required").values())
          .as("%s requires connectorType", variant)
          .extracting(JsonNode::asString)
          .contains("connectorType");
      assertThat(properties.path("user").path("description").asString()).isNotBlank();
      assertThat(properties.path("password").path("writeOnly").asBoolean())
          .as("%s password is write-only", variant)
          .isTrue();
    }
    assertThat(connectorTypes).containsExactlyInAnyOrder("MQTT", "SQL");
  }

  @Test
  @DisplayName("Should map each connectorType to the schema of its connector")
  void shouldMapEachConnectorTypeToItsSchema() {
    JsonNode discriminator =
        resolve("#/components/schemas/ConnectorConfiguration").path("discriminator");

    assertThat(discriminator.path("propertyName").asString()).isEqualTo("connectorType");
    assertThat(discriminator.path("mapping").propertyNames())
        .containsExactlyInAnyOrder("MQTT", "SQL");
  }

  /** Without this test, a JSON Schema validator would recurse without end on such a schema. */
  @Test
  @DisplayName("Should not let a schema contain itself through allOf, oneOf or anyOf")
  void shouldNotComposeASchemaWithItself() {
    List<String> selfComposed = new ArrayList<>();
    for (String name : spec.path("components").path("schemas").propertyNames()) {
      String ref = "#/components/schemas/" + name;
      if (composes(ref, ref, new HashSet<>())) {
        selfComposed.add(name);
      }
    }

    assertThat(selfComposed).isEmpty();
  }

  private boolean composes(String ref, String target, Set<String> seen) {
    if (!seen.add(ref)) {
      return false;
    }
    JsonNode schema = resolve(ref);
    for (String keyword : List.of("allOf", "oneOf", "anyOf")) {
      for (JsonNode part : schema.path(keyword)) {
        JsonNode partRef = part.path("$ref");
        if (partRef.isString()
            && (partRef.asString().equals(target) || composes(partRef.asString(), target, seen))) {
          return true;
        }
      }
    }
    return false;
  }

  /** The properties of a schema, including those of the schemas it is {@code allOf}. */
  private ObjectNode propertiesOf(JsonNode schema) {
    ObjectNode properties = JsonNodeFactory.instance.objectNode();
    for (JsonNode part : schema.path("allOf")) {
      properties.setAll(propertiesOf(schemaOf(part)));
    }
    if (schema.path("properties") instanceof ObjectNode own) {
      properties.setAll(own);
    }
    return properties;
  }

  private JsonNode schemaOf(JsonNode schemaOrRef) {
    JsonNode ref = schemaOrRef.path("$ref");
    return ref.isString() ? resolve(ref.asString()) : schemaOrRef;
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
