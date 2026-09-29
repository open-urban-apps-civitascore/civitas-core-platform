package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.output.LayerOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link LayerInputDTO}, {@link LayerOutputDTO}, and {@link
 * Layer}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LayerMapper extends DtoMapper<LayerInputDTO, LayerOutputDTO, Layer> {

  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "dataSink", ignore = true)
  @Mapping(target = "defaultStyle", ignore = true)
  @Mapping(target = "alternativeStyles", ignore = true)
  @Override
  Layer toEntity(LayerInputDTO input);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Mapping(target = "dataSinkId", source = "dataSink.id")
  @Mapping(target = "defaultStyleId", source = "defaultStyle.id")
  @Mapping(target = "alternativeStyleIds", ignore = true)
  @Override
  LayerOutputDTO toOutput(Layer entity);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Mapping(target = "dataSinkId", source = "dataSink.id")
  @Mapping(target = "defaultStyleId", source = "defaultStyle.id")
  @Mapping(target = "alternativeStyleIds", ignore = true)
  @Override
  LayerInputDTO toInput(Layer entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSet", ignore = true)
  @Mapping(target = "dataSink", ignore = true)
  @Mapping(target = "defaultStyle", ignore = true)
  @Mapping(target = "alternativeStyles", ignore = true)
  @Override
  void updateEntity(@MappingTarget Layer entity, LayerInputDTO input);
}
