package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.model.output.StyleOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link StyleInputDTO}, {@link StyleOutputDTO}, and {@link
 * Style}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StyleMapper extends DtoMapper<StyleInputDTO, StyleOutputDTO, Style> {

  @Mapping(target = "dataSet", ignore = true)
  @Override
  Style toEntity(StyleInputDTO input);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Mapping(target = "inUse", ignore = true)
  @Override
  StyleOutputDTO toOutput(Style entity);

  @Mapping(target = "dataSetId", source = "dataSet.id")
  @Override
  StyleInputDTO toInput(Style entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataSet", ignore = true)
  @Override
  void updateEntity(@MappingTarget Style entity, StyleInputDTO input);
}
