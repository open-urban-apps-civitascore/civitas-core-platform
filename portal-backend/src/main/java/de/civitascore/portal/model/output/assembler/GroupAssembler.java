package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.output.GroupOutputDTO;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupAssembler implements BaseAssembler<Group, GroupOutputDTO, String> {

  private final GroupMapper groupMapper;
  private final UserMapper userMapper;
  private final RoleMapper roleMapper;

  @Override
  public GroupOutputDTO mapToBaseDto(Group entity) {
    GroupOutputDTO output = groupMapper.toOutput(entity);

    // Map contactUser
    if (entity.getContactUser() != null) {
      output.setContactUser(userMapper.toSummary(entity.getContactUser()));
    }

    // Map parentGroup
    if (entity.getParentGroup() != null) {
      output.setParentGroup(groupMapper.toSummary(entity.getParentGroup()));
    }

    // Map members
    if (entity.getMembers() != null && !entity.getMembers().isEmpty()) {
      output.setMembers(
          entity.getMembers().stream().map(userMapper::toSummary).collect(Collectors.toList()));
    }

    // Map systemRoles
    if (entity.getSystemRoles() != null && !entity.getSystemRoles().isEmpty()) {
      output.setSystemRoles(
          entity.getSystemRoles().stream().map(roleMapper::toSummary).collect(Collectors.toList()));
    }

    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Group entity) {
    return (I) groupMapper.toInput(entity);
  }
}
