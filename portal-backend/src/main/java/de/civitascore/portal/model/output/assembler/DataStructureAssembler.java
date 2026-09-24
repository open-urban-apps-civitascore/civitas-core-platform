package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureVersionUsageSummaryDTO;
import de.civitascore.portal.service.ArtifactUsageLookup;
import de.civitascore.portal.service.ArtifactUsageLookup.ArtifactUsage;
import java.util.Map;
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
    Map<UUID, ArtifactUsage> usageByVersion = artifactUsageLookup.ofEachVersion(entity);
    dto.setDataStructureVersions(
        entity.getDataStructureVersions().stream()
            .map(version -> toVersionRow(version, usageByVersion.get(version.getId())))
            .toList());
    ArtifactUsage usage = ArtifactUsage.anyOf(usageByVersion.values());
    dto.setInUse(usage.inUse());
    dto.setInUseByReleased(usage.inUseByReleased());
    return dto;
  }

  private DataStructureVersionUsageSummaryDTO toVersionRow(
      DataStructureVersion version, ArtifactUsage usage) {
    DataStructureVersionUsageSummaryDTO row = dataStructureVersionMapper.toUsageSummary(version);
    row.setInUse(usage.inUse());
    row.setInUseByReleased(usage.inUseByReleased());
    return row;
  }

  /** {@inheritDoc} Converts a data structure entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataStructure entity) {
    return (I) dataStructureMapper.toInput(entity);
  }
}
