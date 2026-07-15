package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataStructureVersion} entities to {@link
 * DataStructureVersionOutputDTO}. Participates in the template method pattern defined by {@link
 * BaseAssembler}.
 *
 * <p>The model content lives in the Model Forge registry: {@link #enrichDto} serves the DTO's
 * {@code model} from the bundled schema view of the version's {@code modelUrn} pin and its {@code
 * styles} from the document's {@code x-ui-styles} keyword — the frontend contract (separate
 * model/styles fields) stays stable.
 */
@Component
@RequiredArgsConstructor
public class DataStructureVersionAssembler
    implements BaseAssembler<DataStructureVersion, DataStructureVersionOutputDTO, UUID> {

  private final DataStructureVersionMapper dataStructureVersionMapper;
  private final DataSourceRepository dataSourceRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  /** {@inheritDoc} Delegates to the {@link DataStructureVersionMapper} for basic field mapping. */
  @Override
  public DataStructureVersionOutputDTO mapToBaseDto(DataStructureVersion entity) {
    return dataStructureVersionMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Sets the {@code inUse} flag based on whether any data source or data sink
   * references this version, and serves {@code model}/{@code styles} from the registry pin.
   */
  @Override
  public DataStructureVersionOutputDTO enrichDto(
      DataStructureVersionOutputDTO dto, DataStructureVersion entity) {
    dto.setInUse(
        dataSourceRepository.existsByDataStructureVersionId(entity.getId())
            || modelRegistryGateway.isReferencedBySink(entity.getModelUrn()));
    if (entity.getModelUrn() != null) {
      modelRegistryGateway
          .fetchModel(entity.getModelUrn())
          .ifPresent(
              document -> {
                dto.setModel(document.content());
                dto.setStyles(document.styles());
              });
    }
    return dto;
  }

  /**
   * {@inheritDoc} Converts a data structure version entity back to its input DTO for PATCH
   * operations. The current model/styles are read from the registry so a partial patch (e.g. styles
   * only) still carries the full content forward.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataStructureVersion entity) {
    DataStructureVersionInputDTO input = dataStructureVersionMapper.toInput(entity);
    if (entity.getModelUrn() != null) {
      modelRegistryGateway
          .fetchModel(entity.getModelUrn())
          .ifPresent(
              document -> {
                input.setModel(document.content());
                input.setStyles(document.styles());
              });
    }
    return (I) input;
  }
}
