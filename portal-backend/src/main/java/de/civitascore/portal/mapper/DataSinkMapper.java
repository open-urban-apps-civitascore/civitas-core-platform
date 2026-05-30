package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link DataSinkInputDTO}, {@link DataSinkOutputDTO}, and
 * {@link DataSink}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataSinkMapper extends DtoMapper<DataSinkInputDTO, DataSinkOutputDTO, DataSink> {

  @Mapping(target = "pipeline", ignore = true)
  @Override
  DataSink toEntity(DataSinkInputDTO input);

  @Mapping(target = "dataSetId", ignore = true)
  @Mapping(target = "pipelineId", source = "pipeline.id")
  @Mapping(target = "configuration", ignore = true)
  @Override
  DataSinkOutputDTO toOutput(DataSink entity);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "pipelineId", source = "pipeline.id")
  @Override
  DataSinkInputDTO toInput(DataSink entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "pipeline", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSink entity, DataSinkInputDTO input);
}
