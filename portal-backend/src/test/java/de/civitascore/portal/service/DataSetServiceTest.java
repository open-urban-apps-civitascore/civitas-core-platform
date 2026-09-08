package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.messaging.saga.SagaResultPayload;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.service.validation.ClosureFinding;
import de.civitascore.portal.service.validation.DataSourceDatapoolScopeValidator;
import de.civitascore.portal.service.validation.PipelineClosureValidator;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.PipelineClosureValidationException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.SagaInFlightException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSetService Tests")
class DataSetServiceTest {

  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private LayerRepository layerRepository;
  @Mock private DataSetMapper dataSetMapper;
  @Mock private DataPoolRepository dataPoolRepository;
  @Mock private AssignmentFactory assignmentFactory;
  @Mock private DataSetSagaPublisher sagaPublisher;
  @Mock private PipelineRuntimeStatusService pipelineRuntimeStatusService;
  @Mock private ObjectProvider<AllowedScopes> allowedScopesProvider;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private PipelineClosureValidator pipelineClosureValidator;

  private DataSetService createService() {
    // Default to TENANT wildcard so the F4 target-pool check passes for existing pool-setting
    // tests;
    // F4-specific tests override allowedScopesProvider.getObject() after calling createService().
    lenient().when(allowedScopesProvider.getObject()).thenReturn(wildcardScopes());
    // Persisting a dataset creates its Model Forge manifest; return a dummy pin so tests exercising
    // create/update do not NPE (lenient — not every test triggers a persist).
    lenient()
        .when(modelRegistryGateway.createDataSetManifest(any()))
        .thenReturn(
            new ModelRegistryGateway.ModelPin(
                "urn:core:platform:civitas:dataset:common:test:abcdefghij",
                "urn:core:platform:civitas:dataset:common:test:abcdefghij:1.0.0",
                "1.0.0"));
    return new DataSetService(
        dataSetRepository,
        dataSinkRepository,
        layerRepository,
        dataSetMapper,
        dataPoolRepository,
        assignmentFactory,
        sagaPublisher,
        pipelineRuntimeStatusService,
        allowedScopesProvider,
        new DataSourceDatapoolScopeValidator(),
        modelRegistryGateway,
        new DataSetMutationGuard(dataSetRepository),
        pipelineClosureValidator);
  }

  private static AllowedScopes wildcardScopes() {
    AllowedScopes scopes = new AllowedScopes();
    scopes.setWildcard();
    return scopes;
  }

  private static AllowedScopes poolScopes(UUID... poolIds) {
    AllowedScopes scopes = new AllowedScopes();
    scopes.setPoolIds(Set.of(poolIds));
    return scopes;
  }

