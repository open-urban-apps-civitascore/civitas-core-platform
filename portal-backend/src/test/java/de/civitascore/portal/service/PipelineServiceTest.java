package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

  @Mock private PipelineRepository pipelineRepository;
  @Mock private PipelineMapper pipelineMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private DataSinkService dataSinkService;
  @Mock private DataSinkRepository dataSinkRepository;

  @InjectMocks private PipelineService pipelineService;

  @Nested
  @DisplayName("DataSource Linking Validation")
  class DataSourceLinkingValidation {

    @Test
    @DisplayName("Should reject linking a DRAFT datasource to a pipeline")
    void shouldRejectDraftDataSource() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSourceId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);

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

      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE status");
    }

    @Test
    @DisplayName("Should accept linking an AVAILABLE datasource to a pipeline")
    void shouldAcceptAvailableDataSource() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSourceId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);

      DataSource availableDataSource = new DataSource();
      availableDataSource.setId(dataSourceId);
      availableDataSource.setName("available-source");
      availableDataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(Set.of(dataSourceId));

      Pipeline pipelineEntity = new Pipeline();
      pipelineEntity.setId(UUID.randomUUID());
      pipelineEntity.setName("test-pipeline");
      pipelineEntity.setDataSet(dataSet);
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

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("provide-only-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(null);

      Pipeline pipelineEntity = new Pipeline();
      pipelineEntity.setId(UUID.randomUUID());
      pipelineEntity.setName("provide-only-pipeline");
      pipelineEntity.setDataSet(dataSet);
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
  @DisplayName("Nested DataSink CRUD")
  class NestedDataSinkCrud {

    private DataSet dataSet(UUID id) {
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

    static Stream<List<DataSinkInputDTO>> emptyAndNullDataSinks() {
      return Stream.of(List.of(), null);
    }

    @Test
    @DisplayName("Creating a pipeline with a new DataSink delegates to DataSinkService.create")
    void createPipeline_withNewDataSink_callsDataSinkServiceCreate() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      DataSet dataSet = dataSet(dataSetId);

      DataSinkInputDTO sinkInput = new DataSinkInputDTO();
      sinkInput.setDataSinkType(DataSinkType.FROST);
      sinkInput.setConfiguration(Map.of());

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinks(List.of(sinkInput));

      Pipeline pipelineEntity = pipeline(pipelineId, dataSet);

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of());

      pipelineService.create(input);

      verify(dataSinkService).create(sinkInput);
      assertThat(sinkInput.getPipelineId()).isEqualTo(pipelineId);
      assertThat(sinkInput.getDataSetId()).isEqualTo(dataSetId);
    }

    @ParameterizedTest(name = "dataSinks={0}")
    @MethodSource("emptyAndNullDataSinks")
    @DisplayName("Updating a pipeline with empty or null dataSinks deletes existing DataSinks")
    void updatePipeline_withEmptyOrNullDataSinks_deletesExisting(List<DataSinkInputDTO> dataSinks) {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID existingSinkId = UUID.randomUUID();
      DataSet dataSet = dataSet(dataSetId);

      Pipeline existing = pipeline(pipelineId, dataSet);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinks(dataSinks);

      DataSink existingSink = new DataSink();
      existingSink.setId(existingSinkId);

      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(existing));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(existingSink));

      pipelineService.update(pipelineId, input);

      verify(dataSinkService).deleteById(existingSinkId);
    }

    @Test
    @DisplayName(
        "Updating a pipeline with existing DataSink ID delegates to DataSinkService.update")
    void updatePipeline_withExistingDataSinkId_callsDataSinkServiceUpdate() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID existingSinkId = UUID.randomUUID();
      DataSet dataSet = dataSet(dataSetId);

      Pipeline existing = pipeline(pipelineId, dataSet);

      DataSinkInputDTO sinkInput = new DataSinkInputDTO();
      sinkInput.setId(existingSinkId);
      sinkInput.setDataSinkType(DataSinkType.FROST);
      sinkInput.setConfiguration(Map.of());

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinks(List.of(sinkInput));

      DataSink existingSink = new DataSink();
      existingSink.setId(existingSinkId);
      existingSink.setPipeline(existing);

      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(existing));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSinkRepository.findAllById(List.of(existingSinkId)))
          .thenReturn(List.of(existingSink));
      when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(existingSink));

      pipelineService.update(pipelineId, input);

      verify(dataSinkService).update(existingSinkId, sinkInput);
    }

    @Test
    @DisplayName(
        "Should throw InvalidInputException when a DataSink with an id belongs to a different pipeline")
    void updatePipeline_withDataSinkFromDifferentPipeline_throws() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID otherPipelineId = UUID.randomUUID();
      UUID existingSinkId = UUID.randomUUID();
      DataSet dataSet = dataSet(dataSetId);

      Pipeline existing = pipeline(pipelineId, dataSet);
      Pipeline otherPipeline = pipeline(otherPipelineId, dataSet);

      DataSinkInputDTO sinkInput = new DataSinkInputDTO();
      sinkInput.setId(existingSinkId);
      sinkInput.setDataSinkType(DataSinkType.FROST);
      sinkInput.setConfiguration(Map.of());

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSinks(List.of(sinkInput));

      DataSink sinkOnOtherPipeline = new DataSink();
      sinkOnOtherPipeline.setId(existingSinkId);
      sinkOnOtherPipeline.setPipeline(otherPipeline);

      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(existing));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSinkRepository.findAllById(List.of(existingSinkId)))
          .thenReturn(List.of(sinkOnOtherPipeline));

      assertThatThrownBy(() -> pipelineService.update(pipelineId, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("cannot be moved");
    }
  }
}
