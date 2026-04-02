package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.model.output.summary.PermissionSummaryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting {@link Permission} entities to {@link PermissionOutputDTO} and
 * {@link PermissionSummaryDTO}. Permissions are read-only resources, so no input DTO mapping is
 * provided.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionMapper {

  PermissionOutputDTO toOutput(Permission entity);

  PermissionSummaryDTO toSummary(Permission entity);
}
