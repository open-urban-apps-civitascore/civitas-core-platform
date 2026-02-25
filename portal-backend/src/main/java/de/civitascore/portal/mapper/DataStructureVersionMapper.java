package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataStructureVersionMapper
    extends DtoMapper<
        DataStructureVersionInputDTO, DataStructureVersionOutputDTO, DataStructureVersion> {

  @Mapping(target = "dataStructure", ignore = true)
  @Override
  DataStructureVersion toEntity(DataStructureVersionInputDTO input);

  @Override
  DataStructureVersionOutputDTO toOutput(DataStructureVersion entity);

  @Override
  DataStructureVersionInputDTO toInput(DataStructureVersion entity);

  DataStructureVersionSummaryDTO toSummary(DataStructureVersion entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "dataStructure", ignore = true)
  @Mapping(target = "dataStructureVersionStatus", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataStructureVersion entity, DataStructureVersionInputDTO input);
}
