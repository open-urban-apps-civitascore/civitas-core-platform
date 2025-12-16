package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.model.output.summary.RoleSummaryDTO;
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
public interface RoleMapper extends DtoMapper<RoleInputDTO, RoleOutputDTO, Role> {

  @Mapping(target = "permissions", ignore = true)
  @Override
  Role toEntity(RoleInputDTO input);

  @Mapping(target = "permissions", ignore = true)
  @Mapping(target = "modifiedBy", ignore = true)
  @Override
  RoleOutputDTO toOutput(Role entity);

  @Mapping(target = "permissionIds", ignore = true)
  @Override
  RoleInputDTO toInput(Role entity);

  RoleSummaryDTO toSummary(Role entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "permissions", ignore = true)
  @Mapping(target = "isReadonly", ignore = true)
  @Override
  void updateEntity(@MappingTarget Role entity, RoleInputDTO input);
}
