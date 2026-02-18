package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSetAssembler implements BaseAssembler<DataSet, DataSetOutputDTO, UUID> {

  private final DataSetMapper dataSetMapper;

  @Override
  public DataSetOutputDTO mapToBaseDto(DataSet entity) {
    return dataSetMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSet entity) {
    return (I) dataSetMapper.toInput(entity);
  }
}
