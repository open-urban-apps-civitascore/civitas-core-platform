package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.StyleMapper;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.output.StyleOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Style} entities to {@link StyleOutputDTO}. The {@code inUse} flag
 * is populated in the enrichDto hook (wired in the enrichment layer).
 */
@Component
@RequiredArgsConstructor
public class StyleAssembler implements BaseAssembler<Style, StyleOutputDTO, UUID> {

  private final StyleMapper styleMapper;

  @Override
  public StyleOutputDTO mapToBaseDto(Style entity) {
    return styleMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Style entity) {
    return (I) styleMapper.toInput(entity);
  }
}
