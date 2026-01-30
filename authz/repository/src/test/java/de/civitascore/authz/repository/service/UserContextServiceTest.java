package de.civitascore.authz.repository.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.authz.repository.data.UserRepository;
import de.civitascore.authz.repository.model.dto.UserContextResponse;
import de.civitascore.authz.repository.model.dto.UserContextResponse.AssignmentContext;
import de.civitascore.authz.repository.model.dto.UserContextResponse.GroupContext;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.User;
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
      // createUser already returns empty groups set
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
      // createGroup already returns empty assignments set
      when(user.getGroups()).thenReturn(Set.of(group));
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
      Role role = createRole("Empty Role", RoleType.SYSTEM);
      // createRole already returns empty permissions set
      Assignment assignment = createAssignment(group, role, ScopeType.TENANT, "tenant-1");
      when(group.getAssignments()).thenReturn(Set.of(assignment));
      when(user.getGroups()).thenReturn(Set.of(group));
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
      Role role = createRole("Admin Role", RoleType.SYSTEM);
      Permission adminPermission = createPermission("admin:all");
      when(role.getPermissions()).thenReturn(Set.of(adminPermission));
      Assignment assignment = createAssignment(group, role, ScopeType.TENANT, null);
      when(group.getAssignments()).thenReturn(Set.of(assignment));
      when(user.getGroups()).thenReturn(Set.of(group));
      when(userRepository.findByExternalIdWithContext(EXTERNAL_ID)).thenReturn(Optional.of(user));

      Optional<UserContextResponse> result = userContextService.getUserContext(EXTERNAL_ID);

      assertThat(result).isPresent();
      AssignmentContext assignmentContext = result.get().getGroups().get(0).getAssignments().get(0);
      assertThat(assignmentContext.getScopeType()).isEqualTo("TENANT");
      assertThat(assignmentContext.getScopeId()).isNull();
    }

    @Test
    @DisplayName("sorts permissions alphabetically")
    void sortsPermissionsAlphabetically() {
      User user = createUser();
      Group group = createGroup("Test Group");
      Role role = createRole("Multi-Permission Role", RoleType.SYSTEM);
      Permission zebraRead = createPermission("zebra:read");
      Permission alphaWrite = createPermission("alpha:write");
      Permission betaDelete = createPermission("beta:delete");
      Permission gammaCreate = createPermission("gamma:create");
      when(role.getPermissions())
          .thenReturn(Set.of(zebraRead, alphaWrite, betaDelete, gammaCreate));
      Assignment assignment = createAssignment(group, role, ScopeType.TENANT, "tenant-1");
      when(group.getAssignments()).thenReturn(Set.of(assignment));
      when(user.getGroups()).thenReturn(Set.of(group));
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
      Role role = createRoleWithId(roleId, "DataEditor", RoleType.DATA);
      Permission datasetWrite = createPermission("dataset:write");
      when(role.getPermissions()).thenReturn(Set.of(datasetWrite));
      Assignment assignment = createAssignment(group, role, ScopeType.DATASPACE, "dataspace-123");
      when(group.getAssignments()).thenReturn(Set.of(assignment));
      when(user.getGroups()).thenReturn(Set.of(group));
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
      // createGroup already returns empty assignments set
      when(user.getGroups()).thenReturn(Set.of(group1, group2));
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
      Role role1 = createRole("Reader", RoleType.SYSTEM);
      Role role2 = createRole("Writer", RoleType.DATA);
      Permission dataRead = createPermission("data:read");
      Permission dataWrite = createPermission("data:write");
      when(role1.getPermissions()).thenReturn(Set.of(dataRead));
      when(role2.getPermissions()).thenReturn(Set.of(dataWrite));
      Assignment assignment1 = createAssignment(group, role1, ScopeType.TENANT, "tenant-1");
      Assignment assignment2 = createAssignment(group, role2, ScopeType.DATASPACE, "dataspace-1");
      when(group.getAssignments()).thenReturn(Set.of(assignment1, assignment2));
      when(user.getGroups()).thenReturn(Set.of(group));
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

  // Helper methods for creating test entities using mocks
  // (portal-model entities have protected constructors)

  private User createUser() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(USER_ID);
    when(user.getExternalId()).thenReturn(EXTERNAL_ID);
    when(user.getGroups()).thenReturn(new HashSet<>());
    return user;
  }

  private Group createGroup(String name) {
    Group group = mock(Group.class);
    when(group.getId()).thenReturn(UUID.randomUUID());
    when(group.getName()).thenReturn(name);
    when(group.getAssignments()).thenReturn(new HashSet<>());
    return group;
  }

  private Role createRole(String name, RoleType roleType) {
    return createRoleWithId(UUID.randomUUID(), name, roleType);
  }

  private Role createRoleWithId(UUID id, String name, RoleType roleType) {
    Role role = mock(Role.class);
    when(role.getId()).thenReturn(id);
    when(role.getName()).thenReturn(name);
    when(role.getRoleType()).thenReturn(roleType);
    when(role.getPermissions()).thenReturn(new HashSet<>());
    return role;
  }

  private Permission createPermission(String name) {
    Permission permission = mock(Permission.class);
    // Only stub methods actually used by UserContextService
    when(permission.getName()).thenReturn(name);
    return permission;
  }

  private Assignment createAssignment(Group group, Role role, ScopeType scopeType, String scopeId) {
    Assignment assignment = mock(Assignment.class);
    // Only stub methods actually used by UserContextService
    when(assignment.getRole()).thenReturn(role);
    when(assignment.getScopeType()).thenReturn(scopeType);
    when(assignment.getScopeId()).thenReturn(scopeId);
    return assignment;
  }
}
