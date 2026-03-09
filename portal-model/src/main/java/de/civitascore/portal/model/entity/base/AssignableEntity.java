package de.civitascore.portal.model.entity.base;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import jakarta.persistence.MappedSuperclass;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Superclass for entities that own {@link Assignment}s with diff-based replacement semantics. */
@MappedSuperclass
public abstract class AssignableEntity extends NamedEntity {

  /** Logical identity of an assignment: group + role + scope type + scope entity ID. */
  private record AssignmentKey(UUID groupId, UUID roleId, ScopeType scopeType, UUID scopeId) {
    public static AssignmentKey of(Assignment a) {
      NamedEntity scope = a.getScope();
      return new AssignmentKey(
          a.getGroup().getId(),
          a.getRole().getId(),
          a.getScopeType(),
          scope != null ? scope.getId() : null);
    }
  }

  public abstract Set<Assignment> getAssignments();

  /**
   * Diff-based replace: keeps matching assignments (preserves audit fields), removes stale, adds
   * new.
   */
  public void setAssignments(Collection<Assignment> assignments) {
    Set<Assignment> existing = getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      existing.clear();
      return;
    }

    Map<AssignmentKey, Assignment> existingByKey =
        existing.stream().collect(Collectors.toMap(AssignmentKey::of, a -> a));

    Set<Assignment> desired = new HashSet<>();
    for (Assignment a : assignments) {
      linkAssignment(a);
      AssignmentKey key = AssignmentKey.of(a);
      Assignment match = existingByKey.get(key);
      desired.add(match != null ? match : a);
    }

    existing.retainAll(desired);
    existing.addAll(desired);
  }

  protected abstract void linkAssignment(Assignment assignment);
}
