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

  /**
   * Projects a data source for a caller authorized on the consuming dataset rather than on the
   * source. Mapped by an explicit allow-list: with by-name mapping, declaring a field on the DTO
   * would be enough to start emitting it, and {@code configuration} holds the connector
   * credentials.
   */
  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "id")
  @Mapping(target = "name")
  @Mapping(target = "description")
  @Mapping(target = "connectorType")
  DataSourceSummaryDTO toSummary(DataSource entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSourceStatus", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "dataStructureVersion", ignore = true)
  @Mapping(target = "datapoolScopeType", ignore = true)
  @Mapping(target = "scopedDataPools", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSource entity, DataSourceInputDTO input);
}
