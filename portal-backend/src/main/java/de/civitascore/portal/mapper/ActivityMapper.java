package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Activity;
import de.civitascore.portal.model.input.ActivityInputDTO;
import de.civitascore.portal.model.output.ActivityOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link ActivityInputDTO}, {@link ActivityOutputDTO}, and
 * {@link Activity}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ActivityMapper extends DtoMapper<ActivityInputDTO, ActivityOutputDTO, Activity> {
  @Mapping(target = "agents", ignore = true)
  @Mapping(target = "distributions", ignore = true)
  @Override
  Activity toEntity(ActivityInputDTO input);

  @Override
  ActivityOutputDTO toOutput(Activity entity);

  @Mapping(target = "agentIds", ignore = true)
  @Override
  ActivityInputDTO toInput(Activity entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "agents", ignore = true)
  @Mapping(target = "distributions", ignore = true)
  @Override
  void updateEntity(@MappingTarget Activity entity, ActivityInputDTO input);
}
