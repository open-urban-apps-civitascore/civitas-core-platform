package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupService Tests")
class GroupServiceTest {

  @Mock private GroupRepository groupRepository;
  @Mock private GroupMapper groupMapper;
  @Mock private UserService userService;
  @Mock private AssignmentBuilderService assignmentBuilderService;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private GroupService groupService;

  private Role createRole(UUID id, RoleType roleType) {
    Role role = new Role();
    role.setId(id);
    role.setName("Test Role");
    role.setRoleType(roleType);
    return role;
  }

  private Assignment createExistingAssignment(Role role, ScopeType scopeType) {
    Assignment a = new Assignment();
    a.setId(UUID.randomUUID());
    a.setRole(role);
    a.setScopeType(scopeType);
    return a;
  }

  @Nested
  @DisplayName("replaceAssignments")
  class ReplaceAssignmentsTests {

    @Test
    @DisplayName("Should add new assignments for a group")
    void shouldAddNewAssignmentsForGroup() {
      UUID groupId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.DATA);

      Group group = new Group();
      group.setId(groupId);
      group.setName("Test Group");
      group.setAssignments(new HashSet<>());

      Assignment newAssignment = createExistingAssignment(role, ScopeType.TENANT);

      AssignmentGroupInputDTO input = new AssignmentGroupInputDTO();
      input.setRoleId(roleId);
      input.setScopeType(ScopeType.TENANT);

      when(groupRepository.findByIdWithRelations(groupId)).thenReturn(Optional.of(group));
      when(assignmentBuilderService.build(input)).thenReturn(newAssignment);
      when(groupRepository.save(group)).thenReturn(group);

      Group result = groupService.replaceAssignments(groupId, List.of(input));

      assertThat(result).isEqualTo(group);
      assertThat(result.getAssignments()).containsExactly(newAssignment);
      verify(groupRepository).save(group);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when group not found")
    void shouldThrowWhenGroupNotFound() {
      UUID groupId = UUID.randomUUID();
      when(groupRepository.findByIdWithRelations(groupId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> groupService.replaceAssignments(groupId, List.of()))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle empty assignment list by clearing all assignments")
    void shouldHandleEmptyAssignmentList() {
      UUID groupId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.DATA);

      Group group = new Group();
      group.setId(groupId);
      group.setName("Test Group");
      group.setAssignments(
          new HashSet<>(List.of(createExistingAssignment(role, ScopeType.TENANT))));

      when(groupRepository.findByIdWithRelations(groupId)).thenReturn(Optional.of(group));
      when(groupRepository.save(group)).thenReturn(group);

      Group result = groupService.replaceAssignments(groupId, List.of());

      assertThat(result.getAssignments()).isEmpty();
      verify(groupRepository).save(any(Group.class));
    }
  }
}
