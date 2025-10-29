package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.GroupOutputDTO;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.util.RestPage;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Assignment Controller Integration Tests")
class AssignmentControllerIntegrationTest
    extends BaseControllerIntegrationTest<AssignmentInputDTO, AssignmentOutputDTO, String> {

  private final String ASSIGNMENTS_ENDPOINT = "/assignments";

  @Autowired private AssignmentRepository assignmentRepository;

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
    String groupId = createTestGroup();
    String roleId = createTestRole();

    AssignmentInputDTO input = new AssignmentInputDTO();
    input.setGroupId(groupId);
    input.setRoleId(roleId);
    input.setScopeType(ScopeType.TENANT);
    input.setIsInherited(false);
    return input;
  }

  @Override
  protected AssignmentInputDTO createInvalidInput() {
    return new AssignmentInputDTO();
  }

  @Override
  protected AssignmentInputDTO createUpdateInput() {
    String groupId = createTestGroup();
    String roleId = createTestRole();

    AssignmentInputDTO input = new AssignmentInputDTO();
    input.setGroupId(groupId);
    input.setRoleId(roleId);
    input.setScopeType(ScopeType.TENANT);
    input.setIsInherited(false);
    return input;
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
  protected String getIdFromOutput(AssignmentOutputDTO output) {
    return output.getId();
  }

  private String createTestGroup() {
    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setTitle("Test Group " + System.currentTimeMillis());
    groupInput.setDescription("Test group for assignment");

    HttpHeaders headers = createAuthHeaders();
    HttpEntity<GroupInputDTO> request = new HttpEntity<>(groupInput, headers);
    ResponseEntity<GroupOutputDTO> response =
        restTemplate.exchange(
            "/groups",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<GroupOutputDTO>() {});

    if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
      return response.getBody().getId();
    }
    throw new IllegalStateException("Failed to create test group");
  }

  private String createTestRole() {
    RoleInputDTO roleInput = new RoleInputDTO();
    roleInput.setName("test_role_" + System.currentTimeMillis());
    roleInput.setTitle("Test Role " + System.currentTimeMillis());
    roleInput.setDescription("Test role for assignment");
    roleInput.setRoleType(RoleType.DATA);
    roleInput.setUserModifiable(true);
    roleInput.setIsDefault(true);

    HttpHeaders headers = createAuthHeaders();
    HttpEntity<RoleInputDTO> request = new HttpEntity<>(roleInput, headers);
    ResponseEntity<RoleOutputDTO> response =
        restTemplate.exchange(
            "/roles", HttpMethod.POST, request, new ParameterizedTypeReference<RoleOutputDTO>() {});

    if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
      return response.getBody().getId();
    }
    throw new IllegalStateException("Failed to create test role");
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
      assertThat(output.getTenantId()).as("Tenant ID should be set").isNotNull();
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

    @Test
    @DisplayName("Should create assignment with different scope types")
    void shouldCreateAssignmentWithDifferentScopeTypes() {
      for (ScopeType scopeType : ScopeType.values()) {
        String groupId = createTestGroup();
        String roleId = createTestRole();

        AssignmentInputDTO input = new AssignmentInputDTO();
        input.setGroupId(groupId);
        input.setRoleId(roleId);
        input.setScopeType(scopeType);
        input.setScopeId("testId-" + scopeType.name());
        input.setIsInherited(false);

        ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getScopeType()).isEqualTo(scopeType);
      }
    }

    @Test
    @DisplayName("Should create assignment with scope ID")
    void shouldCreateAssignmentWithScopeId() {
      AssignmentInputDTO input = createValidInput();
      input.setScopeType(ScopeType.DATASPACE);
      input.setScopeId("dataspace-123");

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getScopeId()).isEqualTo("dataspace-123");
    }

    @Test
    @DisplayName("Should create inherited assignment")
    void shouldCreateInheritedAssignment() {
      AssignmentInputDTO input = createValidInput();
      input.setIsInherited(true);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getIsInherited()).isTrue();
    }

    @Test
    @DisplayName("Should create assignment with metadata")
    void shouldCreateAssignmentWithMetadata() {
      AssignmentInputDTO input = createValidInput();
      input.setMetadata("{\"key\": \"value\"}");

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should fail to create assignment with non-existent group")
    void shouldFailToCreateAssignmentWithNonExistentGroup() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId("non-existent-group");
      input.setRoleId(createTestRole());
      input.setScopeType(ScopeType.TENANT);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND or BAD_REQUEST status")
          .isIn(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create assignment with non-existent role")
    void shouldFailToCreateAssignmentWithNonExistentRole() {
      AssignmentInputDTO input = new AssignmentInputDTO();
      input.setGroupId(createTestGroup());
      input.setRoleId("non-existent-role");
      input.setScopeType(ScopeType.TENANT);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND or BAD_REQUEST status")
          .isIn(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Read Assignment Tests")
  class ReadAssignmentTests {

    @Test
    @DisplayName("Should retrieve assignment by ID successfully")
    void shouldRetrieveAssignmentById() {
      String assignmentId = createTestEntity();

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
      ResponseEntity<AssignmentOutputDTO> response = performGetById("non-existent-id");

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve assignment without authentication")
    void shouldFailToRetrieveAssignmentWithoutAuth() {
      String assignmentId = createTestEntity();

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
  @DisplayName("Update Assignment Tests")
  class UpdateAssignmentTests {

    @Test
    @DisplayName("Should update assignment successfully with PUT")
    void shouldUpdateAssignmentWithPut() {
      String assignmentId = createTestEntity();

      AssignmentInputDTO updateInput = createUpdateInput();
      ResponseEntity<AssignmentOutputDTO> response = performUpdate(assignmentId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      AssignmentOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(assignmentId);
      assertThat(output.getScopeType())
          .as("Scope type should be updated")
          .isEqualTo(updateInput.getScopeType());
      assertThat(output.getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should partially update assignment with PATCH")
    void shouldPartiallyUpdateAssignmentWithPatch() {
      String assignmentId = createTestEntity();

      AssignmentInputDTO patchInput = new AssignmentInputDTO();
      patchInput.setIsInherited(true);

      ResponseEntity<AssignmentOutputDTO> response = performPatch(assignmentId, patchInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should fail to update non-existent assignment")
    void shouldFailToUpdateNonExistentAssignment() {
      AssignmentInputDTO updateInput = createUpdateInput();

      ResponseEntity<AssignmentOutputDTO> response = performUpdate("non-existent-id", updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update assignment without authentication")
    void shouldFailToUpdateAssignmentWithoutAuth() {
      String assignmentId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + assignmentId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should update assignment scope")
    void shouldUpdateAssignmentScope() {
      String assignmentId = createTestEntity();

      AssignmentInputDTO updateInput = createUpdateInput();
      updateInput.setScopeType(ScopeType.DATASET);
      updateInput.setScopeId("dataset-456");

      ResponseEntity<AssignmentOutputDTO> response = performUpdate(assignmentId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getScopeType()).isEqualTo(ScopeType.DATASET);
      assertThat(response.getBody().getScopeId()).isEqualTo("dataset-456");
    }
  }

  @Nested
  @DisplayName("Delete Assignment Tests")
  class DeleteAssignmentTests {

    @Test
    @DisplayName("Should delete assignment successfully")
    void shouldDeleteAssignmentSuccessfully() {
      String assignmentId = createTestEntity();

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
      ResponseEntity<Void> response = performDelete("non-existent-id");

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete assignment without authentication")
    void shouldFailToDeleteAssignmentWithoutAuth() {
      String assignmentId = createTestEntity();

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
    @DisplayName("Should handle assignment with parent assignment")
    void shouldHandleAssignmentWithParent() {
      String parentId = createTestEntity();

      AssignmentInputDTO childInput = createValidInput();
      childInput.setParentAssignmentId(parentId);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(childInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should maintain tenant isolation")
    void shouldMaintainTenantIsolation() {
      String assignmentId = createTestEntity();

      ResponseEntity<AssignmentOutputDTO> response = performGetById(assignmentId);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTenantId()).isNotNull();
    }

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
      String groupId = createTestGroup();
      String role1Id = createTestRole();
      String role2Id = createTestRole();

      AssignmentInputDTO input1 = new AssignmentInputDTO();
      input1.setGroupId(groupId);
      input1.setRoleId(role1Id);
      input1.setIsInherited(false);
      input1.setScopeId("test-tenant" + System.currentTimeMillis());

      input1.setScopeType(ScopeType.TENANT);

      AssignmentInputDTO input2 = new AssignmentInputDTO();
      input2.setGroupId(groupId);
      input2.setRoleId(role2Id);
      input2.setScopeType(ScopeType.DATASPACE);
      input2.setScopeId("test-dataspace" + System.currentTimeMillis());
      input2.setIsInherited(false);

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
}
