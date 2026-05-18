package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.LayerMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.repository.StyleRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.List;
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
class LayerServiceTest {

  @Mock private LayerRepository layerRepository;
  @Mock private LayerMapper layerMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private StyleRepository styleRepository;

  @InjectMocks private LayerService layerService;

  private DataSet dataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setName("ds-" + id);
    return ds;
  }

  private LayerInputDTO baseInput(UUID dataSetId, UUID dataSinkId) {
    LayerInputDTO input = new LayerInputDTO();
    input.setDataSetId(dataSetId);
    input.setDataSinkId(dataSinkId);
    input.setLayerName("test-layer");
    return input;
  }

  @Nested
  @DisplayName("findByIdAndDataSetOrThrow()")
  class FindByIdAndDataSetOrThrow {

    @Test
    @DisplayName("Should return Layer when it belongs to the requested dataset")
    void shouldReturnLayerForMatchingDataset() {
      UUID dataSetId = UUID.randomUUID();
      UUID layerId = UUID.randomUUID();

      Layer layer = new Layer();
      layer.setId(layerId);
      layer.setDataSet(dataSet(dataSetId));

      when(layerRepository.findById(layerId)).thenReturn(Optional.of(layer));

      assertThat(layerService.findByIdAndDataSetOrThrow(layerId, dataSetId)).isSameAs(layer);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when Layer belongs to a different dataset")
    void shouldThrowWhenDatasetMismatch() {
      UUID layerId = UUID.randomUUID();

      Layer layer = new Layer();
      layer.setId(layerId);
      layer.setDataSet(dataSet(UUID.randomUUID()));

      when(layerRepository.findById(layerId)).thenReturn(Optional.of(layer));

      assertThatThrownBy(() -> layerService.findByIdAndDataSetOrThrow(layerId, UUID.randomUUID()))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("postConvertToEntity()")
  class PostConvertToEntity {

    @Test
    @DisplayName("Should resolve dataset and dataSink from input IDs")
    void shouldResolveDatasetAndDataSink() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      DataSink dataSink = new DataSink();
      dataSink.setId(dataSinkId);
      dataSink.setDataSet(ds);

      LayerInputDTO input = baseInput(dataSetId, dataSinkId);
      Layer entity = new Layer();

      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.of(dataSink));
      when(layerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Layer result = layerService.create(input);

      assertThat(result.getDataSet()).isSameAs(ds);
      assertThat(result.getDataSink()).isSameAs(dataSink);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when dataset not found")
    void shouldThrowWhenDatasetNotFound() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();

      Layer entity = new Layer();
      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> layerService.create(baseInput(dataSetId, dataSinkId)))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when dataSink not found")
    void shouldThrowWhenDataSinkNotFound() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      Layer entity = new Layer();
      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> layerService.create(baseInput(dataSetId, dataSinkId)))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName(
        "Should throw ResourceNotFoundException when a referenced alternative Style is not found")
    void shouldThrowWhenAlternativeStyleNotFound() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();
      UUID knownStyleId = UUID.randomUUID();
      UUID unknownStyleId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      DataSink dataSink = new DataSink();
      dataSink.setId(dataSinkId);
      dataSink.setDataSet(ds);

      Style knownStyle = new Style();
      knownStyle.setId(knownStyleId);

      LayerInputDTO input = baseInput(dataSetId, dataSinkId);
      input.setAlternativeStyleIds(List.of(knownStyleId, unknownStyleId));

      Layer entity = new Layer();
      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.of(dataSink));
      when(styleRepository.findAllById(List.of(knownStyleId, unknownStyleId)))
          .thenReturn(List.of(knownStyle));

      assertThatThrownBy(() -> layerService.create(input))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("preProcessUpdateInput()")
  class PreProcessUpdateInput {

    @Test
    @DisplayName("Should throw ResourceNotFoundException when trying to change the dataset")
    void shouldThrowWhenDatasetChanges() {
      UUID layerId = UUID.randomUUID();
      DataSet original = dataSet(UUID.randomUUID());

      Layer existing = new Layer();
      existing.setId(layerId);
      existing.setDataSet(original);

      LayerInputDTO input = new LayerInputDTO();
      input.setDataSetId(UUID.randomUUID());
      input.setDataSinkId(UUID.randomUUID());
      input.setLayerName("updated");

      assertThatThrownBy(() -> layerService.preProcessUpdateInput(input, existing))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
