package de.civitascore.authz.repository.service;

import de.civitascore.authz.repository.data.UserRepository;
import de.civitascore.authz.repository.model.dto.UserContextResponse;
import de.civitascore.authz.repository.model.dto.UserContextResponse.AssignmentContext;
import de.civitascore.authz.repository.model.dto.UserContextResponse.GroupContext;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.User;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for building user authorization context from the portal database.
 *
 * <p>Maps the JPA entity graph (User → Groups → Assignments → Roles → Permissions) into a flat DTO
 * suitable for OPA policy evaluation via the AuthZ Repository REST API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserContextService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public Optional<UserContextResponse> getUserContext(String externalId) {
    log.debug("Fetching user context for externalId: {}", maskUuid(Encode.forJava(externalId)));

    return userRepository.findByExternalIdWithContext(externalId).map(this::mapToResponse);
  }

  private static String maskUuid(String uuid) {
    if (uuid == null || uuid.length() < 8) {
      return "***";
    }
    return uuid.substring(0, 4) + "****" + uuid.substring(uuid.length() - 4);
  }

  private UserContextResponse mapToResponse(User user) {
    List<GroupContext> groupContexts =
        user.getGroups() == null
            ? List.of()
            : user.getGroups().stream().map(this::mapGroupContext).toList();

    return UserContextResponse.builder()
        .userId(user.getId())
        .externalId(user.getExternalId())
        .groups(groupContexts)
        .build();
  }

  private GroupContext mapGroupContext(Group group) {
    List<AssignmentContext> assignmentContexts =
        group.getAssignments() == null
            ? List.of()
            : group.getAssignments().stream().map(this::mapAssignmentContext).toList();

    return GroupContext.builder()
        .id(group.getId())
        .name(group.getName())
        .assignments(assignmentContexts)
        .build();
  }

  private AssignmentContext mapAssignmentContext(Assignment assignment) {
    Role role = assignment.getRole();
    List<String> permissionNames =
        role == null || role.getPermissions() == null
            ? List.of()
            : role.getPermissions().stream().map(Permission::getName).sorted().toList();

    return AssignmentContext.builder()
        .roleId(role != null ? role.getId() : null)
        .roleName(role != null ? role.getName() : null)
        .roleType(role != null && role.getRoleType() != null ? role.getRoleType().name() : null)
        .scopeType(assignment.getScopeType() != null ? assignment.getScopeType().name() : null)
        .scopeId(assignment.getScopeId() != null ? assignment.getScopeId().toString() : null)
        .permissions(permissionNames)
        .build();
  }
}
