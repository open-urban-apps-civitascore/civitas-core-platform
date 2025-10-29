package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.input.PermissionInputDTO;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.model.output.summary.PermissionSummaryDTO;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PermissionMapper
    extends DtoMapper<PermissionInputDTO, PermissionOutputDTO, Permission> {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Override
  Permission toEntity(PermissionInputDTO input);

  @Override
  PermissionOutputDTO toOutput(Permission entity);

  PermissionSummaryDTO toSummary(Permission entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Override
  void updateEntity(@MappingTarget Permission entity, PermissionInputDTO input);
}
