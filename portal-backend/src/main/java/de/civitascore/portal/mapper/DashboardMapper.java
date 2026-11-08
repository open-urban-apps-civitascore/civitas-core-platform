package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.model.output.DashboardOutputDTO;
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
public interface DashboardMapper
    extends DtoMapper<DashboardInputDTO, DashboardOutputDTO, Dashboard> {

  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "active", ignore = true)
  @Mapping(target = "externalDashboardId", ignore = true)
  @Override
  Dashboard toEntity(DashboardInputDTO input);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Override
  DashboardOutputDTO toOutput(Dashboard entity);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Override
  DashboardInputDTO toInput(Dashboard entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "active", ignore = true)
  @Mapping(target = "externalDashboardId", ignore = true)
  @Mapping(
      target = "released",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(
      target = "standaloneMode",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(
      target = "showFilters",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(
      target = "filterExpansion",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  // An edit in the portal sends no bundle; it must not erase one that is waiting for the release.
  @Mapping(
      target = "supersetBundle",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Override
  void updateEntity(@MappingTarget Dashboard entity, DashboardInputDTO input);
}
