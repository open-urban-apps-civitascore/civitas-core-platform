package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.model.output.summary.PermissionSummaryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionMapper {

  PermissionOutputDTO toOutput(Permission entity);

  PermissionSummaryDTO toSummary(Permission entity);
}
