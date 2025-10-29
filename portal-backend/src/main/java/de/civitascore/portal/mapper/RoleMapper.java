package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.model.output.summary.RoleSummaryDTO;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RoleMapper extends DtoMapper<RoleInputDTO, RoleOutputDTO, Role> {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "permissions", ignore = true)
  @Override
  Role toEntity(RoleInputDTO input);

  @Mapping(target = "permissions", ignore = true)
  @Override
  RoleOutputDTO toOutput(Role entity);

  RoleSummaryDTO toSummary(Role entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "permissions", ignore = true)
  @Override
  void updateEntity(@MappingTarget Role entity, RoleInputDTO input);
}
