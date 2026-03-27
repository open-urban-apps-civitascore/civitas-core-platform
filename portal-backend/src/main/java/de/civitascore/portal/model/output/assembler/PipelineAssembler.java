package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Pipeline} entities to {@link PipelineOutputDTO}. Participates in
 * the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class PipelineAssembler implements BaseAssembler<Pipeline, PipelineOutputDTO, UUID> {

  private final PipelineMapper pipelineMapper;

  /** {@inheritDoc} Delegates to the {@link PipelineMapper} for basic field mapping. */
  @Override
  public PipelineOutputDTO mapToBaseDto(Pipeline entity) {
    return pipelineMapper.toOutput(entity);
  }

  /** {@inheritDoc} Converts a pipeline entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Pipeline entity) {
    return (I) pipelineMapper.toInput(entity);
  }
}
