package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataStructureVersionAssembler
    implements BaseAssembler<DataStructureVersion, DataStructureVersionOutputDTO, UUID> {

  private final DataStructureVersionMapper dataStructureVersionMapper;

  @Override
  public DataStructureVersionOutputDTO mapToBaseDto(DataStructureVersion entity) {
    return dataStructureVersionMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataStructureVersion entity) {
    return (I) dataStructureVersionMapper.toInput(entity);
  }
}
