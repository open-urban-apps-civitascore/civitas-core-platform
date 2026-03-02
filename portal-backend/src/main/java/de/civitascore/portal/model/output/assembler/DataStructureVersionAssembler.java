package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataStructureVersionAssembler
    implements BaseAssembler<DataStructureVersion, DataStructureVersionOutputDTO, UUID> {

  private final DataStructureVersionMapper dataStructureVersionMapper;
  private final DataSourceRepository dataSourceRepository;

  @Override
  public DataStructureVersionOutputDTO mapToBaseDto(DataStructureVersion entity) {
    return dataStructureVersionMapper.toOutput(entity);
  }

  @Override
  public DataStructureVersionOutputDTO enrichDto(
      DataStructureVersionOutputDTO dto, DataStructureVersion entity) {
    dto.setInUse(dataSourceRepository.existsByDataStructureVersionId(entity.getId()));
    return dto;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataStructureVersion entity) {
    return (I) dataStructureVersionMapper.toInput(entity);
  }
}
