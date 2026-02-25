package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataStructureMapper;
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
  private final DataStructureMapper dataStructureMapper;

  @Override
  public DataStructureVersionOutputDTO mapToBaseDto(DataStructureVersion entity) {
    DataStructureVersionOutputDTO output = dataStructureVersionMapper.toOutput(entity);

    // Map dataStructure
    if (entity.getDataStructure() != null) {
      output.setDataStructure(dataStructureMapper.toSummary(entity.getDataStructure()));
    }

    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataStructureVersion entity) {
    return (I) dataStructureVersionMapper.toInput(entity);
  }
}
