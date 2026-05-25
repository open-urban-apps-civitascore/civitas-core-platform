package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.model.output.summary.PipelineSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link PipelineInputDTO}, {@link PipelineOutputDTO}, and
 * {@link Pipeline}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PipelineMapper extends DtoMapper<PipelineInputDTO, PipelineOutputDTO, Pipeline> {

  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "dataSources", ignore = true)
  @Override
  Pipeline toEntity(PipelineInputDTO input);

  @Mapping(target = "dataSinks", ignore = true)
  @Override
  PipelineOutputDTO toOutput(Pipeline entity);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Mapping(target = "dataSinks", ignore = true)
  @Override
  PipelineInputDTO toInput(Pipeline entity);

  PipelineSummaryDTO toSummary(Pipeline entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "dataSources", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Override
  void updateEntity(@MappingTarget Pipeline entity, PipelineInputDTO input);
}
