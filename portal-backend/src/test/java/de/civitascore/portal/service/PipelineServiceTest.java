package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.DataSourceDatapoolScopeValidator;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.InvalidInputException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock private DataSinkService dataSinkService;
  @Mock private DataSinkRepository dataSinkRepository;

  @Mock private ObjectProvider<AllowedScopes> allowedScopesProvider;

  @Spy private DataSourceDatapoolScopeValidator datapoolScopeValidator;

  @Mock private DataSetMutationGuard dataSetMutationGuard;

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
  @DisplayName("DataSource Linking Validation")
  class DataSourceLinkingValidation {

    @Test
    @DisplayName("Should reject linking a DRAFT datasource to a pipeline")
    void shouldRejectDraftDataSource() {
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

      Pipeline entity = new Pipeline();
      entity.setName("test-pipeline");

      when(pipelineMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dataSourceId)))
          .thenReturn(List.of(draftDataSource));

      // A DRAFT source is rejected with the same answer as a nonexistent or out-of-pool one:
      // naming the lifecycle status would leak it to a caller who may not read the data source.
      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(DataSourceScopeViolationException.class)
          .satisfies(
              ex ->
                  assertThat(((DataSourceScopeViolationException) ex).getOffendingDataSourceIds())
                      .containsExactly(dataSourceId))
          .hasMessageNotContaining("AVAILABLE status");
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
      when(dataSinkRepository.findByPipelineId(any())).thenReturn(List.of());

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
      when(dataSinkRepository.findByPipelineId(any())).thenReturn(List.of());

      Pipeline result = pipelineService.create(input);

      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("DataSink linkage via dataSinkIds")
  class DataSinkLinkage {

    @Test
    @DisplayName("Attaches a free DataSink listed in dataSinkIds to the pipeline")
    void attachesFreeDataSink() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(dataSetId);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinkIds(Set.of(sinkId));

      Pipeline pipelineEntity = pipeline(pipelineId, dataSet);

      DataSink freeSink = new DataSink();
      freeSink.setId(sinkId);
      freeSink.setDataSet(dataSet);

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSinkRepository.findAllById(Set.of(sinkId))).thenReturn(List.of(freeSink));
      when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of());

      pipelineService.create(input);

      verify(dataSinkRepository)
          .save(
              argThat(
                  s ->
                      s.getId().equals(sinkId)
                          && s.getPipeline() != null
                          && s.getPipeline().getId().equals(pipelineId)));
    }

    @Test
    @DisplayName("Detaches a previously-linked DataSink that is no longer in dataSinkIds")
    void detachesRemovedDataSink() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID removedSinkId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(dataSetId);

      Pipeline existing = pipeline(pipelineId, dataSet);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinkIds(Set.of());

      DataSink linkedSink = new DataSink();
      linkedSink.setId(removedSinkId);
      linkedSink.setDataSet(dataSet);
      linkedSink.setPipeline(existing);

      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(existing));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(linkedSink));

      pipelineService.update(pipelineId, input);

      verify(dataSinkRepository)
          .save(argThat(s -> s.getId().equals(removedSinkId) && s.getPipeline() == null));
    }

    @Test
    @DisplayName("Rejects a DataSink belonging to a different DataSet")
    void rejectsCrossDatasetDataSink() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(dataSetId);
      DataSet otherDataSet = draftDataSet(UUID.randomUUID());

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinkIds(Set.of(sinkId));

      Pipeline pipelineEntity = pipeline(pipelineId, dataSet);

      DataSink crossSink = new DataSink();
      crossSink.setId(sinkId);
      crossSink.setDataSet(otherDataSet);

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSinkRepository.findAllById(Set.of(sinkId))).thenReturn(List.of(crossSink));

      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("different DataSet");
    }

    @Test
    @DisplayName("Rejects a DataSink already attached to another Pipeline")
    void rejectsDataSinkAttachedElsewhere() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID otherPipelineId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(dataSetId);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinkIds(Set.of(sinkId));

      Pipeline pipelineEntity = pipeline(pipelineId, dataSet);
      Pipeline otherPipeline = pipeline(otherPipelineId, dataSet);
      otherPipeline.setName("other-pipeline");

      DataSink occupiedSink = new DataSink();
      occupiedSink.setId(sinkId);
      occupiedSink.setDataSet(dataSet);
      occupiedSink.setPipeline(otherPipeline);

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSinkRepository.findAllById(Set.of(sinkId))).thenReturn(List.of(occupiedSink));

      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("already attached to another Pipeline");
    }

    @Test
    @DisplayName("Rejects a dataSinkIds list containing an unknown UUID")
    void rejectsUnknownDataSinkId() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(dataSetId);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinkIds(Set.of(sinkId));

      Pipeline pipelineEntity = pipeline(pipelineId, dataSet);

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSinkRepository.findAllById(Set.of(sinkId))).thenReturn(List.of());

      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("One or more DataSink IDs not found");
    }

    @Test
    @DisplayName("Deleting a pipeline unlinks its DataSinks but does not delete them")
    void deletePipelineUnlinksDataSinks() {
      UUID pipelineId = UUID.randomUUID();
      UUID dataSetId = UUID.randomUUID();
      DataSet dataSet = draftDataSet(dataSetId);

      Pipeline existing = pipeline(pipelineId, dataSet);

      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(existing));
      when(pipelineRepository.existsById(pipelineId)).thenReturn(true);

      pipelineService.deleteById(pipelineId);

      verify(dataSinkService).unlinkByPipelineId(pipelineId);
      verify(dataSinkService, never()).deleteById(any());
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
      when(dataSinkRepository.findByPipelineId(any())).thenReturn(List.of());

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
      when(dataSinkRepository.findByPipelineId(any())).thenReturn(List.of());

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
