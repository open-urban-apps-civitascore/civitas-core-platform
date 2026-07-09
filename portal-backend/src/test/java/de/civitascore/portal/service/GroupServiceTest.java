package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.configuration.KeycloakProperties;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.util.ResourceInUseException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupService Tests")
class GroupServiceTest {

  @Mock private GroupRepository groupRepository;
  @Mock private GroupMapper groupMapper;
  @Mock private UserService userService;
  @Mock private AssignmentFactory assignmentFactory;
  @Mock private ConfigEventPublisherService configEventPublisher;
  @Mock private EventProperties eventProperties;

  private static final String TARGET_REALM = "test-realm";
  private static final String AUTH_SERVER_URL = "http://keycloak:8080";
  private static final KeycloakProperties KEYCLOAK_PROPERTIES =
      new KeycloakProperties(TARGET_REALM, AUTH_SERVER_URL, TARGET_REALM);

  private GroupService createService() {
    return new GroupService(
        configEventPublisher,
        groupRepository,
        groupMapper,
        userService,
        assignmentFactory,
        KEYCLOAK_PROPERTIES,
        eventProperties);
  }

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
      GroupService groupService = createService();
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
      GroupService groupService = createService();
      UUID groupId = UUID.randomUUID();
      when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> groupService.replaceAssignments(groupId, Set.of()))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should silently discard duplicate assignments in input")
    void shouldDiscardDuplicateAssignments() {
      GroupService groupService = createService();
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
      GroupService groupService = createService();
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

  @Nested
  @DisplayName("deleteById()")
  class DeleteByIdTests {

    @Test
    @DisplayName("Should throw ResourceInUseException when group has child groups")
    void shouldThrowWhenGroupHasChildGroups() {
      GroupService service = createService();
      UUID groupId = UUID.randomUUID();

      Group group = new Group();
      group.setId(groupId);
      group.setName("Parent Group");

      Group child = new Group();
      child.setId(UUID.randomUUID());
      child.setName("Child Group");
      child.setParentGroup(group);
      group.setChildGroups(Set.of(child));

      when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

      assertThatThrownBy(() -> service.deleteById(groupId))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("child groups");
    }
  }

  // ---------------------------------------------------------------------------
  // toConfigValuePostSave
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("toConfigValuePostSave()")
  class ToConfigValuePostSaveTests {

    @Test
    @DisplayName("Should build GroupConfig with name (path is left to Keycloak)")
    void shouldBuildGroupConfigWithName() {
      GroupService service = createService();
      Group group = new Group();
      group.setId(UUID.randomUUID());
      group.setName("Editors");

      GroupConfig config =
          (GroupConfig) service.toConfigValuePostSave(group, new GroupInputDTO(), null);

      assertThat(config.getName()).isEqualTo("Editors");
      assertThat(config.getPath()).isNull();
    }

    @Test
    @DisplayName("Should set externalId as Keycloak id when present")
    void shouldSetKeycloakIdFromExternalId() {
      GroupService service = createService();
      Group group = new Group();
      group.setId(UUID.randomUUID());
      group.setName("Editors");
      group.setExternalId("kc-group-uuid-123");

      GroupConfig config =
          (GroupConfig) service.toConfigValuePostSave(group, new GroupInputDTO(), null);

      assertThat(config.getId()).isEqualTo("kc-group-uuid-123");
    }

    @Test
    @DisplayName("Should not set id when externalId is null")
    void shouldNotSetIdWhenExternalIdNull() {
      GroupService service = createService();
      Group group = new Group();
      group.setId(UUID.randomUUID());
      group.setName("Editors");
      group.setExternalId(null);

      GroupConfig config =
          (GroupConfig) service.toConfigValuePostSave(group, new GroupInputDTO(), null);

      assertThat(config.getId()).isNull();
    }

    @Test
    @DisplayName("Should set parentId from parent group externalId")
    void shouldSetParentIdFromParentExternalId() {
      GroupService service = createService();
      Group parent = new Group();
      parent.setId(UUID.randomUUID());
      parent.setExternalId("kc-parent-uuid");

      Group group = new Group();
      group.setId(UUID.randomUUID());
      group.setName("Sub Editors");
      group.setParentGroup(parent);

      GroupConfig config =
          (GroupConfig) service.toConfigValuePostSave(group, new GroupInputDTO(), null);

      assertThat(config.getParentId()).isEqualTo("kc-parent-uuid");
    }

    @Test
    @DisplayName("Should not set parentId when no parent group")
    void shouldNotSetParentIdWhenNoParent() {
      GroupService service = createService();
      Group group = new Group();
      group.setId(UUID.randomUUID());
      group.setName("Top Level");

      GroupConfig config =
          (GroupConfig) service.toConfigValuePostSave(group, new GroupInputDTO(), null);

      assertThat(config.getParentId()).isNull();
    }
  }

  // ---------------------------------------------------------------------------
  // updateExternalId
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("updateExternalId()")
  class UpdateExternalIdTests {

    @Test
    @DisplayName("Should set externalId when value is valid")
    void shouldSetExternalIdWhenValid() {
      GroupService service = createService();
      Group group = new Group();

      service.updateExternalId(group, "kc-group-id-456");

      assertThat(group.getExternalId()).isEqualTo("kc-group-id-456");
    }

    @Test
    @DisplayName("Should not set externalId when null")
    void shouldSkipWhenNull() {
      GroupService service = createService();
      Group group = new Group();
      group.setExternalId("original");

      service.updateExternalId(group, null);

      assertThat(group.getExternalId()).isEqualTo("original");
    }

    @Test
    @DisplayName("Should not set externalId when blank")
    void shouldSkipWhenBlank() {
      GroupService service = createService();
      Group group = new Group();
      group.setExternalId("original");

      service.updateExternalId(group, "   ");

      assertThat(group.getExternalId()).isEqualTo("original");
    }
  }

  // ---------------------------------------------------------------------------
  // resolveTopic
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("resolveTopic()")
  class ResolveTopicTests {

    @Test
    @DisplayName("Should resolve create topic")
    void shouldResolveCreateTopic() {
      assertThat(createService().resolveTopic("create")).isEqualTo(Topics.GROUP_CREATED);
    }

    @Test
    @DisplayName("Should resolve update topic")
    void shouldResolveUpdateTopic() {
      assertThat(createService().resolveTopic("update")).isEqualTo(Topics.GROUP_UPDATED);
    }

    @Test
    @DisplayName("Should resolve delete topic")
    void shouldResolveDeleteTopic() {
      assertThat(createService().resolveTopic("delete")).isEqualTo(Topics.GROUP_DELETED);
    }

    @Test
    @DisplayName("Should throw on unknown operation")
    void shouldThrowOnUnknownOperation() {
      assertThatThrownBy(() -> createService().resolveTopic("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown operation");
    }
  }

  // ---------------------------------------------------------------------------
  // Config adapter metadata
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Config adapter metadata")
  class ConfigAdapterMetadataTests {

    @Test
    @DisplayName("Should return configured realm")
    void shouldReturnTargetRealm() {
      Group group = new Group();
      assertThat(createService().getRealm(group)).isEqualTo(TARGET_REALM);
    }

    @Test
    @DisplayName("Should return 'group' as target component")
    void shouldReturnGroupComponent() {
      assertThat(createService().getTargetComponent()).isEqualTo("group");
    }

    @Test
    @DisplayName("Should return '/groups' as config path")
    void shouldReturnGroupsPath() {
      assertThat(createService().getConfigPath()).isEqualTo("/groups");
    }
  }
}
