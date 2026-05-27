package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSinkServiceTest {

  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private DataSinkMapper dataSinkMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private PipelineRepository pipelineRepository;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;

  @InjectMocks private DataSinkService dataSinkService;

  @Nested
  @DisplayName("findByIdAndDataSetOrThrow()")
  class FindByIdAndDataSet {

    @Test
    @DisplayName("Should return sink when it belongs to the requested dataset")
    void shouldReturnSinkForMatchingDataset() {
      UUID dataSetId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setName("ds");

      DataSink sink = new DataSink();
      sink.setId(sinkId);
      sink.setDataSet(dataSet);

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(sink));

      DataSink result = dataSinkService.findByIdAndDataSetOrThrow(sinkId, dataSetId);

      assertThat(result).isSameAs(sink);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when sink belongs to a different dataset")
    void shouldThrowWhenDatasetMismatch() {
      UUID sinkId = UUID.randomUUID();

      DataSet actualDataSet = new DataSet();
      actualDataSet.setId(UUID.randomUUID());
      actualDataSet.setName("actual-ds");

      DataSink sink = new DataSink();
      sink.setId(sinkId);
      sink.setDataSet(actualDataSet);

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(sink));

      assertThatThrownBy(() -> dataSinkService.findByIdAndDataSetOrThrow(sinkId, UUID.randomUUID()))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("postConvertToEntity()")
  class PostConvertToEntity {

    @Test
    @DisplayName("Should resolve dataset and pipeline from input IDs")
    void shouldResolveRelationships() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setName("ds");

      Pipeline pipeline = new Pipeline();
      pipeline.setId(pipelineId);
      pipeline.setName("pl");
      pipeline.setDataSet(dataSet);

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      DataSink entity = new DataSink();

      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(pipeline));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSink result = dataSinkService.create(input);

      assertThat(result.getDataSet()).isSameAs(dataSet);
      assertThat(result.getPipeline()).isSameAs(pipeline);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when dataset is not found")
    void shouldThrowWhenDatasetNotFound() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when pipeline is not found")
    void shouldThrowWhenPipelineNotFound() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setName("ds");

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("validateConfiguration() — FROST")
  class FrostValidation {

    @Test
    @DisplayName("Should throw InvalidInputException when FROST config is non-empty")
    void shouldThrowWhenFrostConfigNonEmpty() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setName("ds");

      Pipeline pipeline = new Pipeline();
      pipeline.setId(pipelineId);
      pipeline.setName("pl");
      pipeline.setDataSet(dataSet);

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("unexpected", "value"));

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(pipeline));

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }
  }

  @Nested
  @DisplayName("validateConfiguration() — POSTGIS")
  class PostgisValidation {

    private DataSinkInputDTO basePostgisInput(UUID dataSetId, UUID pipelineId) {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.POSTGIS);
      return input;
    }

    private void stubDataSetAndPipeline(
        UUID dataSetId, UUID pipelineId, DataSet dataSet, Pipeline pipeline) {
      when(dataSinkMapper.toEntity(any())).thenReturn(new DataSink());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(pipeline));
    }

    @Test
    @DisplayName("Should throw InvalidInputException when tableName is not a String")
    void shouldThrowWhenTableNameIsNotAString() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setName("ds");

      Pipeline pipeline = new Pipeline();
      pipeline.setId(pipelineId);
      pipeline.setName("pl");
      pipeline.setDataSet(dataSet);

      DataSinkInputDTO input = basePostgisInput(dataSetId, pipelineId);
      input.setConfiguration(
          Map.of("tableName", 42, "dataStructureVersionId", UUID.randomUUID().toString()));

      stubDataSetAndPipeline(dataSetId, pipelineId, dataSet, pipeline);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when tableName is blank")
    void shouldThrowWhenTableNameBlank() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setName("ds");

      Pipeline pipeline = new Pipeline();
      pipeline.setId(pipelineId);
      pipeline.setName("pl");
      pipeline.setDataSet(dataSet);

      DataSinkInputDTO input = basePostgisInput(dataSetId, pipelineId);
      input.setConfiguration(
          Map.of("tableName", "  ", "dataStructureVersionId", UUID.randomUUID().toString()));

      stubDataSetAndPipeline(dataSetId, pipelineId, dataSet, pipeline);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when dataStructureVersionId is missing")
    void shouldThrowWhenDsvIdMissing() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setName("ds");

      Pipeline pipeline = new Pipeline();
      pipeline.setId(pipelineId);
      pipeline.setName("pl");
      pipeline.setDataSet(dataSet);

      DataSinkInputDTO input = basePostgisInput(dataSetId, pipelineId);
      input.setConfiguration(Map.of("tableName", "sensor_readings"));

      stubDataSetAndPipeline(dataSetId, pipelineId, dataSet, pipeline);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when DataStructureVersion is not found")
    void shouldThrowWhenDsvNotFound() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setName("ds");

      Pipeline pipeline = new Pipeline();
      pipeline.setId(pipelineId);
      pipeline.setName("pl");
      pipeline.setDataSet(dataSet);

      DataSinkInputDTO input = basePostgisInput(dataSetId, pipelineId);
      input.setConfiguration(
          Map.of("tableName", "sensor_readings", "dataStructureVersionId", dsvId.toString()));

      stubDataSetAndPipeline(dataSetId, pipelineId, dataSet, pipeline);
      when(dataStructureVersionRepository.existsById(dsvId)).thenReturn(false);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }
  }

  @Nested
  @DisplayName("pipeline ownership validation")
  class PipelineOwnership {

    @Test
    @DisplayName("Should throw InvalidInputException when pipeline belongs to a different dataset")
    void shouldThrowWhenPipelineDataSetMismatch() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSet sinkDataSet = new DataSet();
      sinkDataSet.setId(dataSetId);
      sinkDataSet.setName("sink-ds");

      DataSet pipelineDataSet = new DataSet();
      pipelineDataSet.setId(UUID.randomUUID());
      pipelineDataSet.setName("pipeline-ds");

      Pipeline pipeline = new Pipeline();
      pipeline.setId(pipelineId);
      pipeline.setName("pl");
      pipeline.setDataSet(pipelineDataSet);

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      when(dataSinkMapper.toEntity(any())).thenReturn(new DataSink());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(sinkDataSet));
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(pipeline));

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }
  }
}
