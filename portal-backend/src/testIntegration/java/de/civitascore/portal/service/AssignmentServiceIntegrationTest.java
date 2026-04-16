package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.assignment.AssignmentInputDTO;
import de.civitascore.portal.util.InvalidInputException;
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
  @Autowired private PortalTestDataFactory portalData;

  private Group testGroup;
  private Role testDataRole;
  private Role testSystemRole;
  private DataSpace testDataSpace;
  private DataSet testDataSet;
  private Catalog testCatalog;
  private DataStructure testDataStructure;

  @BeforeEach
  void setUp() {
    testGroup = portalData.group(b -> b.description("Test group for assignment testing"));
    testDataRole =
        portalData.role(
            b -> b.roleType(RoleType.DATA).description("Test DATA role for assignment testing"));
    testSystemRole =
        portalData.role(
            b ->
                b.roleType(RoleType.SYSTEM).description("Test SYSTEM role for assignment testing"));
    testDataSpace =
        portalData.dataSpace(b -> b.description("Test dataspace for assignment testing"));
    testDataSet =
        portalData.dataSet(
            b -> b.description("Test dataset for assignment testing").openDataAccess(false));
    testCatalog = portalData.catalog(b -> b.description("Test catalog for assignment testing"));
    testDataStructure =
        portalData.dataStructure(b -> b.description("Test datastructure for assignment testing"));
  }

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
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

    // TODO v2.1: re-enable when DATASPACE scope is available
    @Test
    @DisplayName("Should reject DATASPACE scope (not available in this release)")
    void shouldSetDataspaceWhenScopeTypeIsDataspace() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(testDataSpace.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    // TODO v2.1: re-enable when CATALOG scope is available
    @Test
    @DisplayName("Should reject CATALOG scope (not available in this release)")
    void shouldSetCatalogWhenScopeTypeIsCatalog() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(testCatalog.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);
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
          .hasMessageContaining("DataSource");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for DATASTRUCTURE scope")
    void shouldThrowExceptionForDatastructureScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASTRUCTURE);
      input.setScopeId(testDataStructure.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getDataStructure()).isNotNull();
      assertThat(assignment.getDataStructure().getId()).isEqualTo(testDataStructure.getId());
      assertThat(assignment.getScopeType()).isEqualTo(ScopeType.DATASTRUCTURE);
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

    // TODO v2.1: expect ResourceNotFoundException("DataSpace") once DATASPACE scope is re-enabled
    @Test
    @DisplayName(
        "Should throw InvalidInputException for DATASPACE scope (not available in this release)")
    void shouldThrowExceptionForNonExistentDataspace() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    // TODO v2.1: expect ResourceNotFoundException("Catalog") once CATALOG scope is re-enabled
    @Test
    @DisplayName(
        "Should throw InvalidInputException for CATALOG scope (not available in this release)")
    void shouldThrowExceptionForNonExistentCatalog() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(UUID.randomUUID());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);
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

    // TODO v2.1: re-enable when DATASPACE scope is available
    @Test
    @DisplayName("Should reject DATASPACE scope (not available in this release)")
    void shouldSucceedWhenScopeTypeIsDataspaceAndDataspaceIsSet() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(testDataSpace.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    // TODO v2.1: re-enable when CATALOG scope is available
    @Test
    @DisplayName("Should reject CATALOG scope (not available in this release)")
    void shouldSucceedWhenScopeTypeIsCatalogAndCatalogIsSet() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(testCatalog.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);
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
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("SYSTEM roles cannot be scoped");
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

      // TODO v2.1: switch back to DATASPACE scope
      // AssignmentInputDTO input2 = new AssignmentInputDTO();
      // input2.setGroupId(testGroup.getId());
      // input2.setRoleId(testDataRole.getId());
      // input2.setScopeType(ScopeType.DATASPACE);
      // input2.setScopeId(testDataSpace.getId());
      AssignmentInputDTO input2 = new AssignmentInputDTO();
      input2.setGroupId(testGroup.getId());
      input2.setRoleId(testDataRole.getId());
      input2.setScopeType(ScopeType.TENANT);

      Assignment assignment2 = assignmentService.create(input2);

      assertThat(assignment1).isNotNull();
      assertThat(assignment2).isNotNull();
      assertThat(assignment1.getId()).isNotEqualTo(assignment2.getId());
    }
  }

  @Nested
  @DisplayName("Assignment scope Mapping Tests")
  class ScopeMappingTests {

    @Test
    @DisplayName("Should map scope to dataset when scopeType is DATASET")
    void shouldMapScopeToDataset() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getScope()).isNotNull();
      assertThat(assignment.getScope().getId()).isEqualTo(testDataSet.getId());
      assertThat(assignment.getDataset()).isNotNull();
      assertThat(assignment.getDataset().getId()).isEqualTo(testDataSet.getId());
    }

    // TODO v2.1: re-enable when DATASPACE scope is available
    @Test
    @DisplayName("Should reject DATASPACE scope (not available in this release)")
    void shouldMapScopeToDataspace() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(testDataSpace.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    // TODO v2.1: re-enable when CATALOG scope is available
    @Test
    @DisplayName("Should reject CATALOG scope (not available in this release)")
    void shouldMapScopeToCatalog() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(testCatalog.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should have null scope when scopeType is TENANT")
    void shouldHaveNullScopeForTenantScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.TENANT);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getScope()).isNull();
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should have null scope when scopeType is null (SYSTEM role)")
    void shouldHaveNullScopeForSystemRole() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testSystemRole.getId());
      input.setScopeType(null);

      Assignment assignment = assignmentService.create(input);

      assertThat(assignment.getScope()).isNull();
      assertThat(assignment.getScopeType()).isNull();
      assertThat(assignment.getDataset()).isNull();
      assertThat(assignment.getDataSpace()).isNull();
      assertThat(assignment.getCatalog()).isNull();
    }

    @Test
    @DisplayName("Should retrieve scope correctly after persisting and reloading")
    void shouldRetrieveScopeAfterPersist() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASET);
      input.setScopeId(testDataSet.getId());

      Assignment created = assignmentService.create(input);
      UUID assignmentId = created.getId();

      // Retrieve the assignment from database
      Assignment retrieved = assignmentService.findByIdOrThrow(assignmentId);

      assertThat(retrieved.getScope()).isNotNull();
      assertThat(retrieved.getScope().getId()).isEqualTo(testDataSet.getId());
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
      assertThat(results.getFirst().getScopeType()).isEqualTo(ScopeType.DATASET);
      assertThat(results.getFirst().getDataset().getId()).isEqualTo(testDataSet.getId());
    }

    // TODO v2.1: re-enable when DATASPACE scope is available
    @Test
    @DisplayName("Should return empty list for DATASPACE scope (not available in this release)")
    void shouldReturnAssignmentsMatchingDataspaceScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(testDataSpace.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);

      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(
              ScopeType.DATASPACE, testDataSpace.getId());
      assertThat(results).isEmpty();
    }

    // TODO v2.1: re-enable when CATALOG scope is available
    @Test
    @DisplayName("Should return empty list for CATALOG scope (not available in this release)")
    void shouldReturnAssignmentsMatchingCatalogScope() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(testGroup.getId());
      input.setRoleId(testDataRole.getId());
      input.setScopeType(ScopeType.CATALOG);
      input.setScopeId(testCatalog.getId());

      assertThatThrownBy(() -> assignmentService.create(input))
          .isInstanceOf(InvalidInputException.class);

      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(ScopeType.CATALOG, testCatalog.getId());
      assertThat(results).isEmpty();
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
      Group secondGroup = portalData.group(b -> b.description("Second test group"));

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

      // TODO v2.1: switch back to DATASPACE scope query
      // Query with DATASTRUCTURE scope type using the dataset's ID — should find nothing
      List<Assignment> results =
          assignmentService.findAllByScopeTypeAndScopeId(
              ScopeType.DATASTRUCTURE, testDataSet.getId());

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
      Assignment result = results.getFirst();
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
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    // TODO v2.1: add DATASPACE and CATALOG back to scope types array
    @Test
    @DisplayName("Should handle creation with all available scope types")
    void shouldHandleCreationWithAllScopeTypes() {
      // Test with each valid scope type (DATASPACE and CATALOG excluded until v2.1)
      ScopeType[] scopeTypes = new ScopeType[] {ScopeType.TENANT, ScopeType.DATASET};

      for (ScopeType scopeType : scopeTypes) {
        AssignmentInputDTO input = new AssignmentInputDTO();
        input.setGroupId(testGroup.getId());
        input.setRoleId(testDataRole.getId());
        input.setScopeType(scopeType);

        if (scopeType == ScopeType.DATASET) {
          input.setScopeId(testDataSet.getId());
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
      Role secondDataRole =
          portalData.role(b -> b.roleType(RoleType.DATA).description("Second test DATA role"));

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
