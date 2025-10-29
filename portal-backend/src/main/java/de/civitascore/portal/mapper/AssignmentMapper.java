package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AssignmentMapper
    extends DtoMapper<AssignmentInputDTO, AssignmentOutputDTO, Assignment> {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "group", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "assignmentType", ignore = true)
  @Mapping(target = "parentAssignment", ignore = true)
  @Override
  Assignment toEntity(AssignmentInputDTO input);

  @Mapping(target = "group", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "parentAssignment", ignore = true)
  @Override
  AssignmentOutputDTO toOutput(Assignment entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "group", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "assignmentType", ignore = true)
  @Mapping(target = "parentAssignment", ignore = true)
  @Override
  void updateEntity(@MappingTarget Assignment entity, AssignmentInputDTO input);
}
