package de.civitascore.portal.model.entity.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AssignableEntity Tests")
class AssignableEntityTest {

  private Assignment createAssignment(Group group, Role role, DataSet scope) {
    Assignment assignment =
        Assignment.builder()
            .id(UUID.randomUUID())
            .group(group)
            .role(role)
            .scopeType(ScopeType.DATASET)
            .dataset(scope)
            .build();
    return assignment;
  }

  private Group groupWithId() {
    return Group.builder().id(UUID.randomUUID()).name("TestGroup").build();
  }

  private Role roleWithId() {
    return Role.builder().id(UUID.randomUUID()).name("TestRole").roleType(RoleType.SYSTEM).build();
  }

  private DataSet dataSetEntity() {
    DataSet ds = DataSet.builder().id(UUID.randomUUID()).name("TestDataSet").build();
    ds.setAssignments(new HashSet<>());
    return ds;
  }

  @Nested
  @DisplayName("setAssignments()")
  class SetAssignmentsTests {

    @Test
    @DisplayName("Should set assignments without error")
    void shouldSetAssignments() {
      DataSet dataSet = dataSetEntity();
      Group group = groupWithId();
      Role role = roleWithId();
      Assignment assignment = createAssignment(group, role, dataSet);

      dataSet.setAssignments(Set.of(assignment));

      assertThat(dataSet.getAssignments()).hasSize(1);
    }

    @Test
    @DisplayName("Should handle duplicate keys gracefully")
    void shouldHandleDuplicateKeysGracefully() {
      DataSet dataSet = dataSetEntity();
      Group group = groupWithId();
      Role role = roleWithId();

      // Two assignments with same group+role+scope = same key
      Assignment a1 = createAssignment(group, role, dataSet);
      Assignment a2 = createAssignment(group, role, dataSet);

      // Should not throw IllegalStateException
      assertThatNoException().isThrownBy(() -> dataSet.setAssignments(Set.of(a1, a2)));
    }

    @Test
    @DisplayName("Should replace existing assignments")
    void shouldReplaceExistingAssignments() {
      DataSet dataSet = dataSetEntity();
      Group group1 = groupWithId();
      Group group2 = groupWithId();
      Role role = roleWithId();

      Assignment old = createAssignment(group1, role, dataSet);
      dataSet.setAssignments(Set.of(old));
      assertThat(dataSet.getAssignments()).hasSize(1);

      Assignment replacement = createAssignment(group2, role, dataSet);
      dataSet.setAssignments(Set.of(replacement));
      assertThat(dataSet.getAssignments()).hasSize(1);
    }

    @Test
    @DisplayName("Should detach a removed assignment from its group's collection (work item 1597)")
    void shouldDetachStaleAssignmentFromGroup() {
      DataSet dataSet = dataSetEntity();
      Group group = groupWithId();
      Role roleA = roleWithId();
      Role roleB = roleWithId();

      // Assignment is co-owned by the dataset and the group (bidirectional, both
      // orphanRemoval=true).
      Assignment a = createAssignment(group, roleA, dataSet);
      group.getAssignments().add(a);
      dataSet.setAssignments(Set.of(a));
      assertThat(group.getAssignments()).contains(a);

      // Replacing role A with role B must also remove the stale assignment from the group,
      // otherwise
      // it stays reachable via Group#assignments and is never orphan-removed.
      Assignment b = createAssignment(group, roleB, dataSet);
      dataSet.setAssignments(Set.of(b));

      assertThat(dataSet.getAssignments()).hasSize(1);
      assertThat(group.getAssignments()).doesNotContain(a);
    }

    @Test
    @DisplayName("Replacing a group's assignments detaches stale ones from the scope entity")
    void shouldDetachStaleAssignmentFromScopeWhenGroupOwns() {
      Group group = groupWithId();
      DataSet dataSet = dataSetEntity();
      Role roleA = roleWithId();
      Role roleB = roleWithId();

      // Same assignment is held by both owning collections.
      Assignment a = createAssignment(group, roleA, dataSet);
      group.getAssignments().add(a);
      dataSet.getAssignments().add(a);

      // Replace on the group side: must not throw (the group's own collection is mutated while
      // being
      // inspected) and must detach the stale assignment from the dataset so it can be
      // orphan-removed.
      Assignment b = createAssignment(group, roleB, dataSet);
      group.setAssignments(Set.of(b));

      assertThat(group.getAssignments()).extracting(Assignment::getRole).containsExactly(roleB);
      assertThat(dataSet.getAssignments()).doesNotContain(a);
    }
  }
}
