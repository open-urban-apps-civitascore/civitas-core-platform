package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.input.CatalogInputDTO;
import de.civitascore.portal.model.output.CatalogOutputDTO;
import de.civitascore.portal.model.output.summary.CatalogSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link CatalogInputDTO}, {@link CatalogOutputDTO}, and
 * {@link Catalog}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CatalogMapper extends DtoMapper<CatalogInputDTO, CatalogOutputDTO, Catalog> {
  @Mapping(target = "childCatalogs", ignore = true)
  @Mapping(target = "parentCatalogs", ignore = true)
  @Mapping(target = "dataSets", ignore = true)
  @Override
  Catalog toEntity(CatalogInputDTO input);

  @Override
  CatalogOutputDTO toOutput(Catalog entity);

  @Mapping(target = "childCatalogIds", ignore = true)
  @Mapping(target = "dataSetIds", ignore = true)
  @Override
  CatalogInputDTO toInput(Catalog entity);

  CatalogSummaryDTO toSummary(Catalog entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "childCatalogs", ignore = true)
  @Mapping(target = "parentCatalogs", ignore = true)
  @Mapping(target = "dataSets", ignore = true)
  @Override
  void updateEntity(@MappingTarget Catalog entity, CatalogInputDTO input);
}
