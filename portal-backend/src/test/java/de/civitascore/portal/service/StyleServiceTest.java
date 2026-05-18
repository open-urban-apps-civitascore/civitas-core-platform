package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.StyleMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.repository.StyleRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
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
class StyleServiceTest {

  @Mock private StyleRepository styleRepository;
  @Mock private StyleMapper styleMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private LayerRepository layerRepository;

  @InjectMocks private StyleService styleService;

  private DataSet dataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setName("ds-" + id);
    return ds;
  }

  @Nested
  @DisplayName("findByIdAndDataSetOrThrow()")
  class FindByIdAndDataSetOrThrow {

    @Test
    @DisplayName("Should return Style when it belongs to the requested dataset")
    void shouldReturnStyleForMatchingDataset() {
      UUID dataSetId = UUID.randomUUID();
      UUID styleId = UUID.randomUUID();

      Style style = new Style();
      style.setId(styleId);
      style.setDataSet(dataSet(dataSetId));

      when(styleRepository.findById(styleId)).thenReturn(Optional.of(style));

      assertThat(styleService.findByIdAndDataSetOrThrow(styleId, dataSetId)).isSameAs(style);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when Style belongs to a different dataset")
    void shouldThrowWhenDatasetMismatch() {
      UUID styleId = UUID.randomUUID();

      Style style = new Style();
      style.setId(styleId);
      style.setDataSet(dataSet(UUID.randomUUID()));

      when(styleRepository.findById(styleId)).thenReturn(Optional.of(style));

      assertThatThrownBy(() -> styleService.findByIdAndDataSetOrThrow(styleId, UUID.randomUUID()))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("postConvertToEntity()")
  class PostConvertToEntity {

    @Test
    @DisplayName("Should resolve dataset from input ID")
    void shouldResolveDataset() {
      UUID dataSetId = UUID.randomUUID();
      DataSet ds = dataSet(dataSetId);

      StyleInputDTO input = new StyleInputDTO();
      input.setDataSetId(dataSetId);
      input.setName("sld-name");
      input.setSldContent("<sld/>");

      Style entity = new Style();
      when(styleMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(ds));
      when(styleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Style result = styleService.create(input);

      assertThat(result.getDataSet()).isSameAs(ds);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when dataset not found")
    void shouldThrowWhenDatasetNotFound() {
      UUID dataSetId = UUID.randomUUID();

      StyleInputDTO input = new StyleInputDTO();
      input.setDataSetId(dataSetId);
      input.setName("sld-name");
      input.setSldContent("<sld/>");

      Style entity = new Style();
      when(styleMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> styleService.create(input))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("preProcessUpdateInput()")
  class PreProcessUpdateInput {

    @Test
    @DisplayName("Should throw ResourceNotFoundException when trying to change the dataset")
    void shouldThrowWhenDatasetChanges() {
      UUID styleId = UUID.randomUUID();
      DataSet original = dataSet(UUID.randomUUID());

      Style existing = new Style();
      existing.setId(styleId);
      existing.setDataSet(original);

      StyleInputDTO input = new StyleInputDTO();
      input.setDataSetId(UUID.randomUUID());
      input.setName("updated");
      input.setSldContent("<sld/>");

      assertThatThrownBy(() -> styleService.preProcessUpdateInput(input, existing))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("preProcessDelete()")
  class PreProcessDelete {

    @Test
    @DisplayName("Should allow delete when no Layer references this Style as default")
    void shouldAllowDeleteWhenNoLayerReferences() {
      UUID styleId = UUID.randomUUID();
      Style style = new Style();
      style.setId(styleId);

      when(styleRepository.existsById(styleId)).thenReturn(true);
      when(styleRepository.findById(styleId)).thenReturn(Optional.of(style));
      when(layerRepository.existsByDefaultStyleId(styleId)).thenReturn(false);

      assertThatCode(() -> styleService.deleteById(styleId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
        "Should throw ResourceInUseException when a Layer references this Style as default")
    void shouldThrowWhenLayerReferencesStyleAsDefault() {
      UUID styleId = UUID.randomUUID();
      Style style = new Style();
      style.setId(styleId);

      when(styleRepository.existsById(styleId)).thenReturn(true);
      when(styleRepository.findById(styleId)).thenReturn(Optional.of(style));
      when(layerRepository.existsByDefaultStyleId(styleId)).thenReturn(true);

      assertThatThrownBy(() -> styleService.deleteById(styleId))
          .isInstanceOf(ResourceInUseException.class);
    }
  }
}
