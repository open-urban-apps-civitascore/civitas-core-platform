package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PipelineAssembler implements BaseAssembler<Pipeline, PipelineOutputDTO, UUID> {

  private final PipelineMapper pipelineMapper;

  @Override
  public PipelineOutputDTO mapToBaseDto(Pipeline entity) {
    return pipelineMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Pipeline entity) {
    return (I) pipelineMapper.toInput(entity);
  }
}
