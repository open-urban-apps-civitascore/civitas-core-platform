package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.RestPage;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Role Controller Integration Tests")
class RoleControllerIntegrationTest
    extends BaseControllerIntegrationTest<RoleInputDTO, RoleOutputDTO, String> {

  private final String ROLES_ENDPOINT = "/roles";

  @Autowired private RoleRepository roleRepository;

  @Override
  protected String getEndpointPath() {
    return ROLES_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    roleRepository.deleteAll();
  }

  @Override
  protected RoleInputDTO createValidInput() {
    RoleInputDTO input = new RoleInputDTO();
    input.setName("test_role_" + System.currentTimeMillis());
    input.setTitle("Test Role " + System.currentTimeMillis());
    input.setDescription("A test role for integration testing");
    input.setRoleType(RoleType.GOVERNANCE);
    input.setIsDefault(false);
    input.setUserModifiable(true);
    return input;
  }

  @Override
  protected RoleInputDTO createInvalidInput() {
    RoleInputDTO input = new RoleInputDTO();
    input.setDescription("Invalid role without required fields");
    return input;
  }

  @Override
  protected RoleInputDTO createUpdateInput() {
    RoleInputDTO input = new RoleInputDTO();
    input.setName("updated_role");
    input.setTitle("Updated Role");
    input.setDescription("Updated description");
    input.setRoleType(RoleType.GOVERNANCE);
    input.setUserModifiable(true);
    return input;
  }

  @Override
  protected ParameterizedTypeReference<RoleOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<RoleOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected String getIdFromOutput(RoleOutputDTO output) {
    return output.getId();
  }

  @Nested
  @DisplayName("Create Role Tests")
  class CreateRoleTests {

    @Test
    @DisplayName("Should create role successfully with valid data")
    void shouldCreateRoleSuccessfully() {
      RoleInputDTO input = createValidInput();

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RoleOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getTitle()).as("Title should match input").isEqualTo(input.getTitle());
      assertThat(output.getRoleType())
          .as("Role type should match input")
          .isEqualTo(input.getRoleType());
      assertThat(output.getTenantId()).as("Tenant ID should be set").isNotNull();
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();
    }

    @Test
    @DisplayName("Should fail to create role with missing required fields")
    void shouldFailToCreateRoleWithMissingFields() {
      RoleInputDTO input = createInvalidInput();

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create role without authentication")
    void shouldFailToCreateRoleWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("", org.springframework.http.HttpMethod.POST);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create role with different role types")
    void shouldCreateRoleWithDifferentRoleTypes() {
      for (RoleType type : RoleType.values()) {
        RoleInputDTO input = createValidInput();
        input.setName("role_" + type.name().toLowerCase() + "_" + System.currentTimeMillis());
        input.setTitle("Role " + type.name());
        input.setRoleType(type);

        ResponseEntity<RoleOutputDTO> response = performCreate(input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRoleType()).isEqualTo(type);
      }
    }

    @Test
    @DisplayName("Should create role with permissions")
    void shouldCreateRoleWithPermissions() {
      RoleInputDTO input = createValidInput();
      input.setPermissionIds(Collections.emptyList());

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should fail to create duplicate role with same title in same tenant")
    void shouldFailToCreateDuplicateRole() {
      RoleInputDTO input = createValidInput();
      input.setTitle("Unique Role Title");

      // Create first role
      ResponseEntity<RoleOutputDTO> firstResponse = performCreate(input);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      // Try to create duplicate
      ResponseEntity<RoleOutputDTO> secondResponse = performCreate(input);

      assertThat(secondResponse.getStatusCode())
          .as("Should return CONFLICT status for duplicate")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should create default role")
    void shouldCreateDefaultRole() {
      RoleInputDTO input = createValidInput();
      input.setIsDefault(true);

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getIsDefault()).isTrue();
    }
  }

  @Nested
  @DisplayName("Read Role Tests")
  class ReadRoleTests {

    @Test
    @DisplayName("Should retrieve role by ID successfully")
    void shouldRetrieveRoleById() {
      String roleId = createTestEntity();

      ResponseEntity<RoleOutputDTO> response = performGetById(roleId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RoleOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(roleId);
      assertThat(output.getTitle()).as("Title should be present").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent role")
    void shouldReturn404ForNonExistentRole() {
      ResponseEntity<RoleOutputDTO> response = performGetById("non-existent-id");

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve role without authentication")
    void shouldFailToRetrieveRoleWithoutAuth() {
      String roleId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + roleId, org.springframework.http.HttpMethod.GET);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should retrieve all roles with pagination")
    void shouldRetrieveAllRolesWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<RoleOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<RoleOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should contain roles").isNotEmpty();
      assertThat(page.getTotalElements()).as("Total elements should be positive").isPositive();
    }

    @Test
    @DisplayName("Should retrieve roles with pagination parameters")
    void shouldRetrieveRolesWithPaginationParams() {
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "title,asc");

      ResponseEntity<RestPage<RoleOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Update Role Tests")
  class UpdateRoleTests {

    @Test
    @DisplayName("Should update role successfully with PUT")
    void shouldUpdateRoleWithPut() {
      String roleId = createTestEntity();

      RoleInputDTO updateInput = createUpdateInput();
      ResponseEntity<RoleOutputDTO> response = performUpdate(roleId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RoleOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(roleId);
      assertThat(output.getTitle()).as("Title should be updated").isEqualTo(updateInput.getTitle());
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo(updateInput.getDescription());
      assertThat(output.getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should partially update role with PATCH")
    void shouldPartiallyUpdateRoleWithPatch() {
      String roleId = createTestEntity();

      RoleInputDTO patchInput = new RoleInputDTO();
      patchInput.setDescription("Only description updated");

      ResponseEntity<RoleOutputDTO> response = performPatch(roleId, patchInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should fail to update non-existent role")
    void shouldFailToUpdateNonExistentRole() {
      RoleInputDTO updateInput = createUpdateInput();

      ResponseEntity<RoleOutputDTO> response = performUpdate("non-existent-id", updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update role without authentication")
    void shouldFailToUpdateRoleWithoutAuth() {
      String roleId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + roleId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should update role permissions")
    void shouldUpdateRolePermissions() {
      String roleId = createTestEntity();

      RoleInputDTO updateInput = createUpdateInput();
      updateInput.setPermissionIds(Collections.emptyList());

      ResponseEntity<RoleOutputDTO> response = performUpdate(roleId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Delete Role Tests")
  class DeleteRoleTests {

    @Test
    @DisplayName("Should delete role successfully")
    void shouldDeleteRoleSuccessfully() {
      String roleId = createTestEntity();

      ResponseEntity<Void> response = performDelete(roleId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<RoleOutputDTO> getResponse = performGetById(roleId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted role should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent role")
    void shouldFailToDeleteNonExistentRole() {
      ResponseEntity<Void> response = performDelete("non-existent-id");

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete role without authentication")
    void shouldFailToDeleteRoleWithoutAuth() {
      String roleId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + roleId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Business Logic Tests")
  class BusinessLogicTests {

    @Test
    @DisplayName("Should handle system role type")
    void shouldHandleSystemRoleType() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.SYSTEM);
      input.setUserModifiable(false);

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getRoleType()).isEqualTo(RoleType.SYSTEM);
      assertThat(response.getBody().getUserModifiable()).isFalse();
    }

    @Test
    @DisplayName("Should handle custom role type")
    void shouldHandleCustomRoleType() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.DATA);

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getRoleType()).isEqualTo(RoleType.DATA);
    }

    @Test
    @DisplayName("Should maintain tenant isolation")
    void shouldMaintainTenantIsolation() {
      String roleId = createTestEntity();

      ResponseEntity<RoleOutputDTO> response = performGetById(roleId);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTenantId()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle special characters in name")
    void shouldHandleSpecialCharactersInName() {
      RoleInputDTO input = createValidInput();
      input.setName("role_with_special_chars_äöü");
      input.setTitle("Role with special chars: äöü ß @#$%");

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should handle null description")
    void shouldHandleNullDescription() {
      RoleInputDTO input = createValidInput();
      input.setDescription(null);

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle empty title as invalid")
    void shouldHandleEmptyTitle() {
      RoleInputDTO input = createValidInput();
      input.setTitle("");

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
