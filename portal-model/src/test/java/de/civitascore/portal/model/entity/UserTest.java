package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("User Entity Tests")
class UserTest {

  private User userWithId(UUID id) {
    User user = new User();
    user.setId(id);
    user.setFirstName("First-" + id.toString().substring(0, 8));
    user.setLastName("Last-" + id.toString().substring(0, 8));
    user.setEmail(id.toString().substring(0, 8) + "@test.com");
    return user;
  }

  private Group groupWithId(UUID id) {
    Group group = new Group();
    group.setId(id);
    group.setName("Group-" + id.toString().substring(0, 8));
    return group;
  }

  @Nested
  @DisplayName("addGroup()")
  class AddGroupTests {

    @Test
    @DisplayName("Should add group and update bidirectional relation")
    void shouldAddGroupAndUpdateBidirectionalRelation() {
      User user = userWithId(UUID.randomUUID());
      Group group = groupWithId(UUID.randomUUID());

      user.addGroup(group);

      assertThat(user.getGroups()).containsExactly(group);
      assertThat(group.getMembers()).containsExactly(user);
    }

    @Test
    @DisplayName("Should handle null group without NPE")
    void shouldHandleNullGroup() {
      User user = userWithId(UUID.randomUUID());

      user.addGroup(null);

      assertThat(user.getGroups()).isEmpty();
    }

    @Test
    @DisplayName("Should not add duplicate group")
    void shouldNotAddDuplicateGroup() {
      User user = userWithId(UUID.randomUUID());
      Group group = groupWithId(UUID.randomUUID());

      user.addGroup(group);
      user.addGroup(group);

      assertThat(user.getGroups()).hasSize(1);
      assertThat(group.getMembers()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("removeGroup()")
  class RemoveGroupTests {

    @Test
    @DisplayName("Should remove group and update bidirectional relation")
    void shouldRemoveGroupAndUpdateBidirectionalRelation() {
      User user = userWithId(UUID.randomUUID());
      Group group = groupWithId(UUID.randomUUID());
      user.addGroup(group);

      user.removeGroup(group);

      assertThat(user.getGroups()).isEmpty();
      assertThat(group.getMembers()).doesNotContain(user);
    }

    @Test
    @DisplayName("Should handle removing non-existent group without error")
    void shouldHandleRemovingNonExistentGroup() {
      User user = userWithId(UUID.randomUUID());
      Group group = groupWithId(UUID.randomUUID());

      user.removeGroup(group);

      assertThat(user.getGroups()).isEmpty();
    }
  }

  @Nested
  @DisplayName("setGroups()")
  class SetGroupsTests {

    @Test
    @DisplayName("Should replace all groups")
    void shouldReplaceAllGroups() {
      User user = userWithId(UUID.randomUUID());
      Group oldGroup = groupWithId(UUID.randomUUID());
      Group newGroup1 = groupWithId(UUID.randomUUID());
      Group newGroup2 = groupWithId(UUID.randomUUID());
      user.addGroup(oldGroup);

      user.setGroups(new HashSet<>(Set.of(newGroup1, newGroup2)));

      assertThat(user.getGroups()).containsExactlyInAnyOrder(newGroup1, newGroup2);
      assertThat(oldGroup.getMembers()).doesNotContain(user);
      assertThat(newGroup1.getMembers()).contains(user);
      assertThat(newGroup2.getMembers()).contains(user);
    }

    @Test
    @DisplayName("Should handle null input by clearing all groups")
    void shouldHandleNullInput() {
      User user = userWithId(UUID.randomUUID());
      Group group = groupWithId(UUID.randomUUID());
      user.addGroup(group);

      user.setGroups(null);

      assertThat(user.getGroups()).isEmpty();
      assertThat(group.getMembers()).doesNotContain(user);
    }
  }
}
