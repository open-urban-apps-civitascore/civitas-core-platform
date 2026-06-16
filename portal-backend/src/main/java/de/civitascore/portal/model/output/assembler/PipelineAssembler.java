package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.repository.DataSinkRepository;
import java.util.HashSet;
import java.util.List;
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
  private final DataSinkRepository dataSinkRepository;

  /** {@inheritDoc} Delegates to the {@link PipelineMapper} for basic field mapping. */
  @Override
  public PipelineOutputDTO mapToBaseDto(Pipeline entity) {
    return pipelineMapper.toOutput(entity);
  }

  /** {@inheritDoc} Loads DataSink IDs linked to this pipeline. */
  @Override
  public PipelineOutputDTO enrichDto(PipelineOutputDTO dto, Pipeline entity) {
    dto.setDataSinkIds(findDataSinkIds(entity));
    return dto;
  }

  /** {@inheritDoc} Converts a pipeline entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Pipeline entity) {
    PipelineInputDTO input = pipelineMapper.toInput(entity);
    input.setDataSinkIds(new HashSet<>(findDataSinkIds(entity)));
    return (I) input;
  }

  private List<UUID> findDataSinkIds(Pipeline entity) {
    return dataSinkRepository.findByPipelineId(entity.getId()).stream()
        .map(DataSink::getId)
        .toList();
  }
}
