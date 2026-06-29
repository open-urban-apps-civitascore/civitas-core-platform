package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataPoolMapper;
import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.service.AssignmentService;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Role} entities to {@link RoleOutputDTO}. Participates in the
 * template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class RoleAssembler implements BaseAssembler<Role, RoleOutputDTO, UUID> {

  private final RoleMapper roleMapper;
  private final PermissionMapper permissionMapper;
  private final DataPoolMapper dataPoolMapper;
  private final AssignmentService assignmentService;

  /** {@inheritDoc} Maps role fields including permission summaries. */
  @Override
  public RoleOutputDTO mapToBaseDto(Role entity) {
    RoleOutputDTO output = roleMapper.toOutput(entity);

    if (entity.getPermissions() != null && !entity.getPermissions().isEmpty()) {
      output.setPermissions(
          entity.getPermissions().stream()
              .map(permissionMapper::toSummary)
              .collect(Collectors.toList()));
    }

    output.setDatapools(
        dataPoolMapper.toDataPoolSummaries(
            assignmentService.findAllByRoleIdAndScopeType(entity.getId(), ScopeType.DATAPOOL)));

    return output;
  }

  /** {@inheritDoc} Converts a role entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Role entity) {
    return (I) roleMapper.toInput(entity);
  }

  /**
   * {@inheritDoc} Computes and sets group and user counts by traversing assignments and group
   * hierarchies.
   */
  @Override
  public RoleOutputDTO enrichDto(RoleOutputDTO dto, Role entity) {
    dto.setGroupCount(0L);
    dto.setUserCount(0L);

    Set<Group> groups =
        assignmentService.findAllByRoleId(entity.getId()).stream()
            .map(Assignment::getGroup)
            .collect(Collectors.toSet());
    groups.addAll(
        groups.stream()
            .map(Group::getChildGroupsRecursive)
            .flatMap(Set::stream)
            .collect(Collectors.toSet()));

    Set<User> members =
        groups.stream().flatMap(group -> group.getMembers().stream()).collect(Collectors.toSet());

    Long totalGroups = (long) groups.size();
    Long totalMembers = (long) members.size();

    if (totalGroups != null) {
      dto.setGroupCount(totalGroups);
    }
    if (totalMembers != null) {
      dto.setUserCount(totalMembers);
    }

    return dto;
  }
}
