package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Pipeline} entities to {@link PipelineOutputDTO}. Participates in
 * the template method pattern defined by {@link BaseAssembler}.
 *
 * <p>The pipeline definition lives in the Model Forge registry: {@link #enrichDto} serves the DTO's
 * {@code model} from the pinned artifact's authored content and its {@code styles} (React Flow
 * layout) from the document's {@code x-ui-styles} keyword — the frontend contract (separate
 * model/styles fields) stays stable.
 */
@Component
@RequiredArgsConstructor
public class PipelineAssembler implements BaseAssembler<Pipeline, PipelineOutputDTO, UUID> {

  private final PipelineMapper pipelineMapper;
  private final DataSinkRepository dataSinkRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  /** {@inheritDoc} Delegates to the {@link PipelineMapper} for basic field mapping. */
  @Override
  public PipelineOutputDTO mapToBaseDto(Pipeline entity) {
    return pipelineMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Loads DataSink IDs linked to this pipeline and serves {@code model}/{@code
   * styles} from the registry pin.
   */
  @Override
  public PipelineOutputDTO enrichDto(PipelineOutputDTO dto, Pipeline entity) {
    dto.setDataSinkIds(findDataSinkIds(entity));
    if (entity.getModelUrn() != null) {
      modelRegistryGateway
          .fetchPayload(entity.getModelUrn())
          .ifPresent(
              document -> {
                dto.setModel(document.content());
                dto.setStyles(document.styles());
              });
    }
    return dto;
  }

  /**
   * {@inheritDoc} Converts a pipeline entity back to its input DTO for PATCH operations. The
   * current model/styles are read from the registry so a partial patch still carries the full
   * definition forward.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Pipeline entity) {
    PipelineInputDTO input = pipelineMapper.toInput(entity);
    input.setDataSinkIds(new HashSet<>(findDataSinkIds(entity)));
    if (entity.getModelUrn() != null) {
      modelRegistryGateway
          .fetchPayload(entity.getModelUrn())
          .ifPresent(
              document -> {
                input.setModel(document.content());
                input.setStyles(document.styles());
              });
    }
    return (I) input;
  }

  private List<UUID> findDataSinkIds(Pipeline entity) {
    return dataSinkRepository.findByPipelineId(entity.getId()).stream()
        .map(DataSink::getId)
        .toList();
  }
}
