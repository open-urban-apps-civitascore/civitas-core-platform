package de.civitascore.portal.model.entity.base;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import jakarta.persistence.MappedSuperclass;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Superclass for entities that own {@link Assignment}s with diff-based replacement semantics. */
@SuperBuilder
@NoArgsConstructor
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
   * Diff-based replace: keeps matching assignments (preserves audit fields), removes stale ones,
   * and adds new ones. Incoming assignments that resolve to the same logical key are collapsed to a
   * single entry. Stale assignments are also detached from both owning collections so that orphan
   * removal deletes them (see {@link #detachFromOwners}).
   */
  public void setAssignments(Collection<Assignment> assignments) {
    Set<Assignment> existing = getAssignments();

    Map<AssignmentKey, Assignment> desiredByKey = new HashMap<>();
    if (assignments != null) {
      Map<AssignmentKey, Assignment> existingByKey =
          existing.stream()
              .collect(Collectors.toMap(AssignmentKey::of, a -> a, (keep, duplicate) -> keep));

      for (Assignment a : assignments) {
        // linkAssignment sets the scope, which the key depends on, so it must run before keying.
        linkAssignment(a);
        AssignmentKey key = AssignmentKey.of(a);
        Assignment match = existingByKey.get(key);
        desiredByKey.putIfAbsent(key, match != null ? match : a);
      }
    }
    Set<Assignment> desired = new HashSet<>(desiredByKey.values());

    // Collected to a list first: detaching mutates the very collections being inspected — including
    // `existing` itself, since this entity is one of the assignment's owners.
    existing.stream()
        .filter(stale -> !desired.contains(stale))
        .toList()
        .forEach(this::detachFromOwners);
    existing.retainAll(desired);
    existing.addAll(desired);
  }

  /**
   * Detaches a removed assignment from both collections that own it: its {@code Group} and its
   * scope entity (e.g. a {@code DataSet}). Both map the relation with {@code orphanRemoval = true}
   * and the counterpart collection is frequently loaded in the same session, so an assignment
   * removed from only one side stays reachable through the other and is never orphan-removed.
   * Clearing it from both sides makes it a true orphan so Hibernate deletes it.
   */
  private void detachFromOwners(Assignment assignment) {
    if (assignment.getGroup() != null) {
      assignment.getGroup().getAssignments().remove(assignment);
    }
    if (assignment.getScope() instanceof AssignableEntity scope) {
      scope.getAssignments().remove(assignment);
    }
  }

  protected abstract void linkAssignment(Assignment assignment);
}
