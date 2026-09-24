package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.service.ArtifactUsageLookup;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataStructure} entities to {@link DataStructureOutputDTO}.
 * Participates in the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class DataStructureAssembler
    implements BaseAssembler<DataStructure, DataStructureOutputDTO, UUID> {

  private final DataStructureMapper dataStructureMapper;
  private final ArtifactUsageLookup artifactUsageLookup;

  /** {@inheritDoc} Delegates to the {@link DataStructureMapper} for basic field mapping. */
  @Override
  public DataStructureOutputDTO mapToBaseDto(DataStructure entity) {
    return dataStructureMapper.toOutput(entity);
  }

  /** {@inheritDoc} Sets the {@code inUse} and {@code inUseByReleased} flags. */
  @Override
  public DataStructureOutputDTO enrichDto(DataStructureOutputDTO dto, DataStructure entity) {
    ArtifactUsageLookup.ArtifactUsage usage = artifactUsageLookup.of(entity);
    dto.setInUse(usage.inUse());
    dto.setInUseByReleased(usage.inUseByReleased());
    return dto;
  }

  /** {@inheritDoc} Converts a data structure entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataStructure entity) {
    return (I) dataStructureMapper.toInput(entity);
  }
}
