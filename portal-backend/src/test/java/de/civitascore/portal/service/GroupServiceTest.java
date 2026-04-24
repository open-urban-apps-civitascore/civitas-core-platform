package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupService Tests")
class GroupServiceTest {

  @Mock private GroupRepository groupRepository;
  @Mock private GroupMapper groupMapper;
  @Mock private UserService userService;
  @Mock private AssignmentFactory assignmentFactory;
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

      when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
      when(assignmentFactory.build(input)).thenReturn(newAssignment);
      when(groupRepository.save(group)).thenReturn(group);

      Group result = groupService.replaceAssignments(groupId, Set.of(input));

      assertThat(result).isEqualTo(group);
      assertThat(result.getAssignments()).containsExactly(newAssignment);
      verify(groupRepository).save(group);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when group not found")
    void shouldThrowWhenGroupNotFound() {
      UUID groupId = UUID.randomUUID();
      when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> groupService.replaceAssignments(groupId, Set.of()))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should silently discard duplicate assignments in input")
    void shouldDiscardDuplicateAssignments() {
      UUID groupId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.DATA);

      Group group = new Group();
      group.setId(groupId);
      group.setName("Test Group");
      group.setAssignments(new HashSet<>());

      Assignment newAssignment = createExistingAssignment(role, ScopeType.TENANT);

      AssignmentGroupInputDTO input1 = new AssignmentGroupInputDTO();
      input1.setRoleId(roleId);
      input1.setScopeType(ScopeType.TENANT);

      when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
      when(assignmentFactory.build(input1)).thenReturn(newAssignment);
      when(groupRepository.save(group)).thenReturn(group);

      // input1 appears twice but Set deduplicates, so only one assignment is created
      Set<AssignmentGroupInputDTO> inputs = new HashSet<>(List.of(input1, input1));
      Group result = groupService.replaceAssignments(groupId, inputs);

      assertThat(result.getAssignments()).hasSize(1);
      verify(groupRepository).save(group);
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

      when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
      when(groupRepository.save(group)).thenReturn(group);

      Group result = groupService.replaceAssignments(groupId, Set.of());

      assertThat(result.getAssignments()).isEmpty();
      verify(groupRepository).save(any(Group.class));
    }
  }
}
