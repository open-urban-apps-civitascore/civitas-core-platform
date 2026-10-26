package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.problem.ProblemType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String PROBLEM_DETAIL_SCHEMA = "#/components/schemas/ProblemDetail";
  private static final String EXAMPLES_REF = "#/components/examples/";
  private static final List<String> OPERATION_METHODS =
      List.of("get", "put", "post", "patch", "delete");
  private static final Pattern TICKET_NUMBER = Pattern.compile("(?<![\\w&])[#!]\\d+\\b");

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
    "/datasets/{dataSetId}/released/meta, name",
    "/datasets/{dataSetId}/ready/meta, openDataAccess",
    "/datasources/{dataSourceId}/released/meta, datapoolScope",
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
    }
    assertThat(connectorTypes).containsExactlyInAnyOrder("MQTT", "SQL", "HTTP");
  }

  @ParameterizedTest
  @CsvSource({
    "MqttConnector, password",
    "SqlConnector, password",
    "HttpConnectorBasicAuthentication, password",
    "HttpConnectorBearerTokenAuthentication, token",
    "HttpConnectorApiTokenAuthentication, value",
    "HttpConnectorOAuth2Authentication, clientSecret",
    "HttpConnectorClientCertificateAuthentication, certificate",
    "HttpConnectorClientCertificateAuthentication, privateKey"
  })
  @DisplayName("Should describe each connector secret as write-only")
  void shouldDescribeSecretAsWriteOnly(String schema, String secret) {
    JsonNode secretProperty = propertiesOf(component(schema)).path(secret);

    assertThat(secretProperty.path("writeOnly").asBoolean()).isTrue();
    assertThat(secretProperty.path("format").asString()).isEqualTo("password");
  }

  @Test
  @DisplayName("Should export each HTTP/REST connector pattern as a portable, anchored expression")
  void shouldExportHttpConnectorPatternsAnchoredWithoutInlineFlags() {
    List<String> patterns = new ArrayList<>();
    spec.path("components").path("schemas").properties().stream()
        .filter(schema -> schema.getKey().startsWith("HttpConnector"))
        .forEach(schema -> collectPatterns(schema.getValue(), patterns));

    // A JSON Schema pattern matches anywhere in the value, and JavaScript knows no (?i).
    assertThat(patterns)
        .isNotEmpty()
        .allSatisfy(
            pattern -> assertThat(pattern).startsWith("^").endsWith("$").doesNotContain("(?"));
  }

  @Test
  @DisplayName("Should describe the method of an HTTP/REST API call as read-only")
  void shouldDescribeHttpMethodAsReadOnly() {
    assertThat(
            propertiesOf(component("HttpConnectorApiCall"))
                .path("method")
                .path("readOnly")
                .asBoolean())
        .isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "MqttConnector",
    "SqlConnector",
    "HttpConnector",
    "HttpConnectorApiCall",
    "HttpConnectorParameter",
    "HttpConnectorNoAuthentication",
    "HttpConnectorBasicAuthentication",
    "HttpConnectorBearerTokenAuthentication",
    "HttpConnectorApiTokenAuthentication",
    "HttpConnectorOAuth2Authentication",
    "HttpConnectorClientCertificateAuthentication",
    "HttpConnectorPageNumberPagination",
    "HttpConnectorLimitOffsetPagination",
    "HttpConnectorNextLinkPagination",
    "HttpConnectorCursorPagination"
  })
  @DisplayName("Should describe every connector field")
  void shouldDescribeEveryConnectorField(String schema) {
    List<String> undescribed = new ArrayList<>();
    propertiesOf(component(schema))
        .properties()
        .forEach(
            property -> {
              if (!Set.of("connectorType", "type").contains(property.getKey())
                  && property.getValue().path("description").asString().isBlank()) {
                undescribed.add(property.getKey());
              }
            });

    assertThat(component(schema).path("description").asString()).isNotBlank();
    assertThat(undescribed).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "HttpConnector, authentication, HttpConnectorAuthentication,"
        + " NONE=HttpConnectorNoAuthentication BASIC=HttpConnectorBasicAuthentication"
        + " BEARER_TOKEN=HttpConnectorBearerTokenAuthentication"
        + " API_TOKEN=HttpConnectorApiTokenAuthentication"
        + " OAUTH2=HttpConnectorOAuth2Authentication"
        + " CLIENT_CERTIFICATE=HttpConnectorClientCertificateAuthentication",
    "HttpConnectorApiCall, pagination, HttpConnectorPagination,"
        + " PAGE_NUMBER=HttpConnectorPageNumberPagination"
        + " LIMIT_OFFSET=HttpConnectorLimitOffsetPagination"
        + " CURSOR=HttpConnectorCursorPagination"
        + " NEXT_LINK=HttpConnectorNextLinkPagination"
  })
  @DisplayName(
      "Should describe each nested HTTP/REST variant as oneOf with a type discriminator,"
          + " each variant restricting type to its own value")
  void shouldDescribeNestedHttpVariantsAsOneOf(
      String schema, String property, String base, String variants) {
    Map<String, String> expected =
        Arrays.stream(variants.split(" "))
            .map(variant -> variant.split("="))
            .collect(Collectors.toMap(v -> v[0], v -> "#/components/schemas/" + v[1]));
    JsonNode discriminator = component(base).path("discriminator");
    Map<String, String> mapping = new HashMap<>();
    discriminator
        .path("mapping")
        .properties()
        .forEach(e -> mapping.put(e.getKey(), e.getValue().asString()));
    List<String> oneOf = new ArrayList<>();
    Map<String, String> variantByType = new HashMap<>();
    for (JsonNode variant : propertiesOf(component(schema)).path(property).path("oneOf")) {
      String ref = variant.path("$ref").asString();
      oneOf.add(ref);
      propertiesOf(schemaOf(variant))
          .path("type")
          .path("enum")
          .forEach(type -> variantByType.merge(type.asString(), ref, (a, b) -> a + " " + b));
    }

    assertThat(discriminator.path("propertyName").asString()).isEqualTo("type");
    assertThat(mapping).containsExactlyInAnyOrderEntriesOf(expected);
    assertThat(oneOf)
        .as("%s.%s oneOf", schema, property)
        .containsExactlyInAnyOrderElementsOf(expected.values());
    assertThat(variantByType)
        .as("%s.%s variant of each type value", schema, property)
        .containsExactlyInAnyOrderEntriesOf(expected);
  }

  @ParameterizedTest
  @CsvSource({"DataSourceInputDTO", "DataSourceOutputDTO"})
  @DisplayName("Should map each connectorType to the schema of its connector")
  void shouldMapEachConnectorTypeToItsSchema(String dto) {
    JsonNode discriminator =
        schemaOf(
                spec.path("components")
                    .path("schemas")
                    .path(dto)
                    .path("properties")
                    .path("connector"))
            .path("discriminator");

    assertThat(discriminator.path("propertyName").asString()).isEqualTo("connectorType");
    assertThat(discriminator.path("mapping").propertyNames())
        .containsExactlyInAnyOrder("MQTT", "SQL", "HTTP");
  }

  @Test
  void everyOperation_documentsExactlyOneSuccessResponse() {
    List<String> offenders = new ArrayList<>();
    forEachOperation(
        (name, operation) -> {
          long successes =
              operation.path("responses").propertyNames().stream()
                  .filter(code -> code.startsWith("2"))
                  .count();
          if (successes != 1) {
            offenders.add(name + " documents " + successes + " 2xx responses");
          }
        });

    assertThat(offenders).isEmpty();
  }

  @Test
  void componentExamples_coverEveryCatalogTypeWithItsUrnAndStatus() {
    JsonNode examples = spec.path("components").path("examples");

    assertThat(ProblemType.values())
        .allSatisfy(
            type -> {
              JsonNode example = examples.path(type.name()).path("value");
              assertThat(example.path("type").asString())
                  .as(type.name())
                  .isEqualTo(type.type().toString());
              assertThat(example.path("status").asInt())
                  .as(type.name())
                  .isEqualTo(type.status().value());
              assertThat(example.path("title").asString()).as(type.name()).isNotBlank();
              assertThat(example.path("detail").asString()).as(type.name()).isNotBlank();
            });
  }

  @Test
  void everyErrorResponse_usesProblemJsonAndOnlyCatalogExamples() {
    List<String> offenders = new ArrayList<>();
    forEachOperation(
        (name, operation) ->
            operation
                .path("responses")
                .properties()
                .forEach(
                    response -> {
                      String code = response.getKey();
                      if (code.startsWith("4") || code.startsWith("5")) {
                        collectNonCatalogContent(
                            name + " " + code, code, responseOf(response.getValue()), offenders);
                      }
                    }));

    assertThat(offenders).isEmpty();
  }

  @Test
  void layerItemOperations_documentExactlyTheirDeclaredAndImplicitStatuses() {
    JsonNode getLayer =
        spec.path("paths").path("/datasets/{dataSetId}/layers/{layerId}").path("get");

    assertThat(getLayer.path("responses").propertyNames())
        .containsExactlyInAnyOrder("200", "400", "401", "403", "404", "500");
    assertThat(
            responseOf(getLayer.path("responses").path("404"))
                .path("content")
                .path(PROBLEM_JSON)
                .path("examples")
                .propertyNames())
        .containsExactly("NOT_FOUND");
  }

  private JsonNode responseOf(JsonNode responseOrRef) {
    JsonNode ref = responseOrRef.path("$ref");
    return ref.isString() ? resolve(ref.asString()) : responseOrRef;
  }

  private void collectNonCatalogContent(
      String location, String code, JsonNode response, List<String> offenders) {
    JsonNode content = response.path("content");
    if (!new HashSet<>(content.propertyNames()).equals(Set.of(PROBLEM_JSON))) {
      offenders.add(location + " has content " + content.propertyNames());
      return;
    }
    JsonNode problemJson = content.path(PROBLEM_JSON);
    if (!problemJson.path("schema").path("$ref").asString().equals(PROBLEM_DETAIL_SCHEMA)) {
      offenders.add(location + " has no ProblemDetail schema");
    }
    if (problemJson.path("examples").isEmpty()) {
      offenders.add(location + " has no example");
    }
    problemJson
        .path("examples")
        .values()
        .forEach(
            example -> {
              JsonNode value = resolve(example.path("$ref").asString()).path("value");
              if (!example.path("$ref").asString().startsWith(EXAMPLES_REF)
                  || !String.valueOf(value.path("status").asInt()).equals(code)) {
                offenders.add(location + " has the example " + example);
              }
            });
  }

  @ParameterizedTest
  @CsvSource({
    "/assignments, roleId, string, uuid",
    "/datasets, datapoolIds, array, uuid",
    "/datasets/{dataSetId}/layers, createdAtFrom, string, date-time"
  })
  void listOperations_documentTheFiltersOfTheirSpec(
      String path, String name, String type, String format) {
    JsonNode parameter =
        spec.path("paths").path(path).path("get").path("parameters").values().stream()
            .filter(candidate -> candidate.path("name").asString().equals(name))
            .findFirst()
            .orElseThrow();
    JsonNode schema = parameter.path("schema");

    assertThat(parameter.path("in").asString()).isEqualTo("query");
    assertThat(parameter.path("description").asString()).isNotBlank();
    assertThat(schema.path("type").asString()).isEqualTo(type);
    assertThat((type.equals("array") ? schema.path("items") : schema).path("format").asString())
        .isEqualTo(format);
  }

  @Test
  void operationIds_areUniqueWithoutNumericSuffix() {
    List<String> operationIds = new ArrayList<>();
    forEachOperation(
        (name, operation) -> operationIds.add(operation.path("operationId").asString()));

    assertThat(operationIds).doesNotHaveDuplicates().noneMatch(id -> id.matches(".*_\\d+$"));
  }

  @Test
  void everyParameter_hasAType() {
    List<String> untyped = new ArrayList<>();
    spec.path("paths")
        .properties()
        .forEach(
            path -> {
              collectUntypedParameters(path.getKey(), path.getValue(), untyped);
              for (String method : OPERATION_METHODS) {
                collectUntypedParameters(
                    method + " " + path.getKey(), path.getValue().path(method), untyped);
              }
            });

    assertThat(untyped).isEmpty();
  }

  @Test
  void descriptionsAndSummaries_containNoTicketNumbers() {
    List<String> withTicketNumbers = new ArrayList<>();
    collectTicketNumbers(spec, withTicketNumbers);

    assertThat(withTicketNumbers).isEmpty();
  }

  private void forEachOperation(BiConsumer<String, JsonNode> action) {
    spec.path("paths")
        .properties()
        .forEach(
            path -> {
              for (String method : OPERATION_METHODS) {
                JsonNode operation = path.getValue().path(method);
                if (!operation.isMissingNode()) {
                  action.accept(method.toUpperCase() + " " + path.getKey(), operation);
                }
              }
            });
  }

  private static void collectUntypedParameters(
      String location, JsonNode pathItemOrOperation, List<String> untyped) {
    for (JsonNode parameter : pathItemOrOperation.path("parameters")) {
      JsonNode schema = parameter.path("schema");
      if (!schema.has("type") && !schema.has("$ref")) {
        untyped.add(location + " " + parameter.path("name").asString());
      }
    }
  }

  private static void collectTicketNumbers(JsonNode node, List<String> withTicketNumbers) {
    if (node.isObject()) {
      for (String field : List.of("description", "summary")) {
        JsonNode text = node.get(field);
        if (text != null && text.isString() && TICKET_NUMBER.matcher(text.asString()).find()) {
          withTicketNumbers.add(text.asString());
        }
      }
    }
    node.values().forEach(child -> collectTicketNumbers(child, withTicketNumbers));
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

  private JsonNode component(String name) {
    JsonNode schema = spec.path("components").path("schemas").path(name);
    assertThat(schema.isMissingNode()).as("schema %s", name).isFalse();
    return schema;
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

  private static void collectPatterns(JsonNode node, List<String> patterns) {
    if (node.isObject()) {
      JsonNode pattern = node.get("pattern");
      if (pattern != null && pattern.isString()) {
        patterns.add(pattern.asString());
      }
    }
    node.values().forEach(child -> collectPatterns(child, patterns));
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
