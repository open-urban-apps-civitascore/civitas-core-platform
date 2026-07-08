package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataStructureVersion} entities to {@link
 * DataStructureVersionOutputDTO}. Participates in the template method pattern defined by {@link
 * BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class DataStructureVersionAssembler
    implements BaseAssembler<DataStructureVersion, DataStructureVersionOutputDTO, UUID> {

  private final DataStructureVersionMapper dataStructureVersionMapper;
  private final DataSourceRepository dataSourceRepository;
  private final DataSinkRepository dataSinkRepository;

  /** {@inheritDoc} Delegates to the {@link DataStructureVersionMapper} for basic field mapping. */
  @Override
  public DataStructureVersionOutputDTO mapToBaseDto(DataStructureVersion entity) {
    return dataStructureVersionMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Sets the {@code inUse} flag based on whether any data source or data sink
   * references this version.
   */
  @Override
  public DataStructureVersionOutputDTO enrichDto(
      DataStructureVersionOutputDTO dto, DataStructureVersion entity) {
    dto.setInUse(
        dataSourceRepository.existsByDataStructureVersionId(entity.getId())
            || dataSinkRepository.existsByDataStructureVersionId(entity.getId()));
    return dto;
  }

  /**
   * {@inheritDoc} Converts a data structure version entity back to its input DTO for PATCH
   * operations.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataStructureVersion entity) {
    return (I) dataStructureVersionMapper.toInput(entity);
  }
}
