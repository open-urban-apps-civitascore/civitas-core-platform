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
  }
}
