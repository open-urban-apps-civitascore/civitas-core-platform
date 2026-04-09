package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.util.RestPage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("DataSource Controller Integration Tests")
class DataSourceControllerIntegrationTest
    extends BaseDataEntityControllerIntegrationTest<DataSourceInputDTO, DataSourceOutputDTO> {

  private static final String DATASOURCES_ENDPOINT = "/datasources";

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private PipelineRepository pipelineRepository;

  @Override
  protected String getEndpointPath() {
    return DATASOURCES_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  private UUID createAvailableDataStructureVersionId() {
    var ds = portalData.dataStructure().withStatus(DataStructureStatus.AVAILABLE).build();
    DataStructureVersion dsv =
        portalData
            .dataStructureVersion()
            .withStatus(DataStructureVersionStatus.AVAILABLE)
            .withDataStructure(ds)
            .build();
    return dsv.getId();
  }

  @Override
  protected DataSourceInputDTO createValidInput() {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName("test_datasource_" + UUID.randomUUID().toString().substring(0, 8));
    input.setDescription("A test data source for integration testing");
    input.setConnectorType(ConnectorType.MQTT);
    input.setConfiguration(
        Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("sensor/data"), "qos", 1));
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
    input.setConnectorType(ConnectorType.MQTT);
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

  private UUID createPublishableTestEntity() {
    UUID dsvId = createAvailableDataStructureVersionId();
    DataSourceInputDTO input = createValidInput();
    input.setDataStructureVersionId(dsvId);
    ResponseEntity<DataSourceOutputDTO> response = performCreate(input);
    return response.getBody().getId();
  }

  private UUID createPublishableSqlTestEntity() {
    UUID dsvId = createAvailableDataStructureVersionId();
    DataSourceInputDTO input = createValidSqlInput();
    input.setDataStructureVersionId(dsvId);
    ResponseEntity<DataSourceOutputDTO> response = performCreate(input);
    return response.getBody().getId();
  }

  private ResponseEntity<DataSourceOutputDTO> performPublish(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/publish";
    return exchange(url, HttpMethod.POST, createAuthHeaders(), null, getOutputTypeReference());
  }

  private ResponseEntity<DataSourceOutputDTO> performUnpublish(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/unpublish";
    return exchange(url, HttpMethod.POST, createAuthHeaders(), null, getOutputTypeReference());
  }

  private ResponseEntity<String> performUnpublishExpectingError(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/unpublish";
    return restTemplate.exchange(
        url, HttpMethod.POST, new HttpEntity<>(createAuthHeaders()), String.class);
  }

  private DataSource createAvailableDataSource() {
    UUID id = createPublishableTestEntity();
    performPublish(id);
    return dataSourceRepository.findById(id).orElseThrow();
  }

  private void linkDataSourceToDataSetViaStatus(DataSource dataSource, DataSetStatus status) {
    DataSet dataSet = portalData.dataSet().withStatus(status).build();
    portalData.pipeline().withDataSet(dataSet).withDataSources(Set.of(dataSource)).build();
  }

  private ResponseEntity<String> performPublishExpectingError(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/publish";
    return restTemplate.exchange(
        url, HttpMethod.POST, new HttpEntity<>(createAuthHeaders()), String.class);
  }

  private ResponseEntity<String> performGetByIdExpectingError(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id;
    return restTemplate.exchange(
        url, HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);
  }

  private UUID createDraftDataStructureVersionId() {
    var ds = portalData.dataStructure().withStatus(DataStructureStatus.AVAILABLE).build();
    DataStructureVersion dsv =
        portalData
            .dataStructureVersion()
            .withStatus(DataStructureVersionStatus.DRAFT)
            .withDataStructure(ds)
            .build();
    return dsv.getId();
  }

  private UUID createDsvWithDraftParentDataStructure() {
    var ds = portalData.dataStructure().withStatus(DataStructureStatus.DRAFT).build();
    DataStructureVersion dsv =
        portalData
            .dataStructureVersion()
            .withStatus(DataStructureVersionStatus.AVAILABLE)
            .withDataStructure(ds)
            .build();
    return dsv.getId();
  }

  private DataSourceInputDTO createValidSqlInput() {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName("sql_datasource_" + UUID.randomUUID().toString().substring(0, 8));
    input.setDescription("SQL data source");
    input.setConnectorType(ConnectorType.SQL);
    input.setConfiguration(
        Map.of(
            "driver", "postgres",
            "dsn", "postgres://localhost:5432/db",
            "table", "measurements",
            "columns", List.of("id", "value", "timestamp"),
            "user", "admin",
            "password", "secret"));
    return input;
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
      assertThat(output.getConnectorType()).isEqualTo(ConnectorType.MQTT);
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
    @DisplayName("Should fail to create data source without authentication")
    void shouldFailToCreateWithoutAuth() {
      ResponseEntity<String> response = performRequestWithoutAuth("", HttpMethod.POST);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create SQL data source with masked password in response")
    void shouldCreateSqlDataSourceWithMaskedPassword() {
      DataSourceInputDTO input = createValidSqlInput();

      ResponseEntity<DataSourceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();

      DataSourceOutputDTO output = response.getBody();
      Map<String, Object> config = output.getConfiguration();
      assertThat(config).isNotNull();
      assertThat(config.get("driver")).isEqualTo("postgres");
      assertThat(config.get("table")).isEqualTo("measurements");
      assertThat(config.get("dsn").toString()).isEqualTo("postgres://localhost:5432/db");
      assertThat(config.get("user")).isEqualTo("admin");
      assertThat(config.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }
  }

  @Nested
  @DisplayName("MQTT Blank Parameter Normalization Tests")
  class MqttBlankNormalizationTests {

    @Test
    @DisplayName("Should normalize blank optional MQTT strings to null on create")
    void shouldNormalizeBlankOptionalStringsOnCreate() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("mqtt_blank_test_" + UUID.randomUUID().toString().substring(0, 8));
      input.setConnectorType(ConnectorType.MQTT);
      Map<String, Object> config = new HashMap<>();
      config.put("urls", List.of("tcp://broker:1883"));
      config.put("topics", List.of("sensor/data"));
      config.put("qos", 1);
      config.put("client_id", "");
      config.put("connect_timeout", "   ");
      config.put("keepalive", "");
      config.put("user", " ");
      input.setConfiguration(config);

      ResponseEntity<DataSourceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      Map<String, Object> resultConfig = response.getBody().getConfiguration();
      assertThat(resultConfig.get("client_id")).isNull();
      assertThat(resultConfig.get("connect_timeout")).isNull();
      assertThat(resultConfig.get("keepalive")).isNull();
      assertThat(resultConfig.get("user")).isNull();
    }

    @Test
    @DisplayName("Should persist non-blank optional MQTT strings unchanged")
    void shouldPersistNonBlankOptionalStringsUnchanged() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("mqtt_nonblank_test_" + UUID.randomUUID().toString().substring(0, 8));
      input.setConnectorType(ConnectorType.MQTT);
      input.setConfiguration(
          Map.of(
              "urls", List.of("tcp://broker:1883"),
              "topics", List.of("sensor/data"),
              "qos", 1,
              "client_id", "my-client",
              "connect_timeout", "5s",
              "keepalive", "30s",
              "user", "mqttuser"));

      ResponseEntity<DataSourceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      Map<String, Object> resultConfig = response.getBody().getConfiguration();
      assertThat(resultConfig.get("client_id")).isEqualTo("my-client");
      assertThat(resultConfig.get("connect_timeout")).isEqualTo("5s");
      assertThat(resultConfig.get("keepalive")).isEqualTo("30s");
      assertThat(resultConfig.get("user")).isEqualTo("mqttuser");
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
  }

  @Nested
  @DisplayName("Publish DataSource Tests")
  class PublishTests {

    @Test
    @DisplayName("Should publish valid MQTT data source")
    void shouldPublishValidMqttDataSource() {
      UUID id = createPublishableTestEntity();

      ResponseEntity<DataSourceOutputDTO> response = performPublish(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should publish valid SQL data source")
    void shouldPublishValidSqlDataSource() {
      UUID id = createPublishableSqlTestEntity();

      ResponseEntity<DataSourceOutputDTO> response = performPublish(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to publish already AVAILABLE data source")
    void shouldFailToPublishAvailableDataSource() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

      ResponseEntity<String> response = performPublishExpectingError(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to publish data source without configuration")
    void shouldFailToPublishWithoutConfiguration() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("bare_source_" + UUID.randomUUID().toString().substring(0, 8));
      input.setConnectorType(ConnectorType.MQTT);

      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      ResponseEntity<String> response = performPublishExpectingError(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to publish non-existent data source")
    void shouldFailToPublishNonExistent() {
      ResponseEntity<String> response = performPublishExpectingError(UUID.randomUUID());

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
      UUID id = createPublishableTestEntity();
      performPublish(id);

      ResponseEntity<Void> response = performDelete(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
  @DisplayName("PATCH Configuration Tests")
  class PatchConfigurationTests {

    @Test
    @DisplayName("PATCH non-config fields should preserve existing configuration")
    void patchNonConfigFieldsShouldPreserveConfiguration() {
      DataSourceInputDTO input = createValidSqlInput();
      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      Map<String, Object> patch = Map.of("description", "updated description");
      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patch);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDescription()).isEqualTo("updated description");
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config).isNotNull();
      assertThat(config.get("driver")).isEqualTo("postgres");
      assertThat(config.get("table")).isEqualTo("measurements");
      assertThat(config.get("dsn")).isEqualTo("postgres://localhost:5432/db");
      assertThat(config.get("user")).isEqualTo("admin");
      assertThat(config.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }

    @Test
    @DisplayName("PATCH non-sensitive config fields should preserve encrypted password")
    void patchNonSensitiveConfigFieldsShouldPreservePassword() {
      DataSourceInputDTO input = createValidSqlInput();
      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      Map<String, Object> patch = Map.of("configuration", Map.of("table", "new_table"));
      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patch);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("table")).isEqualTo("new_table");
      assertThat(config.get("dsn")).isEqualTo("postgres://localhost:5432/db");
      assertThat(config.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }

    @Test
    @DisplayName("PATCH with new password should encrypt new credentials")
    void patchWithNewPasswordShouldEncrypt() {
      DataSourceInputDTO input = createValidSqlInput();
      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      Map<String, Object> patch =
          Map.of("configuration", Map.of("user", "newuser", "password", "newpass"));
      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patch);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("user")).isEqualTo("newuser");
      assertThat(config.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
      assertThat(config.get("dsn")).isEqualTo("postgres://localhost:5432/db");
    }

    @Test
    @DisplayName("PATCH MQTT with new password should encrypt")
    void patchMqttWithNewPasswordShouldEncrypt() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("mqtt_patch_test_" + UUID.randomUUID().toString().substring(0, 8));
      input.setConnectorType(ConnectorType.MQTT);
      input.setConfiguration(
          Map.of(
              "urls",
              List.of("tcp://broker:1883"),
              "topics",
              List.of("sensor/data"),
              "qos",
              1,
              "user",
              "mqttuser",
              "password",
              "mqttpass"));
      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      Map<String, Object> patch =
          Map.of("configuration", Map.of("user", "newuser", "password", "newpass"));
      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patch);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("topics")).isEqualTo(List.of("sensor/data"));
      assertThat(config.get("user")).isEqualTo("newuser");
      assertThat(config.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }

    @Test
    @DisplayName("PATCH MQTT non-sensitive fields should preserve encrypted password")
    void patchMqttNonSensitiveFieldsShouldPreservePassword() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("mqtt_patch_preserve_" + UUID.randomUUID().toString().substring(0, 8));
      input.setConnectorType(ConnectorType.MQTT);
      input.setConfiguration(
          Map.of(
              "urls",
              List.of("tcp://broker:1883"),
              "topics",
              List.of("sensor/data"),
              "qos",
              1,
              "password",
              "my-secret"));
      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      Map<String, Object> patch = Map.of("configuration", Map.of("topics", List.of("new/topic")));
      ResponseEntity<DataSourceOutputDTO> response = performPatch(id, patch);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("topics")).isEqualTo(List.of("new/topic"));
      assertThat(config.get("qos")).isEqualTo(1);
    }

    @Test
    @DisplayName("PATCH then publish should work")
    void patchThenPublishShouldWork() {
      UUID id = createPublishableSqlTestEntity();

      Map<String, Object> patch = Map.of("description", "Published via patch");
      ResponseEntity<DataSourceOutputDTO> patchResponse = performPatch(id, patch);

      assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patchResponse.getBody().getDescription()).isEqualTo("Published via patch");

      ResponseEntity<DataSourceOutputDTO> publishResponse = performPublish(id);

      assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(publishResponse.getBody().getDataSourceStatus())
          .isEqualTo(DataSourceStatus.AVAILABLE);
      assertThat(publishResponse.getBody().getDescription()).isEqualTo("Published via patch");
    }
  }

  @Nested
  @DisplayName("Unique Name Constraint Tests")
  class UniqueNameTests {

    @Test
    @DisplayName("Should reject create with duplicate name")
    void shouldRejectCreateWithDuplicateName() {
      DataSourceInputDTO input = createValidInput();
      String name = input.getName();
      performCreate(input);

      DataSourceInputDTO duplicate = createValidInput();
      duplicate.setName(name);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath(),
              HttpMethod.POST,
              new HttpEntity<>(duplicate, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should reject update with duplicate name")
    void shouldRejectUpdateWithDuplicateName() {
      DataSourceInputDTO first = createValidInput();
      String existingName = first.getName();
      performCreate(first);

      DataSourceInputDTO second = createValidInput();
      ResponseEntity<DataSourceOutputDTO> secondResponse = performCreate(second);
      UUID secondId = secondResponse.getBody().getId();

      DataSourceInputDTO update = createUpdateInput();
      update.setName(existingName);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + secondId,
              HttpMethod.PUT,
              new HttpEntity<>(update, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should reject patch with duplicate name")
    void shouldRejectPatchWithDuplicateName() {
      DataSourceInputDTO first = createValidInput();
      String existingName = first.getName();
      performCreate(first);

      UUID secondId = createTestEntity();

      Map<String, Object> patch = Map.of("name", existingName);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + secondId,
              HttpMethod.PATCH,
              new HttpEntity<>(patch, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should reject published meta update with duplicate name")
    void shouldRejectPublishedMetaUpdateWithDuplicateName() {
      DataSourceInputDTO first = createValidInput();
      String existingName = first.getName();
      performCreate(first);

      UUID secondId = createPublishableTestEntity();
      performPublish(secondId);

      Map<String, Object> metaUpdate = Map.of("name", existingName);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + secondId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(metaUpdate, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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
    @DisplayName("Should reject linking a DRAFT DataStructureVersion")
    void shouldRejectLinkingDraftDataStructureVersion() {
      UUID dsvId = createDraftDataStructureVersionId();
      DataSourceInputDTO input = createValidInput();
      input.setDataStructureVersionId(dsvId);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject linking when parent DataStructure is DRAFT")
    void shouldRejectLinkingWhenParentDataStructureIsDraft() {
      UUID dsvId = createDsvWithDraftParentDataStructure();
      DataSourceInputDTO input = createValidInput();
      input.setDataStructureVersionId(dsvId);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject changing DSV on published data source via PUT")
    void shouldRejectChangingDsvWhenPublished() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

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
    @DisplayName("Should reject removing DSV from published data source via PUT")
    void shouldRejectRemovingDsvWhenPublished() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

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
    @DisplayName("Should fail to publish without DataStructureVersion")
    void shouldFailToPublishWithoutDataStructureVersion() {
      UUID id = createTestEntity();

      ResponseEntity<String> response = performPublishExpectingError(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Configuration Masking Tests")
  class MaskingTests {

    @Test
    @DisplayName("Should mask sensitive SQL fields in GET response")
    void shouldMaskSensitiveSqlFieldsInGetResponse() {
      DataSourceInputDTO input = createValidSqlInput();
      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      ResponseEntity<DataSourceOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("driver")).isEqualTo("postgres");
      assertThat(config.get("table")).isEqualTo("measurements");
      assertThat(config.get("dsn")).isEqualTo("postgres://localhost:5432/db");
      assertThat(config.get("user")).isEqualTo("admin");
      assertThat(config.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }

    @Test
    @DisplayName("Should mask sensitive MQTT fields in GET response")
    void shouldMaskSensitiveMqttFieldsInGetResponse() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("mqtt_with_password_" + UUID.randomUUID().toString().substring(0, 8));
      input.setConnectorType(ConnectorType.MQTT);
      input.setConfiguration(
          Map.of(
              "urls",
              List.of("tcp://broker:1883"),
              "topics",
              List.of("sensor/data"),
              "qos",
              1,
              "user",
              "mqttuser",
              "password",
              "my-secret-password"));

      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      ResponseEntity<DataSourceOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("topics")).isEqualTo(List.of("sensor/data"));
      assertThat(config.get("user")).isEqualTo("mqttuser");
      assertThat(config.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }
  }

  @Nested
  @DisplayName("inUse Flag Tests")
  class InUseFlagTests {

    @Test
    @DisplayName("Should return inUse=false when DataSource is not referenced by any DataSet")
    void shouldReturnInUseFalseWhenNotReferenced() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

      ResponseEntity<DataSourceOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().isInUse()).isFalse();
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
    }

    @Test
    @DisplayName("Should return inUse=true when DataSource is referenced by a READY DataSet")
    void shouldReturnInUseTrueWhenReadyDataSetReferences() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.READY);

      ResponseEntity<DataSourceOutputDTO> response = performGetById(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().isInUse()).isTrue();
    }

    @Test
    @DisplayName("Should return inUse=true when DataSource is referenced by an AVAILABLE DataSet")
    void shouldReturnInUseTrueWhenAvailableDataSetReferences() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.AVAILABLE);

      ResponseEntity<DataSourceOutputDTO> response = performGetById(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().isInUse()).isTrue();
    }
  }

  @Nested
  @DisplayName("Published Meta Update Tests")
  class PublishedMetaTests {

    @Test
    @DisplayName("Should update name and description of AVAILABLE data source")
    void shouldUpdateNameAndDescription() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

      Map<String, Object> metaUpdate =
          Map.of("name", "updated-name", "description", "updated-desc");

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + id + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              metaUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo("updated-name");
      assertThat(response.getBody().getDescription()).isEqualTo("updated-desc");
    }

    @Test
    @DisplayName("Should update configuration of AVAILABLE data source via published/meta")
    void shouldUpdateConfiguration() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

      Map<String, Object> newConfig =
          Map.of(
              "urls", List.of("tcp://new-broker:1883"),
              "topics", List.of("new/topic"),
              "qos", 2);
      Map<String, Object> metaUpdate = Map.of("name", "updated-name", "configuration", newConfig);

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + id + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              metaUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("urls")).isEqualTo(List.of("tcp://new-broker:1883"));
      assertThat(config.get("topics")).isEqualTo(List.of("new/topic"));
      assertThat(config.get("qos")).isEqualTo(2);
    }

    @Test
    @DisplayName("Should preserve masked password when updating configuration via published/meta")
    void shouldPreserveMaskedPassword() {
      UUID id = createPublishableSqlTestEntity();
      performPublish(id);

      Map<String, Object> newConfig =
          new HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://new-host:5432/db",
                  "table", "measurements",
                  "columns", List.of("id", "value", "timestamp"),
                  "user", "admin",
                  "password", ConnectorHandler.MASKED_VALUE));
      Map<String, Object> metaUpdate = Map.of("name", "updated-sql", "configuration", newConfig);

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + id + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              metaUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("dsn")).isEqualTo("postgres://new-host:5432/db");
      assertThat(config.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }

    @Test
    @DisplayName("Should not change configuration when not provided in published/meta update")
    void shouldNotChangeConfigurationWhenNotProvided() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

      ResponseEntity<DataSourceOutputDTO> before =
          exchange(
              getEndpointPath() + "/" + id,
              HttpMethod.GET,
              createAuthHeaders(),
              null,
              getOutputTypeReference());
      Map<String, Object> originalConfig = before.getBody().getConfiguration();

      Map<String, Object> metaUpdate = Map.of("name", "updated-name");

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + id + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              metaUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getConfiguration()).isEqualTo(originalConfig);
    }

    @Test
    @DisplayName("Should reject published/meta update on DRAFT data source")
    void shouldRejectUpdateOnDraftDataSource() {
      UUID id = createPublishableTestEntity();

      Map<String, Object> metaUpdate = Map.of("name", "updated-name");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + id + "/published/meta",
              HttpMethod.PUT,
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
              getEndpointPath() + "/" + dataSource.getId() + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              metaUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo("updated-name");
      assertThat(response.getBody().getDescription()).isEqualTo("updated-desc");
    }

    @Test
    @DisplayName("Should reject configuration change when in use")
    void shouldRejectConfigurationChangeWhenInUse() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.AVAILABLE);

      Map<String, Object> newConfig =
          Map.of(
              "urls", List.of("tcp://new-broker:1883"),
              "topics", List.of("new/topic"),
              "qos", 2);
      Map<String, Object> metaUpdate = Map.of("name", "updated-name", "configuration", newConfig);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + dataSource.getId() + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(metaUpdate, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject connector type change when in use")
    void shouldRejectConnectorTypeChangeWhenInUse() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.READY);

      Map<String, Object> metaUpdate = Map.of("name", "updated-name", "connectorType", "SQL");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + dataSource.getId() + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(metaUpdate, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should allow full update when not in use")
    void shouldAllowFullUpdateWhenNotInUse() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

      Map<String, Object> newConfig =
          Map.of(
              "urls", List.of("tcp://new-broker:1883"),
              "topics", List.of("new/topic"),
              "qos", 2);
      Map<String, Object> fullUpdate = Map.of("name", "updated-name", "configuration", newConfig);

      ResponseEntity<DataSourceOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + id + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              fullUpdate,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo("updated-name");
      Map<String, Object> config = response.getBody().getConfiguration();
      assertThat(config.get("urls")).isEqualTo(List.of("tcp://new-broker:1883"));
    }
  }

  @Nested
  @DisplayName("Unpublish DataSource Tests")
  class UnpublishTests {

    @Test
    @DisplayName("Should unpublish DataSource when not in use")
    void shouldUnpublishWhenNotInUse() {
      UUID id = createPublishableTestEntity();
      performPublish(id);

      ResponseEntity<DataSourceOutputDTO> response = performUnpublish(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.DRAFT);
    }

    @Test
    @DisplayName("Should return 409 when unpublishing a DataSource in use by a READY DataSet")
    void shouldReturn409WhenInUseByReadyDataSet() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.READY);

      ResponseEntity<String> response = performUnpublishExpectingError(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should return 409 when unpublishing a DataSource in use by an AVAILABLE DataSet")
    void shouldReturn409WhenInUseByAvailableDataSet() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.AVAILABLE);

      ResponseEntity<String> response = performUnpublishExpectingError(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName(
        "Should return 409 when unpublishing a DataSource referenced by a Pipeline in a DRAFT DataSet")
    void shouldReturn409WhenInUseByDraftDataSet() {
      DataSource dataSource = createAvailableDataSource();
      linkDataSourceToDataSetViaStatus(dataSource, DataSetStatus.DRAFT);

      ResponseEntity<String> response = performUnpublishExpectingError(dataSource.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
  }
}
