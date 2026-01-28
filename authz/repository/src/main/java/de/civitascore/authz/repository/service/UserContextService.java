package de.civitascore.authz.repository.service;

import de.civitascore.authz.repository.data.UserRepository;
import de.civitascore.authz.repository.model.dto.UserContextResponse;
import de.civitascore.authz.repository.model.dto.UserContextResponse.AssignmentContext;
import de.civitascore.authz.repository.model.dto.UserContextResponse.GroupContext;
import de.civitascore.authz.repository.model.entity.Assignment;
import de.civitascore.authz.repository.model.entity.Group;
import de.civitascore.authz.repository.model.entity.Permission;
import de.civitascore.authz.repository.model.entity.User;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserContextService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public Optional<UserContextResponse> getUserContext(String externalId) {
    log.debug("Fetching user context for externalId: {}", externalId);

    return userRepository.findByExternalIdWithContext(externalId).map(this::mapToResponse);
  }

  private UserContextResponse mapToResponse(User user) {
    List<GroupContext> groupContexts =
        user.getGroups().stream().map(this::mapGroupContext).toList();

    return UserContextResponse.builder()
        .userId(user.getId())
        .externalId(user.getExternalId())
        .groups(groupContexts)
        .build();
  }

  private GroupContext mapGroupContext(Group group) {
    List<AssignmentContext> assignmentContexts =
        group.getAssignments().stream().map(this::mapAssignmentContext).toList();

    return GroupContext.builder()
        .id(group.getId())
        .name(group.getName())
        .assignments(assignmentContexts)
        .build();
  }

  private AssignmentContext mapAssignmentContext(Assignment assignment) {
    List<String> permissionNames =
        assignment.getRole().getPermissions().stream().map(Permission::getName).sorted().toList();

    return AssignmentContext.builder()
        .roleId(assignment.getRole().getId())
        .roleName(assignment.getRole().getName())
        .roleType(assignment.getRole().getRoleType())
        .scopeType(assignment.getScopeType())
        .scopeId(assignment.getScopeId())
        .permissions(permissionNames)
        .build();
  }
}
