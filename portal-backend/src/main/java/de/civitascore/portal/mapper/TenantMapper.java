package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.model.input.TenantInputDTO;
import de.civitascore.portal.model.output.TenantOutputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface TenantMapper extends DtoMapper<TenantInputDTO, TenantOutputDTO, Tenant> {

  @Override
  Tenant toEntity(TenantInputDTO input);

  @Override
  TenantOutputDTO toOutput(Tenant entity);

  @Override
  TenantInputDTO toInput(Tenant entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Override
  void updateEntity(@MappingTarget Tenant entity, TenantInputDTO input);
}
