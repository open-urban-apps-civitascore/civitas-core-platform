package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.model.input.TenantInputDTO;
import de.civitascore.portal.model.output.TenantOutputDTO;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantMapper extends DtoMapper<TenantInputDTO, TenantOutputDTO, Tenant> {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Override
  Tenant toEntity(TenantInputDTO input);

  @Override
  TenantOutputDTO toOutput(Tenant entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Override
  void updateEntity(@MappingTarget Tenant entity, TenantInputDTO input);
}
