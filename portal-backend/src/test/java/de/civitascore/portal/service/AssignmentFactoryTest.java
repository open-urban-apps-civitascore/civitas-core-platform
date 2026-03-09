package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
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
@DisplayName("AssignmentFactory Tests")
class AssignmentFactoryTest {

  @Mock private GroupRepository groupRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataStructureRepository dataStructureRepository;
  @Mock private DataSourceRepository dataSourceRepository;

  @InjectMocks private AssignmentFactory assignmentFactory;

  private Group createGroup(UUID id) {
    Group group = new Group();
    group.setId(id);
    group.setName("Test Group");
    return group;
  }

  private Role createRole(UUID id, RoleType roleType) {
    Role role = new Role();
    role.setId(id);
    role.setName("Test Role");
    role.setRoleType(roleType);
    return role;
  }

  @Nested
  @DisplayName("build(AssignmentGroupInputDTO)")
  class BuildAssignmentGroupInputTests {

    @Test
    @DisplayName("Should build assignment with DATA role and TENANT scope (no scopeId)")
    void shouldBuildWithDataRoleAndTenantScope() {
      UUID roleId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.DATA);
      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);
      dto.setScopeType(ScopeType.TENANT);

      Assignment result = assignmentFactory.build(dto);

      assertThat(result.getRole()).isEqualTo(role);
      assertThat(result.getScopeType()).isEqualTo(ScopeType.TENANT);
      assertThat(result.getGroup()).isNull();
    }

    @Test
    @DisplayName("Should build assignment with DATA role and DATASET scope")
    void shouldBuildWithDataRoleAndDatasetScope() {
      UUID roleId = UUID.randomUUID();
      UUID scopeId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.DATA);
      DataSet dataSet = new DataSet();
      dataSet.setId(scopeId);

      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
      when(dataSetRepository.findById(scopeId)).thenReturn(Optional.of(dataSet));

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);
      dto.setScopeType(ScopeType.DATASET);
      dto.setScopeId(scopeId);

      Assignment result = assignmentFactory.build(dto);

      assertThat(result.getRole()).isEqualTo(role);
      assertThat(result.getScopeType()).isEqualTo(ScopeType.DATASET);
      assertThat(result.getDataset()).isEqualTo(dataSet);
    }

    @Test
    @DisplayName("Should build assignment with SYSTEM role and no scope")
    void shouldBuildWithSystemRoleAndNoScope() {
      UUID roleId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.SYSTEM);
      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);

      Assignment result = assignmentFactory.build(dto);

      assertThat(result.getRole()).isEqualTo(role);
      assertThat(result.getScopeType()).isNull();
    }

    @Test
    @DisplayName("Should reject SYSTEM role with scopeId")
    void shouldRejectSystemRoleWithScopeId() {
      UUID roleId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.SYSTEM);
      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);
      dto.setScopeType(ScopeType.DATASET);
      dto.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("SYSTEM roles cannot be scoped");
    }

    @Test
    @DisplayName("Should throw when roleId is null")
    void shouldThrowWhenRoleIdIsNull() {
      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("roleId is required");
    }

    @Test
    @DisplayName("Should throw when role not found")
    void shouldThrowWhenRoleNotFound() {
      UUID roleId = UUID.randomUUID();
      when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Role");
    }
  }

  @Nested
  @DisplayName("build(AssignmentInputDTO)")
  class BuildAssignmentInputTests {

    @Test
    @DisplayName("Should build assignment with group, role, and scope")
    void shouldBuildFullAssignment() {
      UUID groupId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      UUID scopeId = UUID.randomUUID();
      Group group = createGroup(groupId);
      Role role = createRole(roleId, RoleType.DATA);
      DataSource dataSource = new DataSource();
      dataSource.setId(scopeId);

      when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
      when(dataSourceRepository.findById(scopeId)).thenReturn(Optional.of(dataSource));

      AssignmentInputDTO dto = new AssignmentInputDTO();
      dto.setGroupId(groupId);
      dto.setRoleId(roleId);
      dto.setScopeType(ScopeType.DATASOURCE);
      dto.setScopeId(scopeId);

      Assignment result = assignmentFactory.build(dto);

      assertThat(result.getGroup()).isEqualTo(group);
      assertThat(result.getRole()).isEqualTo(role);
      assertThat(result.getScopeType()).isEqualTo(ScopeType.DATASOURCE);
      assertThat(result.getDataSource()).isEqualTo(dataSource);
    }

    @Test
    @DisplayName("Should throw when groupId is null")
    void shouldThrowWhenGroupIdIsNull() {
      AssignmentInputDTO dto = new AssignmentInputDTO();
      dto.setRoleId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("groupId is required");
    }

    @Test
    @DisplayName("Should throw when group not found")
    void shouldThrowWhenGroupNotFound() {
      UUID groupId = UUID.randomUUID();

      when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

      AssignmentInputDTO dto = new AssignmentInputDTO();
      dto.setGroupId(groupId);
      dto.setRoleId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Group");
    }

    @Test
    @DisplayName("Should allow SYSTEM role without scopeId")
    void shouldAllowSystemRoleWithoutScopeId() {
      UUID groupId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      Group group = createGroup(groupId);
      Role role = createRole(roleId, RoleType.SYSTEM);

      when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

      AssignmentInputDTO dto = new AssignmentInputDTO();
      dto.setGroupId(groupId);
      dto.setRoleId(roleId);

      Assignment result = assignmentFactory.build(dto);

      assertThat(result.getGroup()).isEqualTo(group);
      assertThat(result.getRole()).isEqualTo(role);
      assertThat(result.getScopeType()).isNull();
    }
  }

  @Nested
  @DisplayName("build(AssignmentScopedInputDTO)")
  class BuildAssignmentScopedInputTests {

    @Test
    @DisplayName("Should build assignment with group and DATA role")
    void shouldBuildWithGroupAndDataRole() {
      UUID groupId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      Group group = createGroup(groupId);
      Role role = createRole(roleId, RoleType.DATA);

      when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

      AssignmentScopedInputDTO dto = new AssignmentScopedInputDTO();
      dto.setGroupId(groupId);
      dto.setRoleId(roleId);

      Assignment result = assignmentFactory.build(dto);

      assertThat(result.getGroup()).isEqualTo(group);
      assertThat(result.getRole()).isEqualTo(role);
      assertThat(result.getScopeType()).isNull();
    }

    @Test
    @DisplayName("Should always reject SYSTEM role regardless of scope")
    void shouldAlwaysRejectSystemRole() {
      UUID groupId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      Group group = createGroup(groupId);
      Role role = createRole(roleId, RoleType.SYSTEM);

      when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

      AssignmentScopedInputDTO dto = new AssignmentScopedInputDTO();
      dto.setGroupId(groupId);
      dto.setRoleId(roleId);

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("SYSTEM roles cannot be scoped");
    }
  }

  @Nested
  @DisplayName("Scope resolution")
  class ScopeResolutionTests {

    @Test
    @DisplayName("Should resolve DATASTRUCTURE scope")
    void shouldResolveDataStructureScope() {
      UUID roleId = UUID.randomUUID();
      UUID scopeId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.GOVERNANCE);
      DataStructure dataStructure = new DataStructure();
      dataStructure.setId(scopeId);

      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
      when(dataStructureRepository.findById(scopeId)).thenReturn(Optional.of(dataStructure));

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);
      dto.setScopeType(ScopeType.DATASTRUCTURE);
      dto.setScopeId(scopeId);

      Assignment result = assignmentFactory.build(dto);

      assertThat(result.getDataStructure()).isEqualTo(dataStructure);
      assertThat(result.getScopeType()).isEqualTo(ScopeType.DATASTRUCTURE);
    }

    @Test
    @DisplayName("Should throw for scope entity not found")
    void shouldThrowWhenScopeEntityNotFound() {
      UUID roleId = UUID.randomUUID();
      UUID scopeId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.DATA);

      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
      when(dataSetRepository.findById(scopeId)).thenReturn(Optional.empty());

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);
      dto.setScopeType(ScopeType.DATASET);
      dto.setScopeId(scopeId);

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("DataSet");
    }

    @Test
    @DisplayName("Should reject DATASPACE scope as not available")
    void shouldRejectDataspaceScope() {
      UUID roleId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.DATA);

      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);
      dto.setScopeType(ScopeType.DATASPACE);
      dto.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("not available in this release");
    }

    @Test
    @DisplayName("Should reject CATALOG scope as not available")
    void shouldRejectCatalogScope() {
      UUID roleId = UUID.randomUUID();
      Role role = createRole(roleId, RoleType.DATA);

      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

      AssignmentGroupInputDTO dto = new AssignmentGroupInputDTO();
      dto.setRoleId(roleId);
      dto.setScopeType(ScopeType.CATALOG);
      dto.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentFactory.build(dto))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("not available in this release");
    }
  }
}
