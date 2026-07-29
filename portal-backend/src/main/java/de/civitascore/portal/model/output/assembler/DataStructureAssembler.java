package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.repository.DataSinkRepository;
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
  private final DataSinkRepository dataSinkRepository;

  /** {@inheritDoc} Delegates to the {@link DataStructureMapper} for basic field mapping. */
  @Override
  public DataStructureOutputDTO mapToBaseDto(DataStructure entity) {
    return dataStructureMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Sets the {@code inUse} flag based on whether any data source or data sink
   * references one of this structure's versions.
   */
  @Override
  public DataStructureOutputDTO enrichDto(DataStructureOutputDTO dto, DataStructure entity) {
    Set<UUID> versionIds =
        entity.getDataStructureVersions().stream()
            .map(DataStructureVersion::getId)
            .collect(Collectors.toSet());
    if (!versionIds.isEmpty()) {
      dto.setInUse(
          dataSourceRepository.existsByDataStructureVersionIdIn(versionIds)
              || dataSinkRepository.existsByDataStructureVersionIdIn(versionIds));
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
