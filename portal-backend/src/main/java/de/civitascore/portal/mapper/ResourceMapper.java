package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Resource;
import de.civitascore.portal.model.input.ResourceInputDTO;
import de.civitascore.portal.model.output.ResourceOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ResourceMapper extends DtoMapper<ResourceInputDTO, ResourceOutputDTO, Resource> {
  @Mapping(target = "distributions", ignore = true)
  @Override
  Resource toEntity(ResourceInputDTO input);

  @Override
  ResourceOutputDTO toOutput(Resource entity);

  @Override
  ResourceInputDTO toInput(Resource entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "distributions", ignore = true)
  @Override
  void updateEntity(@MappingTarget Resource entity, ResourceInputDTO input);
}
