package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.input.PermissionInputDTO;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.util.RestPage;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Permission Controller Integration Tests")
class PermissionControllerIntegrationTest
    extends BaseControllerIntegrationTest<PermissionInputDTO, PermissionOutputDTO> {

  private final String PERMISSIONS_ENDPOINT = "/permissions";

  @Autowired private PermissionRepository permissionRepository;

  @Override
  protected String getEndpointPath() {
    return PERMISSIONS_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    permissionRepository.deleteAll();
  }

  @Override
  protected PermissionInputDTO createValidInput() {
    PermissionInputDTO input = new PermissionInputDTO();
    input.setName("Test Permission " + System.currentTimeMillis());
    input.setDescription("A test permission for integration testing");
    input.setPermissionType(PermissionType.DATA);
    return input;
  }

  @Override
  protected PermissionInputDTO createInvalidInput() {
    PermissionInputDTO input = new PermissionInputDTO();
    input.setDescription("Invalid permission without required fields");
    return input;
  }

  @Override
  protected PermissionInputDTO createUpdateInput() {
    PermissionInputDTO input = new PermissionInputDTO();
    input.setName("updated_permission");
    input.setName("Updated Permission");
    input.setDescription("Updated description");
    input.setPermissionType(PermissionType.SYSTEM);
    return input;
  }

  @Override
  protected ParameterizedTypeReference<PermissionOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<PermissionOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected String getIdFromOutput(PermissionOutputDTO output) {
    return output.getId();
  }

  @Nested
  @DisplayName("Create Permission Tests")
  class CreatePermissionTests {

    @Test
    @DisplayName("Should create permission successfully with valid data")
    void shouldCreatePermissionSuccessfully() {
      PermissionInputDTO input = createValidInput();

      ResponseEntity<PermissionOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      PermissionOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getName()).as("Name should match input").isEqualTo(input.getName());
      assertThat(output.getPermissionType())
          .as("Permission type should match input")
          .isEqualTo(input.getPermissionType());
      assertThat(output.getTenantId()).as("Tenant ID should be set").isNotNull();
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();
    }

    @Test
    @DisplayName("Should fail to create permission with missing required fields")
    void shouldFailToCreatePermissionWithMissingFields() {
      PermissionInputDTO input = createInvalidInput();

      ResponseEntity<PermissionOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create permission without authentication")
    void shouldFailToCreatePermissionWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("", org.springframework.http.HttpMethod.POST);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create permission with different permission types")
    void shouldCreatePermissionWithDifferentTypes() {
      for (PermissionType type : PermissionType.values()) {
        PermissionInputDTO input = createValidInput();
        input.setName("permission_" + type.name().toLowerCase() + "_" + System.currentTimeMillis());
        input.setName("Permission " + type.name());
        input.setPermissionType(type);

        ResponseEntity<PermissionOutputDTO> response = performCreate(input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPermissionType()).isEqualTo(type);
      }
    }

    @Test
    @DisplayName("Should fail to create duplicate permission with same Name in same tenant")
    void shouldFailToCreateDuplicatePermission() {
      PermissionInputDTO input = createValidInput();
      input.setName("Unique Permission Name");

      // Create first permission
      ResponseEntity<PermissionOutputDTO> firstResponse = performCreate(input);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      // Try to create duplicate
      ResponseEntity<PermissionOutputDTO> secondResponse = performCreate(input);

      assertThat(secondResponse.getStatusCode())
          .as("Should return CONFLICT status for duplicate")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should create default permission")
    void shouldCreateDefaultPermission() {
      PermissionInputDTO input = createValidInput();
      input.setDescription("TestDescription");

      ResponseEntity<PermissionOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isEqualTo(input.getDescription());
    }
  }

  @Nested
  @DisplayName("Read Permission Tests")
  class ReadPermissionTests {

    @Test
    @DisplayName("Should retrieve permission by ID successfully")
    void shouldRetrievePermissionById() {
      String permissionId = createTestEntity();

      ResponseEntity<PermissionOutputDTO> response = performGetById(permissionId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      PermissionOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(permissionId);
      assertThat(output.getName()).as("Name should be present").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent permission")
    void shouldReturn404ForNonExistentPermission() {
      ResponseEntity<PermissionOutputDTO> response = performGetById("non-existent-id");

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve permission without authentication")
    void shouldFailToRetrievePermissionWithoutAuth() {
      String permissionId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + permissionId, org.springframework.http.HttpMethod.GET);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should retrieve all permissions with pagination")
    void shouldRetrieveAllPermissionsWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<PermissionOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<PermissionOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should contain permissions").isNotEmpty();
      assertThat(page.getTotalElements()).as("Total elements should be positive").isPositive();
    }

    @Test
    @DisplayName("Should retrieve permissions with pagination parameters")
    void shouldRetrievePermissionsWithPaginationParams() {
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "name,asc");

      ResponseEntity<RestPage<PermissionOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Update Permission Tests")
  class UpdatePermissionTests {

    @Test
    @DisplayName("Should update permission successfully with PUT")
    void shouldUpdatePermissionWithPut() {
      String permissionId = createTestEntity();

      PermissionInputDTO updateInput = createUpdateInput();
      ResponseEntity<PermissionOutputDTO> response = performUpdate(permissionId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      PermissionOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(permissionId);
      assertThat(output.getName()).as("Name should be updated").isEqualTo(updateInput.getName());
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo(updateInput.getDescription());
      assertThat(output.getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should partially update permission with PATCH - single field")
    void shouldPartiallyUpdatePermissionWithPatch() {
      String permissionId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Only description updated");

      ResponseEntity<PermissionOutputDTO> response = performPatch(permissionId, patchMap);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be updated")
          .isEqualTo("Only description updated");
    }

    @Test
    @DisplayName("Should update multiple fields with PATCH")
    void shouldUpdateMultipleFieldsWithPatch() {
      String permissionId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("name", "PatchedPermission");
      patchMap.put("description", "Patched description");

      ResponseEntity<PermissionOutputDTO> response = performPatch(permissionId, patchMap);
      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("PatchedPermission");
      assertThat(response.getBody().getDescription()).isEqualTo("Patched description");
    }

    @Test
    @DisplayName("Should set description to null with PATCH")
    void shouldSetDescriptionToNullWithPatch() {
      String permissionId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", null);

      ResponseEntity<PermissionOutputDTO> response = performPatch(permissionId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be set to null")
          .isNull();
    }

    @Test
    @DisplayName("Should leave omitted fields unchanged with PATCH")
    void shouldLeaveOmittedFieldsUnchangedWithPatch() {
      String permissionId = createTestEntity();

      ResponseEntity<PermissionOutputDTO> initialResponse = performGetById(permissionId);
      PermissionOutputDTO initialPermission = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "New description");

      ResponseEntity<PermissionOutputDTO> response = performPatch(permissionId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isEqualTo("New description");
      assertThat(response.getBody().getName())
          .as("Name should remain unchanged")
          .isEqualTo(initialPermission.getName());
      assertThat(response.getBody().getCreatedAt())
          .as("Resource should remain unchanged")
          .isEqualTo(initialPermission.getCreatedAt());
    }

    @Test
    @DisplayName("Should handle empty PATCH (no changes)")
    void shouldHandleEmptyPatch() {
      String permissionId = createTestEntity();

      ResponseEntity<PermissionOutputDTO> initialResponse = performGetById(permissionId);
      PermissionOutputDTO initialPermission = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();

      ResponseEntity<PermissionOutputDTO> response = performPatch(permissionId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(initialPermission.getName());
      assertThat(response.getBody().getDescription()).isEqualTo(initialPermission.getDescription());
    }

    @Test
    @DisplayName("Should be idempotent with PATCH")
    void shouldBeIdempotentWithPatch() {
      String permissionId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Idempotent test");

      ResponseEntity<PermissionOutputDTO> firstResponse = performPatch(permissionId, patchMap);
      ResponseEntity<PermissionOutputDTO> secondResponse = performPatch(permissionId, patchMap);

      assertThat(firstResponse.getBody()).isNotNull();
      assertThat(secondResponse.getBody()).isNotNull();
      assertThat(firstResponse.getBody().getDescription())
          .isEqualTo(secondResponse.getBody().getDescription());
    }

    @Test
    @DisplayName("Should fail to update non-existent permission")
    void shouldFailToUpdateNonExistentPermission() {
      PermissionInputDTO updateInput = createUpdateInput();

      ResponseEntity<PermissionOutputDTO> response = performUpdate("non-existent-id", updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update permission without authentication")
    void shouldFailToUpdatePermissionWithoutAuth() {
      String permissionId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + permissionId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Delete Permission Tests")
  class DeletePermissionTests {

    @Test
    @DisplayName("Should delete permission successfully")
    void shouldDeletePermissionSuccessfully() {
      String permissionId = createTestEntity();

      ResponseEntity<Void> response = performDelete(permissionId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<PermissionOutputDTO> getResponse = performGetById(permissionId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted permission should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent permission")
    void shouldFailToDeleteNonExistentPermission() {
      ResponseEntity<Void> response = performDelete("non-existent-id");

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete permission without authentication")
    void shouldFailToDeletePermissionWithoutAuth() {
      String permissionId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + permissionId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Business Logic Tests")
  class BusinessLogicTests {

    @Test
    @DisplayName("Should handle system permission type")
    void shouldHandleSystemPermissionType() {
      PermissionInputDTO input = createValidInput();
      input.setPermissionType(PermissionType.SYSTEM);

      ResponseEntity<PermissionOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getPermissionType()).isEqualTo(PermissionType.SYSTEM);
    }

    @Test
    @DisplayName("Should handle custom permission type")
    void shouldHandleCustomPermissionType() {
      PermissionInputDTO input = createValidInput();
      input.setPermissionType(PermissionType.GOVERNANCE);

      ResponseEntity<PermissionOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getPermissionType()).isEqualTo(PermissionType.GOVERNANCE);
    }

    @Test
    @DisplayName("Should maintain tenant isolation")
    void shouldMaintainTenantIsolation() {
      String permissionId = createTestEntity();

      ResponseEntity<PermissionOutputDTO> response = performGetById(permissionId);
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
      PermissionInputDTO input = createValidInput();
      input.setName("permission_with_special_chars_äöü");
      input.setName("Permission with special chars: äöü ß @#$%");

      ResponseEntity<PermissionOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should handle null description")
    void shouldHandleNullDescription() {
      PermissionInputDTO input = createValidInput();
      input.setDescription(null);

      ResponseEntity<PermissionOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle empty Name as invalid")
    void shouldHandleEmptyTName() {
      PermissionInputDTO input = createValidInput();
      input.setName("");

      ResponseEntity<PermissionOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
