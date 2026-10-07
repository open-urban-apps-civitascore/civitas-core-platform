package de.civitascore.portal.security;

import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.AssignmentService;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Authorizes changes to the assignments a data entity's request body carries.
 *
 * <p>OPA never sees the body, so the backend checks each added or removed role: the caller must
 * hold every permission of the role that applies to the entity, or hold {@code ASSIGNMENT_CREATE}
 * (adding) or {@code ASSIGNMENT_DELETE} (removing) tenant-wide.
 *
 * <p>The caller's permissions on the entity come from tenant-wide assignments, assignments on the
 * entity and, for a data set, assignments on its data pool. Every decision is logged for audit.
 */
@Component
@Slf4j
public class AssignmentChangeAuthorizer {

  /** Permission prefixes that take effect per scope type; a data pool passes on data set ones. */
  private static final Map<ScopeType, List<String>> APPLICABLE_PREFIXES =
      Map.of(
          ScopeType.DATASET, List.of("DATASET_"),
          ScopeType.DATASOURCE, List.of("DATASOURCE_"),
          ScopeType.DATASTRUCTURE, List.of("DATASTRUCTURE_"),
          ScopeType.DATAPOOL, List.of("DATAPOOL_", "DATASET_"));

  private final AssignmentService assignmentService;

  public AssignmentChangeAuthorizer(AssignmentService assignmentService) {
    this.assignmentService = assignmentService;
  }

  /** Group and role of an assignment; the scope is always the entity being changed. */
  private record AssignmentKey(UUID groupId, UUID roleId) {
    static AssignmentKey of(Assignment assignment) {
      return new AssignmentKey(assignment.getGroup().getId(), assignment.getRole().getId());
    }
  }

  /** The entity whose assignments change, with the data pool a data set inherits from. */
  private record Target(ScopeType type, UUID id, UUID poolId) {
    static Target of(BaseDataEntity entity) {
      return switch (entity) {
        case DataSet dataSet ->
            new Target(
                ScopeType.DATASET,
                dataSet.getId(),
                dataSet.getDataPool() != null ? dataSet.getDataPool().getId() : null);
        case DataSource dataSource -> new Target(ScopeType.DATASOURCE, dataSource.getId(), null);
        case DataStructure dataStructure ->
            new Target(ScopeType.DATASTRUCTURE, dataStructure.getId(), null);
        case DataPool dataPool -> new Target(ScopeType.DATAPOOL, dataPool.getId(), null);
        default -> {
          // Fail closed: an unmapped entity type is a wiring error, but a security guard must
          // never grant on an unrecognized input.
          log.error("Assignment change denied: unsupported entity type {}", entity.getClass());
          throw new AccessDeniedException("Unsupported entity type for assignment changes");
        }
      };
    }

    boolean appliesTo(String permission) {
      return APPLICABLE_PREFIXES.get(type).stream().anyMatch(permission::startsWith);
    }
  }

  /** The caller, their assignments, and the permissions those give them on the target. */
  private record Caller(
      String externalId, List<Assignment> assignments, Set<String> permissionsOnTarget) {}

  /**
   * Authorizes replacing the entity's assignments with the desired ones. Must run before the
   * replacement is applied, while the entity still holds its current assignments.
   *
   * @param entity the data entity whose assignments are replaced
   * @param desired the assignments the entity holds afterwards
   * @throws AccessDeniedException if the caller is unauthenticated, the entity type is unsupported,
   *     or the caller may not add or remove one of the changed roles
   */
  public void authorizeReplacement(BaseDataEntity entity, Collection<Assignment> desired) {
    Map<AssignmentKey, Role> current = rolesByKey(entity.getAssignments());
    Map<AssignmentKey, Role> requested = rolesByKey(desired);
    Map<AssignmentKey, Role> added = missingFrom(requested, current);
    Map<AssignmentKey, Role> removed = missingFrom(current, requested);
    if (added.isEmpty() && removed.isEmpty()) {
      return;
    }

    String externalId = currentUserExternalId();
    Target target = Target.of(entity);
    List<Assignment> callerAssignments = assignmentService.findAllByUserExternalId(externalId);
    Caller caller =
        new Caller(externalId, callerAssignments, permissionsOn(target, callerAssignments));

    String addedBy = authorize(added, PermissionName.ASSIGNMENT_CREATE, "add", target, caller);
    String removedBy =
        authorize(removed, PermissionName.ASSIGNMENT_DELETE, "remove", target, caller);

    log.info(
        "Assignment change authorized for user {} on {}: added={} ({}) removed={} ({})",
        Encode.forJava(externalId),
        target,
        added.keySet(),
        addedBy,
        removed.keySet(),
        removedBy);
  }

