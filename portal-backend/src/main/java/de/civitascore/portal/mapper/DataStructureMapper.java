package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link DataStructureInputDTO}, {@link
 * DataStructureOutputDTO}, and {@link DataStructure}.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {DataStructureVersionMapper.class},
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataStructureMapper
    extends DtoMapper<DataStructureInputDTO, DataStructureOutputDTO, DataStructure> {

  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "dataStructureVersions", ignore = true)
  @Override
  DataStructure toEntity(DataStructureInputDTO input);

  @Mapping(target = "dataStructureVersions", ignore = true)
  @Override
  DataStructureOutputDTO toOutput(DataStructure entity);

  @Mapping(target = "assignments", ignore = true)
  @Override
  DataStructureInputDTO toInput(DataStructure entity);

  DataStructureSummaryDTO toSummary(DataStructure entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "dataStructureVersions", ignore = true)
  @Mapping(target = "dataStructureStatus", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataStructure entity, DataStructureInputDTO input);
}
