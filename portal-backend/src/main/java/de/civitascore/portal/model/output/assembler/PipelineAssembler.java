package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.repository.DataSinkRepository;
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
  private final DataSinkAssembler dataSinkAssembler;
  private final DataSinkRepository dataSinkRepository;

  /** {@inheritDoc} Delegates to the {@link PipelineMapper} for basic field mapping. */
  @Override
  public PipelineOutputDTO mapToBaseDto(Pipeline entity) {
    return pipelineMapper.toOutput(entity);
  }

  /** {@inheritDoc} Loads DataSinks for this pipeline and populates the output DTO. */
  @Override
  public PipelineOutputDTO enrichDto(PipelineOutputDTO dto, Pipeline entity) {
    List<DataSinkOutputDTO> sinks =
        dataSinkRepository.findByPipelineId(entity.getId()).stream()
            .map(dataSinkAssembler::toOutput)
            .toList();
    dto.setDataSinks(sinks);
    return dto;
  }

  /** {@inheritDoc} Converts a pipeline entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Pipeline entity) {
    PipelineInputDTO input = pipelineMapper.toInput(entity);
    input.setDataSinks(
        dataSinkRepository.findByPipelineId(entity.getId()).stream()
            .<DataSinkInputDTO>map(dataSinkAssembler::toInput)
            .toList());
    return (I) input;
  }
}
