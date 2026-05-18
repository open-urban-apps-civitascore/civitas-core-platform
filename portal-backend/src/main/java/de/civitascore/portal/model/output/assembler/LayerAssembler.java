package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.LayerMapper;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.output.LayerOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Layer} entities to {@link LayerOutputDTO}. The {@code
 * alternativeStyleIds} field is populated in the enrichDto hook (wired in the enrichment layer).
 */
@Component
@RequiredArgsConstructor
public class LayerAssembler implements BaseAssembler<Layer, LayerOutputDTO, UUID> {

  private final LayerMapper layerMapper;

  @Override
  public LayerOutputDTO mapToBaseDto(Layer entity) {
    return layerMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Layer entity) {
    return (I) layerMapper.toInput(entity);
  }
}
