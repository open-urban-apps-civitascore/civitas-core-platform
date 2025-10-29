package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {UserMapper.class, DataSpaceMapper.class})
public interface DataSetMapper extends DtoMapper<DataSetInputDTO, DataSetOutputDTO, DataSet> {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSpaces", ignore = true)
  @Override
  DataSet toEntity(DataSetInputDTO input);

  @Override
  DataSetOutputDTO toOutput(DataSet entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSpaces", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSet entity, DataSetInputDTO input);
}
