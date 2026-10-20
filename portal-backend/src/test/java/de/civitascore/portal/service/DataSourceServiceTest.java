package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.connector.Secret;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataSourceMetaInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.security.AssignmentChangeAuthorizer;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.service.connector.ConnectorValidationException;
import de.civitascore.portal.service.connector.ConnectorValidator;
import de.civitascore.portal.service.connector.ConnectorViolation;
import de.civitascore.portal.service.connector.DataSourceConnectorStore;
import de.civitascore.portal.service.validation.DataSourceDatapoolScopeValidator;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
  @Spy private ConnectorValidator connectorValidator = new ConnectorValidator(List.of());

  @Mock private AssignmentService assignmentService;
  @Mock private DataStructureVersionService dataStructureVersionService;
  @Mock private PipelineRepository pipelineRepository;
  @Mock private DataPoolRepository dataPoolRepository;
  @Mock private DataSourceConnectorStore connectorStore;
  @Mock private ScopeAccessAuthorizer scopeAccessAuthorizer;
  @Mock private ArtifactUsageLookup artifactUsageLookup;
  @Mock private AssignmentFactory assignmentFactory;
  @Mock private AssignmentChangeAuthorizer assignmentChangeAuthorizer;

  @Spy private DataSourceDatapoolScopeValidator datapoolScopeValidator;

  @InjectMocks private DataSourceService dataSourceService;

  private static final ArtifactUsageLookup.ArtifactUsage NOT_IN_USE =
      new ArtifactUsageLookup.ArtifactUsage(false, List.of());
  private static final ArtifactUsageLookup.ArtifactUsage IN_USE_BY_DRAFTS =
      new ArtifactUsageLookup.ArtifactUsage(true, List.of());
  private static final ArtifactUsageLookup.ArtifactUsage IN_USE_BY_RELEASED =
      new ArtifactUsageLookup.ArtifactUsage(
          true,
          List.of(
              new ArtifactUsageLookup.ReleasedReferrer(
                  ArtifactUsageLookup.ReferrerKind.PIPELINE, UUID.randomUUID().toString())));

  @BeforeEach
  void stubUsage() {
    lenient().when(artifactUsageLookup.of(any(DataSource.class))).thenReturn(NOT_IN_USE);
  }

  private static SqlConnectorConfiguration sqlConnector(String password) {
    return new SqlConnectorConfiguration(
        "postgres",
        "postgres://host/db",
        "users",
        List.of("id"),
        null,
        "admin",
        Secret.of(password));
  }

  /** Pins a stored connector on the entity, read from its registry document. */
  private void stubStoredConnector(DataSource entity, Map<String, Object> document) {
    String urn = "urn:core:platform:civitas:datasource:common:" + entity.getId();
    entity.setConfigurationLogicalUrn(urn);
    entity.setConfigurationUrn(urn + ":1.0.0");
    lenient()
        .when(connectorStore.read(entity))
        .thenReturn(ConnectorJson.readStored(entity.getConnectorType(), document));
  }

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
    void create_withConnector_writesItForTheSavedDataSource() {
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("test-source");
      input.setConnector(sqlConnector("secret"));
      DataSource entity = new DataSource();

      when(dataSourceMapper.toEntity(any())).thenReturn(entity);
      when(dataSourceRepository.save(any()))
          .thenAnswer(
              invocation -> {
                entity.setId(UUID.randomUUID());
                return entity;
              });

      dataSourceService.create(input);

      ArgumentCaptor<DataSource> written = ArgumentCaptor.forClass(DataSource.class);
      verify(connectorStore).write(written.capture(), eq(input.getConnector()));
      assertThat(written.getValue().getId())
          .as("the registry artifact takes the id of the saved Data source")
          .isNotNull();
    }
  }

  @Nested
  @DisplayName("Release DataSource")
  class ReleaseTests {

    @Test
    @DisplayName("Should reject release when the DataStructureVersion is DRAFT")
    void shouldRejectReleaseWithDraftDataStructureVersion() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);
      entity
          .getDataStructureVersion()
          .setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataStructureVersion")
          .hasMessageContaining("AVAILABLE");
    }

    @Test
    @DisplayName("Should reject release when the parent DataStructure is DRAFT")
    void shouldRejectReleaseWithDraftParentDataStructure() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);
      entity
          .getDataStructureVersion()
          .getDataStructure()
          .setDataStructureStatus(DataStructureStatus.DRAFT);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("parent DataStructure")
          .hasMessageContaining("AVAILABLE");
    }

    @Test
    @DisplayName("Should release valid MQTT data source")
    void shouldReleaseValidMqttDataSource() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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

      DataSource result = dataSourceService.release(id);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.AVAILABLE);
    }

    @Test
    void release_refreshesTheElementOfTheReleasedDataSourceAfterValidation() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);
      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.release(id);

      InOrder order = inOrder(connectorStore, dataSourceRepository);
      order.verify(connectorStore).read(entity);
      order.verify(dataSourceRepository).save(entity);
      order.verify(connectorStore).refreshElement(entity);
      assertThat(result).isSameAs(entity);
    }

    @Test
    void release_rejected_refreshesNoElement() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);
      entity.setDataStructureVersion(null);
      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(InvalidInputException.class);
      verify(connectorStore, never()).refreshElement(any());
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
      entity.setDescription("A data source");
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
      entity.setDescription("A data source");
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
      entity.setDescription("A data source");
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      stubStoredConnector(entity, Map.of("topics", List.of("sensor/data"), "qos", 1));
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(ConnectorValidationException.class)
          .extracting(e -> ((ConnectorValidationException) e).getViolations())
          .isEqualTo(
              List.of(
                  new ConnectorViolation(
                      "connector.urls", "'urls' is required and must be a non-empty list")));
    }

    @Test
    @DisplayName("Should fail to release MQTT source with invalid qos")
    void shouldFailToReleaseMqttWithInvalidQos() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDescription("A data source");
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      stubStoredConnector(
          entity,
          Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("sensor/data"), "qos", 5));
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(ConnectorValidationException.class)
          .extracting(e -> ((ConnectorValidationException) e).getViolations())
          .isEqualTo(List.of(new ConnectorViolation("connector.qos", "'qos' must be 0, 1, or 2")));
    }

    @Test
    @DisplayName("Should fail to release MQTT source with more than one topic filter")
    void shouldFailToReleaseMqttWithMultipleTopics() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDescription("A data source");
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      stubStoredConnector(
          entity,
          Map.of(
              "urls",
              List.of("tcp://broker:1883"),
              "topics",
              List.of("sensor/a", "sensor/b"),
              "qos",
              1));
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(ConnectorValidationException.class)
          .extracting(e -> ((ConnectorValidationException) e).getViolations())
          .isEqualTo(
              List.of(
                  new ConnectorViolation(
                      "connector.topics",
                      "'topics' must contain exactly one topic filter; use wildcards (+, #) for"
                          + " multiple topics")));
    }

    @Test
    @DisplayName("Should fail to release SQL source without driver")
    void shouldFailToReleaseSqlWithoutDriver() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDescription("A data source");
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.SQL);
      stubStoredConnector(
          entity, Map.of("dsn", "postgres://host/db", "table", "users", "columns", List.of("id")));
      entity.setDataStructureVersion(createDataStructureVersion());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.release(id))
          .isInstanceOf(ConnectorValidationException.class)
          .extracting(e -> ((ConnectorValidationException) e).getViolations())
          .isEqualTo(
              List.of(
                  new ConnectorViolation(
                      "connector.driver", "'driver' is required and must not be blank")));
    }

    @Test
    @DisplayName("Should fail to release without a stored configuration")
    void shouldFailToReleaseWithEmptyConfiguration() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDescription("A data source");
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      entity.setConnectorType(ConnectorType.MQTT);
      // no configuration ever stored: the registry pin is null
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
      when(artifactUsageLookup.of(entity)).thenReturn(IN_USE_BY_DRAFTS);

      DataSource result = dataSourceService.unrelease(id);

      assertThat(result.getDataSourceStatus()).isEqualTo(DataSourceStatus.DRAFT);
    }

    @Test
    @DisplayName(
        "Should block unrelease when a Pipeline of an AVAILABLE DataSet references the DataSource")
    void shouldBlockUnreleaseWhenInUseByReleased() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(artifactUsageLookup.of(entity)).thenReturn(IN_USE_BY_RELEASED);

      assertThatThrownBy(() -> dataSourceService.unrelease(id))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("a Pipeline of a released Dataset");
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

    @Test
    @DisplayName("Should update name and description")
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

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      doThrow(new AccessDeniedException("denied"))
          .when(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATAPOOL), any());

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(AccessDeniedException.class);
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

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }

    @Test
    @DisplayName("Should not update configuration when not provided")
    void shouldNotUpdateConfigurationWhenNotProvided() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("old-name");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      entity.setConnectorType(ConnectorType.MQTT);
      stubStoredConnector(entity, Map.of("urls", List.of("tcp://broker:1883")));
      String originalPin = entity.getConfigurationUrn();

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setName("new-name");

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      verify(connectorStore, never()).write(any(), any());
      assertThat(result.getConfigurationUrn())
          .as("the stored content pin stays untouched")
          .isEqualTo(originalPin);
    }

    @Test
    @DisplayName("Should update assignments")
    void shouldUpdateAssignments() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setName("source");
      input.setAssignments(Set.of());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.updateReleasedMeta(id, input);

      assertThat(result.getAssignments()).isEmpty();
    }

    @Test
    @DisplayName("Should store nothing when the assignment change is denied")
    void shouldStoreNothingWhenAssignmentChangeDenied() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setName("source");
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      AssignmentScopedInputDTO requested = new AssignmentScopedInputDTO();
      requested.setGroupId(UUID.randomUUID());
      requested.setRoleId(UUID.randomUUID());

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setName("renamed");
      input.setAssignments(Set.of(requested));

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(assignmentFactory.build(requested)).thenReturn(new Assignment());
      doThrow(new AccessDeniedException("denied"))
          .when(assignmentChangeAuthorizer)
          .authorizeReplacement(eq(entity), any());

      assertThatThrownBy(() -> dataSourceService.updateReleasedMeta(id, input))
          .isInstanceOf(AccessDeniedException.class);
      assertThat(entity.getAssignments()).isEmpty();
      verify(dataSourceRepository, never()).save(any());
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

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
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

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
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

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
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
    @DisplayName("Should refuse to delete a DRAFT data source that a Pipeline references")
    void shouldRefuseDeletingDraftDataSourceReferencedByPipeline() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);

      when(dataSourceRepository.existsById(id)).thenReturn(true);
      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(pipelineRepository.existsByDataSourcesId(id)).thenReturn(true);

      assertThatThrownBy(() -> dataSourceService.deleteById(id))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("referenced by a Pipeline");
      verify(dataSourceRepository, never()).deleteById(id);
      verify(connectorStore, never()).delete(any());
    }

    @Test
    @DisplayName("Should delete a DRAFT data source that no Pipeline references")
    void shouldDeleteDraftDataSourceNotReferencedByPipeline() {
      UUID id = UUID.randomUUID();
      DataSource entity = createMqttDataSource(id);

      when(dataSourceRepository.existsById(id)).thenReturn(true);
      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(pipelineRepository.existsByDataSourcesId(id)).thenReturn(false);

      dataSourceService.deleteById(id);

      verify(dataSourceRepository).deleteById(id);
      verify(connectorStore).delete(entity);
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
    @DisplayName("Should link a DRAFT DataStructureVersion to a DRAFT DataSource")
    void shouldLinkDraftDataStructureVersion() {
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
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.create(input);

      assertThat(result.getDataStructureVersion()).isSameAs(dsv);
    }

    @Test
    @DisplayName("Should link a version whose parent DataStructure is DRAFT")
    void shouldLinkVersionWithDraftParentDataStructure() {
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
      when(dataSourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      DataSource result = dataSourceService.create(input);

      assertThat(result.getDataStructureVersion()).isSameAs(dsv);
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
      input.setConnector(
          new MqttConnectorConfiguration(null, null, null, null, null, null, null, null, null));
      input.setDataStructureVersionId(UUID.randomUUID());

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> dataSourceService.update(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("can only be updated in DRAFT status");
    }
  }

  @Nested
  @DisplayName("Update DataSource")
  class UpdateTests {

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
    void update_writesTheConnectorOfTheInput() {
      UUID id = UUID.randomUUID();
      DataSource entity = new DataSource();
      entity.setId(id);
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setName("updated");
      input.setConnector(sqlConnector(Secret.PLACEHOLDER));
      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      dataSourceService.update(id, input);

      verify(connectorStore).write(entity, input.getConnector());
    }

    @Test
    void update_withConnectorTypeChangeOnAvailable_isRejected() {
      UUID id = UUID.randomUUID();
      DataSource entity = createSqlDataSource(id);
      entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      DataSourceInputDTO input = new DataSourceInputDTO();
      input.setConnector(
          new MqttConnectorConfiguration(null, null, null, null, null, null, null, null, null));

      assertThatThrownBy(() -> dataSourceService.preProcessUpdateInput(input, entity))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Cannot change connector type");
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

      DataSourceMetaInputDTO input = new DataSourceMetaInputDTO();
      input.setName("source");
      input.setDatapoolScope(scope);

      when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
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
    entity.setDescription("An MQTT data source");
    entity.setDataSourceStatus(DataSourceStatus.DRAFT);
    entity.setConnectorType(ConnectorType.MQTT);
    stubStoredConnector(
        entity,
        Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("sensor/data"), "qos", 1));
    entity.setDataStructureVersion(createDataStructureVersion());
    return entity;
  }

  private DataSource createSqlDataSource(UUID id) {
    DataSource entity = new DataSource();
    entity.setId(id);
    entity.setName("sql-source");
    entity.setDescription("A SQL data source");
    entity.setDataSourceStatus(DataSourceStatus.DRAFT);
    entity.setConnectorType(ConnectorType.SQL);
    stubStoredConnector(
        entity,
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
    dsv.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
    DataStructure parent = new DataStructure();
    parent.setId(UUID.randomUUID());
    parent.setDataStructureStatus(DataStructureStatus.AVAILABLE);
    dsv.setDataStructure(parent);
    return dsv;
  }
}
