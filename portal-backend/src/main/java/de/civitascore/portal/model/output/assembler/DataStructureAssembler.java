package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureVersionUsageSummaryDTO;
import de.civitascore.portal.service.ArtifactUsageLookup;
import de.civitascore.portal.service.ArtifactUsageLookup.ArtifactUsage;
import de.civitascore.portal.service.ArtifactUsageLookup.DataStructureUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataStructure} entities to {@link DataStructureOutputDTO}.
 * Participates in the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class DataStructureAssembler
    implements BaseAssembler<DataStructure, DataStructureOutputDTO> {

  private final DataStructureMapper dataStructureMapper;
  private final DataStructureVersionMapper dataStructureVersionMapper;
  private final ArtifactUsageLookup artifactUsageLookup;

  /** {@inheritDoc} Delegates to the {@link DataStructureMapper} for basic field mapping. */
  @Override
  public DataStructureOutputDTO mapToBaseDto(DataStructure entity) {
    return dataStructureMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Adds the version rows and sets the {@code inUse} and {@code inUseByReleased}
   * flags on each row and on the structure.
   */
  @Override
  public DataStructureOutputDTO enrichDto(DataStructureOutputDTO dto, DataStructure entity) {
    DataStructureUsage usage = artifactUsageLookup.ofStructureAndVersions(entity);
    dto.setDataStructureVersions(
        entity.getDataStructureVersions().stream()
            .map(version -> toVersionRow(version, usage.versions().get(version.getId())))
            .toList());
    dto.setInUse(usage.structure().inUse());
    dto.setInUseByReleased(usage.structure().inUseByReleased());
    return dto;
  }

  private DataStructureVersionUsageSummaryDTO toVersionRow(
      DataStructureVersion version, ArtifactUsage usage) {
    DataStructureVersionUsageSummaryDTO row = dataStructureVersionMapper.toUsageSummary(version);
    row.setInUse(usage.inUse());
    row.setInUseByReleased(usage.inUseByReleased());
    return row;
  }

  /** Converts a data structure entity back to its input DTO for PATCH operations. */
  public DataStructureInputDTO toInput(DataStructure entity) {
    return dataStructureMapper.toInput(entity);
  }
}
