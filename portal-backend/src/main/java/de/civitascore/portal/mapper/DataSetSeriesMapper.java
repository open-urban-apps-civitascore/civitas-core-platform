package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataSetSeries;
import de.civitascore.portal.model.input.DataSetSeriesInputDTO;
import de.civitascore.portal.model.output.DataSetSeriesOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link DataSetSeriesInputDTO}, {@link
 * DataSetSeriesOutputDTO}, and {@link DataSetSeries}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataSetSeriesMapper
    extends DtoMapper<DataSetSeriesInputDTO, DataSetSeriesOutputDTO, DataSetSeries> {
  @Mapping(target = "dataSets", ignore = true)
  @Override
  DataSetSeries toEntity(DataSetSeriesInputDTO input);

  @Override
  DataSetSeriesOutputDTO toOutput(DataSetSeries entity);

  @Override
  DataSetSeriesInputDTO toInput(DataSetSeries entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSets", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataSetSeries entity, DataSetSeriesInputDTO input);
}
