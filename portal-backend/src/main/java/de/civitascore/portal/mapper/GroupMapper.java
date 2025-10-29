package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.output.GroupOutputDTO;
import de.civitascore.portal.model.output.summary.GroupSummaryDTO;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface GroupMapper extends DtoMapper<GroupInputDTO, GroupOutputDTO, Group> {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "members", ignore = true) // Service resolves
  @Mapping(target = "systemRoles", ignore = true)
  @Mapping(target = "contactUser", ignore = true) // Service resolves from ID
  @Mapping(target = "parentGroup", ignore = true) // Service resolves
  @Mapping(target = "childGroups", ignore = true)
  @Override
  Group toEntity(GroupInputDTO input);

  @Mapping(target = "members", ignore = true) // Assembler enriches
  @Mapping(target = "systemRoles", ignore = true)
  @Mapping(target = "contactUser", ignore = true)
  @Mapping(target = "parentGroup", ignore = true)
  @Override
  GroupOutputDTO toOutput(Group entity);

  GroupSummaryDTO toSummary(Group entity);

  // Shared update for PUT/PATCH: ignores omitted fields for partials
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "members", ignore = true)
  @Mapping(target = "systemRoles", ignore = true)
  @Mapping(target = "contactUser", ignore = true) // Service re-resolves if needed
  @Mapping(target = "parentGroup", ignore = true)
  @Mapping(target = "childGroups", ignore = true)
  @BeanMapping(
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
      nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
  @Override
  void updateEntity(@MappingTarget Group entity, GroupInputDTO input);
}
