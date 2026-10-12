package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.connector.Secret;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.RestPage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("DataSource Controller Integration Tests")
class DataSourceControllerIntegrationTest
    extends BaseDataEntityControllerIntegrationTest<DataSourceInputDTO, DataSourceOutputDTO> {

  private static final String DATASOURCES_ENDPOINT = "/datasources";
  private static final MqttConnectorConfiguration EMPTY_MQTT =
      new MqttConnectorConfiguration(null, null, null, null, null, null, null, null, null);
  private static final Secret PLACEHOLDER = Secret.of(Secret.PLACEHOLDER);

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private ModelRegistryGateway modelRegistryGateway;

  @Override
  protected String getEndpointPath() {
    return DATASOURCES_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  private UUID createAvailableDataStructureVersionId() {
    var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
    DataStructureVersion dsv =
        portalData.dataStructureVersion(
            ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
    return dsv.getId();
  }

  @Override
  protected DataSourceInputDTO createValidInput() {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName("test_datasource_" + UUID.randomUUID().toString().substring(0, 8));
    input.setDescription("A test data source for integration testing");
    input.setConnector(
        new MqttConnectorConfiguration(
            List.of("tcp://broker:1883"),
            List.of("sensor/data"),
            1,
            null,
            null,
            null,
            null,
            null,
            null));
    return input;
  }

  @Override
  protected DataSourceInputDTO createInvalidInput() {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setDescription("Invalid data source without required fields");
    return input;
  }

  @Override
  protected DataSourceInputDTO createUpdateInput() {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName("updated_datasource");
    input.setDescription("Updated description");
    input.setConnector(EMPTY_MQTT);
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DataSourceOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataSourceOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataSourceOutputDTO output) {
    return output.getId();
  }

  private UUID createReleasableTestEntity() {
    UUID dsvId = createAvailableDataStructureVersionId();
    DataSourceInputDTO input = createValidInput();
    input.setDataStructureVersionId(dsvId);
    ResponseEntity<DataSourceOutputDTO> response = performCreate(input);
    return response.getBody().getId();
  }

  private UUID createReleasableSqlTestEntity() {
    return createWithConnector(sqlConnector(), createAvailableDataStructureVersionId())
        .getBody()
        .getId();
  }

  /** The connector as JSON, because a {@link Secret} serializes to its placeholder. */
  private static Map<String, Object> connector(String connectorType, Object... keysAndValues) {
    Map<String, Object> connector = new LinkedHashMap<>();
    connector.put("connectorType", connectorType);
    for (int i = 0; i < keysAndValues.length; i += 2) {
      connector.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return connector;
  }

  private static Map<String, Object> sqlConnector() {
    return connector(
        "SQL",
        "driver",
        "postgres",
        "dsn",
        "postgres://localhost:5432/db",
        "table",
        "measurements",
        "columns",
        List.of("id", "value", "timestamp"),
        "user",
        "admin",
        "password",
        "secret");
  }

  private static Map<String, Object> mqttConnector(Object... extraKeysAndValues) {
    List<Object> keysAndValues =
        new ArrayList<>(
            List.of(
                "urls", List.of("tcp://broker:1883"), "topics", List.of("sensor/data"), "qos", 1));
    keysAndValues.addAll(Arrays.asList(extraKeysAndValues));
    return connector("MQTT", keysAndValues.toArray());
  }

  private Map<String, Object> body(Map<String, Object> connector, UUID dataStructureVersionId) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "datasource_" + UUID.randomUUID().toString().substring(0, 8));
    body.put("description", "a description");
    body.put("connector", connector);
    body.put("dataStructureVersionId", dataStructureVersionId);
    return body;
  }

  private ResponseEntity<DataSourceOutputDTO> createWithConnector(
      Map<String, Object> connector, UUID dataStructureVersionId) {
    ResponseEntity<DataSourceOutputDTO> response =
        exchange(
            DATASOURCES_ENDPOINT,
            HttpMethod.POST,
            createAuthHeaders(),
            body(connector, dataStructureVersionId),
            getOutputTypeReference());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response;
  }

  private UUID createWithConnector(Map<String, Object> connector) {
    return createWithConnector(connector, null).getBody().getId();
  }

  private ResponseEntity<ProblemDetail> writeExpectingProblem(
      HttpMethod method, String path, Object body) {
    return exchangeForProblem(DATASOURCES_ENDPOINT + path, method, createAuthHeaders(), body);
  }

  private <T extends ConnectorConfiguration> T connectorOf(UUID id, Class<T> type) {
    return type.cast(performGetById(id).getBody().getConnector());
  }

  /** The registry document, which follows {@code datasource.schema.json}. */
  private Map<String, Object> storedDocument(UUID id) {
    return modelRegistryGateway
        .fetchPayload(dataSourceRepository.findById(id).orElseThrow().getConfigurationUrn())
        .map(ModelRegistryGateway.RegistryDocument::content)
        .orElseThrow();
  }

  private ResponseEntity<DataSourceOutputDTO> performRelease(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/release";
    return exchange(url, HttpMethod.POST, createAuthHeaders(), null, getOutputTypeReference());
  }

  private ResponseEntity<DataSourceOutputDTO> performUnrelease(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/unrelease";
    return exchange(url, HttpMethod.POST, createAuthHeaders(), null, getOutputTypeReference());
  }

  private ResponseEntity<String> performUnreleaseExpectingError(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/unrelease";
    return restTemplate.exchange(
        url, HttpMethod.POST, new HttpEntity<>(createAuthHeaders()), String.class);
  }

  private DataSource createAvailableDataSource() {
    UUID id = createReleasableTestEntity();
    performRelease(id);
    return dataSourceRepository.findById(id).orElseThrow();
  }

  private void linkDataSourceToDataSetViaStatus(DataSource dataSource, DataSetStatus status) {
    DataSet dataSet = portalData.dataSet(b -> b.dataSetStatus(status));
    portalData.pipeline(dataSet, b -> b.dataSources(Set.of(dataSource)));
  }

  private ResponseEntity<String> performReleaseExpectingError(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/release";
    return restTemplate.exchange(
        url, HttpMethod.POST, new HttpEntity<>(createAuthHeaders()), String.class);
  }

  private ResponseEntity<String> performGetByIdExpectingError(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id;
    return restTemplate.exchange(
        url, HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);
  }

  private UUID createDraftDataStructureVersionId() {
    var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
    DataStructureVersion dsv =
        portalData.dataStructureVersion(
            ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.DRAFT));
    return dsv.getId();
  }

  private UUID createDsvWithDraftParentDataStructure() {
    var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.DRAFT));
    DataStructureVersion dsv =
        portalData.dataStructureVersion(
            ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
    return dsv.getId();
  }

  @Nested
  @DisplayName("Create DataSource Tests")
  class CreateTests {

    @Test
    @DisplayName("Should create data source in DRAFT status")
    void shouldCreateDataSourceInDraftStatus() {
      DataSourceInputDTO input = createValidInput();

      ResponseEntity<DataSourceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();

      DataSourceOutputDTO output = response.getBody();
      assertThat(output.getId()).isNotNull();
      assertThat(output.getName()).isEqualTo(input.getName());
      assertThat(output.getDescription()).isEqualTo(input.getDescription());
      assertThat(output.getDataSourceStatus()).isEqualTo(DataSourceStatus.DRAFT);
      assertThat(output.getConnector().connectorType()).isEqualTo(ConnectorType.MQTT);
      assertThat(output.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should fail to create data source with missing name")
    void shouldFailToCreateWithMissingName() {
      DataSourceInputDTO input = createInvalidInput();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create data source with blank description")
    void shouldFailToCreateWithBlankDescription() {
      DataSourceInputDTO input = createValidInput();
      input.setDescription("  ");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create data source without authentication")
    void shouldFailToCreateWithoutAuth() {
      ResponseEntity<String> response = performRequestWithoutAuth("", HttpMethod.POST);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void create_withSqlConnector_returnsConnectorWithMaskedPassword() {
      DataSourceOutputDTO output = createWithConnector(sqlConnector(), null).getBody();

      assertThat(output.getConnector())
          .isEqualTo(
              new SqlConnectorConfiguration(
                  "postgres",
                  "postgres://localhost:5432/db",
                  "measurements",
                  List.of("id", "value", "timestamp"),
                  null,
                  "admin",
                  PLACEHOLDER));
    }

    @Test
    void create_withPassword_storesItEncryptedInTheRegistry() {
      UUID id = createWithConnector(sqlConnector());

      Map<String, Object> document = storedDocument(id);
      assertThat(document)
          .containsEntry("connectionType", "sql")
          .doesNotContainKey("connectorType");
      assertThat((String) document.get("password")).startsWith("ENC(").doesNotContain("secret");
    }

    @Test
    @DisplayName("Should store the configuration under the data source's id")
    void shouldStoreTheConfigurationUnderTheDataSourceId() {
      UUID dataSourceId = createWithConnector(sqlConnector());

      assertThat(
              dataSourceRepository
                  .findById(dataSourceId)
                  .orElseThrow()
                  .getConfigurationLogicalUrn())
          .isEqualTo("urn:core:platform:civitas:datasource:common:" + dataSourceId);
    }

    @Test
    void create_withExplicitNullProtocolVersion_storesDefault() {
      UUID id = createWithConnector(mqttConnector("protocol_version", null));

      assertThat(connectorOf(id, MqttConnectorConfiguration.class).protocolVersion())
          .isEqualTo("3");
    }

    @Test
    void create_withValueOutOfRange_returnsFieldError() {
      ResponseEntity<ProblemDetail> response =
          writeExpectingProblem(HttpMethod.POST, "", body(mqttConnector("qos", 5), null));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getProperties().get("fieldErrors"))
          .isEqualTo(
              List.of(Map.of("field", "connector.qos", "message", "'qos' must be 0, 1, or 2")));
    }

    @Test
    void patch_withInvalidValue_returnsFieldErrorNamingTheRequestKey() {
      UUID id = createWithConnector(mqttConnector());

      ResponseEntity<ProblemDetail> response =
          writeExpectingProblem(
              HttpMethod.PATCH, "/" + id, Map.of("connector", Map.of("protocol_version", "4")));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:VALIDATION_FAILED");
      assertThat(response.getBody().getProperties().get("fieldErrors"))
          .isEqualTo(
              List.of(
                  Map.of(
                      "field",
                      "connector.protocol_version",
                      "message",
                      "'protocol_version' must be 3 or 5")));
    }
  }

  @Nested
  class MalformedConnectorTests {

    private void assertMalformed(ResponseEntity<ProblemDetail> response, String detail) {
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:MALFORMED_REQUEST");
      assertThat(response.getBody().getDetail()).isEqualTo(detail);
      assertThat(response.getBody().getInstance()).asString().contains(DATASOURCES_ENDPOINT);
    }

    @Test
    void create_withUnknownConnectorKey_returnsMalformedRequestNamingTheKey() {
      assertMalformed(
          writeExpectingProblem(
              HttpMethod.POST, "", body(mqttConnector("protocolVersion", "5"), null)),
          "Unknown key: connector.protocolVersion");
    }

    @Test
    void create_withUnknownNestedKey_returnsMalformedRequestNamingTheKey() {
      assertMalformed(
          writeExpectingProblem(
              HttpMethod.POST,
              "",
              body(mqttConnector("tls", Map.of("enabled", true, "verify", false)), null)),
          "Unknown key: connector.tls.verify");
    }

    @Test
    void create_withKeyOfThePreviousShape_returnsMalformedRequestNamingTheKey() {
      Map<String, Object> body = body(null, null);
      body.put("configuration", Map.of("urls", List.of("tcp://broker:1883")));

      assertMalformed(
          writeExpectingProblem(HttpMethod.POST, "", body), "Unknown key: configuration");
    }

    @Test
    void update_withUnknownTopLevelKey_returnsMalformedRequestNamingTheKey() {
      UUID id = createTestEntity();
      Map<String, Object> body = body(mqttConnector(), null);
      body.put("connectorType", "MQTT");

      assertMalformed(
          writeExpectingProblem(HttpMethod.PUT, "/" + id, body), "Unknown key: connectorType");
    }

    @Test
    void patch_withUnknownTopLevelKey_returnsMalformedRequestNamingTheKey() {
      UUID id = createTestEntity();

      assertMalformed(
          writeExpectingProblem(
              HttpMethod.PATCH, "/" + id, Map.of("dataSourceStatus", "AVAILABLE")),
          "Unknown key: dataSourceStatus");
    }

    @Test
    void update_withUnknownKey_returnsMalformedRequestNamingTheKey() {
      UUID id = createTestEntity();

      assertMalformed(
          writeExpectingProblem(HttpMethod.PUT, "/" + id, body(mqttConnector("broker", "x"), null)),
          "Unknown key: connector.broker");
    }

    @Test
    void patch_withUnknownKey_returnsMalformedRequestNamingTheKey() {
      UUID id = createTestEntity();

      assertMalformed(
          writeExpectingProblem(
              HttpMethod.PATCH, "/" + id, Map.of("connector", Map.of("tls", Map.of("verify", 1)))),
          "Unknown key: connector.tls.verify");
    }

    @Test
    void patch_withInvalidUrl_returnsMalformedRequestWithoutTheValue() {
      UUID id = createTestEntity();

      assertMalformed(
          writeExpectingProblem(
              HttpMethod.PATCH,
              "/" + id,
              Map.of("connector", Map.of("urls", List.of("tcp://user:pw@ no host")))),
          "Invalid value: connector.urls[0]");
    }

    @Test
    void patch_withUnknownConnectorType_returnsMalformedRequest() {
      UUID id = createTestEntity();

      assertMalformed(
          writeExpectingProblem(
              HttpMethod.PATCH, "/" + id, Map.of("connector", Map.of("connectorType", "HTTP"))),
          "Unknown connectorType: HTTP");
    }

    @Test
    void create_withUnknownConnectorType_returnsMalformedRequest() {
      assertMalformed(
          writeExpectingProblem(HttpMethod.POST, "", body(connector("FTP"), null)),
          "Unknown connectorType: FTP");
    }
  }

  @Nested
  @DisplayName("MQTT Blank Parameter Normalization Tests")
  class MqttBlankNormalizationTests {

    @Test
    @DisplayName("Should normalize blank optional MQTT strings to null on create")
    void shouldNormalizeBlankOptionalStringsOnCreate() {
      UUID id =
          createWithConnector(
              mqttConnector("connect_timeout", "   ", "keepalive", "", "user", " "));

      MqttConnectorConfiguration connector = connectorOf(id, MqttConnectorConfiguration.class);
      assertThat(connector.connectTimeout()).isNull();
      assertThat(connector.keepalive()).isNull();
      assertThat(connector.user()).isNull();
    }

    @Test
    @DisplayName("Should persist non-blank optional MQTT strings unchanged")
    void shouldPersistNonBlankOptionalStringsUnchanged() {
      UUID id =
          createWithConnector(
              mqttConnector("connect_timeout", "5s", "keepalive", "30s", "user", "mqttuser"));

      MqttConnectorConfiguration connector = connectorOf(id, MqttConnectorConfiguration.class);
      assertThat(connector.connectTimeout()).isEqualTo("5s");
      assertThat(connector.keepalive()).isEqualTo("30s");
      assertThat(connector.user()).isEqualTo("mqttuser");
    }
  }

  @Nested
  @DisplayName("Read DataSource Tests")
  class ReadTests {

    @Test
    @DisplayName("Should retrieve data source by ID")
    void shouldRetrieveDataSourceById() {
      UUID id = createTestEntity();

      ResponseEntity<DataSourceOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("Should return 404 for non-existent data source")
    void shouldReturn404ForNonExistent() {
      ResponseEntity<String> response = performGetByIdExpectingError(UUID.randomUUID());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should retrieve all data sources with pagination")
    void shouldRetrieveAllWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<DataSourceOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should filter data sources by status")
    void shouldFilterByStatus() {
      createTestEntity();

      ResponseEntity<RestPage<DataSourceOutputDTO>> response =
          performGetAll(Map.of("dataSourceStatus", "DRAFT"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent())
          .allMatch(ds -> ds.getDataSourceStatus() == DataSourceStatus.DRAFT);
    }
  }

  @Nested
  @DisplayName("Update DataSource Tests")
  class UpdateTests {

    @Test
    @DisplayName("Should partially update data source with PATCH")
    void shouldPartiallyUpdateWithPatch() {
      UUID id = createTestEntity();

      Map<String, Object> patchMap =
          Collections.singletonMap("description", "Updated description via PATCH");

      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isEqualTo("Updated description via PATCH");
    }

    @Test
    @DisplayName("Should reject PATCH that sets description to null")
    void shouldRejectSettingDescriptionToNullWithPatch() {
      UUID id = createTestEntity();

      Map<String, Object> patchMap = Collections.singletonMap("description", null);

      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patchMap);

      assertThat(response.getStatusCode())
          .as("Description is mandatory and cannot be cleared via PATCH")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("PUT should update data source")
    void putShouldUpdateDataSource() {
      UUID id = createTestEntity();
      DataSourceInputDTO input = createUpdateInput();

      ResponseEntity<DataSourceOutputDTO> response = performUpdate(id, input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("updated_datasource");
      assertThat(response.getBody().getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("Should leave omitted fields unchanged with PATCH")
    void shouldLeaveOmittedFieldsUnchangedWithPatch() {
      UUID id = createTestEntity();

      ResponseEntity<DataSourceOutputDTO> initial = performGetById(id);
      String originalName = initial.getBody().getName();

      Map<String, Object> patchMap = Map.of("description", "New description");
      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(originalName);
      assertThat(response.getBody().getDescription()).isEqualTo("New description");
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignments", "datapoolScope"})
    @DisplayName("Should reject an explicit null for a field that cannot hold null with PATCH")
    void shouldRejectExplicitNullWithPatch(String field) {
      UUID id = createTestEntity();
      Map<String, Object> patch = new HashMap<>();
      patch.put(field, null);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id, HttpMethod.PATCH, createAuthHeaders(), patch);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getDetail()).contains(field);
    }
  }

  @Nested
  @DisplayName("Release DataSource Tests")
  class ReleaseTests {

    @Test
    @DisplayName("Should release valid MQTT data source")
    void shouldReleaseValidMqttDataSource() {
      UUID id = createReleasableTestEntity();

      ResponseEntity<DataSourceOutputDTO> response = performRelease(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should release valid SQL data source")
    void shouldReleaseSqlDataSource() {
      UUID id = createReleasableSqlTestEntity();

      ResponseEntity<DataSourceOutputDTO> response = performRelease(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to release already AVAILABLE data source")
    void shouldFailToReleaseAvailableDataSource() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      ResponseEntity<String> response = performReleaseExpectingError(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void release_withIncompleteConnector_returnsFieldErrorsWithConnectorPaths() {
      UUID id =
          createWithConnector(connector("MQTT"), createAvailableDataStructureVersionId())
              .getBody()
              .getId();

      ResponseEntity<ProblemDetail> response =
          writeExpectingProblem(HttpMethod.POST, "/" + id + "/release", null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:VALIDATION_FAILED");
      List<?> fieldErrors = (List<?>) response.getBody().getProperties().get("fieldErrors");
      List<Object> fields =
          fieldErrors.stream().map(error -> (Object) ((Map<?, ?>) error).get("field")).toList();
      assertThat(fields).containsExactly("connector.qos", "connector.topics", "connector.urls");
    }

    @Test
    void release_withoutConnector_isRejected() {
      DataSourceInputDTO input = createValidInput();
      input.setConnector(null);
      input.setDataStructureVersionId(createAvailableDataStructureVersionId());
      UUID id = performCreate(input).getBody().getId();

      ResponseEntity<ProblemDetail> response =
          writeExpectingProblem(HttpMethod.POST, "/" + id + "/release", null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getDetail()).contains("Connector type");
    }

    @Test
    @DisplayName("Should fail to release non-existent data source")
    void shouldFailToReleaseNonExistent() {
      ResponseEntity<String> response = performReleaseExpectingError(UUID.randomUUID());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Delete DataSource Tests")
  class DeleteTests {

    @Test
    @DisplayName("Should delete DRAFT data source successfully")
    void shouldDeleteDraftDataSource() {
      UUID id = createTestEntity();

      ResponseEntity<Void> response = performDelete(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<String> getResponse = performGetByIdExpectingError(id);
      assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete AVAILABLE data source")
    void shouldFailToDeleteAvailableDataSource() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      ResponseEntity<Void> response = performDelete(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should refuse to delete a DRAFT data source that a Pipeline references")
    void shouldRefuseDeletingDraftDataSourceReferencedByPipeline() {
      UUID id = createTestEntity();
      linkDataSourceToDataSetViaStatus(
          dataSourceRepository.findById(id).orElseThrow(), DataSetStatus.DRAFT);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id, HttpMethod.DELETE, createAuthHeaders(), null);

      // The foreign key also answers 409, so only the problem type shows the guard ran before
      // postDelete dropped the Model Forge artifact.
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:RESOURCE_IN_USE");
      assertThat(dataSourceRepository.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("Should fail to delete non-existent data source")
    void shouldFailToDeleteNonExistent() {
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete data source without authentication")
    void shouldFailToDeleteWithoutAuth() {
      UUID id = createTestEntity();

      ResponseEntity<String> response = performRequestWithoutAuth("/" + id, HttpMethod.DELETE);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("PATCH Connector Tests")
  class PatchConnectorTests {

    private SqlConnectorConfiguration patchSql(UUID id, Map<String, Object> patch) {
      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patch);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      return (SqlConnectorConfiguration) response.getBody().getConnector();
    }

    @Test
    @DisplayName("PATCH non-connector fields should preserve the connector and its secret")
    void patchNonConnectorFieldsShouldPreserveConnector() {
      UUID id = createWithConnector(sqlConnector());
      Object storedPassword = storedDocument(id).get("password");

      SqlConnectorConfiguration connector =
          patchSql(id, Map.of("description", "updated description"));

      assertThat(connector.table()).isEqualTo("measurements");
      assertThat(connector.password()).isEqualTo(PLACEHOLDER);
      assertThat(storedDocument(id)).containsEntry("password", storedPassword);
    }

    @Test
    @DisplayName("PATCH of one connector field keeps the others and the stored secret")
    void patchOneConnectorFieldShouldPreserveOthers() {
      UUID id = createWithConnector(sqlConnector());
      Object storedPassword = storedDocument(id).get("password");

      SqlConnectorConfiguration connector =
          patchSql(id, Map.of("connector", Map.of("table", "new_table")));

      assertThat(connector.table()).isEqualTo("new_table");
      assertThat(connector.dsn()).isEqualTo("postgres://localhost:5432/db");
      assertThat(storedDocument(id)).containsEntry("password", storedPassword);
    }

    @Test
    @DisplayName("PATCH with new password should encrypt new credentials")
    void patchWithNewPasswordShouldEncrypt() {
      UUID id = createWithConnector(sqlConnector());
      Object storedPassword = storedDocument(id).get("password");

      SqlConnectorConfiguration connector =
          patchSql(id, Map.of("connector", Map.of("user", "newuser", "password", "newpass")));

      assertThat(connector.user()).isEqualTo("newuser");
      assertThat(connector.password()).isEqualTo(PLACEHOLDER);
      assertThat((String) storedDocument(id).get("password"))
          .startsWith("ENC(")
          .isNotEqualTo(storedPassword);
    }

    @Test
    void patch_withNullAndBlankValues_clearsFields() {
      UUID id = createWithConnector(sqlConnector());
      Map<String, Object> connectorPatch = new HashMap<>();
      connectorPatch.put("user", null);
      connectorPatch.put("table", "  ");
      connectorPatch.put("password", "");

      SqlConnectorConfiguration connector = patchSql(id, Map.of("connector", connectorPatch));

      assertThat(connector.user()).isNull();
      assertThat(connector.table()).isNull();
      assertThat(connector.password()).isNull();
      assertThat(storedDocument(id)).containsEntry("password", null);
    }

    @Test
    void patch_withOtherConnectorTypeOnDraft_replacesConnector() {
      UUID id = createWithConnector(sqlConnector());

      ResponseEntity<DataSourceOutputDTO> response =
          performPatch(id, Map.of("connector", mqttConnector()));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getConnector())
          .isEqualTo(
              new MqttConnectorConfiguration(
                  List.of("tcp://broker:1883"),
                  List.of("sensor/data"),
                  1,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null));
      assertThat(storedDocument(id))
          .containsEntry("connectionType", "mqtt")
          .containsEntry("password", null)
          .doesNotContainKey("dsn");
    }

    @Test
    void patch_withNullConnector_removesConnector() {
      UUID id = createWithConnector(sqlConnector());
      Map<String, Object> patch = new HashMap<>();
      patch.put("connector", null);

      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patch);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getConnector()).isNull();
    }

    @Test
    void update_withPlaceholderAndNoStoredPassword_storesNoPassword() {
      UUID id = createWithConnector(mqttConnector());
      Map<String, Object> put = body(mqttConnector("password", Secret.PLACEHOLDER), null);

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              DATASOURCES_ENDPOINT + "/" + id,
              HttpMethod.PUT,
              createAuthHeaders(),
              put,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(((MqttConnectorConfiguration) response.getBody().getConnector()).password())
          .isNull();
      assertThat(storedDocument(id)).containsEntry("password", null);
    }

    @Test
    @DisplayName("PATCH then release should work")
    void patchThenReleaseShouldWork() {
      UUID id = createReleasableSqlTestEntity();

      Map<String, Object> patch = Map.of("description", "Released via patch");
      ResponseEntity<DataSourceOutputDTO> patchResponse = performPatch(id, patch);

      assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patchResponse.getBody().getDescription()).isEqualTo("Released via patch");

      ResponseEntity<DataSourceOutputDTO> releaseResponse = performRelease(id);

      assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(releaseResponse.getBody().getDataSourceStatus())
          .isEqualTo(DataSourceStatus.AVAILABLE);
      assertThat(releaseResponse.getBody().getDescription()).isEqualTo("Released via patch");
    }
  }

  @Nested
  @DisplayName("Duplicate Name Tests")
  class DuplicateNameTests {

    // Name uniqueness on DataSource was removed (#1453) to close a create-oracle: read access
    // is scope-restricted but create access is global, so a uniqueness violation leaked the
    // existence of records the caller had no permission to read. Duplicate names must succeed.

    @Test
    @DisplayName("Should allow create with duplicate name")
    void shouldAllowCreateWithDuplicateName() {
      DataSourceInputDTO input = createValidInput();
      String name = input.getName();
      performCreate(input);

      DataSourceInputDTO duplicate = createValidInput();
      duplicate.setName(name);

      ResponseEntity<DataSourceOutputDTO> response = performCreate(duplicate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody().getName()).isEqualTo(name);
    }

    @Test
    @DisplayName("Should allow update with duplicate name")
    void shouldAllowUpdateWithDuplicateName() {
      DataSourceInputDTO first = createValidInput();
      String existingName = first.getName();
      performCreate(first);

      DataSourceInputDTO second = createValidInput();
      ResponseEntity<DataSourceOutputDTO> secondResponse = performCreate(second);
      UUID secondId = secondResponse.getBody().getId();

      DataSourceInputDTO update = createUpdateInput();
      update.setName(existingName);

      ResponseEntity<DataSourceOutputDTO> response = performUpdate(secondId, update);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo(existingName);
    }

    @Test
    @DisplayName("Should allow patch with duplicate name")
    void shouldAllowPatchWithDuplicateName() {
      DataSourceInputDTO first = createValidInput();
      String existingName = first.getName();
      performCreate(first);

      UUID secondId = createTestEntity();

      Map<String, Object> patch = Map.of("name", existingName);

      ResponseEntity<DataSourceOutputDTO> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + secondId,
              HttpMethod.PATCH,
              new HttpEntity<>(patch, createAuthHeaders()),
              DataSourceOutputDTO.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo(existingName);
    }

    @Test
    @DisplayName("Should allow released meta update with duplicate name")
    void shouldAllowPublishedMetaUpdateWithDuplicateName() {
      DataSourceInputDTO first = createValidInput();
      String existingName = first.getName();
      performCreate(first);

      UUID secondId = createReleasableTestEntity();
      performRelease(secondId);

      Map<String, Object> metaUpdate = Map.of("name", existingName, "description", "a description");

      ResponseEntity<DataSourceOutputDTO> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + secondId + "/released/meta",
              HttpMethod.PATCH,
              new HttpEntity<>(metaUpdate, createAuthHeaders()),
              DataSourceOutputDTO.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo(existingName);
    }

    @Test
    @DisplayName("Should allow updating own name to the same value")
    void shouldAllowUpdatingOwnName() {
      DataSourceInputDTO input = createValidInput();
      ResponseEntity<DataSourceOutputDTO> response = performCreate(input);
      UUID id = response.getBody().getId();

      DataSourceInputDTO update = createUpdateInput();
      update.setName(input.getName());

      ResponseEntity<DataSourceOutputDTO> updateResponse = performUpdate(id, update);

      assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  @Nested
  @DisplayName("DataStructureVersion Linking Tests")
  class DataStructureVersionLinkingTests {

    @Test
    @DisplayName("Should create data source with dataStructureVersionId")
    void shouldCreateWithDataStructureVersionId() {
      UUID dsvId = createAvailableDataStructureVersionId();
      DataSourceInputDTO input = createValidInput();
      input.setDataStructureVersionId(dsvId);

      ResponseEntity<DataSourceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody().getDataStructureVersion().getId()).isEqualTo(dsvId);
    }

    @Test
    @DisplayName("Should save a DRAFT DataSource with a DRAFT DataStructureVersion")
    void shouldSaveDraftDataSourceWithDraftDataStructureVersion() {
      UUID dsvId = createDraftDataStructureVersionId();
      DataSourceInputDTO input = createValidInput();
      input.setDataStructureVersionId(dsvId);

      ResponseEntity<DataSourceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody().getDataStructureVersion().getId()).isEqualTo(dsvId);
      assertThat(performReleaseExpectingError(response.getBody().getId()).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should save a DRAFT DataSource when the parent DataStructure is DRAFT")
    void shouldSaveDraftDataSourceWithDraftParentDataStructure() {
      UUID dsvId = createDsvWithDraftParentDataStructure();
      DataSourceInputDTO input = createValidInput();
      input.setDataStructureVersionId(dsvId);

      ResponseEntity<DataSourceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody().getDataStructureVersion().getId()).isEqualTo(dsvId);
      assertThat(performReleaseExpectingError(response.getBody().getId()).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject changing DSV on released data source via PUT")
    void shouldRejectChangingDsvWhenPublished() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      UUID newDsvId = createAvailableDataStructureVersionId();
      DataSourceInputDTO update = createUpdateInput();
      update.setDataStructureVersionId(newDsvId);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + id,
              HttpMethod.PUT,
              new HttpEntity<>(update, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject removing DSV from released data source via PUT")
    void shouldRejectRemovingDsvWhenPublished() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      DataSourceInputDTO update = createUpdateInput();
      update.setDataStructureVersionId(null);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + id,
              HttpMethod.PUT,
              new HttpEntity<>(update, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to release without DataStructureVersion")
    void shouldFailToReleaseWithoutDataStructureVersion() {
      UUID id = createTestEntity();

      ResponseEntity<String> response = performReleaseExpectingError(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Connector Masking Tests")
  class MaskingTests {

    private Map<String, Object> getRaw(UUID id) {
      return exchange(
              DATASOURCES_ENDPOINT + "/" + id,
              HttpMethod.GET,
              createAuthHeaders(),
              null,
              new ParameterizedTypeReference<Map<String, Object>>() {})
          .getBody();
    }

    @Test
    void get_withStoredPassword_showsPlaceholderAndNoOtherConnectorMember() {
      UUID id = createWithConnector(mqttConnector("user", "mqttuser", "password", "my-secret"));

      Map<String, Object> response = getRaw(id);

      assertThat(response.get("connector"))
          .asInstanceOf(InstanceOfAssertFactories.MAP)
          .containsEntry("connectorType", "MQTT")
          .containsEntry("user", "mqttuser")
          .containsEntry("password", Secret.PLACEHOLDER)
          .doesNotContainKey("connectionType");
    }

    @Test
    void get_withoutPassword_showsNull() {
      UUID id = createWithConnector(mqttConnector("password", ""));

      assertThat(getRaw(id).get("connector"))
          .asInstanceOf(InstanceOfAssertFactories.MAP)
          .containsEntry("password", null);
    }
  }

  @Nested
  @DisplayName("inUse Flag Tests")
  class InUseFlagTests {

    @Test
    @DisplayName("Should return inUse=false when DataSource is not referenced by any DataSet")
    void shouldReturnInUseFalseWhenNotReferenced() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      ResponseEntity<DataSourceOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().isInUse()).isFalse();
      assertThat(response.getBody().isInUseByReleased()).isFalse();
    }

    @Test
    @DisplayName(
        "Should return inUse=true when DataSource is referenced by a Pipeline in a DRAFT DataSet")
    void shouldReturnInUseTrueWhenReferencedByPipelineInDraftDataSet() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.DRAFT);

      ResponseEntity<DataSourceOutputDTO> response = performGetById(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().isInUse()).isTrue();
      assertThat(response.getBody().isInUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("Should return inUse=true when DataSource is referenced by a READY DataSet")
    void shouldReturnInUseTrueWhenReadyDataSetReferences() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.READY);

      ResponseEntity<DataSourceOutputDTO> response = performGetById(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().isInUse()).isTrue();
      assertThat(response.getBody().isInUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("Should return inUse=true when DataSource is referenced by an AVAILABLE DataSet")
    void shouldReturnInUseTrueWhenAvailableDataSetReferences() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.AVAILABLE);

      ResponseEntity<DataSourceOutputDTO> response = performGetById(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().isInUse()).isTrue();
      assertThat(response.getBody().isInUseByReleased()).isTrue();
    }
  }

  @Nested
  @DisplayName("Released Meta Update Tests")
  class ReleasedMetaTests {

    @Test
    @DisplayName("Should update name and description of AVAILABLE data source")
    void shouldUpdateNameAndDescription() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      Map<String, Object> metaUpdate =
          Map.of("name", "updated-name", "description", "updated-desc");

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + id + "/released/meta",
              HttpMethod.PATCH,
              createAuthHeaders(),
              metaUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo("updated-name");
      assertThat(response.getBody().getDescription()).isEqualTo("updated-desc");
    }

    @Test
    @DisplayName("Should not change configuration when not provided in released/meta update")
    void shouldNotChangeConfigurationWhenNotProvided() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      ResponseEntity<DataSourceOutputDTO> before =
          exchange(
              getEndpointPath() + "/" + id,
              HttpMethod.GET,
              createAuthHeaders(),
              null,
              getOutputTypeReference());
      var originalConnector = before.getBody().getConnector();

      Map<String, Object> metaUpdate =
          Map.of("name", "updated-name", "description", "a description");

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + id + "/released/meta",
              HttpMethod.PATCH,
              createAuthHeaders(),
              metaUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getConnector()).isEqualTo(originalConnector);
    }

    @Test
    @DisplayName("Should reject released/meta update on DRAFT data source")
    void shouldRejectUpdateOnDraftDataSource() {
      UUID id = createReleasableTestEntity();

      Map<String, Object> metaUpdate =
          Map.of("name", "updated-name", "description", "a description");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + id + "/released/meta",
              HttpMethod.PATCH,
              new HttpEntity<>(metaUpdate, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should update name and description when in use")
    void shouldUpdateNameAndDescriptionWhenInUse() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.AVAILABLE);

      Map<String, Object> metaUpdate =
          Map.of("name", "updated-name", "description", "updated-desc");

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSource.getId() + "/released/meta",
              HttpMethod.PATCH,
              createAuthHeaders(),
              metaUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo("updated-name");
      assertThat(response.getBody().getDescription()).isEqualTo("updated-desc");
    }

    @Test
    @DisplayName("Should replace assignments of AVAILABLE data source via released/meta")
    void shouldReplaceAssignmentsViaReleasedMeta() {
      UUID groupId1 = portalData.group().getId();
      UUID groupId2 = portalData.group().getId();
      UUID roleId = portalData.role().getId();

      AssignmentScopedInputDTO initialAssignment = new AssignmentScopedInputDTO();
      initialAssignment.setGroupId(groupId1);
      initialAssignment.setRoleId(roleId);

      DataSourceInputDTO input = createValidInput();
      input.setDataStructureVersionId(createAvailableDataStructureVersionId());
      input.setAssignments(Set.of(initialAssignment));
      UUID id = performCreate(input).getBody().getId();
      performRelease(id);

      AssignmentScopedInputDTO replacementAssignment = new AssignmentScopedInputDTO();
      replacementAssignment.setGroupId(groupId2);
      replacementAssignment.setRoleId(roleId);

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + id + "/released/meta",
              HttpMethod.PATCH,
              createAuthHeaders(),
              Map.of("assignments", Set.of(replacementAssignment)),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      ResponseEntity<List<AssignmentOutputDTO>> assignmentsResponse =
          restTemplate.exchange(
              getEndpointPath() + "/" + id + "/assignments",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              new ParameterizedTypeReference<>() {});

      assertThat(assignmentsResponse.getBody()).hasSize(1);
      assertThat(assignmentsResponse.getBody().getFirst().getGroup().getId()).isEqualTo(groupId2);
    }

    @Test
    @DisplayName(
        "Should reject clearing the description of AVAILABLE data source with an explicit null")
    void shouldRejectClearingDescriptionWithNull() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      Map<String, Object> metaUpdate = new HashMap<>();
      metaUpdate.put("description", null);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + id + "/released/meta",
              HttpMethod.PATCH,
              new HttpEntity<>(metaUpdate, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignments", "datapoolScope"})
    @DisplayName("Should reject an explicit null for a field that cannot hold null")
    void shouldRejectExplicitNullInMetaPatch(String field) {
      UUID id = createReleasableTestEntity();
      performRelease(id);
      Map<String, Object> metaUpdate = new HashMap<>();
      metaUpdate.put(field, null);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id + "/released/meta",
              HttpMethod.PATCH,
              createAuthHeaders(),
              metaUpdate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getDetail()).contains(field);
    }

    @Test
    @DisplayName("Should reject a field that the data source input does not have")
    void shouldRejectUnknownFieldInMetaPatch() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id + "/released/meta",
              HttpMethod.PATCH,
              createAuthHeaders(),
              Map.of("name", "updated-name", "dataSourceStatus", "DRAFT"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getDetail()).contains("dataSourceStatus");
      assertThat(performGetById(id).getBody().getName()).isNotEqualTo("updated-name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"connector", "dataStructureVersionId"})
    @DisplayName("Should reject a field fixed after release when no dataset uses the data source")
    void shouldRejectFieldFixedAfterReleaseWhenNotInUse(String field) {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      assertRejectedAndUnchanged(id, field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"connector", "dataStructureVersionId"})
    @DisplayName("Should reject a field fixed after release when an AVAILABLE dataset uses it")
    void shouldRejectFieldFixedAfterReleaseWhenInUse(String field) {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.AVAILABLE);

      assertRejectedAndUnchanged(dataSource.getId(), field);
    }

    private void assertRejectedAndUnchanged(UUID id, String field) {
      DataSourceOutputDTO before = performGetById(id).getBody();
      Map<String, Object> newValues =
          Map.of(
              "connector",
              Map.of("connectorType", "MQTT", "urls", List.of("tcp://new-broker:1883")),
              "dataStructureVersionId",
              createAvailableDataStructureVersionId());

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id + "/released/meta",
              HttpMethod.PATCH,
              createAuthHeaders(),
              Map.of("name", "updated-name", field, newValues.get(field)));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getDetail()).contains(field);
      DataSourceOutputDTO after = performGetById(id).getBody();
      assertThat(after.getName()).isEqualTo(before.getName());
      assertThat(after.getConnector()).isEqualTo(before.getConnector());
      assertThat(after.getDataStructureVersion().getId())
          .isEqualTo(before.getDataStructureVersion().getId());
    }
  }

  @Nested
  @DisplayName("Unrelease DataSource Tests")
  class UnreleaseTests {

    @Test
    @DisplayName("Should unrelease DataSource when not in use")
    void shouldUnreleaseWhenNotInUse() {
      UUID id = createReleasableTestEntity();
      performRelease(id);

      ResponseEntity<DataSourceOutputDTO> response = performUnrelease(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.DRAFT);
    }

    @Test
    @DisplayName("Should unrelease a DataSource a READY DataSet uses")
    void shouldUnreleaseWhenInUseByReadyDataSet() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.READY);

      ResponseEntity<DataSourceOutputDTO> response = performUnrelease(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should return 409 when unreleasing a DataSource in use by an AVAILABLE DataSet")
    void shouldReturn409WhenInUseByAvailableDataSet() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.AVAILABLE);

      ResponseEntity<String> response = performUnreleaseExpectingError(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should unrelease a DataSource a Pipeline in a DRAFT DataSet references")
    void shouldUnreleaseWhenInUseByDraftDataSet() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.DRAFT);

      ResponseEntity<DataSourceOutputDTO> response = performUnrelease(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.DRAFT);
    }
  }

  @Nested
  @DisplayName("DataPool ID Filter Tests")
  class DatapoolIdFilterTests {

    @Test
    @DisplayName("No datapoolId param returns all DataSources")
    void noFilterReturnsAll() {
      DataSource dsAll = portalData.dataSource(b -> b.datapoolScopeType(DatapoolScopeType.ALL));
      DataSource dsNone = portalData.dataSource(b -> b.datapoolScopeType(DatapoolScopeType.NONE));

      RestPage<DataSourceOutputDTO> page = getAllWithDatapoolId(null);

      assertThat(page.getTotalElements()).isEqualTo(2);
      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .containsExactlyInAnyOrder(dsAll.getId(), dsNone.getId());
      assertThat(page.getContent())
          .extracting(dto -> dto.getDatapoolScope().getType())
          .containsExactlyInAnyOrder(DatapoolScopeType.ALL, DatapoolScopeType.NONE);
    }

    @Test
    @DisplayName("scope=ALL is always included")
    void scopeAllIsAlwaysIncluded() {
      DataPool pool = portalData.dataPool();
      DataSource dsAll = portalData.dataSource(b -> b.datapoolScopeType(DatapoolScopeType.ALL));

      RestPage<DataSourceOutputDTO> page = getAllWithDatapoolId(pool.getId());

      assertThat(page.getContent()).extracting(DataSourceOutputDTO::getId).contains(dsAll.getId());
    }

    @Test
    @DisplayName("scope=SPECIFIC with matching pool is included")
    void scopeSpecificMatchingPoolIsIncluded() {
      DataPool pool = portalData.dataPool();
      DataSource dsSpecific =
          portalData.dataSource(
              b ->
                  b.datapoolScopeType(DatapoolScopeType.SPECIFIC)
                      .scopedDataPools(new HashSet<>(Set.of(pool))));

      RestPage<DataSourceOutputDTO> page = getAllWithDatapoolId(pool.getId());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .contains(dsSpecific.getId());
    }

    @Test
    @DisplayName("scope=SPECIFIC with non-matching pool is excluded")
    void scopeSpecificNonMatchingPoolIsExcluded() {
      DataPool poolA = portalData.dataPool();
      DataPool poolB = portalData.dataPool();
      DataSource dsSpecificB =
          portalData.dataSource(
              b ->
                  b.datapoolScopeType(DatapoolScopeType.SPECIFIC)
                      .scopedDataPools(new HashSet<>(Set.of(poolB))));

      RestPage<DataSourceOutputDTO> page = getAllWithDatapoolId(poolA.getId());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .doesNotContain(dsSpecificB.getId());
    }

    @Test
    @DisplayName("scope=NONE is always excluded")
    void scopeNoneIsAlwaysExcluded() {
      DataPool pool = portalData.dataPool();
      DataSource dsNone = portalData.dataSource(b -> b.datapoolScopeType(DatapoolScopeType.NONE));

      RestPage<DataSourceOutputDTO> page = getAllWithDatapoolId(pool.getId());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .doesNotContain(dsNone.getId());
    }

    @Test
    @DisplayName("Multiple SPECIFIC DataSources scoped to same pool produce no duplicate rows")
    void noDuplicatesWithMultipleScopedDataSources() {
      DataPool pool = portalData.dataPool();
      for (int i = 0; i < 3; i++) {
        portalData.dataSource(
            b ->
                b.datapoolScopeType(DatapoolScopeType.SPECIFIC)
                    .scopedDataPools(new HashSet<>(Set.of(pool))));
      }

      RestPage<DataSourceOutputDTO> page = getAllWithDatapoolId(pool.getId());

      long distinctIds =
          page.getContent().stream().map(DataSourceOutputDTO::getId).distinct().count();
      assertThat(distinctIds).isEqualTo(page.getContent().size());
    }

    private RestPage<DataSourceOutputDTO> getAllWithDatapoolId(UUID datapoolId) {
      Map<String, String> params =
          datapoolId != null ? Map.of("datapoolId", datapoolId.toString()) : Map.of();
      ResponseEntity<RestPage<DataSourceOutputDTO>> response = performGetAll(params);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      return response.getBody();
    }
  }

  @Nested
  @DisplayName("DataPool Scope Change Tests")
  class DatapoolScopeChangeTests {

    private DataPool usedPool;
    private DataPool otherPool;

    private void createPools() {
      usedPool = portalData.dataPool();
      otherPool = portalData.dataPool();
    }

    private void useInDataSetOfPool(DataSource dataSource, DataPool pool) {
      DataSet dataSet = portalData.dataSet(b -> b.dataPool(pool));
      portalData.pipeline(dataSet, b -> b.dataSources(Set.of(dataSource)));
    }

    private DataSource draftDataSourceUsedInPool() {
      createPools();
      DataSource dataSource =
          portalData.dataSource(b -> b.datapoolScopeType(DatapoolScopeType.ALL));
      useInDataSetOfPool(dataSource, usedPool);
      return dataSource;
    }

    private DatapoolScopeInputDTO specificScope(DataPool pool) {
      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of(pool.getId()));
      return scope;
    }

    private Map<String, Object> specificScopePatch(DataPool pool) {
      return Map.of(
          "datapoolScope",
          Map.of("type", "SPECIFIC", "datapoolIds", List.of(pool.getId().toString())));
    }

    private void assertScopeViolationAndScopeUnchanged(
        ResponseEntity<ProblemDetail> response, UUID id) {
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getProperties()).containsKey("offendingDataSourceIds");
      assertThat(performGetById(id).getBody().getDatapoolScope().getType())
          .isEqualTo(DatapoolScopeType.ALL);
    }

    @Test
    @DisplayName("PUT on a DRAFT data source should reject a scope that excludes a using dataset")
    void putDraft_whenScopeExcludesUsingDataSetPool_shouldRejectAndKeepScope() {
      DataSource dataSource = draftDataSourceUsedInPool();
      DataSourceInputDTO input = createUpdateInput();
      input.setDatapoolScope(specificScope(otherPool));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + dataSource.getId(),
              HttpMethod.PUT,
              createAuthHeaders(),
              input);

      assertScopeViolationAndScopeUnchanged(response, dataSource.getId());
    }

    @Test
    @DisplayName("PATCH on a DRAFT data source should reject a scope that excludes a using dataset")
    void patchDraft_whenScopeExcludesUsingDataSetPool_shouldRejectAndKeepScope() {
      DataSource dataSource = draftDataSourceUsedInPool();

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + dataSource.getId(),
              HttpMethod.PATCH,
              createAuthHeaders(),
              specificScopePatch(otherPool));

      assertScopeViolationAndScopeUnchanged(response, dataSource.getId());
    }

    @Test
    @DisplayName("PUT on a DRAFT data source should accept a narrowed scope that keeps the pool")
    void putDraft_whenScopeKeepsUsingDataSetPool_shouldUpdateScope() {
      DataSource dataSource = draftDataSourceUsedInPool();
      DataSourceInputDTO input = createUpdateInput();
      input.setDatapoolScope(specificScope(usedPool));

      ResponseEntity<DataSourceOutputDTO> response = performUpdate(dataSource.getId(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDatapoolScope().getType())
          .isEqualTo(DatapoolScopeType.SPECIFIC);
      assertThat(response.getBody().getDatapoolScope().getDatapoolIds())
          .containsExactly(usedPool.getId());
    }

    @Test
    @DisplayName("PATCH on a DRAFT data source should accept a narrowed scope that keeps the pool")
    void patchDraft_whenScopeKeepsUsingDataSetPool_shouldUpdateScope() {
      DataSource dataSource = draftDataSourceUsedInPool();

      ResponseEntity<DataSourceOutputDTO> response =
          performPatch(dataSource.getId(), specificScopePatch(usedPool));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDatapoolScope().getType())
          .isEqualTo(DatapoolScopeType.SPECIFIC);
      assertThat(response.getBody().getDatapoolScope().getDatapoolIds())
          .containsExactly(usedPool.getId());
    }

    @Test
    @DisplayName("Released meta PATCH should reject a scope that excludes a using dataset")
    void patchReleasedMeta_whenScopeExcludesUsingDataSetPool_shouldRejectAndKeepScope() {
      createPools();
      DataSource dataSource = createAvailableDataSource();
      useInDataSetOfPool(dataSource, usedPool);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + dataSource.getId() + "/released/meta",
              HttpMethod.PATCH,
              createAuthHeaders(),
              specificScopePatch(otherPool));

      assertScopeViolationAndScopeUnchanged(response, dataSource.getId());
    }
  }
}
