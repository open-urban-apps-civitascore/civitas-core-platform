package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSetAssembler implements BaseAssembler<DataSet, DataSetOutputDTO, UUID> {

  private final DataSetMapper dataSetMapper;
  private final UserRepository userRepository;
  private final UserMapper userMapper;

  @Override
  public DataSetOutputDTO mapToBaseDto(DataSet entity) {
    return dataSetMapper.toOutput(entity);
  }

  @Override
  public DataSetOutputDTO enrichDto(DataSetOutputDTO dto, DataSet entity) {
    if (entity.getCreatedBy() != null) {
      userRepository
          .findByExternalId(entity.getCreatedBy().toString())
          .ifPresent(user -> dto.setCreatedBy(userMapper.toSummary(user)));
    }
    return dto;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSet entity) {
    return (I) dataSetMapper.toInput(entity);
  }
}
