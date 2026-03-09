package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.assignment.AssignmentInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.RestPage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Assignment Controller Integration Tests")
class AssignmentControllerIntegrationTest
    extends BaseControllerIntegrationTest<AssignmentInputDTO, AssignmentOutputDTO> {

  private final String ASSIGNMENTS_ENDPOINT = "/assignments";

  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private DataSpaceRepository dataSpaceRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private CatalogRepository catalogRepository;

  @Override
  protected String getEndpointPath() {
    return ASSIGNMENTS_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    assignmentRepository.deleteAll();
  }

  @Override
  protected AssignmentInputDTO createValidInput() {
    UUID groupId = createTestGroup();
    UUID roleId = createTestRole();

    AssignmentInputDTO input = new AssignmentInputDTO();
    input.setGroupId(groupId);
    input.setRoleId(roleId);
    input.setScopeType(ScopeType.TENANT);
    return input;
  }

  @Override
  protected AssignmentInputDTO createInvalidInput() {
    return new AssignmentInputDTO();
  }

  @Override
  protected AssignmentInputDTO createUpdateInput() {
    return createValidInput();
  }

  @Override
  protected ParameterizedTypeReference<AssignmentOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<AssignmentOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(AssignmentOutputDTO output) {
    return output.getId();
  }

  private UUID createTestGroup() {
    Group group = new Group();
    group.setName("Test Group " + UUID.randomUUID().toString().substring(0, 8));
    group.setDescription("Test group for assignment");
    return groupRepository.save(group).getId();
  }

  private UUID createTestRole(String name) {
    Role role = new Role();
    role.setName(name);
    role.setDescription("Test role for assignment");
    role.setRoleType(RoleType.DATA);
    return roleRepository.save(role).getId();
  }

  private UUID createTestRole() {
    return createTestRole("Test Role " + System.currentTimeMillis());
  }

  private UUID createTestDataSpace() {
    return createTestDataSpaceEntity().getId();
  }

  private DataSpace createTestDataSpaceEntity() {
    DataSpace dataSpace = new DataSpace();
    dataSpace.setName("Test DataSpace " + UUID.randomUUID().toString().substring(0, 8));
    dataSpace.setDescription("Test dataspace for assignment");
    return dataSpaceRepository.save(dataSpace);
  }

  private UUID createTestDataSet(UUID dataSpaceId) {
    DataSpace dataSpace = dataSpaceRepository.findById(dataSpaceId).orElseThrow();
    DataSet dataSet = new DataSet();
    dataSet.setName("Test DataSet " + UUID.randomUUID().toString().substring(0, 8));
    dataSet.setDescription("Test dataset for assignment");
    dataSet.setDataSpaces(Set.of(dataSpace));
    return dataSetRepository.save(dataSet).getId();
  }

  private UUID createTestCatalog() {
    Catalog catalog = new Catalog();
    catalog.setName("Test Catalog " + UUID.randomUUID().toString().substring(0, 8));
    catalog.setDescription("Test catalog for assignment");
    return catalogRepository.save(catalog).getId();
  }

  private UUID getScopeIdForType(ScopeType scopeType) {
    return switch (scopeType) {
      case TENANT -> null;
      case DATASPACE -> createTestDataSpace();
      case DATASET -> createTestDataSet(createTestDataSpace());
      case CATALOG -> createTestCatalog();
      case DATASOURCE, DATASTRUCTURE -> null;
    };
  }

  @Nested
  @DisplayName("Create Assignment Tests")
  class CreateAssignmentTests {

    @Test
    @DisplayName("Should create assignment successfully with valid data")
    void shouldCreateAssignmentSuccessfully() {
      AssignmentInputDTO input = createValidInput();

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      AssignmentOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getGroup()).as("Group should be set").isNotNull();
      assertThat(output.getRole()).as("Role should be set").isNotNull();
      assertThat(output.getScopeType())
          .as("Scope type should match input")
          .isEqualTo(input.getScopeType());
      assertThat(output.getScope()).as("Scope should be null for TENANT assignments").isNull();
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();
    }

    @Test
    @DisplayName("Should fail to create assignment with missing required fields")
    void shouldFailToCreateAssignmentWithMissingFields() {
      AssignmentInputDTO input = createInvalidInput();

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create assignment without authentication")
    void shouldFailToCreateAssignmentWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("", org.springframework.http.HttpMethod.POST);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // TODO v2.1: add DATASPACE and CATALOG back to scope types list
    @Test
    @DisplayName("Should create assignment with different scope types")
    void shouldCreateAssignmentWithDifferentScopeTypes() {
      for (ScopeType scopeType : List.of(ScopeType.TENANT, ScopeType.DATASET)) {
        UUID groupId = createTestGroup();
        UUID roleId = createTestRole();

        AssignmentInputDTO input = new AssignmentInputDTO();
        input.setGroupId(groupId);
        input.setRoleId(roleId);
        input.setScopeType(scopeType);
        input.setScopeId(getScopeIdForType(scopeType));

        ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

        assertThat(response.getStatusCode())
            .as("Should create assignment with scope type " + scopeType)
            .isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getScopeType()).isEqualTo(scopeType);
      }
    }

    // TODO v2.1: re-enable when DATASPACE scope is available
    @Test
    @DisplayName("Should reject assignment with dataspace scope (not available in this release)")
    void shouldCreateAssignmentWithDataspaceScope() {
      DataSpace dataSpace = createTestDataSpaceEntity();

      AssignmentInputDTO input = createValidInput();
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId(dataSpace.getId());

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should create assignment with metadata")
    void shouldCreateAssignmentWithMetadata() {
      AssignmentInputDTO input = createValidInput();

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should fail to create assignment with non-existent group")
    void shouldFailToCreateAssignmentWithNonExistentGroup() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(UUID.randomUUID());
      input.setRoleId(createTestRole());
      input.setScopeType(ScopeType.TENANT);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND or BAD_REQUEST status")
          .isIn(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to create assignment with non-existent role")
    void shouldFailToCreateAssignmentWithNonExistentRole() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(createTestGroup());
      input.setRoleId(UUID.randomUUID());
      input.setScopeType(ScopeType.TENANT);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND or BAD_REQUEST status")
          .isIn(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Read Assignment Tests")
  class ReadAssignmentTests {

    @Test
    @DisplayName("Should retrieve assignment by ID successfully")
    void shouldRetrieveAssignmentById() {
      UUID assignmentId = createTestEntity();

      ResponseEntity<AssignmentOutputDTO> response = performGetById(assignmentId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      AssignmentOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(assignmentId);
      assertThat(output.getGroup()).as("Group should be present").isNotNull();
      assertThat(output.getRole()).as("Role should be present").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent assignment")
    void shouldReturn404ForNonExistentAssignment() {
      ResponseEntity<AssignmentOutputDTO> response = performGetById(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve assignment without authentication")
    void shouldFailToRetrieveAssignmentWithoutAuth() {
      UUID assignmentId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + assignmentId, org.springframework.http.HttpMethod.GET);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should retrieve all assignments with pagination")
    void shouldRetrieveAllAssignmentsWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<AssignmentOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<AssignmentOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should contain assignments").isNotEmpty();
      assertThat(page.getTotalElements()).as("Total elements should be positive").isPositive();
    }

    @Test
    @DisplayName("Should retrieve assignments with pagination parameters")
    void shouldRetrieveAssignmentsWithPaginationParams() {
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "createdAt,desc");

      ResponseEntity<RestPage<AssignmentOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Delete Assignment Tests")
  class DeleteAssignmentTests {

    @Test
    @DisplayName("Should delete assignment successfully")
    void shouldDeleteAssignmentSuccessfully() {
      UUID assignmentId = createTestEntity();

      ResponseEntity<Void> response = performDelete(assignmentId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<AssignmentOutputDTO> getResponse = performGetById(assignmentId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted assignment should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent assignment")
    void shouldFailToDeleteNonExistentAssignment() {
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete assignment without authentication")
    void shouldFailToDeleteAssignmentWithoutAuth() {
      UUID assignmentId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + assignmentId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Business Logic Tests")
  class BusinessLogicTests {

    @Test
    @DisplayName("Should handle global scope assignment")
    void shouldHandleGlobalScopeAssignment() {
      AssignmentInputDTO input = createValidInput();
      input.setScopeType(ScopeType.TENANT);
      input.setScopeId(null);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getScopeType()).isEqualTo(ScopeType.TENANT);
      assertThat(response.getBody().getScope())
          .as("Scope should be null for TENANT assignments")
          .isNull();
    }

    @Test
    @DisplayName("Should handle tenant scope assignment")
    void shouldHandleTenantScopeAssignment() {
      AssignmentInputDTO input = createValidInput();
      input.setScopeType(ScopeType.TENANT);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getScopeType()).isEqualTo(ScopeType.TENANT);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle multiple assignments for same group")
    void shouldHandleMultipleAssignmentsForSameGroup() {
      UUID groupId = createTestGroup();
      UUID role1Id = createTestRole("role1");
      UUID role2Id = createTestRole("role2");

      AssignmentInputDTO input1 = new AssignmentInputDTO();
      input1.setGroupId(groupId);
      input1.setRoleId(role1Id);
      input1.setScopeType(ScopeType.TENANT);

      // TODO v2.1: switch back to DATASPACE scope
      // AssignmentInputDTO input2 = new AssignmentInputDTO();
      // input2.setGroupId(groupId);
      // input2.setRoleId(role2Id);
      // input2.setScopeType(ScopeType.DATASPACE);
      // input2.setScopeId(createTestDataSpace());
      AssignmentInputDTO input2 = new AssignmentInputDTO();
      input2.setGroupId(groupId);
      input2.setRoleId(role2Id);
      input2.setScopeType(ScopeType.DATASET);
      input2.setScopeId(createTestDataSet(createTestDataSpace()));

      ResponseEntity<AssignmentOutputDTO> response1 = performCreate(input1);
      ResponseEntity<AssignmentOutputDTO> response2 = performCreate(input2);

      assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle null scope ID for global scope")
    void shouldHandleNullScopeIdForGlobalScope() {
      AssignmentInputDTO input = createValidInput();
      input.setScopeType(ScopeType.TENANT);
      input.setScopeId(null);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
  }

  @Override
  @Test
  @DisplayName("Should return INTERNAL_SERVER_ERROR when PATCH due to unsupported operation")
  void shouldRejectPatchThatResultsInInvalidEntity() {
    UUID id = createTestEntity();
    Map<String, Object> patchBody =
        objectMapper.convertValue(createInvalidInput(), new TypeReference<>() {});

    ResponseEntity<AssignmentOutputDTO> response = performPatch(id, patchBody);

    assertThat(response.getStatusCode())
        .as("PATCH producing an invalid entity should return INTERNAL_SERVER_ERROR")
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