  private DataSet readyDataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setDataSetStatus(DataSetStatus.READY);
    ds.setPipelines(new HashSet<>());
    NamedApi api = new NamedApi();
    api.setName("Traffic Sensor Readings");
    api.setSlug("traffic");
    api.setStandard(ApiStandard.STA);
    ds.setNamedApis(new HashSet<>(Set.of(api)));
    return ds;
  }

  private DataSet availableDataSet(UUID id) {
    DataSet ds = readyDataSet(id);
    ds.setDataSetStatus(DataSetStatus.AVAILABLE);
    ds.setProvisioned(true);
    ds.setProjectId("proj-1");
    ds.setFrostBaseUrl("https://frost.example.com/Projects(1)");
    ds.getNamedApis().forEach(api -> api.setRouteId("route-1"));
    ds.setServiceId("svc-1");
    ds.setPublicUrl("https://example.com");
    ds.setPipelineIds(List.of("pipe-1"));
    return ds;
  }

  private DataSet draftDataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setName("test-dataset");
    ds.setDescription("test description");
    ds.setDataSetStatus(DataSetStatus.DRAFT);
    ds.setPipelines(new HashSet<>());
    return ds;
  }

  @Nested
  @DisplayName("stage()")
  class StageTests {

    @Test
    @DisplayName("stages dataset with feed-in pipeline (has datasources)")
    void stagesWithDatasources() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(List.of(new DataSource())));
      ds.getPipelines().add(p);

      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().stage(id);
      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    }

    @Test
    @DisplayName("rejects pipeline without datasources")
    void rejectsEmptyPipeline() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      ds.getPipelines().add(new Pipeline());

      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().stage(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataSources");
    }

    @Test
    @DisplayName("rejects dataset without pipelines")
    void rejectsNoPipelines() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);

      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().stage(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Pipeline");
    }

    @Test
    @DisplayName("stages dataset with valid named APIs")
    void stagesWithValidNamedApis() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSetWithPipeline(id);
      NamedApi api = new NamedApi();
      api.setName("Traffic");
      api.setSlug("traffic");
      api.setStandard(ApiStandard.STA);
      ds.setNamedApis(new HashSet<>(Set.of(api)));

      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().stage(id);
      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    }

    /** A DRAFT dataset that passes the pipeline precondition of {@code stage()}. */
    private DataSet draftDataSetWithPipeline(UUID id) {
      DataSet ds = draftDataSet(id);
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(List.of(new DataSource())));
      ds.getPipelines().add(p);
      return ds;
    }
  }

  @Nested
  @DisplayName("unstage()")
  class UnstageTests {

    @Test
    @DisplayName("throws when dataset not in READY status")
    void throwsWhenNotReady() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().unstage(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("READY");
    }

    @Test
    @DisplayName("throws SagaInFlightException when a CREATE saga is in-flight")
    void throwsWhenCreateSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().unstage(id))
          .isInstanceOfSatisfying(
              SagaInFlightException.class,
              exception -> {
                assertThat(exception.getDataSetId()).isEqualTo(id);
                assertThat(exception.getPendingSagaType()).isEqualTo(PendingSagaType.CREATE);
              })
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining(PendingSagaType.CREATE.name());
    }

    @Test
    @DisplayName("allows unstage while an UNRELEASE saga is in-flight (AVAILABLE -> DRAFT chain)")
    void allowsUnstageWhileUnreleaseInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().unstage(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.UNRELEASE);
    }

    @Test
    @DisplayName("reverts status to DRAFT")
    void revertsToDraft() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().unstage(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
    }
  }

  @Nested
  @DisplayName("release()")
  class ReleaseTests {

    @Test
    @DisplayName("throws when dataset not found")
    void throwsWhenDatasetNotFound() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("throws when dataset not in READY status")
    void throwsWhenNotReady() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("READY");
    }

    @Test
    @DisplayName("throws SagaInFlightException when a saga is in-flight")
    void throwsWhenSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOfSatisfying(
              SagaInFlightException.class,
              exception -> {
                assertThat(exception.getDataSetId()).isEqualTo(id);
                assertThat(exception.getPendingSagaType()).isEqualTo(PendingSagaType.DELETE);
              })
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining(PendingSagaType.DELETE.name());
    }

    @Test
    @DisplayName("sets status to AVAILABLE, pendingSagaType to CREATE, publishes trigger")
    void setsStatusAndPublishesTrigger() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.existsByDataSetIdAndDataSinkType(id, DataSinkType.FROST))
          .thenReturn(true);
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetService service = createService();
      DataSet result = service.release(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.CREATE);
      verify(sagaPublisher).publishCreateRequested(result);
    }

    @Test
    @DisplayName("rejects layers with no OWS named API to serve them")
    void rejectsLayersWithoutOwsApi() {
      // Layers and the OWS route are gated independently downstream, so this would provision
      // feature types that no route can reach and still report the release successful.
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(layerRepository.existsByDataSetId(id)).thenReturn(true);

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("no OWS named API");

      verify(sagaPublisher, never()).publishCreateRequested(any());
    }

    @Test
    @DisplayName("rejects an OWS named API with no POSTGIS sink behind it")
    void rejectsOwsApiWithoutPostgisSink() {
      // The route would rewrite to a workspace that is only provisioned when a POSTGIS sink
      // exists — without one the published endpoint 404s.
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.getNamedApis().forEach(api -> api.setStandard(ApiStandard.OWS));
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(layerRepository.existsByDataSetId(id)).thenReturn(false);
      when(dataSinkRepository.existsByDataSetIdAndDataSinkType(id, DataSinkType.POSTGIS))
          .thenReturn(false);

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("no POSTGIS data sink");

      verify(sagaPublisher, never()).publishCreateRequested(any());
    }

    @Test
    @DisplayName("rejects an STA named API with no FROST sink behind it")
    void rejectsStaApiWithoutFrostSink() {
      // The FROST project is only provisioned when a FROST sink exists — without one the published
      // STA endpoint has no upstream at all.
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(layerRepository.existsByDataSetId(id)).thenReturn(false);
      when(dataSinkRepository.existsByDataSetIdAndDataSinkType(id, DataSinkType.FROST))
          .thenReturn(false);

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("no FROST data sink");

      verify(sagaPublisher, never()).publishCreateRequested(any());
    }

    @Test
    @DisplayName("releases layers served by an OWS named API backed by a POSTGIS sink")
    void releasesCompleteMapSurface() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.getNamedApis().forEach(api -> api.setStandard(ApiStandard.OWS));
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(layerRepository.existsByDataSetId(id)).thenReturn(true);
      when(dataSinkRepository.existsByDataSetIdAndDataSinkType(id, DataSinkType.POSTGIS))
          .thenReturn(true);
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().release(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      verify(sagaPublisher).publishCreateRequested(result);
    }
  }

  @Nested
  @DisplayName("datapool scope re-validation (backstop)")
  class DatapoolScopeRevalidationTests {

    private DataPool pool(UUID id) {
      DataPool p = new DataPool();
      p.setId(id);
      return p;
    }

    private DataSource dataSource(UUID id, DatapoolScopeType scopeType, DataPool... scopedPools) {
      DataSource ds = new DataSource();
      ds.setId(id);
      ds.setDatapoolScopeType(scopeType);
      ds.setScopedDataPools(new HashSet<>(Set.of(scopedPools)));
      return ds;
    }

    private void addPipelineWithSource(DataSet dataSet, DataSource source) {
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(Set.of(source)));
      dataSet.getPipelines().add(p);
    }

    @Test
    @DisplayName(
        "release rejects when a pipeline datasource is out of scope for the dataset's pool")
    void releaseRejectsOutOfScopeDataSource() {
      UUID id = UUID.randomUUID();
      DataPool poolB = pool(UUID.randomUUID());
      DataSet ds = readyDataSet(id);
      ds.setDataPool(poolB);
      // SPECIFIC-scoped to a DIFFERENT pool than the dataset now sits in.
      UUID offendingId = UUID.randomUUID();
      addPipelineWithSource(
          ds, dataSource(offendingId, DatapoolScopeType.SPECIFIC, pool(UUID.randomUUID())));

      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(offendingId));
      verify(sagaPublisher, never()).publishCreateRequested(any());
    }

    @Test
    @DisplayName("release passes when the pipeline datasource is in scope for the dataset's pool")
    void releasePassesInScopeDataSource() {
      UUID id = UUID.randomUUID();
      DataPool poolB = pool(UUID.randomUUID());
      DataSet ds = readyDataSet(id);
      ds.setDataPool(poolB);
      addPipelineWithSource(ds, dataSource(UUID.randomUUID(), DatapoolScopeType.SPECIFIC, poolB));

      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.existsByDataSetIdAndDataSinkType(id, DataSinkType.FROST))
          .thenReturn(true);
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().release(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      verify(sagaPublisher).publishCreateRequested(result);
    }

    @Test
    @DisplayName("stage rejects when a pipeline datasource is out of scope for the dataset's pool")
    void stageRejectsOutOfScopeDataSource() {
      UUID id = UUID.randomUUID();
      DataPool poolB = pool(UUID.randomUUID());
      DataSet ds = draftDataSet(id);
      ds.setName("name");
      ds.setDescription("desc");
      ds.setDataPool(poolB);
      UUID offendingId = UUID.randomUUID();
      addPipelineWithSource(
          ds, dataSource(offendingId, DatapoolScopeType.SPECIFIC, pool(UUID.randomUUID())));

      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().stage(id))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(offendingId));
    }

    @Test
    @DisplayName("release rejects a pool-less dataset carrying a SPECIFIC-scoped datasource")
    void releaseRejectsSpecificSourceInPoolLessDataset() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataPool(null);
      UUID offendingId = UUID.randomUUID();
      addPipelineWithSource(
          ds, dataSource(offendingId, DatapoolScopeType.SPECIFIC, pool(UUID.randomUUID())));

      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(DataSourceScopeViolationException.class);
      verify(sagaPublisher, never()).publishCreateRequested(any());
    }
  }

  @Nested
  @DisplayName("unrelease()")
  class UnreleaseTests {

    @Test
    @DisplayName("throws when dataset not in AVAILABLE status")
    void throwsWhenNotAvailable() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().unrelease(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }

    @Test
    @DisplayName("throws SagaInFlightException when saga already in-flight")
    void throwsWhenSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().unrelease(id))
          .isInstanceOfSatisfying(
              SagaInFlightException.class,
              exception -> {
                assertThat(exception.getDataSetId()).isEqualTo(id);
                assertThat(exception.getPendingSagaType()).isEqualTo(PendingSagaType.CREATE);
              })
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining(PendingSagaType.CREATE.name());
    }

    @Test
    @DisplayName(
        "optimistically sets status to READY, pendingSagaType to UNRELEASE, publishes trigger")
    void setsReadyAndPendingUnreleaseAndPublishes() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetService service = createService();
      DataSet result = service.unrelease(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.UNRELEASE);
      // Infrastructure fields must survive unrelease so the teardown payload carries the resource
      // IDs; the completion callback clears route/pipeline, not unrelease.
      assertThat(result.getProjectId()).isEqualTo("proj-1");
      assertThat(result.getServiceId()).isEqualTo("svc-1");
      verify(sagaPublisher).publishUnreleaseRequested(result);
    }
  }

  @Nested
  @DisplayName("updateReleasedMeta()")
  class UpdateReleasedMetaTests {

    @ParameterizedTest(name = "{0}")
    @EnumSource(PendingSagaType.class)
    @DisplayName("throws SagaInFlightException for every saga type")
    void throwsWhenSagaInFlight(PendingSagaType pendingSagaType) {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(pendingSagaType);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOfSatisfying(
              SagaInFlightException.class,
              exception -> {
                assertThat(exception.getDataSetId()).isEqualTo(id);
                assertThat(exception.getPendingSagaType()).isEqualTo(pendingSagaType);
              })
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining(pendingSagaType.name());
    }

    @Test
    @DisplayName("allows update when no saga is pending")
    void allowsUpdateWhenNoSagaPending() {
      UUID id = UUID.randomUUID();
      UUID poolId = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      DataPool pool = new DataPool();
      pool.setId(poolId);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.of(pool));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      input.setDatapoolId(poolId);

      DataSet result = createService().updateReleasedMeta(id, input);
      assertThat(result).isNotNull();
      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }

    @Test
    @DisplayName("publishes an update for a provisioned dataset that has no FROST project")
    void publishesUpdateForProvisionedDatasetWithoutFrostProject() {
      // A dataset with no FROST sink has no projectId, so keying the publish on one would silently
      // stop all UPDATE sagas for it — no route auth re-apply, no GeoServer prune. Its named API is
      // OWS: an STA one could not have been released without a FROST sink in the first place.
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.getNamedApis().forEach(api -> api.setStandard(ApiStandard.OWS));
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setProvisioned(true);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      DataSet result = createService().updateReleasedMeta(id, input);

      assertThat(result.getProjectId()).isNull();
      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.UPDATE);
      verify(sagaPublisher).publishUpdateRequested(eq(result), any());
    }

    @Test
    @DisplayName("rejects a released-dataset pool switch to a pool a pipeline datasource is not in")
    void rejectsReleasedPoolSwitchWithOutOfScopeDataSource() {
      UUID id = UUID.randomUUID();
      UUID poolAId = UUID.randomUUID();
      UUID poolBId = UUID.randomUUID();
      DataPool poolA = new DataPool();
      poolA.setId(poolAId);
      DataPool poolB = new DataPool();
      poolB.setId(poolBId);

      DataSet ds = readyDataSet(id);
      ds.setDataPool(poolA);
      UUID offendingId = UUID.randomUUID();
      DataSource specificToA = new DataSource();
      specificToA.setId(offendingId);
      specificToA.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      specificToA.setScopedDataPools(new HashSet<>(Set.of(poolA)));
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(Set.of(specificToA)));
      ds.getPipelines().add(p);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataPoolRepository.findById(poolBId)).thenReturn(Optional.of(poolB));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setDatapoolId(poolBId);

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(offendingId));
      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }

    @Test
    @DisplayName(
        "rejects any non-null namedApis on /released/meta (concept #1379/#1384 immutability)")
    void rejectsAnyNamedApisOnReleased() {
      // The contract is that namedApis cannot appear at all on this endpoint — even a no-op
      // round-trip of the existing list is rejected, not just diffs.
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      NamedApiInputDTO roundTripped = new NamedApiInputDTO();
      roundTripped.setName("Traffic Sensor Readings");
      roundTripped.setSlug("traffic");
      roundTripped.setStandard(ApiStandard.STA);
      input.setNamedApis(List.of(roundTripped));

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("cannot be changed")
          .hasMessageContaining("AVAILABLE");

      // Pins the ordering contract: the namedApis guard fires BEFORE the saga trigger. Without
      // this, a regression that placed the check after publishUpdateRequested would leak an
      // UPDATE event for invalid input.
      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }

    @Test
    @DisplayName("rejects duplicate named-API slugs with a business 400 (not a DB 500)")
    void rejectsDuplicateNamedApiSlugs() {
      // Two named APIs sharing a slug must be rejected up front; otherwise both reach the DB unique
      // constraint as an opaque 500 (and the slug is the per-named-API route key).
      DataSet entity = new DataSet();
      NamedApiInputDTO first = new NamedApiInputDTO();
      first.setName("Traffic A");
      first.setSlug("traffic");
      first.setStandard(ApiStandard.STA);
      NamedApiInputDTO duplicate = new NamedApiInputDTO();
      duplicate.setName("Traffic B");
      duplicate.setSlug("traffic");
      duplicate.setStandard(ApiStandard.STA);
      DataSetInputDTO input = new DataSetInputDTO();
      input.setNamedApis(List.of(first, duplicate));

      assertThatThrownBy(() -> createService().postConvertToEntity(entity, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Duplicate named-API slug")
          .hasMessageContaining("traffic");
    }

    @Test
    @DisplayName("rejects a second OWS named API on the same dataset")
    void rejectsSecondOwsNamedApi() {
      DataSet entity = new DataSet();
      NamedApiInputDTO maps = new NamedApiInputDTO();
      maps.setName("Maps");
      maps.setSlug("maps");
      maps.setStandard(ApiStandard.OWS);
      NamedApiInputDTO alias = new NamedApiInputDTO();
      alias.setName("Maps Alias");
      alias.setSlug("maps-alias");
      alias.setStandard(ApiStandard.OWS);
      DataSetInputDTO input = new DataSetInputDTO();
      input.setNamedApis(List.of(maps, alias));

      assertThatThrownBy(() -> createService().postConvertToEntity(entity, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("at most one OWS named API");
    }

    @Test
    @DisplayName("accepts one OWS named API alongside other standards")
    void acceptsOneOwsApiWithOtherStandards() {
      DataSet entity = new DataSet();
      NamedApiInputDTO maps = new NamedApiInputDTO();
      maps.setName("Maps");
      maps.setSlug("maps");
      maps.setStandard(ApiStandard.OWS);
      NamedApiInputDTO sensors = new NamedApiInputDTO();
      sensors.setName("Sensors");
      sensors.setSlug("sensors");
      sensors.setStandard(ApiStandard.STA);
      DataSetInputDTO input = new DataSetInputDTO();
      input.setNamedApis(List.of(maps, sensors));
      when(dataSetMapper.toNamedApiEntity(any()))
          .thenAnswer(
              inv -> {
                NamedApiInputDTO dto = inv.getArgument(0);
                NamedApi api = new NamedApi();
                api.setName(dto.getName());
                api.setSlug(dto.getSlug());
                api.setStandard(dto.getStandard());
                return api;
              });

      assertThatCode(() -> createService().postConvertToEntity(entity, input))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects an empty namedApis list too (the field is forbidden, not just changes)")
    void rejectsEmptyNamedApisListOnReleased() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      input.setNamedApis(List.of());

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("cannot be changed")
          .hasMessageContaining("READY");

      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }

    @Test
    @DisplayName("allows updateReleasedMeta with namedApis omitted (PATCH semantics)")
    void allowsOmittedNamedApis() {
      UUID id = UUID.randomUUID();
      // availableDataSet seeds a NamedApi with slug "traffic"; the omit-means-unchanged
      // contract must leave that collection untouched.
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      // input.getNamedApis() stays null

      DataSet result = createService().updateReleasedMeta(id, input);
      assertThat(result.getNamedApis()).extracting(NamedApi::getSlug).containsExactly("traffic");
    }

    @Test
    @DisplayName("publishes an UPDATE saga with the pre-update pipelines for a provisioned dataset")
    void publishesUpdateSagaForAvailableDataSet() {
      // The saga trigger is the only path that propagates a metadata edit to the provisioned
      // infrastructure; without this test, removing it leaves the DB updated and NiFi/APISIX stale
      // with nothing failing. The pipeline snapshot must predate the update, since the publisher
      // derives removals from it.
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      Pipeline existing = new Pipeline();
      existing.setId(UUID.randomUUID());
      ds.getPipelines().add(existing);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      DataSet result = createService().updateReleasedMeta(id, input);

      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.UPDATE);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Set<Pipeline>> previousPipelines = ArgumentCaptor.forClass(Set.class);
      verify(sagaPublisher).publishUpdateRequested(eq(result), previousPipelines.capture());
      assertThat(previousPipelines.getValue()).containsExactly(existing);
    }

    @Test
    @DisplayName("publishes no saga for an AVAILABLE dataset that was never provisioned")
    void publishesNoSagaWithoutProjectId() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setProvisioned(false);
      ds.setProjectId(null);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      DataSet result = createService().updateReleasedMeta(id, input);

      assertThat(result.getPendingSagaType()).isNull();
      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }
  }

  @Nested
  @DisplayName("updateReadyMeta()")
  class UpdateReadyMetaTests {

    @Test
    @DisplayName("applies the updated fields to a READY dataset")
    void updatesReadyDataSet() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      input.setDescription("updated description");

      DataSet result = createService().updateReadyMeta(id, input);

      verify(dataSetMapper).updateEntity(ds, input);
      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(result.getPendingSagaType()).isNull();
      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }

    @Test
    @DisplayName("rejects a DRAFT dataset")
    void rejectsDraftDataSet() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(draftDataSet(id)));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      assertThatThrownBy(() -> createService().updateReadyMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("requires a READY dataset")
          .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("rejects an AVAILABLE dataset — releasing it makes /released/meta the only path")
    void rejectsAvailableDataSet() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(availableDataSet(id)));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      assertThatThrownBy(() -> createService().updateReadyMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("requires a READY dataset")
          .hasMessageContaining("AVAILABLE");
      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }
  }

  private enum MetaEndpoint {
    READY,
    RELEASED;

    DataSet update(DataSetService service, UUID id, DataSetInputDTO input) {
      return this == READY
          ? service.updateReadyMeta(id, input)
          : service.updateReleasedMeta(id, input);
    }
  }

  /**
   * Both endpoints delegate to the same private helper, so the shared guards are asserted against
   * both entry points rather than against one. A guard added to a single public method instead of
   * the helper fails here.
   */
  @Nested
  @DisplayName("shared metadata-update behaviour of /ready/meta and /released/meta")
  class SharedMetaUpdateTests {

    private DataSetInputDTO renameInput() {
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      return input;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MetaEndpoint.class)
    @DisplayName("accepts the same editable field set")
    void acceptsSameFieldSet(MetaEndpoint endpoint) {
      UUID id = UUID.randomUUID();
      UUID poolId = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(poolId);
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.of(pool));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      input.setDescription("updated description");
      input.setOpenDataAccess(true);
      input.setDatapoolId(poolId);

      DataSet result = endpoint.update(createService(), id, input);

      verify(dataSetMapper).updateEntity(ds, input);
      assertThat(result.getDataPool()).isEqualTo(pool);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MetaEndpoint.class)
    @DisplayName("rejects any non-null namedApis")
    void rejectsNamedApis(MetaEndpoint endpoint) {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(readyDataSet(id)));

      DataSetInputDTO input = renameInput();
      input.setNamedApis(List.of());

      assertThatThrownBy(() -> endpoint.update(createService(), id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("cannot be changed")
          .hasMessageContaining("READY");
      verify(dataSetMapper, never()).updateEntity(any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MetaEndpoint.class)
    @DisplayName("rejects an in-flight saga")
    void rejectsInFlightSaga(MetaEndpoint endpoint) {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> endpoint.update(createService(), id, renameInput()))
          .isInstanceOf(SagaInFlightException.class)
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining("CREATE");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MetaEndpoint.class)
    @DisplayName("enforces the datapool scope rule on a pool switch")
    void rejectsPoolSwitchWithOutOfScopeDataSource(MetaEndpoint endpoint) {
      UUID id = UUID.randomUUID();
      UUID poolAId = UUID.randomUUID();
      UUID poolBId = UUID.randomUUID();
      DataPool poolA = new DataPool();
      poolA.setId(poolAId);
      DataPool poolB = new DataPool();
      poolB.setId(poolBId);

      DataSet ds = readyDataSet(id);
      ds.setDataPool(poolA);
      UUID offendingId = UUID.randomUUID();
      DataSource specificToA = new DataSource();
      specificToA.setId(offendingId);
      specificToA.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      specificToA.setScopedDataPools(new HashSet<>(Set.of(poolA)));
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(Set.of(specificToA)));
      ds.getPipelines().add(p);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataPoolRepository.findById(poolBId)).thenReturn(Optional.of(poolB));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setDatapoolId(poolBId);

      assertThatThrownBy(() -> endpoint.update(createService(), id, input))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(offendingId));
    }
  }

  @Nested
  @DisplayName("handleSagaCompleted()")
  class HandleSagaCompletedTests {

    @Test
    @DisplayName("CREATE: persists infrastructure fields and clears pendingSagaType")
    void createPersistsInfrastructure() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      DataSet persisted = saved.getValue();
      assertThat(persisted.getProjectId()).isEqualTo("proj-1");
      assertThat(persisted.getFrostBaseUrl()).isEqualTo("https://frost.example.com");
      assertThat(persisted.getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactly(tuple("traffic", "route-1"));
      assertThat(persisted.getServiceId()).isEqualTo("svc-1");
      assertThat(persisted.getPublicUrl()).isEqualTo("https://public.example.com");
      assertThat(persisted.getPipelineIds()).containsExactly("pipe-1");
      assertThat(persisted.getPendingSagaType()).isNull();
      assertThat(persisted.isProvisioned()).isTrue();
    }

    @Test
    @DisplayName("UPDATE: a successful completion marks the dataset provisioned")
    void updateMarksProvisioned() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.UPDATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().isProvisioned()).isTrue();
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    private Pipeline attachedPipeline(DataSet ds, UUID pipelineId) {
      Pipeline pipeline = new Pipeline();
      pipeline.setId(pipelineId);
      ds.getPipelines().add(pipeline);
      return pipeline;
    }

    @Test
    @DisplayName("CREATE: marks the deployed pipelines as successfully deployed")
    void createMarksPipelinesDeployed() {
      UUID id = UUID.randomUUID();
      UUID firstPipeline = UUID.randomUUID();
      UUID secondPipeline = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      attachedPipeline(ds, firstPipeline);
      attachedPipeline(ds, secondPipeline);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1"),
              "svc-1",
              "https://public.example.com",
              List.of(firstPipeline.toString(), secondPipeline.toString()),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<List<String>> marked = ArgumentCaptor.captor();
      verify(pipelineRuntimeStatusService).markDeploymentSucceeded(marked.capture());
      assertThat(marked.getValue())
          .containsExactlyInAnyOrder(firstPipeline.toString(), secondPipeline.toString());
    }

    @Test
    @DisplayName("UPDATE: marks the deployed pipelines as successfully deployed")
    void updateMarksPipelinesDeployed() {
      UUID id = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.UPDATE);
      attachedPipeline(ds, pipelineId);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1"),
              "svc-1",
              "https://public.example.com",
              List.of(pipelineId.toString()),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      verify(pipelineRuntimeStatusService).markDeploymentSucceeded(List.of(pipelineId.toString()));
    }

    @Test
    @DisplayName("UPDATE: a pipeline the same saga tore down is not marked as deployed")
    void updateDoesNotMarkTornDownPipelines() {
      UUID id = UUID.randomUUID();
      UUID keptPipeline = UUID.randomUUID();
      UUID removedPipeline = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.UPDATE);
      // Only the kept pipeline is still attached; the removed one was detached before the saga ran.
      attachedPipeline(ds, keptPipeline);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // The adapter reports every id it processed, including the one it deleted.
      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1"),
              "svc-1",
              "https://public.example.com",
              List.of(keptPipeline.toString(), removedPipeline.toString()),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      verify(pipelineRuntimeStatusService)
          .markDeploymentSucceeded(List.of(keptPipeline.toString()));
    }

    @Test
    @DisplayName("UNRELEASE: torn-down pipelines are not marked as deployed")
    void unreleaseDoesNotMarkPipelinesDeployed() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(), null, null, null, null, null, List.of("pipe-1"), null, null, null);

      createService().handleSagaCompleted(id, result);

      verify(pipelineRuntimeStatusService, never()).markDeploymentSucceeded(any());
    }

    @Test
    @DisplayName("CREATE: writes per-slug routeIds across multiple named APIs")
    void createWritesMultipleRouteIds() {
      UUID id = UUID.randomUUID();
      DataSet ds = new DataSet();
      ds.setId(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      ds.setPipelines(new HashSet<>());
      NamedApi traffic = new NamedApi();
      traffic.setName("Traffic");
      traffic.setSlug("traffic");
      traffic.setStandard(ApiStandard.STA);
      NamedApi weather = new NamedApi();
      weather.setName("Weather");
      weather.setSlug("weather");
      weather.setStandard(ApiStandard.STA);
      ds.setNamedApis(new HashSet<>(Set.of(traffic, weather)));

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1", "weather", "route-2"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactlyInAnyOrder(tuple("traffic", "route-1"), tuple("weather", "route-2"));
    }

    @Test
    @DisplayName("CREATE: partial routeIds map leaves unmatched entries with null routeId")
    void createPartialRouteIdsLeavesEntriesUnchanged() {
      UUID id = UUID.randomUUID();
      DataSet ds = new DataSet();
      ds.setId(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      ds.setPipelines(new HashSet<>());
      NamedApi traffic = new NamedApi();
      traffic.setName("Traffic");
      traffic.setSlug("traffic");
      traffic.setStandard(ApiStandard.STA);
      NamedApi weather = new NamedApi();
      weather.setName("Weather");
      weather.setSlug("weather");
      weather.setStandard(ApiStandard.STA);
      ds.setNamedApis(new HashSet<>(Set.of(traffic, weather)));

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Saga returned only one of the two slugs
      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactlyInAnyOrder(tuple("traffic", "route-1"), tuple("weather", null));
    }

    @Test
    @DisplayName("CREATE: empty routeIds map leaves entity routeIds untouched")
    void createEmptyRouteIdsMapIsNoOp() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id); // routeId already "route-1" via helper
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // routeIds present but empty — distinct from null (which means "no infrastructure step
      // ran"). The branch must skip without overwriting anything.
      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(), null, null, Map.of(), null, null, null, null, null, null);

      createService().handleSagaCompleted(id, result);

      assertThat(ds.getNamedApis()).extracting(NamedApi::getRouteId).containsExactly("route-1");
    }

    @Test
    @DisplayName("CREATE: incoming routeId replaces a previously-set routeId for the same slug")
    void createIncomingRouteIdReplacesExisting() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      // Simulate: entity already has a routeId from a previous saga response.
      ds.getNamedApis().forEach(api -> api.setRouteId("route-old"));
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-NEW"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      Logger serviceLogger = (Logger) LoggerFactory.getLogger(DataSetService.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      serviceLogger.addAppender(appender);
      try {
        createService().handleSagaCompleted(id, result);
      } finally {
        serviceLogger.detachAppender(appender);
      }

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactly(tuple("traffic", "route-NEW"));
      assertThat(appender.list)
          .anyMatch(
              event ->
                  event.getLevel() == Level.ERROR
                      && event.getFormattedMessage().contains("drift=replaced-routeid")
                      && event.getFormattedMessage().contains("traffic")
                      && event.getFormattedMessage().contains("route-old")
                      && event.getFormattedMessage().contains("route-NEW"));
    }

    @Test
    @DisplayName("CREATE: orphan slug in saga result is logged as drift but does not throw")
    void createOrphanSlugSilentlySkipped() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Saga returned a slug that doesn't exist on the entity (drift)
      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1", "ghost", "route-orphan"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      // The matching slug still gets its routeId; orphan is ignored without throwing.
      assertThat(saved.getValue().getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactly(tuple("traffic", "route-1"));
    }

    @Test
    @DisplayName(
        "CREATE: routeIds returned for entity without namedApis is logged as drift but does not"
            + " throw")
    void createUnexpectedRouteIdsLoggedAsDrift() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      ds.setNamedApis(new HashSet<>());
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("ghost", "route-orphan"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      Logger serviceLogger = (Logger) LoggerFactory.getLogger(DataSetService.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      serviceLogger.addAppender(appender);
      try {
        createService().handleSagaCompleted(id, result);
      } finally {
        serviceLogger.detachAppender(appender);
      }

      assertThat(appender.list)
          .anyMatch(
              event ->
                  event.getLevel() == Level.ERROR
                      && event.getFormattedMessage().contains("drift=unexpected-routeids")
                      && event.getFormattedMessage().contains("ghost"));
    }

    @Test
    @DisplayName("UNRELEASE: clears only route/pipeline fields, keeps the sink, stays READY")
    void unreleaseClearsRouteAndPipelineKeepsSink() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      // unrelease set READY optimistically before the saga ran.
      ds.setDataSetStatus(DataSetStatus.READY);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      DataSet persisted = saved.getValue();
      assertThat(persisted.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(persisted.getPendingSagaType()).isNull();
      // Route/consumer-access layer torn down (routes/upstream deleted by DELETE_ROUTE).
      assertThat(persisted.getPipelineIds()).isNull();
      assertThat(persisted.getNamedApis()).allMatch(api -> api.getRouteId() == null);
      assertThat(persisted.getServiceId()).isNull();
      assertThat(persisted.getPublicUrl()).isNull();
      // Data-holding sink references survive so a re-release reuses the existing data.
      assertThat(persisted.getProjectId()).isEqualTo("proj-1");
      assertThat(persisted.getFrostBaseUrl()).isEqualTo("https://frost.example.com/Projects(1)");
      verify(dataSetRepository, never()).delete(any(DataSet.class));
    }

    @Test
    @DisplayName("UNRELEASE: leaves a user-moved DRAFT status untouched")
    void unreleaseLeavesDraftUntouched() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      // AVAILABLE -> DRAFT chain moved the dataset on to DRAFT while the teardown was running.
      ds.setDataSetStatus(DataSetStatus.DRAFT);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("DELETE: removes the entity after teardown, without a status save")
    void deleteRemovesEntity() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      verify(dataSetRepository).delete(ds);
      verify(dataSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("CREATE: marks the dataset provisioned")
    void createMarksProvisioned() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      ds.setProvisioned(false);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().isProvisioned()).isTrue();
    }

    @Test
    @DisplayName("CREATE: marks a dataset with no FROST project provisioned")
    void createMarksProvisionedWithoutFrostProject() {
      // A dataset with no FROST sink gets no project, so the flag cannot be inferred from one — and
      // without the flag its delete would skip the teardown saga and leak the rest of its
      // infrastructure.
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      ArgumentCaptor<DataSet> savedWithoutProject = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(savedWithoutProject.capture());
      assertThat(savedWithoutProject.getValue().getProjectId()).isNull();
      assertThat(savedWithoutProject.getValue().isProvisioned()).isTrue();
    }

    @Test
    @DisplayName("no pending saga: skips save (duplicate delivery)")
    void noPendingSagaSkipsSave() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(null);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      verify(dataSetRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("handleSagaFailed()")
  class HandleSagaFailedTests {

    @Test
    @DisplayName("CREATE failure: reverts status to READY")
    void createFailureRevertsToReady() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "FROST", "timeout", true);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("UNRELEASE failure: reverts optimistic READY to AVAILABLE, pending cleared")
    void unreleaseFailureRevertsToAvailable() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setDataSetStatus(DataSetStatus.READY);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "delete-route", "APISIX down", false);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      // A failed teardown must NOT stay READY — live routes may remain, so undo the optimistic set.
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("UNRELEASE failure: leaves a user-moved DRAFT status untouched")
    void unreleaseFailureLeavesDraftUntouched() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setDataSetStatus(DataSetStatus.DRAFT);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "delete-route", "APISIX down", false);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("DELETE failure: stays AVAILABLE, pending cleared, entity kept")
    void deleteFailureStaysAvailable() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "deprovision-sink", "PostGIS down", false);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
      verify(dataSetRepository, never()).delete(any(DataSet.class));
    }

    @Test
    @DisplayName("UPDATE failure: stays AVAILABLE, pending cleared")
    void updateFailureStaysAvailable() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.UPDATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "update-route", "APISIX timeout", false);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("no pending saga: skips save (duplicate delivery)")
    void noPendingSagaSkipsSave() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(null);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      createService().handleSagaFailed(id, "any", "any", false);

      verify(dataSetRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("datapool resolution")
  class DataPoolResolutionTests {

    @Test
    @DisplayName("create throws ResourceNotFoundException when datapoolId points to unknown pool")
    void createRejectsUnknownDatapoolId() {
      UUID poolId = UUID.randomUUID();
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("ds");
      input.setDatapoolId(poolId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      when(dataSetMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> createService().create(input))
          .isInstanceOf(ResourceNotFoundException.class);
      verify(dataSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create resolves datapoolId and assigns the DataPool to the entity")
    void createAssignsResolvedDataPool() {
      UUID poolId = UUID.randomUUID();
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("ds");
      input.setDatapoolId(poolId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      DataPool pool = new DataPool();
      pool.setId(poolId);

      when(dataSetMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.of(pool));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet saved = createService().create(input);
      assertThat(saved.getDataPool()).isSameAs(pool);
    }

    @Test
    @DisplayName("create rejects a target datapool the caller is not authorized for (F4)")
    void createRejectsUnauthorizedTargetPool() {
      UUID poolId = UUID.randomUUID();
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("ds");
      input.setDatapoolId(poolId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      DataPool pool = new DataPool();
      pool.setId(poolId);

      DataSetService service = createService();
      when(dataSetMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.of(pool));
      // Caller is authorized only for a DIFFERENT pool → the target pool is off-limits.
      when(allowedScopesProvider.getObject()).thenReturn(poolScopes(UUID.randomUUID()));

      assertThatThrownBy(() -> service.create(input)).isInstanceOf(AccessDeniedException.class);
      verify(dataSetRepository, never()).save(any());
    }

    @Test
    @DisplayName(
        "create with null datapoolId creates dataset without pool (datapoolId is optional)")
    void createWithNullDatapoolIdLeavesDataPoolNull() {
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("ds");

      DataSet entity = draftDataSet(UUID.randomUUID());
      when(dataSetMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet saved = createService().create(input);
      assertThat(saved.getDataPool()).isNull();
      verify(dataPoolRepository, never()).findById(any());
    }

    @Test
    @DisplayName("update with an omitted datapoolId leaves the DataPool untouched")
    void updateWithOmittedDatapoolIdKeepsDataPool() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      DataPool existingPool = new DataPool();
      existingPool.setId(UUID.randomUUID());
      ds.setDataPool(existingPool);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      DataSet result = createService().update(id, input);
      assertThat(result.getDataPool()).isSameAs(existingPool);
      verify(dataPoolRepository, never()).findById(any());
    }

    @Test
    @DisplayName("update with an explicit null datapoolId unassigns the DataPool")
    void updateWithExplicitNullDatapoolIdUnassignsDataPool() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      DataPool existingPool = new DataPool();
      existingPool.setId(UUID.randomUUID());
      ds.setDataPool(existingPool);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      input.setDatapoolId(null);

      DataSet result = createService().update(id, input);
      assertThat(result.getDataPool()).isNull();
      verify(dataPoolRepository, never()).findById(any());
    }

    @Test
    @DisplayName("a rename-only update does not trip the scope guard on a pooled dataset")
    void renameOnlyUpdateKeepsPoolAndPassesScopeGuard() {
      UUID id = UUID.randomUUID();
      UUID poolId = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(poolId);

      DataSet entity = draftDataSet(id);
      entity.setDataPool(pool);

      DataSource specificToPool = new DataSource();
      specificToPool.setId(UUID.randomUUID());
      specificToPool.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      specificToPool.setScopedDataPools(new HashSet<>(Set.of(pool)));
      Pipeline pipeline = new Pipeline();
      pipeline.setDataSources(new HashSet<>(Set.of(specificToPool)));
      entity.setPipelines(new HashSet<>(Set.of(pipeline)));

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(entity));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // No datapoolId in the body: the pool must survive, so the SPECIFIC source stays in scope
      // instead of being validated against a pool-less dataset.
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("renamed");

      DataSet result = createService().update(id, input);
      assertThat(result.getDataPool()).isSameAs(pool);
    }

    @Test
    @DisplayName(
        "switching a dataset to a pool its pipeline's SPECIFIC datasource is not scoped for is"
            + " rejected")
    void poolSwitchRejectsOutOfScopePipelineDataSource() {
      UUID poolAId = UUID.randomUUID();
      UUID poolBId = UUID.randomUUID();
      DataPool poolA = new DataPool();
      poolA.setId(poolAId);
      DataPool poolB = new DataPool();
      poolB.setId(poolBId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      entity.setDataPool(poolA);
      UUID offendingId = UUID.randomUUID();
      DataSource specificToA = new DataSource();
      specificToA.setId(offendingId);
      specificToA.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      specificToA.setScopedDataPools(new HashSet<>(Set.of(poolA)));
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(Set.of(specificToA)));
      entity.getPipelines().add(p);

      when(dataPoolRepository.findById(poolBId)).thenReturn(Optional.of(poolB));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setDatapoolId(poolBId);

      assertThatThrownBy(() -> createService().postConvertToEntity(entity, input))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(offendingId));
    }

    @Test
    @DisplayName("switching pool leaves an ALL-scoped pipeline datasource accepted")
    void poolSwitchAcceptsAllScopedPipelineDataSource() {
      UUID poolAId = UUID.randomUUID();
      UUID poolBId = UUID.randomUUID();
      DataPool poolA = new DataPool();
      poolA.setId(poolAId);
      DataPool poolB = new DataPool();
      poolB.setId(poolBId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      entity.setDataPool(poolA);
      DataSource allScoped = new DataSource();
      allScoped.setId(UUID.randomUUID());
      allScoped.setDatapoolScopeType(DatapoolScopeType.ALL);
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(Set.of(allScoped)));
      entity.getPipelines().add(p);

      when(dataPoolRepository.findById(poolBId)).thenReturn(Optional.of(poolB));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setDatapoolId(poolBId);

      DataSet result = createService().postConvertToEntity(entity, input);
      assertThat(result.getDataPool()).isSameAs(poolB);
    }

    @Test
    @DisplayName("an update that leaves the pool unchanged accepts an in-scope bound datasource")
    void poolUnchangedAcceptsInScopeBoundDataSource() {
      UUID poolId = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(poolId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      entity.setDataPool(pool);
      DataSource specificToPool = new DataSource();
      specificToPool.setId(UUID.randomUUID());
      specificToPool.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      specificToPool.setScopedDataPools(new HashSet<>(Set.of(pool)));
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(Set.of(specificToPool)));
      entity.getPipelines().add(p);

      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.of(pool));

      // A PATCH re-sends the existing datapoolId (no pool change); the re-validation must accept
      // the
      // still-in-scope bound datasource rather than reject a legitimate metadata edit.
      DataSetInputDTO input = new DataSetInputDTO();
      input.setDatapoolId(poolId);

      DataSet result = createService().postConvertToEntity(entity, input);
      assertThat(result.getDataPool()).isSameAs(pool);
    }
  }

  @Nested
  @DisplayName("deleteById() — teardown routing")
  class DeleteByIdTests {

    @Test
    @DisplayName("routes a released dataset through the teardown saga")
    void routesReleasedDatasetThroughSaga() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setProvisioned(true);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().deleteById(id);

      // No FROST project on this dataset: the teardown must still run for its other infrastructure.
      assertThat(ds.getProjectId()).isNull();
      assertThat(ds.getPendingSagaType()).isEqualTo(PendingSagaType.DELETE);
      verify(sagaPublisher).publishDeleteRequested(ds);
      verify(dataSetRepository, never()).delete(any(DataSet.class));
    }

    @Test
    @DisplayName("throws SagaInFlightException when a saga is in-flight")
    void throwsWhenSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setProvisioned(true);
      ds.setPendingSagaType(PendingSagaType.UPDATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().deleteById(id))
          .isInstanceOfSatisfying(
              SagaInFlightException.class,
              exception -> {
                assertThat(exception.getDataSetId()).isEqualTo(id);
                assertThat(exception.getPendingSagaType()).isEqualTo(PendingSagaType.UPDATE);
              })
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining(PendingSagaType.UPDATE.name());
      verify(sagaPublisher, never()).publishDeleteRequested(any());
    }

    @Test
    @DisplayName(
        "removes a never-released dataset directly, even when it configures a PostGIS sink")
    void removesNeverReleasedPostgisDatasetDirectly() {
      // A sink row exists from the moment it is configured, long before anything is provisioned.
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setProvisioned(false);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      lenient()
          .when(dataSinkRepository.existsByDataSetIdAndDataSinkType(id, DataSinkType.POSTGIS))
          .thenReturn(true);
      when(dataSinkRepository.findByDataSetId(id)).thenReturn(List.of());

      createService().deleteById(id);

      verify(dataSetRepository).delete(ds);
      verify(sagaPublisher, never()).publishDeleteRequested(any());
    }

    private static final String PIPELINE_URN =
        "urn:core:platform:civitas:pipeline:common:doomed-pipeline:abcdefghij";
    private static final String SINK_URN =
        "urn:core:platform:civitas:data-sink:common:doomed-sink:abcdefghij";

    /** A dataset carrying one pipeline that wires one mapping and one sink. */
    private DataSet datasetWithOwnedArtifacts(UUID id, String manifestUrn) {
      DataSet ds = readyDataSet(id);
      ds.setManifestLogicalUrn(manifestUrn);
      Pipeline pipeline = new Pipeline();
      pipeline.setId(UUID.randomUUID());
      pipeline.setModelLogicalUrn(PIPELINE_URN);
      ds.setPipelines(new java.util.HashSet<>(Set.of(pipeline)));
      return ds;
    }

    private DataSink ownedSink() {
      DataSink sink = new DataSink();
      sink.setId(UUID.randomUUID());
      sink.setConfigurationLogicalUrn(SINK_URN);
      return sink;
    }

    private void stubOwnedArtifacts(UUID id, DataSet ds) {
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findByDataSetId(id)).thenReturn(List.of(ownedSink()));
    }

    @Test
    @DisplayName("removing a dataset directly deletes the artifacts it owned")
    void directRemovalDeletesRegistryArtifacts() {
      UUID id = UUID.randomUUID();
      DataSet ds =
          datasetWithOwnedArtifacts(
              id, "urn:core:platform:civitas:dataset:common:doomed:abcdefghij");
      ds.setProvisioned(false);
      stubOwnedArtifacts(id, ds);

      createService().deleteById(id);

      // The manifest's membership edges carry the pipelines, and each pipeline the mappings no
      // other pipeline uses, so one cascading removal covers them all.
      verify(modelRegistryGateway).deleteArtifact(ds.getManifestLogicalUrn(), true);
      verify(modelRegistryGateway).deleteArtifact(SINK_URN, false);
    }

    @Test
    @DisplayName("the manifest goes before the sink configurations its pipelines wrote through")
    void deletesArtifactsInReferenceOrder() {
      UUID id = UUID.randomUUID();
      DataSet ds =
          datasetWithOwnedArtifacts(
              id, "urn:core:platform:civitas:dataset:common:ordered:abcdefghij");
      ds.setProvisioned(false);
      stubOwnedArtifacts(id, ds);

      createService().deleteById(id);

      // Order is the whole point: a referenced artifact cannot be deleted, and the pipelines the
      // manifest cascade takes hold the edges onto the sink configurations.
      InOrder order = inOrder(modelRegistryGateway);
      order.verify(modelRegistryGateway).deleteArtifact(ds.getManifestLogicalUrn(), true);
      order.verify(modelRegistryGateway).deleteArtifact(SINK_URN, false);
    }

    @Test
    @DisplayName("a refused removal fails the whole delete rather than stranding the artifact")
    void refusedRemovalFailsTheWholeDelete() {
      UUID id = UUID.randomUUID();
      DataSet ds =
          datasetWithOwnedArtifacts(
              id, "urn:core:platform:civitas:dataset:common:shared:abcdefghij");
      ds.setProvisioned(false);
      stubOwnedArtifacts(id, ds);
      doThrow(new IllegalStateException("still referenced"))
          .when(modelRegistryGateway)
          .deleteArtifact(ds.getManifestLogicalUrn(), true);

      // Swallowing this would commit the row deletions and leave the artifact behind with nothing
      // left to reach it; the transaction rolls back instead.
      assertThatThrownBy(() -> createService().deleteById(id))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a refusal after the teardown saga does not restore the dataset")
    void refusalAfterTeardownDoesNotRestoreTheDataSet() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setProvisioned(true);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      ds.setManifestLogicalUrn("urn:core:platform:civitas:dataset:common:torn-down:abcdefghij");

      DataSink sink = new DataSink();
      sink.setId(UUID.randomUUID());
      sink.setConfigurationLogicalUrn(
          "urn:core:platform:civitas:data-sink:common:torn-down-sink:abcdefghij");

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findByDataSetId(id)).thenReturn(List.of(sink));
      doThrow(new IllegalStateException("still referenced"))
          .when(modelRegistryGateway)
          .deleteArtifact(sink.getConfigurationLogicalUrn(), false);

      // The infrastructure this dataset described is already gone. Letting the refusal out would
      // roll the row back with its pending saga type set, and every later delete refuses that.
      assertThatCode(
              () ->
                  createService()
                      .handleSagaCompleted(
                          id,
                          new SagaResultPayload(
                              id.toString(), null, null, null, null, null, null, null, null, null)))
          .doesNotThrowAnyException();
      verify(dataSetRepository).delete(ds);
    }

    @Test
    @DisplayName("completing the teardown saga deletes the manifest and the sinks' configurations")
    void sagaCompletionDeletesRegistryArtifacts() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setProvisioned(true);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      ds.setManifestLogicalUrn("urn:core:platform:civitas:dataset:common:torn-down:abcdefghij");

      DataSink sink = new DataSink();
      sink.setId(UUID.randomUUID());
      sink.setConfigurationLogicalUrn(
          "urn:core:platform:civitas:data-sink:common:torn-down-sink:abcdefghij");

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findByDataSetId(id)).thenReturn(List.of(sink));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      verify(modelRegistryGateway).deleteArtifact(ds.getManifestLogicalUrn(), true);
      verify(modelRegistryGateway).deleteArtifact(sink.getConfigurationLogicalUrn(), false);
    }
  }

  @Nested
  @DisplayName("orphaned-layer cleanup on namedApis reconciliation")
  class OwsLayerCleanupTests {

    @BeforeEach
    void mapIncomingApis() {
      // Every case here adds at least one slug the entity does not carry yet, which routes through
      // the mapper.
      lenient()
          .when(dataSetMapper.toNamedApiEntity(any()))
          .thenAnswer(
              inv -> {
                NamedApiInputDTO dto = inv.getArgument(0);
                return NamedApi.builder()
                    .name(dto.getName())
                    .slug(dto.getSlug())
                    .standard(dto.getStandard())
                    .build();
              });
    }

    private DataSetInputDTO inputWithApis(NamedApiInputDTO... apis) {
      DataSetInputDTO input = new DataSetInputDTO();
      input.setNamedApis(List.of(apis));
      return input;
    }

    private NamedApiInputDTO api(String slug, ApiStandard standard) {
      NamedApiInputDTO dto = new NamedApiInputDTO();
      dto.setName(slug);
      dto.setSlug(slug);
      dto.setStandard(standard);
      return dto;
    }

    /** A persisted dataset already exposing one OWS named API, as the update path sees it. */
    private DataSet persistedWithOwsApi(UUID id) {
      DataSet entity = new DataSet();
      entity.setId(id);
      entity.setNamedApis(
          new HashSet<>(
              Set.of(
                  NamedApi.builder().name("Maps").slug("maps").standard(ApiStandard.OWS).build())));
      return entity;
    }

    @Test
    @DisplayName("deletes the layers when the reconciled state has no OWS named API left")
    void deletesLayersWhenNoOwsApiRemains() {
      UUID id = UUID.randomUUID();
      DataSet entity = persistedWithOwsApi(id);

      createService().postConvertToEntity(entity, inputWithApis(api("sensors", ApiStandard.STA)));

      verify(layerRepository).deleteByDataSetId(id);
    }

    @Test
    @DisplayName("deletes the layers of a dataset that never had an OWS named API")
    void deletesLayersWhenNoOwsApiEverExisted() {
      // Keying on a disappeared OWS entry instead would leave these layers unserved indefinitely.
      UUID id = UUID.randomUUID();
      DataSet entity = new DataSet();
      entity.setId(id);
      entity.setNamedApis(new HashSet<>());

      createService().postConvertToEntity(entity, inputWithApis(api("sensors", ApiStandard.STA)));

      verify(layerRepository).deleteByDataSetId(id);
    }

    @Test
    @DisplayName("keeps the layers while an OWS named API remains")
    void keepsLayersWhileOwsApiRemains() {
      UUID id = UUID.randomUUID();
      DataSet entity = persistedWithOwsApi(id);

      createService()
          .postConvertToEntity(
              entity, inputWithApis(api("maps", ApiStandard.OWS), api("sensors", ApiStandard.STA)));

      verify(layerRepository, never()).deleteByDataSetId(any());
    }

    @Test
    @DisplayName("keeps the layers when the OWS named API is replaced by a differently-slugged one")
    void keepsLayersWhenOwsApiIsReslugged() {
      // The remove+add of the row must not read as "no OWS API left" — the cleanup keys on the
      // reconciled state, not on the removed entry.
      UUID id = UUID.randomUUID();
      DataSet entity = persistedWithOwsApi(id);

      createService().postConvertToEntity(entity, inputWithApis(api("maps-v2", ApiStandard.OWS)));

      verify(layerRepository, never()).deleteByDataSetId(any());
    }

    @Test
    @DisplayName("does not touch layers on create, where the dataset has no id yet")
    void skipsCleanupOnCreate() {
      // A create cannot have orphaned anything, and deleteByDataSetId(null) would be meaningless.
      createService()
          .postConvertToEntity(new DataSet(), inputWithApis(api("sensors", ApiStandard.STA)));

      verify(layerRepository, never()).deleteByDataSetId(any());
    }
  }

  @Nested
  @DisplayName("Membership")
  class MembershipAndOrphans {

    private static final String MANIFEST_URN =
        "urn:core:platform:civitas:dataset:common:test:abcdefghij";
    private static final String MEMBER_URN =
        "urn:core:platform:civitas:datastructure:common:Thing:xyz1234567:1.0.0";

    private DataSet dataSetWithManifest(UUID id) {
      DataSet ds = draftDataSet(id);
      ds.setManifestLogicalUrn(MANIFEST_URN);
      return ds;
    }

    @Test
    void linkMember_whenManifestAndUrnPresent_linksInRegistry() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(dataSetWithManifest(id)));

      createService().linkMember(id, MEMBER_URN);

      verify(modelRegistryGateway).linkToDataSet(MANIFEST_URN, MEMBER_URN);
    }

    @Test
    void linkMember_whenDatasetMissing_throwsNotFound() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> createService().linkMember(id, MEMBER_URN))
          .isInstanceOf(ResourceNotFoundException.class);
      verify(modelRegistryGateway, never()).linkToDataSet(any(), any());
    }

    @Test
    void linkMember_whenManifestMissing_throwsInvalidInput() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(draftDataSet(id)));

      assertThatThrownBy(() -> createService().linkMember(id, MEMBER_URN))
          .isInstanceOf(InvalidInputException.class);
      verify(modelRegistryGateway, never()).linkToDataSet(any(), any());
    }

    @Test
    void linkMember_whenUrnBlank_throwsInvalidInput() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(dataSetWithManifest(id)));

      assertThatThrownBy(() -> createService().linkMember(id, "  "))
          .isInstanceOf(InvalidInputException.class);
      verify(modelRegistryGateway, never()).linkToDataSet(any(), any());
    }

    @Test
    void unlinkMember_whenManifestAndUrnPresent_unlinksInRegistry() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(dataSetWithManifest(id)));

      createService().unlinkMember(id, MEMBER_URN);

      verify(modelRegistryGateway).unlinkFromDataSet(MANIFEST_URN, MEMBER_URN);
    }

    @Test
    void unlinkMember_whenManifestMissing_isNoOp() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(draftDataSet(id)));

      createService().unlinkMember(id, MEMBER_URN);

      verify(modelRegistryGateway, never()).unlinkFromDataSet(any(), any());
    }
  }

  @Nested
  @DisplayName("participating artifact validation")
  class ParticipatingArtifactValidation {

    private DataSet stageable(UUID id) {
      DataSet ds = draftDataSet(id);
      Pipeline pipeline = new Pipeline();
      pipeline.setDataSources(new HashSet<>(List.of(new DataSource())));
      ds.getPipelines().add(pipeline);
      return ds;
    }

    /**
     * A READY dataset publishing no named API, so the map/API-surface check passes and the
     * participating-artifact validation is the only thing that can reject the release.
     */
    private DataSet releasable(UUID id) {
      DataSet ds = new DataSet();
      ds.setId(id);
      ds.setDataSetStatus(DataSetStatus.READY);
      ds.setPipelines(new HashSet<>());
      ds.setNamedApis(new HashSet<>());
      return ds;
    }

    @Test
    @DisplayName("a dataset whose flows carry a defect is not staged")
    void blockedStagingLeavesTheDatasetInDraft() {
      UUID id = UUID.randomUUID();
      DataSet ds = stageable(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      doThrow(
              new PipelineClosureValidationException(
                  List.of(ClosureFinding.notReleased(UUID.randomUUID(), "urn:core:x"))))
          .when(pipelineClosureValidator)
          .validate(any());

      assertThatThrownBy(() -> createService().stage(id))
          .isInstanceOf(PipelineClosureValidationException.class);

      assertThat(ds.getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
      verify(dataSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("a blocked release provisions nothing, rather than compensating afterwards")
    void blockedReleasePublishesNoSaga() {
      UUID id = UUID.randomUUID();
      DataSet ds = releasable(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      doThrow(
              new PipelineClosureValidationException(
                  List.of(ClosureFinding.notAvailable(UUID.randomUUID(), "urn:core:x"))))
          .when(pipelineClosureValidator)
          .validate(any());

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(PipelineClosureValidationException.class);

      verify(sagaPublisher, never()).publishCreateRequested(any());
      assertThat(ds.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(ds.getPendingSagaType()).isNull();
      verify(dataSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("the flows are validated again at release, not only at staging")
    void releaseRevalidates() {
      UUID id = UUID.randomUUID();
      DataSet ds = releasable(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().release(id);

      verify(pipelineClosureValidator).validate(any());
    }
  }
}
