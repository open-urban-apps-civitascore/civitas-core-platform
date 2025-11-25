package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.model.output.DataSpaceOutputDTO;
import de.civitascore.portal.model.output.summary.DataSpaceSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {UserMapper.class},
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataSpaceMapper
    extends DtoMapper<DataSpaceInputDTO, DataSpaceOutputDTO, DataSpace> {

  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSets", ignore = true)
  @Mapping(target = "parentDataSpace", ignore = true)
  @Mapping(target = "childDataSpaces", ignore = true)
  @Override
  DataSpace toEntity(DataSpaceInputDTO input);

  @Override
  DataSpaceOutputDTO toOutput(DataSpace entity);

  @Mapping(target = "ownerUserId", source = "owner.id")
  @Mapping(target = "parentDataSpaceId", source = "parentDataSpace.id")
  @Override
  DataSpaceInputDTO toInput(DataSpace entity);

  DataSpaceSummaryDTO toSummary(DataSpace entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSets", ignore = true)
  @Mapping(target = "parentDataSpace", ignore = true)
  @Mapping(target = "childDataSpaces", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSpace entity, DataSpaceInputDTO input);
}
