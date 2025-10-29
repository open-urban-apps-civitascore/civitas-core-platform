package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.model.output.DataSpaceOutputDTO;
import de.civitascore.portal.model.output.summary.DataSpaceSummaryDTO;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {UserMapper.class})
public interface DataSpaceMapper
    extends DtoMapper<DataSpaceInputDTO, DataSpaceOutputDTO, DataSpace> {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSets", ignore = true)
  @Mapping(target = "parentDataSpace", ignore = true)
  @Mapping(target = "childDataSpaces", ignore = true)
  @Override
  DataSpace toEntity(DataSpaceInputDTO input);

  @Override
  DataSpaceOutputDTO toOutput(DataSpace entity);

  DataSpaceSummaryDTO toSummary(DataSpace entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSets", ignore = true)
  @Mapping(target = "parentDataSpace", ignore = true)
  @Mapping(target = "childDataSpaces", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSpace entity, DataSpaceInputDTO input);
}
