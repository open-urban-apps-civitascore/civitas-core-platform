package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.security.DataSourceDatapoolScopeValidator;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.service.connector.ConnectorHandlerRegistry;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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
  @Mock private DataSetRepository dataSetRepository;
  @Mock private PipelineRepository pipelineRepository;
  @Mock private DataPoolRepository dataPoolRepository;
  @Mock private ScopeAccessAuthorizer scopeAccessAuthorizer;

  @Spy private DataSourceDatapoolScopeValidator datapoolScopeValidator;

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
  @DisplayName("Release DataSource")
  class ReleaseTests {

    @Test
    @DisplayName("Should release valid MQTT data source")
    void shouldReleaseValidMqttDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.MQTT)).thenReturn(mqttHandler);
      when(mqttHandler.validate(any(), any(Class[].class))).thenReturn(Collections.emptyList());

      DataSource result = dataSourceService.release(id);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should release valid SQL data source")
    void shouldReleaseValidSqlDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = createSqlDataSource(id);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.SQL)).thenReturn(sqlHandler);
      when(sqlHandler.validate(any(), any(Class[].class))).thenReturn(Collections.emptyList());

      DataSource result = dataSourceService.release(id);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to release already AVAILABLE data source")
    void shouldFailToReleaseAvailableDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("Should fail to release without connector type")
    void shouldFailToReleaseWithoutConnectorType() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(null);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Connector type");
    }

    @Test
    @DisplayName("Should fail to release without data structure version")
    void shouldFailToReleaseWithoutDataStructureVersion() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setDataStructureVersion(null);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Data structure version");
    }

    @Test
    @DisplayName("Should fail to release MQTT source without urls")
    void shouldFailToReleaseMqttWithoutUrls() {
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

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("urls");
    }

    @Test
    @DisplayName("Should fail to release MQTT source with invalid qos")
    void shouldFailToReleaseMqttWithInvalidQos() {
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

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("qos");
    }

    @Test
    @DisplayName("Should fail to release SQL source without driver")
    void shouldFailToReleaseSqlWithoutDriver() {
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

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("driver");
    }

    @Test
    @DisplayName("Should fail to release with empty configuration")
    void shouldFailToReleaseWithEmptyConfiguration() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setConfiguration(Map.of());
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Configuration is required");
    }

    @Test
    @DisplayName("Should fail to release non-existent data source")
    void shouldFailToReleaseNonExistentDataSource() {
      UUID id = UUID.randomUUID();
      when(dataSourceRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Unrelease DataSource")
  class UnreleaseTests {

    @Test
    @DisplayName("Should unrelease AVAILABLE data source")
    void shouldUnreleaseAvailableDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(pipelineRepository.existsByDataSourcesId(any())).thenReturn(false);

      DataSource result = dataSourceService.unrelease(id);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.DRAFT);
    }

    @Test
    @DisplayName(
        "Should block unrelease when DataSource is referenced by a READY or AVAILABLE DataSet")
    void shouldBlockUnreleaseWhenInUse() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(pipelineRepository.existsByDataSourcesId(any())).thenReturn(true);

      assertThatThrownBy(() -> dataSourceService.unrelease(id))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should fail to unrelease DRAFT data source")
    void shouldFailToUnreleaseDraftDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.unrelease(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }
  }

  @Nested
  @DisplayName("Update Released Metadata")
  class UpdateReleasedMetaTests {

    private void stubNotInUse(UUID id) {
      when(pipelineRepository.existsByDataSourcesId(id)).thenReturn(false);
    }

    private void stubInUse(UUID id) {
      when(pipelineRepository.existsByDataSourcesId(id)).thenReturn(true);
    }

    @Test
    @DisplayName("Should update name and description when not in use")
    void shouldUpdateMetaOfAvailableDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("old-name");
      entity.setDescription("old-desc");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("new-name");
      input.setDescription("new-desc");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubNotInUse(id);
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getName()).isEqualTo("new-name");
      assertThat(result.getDescription()).isEqualTo("new-desc");
    }

    @Test
    @DisplayName(
        "Should deny updateReleasedMeta binding a DataPool the caller is not authorized for")
    void shouldDenyUnauthorizedDatapoolOnUpdateReleasedMeta() {
      UUID id = UUID.randomUUID();
      UUID poolId = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of(poolId));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubNotInUse(id);
      doThrow(new AccessDeniedException("denied"))
          .when(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATAPOOL), any());

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should deny updateReleasedMeta binding a DataStructure the caller cannot access")
    void shouldDenyUnauthorizedDataStructureOnUpdateReleasedMeta() {
      UUID id = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataStructureVersion dsv = createDataStructureVersion();
      dsv.setId(dsvId);
      dsv.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setDataStructureVersionId(dsvId);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubNotInUse(id);
      when(dataStructureVersionService.findByIdOrThrow(dsvId)).thenReturn(dsv);
      doThrow(new AccessDeniedException("denied"))
          .when(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATASTRUCTURE), any());

      // An unauthorized version must surface as the same not-found failure as a missing one, so
      // the caller cannot distinguish an existing-but-forbidden version from a non-existent one.
      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should fail to update metadata of DRAFT data source")
    void shouldFailToUpdateMetaOfDraftDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("new-name");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }

    @Test
    @DisplayName("Should only update provided fields when not in use")
    void shouldOnlyUpdateProvidedFields() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("original-name");
      entity.setDescription("original-desc");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated-name");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubNotInUse(id);
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getName()).isEqualTo("updated-name");
      assertThat(result.getDescription()).isEqualTo("original-desc");
    }

    @Test
    @DisplayName("Should update configuration when not in use")
    void shouldUpdateConfigurationOfAvailableDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("mqtt-source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setConfiguration(
          new java.util.HashMap<>(
              Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("old/topic"))));

      Map<String, Object> newConfig =
          new java.util.HashMap<>(
              Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("new/topic")));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("mqtt-source");
      input.setConfiguration(newConfig);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubNotInUse(id);
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.MQTT)).thenReturn(mqttHandler);
      when(mqttHandler.normalizeAndValidate(any())).thenReturn(newConfig);
      when(mqttHandler.encryptSensitiveFields(any())).thenAnswer(inv -> inv.getArgument(0));
      when(mqttHandler.getSensitiveFields()).thenReturn(Set.of());
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getConfiguration()).containsEntry("topics", List.of("new/topic"));
    }

    @Test
    @DisplayName("Should restore masked sensitive fields when updating configuration")
    void shouldRestoreMaskedFieldsWhenUpdatingConfiguration() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("sql-source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.SQL);
      entity.setConfiguration(
          new java.util.HashMap<>(
              Map.of("driver", "postgres", "dsn", "postgres://host/db", "password", "enc_secret")));

      Map<String, Object> normalized =
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://new-host/db",
                  "password", ConnectorHandler.MASKED_VALUE));
      Map<String, Object> encrypted =
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://new-host/db",
                  "password", "enc_garbage"));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("sql-source");
      input.setConfiguration(
          new java.util.HashMap<>(
              Map.of(
                  "driver", "postgres",
                  "dsn", "postgres://new-host/db",
                  "password", ConnectorHandler.MASKED_VALUE)));

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubNotInUse(id);
      when(connectorHandlerRegistry.getHandlerOrThrow(ConnectorType.SQL)).thenReturn(sqlHandler);
      when(sqlHandler.normalizeAndValidate(any())).thenReturn(normalized);
      when(sqlHandler.encryptSensitiveFields(any())).thenReturn(encrypted);
      when(sqlHandler.getSensitiveFields()).thenReturn(Set.of("password"));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getConfiguration()).containsEntry("password", "enc_secret");
      assertThat(result.getConfiguration()).containsEntry("dsn", "postgres://new-host/db");
    }

    @Test
    @DisplayName("Should not update configuration when not provided")
    void shouldNotUpdateConfigurationWhenNotProvided() {
      UUID id = UUID.randomUUID();
      Map<String, Object> originalConfig =
          new java.util.HashMap<>(Map.of("urls", List.of("tcp://broker:1883")));
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("old-name");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);
      entity.setConfiguration(originalConfig);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("new-name");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubNotInUse(id);
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getConfiguration()).isEqualTo(originalConfig);
    }

    @Test
    @DisplayName("Should update name and description when in use")
    void shouldUpdateNameAndDescriptionWhenInUse() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("old-name");
      entity.setDescription("old-desc");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("new-name");
      input.setDescription("new-desc");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getName()).isEqualTo("new-name");
      assertThat(result.getDescription()).isEqualTo("new-desc");
    }

    @Test
    @DisplayName("Should reject configuration change when in use")
    void shouldRejectConfigurationChangeWhenInUse() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("mqtt-source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("mqtt-source");
      input.setConfiguration(Map.of("urls", List.of("tcp://new-broker:1883")));

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("configuration")
          .hasMessageContaining("in use");
    }

    @Test
    @DisplayName("Should reject connector type change when in use")
    void shouldRejectConnectorTypeChangeWhenInUse() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("mqtt-source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("mqtt-source");
      input.setConnectorType(ConnectorType.SQL);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("connector type")
          .hasMessageContaining("in use");
    }

    @Test
    @DisplayName("Should reject data structure version change when in use")
    void shouldRejectDsvChangeWhenInUse() {
      UUID id = UUID.randomUUID();
      UUID existingDsvId = UUID.randomUUID();
      DataStructureVersion dsv = new DataStructureVersion();
      dsv.setId(existingDsvId);

      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setDataStructureVersion(dsv);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDataStructureVersionId(UUID.randomUUID());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("data structure version")
          .hasMessageContaining("in use");
    }

    @Test
    @DisplayName("Should allow same connector type when in use")
    void shouldAllowSameConnectorTypeWhenInUse() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("mqtt-source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("new-name");
      input.setConnectorType(ConnectorType.MQTT);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getName()).isEqualTo("new-name");
    }

    @Test
    @DisplayName("Should allow assignments update when in use")
    void shouldAllowAssignmentsUpdateWhenInUse() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setAssignments(Set.of());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getAssignments()).isEmpty();
    }

    @Test
    @DisplayName(
        "Should reject narrowing an in-use datasource's scope to exclude a pool it already feeds")
    void shouldRejectNarrowingScopeExcludingLinkedPool() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setDatapoolScopeType(DatapoolScopeType.ALL);

      // The datasource already feeds a dataset sitting in poolA.
      DataPool poolA = new DataPool();
      poolA.setId(UUID.randomUUID());
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setDataPool(poolA);
      Pipeline pipeline = new Pipeline();
      pipeline.setDataSet(dataSet);
      pipeline.setDataSources(new HashSet<>(Set.of(entity)));

      // Narrow the scope to a DIFFERENT pool (poolB), excluding poolA.
      DataPool poolB = new DataPool();
      poolB.setId(UUID.randomUUID());
      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of(poolB.getId()));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);
      when(dataPoolRepository.findAllById(List.of(poolB.getId()))).thenReturn(List.of(poolB));
      when(pipelineRepository.findByDataSourcesId(id)).thenReturn(List.of(pipeline));

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(id));
    }

    @Test
    @DisplayName("Should accept narrowing an in-use datasource's scope to a pool it already feeds")
    void shouldAcceptNarrowingScopeIncludingLinkedPool() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setDatapoolScopeType(DatapoolScopeType.ALL);

      DataPool poolA = new DataPool();
      poolA.setId(UUID.randomUUID());
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setDataPool(poolA);
      Pipeline pipeline = new Pipeline();
      pipeline.setDataSet(dataSet);
      pipeline.setDataSources(new HashSet<>(Set.of(entity)));

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of(poolA.getId()));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);
      when(dataPoolRepository.findAllById(List.of(poolA.getId()))).thenReturn(List.of(poolA));
      when(pipelineRepository.findByDataSourcesId(id)).thenReturn(List.of(pipeline));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getDatapoolScopeType()).isEqualTo(DatapoolScopeType.SPECIFIC);
    }

    @Test
    @DisplayName("Should reject narrowing an in-use datasource's scope to NONE")
    void shouldRejectNarrowingScopeToNone() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setDatapoolScopeType(DatapoolScopeType.ALL);

      DataPool poolA = new DataPool();
      poolA.setId(UUID.randomUUID());
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setDataPool(poolA);
      Pipeline pipeline = new Pipeline();
      pipeline.setDataSet(dataSet);
      pipeline.setDataSources(new HashSet<>(Set.of(entity)));

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.NONE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      stubInUse(id);
      when(pipelineRepository.findByDataSourcesId(id)).thenReturn(List.of(pipeline));

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(id));
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
          .hasMessageContaining("released");
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
      ds.setId(UUID.randomUUID());
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
      ds.setId(UUID.randomUUID());
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
    @DisplayName("Should authorize the parent structure id, not the version id, when linking")
    void shouldAuthorizeParentStructureNotVersion() {
      UUID dsvId = UUID.randomUUID();
      DataStructureVersion dsv = createDataStructureVersion();
      dsv.setId(dsvId);
      dsv.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dsv.getDataStructure().setDataStructureStatus(DataStructureStatus.AVAILABLE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("test");
      input.setDataStructureVersionId(dsvId);

      DataSource entity = new DataSource();
      entity.setId(UUID.randomUUID());

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataStructureVersionService.findByIdOrThrow(dsvId)).thenReturn(dsv);
      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      dataSourceService.create(input);

      // Assignments scope on the parent DataStructure; authorizing the version id would deny every
      // legitimate caller. This assertion fails if the guard is inverted to the version id.
      ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
      verify(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATASTRUCTURE), captor.capture());
      assertThat(captor.getValue())
          .containsExactly(dsv.getDataStructure().getId())
          .doesNotContain(dsvId);
    }

    @Test
    @DisplayName("Should deny an unauthorized structure indistinguishably from a missing version")
    void shouldDenyWhenNotAuthorizedForDataStructure() {
      UUID dsvId = UUID.randomUUID();
      DataStructureVersion dsv = createDataStructureVersion();
      dsv.setId(dsvId);
      dsv.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("test");
      input.setDataStructureVersionId(dsvId);

      DataSource entity = new DataSource();
      entity.setId(UUID.randomUUID());

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataStructureVersionService.findByIdOrThrow(dsvId)).thenReturn(dsv);
      doThrow(new AccessDeniedException("denied"))
          .when(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATASTRUCTURE), any());

      // An unauthorized version must surface as the same not-found failure as a missing one, so
      // the caller cannot distinguish an existing-but-forbidden version from a non-existent one.
      assertThatThrownBy(() -> dataSourceService.create(input))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should reject changing DSV on an in-use AVAILABLE data source")
    void shouldRejectChangingDsvWhenAvailableAndInUse() {
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
      when(pipelineRepository.existsByDataSourcesId(id)).thenReturn(true);

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("data structure version");
    }

    @Test
    @DisplayName("Should reject changing DSV on an AVAILABLE data source via the generic route")
    void shouldRejectChangingDsvWhenAvailableViaGenericUpdate() {
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
          .hasMessageContaining("can only be updated in DRAFT status");
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
    @DisplayName("Should prevent changing connector type of an in-use AVAILABLE data source")
    void shouldPreventChangingConnectorTypeWhenAvailableAndInUse() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");
      input.setConnectorType(ConnectorType.SQL);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(pipelineRepository.existsByDataSourcesId(id)).thenReturn(true);

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("connector type");
    }

    @Test
    @DisplayName("Should reject the generic update route for an AVAILABLE data source")
    void shouldRejectGenericUpdateWhenAvailable() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.update(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("can only be updated in DRAFT status");

      verify(dataSourceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject narrowing the datapool scope of an AVAILABLE source via update")
    void shouldRejectScopeNarrowingViaGenericUpdate() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setDatapoolScopeType(DatapoolScopeType.ALL);

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.NONE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.update(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("can only be updated in DRAFT status");

      // The scope must not have been applied before the guard rejected the call.
      assertThat(entity.getDatapoolScopeType()).isEqualTo(DatapoolScopeType.ALL);
      verify(dataSourceRepository, never()).save(any());
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

  @Nested
  @DisplayName("Datapool Scope")
  class DatapoolScopeTests {

    @Test
    @DisplayName("Should not modify scope when datapoolScope is null")
    void shouldNotModifyScopeWhenNull() {
      DataSource entity = DataSource.builder().build();
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSource result = dataSourceService.create(input);

      assertThat(result.getDatapoolScopeType()).isEqualTo(DatapoolScopeType.ALL);
      assertThat(result.getScopedDataPools()).isEmpty();
    }

    @Test
    @DisplayName("Should set scope to ALL and clear existing scoped pools")
    void shouldSetScopeToAll() {
      UUID poolId = UUID.randomUUID();
      DataPool existingPool = new DataPool();
      existingPool.setId(poolId);

      DataSource entity = DataSource.builder().build();
      entity.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      entity.getScopedDataPools().add(existingPool);

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.ALL);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSource result = dataSourceService.create(input);

      assertThat(result.getDatapoolScopeType()).isEqualTo(DatapoolScopeType.ALL);
      assertThat(result.getScopedDataPools()).isEmpty();
    }

    @Test
    @DisplayName("Should set scope to NONE and clear existing scoped pools")
    void shouldSetScopeToNone() {
      UUID poolId = UUID.randomUUID();
      DataPool existingPool = new DataPool();
      existingPool.setId(poolId);

      DataSource entity = DataSource.builder().build();
      entity.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      entity.getScopedDataPools().add(existingPool);

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.NONE);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSource result = dataSourceService.create(input);

      assertThat(result.getDatapoolScopeType()).isEqualTo(DatapoolScopeType.NONE);
      assertThat(result.getScopedDataPools()).isEmpty();
    }

    @Test
    @DisplayName("Should set scope to SPECIFIC and resolve DataPool entities by ID")
    void shouldSetScopeToSpecificAndResolveDataPools() {
      UUID poolId = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(poolId);

      DataSource entity = DataSource.builder().build();

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of(poolId));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.findAllById(List.of(poolId))).thenReturn(List.of(pool));
      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSource result = dataSourceService.create(input);

      assertThat(result.getDatapoolScopeType()).isEqualTo(DatapoolScopeType.SPECIFIC);
      assertThat(result.getScopedDataPools()).containsExactly(pool);
    }

    @Test
    @DisplayName("Should deny SPECIFIC scope when the caller is not authorized for a DataPool")
    void shouldDenyWhenNotAuthorizedForDatapool() {
      UUID poolId = UUID.randomUUID();
      DataSource entity = DataSource.builder().build();

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of(poolId));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      doThrow(new AccessDeniedException("denied"))
          .when(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATAPOOL), any());

      assertThatThrownBy(() -> dataSourceService.create(input))
          .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should reject SPECIFIC scope with null datapoolIds")
    void shouldRejectSpecificScopeWithNullIds() {
      DataSource entity = DataSource.builder().build();

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(null);

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);

      assertThatThrownBy(() -> dataSourceService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("At least one datapoolId");
    }

    @Test
    @DisplayName("Should reject SPECIFIC scope with empty datapoolIds list")
    void shouldRejectSpecificScopeWithEmptyIds() {
      DataSource entity = DataSource.builder().build();

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of());

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);

      assertThatThrownBy(() -> dataSourceService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("At least one datapoolId");
    }

    @Test
    @DisplayName("Should reject SPECIFIC scope with unknown DataPool ID")
    void shouldRejectSpecificScopeWithUnknownId() {
      UUID unknownId = UUID.randomUUID();
      DataSource entity = DataSource.builder().build();

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of(unknownId));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.findAllById(List.of(unknownId))).thenReturn(List.of());

      assertThatThrownBy(() -> dataSourceService.create(input))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should apply datapoolScope change via updateReleasedMeta")
    void shouldApplyScopeChangeViaUpdateReleasedMeta() {
      UUID id = UUID.randomUUID();
      UUID poolId = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(poolId);

      DataSource entity = DataSource.builder().build();
      entity.setId(id);
      entity.setName("source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
      scope.setType(DatapoolScopeType.SPECIFIC);
      scope.setDatapoolIds(List.of(poolId));

      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(pipelineRepository.existsByDataSourcesId(id)).thenReturn(false);
      when(dataPoolRepository.findAllById(List.of(poolId))).thenReturn(List.of(pool));
      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getDatapoolScopeType()).isEqualTo(DatapoolScopeType.SPECIFIC);
      assertThat(result.getScopedDataPools()).containsExactly(pool);
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
    DataStructure parent = new DataStructure();
    parent.setId(UUID.randomUUID());
    dsv.setDataStructure(parent);
    return dsv;
  }
}
