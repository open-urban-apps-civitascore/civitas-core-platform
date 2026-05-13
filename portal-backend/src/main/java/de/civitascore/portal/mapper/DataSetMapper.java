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

/**
 * MapStruct mapper for converting between {@link DataSetInputDTO}, {@link DataSetOutputDTO}, and
 * {@link DataSet}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {
      DistributionMapper.class,
    },
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataSetMapper extends DtoMapper<DataSetInputDTO, DataSetOutputDTO, DataSet> {
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "dataSetSeries", ignore = true)
  @Mapping(target = "dataSpaces", ignore = true)
  @Mapping(target = "agents", ignore = true)
  @Mapping(target = "distributions", ignore = true)
  @Mapping(target = "catalogs", ignore = true)
  @Mapping(target = "pipelines", ignore = true)
  @Mapping(target = "projectId", ignore = true)
  @Mapping(target = "frostBaseUrl", ignore = true)
  @Mapping(target = "namedApis", ignore = true)
  @Mapping(target = "serviceId", ignore = true)
  @Mapping(target = "publicUrl", ignore = true)
  @Mapping(target = "pipelineIds", ignore = true)
  @Mapping(target = "pendingSagaType", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Override
  DataSet toEntity(DataSetInputDTO input);

  @Mapping(target = "createdBy", ignore = true)
  @Override
  DataSetOutputDTO toOutput(DataSet entity);

  @Mapping(target = "assignments", ignore = true)
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
  @Mapping(target = "pipelines", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "projectId", ignore = true)
  @Mapping(target = "frostBaseUrl", ignore = true)
  @Mapping(target = "namedApis", ignore = true)
  @Mapping(target = "serviceId", ignore = true)
  @Mapping(target = "publicUrl", ignore = true)
  @Mapping(target = "pipelineIds", ignore = true)
  @Mapping(target = "pendingSagaType", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSet entity, DataSetInputDTO input);
}
