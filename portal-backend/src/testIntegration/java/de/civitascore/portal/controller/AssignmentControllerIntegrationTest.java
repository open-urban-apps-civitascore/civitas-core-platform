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
import java.util.UUID;
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
    extends BaseControllerIntegrationTest<AssignmentInputDTO, AssignmentOutputDTO> {

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
    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("Test Group " + System.currentTimeMillis());
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

  private UUID createTestRole() {
    RoleInputDTO roleInput = new RoleInputDTO();
    roleInput.setName("test_role_" + System.currentTimeMillis());
    roleInput.setName("Test Role " + System.currentTimeMillis());
    roleInput.setDescription("Test role for assignment");
    roleInput.setRoleType(RoleType.DATA);

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
        UUID groupId = createTestGroup();
        UUID roleId = createTestRole();

        AssignmentInputDTO input = new AssignmentInputDTO();
        input.setGroupId(groupId);
        input.setRoleId(roleId);
        input.setScopeType(scopeType);
        UUID scopeID = UUID.randomUUID();
        input.setScopeId(scopeID);

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
      UUID scopeID = UUID.randomUUID();
      input.setScopeId(scopeID);

      ResponseEntity<AssignmentOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getScopeId()).isEqualTo(scopeID);
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
      UUID role1Id = createTestRole();
      UUID role2Id = createTestRole();

      AssignmentInputDTO input1 = new AssignmentInputDTO();
      input1.setGroupId(groupId);
      input1.setRoleId(role1Id);
      UUID scopeID = UUID.randomUUID();
      input1.setScopeId(scopeID);

      input1.setScopeType(ScopeType.TENANT);

      AssignmentInputDTO input2 = new AssignmentInputDTO();
      input2.setGroupId(groupId);
      input2.setRoleId(role2Id);
      input2.setScopeType(ScopeType.DATASPACE);
      UUID scopeID2 = UUID.randomUUID();
      input2.setScopeId(scopeID2);

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
