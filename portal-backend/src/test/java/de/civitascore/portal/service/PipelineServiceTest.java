package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.service.removal.DataSinkChange;
import de.civitascore.portal.service.removal.DataSinkRemovalService;
import de.civitascore.portal.service.removal.PreparedDataSinkChange;
import de.civitascore.portal.service.validation.DataSourceDatapoolScopeValidator;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.RemovalConfirmationRequiredException;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

  @Mock private PipelineRepository pipelineRepository;
  @Mock private PipelineMapper pipelineMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSourceRepository dataSourceRepository;

  @Mock private ObjectProvider<AllowedScopes> allowedScopesProvider;

  @Spy private DataSourceDatapoolScopeValidator datapoolScopeValidator;

  @Mock private DataSetMutationGuard dataSetMutationGuard;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataSinkRemovalService dataSinkRemovalService;
  @Mock private PreparedDataSinkChange prepared;

  @InjectMocks private PipelineService pipelineService;

  /** A request that came through OPA. Lenient: only the datasource-reference path consults it. */
  @BeforeEach
  void scopeHeaderPresent() {
    AllowedScopes scopes = new AllowedScopes();
    scopes.setWildcard();
    lenient().when(allowedScopesProvider.getObject()).thenReturn(scopes);
  }

  private DataSet draftDataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setDataSetStatus(DataSetStatus.DRAFT);
    return ds;
  }

  private Pipeline pipeline(UUID id, DataSet dataSet) {
    Pipeline p = new Pipeline();
    p.setId(id);
    p.setName("test-pipeline");
    p.setDataSet(dataSet);
    p.setVersion(1L);
    return p;
  }

  @Nested
  @DisplayName("Registry identity")
  class RegistryIdentity {

    @Test
    @DisplayName("Stores a created pipeline's definition under the id the save assigned")
    void create_storesTheDefinitionUnderTheSavedId() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(dataSetId);
      String logicalUrn = "urn:core:platform:civitas:pipeline:common:" + pipelineId;

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setModel(Map.of("nodes", List.of(), "edges", List.of()));

      Pipeline entity = new Pipeline();
      entity.setName("test-pipeline");
      when(pipelineMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any()))
          .thenAnswer(
              invocation -> {
                Pipeline saved = invocation.getArgument(0);
                saved.setId(pipelineId);
                return saved;
              });
      when(modelRegistryGateway.storePayload(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(
              new ModelRegistryGateway.ModelPin(logicalUrn, logicalUrn + ":1.0.0", "1.0.0"));

      Pipeline result = pipelineService.create(input);

      verify(modelRegistryGateway)
          .storePayload(
              eq(PayloadKind.PIPELINE),
              eq(Optional.empty()),
              eq(pipelineId),
              any(),
              any(),
              any(),
              any());
      assertThat(result.getModelLogicalUrn()).isEqualTo(logicalUrn);
      assertThat(result.getModelUrn()).isEqualTo(logicalUrn + ":1.0.0");
    }
  }

  @Nested
  @DisplayName("DataSource Linking Validation")
  class DataSourceLinkingValidation {

    @Test
    @DisplayName("Should link a DRAFT DataSource to a Pipeline")
    void shouldLinkDraftDataSource() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSourceId = UUID.randomUUID();

      DataSet dataSet = draftDataSet(dataSetId);

      DataSource draftDataSource = new DataSource();
      draftDataSource.setId(dataSourceId);
      draftDataSource.setName("draft-source");
      draftDataSource.setDataSourceStatus(DataSourceStatus.DRAFT);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(Set.of(dataSourceId));

      Pipeline entity = pipeline(UUID.randomUUID(), dataSet);

      when(pipelineMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dataSourceId)))
          .thenReturn(List.of(draftDataSource));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Pipeline result = pipelineService.create(input);

      assertThat(result.getDataSources()).containsExactly(draftDataSource);
    }

    @Test
    @DisplayName("Should reject a nonexistent datasource indistinguishably from an unusable one")
    void shouldRejectNonexistentDataSourceLikeAnUnusableOne() {
      UUID dataSetId = UUID.randomUUID();
      UUID missingId = UUID.randomUUID();

      PipelineInputDTO input = new PipelineInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(Set.of(missingId));

      when(pipelineMapper.toEntity(any())).thenReturn(new Pipeline());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(draftDataSet(dataSetId)));
      when(dataSourceRepository.findAllById(Set.of(missingId))).thenReturn(List.of());

      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(missingId))
          .hasMessageNotContaining("not found");
    }

    @Test
    @DisplayName("Should deny referencing a datasource when no scope header is present")
    void shouldDenyWhenScopeHeaderAbsent() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSourceId = UUID.randomUUID();

      PipelineInputDTO input = new PipelineInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(Set.of(dataSourceId));

      // A request that never passed APISIX/OPA carries no scope header at all.
      when(allowedScopesProvider.getObject()).thenReturn(new AllowedScopes());
      when(pipelineMapper.toEntity(any())).thenReturn(new Pipeline());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(draftDataSet(dataSetId)));

      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(AccessDeniedException.class);
      verify(dataSourceRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Should accept linking an AVAILABLE datasource to a pipeline")
    void shouldAcceptAvailableDataSource() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSourceId = UUID.randomUUID();

      DataSet dataSet = draftDataSet(dataSetId);

      DataSource availableDataSource = new DataSource();
      availableDataSource.setId(dataSourceId);
      availableDataSource.setName("available-source");
      availableDataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(Set.of(dataSourceId));

      Pipeline pipelineEntity = pipeline(UUID.randomUUID(), dataSet);
      pipelineEntity.setDataSources(new HashSet<>());

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dataSourceId)))
          .thenReturn(List.of(availableDataSource));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Pipeline result = pipelineService.create(input);

      assertThat(result.getDataSources()).hasSize(1);
    }

    @Test
    @DisplayName("Should allow creating a pipeline without datasources")
    void shouldAllowNullDataSourceIds() {
      UUID dataSetId = UUID.randomUUID();

      DataSet dataSet = draftDataSet(dataSetId);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("provide-only-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(null);

      Pipeline pipelineEntity = pipeline(UUID.randomUUID(), dataSet);
      pipelineEntity.setDataSources(new HashSet<>());

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Pipeline result = pipelineService.create(input);

      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("DataSink changes")
  class DataSinkChanges {

    @Test
    @DisplayName("Deleting a pipeline deletes its artifact after the row and before the removal")
    void deletePipelineDeletesItsArtifactBeforeTheRemoval() {
      UUID pipelineId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(UUID.randomUUID());
      Pipeline existing = pipeline(pipelineId, dataSet);
      String artifactUrn = "urn:core:platform:civitas:pipeline:common:" + pipelineId;
      existing.setModelLogicalUrn(artifactUrn);
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(existing));
      when(dataSinkRemovalService.prepare(DataSinkChange.deletePipeline(existing), Set.of()))
          .thenReturn(prepared);

      pipelineService.deleteById(pipelineId);

      InOrder inOrder = inOrder(dataSinkRemovalService, pipelineRepository, modelRegistryGateway);
      inOrder
          .verify(dataSinkRemovalService)
          .prepare(DataSinkChange.deletePipeline(existing), Set.of());
      inOrder.verify(pipelineRepository).deleteById(pipelineId);
      inOrder.verify(modelRegistryGateway).deleteArtifact(artifactUrn, true);
      inOrder.verify(dataSinkRemovalService).apply(prepared);
    }

    @Test
    @DisplayName("Refuses an unconfirmed pipeline delete before any write")
    void deleteRefusesUnconfirmedRemoval() {
      UUID pipelineId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(UUID.randomUUID());
      Pipeline existing = pipeline(pipelineId, dataSet);
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(existing));
      when(dataSinkRemovalService.prepare(any(), any()))
          .thenThrow(new RemovalConfirmationRequiredException(null));

      assertThatThrownBy(() -> pipelineService.deleteById(pipelineId))
          .isInstanceOf(RemovalConfirmationRequiredException.class);

      verify(pipelineRepository, never()).deleteById(any());
      verify(modelRegistryGateway, never()).deleteArtifact(any(), eq(true));
    }

    @Test
    @DisplayName("Refuses an update that removes an unconfirmed DataSink link before any write")
    void updateRefusesUnconfirmedRemovalBeforeAnyWrite() {
      UUID pipelineId = UUID.randomUUID();
      UUID confirmedId = UUID.randomUUID();
      Pipeline existing = updatedPipelineWithoutDataSinks(pipelineId);
      when(dataSinkRemovalService.prepare(
              DataSinkChange.linkPipeline(existing, Set.of()), Set.of(confirmedId)))
          .thenThrow(new RemovalConfirmationRequiredException(null));

      assertThatThrownBy(
              () -> pipelineService.update(pipelineId, updateInput(existing, Set.of(confirmedId))))
          .isInstanceOf(RemovalConfirmationRequiredException.class);

      verifyNothingWritten();
    }

    @Test
    @DisplayName("Refuses an update with a DataSink it cannot link before any write")
    void updateRefusesUnlinkableDataSinkBeforeAnyWrite() {
      UUID pipelineId = UUID.randomUUID();
      Pipeline existing = updatedPipelineWithoutDataSinks(pipelineId);
      when(dataSinkRemovalService.prepare(any(), any()))
          .thenThrow(new InvalidInputException("Pipeline", "dataSinkIds", "not found"));

      assertThatThrownBy(() -> pipelineService.update(pipelineId, updateInput(existing, Set.of())))
          .isInstanceOf(InvalidInputException.class);

      verifyNothingWritten();
    }

    @Test
    @DisplayName("Refuses a create with a DataSink it cannot link before the Model Forge write")
    void createRefusesUnlinkableDataSinkBeforeTheModelForgeWrite() {
      UUID dataSetId = UUID.randomUUID();
      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setModel(Map.of("nodes", List.of(), "edges", List.of()));
      input.setDataSinkIds(Set.of(UUID.randomUUID()));
      Pipeline entity = new Pipeline();
      entity.setName("test-pipeline");
      when(pipelineMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(draftDataSet(dataSetId)));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any()))
          .thenAnswer(
              invocation -> {
                Pipeline saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
              });
      when(dataSinkRemovalService.prepare(any(), any()))
          .thenThrow(new InvalidInputException("Pipeline", "dataSinkIds", "not found"));

      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(InvalidInputException.class);

      verify(modelRegistryGateway, never())
          .storePayload(any(), any(), any(), any(), any(), any(), any());
    }

    private Pipeline updatedPipelineWithoutDataSinks(UUID pipelineId) {
      Pipeline existing = pipeline(pipelineId, draftDataSet(UUID.randomUUID()));
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(existing));
      return existing;
    }

    private PipelineInputDTO updateInput(Pipeline existing, Set<UUID> confirmedRemovalIds) {
      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(existing.getDataSet().getId());
      input.setModel(Map.of("nodes", List.of(), "edges", List.of()));
      input.setDataSinkIds(Set.of());
      input.setConfirmedRemovalIds(confirmedRemovalIds);
      return input;
    }

    private void verifyNothingWritten() {
      verify(pipelineMapper, never()).updateEntity(any(), any());
      verify(modelRegistryGateway, never())
          .storePayload(any(), any(), any(), any(), any(), any(), any());
      verify(pipelineRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("DataSource Scope Validation")
  class DataSourceScopeValidation {

    private DataSet dataSetWithoutDataPool(UUID id) {
      DataSet ds = new DataSet();
      ds.setId(id);
      ds.setDataSetStatus(DataSetStatus.DRAFT);
      return ds;
    }

    private DataSet dataSetWithDataPool(UUID id, DataPool pool) {
      DataSet ds = dataSetWithoutDataPool(id);
      ds.setDataPool(pool);
      return ds;
    }

    private DataPool dataPool(UUID id) {
      DataPool pool = new DataPool();
      pool.setId(id);
      return pool;
    }

    private DataSource availableDataSource(UUID id, DatapoolScopeType scopeType) {
      DataSource ds = new DataSource();
      ds.setId(id);
      ds.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      ds.setDatapoolScopeType(scopeType);
      ds.setScopedDataPools(new HashSet<>());
      return ds;
    }

    private Pipeline pipelineEntity(DataSet dataSet) {
      Pipeline p = new Pipeline();
      p.setId(UUID.randomUUID());
      p.setName("test-pipeline");
      p.setDataSet(dataSet);
      p.setDataSources(new HashSet<>());
      return p;
    }

    private PipelineInputDTO createInput(UUID dataSetId, UUID... dataSourceIds) {
      PipelineInputDTO dto = new PipelineInputDTO();
      dto.setName("test-pipeline");
      dto.setDataSetId(dataSetId);
      if (dataSourceIds.length > 0) {
        dto.setDataSourceIds(Set.of(dataSourceIds));
      }
      return dto;
    }

    @Test
    @DisplayName("Should reject DataSource with scope NONE")
    void shouldRejectDataSourceWithScopeNone() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsId = UUID.randomUUID();
      DataSet dataSet = dataSetWithoutDataPool(dataSetId);
      DataSource noneDs = availableDataSource(dsId, DatapoolScopeType.NONE);

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity(dataSet));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dsId))).thenReturn(List.of(noneDs));

      assertThatThrownBy(() -> pipelineService.create(createInput(dataSetId, dsId)))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(dsId));
    }

    @Test
    @DisplayName("Should allow DataSource with scope ALL regardless of DataPool")
    void shouldAllowDataSourceWithScopeAll() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsId = UUID.randomUUID();
      DataSet dataSet = dataSetWithDataPool(dataSetId, dataPool(UUID.randomUUID()));
      DataSource allDs = availableDataSource(dsId, DatapoolScopeType.ALL);
      Pipeline pipeline = pipelineEntity(dataSet);

      when(pipelineMapper.toEntity(any())).thenReturn(pipeline);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dsId))).thenReturn(List.of(allDs));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Pipeline result = pipelineService.create(createInput(dataSetId, dsId));

      assertThat(result.getDataSources()).hasSize(1);
    }

    @Test
    @DisplayName("Should reject DataSource with scope SPECIFIC when DataSet has no DataPool")
    void shouldRejectDataSourceWithScopeSpecificWhenDataSetHasNoDataPool() {
      // A pool-less dataset is in no datapool, so a SPECIFIC (pool-confined) datasource must be
      // rejected here — otherwise its confined data could be routed into a pool-less dataset.
      UUID dataSetId = UUID.randomUUID();
      UUID dsId = UUID.randomUUID();
      DataSet dataSet = dataSetWithoutDataPool(dataSetId);
      DataSource specificDs = availableDataSource(dsId, DatapoolScopeType.SPECIFIC);

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity(dataSet));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dsId))).thenReturn(List.of(specificDs));

      assertThatThrownBy(() -> pipelineService.create(createInput(dataSetId, dsId)))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(dsId));
    }

    @Test
    @DisplayName("Should allow DataSource with scope SPECIFIC when DataPool matches")
    void shouldAllowDataSourceWithScopeSpecificWhenDataPoolMatches() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsId = UUID.randomUUID();
      UUID poolId = UUID.randomUUID();
      DataPool pool = dataPool(poolId);
      DataSet dataSet = dataSetWithDataPool(dataSetId, pool);
      DataSource specificDs = availableDataSource(dsId, DatapoolScopeType.SPECIFIC);
      specificDs.setScopedDataPools(Set.of(pool));
      Pipeline pipeline = pipelineEntity(dataSet);

      when(pipelineMapper.toEntity(any())).thenReturn(pipeline);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dsId))).thenReturn(List.of(specificDs));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Pipeline result = pipelineService.create(createInput(dataSetId, dsId));

      assertThat(result.getDataSources()).hasSize(1);
    }

    @Test
    @DisplayName("Should reject DataSource with scope SPECIFIC when DataPool does not match")
    void shouldRejectDataSourceWithScopeSpecificWhenDataPoolDoesNotMatch() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsId = UUID.randomUUID();
      DataSet dataSet = dataSetWithDataPool(dataSetId, dataPool(UUID.randomUUID()));
      DataSource specificDs = availableDataSource(dsId, DatapoolScopeType.SPECIFIC);
      specificDs.setScopedDataPools(Set.of(dataPool(UUID.randomUUID())));

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity(dataSet));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dsId))).thenReturn(List.of(specificDs));

      assertThatThrownBy(() -> pipelineService.create(createInput(dataSetId, dsId)))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(dsId));
    }

    @Test
    @DisplayName("Should collect all offending DataSource IDs without fail-fast")
    void shouldCollectAllOffendingDataSourceIdsWithoutFailFast() {
      UUID dataSetId = UUID.randomUUID();
      UUID noneId = UUID.randomUUID();
      UUID specificMismatchId = UUID.randomUUID();
      DataSet dataSet = dataSetWithDataPool(dataSetId, dataPool(UUID.randomUUID()));

      DataSource noneDs = availableDataSource(noneId, DatapoolScopeType.NONE);
      DataSource specificDs = availableDataSource(specificMismatchId, DatapoolScopeType.SPECIFIC);
      specificDs.setScopedDataPools(Set.of(dataPool(UUID.randomUUID())));

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity(dataSet));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(noneId, specificMismatchId)))
          .thenReturn(List.of(noneDs, specificDs));

      assertThatThrownBy(
              () -> pipelineService.create(createInput(dataSetId, noneId, specificMismatchId)))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactlyInAnyOrder(noneId, specificMismatchId));
    }
  }
}