  /**
   * Authorizes adding or removing the changed roles.
   *
   * @return what allowed the change, for the audit log
   * @throws AccessDeniedException if a role carries a permission the caller lacks on the target
   */
  private static String authorize(
      Map<AssignmentKey, Role> changed,
      PermissionName override,
      String action,
      Target target,
      Caller caller) {
    if (changed.isEmpty()) {
      return "no change";
    }
    if (holdsTenantWide(caller.assignments(), override)) {
      return "tenant-wide " + override;
    }
    for (Map.Entry<AssignmentKey, Role> entry : changed.entrySet()) {
      AssignmentKey key = entry.getKey();
      Set<String> missing =
          applicablePermissions(entry.getValue(), target).stream()
              .filter(permission -> !caller.permissionsOnTarget().contains(permission))
              .collect(Collectors.toSet());
      if (!missing.isEmpty()) {
        log.warn(
            "Assignment change denied for user {} on {}: may not {} role {} for group {},"
                + " lacking {}",
            Encode.forJava(caller.externalId()),
            target,
            action,
            key.roleId(),
            key.groupId(),
            missing);
        throw new AccessDeniedException(
            "Not authorized to " + action + " role " + key.roleId() + " on " + target.type());
      }
    }
    return "role ceiling";
  }

  private static Map<AssignmentKey, Role> rolesByKey(Collection<Assignment> assignments) {
    Map<AssignmentKey, Role> byKey = new HashMap<>();
    assignments.forEach(a -> byKey.putIfAbsent(AssignmentKey.of(a), a.getRole()));
    return byKey;
  }

  private static Map<AssignmentKey, Role> missingFrom(
      Map<AssignmentKey, Role> source, Map<AssignmentKey, Role> other) {
    Map<AssignmentKey, Role> missing = new HashMap<>(source);
    missing.keySet().removeAll(other.keySet());
    return missing;
  }

  private static Set<String> permissionsOn(Target target, List<Assignment> callerAssignments) {
    return callerAssignments.stream()
        .filter(a -> actsOn(a, target))
        .flatMap(a -> applicablePermissions(a.getRole(), target).stream())
        .collect(Collectors.toSet());
  }

  private static boolean actsOn(Assignment assignment, Target target) {
    if (isTenantWide(assignment)) {
      return true;
    }
    UUID scopeId = assignment.getScope() != null ? assignment.getScope().getId() : null;
    if (scopeId == null) {
      return false;
    }
    return (assignment.getScopeType() == target.type() && scopeId.equals(target.id()))
        || (assignment.getScopeType() == ScopeType.DATAPOOL && scopeId.equals(target.poolId()));
  }

  private static boolean holdsTenantWide(
      List<Assignment> callerAssignments, PermissionName permission) {
    return callerAssignments.stream()
        .filter(AssignmentChangeAuthorizer::isTenantWide)
        .anyMatch(a -> permissionNames(a.getRole()).contains(permission.name()));
  }

  private static boolean isTenantWide(Assignment assignment) {
    return assignment.getScopeType() == null || assignment.getScopeType() == ScopeType.TENANT;
  }

  private static Set<String> applicablePermissions(Role role, Target target) {
    return permissionNames(role).stream().filter(target::appliesTo).collect(Collectors.toSet());
  }

  private static Set<String> permissionNames(Role role) {
    if (role == null || role.getPermissions() == null) {
      return Set.of();
    }
    return role.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
  }

  private static String currentUserExternalId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof PrincipalUserDetails principal
        && principal.getUserId() != null) {
      return principal.getUserId().toString();
    }
    log.warn("Assignment change denied: no authenticated user principal");
    throw new AccessDeniedException("No authenticated user for assignment changes");
  }
}
