package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.model.output.summary.DataSourceSummaryDTO;
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
public interface DataSourceMapper
    extends DtoMapper<DataSourceInputDTO, DataSourceOutputDTO, DataSource> {

  @Mapping(target = "dataSourceStatus", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Override
  DataSource toEntity(DataSourceInputDTO input);

  @Override
  DataSourceOutputDTO toOutput(DataSource entity);

  @Mapping(target = "assignments", ignore = true)
  @Override
  DataSourceInputDTO toInput(DataSource entity);

  DataSourceSummaryDTO toSummary(DataSource entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSourceStatus", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSource entity, DataSourceInputDTO input);
}
