package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.input.DistributionInputDTO;
import de.civitascore.portal.model.output.DistributionOutputDTO;
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
public interface DistributionMapper
    extends DtoMapper<DistributionInputDTO, DistributionOutputDTO, Distribution> {
  @Mapping(target = "resource", ignore = true)
  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "activity", ignore = true)
  @Override
  Distribution toEntity(DistributionInputDTO input);

  @Override
  DistributionOutputDTO toOutput(Distribution entity);

  @Mapping(target = "resourceId", ignore = true)
  @Override
  DistributionInputDTO toInput(Distribution entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "resource", ignore = true)
  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "activity", ignore = true)
  @Override
  void updateEntity(@MappingTarget Distribution entity, DistributionInputDTO input);
}
