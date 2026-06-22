package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.input.DataPoolInputDTO;
import de.civitascore.portal.model.output.DataPoolOutputDTO;
import de.civitascore.portal.model.output.summary.DataPoolSummaryDTO;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link DataPoolInputDTO}, {@link DataPoolOutputDTO}, and
 * {@link DataPool}.
 */
@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class},
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataPoolMapper extends DtoMapper<DataPoolInputDTO, DataPoolOutputDTO, DataPool> {

  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "contactPerson", ignore = true)
  @Override
  DataPool toEntity(DataPoolInputDTO input);

  @Mapping(source = "contactPerson", target = "contactPerson")
  @Override
  DataPoolOutputDTO toOutput(DataPool entity);

  @Mapping(target = "assignments", ignore = true)
  @Mapping(source = "contactPerson.id", target = "contactPersonId")
  @Override
  DataPoolInputDTO toInput(DataPool entity);

  DataPoolSummaryDTO toSummary(DataPool entity);

  default List<DataPoolSummaryDTO> toDataPoolSummaries(List<Assignment> assignments) {
    return assignments.stream()
        .map(Assignment::getDataPool)
        .filter(Objects::nonNull)
        .distinct()
        .map(this::toSummary)
        .collect(Collectors.toList());
  }

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "contactPerson", ignore = true)
  @Override
  void updateEntity(@MappingTarget DataPool entity, DataPoolInputDTO input);
}
