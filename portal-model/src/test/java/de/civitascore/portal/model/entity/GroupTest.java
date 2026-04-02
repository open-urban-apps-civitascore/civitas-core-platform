package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Group Entity Tests")
class GroupTest {

  private Group groupWithId(UUID id) {
    Group group = new Group();
    group.setId(id);
    group.setName("Group-" + id.toString().substring(0, 8));
    group.setChildGroups(new HashSet<>());
    return group;
  }

  @Nested
  @DisplayName("getChildGroupsRecursive()")
  class GetChildGroupsRecursiveTests {

    @Test
    @DisplayName("Should return empty set when no children")
    void shouldReturnEmptyWhenNoChildren() {
      Group root = groupWithId(UUID.randomUUID());

      Set<Group> result = root.getChildGroupsRecursive();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return direct children")
    void shouldReturnDirectChildren() {
      Group root = groupWithId(UUID.randomUUID());
      Group child1 = groupWithId(UUID.randomUUID());
      Group child2 = groupWithId(UUID.randomUUID());
      root.setChildGroups(new HashSet<>(Set.of(child1, child2)));

      Set<Group> result = root.getChildGroupsRecursive();

      assertThat(result).containsExactlyInAnyOrder(child1, child2);
    }

    @Test
    @DisplayName("Should return transitive children")
    void shouldReturnTransitiveChildren() {
      Group root = groupWithId(UUID.randomUUID());
      Group child = groupWithId(UUID.randomUUID());
      Group grandchild = groupWithId(UUID.randomUUID());

      root.setChildGroups(new HashSet<>(Set.of(child)));
      child.setChildGroups(new HashSet<>(Set.of(grandchild)));

      Set<Group> result = root.getChildGroupsRecursive();

      assertThat(result).containsExactlyInAnyOrder(child, grandchild);
    }

    @Test
    @DisplayName("Should handle circular reference without StackOverflowError")
    void shouldHandleCircularReference() {
      Group groupA = groupWithId(UUID.randomUUID());
      Group groupB = groupWithId(UUID.randomUUID());

      // Create cycle: A → B → A
      groupA.setChildGroups(new HashSet<>(Set.of(groupB)));
      groupB.setChildGroups(new HashSet<>(Set.of(groupA)));

      Set<Group> result = groupA.getChildGroupsRecursive();

      assertThat(result).containsExactlyInAnyOrder(groupA, groupB);
    }

    @Test
    @DisplayName("Should handle self-reference without StackOverflowError")
    void shouldHandleSelfReference() {
      Group group = groupWithId(UUID.randomUUID());

      // Create self-cycle: A → A
      group.setChildGroups(new HashSet<>(Set.of(group)));

      Set<Group> result = group.getChildGroupsRecursive();

      assertThat(result).containsExactly(group);
    }

    @Test
    @DisplayName("Should handle deep three-node cycle")
    void shouldHandleDeepCycle() {
      Group a = groupWithId(UUID.randomUUID());
      Group b = groupWithId(UUID.randomUUID());
      Group c = groupWithId(UUID.randomUUID());

      // A → B → C → A
      a.setChildGroups(new HashSet<>(Set.of(b)));
      b.setChildGroups(new HashSet<>(Set.of(c)));
      c.setChildGroups(new HashSet<>(Set.of(a)));

      Set<Group> result = a.getChildGroupsRecursive();

      assertThat(result).containsExactlyInAnyOrder(a, b, c);
    }
  }
}
