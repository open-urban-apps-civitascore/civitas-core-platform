package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {
      UserMapper.class,
      DataSpaceMapper.class,
      DataSetSeriesMapper.class,
      AgentMapper.class,
      DistributionMapper.class,
      CatalogMapper.class
    },
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataSetMapper extends DtoMapper<DataSetInputDTO, DataSetOutputDTO, DataSet> {
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSetSeries", ignore = true)
  @Mapping(target = "dataSpaces", ignore = true)
  @Mapping(target = "agents", ignore = true)
  @Mapping(target = "distributions", ignore = true)
  @Mapping(target = "catalogs", ignore = true)
  @Override
  DataSet toEntity(DataSetInputDTO input);

  @Override
  DataSetOutputDTO toOutput(DataSet entity);

  @Mapping(target = "ownerUserId", source = "owner.id")
  @Mapping(target = "dataSetSeriesId", source = "dataSetSeries.id")
  @Mapping(target = "dataSpaceIds", ignore = true)
  @Mapping(target = "agentIds", ignore = true)
  @Mapping(target = "distributionIds", ignore = true)
  @Mapping(target = "catalogIds", ignore = true)
  @Override
  DataSetInputDTO toInput(DataSet entity);

  DataSetSummaryDTO toSummary(DataSet entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSetSeries", ignore = true)
  @Mapping(target = "dataSpaces", ignore = true)
  @Mapping(target = "agents", ignore = true)
  @Mapping(target = "distributions", ignore = true)
  @Mapping(target = "catalogs", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSet entity, DataSetInputDTO input);
}
