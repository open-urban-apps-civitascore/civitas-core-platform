package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.input.PermissionInputDTO;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.model.output.summary.PermissionSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionMapper
    extends DtoMapper<PermissionInputDTO, PermissionOutputDTO, Permission> {

  @Override
  Permission toEntity(PermissionInputDTO input);

  @Override
  PermissionOutputDTO toOutput(Permission entity);

  @Override
  PermissionInputDTO toInput(Permission entity);

  PermissionSummaryDTO toSummary(Permission entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Override
  void updateEntity(@MappingTarget Permission entity, PermissionInputDTO input);
}
