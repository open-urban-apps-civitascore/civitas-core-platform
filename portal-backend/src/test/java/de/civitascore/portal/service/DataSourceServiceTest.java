package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataSourceMetaInputDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.service.connector.ConnectorHandlerRegistry;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSourceService Tests")
class DataSourceServiceTest {

  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private DataSourceMapper dataSourceMapper;
  @Mock private ConnectorHandlerRegistry connectorHandlerRegistry;
  @Mock private ConnectorHandler mqttHandler;
  @Mock private ConnectorHandler sqlHandler;
  @Mock private AssignmentService assignmentService;
  @Mock private DataStructureVersionService dataStructureVersionService;

  @InjectMocks private DataSourceService dataSourceService;

  @Nested
  @DisplayName("Create DataSource")
  class CreateTests {

    @Test
    @DisplayName("Should create data source in DRAFT status by default")
    void shouldCreateDataSourceInDraftStatus() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("test-source");

      DataSource entity = new DataSource();
      entity.setName("test-source");

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataSourceRepository.save(any())).thenReturn(entity);

      DataSource result = dataSourceService.create(input);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.DRAFT);
    }

    @Test
    @DisplayName("Should reject configuration when no connector type is set")
    void shouldRejectConfigurationWithoutConnectorType() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("test-source");
      input.setConfiguration(Map.of("password", "secret"));

      assertThatThrownBy(() -> dataSourceService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Cannot set configuration without a connector type");
    }
  }

  @Nested
  @DisplayName("Publish DataSource")
  class PublishTests {

    @Test
    @DisplayName("Should publish valid MQTT data source")
    void shouldPublishValidMqttDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.MQTT)).thenReturn(mqttHandler);
      when(mqttHandler.validate(any(), any(Class[].class))).thenReturn(Collections.emptyList());

      DataSource result = dataSourceService.publish(id);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should publish valid SQL data source")
    void shouldPublishValidSqlDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = createSqlDataSource(id);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.SQL)).thenReturn(sqlHandler);
      when(sqlHandler.validate(any(), any(Class[].class))).thenReturn(Collections.emptyList());

      DataSource result = dataSourceService.publish(id);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to publish already AVAILABLE data source")
    void shouldFailToPublishAvailableDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.publish(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("Should fail to publish without connector type")
    void shouldFailToPublishWithoutConnectorType() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(null);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.publish(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Connector type");
    }

    @Test
    @DisplayName("Should fail to publish without data structure version")
    void shouldFailToPublishWithoutDataStructureVersion() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setDataStructureVersion(null);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.publish(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Data structure version");
    }

    @Test
    @DisplayName("Should fail to publish MQTT source without urls")
    void shouldFailToPublishMqttWithoutUrls() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setConfiguration(Map.of("topics", List.of("sensor/data"), "qos", 1));
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.MQTT)).thenReturn(mqttHandler);
      when(mqttHandler.validate(any(), any(Class[].class))).thenReturn(List.of("urls is required"));

      assertThatThrownBy(() -> dataSourceService.publish(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("urls");
    }

    @Test
    @DisplayName("Should fail to publish MQTT source with invalid qos")
    void shouldFailToPublishMqttWithInvalidQos() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setConfiguration(
          Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("sensor/data"), "qos", 5));
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.MQTT)).thenReturn(mqttHandler);
      when(mqttHandler.validate(any(), any(Class[].class)))
          .thenReturn(List.of("qos must be 0, 1, or 2"));

      assertThatThrownBy(() -> dataSourceService.publish(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("qos");
    }

    @Test
    @DisplayName("Should fail to publish SQL source without driver")
    void shouldFailToPublishSqlWithoutDriver() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.SQL);
      entity.setConfiguration(
          Map.of("dsn", "postgres://host/db", "table", "users", "columns", List.of("id")));
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.SQL)).thenReturn(sqlHandler);
      when(sqlHandler.validate(any(), any(Class[].class)))
          .thenReturn(List.of("driver is required"));

      assertThatThrownBy(() -> dataSourceService.publish(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("driver");
    }

    @Test
    @DisplayName("Should fail to publish with empty configuration")
    void shouldFailToPublishWithEmptyConfiguration() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setConfiguration(Map.of());
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.publish(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Configuration is required");
    }

    @Test
    @DisplayName("Should fail to publish non-existent data source")
    void shouldFailToPublishNonExistentDataSource() {
      UUID id = UUID.randomUUID();
      when(dataSourceRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSourceService.publish(id))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Unpublish DataSource")
  class UnpublishTests {

    @Test
    @DisplayName("Should unpublish AVAILABLE data source")
    void shouldUnpublishAvailableDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.unpublish(id);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to unpublish DRAFT data source")
    void shouldFailToUnpublishDraftDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.unpublish(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }
  }

  @Nested
  @DisplayName("Update Published Metadata")
  class UpdatePublishedMetaTests {

    @Test
    @DisplayName("Should update name and description of AVAILABLE data source")
    void shouldUpdateMetaOfAvailableDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("old-name");
      entity.setDescription("old-desc");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setName("new-name");
      input.setDescription("new-desc");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updatePublishedMeta(id, input);

      assertThat(result.getName()).isEqualTo("new-name");
      assertThat(result.getDescription()).isEqualTo("new-desc");
    }

    @Test
    @DisplayName("Should fail to update metadata of DRAFT data source")
    void shouldFailToUpdateMetaOfDraftDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setName("new-name");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.updatePublishedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }

    @Test
    @DisplayName("Should only update provided fields")
    void shouldOnlyUpdateProvidedFields() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("original-name");
      entity.setDescription("original-desc");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setName("updated-name");
      // description not set — should remain unchanged

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updatePublishedMeta(id, input);

      assertThat(result.getName()).isEqualTo("updated-name");
      assertThat(result.getDescription()).isEqualTo("original-desc");
    }
  }

  @Nested
  @DisplayName("Delete DataSource")
  class DeleteTests {

    @Test
    @DisplayName("Should fail to delete AVAILABLE data source")
    void shouldFailToDeleteAvailableDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      when(dataSourceRepository.existsById(id)).thenReturn(true);
      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.deleteById(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }

    @Test
    @DisplayName("Should fail to delete non-existent data source")
    void shouldFailToDeleteNonExistentDataSource() {
      UUID id = UUID.randomUUID();
      when(dataSourceRepository.existsById(id)).thenReturn(false);

      assertThatThrownBy(() -> dataSourceService.deleteById(id))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("DataStructureVersion Linking")
  class DataStructureVersionLinkingTests {

    @Test
    @DisplayName("Should reject linking a DRAFT DataStructureVersion")
    void shouldRejectLinkingDraftDataStructureVersion() {
      UUID dsvId = UUID.randomUUID();
      DataStructureVersion dsv = createDataStructureVersion();
      dsv.setId(dsvId);
      dsv.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dsv.setDataStructure(ds);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("test");
      input.setDataStructureVersionId(dsvId);

      DataSource entity = new DataSource();
      entity.setId(UUID.randomUUID());

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataStructureVersionService.findByIdOrThrow(dsvId)).thenReturn(dsv);

      assertThatThrownBy(() -> dataSourceService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE status");
    }

    @Test
    @DisplayName("Should reject linking when parent DataStructure is DRAFT")
    void shouldRejectLinkingWhenParentDataStructureIsDraft() {
      UUID dsvId = UUID.randomUUID();
      DataStructureVersion dsv = createDataStructureVersion();
      dsv.setId(dsvId);
      dsv.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      DataStructure ds = new DataStructure();
      ds.setDataStructureStatus(DataStructureStatus.DRAFT);
      dsv.setDataStructure(ds);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("test");
      input.setDataStructureVersionId(dsvId);

      DataSource entity = new DataSource();
      entity.setId(UUID.randomUUID());

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataStructureVersionService.findByIdOrThrow(dsvId)).thenReturn(dsv);

      assertThatThrownBy(() -> dataSourceService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("parent DataStructure");
    }

    @Test
    @DisplayName("Should reject changing DSV on AVAILABLE data source")
    void shouldRejectChangingDsvWhenAvailable() {
      UUID id = UUID.randomUUID();
      DataStructureVersion existingDsv = createDataStructureVersion();

      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setDataStructureVersion(existingDsv);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");
      input.setConnectorType(ConnectorType.MQTT);
      input.setDataStructureVersionId(UUID.randomUUID());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.update(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Cannot change data structure version");
    }

    @Test
    @DisplayName("Should reject removing DSV from AVAILABLE data source")
    void shouldRejectRemovingDsvWhenAvailable() {
      UUID id = UUID.randomUUID();
      DataStructureVersion existingDsv = createDataStructureVersion();

      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setDataStructureVersion(existingDsv);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");
      input.setConnectorType(ConnectorType.MQTT);
      input.setDataStructureVersionId(null);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.update(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Cannot remove data structure version");
    }

    @Test
    @DisplayName("Should return null when no DSV linked")
    void shouldReturnNullWhenNoDsvLinked() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataStructureVersion(null);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      DataStructureVersion result = dataSourceService.findLinkedDataStructureVersion(id);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return DSV when linked")
    void shouldReturnDsvWhenLinked() {
      UUID id = UUID.randomUUID();
      DataStructureVersion dsv = createDataStructureVersion();

      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataStructureVersion(dsv);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataStructureVersionService.findByIdOrThrow(dsv.getId())).thenReturn(dsv);

      DataStructureVersion result = dataSourceService.findLinkedDataStructureVersion(id);

      assertThat(result).isEqualTo(dsv);
    }
  }

  @Nested
  @DisplayName("Update DataSource")
  class UpdateTests {

    @Test
    @DisplayName("Should prevent changing connector type of AVAILABLE data source")
    void shouldPreventChangingConnectorTypeWhenAvailable() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");
      input.setConnectorType(ConnectorType.SQL);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.update(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("connector type");
    }

    @Test
    @DisplayName("Should restore masked password from existing entity on PUT")
    void shouldRestoreMaskedPasswordOnPut() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.SQL);
      entity.setConfiguration(
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://host/db",
                  "user", "admin",
                  "password", "enc_secret")));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");
      input.setConnectorType(ConnectorType.SQL);
      input.setConfiguration(
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://host/db",
                  "user", "admin",
                  "password", ConnectorHandler.MASKED_VALUE)));

      Map<String, Object> normalized =
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://host/db",
                  "user", "admin",
                  "password", ConnectorHandler.MASKED_VALUE));
      // encryptSensitiveFields encrypts "********" to garbage; restoreMaskedValues then
      // overwrites it with the original encrypted value from the existing entity.
      Map<String, Object> encrypted =
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://host/db",
                  "user", "admin",
                  "password", "enc_garbage"));

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.SQL)).thenReturn(sqlHandler);
      when(sqlHandler.normalizeAndValidate(any())).thenReturn(normalized);
      when(sqlHandler.getSensitiveFields()).thenReturn(Set.of("password"));
      when(sqlHandler.encryptSensitiveFields(any())).thenReturn(encrypted);

      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      dataSourceService.update(id, input);

      // The encrypted map should have the original encrypted value restored (not the garbage)
      assertThat(encrypted).containsEntry("password", "enc_secret");
    }

    @Test
    @DisplayName("Should not restore when new value is not masked")
    void shouldNotRestoreNonMaskedValues() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.SQL);
      entity.setConfiguration(
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://host/db",
                  "password", "enc_old")));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");
      input.setConnectorType(ConnectorType.SQL);
      input.setConfiguration(
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://host/db",
                  "user", "admin",
                  "password", "newpass")));

      Map<String, Object> normalized =
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://host/db",
                  "user", "admin",
                  "password", "newpass"));

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.SQL)).thenReturn(sqlHandler);
      when(sqlHandler.normalizeAndValidate(any())).thenReturn(normalized);
      when(sqlHandler.getSensitiveFields()).thenReturn(Set.of("password"));
      when(sqlHandler.encryptSensitiveFields(any())).thenAnswer(inv -> inv.getArgument(0));

      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      dataSourceService.update(id, input);

      assertThat(normalized).containsEntry("password", "newpass");
    }
  }

  private DataSource createMqttDataSource(UUID id) {
    DataSource entity = new DataSource();
    entity.setId(id);
    entity.setName("mqtt-source");
    entity.setDataSourceStatus(DataSourceStatus.DRAFT);
    entity.setConnectorType(ConnectorType.MQTT);
    entity.setConfiguration(
        Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("sensor/data"), "qos", 1));
    entity.setDataStructureVersion(createDataStructureVersion());
    return entity;
  }

  private DataSource createSqlDataSource(UUID id) {
    DataSource entity = new DataSource();
    entity.setId(id);
    entity.setName("sql-source");
    entity.setDataSourceStatus(DataSourceStatus.DRAFT);
    entity.setConnectorType(ConnectorType.SQL);
    entity.setConfiguration(
        Map.of(
            "driver", "postgres",
            "dsn", "postgres://host/db",
            "table", "users",
            "columns", List.of("id", "name"),
            "user", "dbuser",
            "password", "dbpass"));
    entity.setDataStructureVersion(createDataStructureVersion());
    return entity;
  }

  private DataStructureVersion createDataStructureVersion() {
    DataStructureVersion dsv = new DataStructureVersion();
    dsv.setId(UUID.randomUUID());
    return dsv;
  }
}
