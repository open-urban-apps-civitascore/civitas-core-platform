package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.StyleMapper;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.output.StyleOutputDTO;
import de.civitascore.portal.repository.LayerRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StyleAssemblerTest {

  @Mock private StyleMapper styleMapper;
  @Mock private LayerRepository layerRepository;

  @InjectMocks private StyleAssembler assembler;

  @Nested
  @DisplayName("enrichDto() — inUse flag")
  class InUseEnrichment {

    @Test
    @DisplayName("Should set inUse to true when a Layer references this Style")
    void shouldSetInUseTrueWhenReferenced() {
      UUID styleId = UUID.randomUUID();
      Style entity = new Style();
      entity.setId(styleId);

      when(layerRepository.existsByDefaultStyleIdOrAlternativeStylesId(styleId, styleId))
          .thenReturn(true);

      StyleOutputDTO result = assembler.enrichDto(new StyleOutputDTO(), entity);

      assertThat(result.isInUse()).isTrue();
    }

    @Test
    @DisplayName("Should set inUse to false when no Layer references this Style")
    void shouldSetInUseFalseWhenNotReferenced() {
      UUID styleId = UUID.randomUUID();
      Style entity = new Style();
      entity.setId(styleId);

      when(layerRepository.existsByDefaultStyleIdOrAlternativeStylesId(styleId, styleId))
          .thenReturn(false);

      StyleOutputDTO result = assembler.enrichDto(new StyleOutputDTO(), entity);

      assertThat(result.isInUse()).isFalse();
    }
  }
}
