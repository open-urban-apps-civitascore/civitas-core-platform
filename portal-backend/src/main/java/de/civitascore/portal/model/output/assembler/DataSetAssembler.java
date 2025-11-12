package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.mapper.DataSpaceMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSetAssembler implements BaseAssembler<DataSet, DataSetOutputDTO, String> {

  private final DataSetMapper dataSetMapper;
  private final UserMapper userMapper;
  private final DataSpaceMapper dataSpaceMapper;

  @Override
  public DataSetOutputDTO mapToBaseDto(DataSet entity) {
    DataSetOutputDTO output = dataSetMapper.toOutput(entity);

    // Map owner
    if (entity.getOwner() != null) {
      output.setOwner(userMapper.toSummary(entity.getOwner()));
    }

    // Map dataSpaces
    if (entity.getDataSpaces() != null && !entity.getDataSpaces().isEmpty()) {
      output.setDataSpaces(
          entity.getDataSpaces().stream()
              .map(dataSpaceMapper::toSummary)
              .collect(Collectors.toList()));
    }

    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSet entity) {
    return (I) dataSetMapper.toInput(entity);
  }
}
