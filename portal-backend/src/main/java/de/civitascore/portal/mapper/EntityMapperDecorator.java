package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.output.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Helper class to handle complex mappings and avoid circular dependencies. This decorator pattern
 * allows us to map nested entities without MapStruct circular references.
 */
@Component
@RequiredArgsConstructor
public class EntityMapperDecorator {

  private final UserMapper userMapper;
  private final GroupMapper groupMapper;
  private final RoleMapper roleMapper;
  private final PermissionMapper permissionMapper;

  /** Map User entity to UserOutputDTO with groups populated. */
  public UserOutputDTO toUserOutputWithGroups(User user) {
    if (user == null) {
      return null;
    }

    UserOutputDTO dto = userMapper.toOutput(user);

    if (user.getGroups() != null) {
      dto.setGroups(
          user.getGroups().stream().map(groupMapper::toSummary).collect(Collectors.toList()));
    }

    return dto;
  }

  /** Map Group entity to GroupOutputDTO with all relationships populated. */
  public GroupOutputDTO toGroupOutputWithRelations(Group group) {
    if (group == null) {
      return null;
    }

    GroupOutputDTO dto = groupMapper.toOutput(group);

    // Map members
    if (group.getMembers() != null) {
      dto.setMembers(
          group.getMembers().stream().map(userMapper::toSummary).collect(Collectors.toList()));
    }

    // Map system roles
    if (group.getSystemRoles() != null) {
      dto.setSystemRoles(
          group.getSystemRoles().stream().map(roleMapper::toSummary).collect(Collectors.toList()));
    }

    // Map contact user
    if (group.getContactUser() != null) {
      dto.setContactUser(userMapper.toSummary(group.getContactUser()));
    }

    // Map parent group
    if (group.getParentGroup() != null) {
      dto.setParentGroup(groupMapper.toSummary(group.getParentGroup()));
    }

    return dto;
  }

  /** Map Role entity to RoleOutputDTO with permissions populated. */
  public RoleOutputDTO toRoleOutputWithPermissions(Role role) {
    if (role == null) {
      return null;
    }

    RoleOutputDTO dto = roleMapper.toOutput(role);

    if (role.getPermissions() != null) {
      dto.setPermissions(
          role.getPermissions().stream()
              .map(permissionMapper::toSummary)
              .collect(Collectors.toList()));
    }

    return dto;
  }

  /** Map list of Users with groups. */
  public List<UserOutputDTO> toUserOutputListWithGroups(List<User> users) {
    if (users == null) {
      return Collections.emptyList();
    }

    return users.stream().map(this::toUserOutputWithGroups).collect(Collectors.toList());
  }

  /** Map list of Groups with relations. */
  public List<GroupOutputDTO> toGroupOutputListWithRelations(List<Group> groups) {
    if (groups == null) {
      return Collections.emptyList();
    }

    return groups.stream().map(this::toGroupOutputWithRelations).collect(Collectors.toList());
  }

  /** Map list of Roles with permissions. */
  public List<RoleOutputDTO> toRoleOutputListWithPermissions(List<Role> roles) {
    if (roles == null) {
      return Collections.emptyList();
    }

    return roles.stream().map(this::toRoleOutputWithPermissions).collect(Collectors.toList());
  }
}
