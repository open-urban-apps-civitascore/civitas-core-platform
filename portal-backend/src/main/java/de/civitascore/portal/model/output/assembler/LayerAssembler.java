package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.LayerMapper;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.output.LayerOutputDTO;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LayerAssembler implements BaseAssembler<Layer, LayerOutputDTO, UUID> {

  private final LayerMapper layerMapper;

  @Override
  public LayerOutputDTO mapToBaseDto(Layer entity) {
    return layerMapper.toOutput(entity);
  }

  @Override
  public LayerOutputDTO enrichDto(LayerOutputDTO dto, Layer entity) {
    List<UUID> altIds = entity.getAlternativeStyles().stream().map(style -> style.getId()).toList();
    dto.setAlternativeStyleIds(altIds);
    return dto;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Layer entity) {
    return (I) layerMapper.toInput(entity);
  }
}
