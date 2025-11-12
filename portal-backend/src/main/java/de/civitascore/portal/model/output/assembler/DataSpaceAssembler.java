package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSpaceMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.output.DataSpaceOutputDTO;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSpaceAssembler implements BaseAssembler<DataSpace, DataSpaceOutputDTO, String> {

  private final DataSpaceMapper dataSpaceMapper;
  private final UserMapper userMapper;

  @Override
  public DataSpaceOutputDTO mapToBaseDto(DataSpace entity) {
    DataSpaceOutputDTO output = dataSpaceMapper.toOutput(entity);

    // Map owner
    if (entity.getOwner() != null) {
      output.setOwner(userMapper.toSummary(entity.getOwner()));
    }

    // Map parentDataSpace
    if (entity.getParentDataSpace() != null) {
      output.setParentDataSpace(dataSpaceMapper.toSummary(entity.getParentDataSpace()));
    }

    // Map childDataSpaces
    if (entity.getChildDataSpaces() != null && !entity.getChildDataSpaces().isEmpty()) {
      output.setChildDataSpaces(
          entity.getChildDataSpaces().stream()
              .map(dataSpaceMapper::toSummary)
              .collect(Collectors.toList()));
    }

    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSpace entity) {
    return (I) dataSpaceMapper.toInput(entity);
  }
}
