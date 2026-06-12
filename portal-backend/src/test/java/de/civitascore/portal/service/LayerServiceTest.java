package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
      entity.setLayerName("test-layer");

      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.of(dataSink));
      when(layerRepository.findByDataSinkIdAndLayerName(dataSinkId, "test-layer"))
          .thenReturn(Optional.empty());
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
        "Should throw ResourceNotFoundException when dataSink belongs to a different dataset")
    void shouldThrowWhenDataSinkFromWrongDataset() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      DataSet otherDataSet = dataSet(UUID.randomUUID());
      DataSink dataSink = new DataSink();
      dataSink.setId(dataSinkId);
      dataSink.setDataSet(otherDataSet);

      Layer entity = new Layer();
      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.of(dataSink));

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

  @Nested
  @DisplayName("preSave() — uniqueness")
  class PreSave {

    @Test
    @DisplayName(
        "Should throw UniqueConstraintViolationException when layerName already exists in dataSink")
    void shouldThrowOnDuplicateLayerName() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      DataSink dataSink = new DataSink();
      dataSink.setId(dataSinkId);
      dataSink.setDataSet(ds);

      LayerInputDTO input = baseInput(dataSetId, dataSinkId);
      Layer entity = new Layer();
      entity.setDataSet(ds);
      entity.setDataSink(dataSink);
      entity.setLayerName("test-layer");

      Layer existing = new Layer();
      existing.setId(UUID.randomUUID());

      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.of(dataSink));
      when(layerRepository.findByDataSinkIdAndLayerName(dataSinkId, "test-layer"))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> layerService.create(input))
          .isInstanceOf(UniqueConstraintViolationException.class);
    }

    @Test
    @DisplayName("Should not throw when updating a Layer with its own existing layerName")
    void shouldAllowUpdateWithSameLayerName() {
      UUID layerId = UUID.randomUUID();
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      DataSink dataSink = new DataSink();
      dataSink.setId(dataSinkId);
      dataSink.setDataSet(ds);

      Layer existingLayer = new Layer();
      existingLayer.setId(layerId);
      existingLayer.setDataSet(ds);
      existingLayer.setDataSink(dataSink);
      existingLayer.setLayerName("test-layer");

      LayerInputDTO input = baseInput(dataSetId, dataSinkId);

      when(layerRepository.findById(layerId)).thenReturn(Optional.of(existingLayer));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.of(dataSink));
      when(layerRepository.findByDataSinkIdAndLayerName(dataSinkId, "test-layer"))
          .thenReturn(Optional.of(existingLayer));
      when(layerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> layerService.update(layerId, input)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("validateStyleOwnership()")
  class ValidateStyleOwnership {

    private DataSet otherDataSet() {
      DataSet ds = new DataSet();
      ds.setId(UUID.randomUUID());
      ds.setName("other-ds");
      return ds;
    }

    @Test
    @DisplayName(
        "Should throw InvalidInputException when defaultStyle belongs to a different dataset")
    void shouldThrowWhenDefaultStyleFromWrongDataset() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();
      UUID styleId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      DataSink dataSink = new DataSink();
      dataSink.setId(dataSinkId);
      dataSink.setDataSet(ds);

      Style style = new Style();
      style.setId(styleId);
      style.setDataSet(otherDataSet());

      LayerInputDTO input = baseInput(dataSetId, dataSinkId);
      input.setDefaultStyleId(styleId);

      Layer entity = new Layer();
      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.of(dataSink));
      when(styleRepository.findById(styleId)).thenReturn(Optional.of(style));

      assertThatThrownBy(() -> layerService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .extracting("resourceInfo")
          .isEqualTo("defaultStyleId");
    }

    @Test
    @DisplayName(
        "Should throw InvalidInputException when an alternativeStyle belongs to a different dataset")
    void shouldThrowWhenAlternativeStyleFromWrongDataset() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSinkId = UUID.randomUUID();
      UUID styleId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      DataSink dataSink = new DataSink();
      dataSink.setId(dataSinkId);
      dataSink.setDataSet(ds);

      Style style = new Style();
      style.setId(styleId);
      style.setDataSet(otherDataSet());

      LayerInputDTO input = baseInput(dataSetId, dataSinkId);
      input.setAlternativeStyleIds(List.of(styleId));

      Layer entity = new Layer();
      when(layerMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(dataSinkRepository.findById(dataSinkId)).thenReturn(Optional.of(dataSink));
      when(styleRepository.findAllById(List.of(styleId))).thenReturn(List.of(style));

      assertThatThrownBy(() -> layerService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .extracting("resourceInfo")
          .isEqualTo("alternativeStyleIds");
    }
  }

  @Nested
  @DisplayName("validateBboxConsistency()")
  class ValidateBboxConsistency {

    static Stream<Arguments> bboxConsistencyCases() {
      Map<String, Object> bbox = Map.of("minx", -180, "miny", -90, "maxx", 180, "maxy", 90);
      return Stream.of(
          // autoCalculate=true: bbox fields must be absent
          Arguments.of(true, null, null, false),
          Arguments.of(true, bbox, null, true),
          Arguments.of(true, null, bbox, true),
          Arguments.of(true, bbox, bbox, true),
          // autoCalculate=false: both bbox fields are required
          Arguments.of(false, bbox, bbox, false),
          Arguments.of(false, null, null, true),
          Arguments.of(false, bbox, null, true),
          Arguments.of(false, null, bbox, true));
    }

    @ParameterizedTest(name = "autoCalculate={0}, nativeBbox={1}, latLonBbox={2} → shouldThrow={3}")
    @MethodSource("bboxConsistencyCases")
    @DisplayName("Should validate bbox field combinations against bboxAutoCalculate")
    void shouldValidateBboxConsistency(
        boolean autoCalculate,
        Map<String, Object> nativeBbox,
        Map<String, Object> latLonBbox,
        boolean shouldThrow) {
      LayerInputDTO input = new LayerInputDTO();
      input.setBboxAutoCalculate(autoCalculate);
      input.setNativeBoundingBox(nativeBbox);
      input.setLatLonBoundingBox(latLonBbox);
      input.setDataSinkId(UUID.randomUUID());
      input.setLayerName("layer");

      if (shouldThrow) {
        assertThatThrownBy(() -> layerService.preProcessCreateInput(input))
            .isInstanceOf(InvalidInputException.class)
            .hasMessageContaining("bboxAutoCalculate");
      } else {
        assertThatCode(() -> layerService.preProcessCreateInput(input)).doesNotThrowAnyException();
      }
    }
  }
}
