package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.model.input.AssignmentScopedInputDTO;
import de.civitascore.portal.model.input.CatalogInputDTO;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;

@DisplayName("Assignment Service Integration Tests")
class AssignmentServiceIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private AssignmentService assignmentService;
  @Autowired private GroupService groupService;
  @Autowired private RoleService roleService;
  @Autowired private DataSetService dataSetService;
  @Autowired private DataSpaceService dataSpaceService;
  @Autowired private CatalogService catalogService;

  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSpaceRepository dataSpaceRepository;
  @Autowired private CatalogRepository catalogRepository;

  private Group testGroup;
  private Role testDataRole;
  private Role testSystemRole;
  private DataSpace testDataSpace;
  private DataSet testDataSet;
  private Catalog testCatalog;

  @BeforeEach
  void setUp() {
    // Create test group
    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("Test Group " + System.currentTimeMillis());
    groupInput.setDescription("Test group for assignment testing");
    testGroup = groupService.create(groupInput);

    // Create test DATA role
    RoleInputDTO dataRoleInput = new RoleInputDTO();
    dataRoleInput.setName("test_data_role_" + System.currentTimeMillis());
    dataRoleInput.setDescription("Test DATA role for assignment testing");
    dataRoleInput.setRoleType(RoleType.DATA);
    testDataRole = roleService.create(dataRoleInput);

    // Create test SYSTEM role
    RoleInputDTO systemRoleInput = new RoleInputDTO();
    systemRoleInput.setName("test_system_role_" + System.currentTimeMillis());
    systemRoleInput.setDescription("Test SYSTEM role for assignment testing");
    systemRoleInput.setRoleType(RoleType.SYSTEM);
    testSystemRole = roleService.create(systemRoleInput);

    // Create test DataSpace
    DataSpaceInputDTO dataSpaceInput = new DataSpaceInputDTO();
    dataSpaceInput.setName("Test DataSpace " + System.currentTimeMillis());
    dataSpaceInput.setDescription("Test dataspace for assignment testing");
    testDataSpace = dataSpaceService.create(dataSpaceInput);

    // Create test DataSet
    DataSetInputDTO dataSetInput = new DataSetInputDTO();
    dataSetInput.setName("Test DataSet " + System.currentTimeMillis());
    dataSetInput.setDescription("Test dataset for assignment testing");
    dataSetInput.setDataSpaceIds(List.of(testDataSpace.getId()));
    testDataSet = dataSetService.create(dataSetInput);

    // Create test Catalog
    CatalogInputDTO catalogInput = new CatalogInputDTO();
    catalogInput.setName("Test Catalog " + System.currentTimeMillis());
    catalogInput.setDescription("Test catalog for assignment testing");
    testCatalog = catalogService.create(catalogInput);
  }

  @AfterEach
  void cleanup() {
    assignmentRepository.deleteAll();
    dataSetRepository.deleteAll();
    dataSpaceRepository.deleteAll();
    catalogRepository.deleteAll();
    roleRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Nested
  @DisplayName("postConvertToEntity Tests")
  class PostConvertToEntityTests {

    @Test
    @DisplayName("Should set group from groupId")
    void shouldSetGroupFromGroupId() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getGroup()).isNotNull();
      assertThat(assignment.getGroup().getId()).isEqualTo(testGroup.getId());
    }

    @Test
    @DisplayName("Should set role from roleId")
    void shouldSetRoleFromRoleId() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getRole()).isNotNull();
      assertThat(assignment.getRole().getId()).isEqualTo(testSystemRole.getId());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for non-existent groupId")
    void shouldThrowExceptionForNonExistentGroup() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(UUID.randomUUID());
      input.setRoleId(testSystemRole.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Group");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for non-existent roleId")
    void shouldThrowExceptionForNonExistentRole() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Role");
    }

    @Test
    @DisplayName("Should set dataset when scopeType is DATASET")
    void shouldSetDatasetWhenScopeTypeIsDataset() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getDataset()).isNotNull();
      assertThat(assignment.getDataset().getId()).isEqualTo(testDataSet.getId());
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.DATASET);
    }

    @Test
    @DisplayName("Should set dataspace when scopeType is DATASPACE")
    void shouldSetDataspaceWhenScopeTypeIsDataspace() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(testDataSpace.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getDataSpace()).isNotNull();
      assertThat(assignment.getDataSpace().getId()).isEqualTo(testDataSpace.getId());
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.DATASPACE);
    }

    @Test
    @DisplayName("Should set catalog when scopeType is CATALOG")
    void shouldSetCatalogWhenScopeTypeIsCatalog() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(testCatalog.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getCatalog()).isNotNull();
      assertThat(assignment.getCatalog().getId()).isEqualTo(testCatalog.getId());
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.CATALOG);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for DATASOURCE scope")
    void shouldThrowExceptionForDatasourceScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASOURCE);
      input.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Datasource");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for DATASTRUCTURE scope")
    void shouldThrowExceptionForDatastructureScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASTRUCTURE);
      input.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Datastructure");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for non-existent dataset")
    void shouldThrowExceptionForNonExistentDataset() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("DataSet");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for non-existent dataspace")
    void shouldThrowExceptionForNonExistentDataspace() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("DataSpace");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for non-existent catalog")
    void shouldThrowExceptionForNonExistentCatalog() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Catalog");
    }

    @Test
    @DisplayName("Should not set scope entity when scopeId is null")
    void shouldNotSetScopeEntityWhenScopeIdIsNull() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.TENANT);
      input.setScopeId(null);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.TENANT);
    }
  }

  @Nested
  @DisplayName("validateScope Tests")
  class ValidateScopeTests {

    @Test
    @DisplayName("Should succeed when scopeType is null and no scope entity is set")
    void shouldSucceedWhenScopeTypeIsNullAndNoScopeEntity() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());
      input.setScopeType(null);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getScopeType()).isNull();
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should succeed when scopeType is TENANT and no scope entity is set")
    void shouldSucceedWhenScopeTypeIsTenantAndNoScopeEntity() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.TENANT);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.TENANT);
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should succeed when scopeType is DATASET and dataset is set")
    void shouldSucceedWhenScopeTypeIsDatasetAndDatasetIsSet() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.DATASET);
      assertThat(assignment.getDataset()).isNotNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should succeed when scopeType is DATASPACE and dataspace is set")
    void shouldSucceedWhenScopeTypeIsDataspaceAndDataspaceIsSet() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(testDataSpace.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.DATASPACE);
      assertThat(assignment.getDataSpace()).isNotNull();
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should succeed when scopeType is CATALOG and catalog is set")
    void shouldSucceedWhenScopeTypeIsCatalogAndCatalogIsSet() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(testCatalog.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.CATALOG);
      assertThat(assignment.getCatalog()).isNotNull();
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
    }
  }

  @Nested
  @DisplayName("validateRoleType Tests")
  class ValidateRoleTypeTests {

    @Test
    @DisplayName("Should succeed when SYSTEM role has null scopeType")
    void shouldSucceedWhenSystemRoleHasNullScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());
      input.setScopeType(null);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getRole().getRoleType()).isEqualTo(RoleType.SYSTEM);
      assertThat(assignment.getScopeType()).isNull();
    }

    @Test
    @DisplayName("Should fail when SYSTEM role has non-null scopeType")
    void shouldFailWhenSystemRoleHasNonNullScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());
      input.setScopeType(ScopeType.TENANT);

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasMessageContaining("SYSTEM roles cannot have scope");
    }

    @Test
    @DisplayName("Should succeed when DATA role has non-null scopeType")
    void shouldSucceedWhenDataRoleHasNonNullScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.TENANT);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getRole().getRoleType()).isEqualTo(RoleType.DATA);
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.TENANT);
    }

    @Test
    @DisplayName("Should fail when DATA role has null scopeType")
    void shouldFailWhenDataRoleHasNullScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(null);

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasMessageContaining("Only SYSTEM roles can have null scope");
    }

    @Test
    @DisplayName("Should succeed when GOVERNANCE role has non-null scopeType")
    void shouldSucceedWhenGovernanceRoleHasNonNullScope() {
      // Create GOVERNANCE role
      RoleInputDTO governanceRoleInput = new RoleInputDTO();
      governanceRoleInput.setName("test_governance_role_" + System.currentTimeMillis());
      governanceRoleInput.setDescription("Test GOVERNANCE role");
      governanceRoleInput.setRoleType(RoleType.GOVERNANCE);
      Role governanceRole = roleService.create(governanceRoleInput);

      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(governanceRole.getId());
      input.setScopeType(ScopeType.TENANT);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getRole().getRoleType()).isEqualTo(RoleType.GOVERNANCE);
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.TENANT);
    }

    @Test
    @DisplayName("Should fail when GOVERNANCE role has null scopeType")
    void shouldFailWhenGovernanceRoleHasNullScope() {
      // Create GOVERNANCE role
      RoleInputDTO governanceRoleInput = new RoleInputDTO();
      governanceRoleInput.setName("test_governance_role_" + System.currentTimeMillis());
      governanceRoleInput.setDescription("Test GOVERNANCE role");
      governanceRoleInput.setRoleType(RoleType.GOVERNANCE);
      Role governanceRole = roleService.create(governanceRoleInput);

      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(governanceRole.getId());
      input.setScopeType(null);

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasMessageContaining("Only SYSTEM roles can have null scope");
    }
  }

  @Nested
  @DisplayName("Combined Validation Tests")
  class CombinedValidationTests {

    @Test
    @DisplayName("Should create valid SYSTEM assignment without scope")
    void shouldCreateValidSystemAssignmentWithoutScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getId()).isNotNull();
      assertThat(assignment.getGroup()).isNotNull();
      assertThat(assignment.getRole()).isNotNull();
      assertThat(assignment.getRole().getRoleType()).isEqualTo(RoleType.SYSTEM);
      assertThat(assignment.getScopeType()).isNull();
    }

    @Test
    @DisplayName("Should create valid DATA assignment with DATASET scope")
    void shouldCreateValidDataAssignmentWithDatasetScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getId()).isNotNull();
      assertThat(assignment.getGroup()).isNotNull();
      assertThat(assignment.getRole()).isNotNull();
      assertThat(assignment.getRole().getRoleType()).isEqualTo(RoleType.DATA);
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.DATASET);
      assertThat(assignment.getDataset()).isNotNull();
    }

    @Test
    @DisplayName("Should create valid DATA assignment with TENANT scope")
    void shouldCreateValidDataAssignmentWithTenantScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.TENANT);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment).isNotNull();
      assertThat(assignment.getId()).isNotNull();
      assertThat(assignment.getRole().getRoleType()).isEqualTo(RoleType.DATA);
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.TENANT);
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should enforce unique constraint on duplicate assignments")
    void shouldEnforceUniqueConstraintOnDuplicateAssignments() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());

      // Create first assignment
      assignmentService.create(input);

      // Try to create duplicate assignment
      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should allow same group and role with different scopes")
    void shouldAllowSameGroupAndRoleWithDifferentScopes() {
      // Create assignment with DATASET scope
      AssignmentInputDTO input1 = new AssignmentInputDTO();
      input1.setGroupId(testGroup.getId());
      input1.setRoleId(testDataRole.getId());
      input1.setScopeType(ScopeType.DATASET);
      input1.setScopeId(testDataSet.getId());

      Assignment assignment1 = assignmentService.create(input1);

      // Create assignment with DATASPACE scope
      AssignmentInputDTO input2 = new AssignmentInputDTO();
      input2.setGroupId(testGroup.getId());
      input2.setRoleId(testDataRole.getId());
      input2.setScopeType(ScopeType.DATASPACE);
      input2.setScopeId(testDataSpace.getId());

      Assignment assignment2 = assignmentService.create(input2);

      assertThat(assignment1).isNotNull();
      assertThat(assignment2).isNotNull();
      assertThat(assignment1.getId()).isNotEqualTo(assignment2.getId());
    }
  }

  @Nested
  @DisplayName("AssignmentOutputDTO scopeId Mapping Tests")
  class ScopeIdMappingTests {

    @Test
    @DisplayName("Should map scopeId to dataset ID when scopeType is DATASET")
    void shouldMapScopeIdToDatasetId() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getScopeId()).isNotNull();
      assertThat(assignment.getScopeId()).isEqualTo(testDataSet.getId());
      assertThat(assignment.getDataset()).isNotNull();
      assertThat(assignment.getDataset().getId()).isEqualTo(testDataSet.getId());
    }

    @Test
    @DisplayName("Should map scopeId to dataspace ID when scopeType is DATASPACE")
    void shouldMapScopeIdToDataspaceId() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(testDataSpace.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getScopeId()).isNotNull();
      assertThat(assignment.getScopeId()).isEqualTo(testDataSpace.getId());
      assertThat(assignment.getDataSpace()).isNotNull();
      assertThat(assignment.getDataSpace().getId()).isEqualTo(testDataSpace.getId());
    }

    @Test
    @DisplayName("Should map scopeId to catalog ID when scopeType is CATALOG")
    void shouldMapScopeIdToCatalogId() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(testCatalog.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getScopeId()).isNotNull();
      assertThat(assignment.getScopeId()).isEqualTo(testCatalog.getId());
      assertThat(assignment.getCatalog()).isNotNull();
      assertThat(assignment.getCatalog().getId()).isEqualTo(testCatalog.getId());
    }

    @Test
    @DisplayName("Should have null scopeId when scopeType is TENANT")
    void shouldHaveNullScopeIdForTenantScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.TENANT);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getScopeId()).isNull();
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should have null scopeId when scopeType is null (SYSTEM role)")
    void shouldHaveNullScopeIdForSystemRole() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());
      input.setScopeType(null);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getScopeId()).isNull();
      assertThat(assignment.getScopeType()).isNull();
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should retrieve scopeId correctly after persisting and reloading")
    void shouldRetrieveScopeIdAfterPersist() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());

      Assignment created = assignmentService.create(input);
      UUID assignmentId = created.getId();

      // Retrieve the assignment from database
      Assignment retrieved = assignmentService.findByIdOrThrow(assignmentId);

      assertThat(retrieved.getScopeId()).isNotNull();
      assertThat(retrieved.getScopeId()).isEqualTo(testDataSet.getId());
      assertThat(retrieved.getDataset()).isNotNull();
      assertThat(retrieved.getDataset().getId()).isEqualTo(testDataSet.getId());
    }
  }

  @Nested
  @DisplayName("findAllByScopeTypeAndScopeId Tests")
  class FindAllByScopeTypeAndScopeIdTests {

    @Test
    @DisplayName("Should return assignments matching DATASET scope type and scope ID")
    void shouldReturnAssignmentsMatchingDatasetScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());
      assignmentService.create(input);

      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASET, testDataSet.getId());

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getScopeType()).isEqualTo(ScopeType.DATASET);
      assertThat(results.get(0).getDataset().getId()).isEqualTo(testDataSet.getId());
    }

    @Test
    @DisplayName("Should return assignments matching DATASPACE scope type and scope ID")
    void shouldReturnAssignmentsMatchingDataspaceScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(testDataSpace.getId());
      assignmentService.create(input);

      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(
              ScopeType.DATASPACE, testDataSpace.getId());

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getScopeType()).isEqualTo(ScopeType.DATASPACE);
      assertThat(results.get(0).getDataSpace().getId()).isEqualTo(testDataSpace.getId());
    }

    @Test
    @DisplayName("Should return assignments matching CATALOG scope type and scope ID")
    void shouldReturnAssignmentsMatchingCatalogScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(testCatalog.getId());
      assignmentService.create(input);

      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.CATALOG, testCatalog.getId());

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getScopeType()).isEqualTo(ScopeType.CATALOG);
      assertThat(results.get(0).getCatalog().getId()).isEqualTo(testCatalog.getId());
    }

    @Test
    @DisplayName("Should return empty list when no assignments match the scope")
    void shouldReturnEmptyListWhenNoAssignmentsMatch() {
      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASET, UUID.randomUUID());

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should return multiple assignments for the same scope")
    void shouldReturnMultipleAssignmentsForSameScope() {
      // Create second group
      GroupInputDTO groupInput = new GroupInputDTO();
      groupInput.setName("Second Group " + System.currentTimeMillis());
      groupInput.setDescription("Second test group");
      Group secondGroup = groupService.create(groupInput);

      // Create first assignment
      AssignmentInputDTO input1 = new AssignmentInputDTO();
      input1.setGroupId(testGroup.getId());
      input1.setRoleId(testDataRole.getId());
      input1.setScopeType(ScopeType.DATASET);
      input1.setScopeId(testDataSet.getId());
      assignmentService.create(input1);

      // Create second assignment with different group, same scope
      AssignmentInputDTO input2 = new AssignmentInputDTO();
      input2.setGroupId(secondGroup.getId());
      input2.setRoleId(testDataRole.getId());
      input2.setScopeType(ScopeType.DATASET);
      input2.setScopeId(testDataSet.getId());
      assignmentService.create(input2);

      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASET, testDataSet.getId());

      assertThat(results).hasSize(2);
      assertThat(results)
          .allSatisfy(
              a -> {
                assertThat(a.getScopeType()).isEqualTo(ScopeType.DATASET);
                assertThat(a.getDataset().getId()).isEqualTo(testDataSet.getId());
              });
    }

    @Test
    @DisplayName("Should not return assignments with different scope type but same entity ID")
    void shouldNotReturnAssignmentsWithDifferentScopeType() {
      // Create a DATASET-scoped assignment
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());
      assignmentService.create(input);

      // Query with DATASPACE scope type using the dataset's ID — should find nothing
      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASPACE, testDataSet.getId());

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should eagerly load group and role relationships")
    void shouldEagerlyLoadRelationships() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());
      assignmentService.create(input);

      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASET, testDataSet.getId());

      assertThat(results).hasSize(1);
      Assignment result = results.get(0);
      // These should not throw LazyInitializationException
      assertThat(result.getGroup()).isNotNull();
      assertThat(result.getGroup().getId()).isEqualTo(testGroup.getId());
      assertThat(result.getRole()).isNotNull();
      assertThat(result.getRole().getId()).isEqualTo(testDataRole.getId());
      assertThat(result.getDataset()).isNotNull();
      assertThat(result.getDataset().getId()).isEqualTo(testDataSet.getId());
    }
  }

  @Nested
  @DisplayName("replaceAllByScopeTypeAndScopeId Tests")
  class ReplaceAllByScopeTypeAndScopeIdTests {

    private AssignmentScopedInputDTO scopedInput(UUID groupId, UUID roleId) {
      AssignmentScopedInputDTO input = new AssignmentScopedInputDTO();
      input.setGroupId(groupId);
      input.setRoleId(roleId);
      return input;
    }

    @Test
    @DisplayName("Should replace existing assignments with new ones")
    void shouldReplaceExistingAssignmentsWithNewOnes() {
      // Create an existing assignment for the dataset scope
      AssignmentInputDTO existingInput = new AssignmentInputDTO();
      existingInput.setGroupId(testGroup.getId());
      existingInput.setRoleId(testDataRole.getId());
      existingInput.setScopeType(ScopeType.DATASET);
      existingInput.setScopeId(testDataSet.getId());
      Assignment existing = assignmentService.create(existingInput);
      UUID existingId = existing.getId();

      // Create a second group for the replacement
      GroupInputDTO groupInput = new GroupInputDTO();
      groupInput.setName("Replacement Group " + System.currentTimeMillis());
      groupInput.setDescription("Group for replacement");
      Group replacementGroup = groupService.create(groupInput);

      // Replace with a new assignment using the different group
      List<Assignment> result =
          assignmentService.replaceAllByScopeTypeAndScopeId(
              List.of(scopedInput(replacementGroup.getId(), testDataRole.getId())),
              ScopeType.DATASET,
              testDataSet.getId());

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getGroup().getId()).isEqualTo(replacementGroup.getId());
      assertThat(result.get(0).getId()).isNotEqualTo(existingId);

      // Verify old assignment is gone
      assertThat(assignmentService.findById(existingId)).isEmpty();
    }

    @Test
    @DisplayName("Should create assignments when none exist for the scope")
    void shouldCreateAssignmentsWhenNoneExist() {
      List<Assignment> result =
          assignmentService.replaceAllByScopeTypeAndScopeId(
              List.of(scopedInput(testGroup.getId(), testDataRole.getId())),
              ScopeType.DATASET,
              testDataSet.getId());

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getGroup().getId()).isEqualTo(testGroup.getId());
      assertThat(result.get(0).getRole().getId()).isEqualTo(testDataRole.getId());
      assertThat(result.get(0).getScopeType()).isEqualTo(ScopeType.DATASET);
      assertThat(result.get(0).getDataset().getId()).isEqualTo(testDataSet.getId());
    }

    @Test
    @DisplayName("Should delete all existing assignments when input list is empty")
    void shouldDeleteAllWhenInputListIsEmpty() {
      // Create an existing assignment
      AssignmentInputDTO existingInput = new AssignmentInputDTO();
      existingInput.setGroupId(testGroup.getId());
      existingInput.setRoleId(testDataRole.getId());
      existingInput.setScopeType(ScopeType.DATASET);
      existingInput.setScopeId(testDataSet.getId());
      assignmentService.create(existingInput);

      List<Assignment> result =
          assignmentService.replaceAllByScopeTypeAndScopeId(
              List.of(), ScopeType.DATASET, testDataSet.getId());

      assertThat(result).isEmpty();
      assertThat(
              assignmentService.findAllByScopeTypeAndScopeId(
                  ScopeType.DATASET, testDataSet.getId()))
          .isEmpty();
    }

    @Test
    @DisplayName("Should create multiple assignments for the same scope")
    void shouldCreateMultipleAssignmentsForSameScope() {
      // Create a second group
      GroupInputDTO groupInput = new GroupInputDTO();
      groupInput.setName("Second Group " + System.currentTimeMillis());
      groupInput.setDescription("Second group");
      Group secondGroup = groupService.create(groupInput);

      List<Assignment> result =
          assignmentService.replaceAllByScopeTypeAndScopeId(
              List.of(
                  scopedInput(testGroup.getId(), testDataRole.getId()),
                  scopedInput(secondGroup.getId(), testDataRole.getId())),
              ScopeType.DATASET,
              testDataSet.getId());

      assertThat(result).hasSize(2);
      assertThat(result)
          .allSatisfy(
              a -> {
                assertThat(a.getScopeType()).isEqualTo(ScopeType.DATASET);
                assertThat(a.getDataset().getId()).isEqualTo(testDataSet.getId());
              });
      assertThat(result)
          .extracting(a -> a.getGroup().getId())
          .containsExactlyInAnyOrder(testGroup.getId(), secondGroup.getId());
    }

    @Test
    @DisplayName("Should work with DATASPACE scope type")
    void shouldWorkWithDataspaceScope() {
      List<Assignment> result =
          assignmentService.replaceAllByScopeTypeAndScopeId(
              List.of(scopedInput(testGroup.getId(), testDataRole.getId())),
              ScopeType.DATASPACE,
              testDataSpace.getId());

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getScopeType()).isEqualTo(ScopeType.DATASPACE);
      assertThat(result.get(0).getDataSpace().getId()).isEqualTo(testDataSpace.getId());
    }

    @Test
    @DisplayName("Should work with CATALOG scope type")
    void shouldWorkWithCatalogScope() {
      List<Assignment> result =
          assignmentService.replaceAllByScopeTypeAndScopeId(
              List.of(scopedInput(testGroup.getId(), testDataRole.getId())),
              ScopeType.CATALOG,
              testCatalog.getId());

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getScopeType()).isEqualTo(ScopeType.CATALOG);
      assertThat(result.get(0).getCatalog().getId()).isEqualTo(testCatalog.getId());
    }

    @Test
    @DisplayName("Should not delete existing assignments when validation fails")
    void shouldNotDeleteExistingWhenValidationFails() {
      // Create a valid existing assignment
      AssignmentInputDTO existingInput = new AssignmentInputDTO();
      existingInput.setGroupId(testGroup.getId());
      existingInput.setRoleId(testDataRole.getId());
      existingInput.setScopeType(ScopeType.DATASET);
      existingInput.setScopeId(testDataSet.getId());
      assignmentService.create(existingInput);

      // Try to replace with an invalid input (non-existent group)
      assertThatThrownBy(
              () ->
                  assignmentService.replaceAllByScopeTypeAndScopeId(
                      List.of(scopedInput(UUID.randomUUID(), testDataRole.getId())),
                      ScopeType.DATASET,
                      testDataSet.getId()))
          .isInstanceOf(ResourceNotFoundException.class);

      // Existing assignment should still be there
      List<Assignment> remaining =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASET, testDataSet.getId());
      assertThat(remaining).hasSize(1);
      assertThat(remaining.get(0).getGroup().getId()).isEqualTo(testGroup.getId());
    }

    @Test
    @DisplayName("Should not delete existing assignments when role validation fails")
    void shouldNotDeleteExistingWhenRoleValidationFails() {
      // Create a valid existing assignment
      AssignmentInputDTO existingInput = new AssignmentInputDTO();
      existingInput.setGroupId(testGroup.getId());
      existingInput.setRoleId(testDataRole.getId());
      existingInput.setScopeType(ScopeType.DATASET);
      existingInput.setScopeId(testDataSet.getId());
      assignmentService.create(existingInput);

      // Try to replace with SYSTEM role + scoped assignment (invalid combination)
      // @PrePersist validation wraps IllegalStateException in InvalidDataAccessApiUsageException
      assertThatThrownBy(
              () ->
                  assignmentService.replaceAllByScopeTypeAndScopeId(
                      List.of(scopedInput(testGroup.getId(), testSystemRole.getId())),
                      ScopeType.DATASET,
                      testDataSet.getId()))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasMessageContaining("SYSTEM roles cannot have scope");

      // Existing assignment should still be there
      List<Assignment> remaining =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASET, testDataSet.getId());
      assertThat(remaining).hasSize(1);
    }

    @Test
    @DisplayName("Should not delete existing when second input in list fails validation")
    void shouldNotDeleteExistingWhenSecondInputFailsValidation() {
      // Create a valid existing assignment
      AssignmentInputDTO existingInput = new AssignmentInputDTO();
      existingInput.setGroupId(testGroup.getId());
      existingInput.setRoleId(testDataRole.getId());
      existingInput.setScopeType(ScopeType.DATASET);
      existingInput.setScopeId(testDataSet.getId());
      assignmentService.create(existingInput);

      // First input is valid, second has non-existent group
      GroupInputDTO groupInput = new GroupInputDTO();
      groupInput.setName("Valid Group " + System.currentTimeMillis());
      groupInput.setDescription("Valid group");
      Group validGroup = groupService.create(groupInput);

      assertThatThrownBy(
              () ->
                  assignmentService.replaceAllByScopeTypeAndScopeId(
                      List.of(
                          scopedInput(validGroup.getId(), testDataRole.getId()),
                          scopedInput(UUID.randomUUID(), testDataRole.getId())),
                      ScopeType.DATASET,
                      testDataSet.getId()))
          .isInstanceOf(ResourceNotFoundException.class);

      // Existing assignment should still be there
      List<Assignment> remaining =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.DATASET, testDataSet.getId());
      assertThat(remaining).hasSize(1);
      assertThat(remaining.get(0).getGroup().getId()).isEqualTo(testGroup.getId());
    }

    @Test
    @DisplayName("Should not affect assignments with different scope")
    void shouldNotAffectAssignmentsWithDifferentScope() {
      // Create assignment on DATASET scope
      AssignmentInputDTO datasetInput = new AssignmentInputDTO();
      datasetInput.setGroupId(testGroup.getId());
      datasetInput.setRoleId(testDataRole.getId());
      datasetInput.setScopeType(ScopeType.DATASET);
      datasetInput.setScopeId(testDataSet.getId());
      assignmentService.create(datasetInput);

      // Create assignment on DATASPACE scope
      AssignmentInputDTO dataspaceInput = new AssignmentInputDTO();
      dataspaceInput.setGroupId(testGroup.getId());
      dataspaceInput.setRoleId(testDataRole.getId());
      dataspaceInput.setScopeType(ScopeType.DATASPACE);
      dataspaceInput.setScopeId(testDataSpace.getId());
      assignmentService.create(dataspaceInput);

      // Replace only the DATASET scope assignments with empty list
      assignmentService.replaceAllByScopeTypeAndScopeId(
          List.of(), ScopeType.DATASET, testDataSet.getId());

      // DATASET scope should be empty
      assertThat(
              assignmentService.findAllByScopeTypeAndScopeId(
                  ScopeType.DATASET, testDataSet.getId()))
          .isEmpty();

      // DATASPACE scope should be untouched
      assertThat(
              assignmentService.findAllByScopeTypeAndScopeId(
                  ScopeType.DATASPACE, testDataSpace.getId()))
          .hasSize(1);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle creation with all scope types")
    void shouldHandleCreationWithAllScopeTypes() {
      // Test with each valid scope type
      ScopeType[] scopeTypes =
          new ScopeType[] {
            ScopeType.TENANT, ScopeType.DATASET, ScopeType.DATASPACE, ScopeType.CATALOG
          };

      for (ScopeType scopeType : scopeTypes) {
        AssignmentInputDTO input = new AssignmentInputDTO();
        input.setGroupId(testGroup.getId());
        input.setRoleId(testDataRole.getId());
        input.setScopeType(scopeType);

        if (scopeType == ScopeType.DATASET) {
          input.setScopeId(testDataSet.getId());
        } else if (scopeType == ScopeType.DATASPACE) {
          input.setScopeId(testDataSpace.getId());
        } else if (scopeType == ScopeType.CATALOG) {
          input.setScopeId(testCatalog.getId());
        }

        Assignment assignment = assignmentService.create(input);

        assertThat(assignment).isNotNull();
        assertThat(assignment.getScopeType()).isEqualTo(scopeType);
      }
    }

    @Test
    @DisplayName("Should persist and retrieve assignment correctly")
    void shouldPersistAndRetrieveAssignmentCorrectly() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());

      Assignment created = assignmentService.create(input);
      UUID assignmentId = created.getId();

      // Retrieve the assignment
      Assignment retrieved = assignmentService.findByIdOrThrow(assignmentId);

      assertThat(retrieved).isNotNull();
      assertThat(retrieved.getId()).isEqualTo(assignmentId);
      assertThat(retrieved.getGroup().getId()).isEqualTo(testGroup.getId());
      assertThat(retrieved.getRole().getId()).isEqualTo(testDataRole.getId());
      assertThat(retrieved.getScopeType()).isEqualTo(ScopeType.DATASET);
      assertThat(retrieved.getDataset().getId()).isEqualTo(testDataSet.getId());
    }

    @Test
    @DisplayName("Should handle multiple assignments for same group with different roles")
    void shouldHandleMultipleAssignmentsForSameGroupWithDifferentRoles() {
      // Create second DATA role
      RoleInputDTO roleInput = new RoleInputDTO();
      roleInput.setName("test_data_role_2_" + System.currentTimeMillis());
      roleInput.setDescription("Second test DATA role");
      roleInput.setRoleType(RoleType.DATA);
      Role secondDataRole = roleService.create(roleInput);

      // Create assignment with first role
      AssignmentInputDTO input1 = new AssignmentInputDTO();
      input1.setGroupId(testGroup.getId());
      input1.setRoleId(testDataRole.getId());
      input1.setScopeType(ScopeType.TENANT);

      Assignment assignment1 = assignmentService.create(input1);

      // Create assignment with second role
      AssignmentInputDTO input2 = new AssignmentInputDTO();
      input2.setGroupId(testGroup.getId());
      input2.setRoleId(secondDataRole.getId());
      input2.setScopeType(ScopeType.TENANT);

      Assignment assignment2 = assignmentService.create(input2);

      assertThat(assignment1).isNotNull();
      assertThat(assignment2).isNotNull();
      assertThat(assignment1.getId()).isNotEqualTo(assignment2.getId());
      assertThat(assignment1.getRole().getId()).isNotEqualTo(assignment2.getRole().getId());
    }
  }
}
