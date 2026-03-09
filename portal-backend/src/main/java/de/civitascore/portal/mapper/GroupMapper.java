package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.output.GroupOutputDTO;
import de.civitascore.portal.model.output.summary.GroupSummaryDTO;
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
public interface GroupMapper extends DtoMapper<GroupInputDTO, GroupOutputDTO, Group> {

  @Mapping(target = "members", ignore = true) // Service resolves
  @Mapping(target = "assignments", ignore = true) // Service resolves
  @Mapping(target = "contactUser", ignore = true) // Service resolves from ID
  @Mapping(target = "parentGroup", ignore = true) // Service resolves
  @Mapping(target = "childGroups", ignore = true)
  @Override
  Group toEntity(GroupInputDTO input);

  @Mapping(target = "members", ignore = true) // Assembler enriches
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "contactUser", ignore = true)
  @Mapping(target = "parentGroup", ignore = true)
  @Mapping(target = "childGroups", ignore = true)
  @Override
  GroupOutputDTO toOutput(Group entity);

  @Mapping(target = "memberIds", ignore = true)
  @Mapping(target = "contactUserId", source = "contactUser.id")
  @Mapping(target = "parentGroupId", source = "parentGroup.id")
  @Override
  GroupInputDTO toInput(Group entity);

  GroupSummaryDTO toSummary(Group entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "members", ignore = true)
  @Mapping(target = "assignments", ignore = true)
  @Mapping(target = "contactUser", ignore = true)
  @Mapping(target = "parentGroup", ignore = true)
  @Mapping(target = "childGroups", ignore = true)
  @Override
  void updateEntity(@MappingTarget Group entity, GroupInputDTO input);
}
