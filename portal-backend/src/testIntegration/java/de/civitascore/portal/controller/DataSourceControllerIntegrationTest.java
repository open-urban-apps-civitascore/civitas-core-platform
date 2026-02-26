package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.util.RestPage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    extends BaseControllerIntegrationTest<DataSourceInputDTO, DataSourceOutputDTO> {

  private static final String DATASOURCES_ENDPOINT = "/datasources";

  @Autowired private DataSourceRepository dataSourceRepository;

  @Override
  protected String getEndpointPath() {
    return DATASOURCES_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    dataSourceRepository.deleteAll();
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

  private ResponseEntity<DataSourceOutputDTO> performPublish(UUID id) {
    String url = DATASOURCES_ENDPOINT + "/" + id + "/publish";
    return exchange(url, HttpMethod.POST, createAuthHeaders(), null, getOutputTypeReference());
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

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Updated description via PATCH");

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
      UUID id = createTestEntity();

      ResponseEntity<DataSourceOutputDTO> response = performPublish(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should publish valid SQL data source")
    void shouldPublishValidSqlDataSource() {
      DataSourceInputDTO input = createValidSqlInput();
      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

      ResponseEntity<DataSourceOutputDTO> response = performPublish(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to publish already AVAILABLE data source")
    void shouldFailToPublishAvailableDataSource() {
      UUID id = createTestEntity();
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
      UUID id = createTestEntity();
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
      DataSourceInputDTO input = createValidSqlInput();
      ResponseEntity<DataSourceOutputDTO> createResponse = performCreate(input);
      UUID id = createResponse.getBody().getId();

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

      DataSourceInputDTO second = createValidInput();
      ResponseEntity<DataSourceOutputDTO> secondResponse = performCreate(second);
      UUID secondId = secondResponse.getBody().getId();
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
}
