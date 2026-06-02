package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.mapper.LayerMapper;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.output.LayerOutputDTO;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LayerAssemblerTest {

  @Mock private LayerMapper layerMapper;

  @InjectMocks private LayerAssembler assembler;

  @Nested
  @DisplayName("enrichDto() — alternativeStyleIds")
  class AlternativeStyleIdsEnrichment {

    @Test
    @DisplayName("Should populate alternativeStyleIds from the entity's alternativeStyles set")
    void shouldPopulateAlternativeStyleIds() {
      UUID styleId1 = UUID.randomUUID();
      UUID styleId2 = UUID.randomUUID();

      Style style1 = new Style();
      style1.setId(styleId1);
      Style style2 = new Style();
      style2.setId(styleId2);

      Layer entity = new Layer();
      entity.setAlternativeStyles(Set.of(style1, style2));

      LayerOutputDTO result = assembler.enrichDto(new LayerOutputDTO(), entity);

      assertThat(result.getAlternativeStyleIds()).containsExactlyInAnyOrder(styleId1, styleId2);
    }

    @Test
    @DisplayName("Should set an empty list when the entity has no alternative styles")
    void shouldSetEmptyListWhenNoAlternativeStyles() {
      Layer entity = new Layer();

      LayerOutputDTO result = assembler.enrichDto(new LayerOutputDTO(), entity);

      assertThat(result.getAlternativeStyleIds()).isNotNull().isEmpty();
    }
  }
}
