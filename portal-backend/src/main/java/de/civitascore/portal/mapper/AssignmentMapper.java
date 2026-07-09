package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.assignment.AssignmentInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between {@link AssignmentInputDTO}, {@link AssignmentOutputDTO},
 * and {@link Assignment}.
 *
 * <p>Builder is disabled because {@link Assignment#getScope()} is a derived getter (no backing
 * field), which confuses MapStruct's builder detection when {@code @SuperBuilder} is present.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    builder = @Builder(disableBuilder = true))
public interface AssignmentMapper
    extends DtoMapper<AssignmentInputDTO, AssignmentOutputDTO, Assignment> {

  @Mapping(target = "group", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "scope", ignore = true)
  @Override
  Assignment toEntity(AssignmentInputDTO input);

  @Mapping(target = "group", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "scope", ignore = true)
  @Override
  AssignmentOutputDTO toOutput(Assignment entity);

  @Mapping(target = "groupId", source = "group.id")
  @Mapping(target = "roleId", source = "role.id")
  @Mapping(target = "scopeId", source = "scope.id")
  @Override
  AssignmentInputDTO toInput(Assignment entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "group", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "scope", ignore = true)
  @Override
  void updateEntity(@MappingTarget Assignment entity, AssignmentInputDTO input);
}
