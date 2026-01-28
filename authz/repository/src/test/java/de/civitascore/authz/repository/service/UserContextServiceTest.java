package de.civitascore.authz.repository.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.authz.repository.data.UserRepository;
import de.civitascore.authz.repository.model.dto.UserContextResponse;
import de.civitascore.authz.repository.model.dto.UserContextResponse.AssignmentContext;
import de.civitascore.authz.repository.model.dto.UserContextResponse.GroupContext;
import de.civitascore.authz.repository.model.entity.Assignment;
import de.civitascore.authz.repository.model.entity.Group;
import de.civitascore.authz.repository.model.entity.Permission;
import de.civitascore.authz.repository.model.entity.Role;
import de.civitascore.authz.repository.model.entity.User;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserContextServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private UserContextService userContextService;

  private static final String EXTERNAL_ID = "test-external-id";
  private static final UUID USER_ID = UUID.randomUUID();

  @Nested
  @DisplayName("getUserContext")
  class GetUserContext {

    @Test
    @DisplayName("returns empty when user not found")
    void returnsEmptyWhenUserNotFound() {
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.empty());

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns user with basic info when found")
    void returnsUserWithBasicInfo() {
      User user = createUser();
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getUserId()).isEqualTo(USER_ID);
      assertThat(result.get().getExternalId()).isEqualTo(EXTERNAL_ID);
    }

    @Test
    @DisplayName("returns empty groups list when user has no groups")
    void returnsEmptyGroupsWhenNoGroups() {
      User user = createUser();
      user.setGroups(new HashSet<>());
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getGroups()).isEmpty();
    }

    @Test
    @DisplayName("maps group with no assignments to empty assignments list")
    void mapsGroupWithNoAssignments() {
      User user = createUser();
      Group group = createGroup("Empty Group");
      group.setAssignments(new HashSet<>());
      user.setGroups(Set.of(group));
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getGroups()).hasSize(1);
      GroupContext groupContext = result.get().getGroups().get(0);
      assertThat(groupContext.getName()).isEqualTo("Empty Group");
      assertThat(groupContext.getAssignments()).isEmpty();
    }

    @Test
    @DisplayName("maps role with no permissions to empty permissions list")
    void mapsRoleWithNoPermissions() {
      User user = createUser();
      Group group = createGroup("Test Group");
      Role role = createRole("Empty Role", "STANDARD");
      role.setPermissions(new HashSet<>());
      Assignment assignment = createAssignment(group, role, "TENANT", "tenant-1");
      group.setAssignments(Set.of(assignment));
      user.setGroups(Set.of(group));
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      AssignmentContext assignmentContext = result.get().getGroups().get(0).getAssignments().get(0);
      assertThat(assignmentContext.getRoleName()).isEqualTo("Empty Role");
      assertThat(assignmentContext.getPermissions()).isEmpty();
    }

    @Test
    @DisplayName("handles assignment with null scopeId")
    void handlesNullScopeId() {
      User user = createUser();
      Group group = createGroup("Test Group");
      Role role = createRole("Admin Role", "ADMIN");
      role.setPermissions(Set.of(createPermission("admin:all")));
      Assignment assignment = createAssignment(group, role, "GLOBAL", null);
      group.setAssignments(Set.of(assignment));
      user.setGroups(Set.of(group));
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      AssignmentContext assignmentContext = result.get().getGroups().get(0).getAssignments().get(0);
      assertThat(assignmentContext.getScopeType()).isEqualTo("GLOBAL");
      assertThat(assignmentContext.getScopeId()).isNull();
    }

    @Test
    @DisplayName("sorts permissions alphabetically")
    void sortsPermissionsAlphabetically() {
      User user = createUser();
      Group group = createGroup("Test Group");
      Role role = createRole("Multi-Permission Role", "STANDARD");
      role.setPermissions(
          Set.of(
              createPermission("zebra:read"),
              createPermission("alpha:write"),
              createPermission("beta:delete"),
              createPermission("gamma:create")));
      Assignment assignment = createAssignment(group, role, "TENANT", "tenant-1");
      group.setAssignments(Set.of(assignment));
      user.setGroups(Set.of(group));
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      AssignmentContext assignmentContext = result.get().getGroups().get(0).getAssignments().get(0);
      assertThat(assignmentContext.getPermissions())
          .containsExactly("alpha:write", "beta:delete", "gamma:create", "zebra:read");
    }

    @Test
    @DisplayName("maps all assignment fields correctly")
    void mapsAllAssignmentFields() {
      User user = createUser();
      Group group = createGroup("Test Group");
      UUID roleId = UUID.randomUUID();
      Role role = createRoleWithId(roleId, "DataEditor", "DATA");
      role.setPermissions(Set.of(createPermission("dataset:write")));
      Assignment assignment = createAssignment(group, role, "DATASPACE", "dataspace-123");
      group.setAssignments(Set.of(assignment));
      user.setGroups(Set.of(group));
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      AssignmentContext assignmentContext = result.get().getGroups().get(0).getAssignments().get(0);
      assertThat(assignmentContext.getRoleId()).isEqualTo(roleId);
      assertThat(assignmentContext.getRoleName()).isEqualTo("DataEditor");
      assertThat(assignmentContext.getRoleType()).isEqualTo("DATA");
      assertThat(assignmentContext.getScopeType()).isEqualTo("DATASPACE");
      assertThat(assignmentContext.getScopeId()).isEqualTo("dataspace-123");
      assertThat(assignmentContext.getPermissions()).containsExactly("dataset:write");
    }

    @Test
    @DisplayName("maps multiple groups correctly")
    void mapsMultipleGroups() {
      User user = createUser();
      Group group1 = createGroup("Group A");
      Group group2 = createGroup("Group B");
      group1.setAssignments(new HashSet<>());
      group2.setAssignments(new HashSet<>());
      user.setGroups(Set.of(group1, group2));
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getGroups()).hasSize(2);
      assertThat(result.get().getGroups())
          .extracting(GroupContext::getName)
          .containsExactlyInAnyOrder("Group A", "Group B");
    }

    @Test
    @DisplayName("maps multiple assignments per group")
    void mapsMultipleAssignmentsPerGroup() {
      User user = createUser();
      Group group = createGroup("Multi-Role Group");
      Role role1 = createRole("Reader", "STANDARD");
      Role role2 = createRole("Writer", "STANDARD");
      role1.setPermissions(Set.of(createPermission("data:read")));
      role2.setPermissions(Set.of(createPermission("data:write")));
      Assignment assignment1 = createAssignment(group, role1, "TENANT", "tenant-1");
      Assignment assignment2 = createAssignment(group, role2, "DATASPACE", "dataspace-1");
      group.setAssignments(Set.of(assignment1, assignment2));
      user.setGroups(Set.of(group));
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      GroupContext groupContext = result.get().getGroups().get(0);
      assertThat(groupContext.getAssignments()).hasSize(2);
      assertThat(groupContext.getAssignments())
          .extracting(AssignmentContext::getRoleName)
          .containsExactlyInAnyOrder("Reader", "Writer");
    }
  }

  // Helper methods for creating test entities

  private User createUser() {
    User user = new User();
    user.setId(USER_ID);
    user.setExternalId(EXTERNAL_ID);
    user.setGroups(new HashSet<>());
    return user;
  }

  private Group createGroup(String name) {
    Group group = new Group();
    group.setId(UUID.randomUUID());
    group.setName(name);
    group.setAssignments(new HashSet<>());
    return group;
  }

  private Role createRole(String name, String roleType) {
    return createRoleWithId(UUID.randomUUID(), name, roleType);
  }

  private Role createRoleWithId(UUID id, String name, String roleType) {
    Role role = new Role();
    role.setId(id);
    role.setName(name);
    role.setRoleType(roleType);
    role.setPermissions(new HashSet<>());
    return role;
  }

  private Permission createPermission(String name) {
    Permission permission = new Permission();
    permission.setId(UUID.randomUUID());
    permission.setName(name);
    return permission;
  }

  private Assignment createAssignment(Group group, Role role, String scopeType, String scopeId) {
    Assignment assignment = new Assignment();
    assignment.setId(UUID.randomUUID());
    assignment.setGroup(group);
    assignment.setRole(role);
    assignment.setScopeType(scopeType);
    assignment.setScopeId(scopeId);
    return assignment;
  }
}
