package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.StyleMapper;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.output.StyleOutputDTO;
import de.civitascore.portal.repository.LayerRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StyleAssembler implements BaseAssembler<Style, StyleOutputDTO> {

  private final StyleMapper styleMapper;
  private final LayerRepository layerRepository;

  @Override
  public StyleOutputDTO mapToBaseDto(Style entity) {
    return styleMapper.toOutput(entity);
  }

  @Override
  public StyleOutputDTO enrichDto(StyleOutputDTO dto, Style entity) {
    UUID styleId = entity.getId();
    dto.setInUse(layerRepository.existsByDefaultStyleIdOrAlternativeStylesId(styleId, styleId));
    return dto;
  }
}
