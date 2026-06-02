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
  @Mock private PipelineRepository pipelineRepository;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;

  @InjectMocks private DataSinkService dataSinkService;

  private DataSet dataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setName("ds");
    return ds;
  }

  private Pipeline pipeline(UUID id, DataSet dataSet) {
    Pipeline p = new Pipeline();
    p.setId(id);
    p.setName("pl");
    p.setDataSet(dataSet);
    return p;
  }

  @Nested
  @DisplayName("findByIdAndDataSetOrThrow()")
  class FindByIdAndDataSet {

    @Test
    @DisplayName("Should return sink when its pipeline belongs to the requested dataset")
    void shouldReturnSinkForMatchingDataset() {
      UUID dataSetId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();

      DataSet dataSet = dataSet(dataSetId);
      Pipeline pipeline = pipeline(UUID.randomUUID(), dataSet);

      DataSink sink = new DataSink();
      sink.setId(sinkId);
      sink.setPipeline(pipeline);

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(sink));

      DataSink result = dataSinkService.findByIdAndDataSetOrThrow(sinkId, dataSetId);

      assertThat(result).isSameAs(sink);
    }

    @Test
    @DisplayName(
        "Should throw ResourceNotFoundException when sink's pipeline belongs to a different dataset")
    void shouldThrowWhenDatasetMismatch() {
      UUID sinkId = UUID.randomUUID();

      DataSet otherDataSet = dataSet(UUID.randomUUID());
      Pipeline pipeline = pipeline(UUID.randomUUID(), otherDataSet);

      DataSink sink = new DataSink();
      sink.setId(sinkId);
      sink.setPipeline(pipeline);

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(sink));

      assertThatThrownBy(() -> dataSinkService.findByIdAndDataSetOrThrow(sinkId, UUID.randomUUID()))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("postConvertToEntity()")
  class PostConvertToEntity {

    @Test
    @DisplayName("Should resolve pipeline from input and set it on the entity")
    void shouldResolvePipeline() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSet dataSet = dataSet(dataSetId);
      Pipeline pipeline = pipeline(pipelineId, dataSet);

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      DataSink entity = new DataSink();

      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(pipeline));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSink result = dataSinkService.create(input);

      assertThat(result.getPipeline()).isSameAs(pipeline);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when pipeline is not found")
    void shouldThrowWhenPipelineNotFound() {
      UUID pipelineId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
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

      DataSet dataSet = dataSet(dataSetId);
      Pipeline pipeline = pipeline(pipelineId, dataSet);

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("unexpected", "value"));

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(pipeline));

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }
  }

  @Nested
  @DisplayName("validateConfiguration() — POSTGIS")
  class PostgisValidation {

    private DataSinkInputDTO basePostgisInput(UUID pipelineId) {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setPipelineId(pipelineId);
      input.setDataSinkType(DataSinkType.POSTGIS);
      return input;
    }

    private void stubPipeline(UUID dataSetId, UUID pipelineId) {
      DataSet dataSet = dataSet(dataSetId);
      Pipeline pipeline = pipeline(pipelineId, dataSet);
      when(dataSinkMapper.toEntity(any())).thenReturn(new DataSink());
      when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(pipeline));
    }

    @Test
    @DisplayName("Should throw InvalidInputException when tableName is not a String")
    void shouldThrowWhenTableNameIsNotAString() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(pipelineId);
      input.setConfiguration(
          Map.of("tableName", 42, "dataStructureVersionId", UUID.randomUUID().toString()));

      stubPipeline(dataSetId, pipelineId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when tableName is blank")
    void shouldThrowWhenTableNameBlank() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(pipelineId);
      input.setConfiguration(
          Map.of("tableName", "  ", "dataStructureVersionId", UUID.randomUUID().toString()));

      stubPipeline(dataSetId, pipelineId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when dataStructureVersionId is missing")
    void shouldThrowWhenDsvIdMissing() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(pipelineId);
      input.setConfiguration(Map.of("tableName", "sensor_readings"));

      stubPipeline(dataSetId, pipelineId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when DataStructureVersion is not found")
    void shouldThrowWhenDsvNotFound() {
      UUID dataSetId = UUID.randomUUID();
      UUID pipelineId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(pipelineId);
      input.setConfiguration(
          Map.of("tableName", "sensor_readings", "dataStructureVersionId", dsvId.toString()));

      stubPipeline(dataSetId, pipelineId);
      when(dataStructureVersionRepository.existsById(dsvId)).thenReturn(false);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }
  }
}
