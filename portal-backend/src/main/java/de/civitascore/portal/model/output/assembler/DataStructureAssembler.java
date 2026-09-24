package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final DataSourceRepository dataSourceRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  /** {@inheritDoc} Delegates to the {@link DataStructureMapper} for basic field mapping. */
  @Override
  public DataStructureOutputDTO mapToBaseDto(DataStructure entity) {
    return dataStructureMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Sets the {@code inUse} flag from whether anything still references one of this
   * structure's versions.
   */
  @Override
  public DataStructureOutputDTO enrichDto(DataStructureOutputDTO dto, DataStructure entity) {
    Set<DataStructureVersion> versions = entity.getDataStructureVersions();
    Set<UUID> versionIds =
        versions.stream().map(DataStructureVersion::getId).collect(Collectors.toSet());
    if (!versionIds.isEmpty()) {
      // The registry answers for every reference it holds onto a version's model; the host FK
      // covers a data source pinned to the version, which the registry does not record.
      dto.setInUse(
          dataSourceRepository.existsByDataStructureVersionIdIn(versionIds)
              || versions.stream()
                  .map(DataStructureVersion::getModelUrn)
                  .anyMatch(modelRegistryGateway::isReferenced));
    }
    return dto;
  }

  /** {@inheritDoc} Converts a data structure entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataStructure entity) {
    return (I) dataStructureMapper.toInput(entity);
  }
}
