package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "pipelines", ignore = true)
  @Override
  DataSink toEntity(DataSinkInputDTO input);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Mapping(target = "pipelineIds", source = "pipelines")
  @Mapping(target = "configuration", ignore = true)
  @Override
  DataSinkOutputDTO toOutput(DataSink entity);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Override
  DataSinkInputDTO toInput(DataSink entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "pipelines", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSink entity, DataSinkInputDTO input);

  default List<UUID> toPipelineIds(Set<Pipeline> pipelines) {
    return pipelines == null
        ? List.of()
        : pipelines.stream().map(Pipeline::getId).sorted().toList();
  }
}
