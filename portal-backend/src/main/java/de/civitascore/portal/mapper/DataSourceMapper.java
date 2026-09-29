package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link DataSourceInputDTO}, {@link DataSourceOutputDTO},
 * and {@link DataSource}.
 */
@Mapper(
    componentModel = "spring",
    uses = {DataStructureVersionMapper.class},
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataSourceMapper
    extends DtoMapper<DataSourceInputDTO, DataSourceOutputDTO, DataSource> {

  @Mapping(target = "dataSourceStatus", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "dataStructureVersion", ignore = true)
  @Mapping(target = "datapoolScopeType", ignore = true)
  @Mapping(target = "scopedDataPools", ignore = true)
  @Override
  DataSource toEntity(DataSourceInputDTO input);

  @Mapping(target = "datapoolScope", ignore = true)
  @Override
  DataSourceOutputDTO toOutput(DataSource entity);

  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "datapoolScope", ignore = true)
  @Mapping(source = "dataStructureVersion.id", target = "dataStructureVersionId")
  @Override
  DataSourceInputDTO toInput(DataSource entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSourceStatus", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "dataStructureVersion", ignore = true)
  @Mapping(target = "datapoolScopeType", ignore = true)
  @Mapping(target = "scopedDataPools", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSource entity, DataSourceInputDTO input);
}
