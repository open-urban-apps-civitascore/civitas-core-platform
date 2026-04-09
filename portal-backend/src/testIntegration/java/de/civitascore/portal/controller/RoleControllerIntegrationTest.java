package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.util.RestPage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Role Controller Integration Tests")
class RoleControllerIntegrationTest
    extends BaseControllerIntegrationTest<RoleInputDTO, RoleOutputDTO> {

  private final String ROLES_ENDPOINT = "/roles";

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private PermissionRepository permissionRepository;

  @Override
  protected String getEndpointPath() {
    return ROLES_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  @Override
  protected RoleInputDTO createValidInput() {
    RoleInputDTO input = new RoleInputDTO();
    input.setName("test_role_" + UUID.randomUUID().toString().substring(0, 8));
    input.setDescription("A test role for integration testing");
    input.setRoleType(RoleType.DATA);
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
    input.setName("Updated Role");
    input.setDescription("Updated description");
    input.setRoleType(RoleType.DATA);
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
  protected UUID getIdFromOutput(RoleOutputDTO output) {
    return output.getId();
  }

  private List<UUID> getPermissionIdsByType(PermissionType type) {
    List<UUID> ids =
        permissionRepository.findAll().stream()
            .filter(p -> p.getPermissionType() == type)
            .map(Permission::getId)
            .limit(2)
            .toList();
    assertThat(ids).as("Expected seeded permissions of type " + type).isNotEmpty();
    return ids;
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
      assertThat(output.getName()).as("Name should match input").isEqualTo(input.getName());
      assertThat(output.getRoleType())
          .as("Role type should match input")
          .isEqualTo(input.getRoleType());
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
        input.setName(
            "role_"
                + type.name().toLowerCase()
                + "_"
                + UUID.randomUUID().toString().substring(0, 8));
        input.setName("Role " + type.name());
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
    @DisplayName("Should fail to create duplicate role with same Name in same tenant")
    void shouldFailToCreateDuplicateRole() {
      RoleInputDTO input = createValidInput();
      input.setName("Unique Role Name");

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
    void shouldCreateRole() {
      RoleInputDTO input = createValidInput();
      input.setName("TestName");

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(input.getName());
    }
  }

  @Nested
  @DisplayName("Read Role Tests")
  class ReadRoleTests {

    @Test
    @DisplayName("Should retrieve role by ID successfully")
    void shouldRetrieveRoleById() {
      UUID roleId = createTestEntity();

      ResponseEntity<RoleOutputDTO> response = performGetById(roleId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RoleOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(roleId);
      assertThat(output.getName()).as("Name should be present").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent role")
    void shouldReturn404ForNonExistentRole() {
      ResponseEntity<RoleOutputDTO> response = performGetById(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve role without authentication")
    void shouldFailToRetrieveRoleWithoutAuth() {
      UUID roleId = createTestEntity();

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
      UUID id = createTestEntity();
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "name,asc");

      ResponseEntity<RestPage<RoleOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should return group count of 0 for new role")
    void shouldReturnCorrectGroupCountForRole() {
      UUID roleId = createTestEntity();

      ResponseEntity<RoleOutputDTO> response = performGetById(roleId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      RoleOutputDTO role = response.getBody();
      assertThat(role.getGroupCount()).as("Initial group count should be zero").isEqualTo(0);
    }

    // TODO: implement in V2.1
    // @Test
    // @DisplayName("Should return correct group count after assignments")
    // void shouldReturnCorrectGroupCountAfterAssignments() {
    //   UUID roleId = createTestEntity();
    //
    //   // Create groups
    //   Triple<Group, Group, Group> groups = createGroupHierarchy();
    //
    //   AssignmentInputDTO assignmentInput = new AssignmentInputDTO();
    //   assignmentInput.setRoleId(roleId);
    //   assignmentInput.setGroupId(groups.getLeft().getId());
    //   assignmentInput.setScopeType(ScopeType.TENANT);
    //   assignmentService.create(assignmentInput);
    //
    //   assignmentInput.setGroupId(groups.getMiddle().getId());
    //   assignmentService.create(assignmentInput);
    //
    //   // Retrieve role and verify group count
    //   ResponseEntity<RoleOutputDTO> response = performGetById(roleId);
    //
    //   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    //   assertThat(response.getBody()).isNotNull();
    //
    //   RoleOutputDTO role = response.getBody();
    //   assertThat(role.getGroupCount())
    //       .as("Group count should reflect assigned groups")
    //       .isEqualTo(3);
    // }

    @Test
    @DisplayName("Should return user count of 0 for new role")
    void shouldReturnCorrectUserCountForRole() {
      UUID roleId = createTestEntity();

      ResponseEntity<RoleOutputDTO> response = performGetById(roleId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      RoleOutputDTO role = response.getBody();
      assertThat(role.getUserCount()).as("Initial user count should be zero").isEqualTo(0);
    }

    // TODO: implement in V2.1
    // @Test
    // @DisplayName("Should return correct user count after assignments")
    // void shouldReturnCorrectUserCountAfterAssignments() {
    //   UUID roleId = createTestEntity();
    //
    //   // Create groups
    //   Triple<Group, Group, Group> groups = createGroupHierarchy();
    //
    //   AssignmentInputDTO assignmentInput = new AssignmentInputDTO();
    //   assignmentInput.setRoleId(roleId);
    //   assignmentInput.setGroupId(groups.getLeft().getId());
    //   assignmentInput.setScopeType(ScopeType.TENANT);
    //   assignmentService.create(assignmentInput);
    //
    //   assignmentInput.setGroupId(groups.getMiddle().getId());
    //   assignmentService.create(assignmentInput);
    //
    //   // Retrieve role and verify user count
    //   ResponseEntity<RoleOutputDTO> response = performGetById(roleId);
    //
    //   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    //   assertThat(response.getBody()).isNotNull();
    //
    //   RoleOutputDTO role = response.getBody();
    //   assertThat(role.getUserCount()).as("User count should reflect assigned
    // users").isEqualTo(3);
    // }

    // TODO: implement in V2.1
    // private Triple<Group, Group, Group> createGroupHierarchy() {
    //   UserInputDTO userInput = new UserInputDTO();
    //   userInput.setFirstName("firstName");
    //   userInput.setLastName("lastName");
    //   userInput.setEmail("user1@test.de");
    //   userInput.setActive(true);
    //   User user1 = userService.create(userInput);
    //
    //   userInput.setEmail("user2@test.de");
    //   User user2 = userService.create(userInput);
    //
    //   userInput.setEmail("user3@test.de");
    //   User user3 = userService.create(userInput);
    //
    //   GroupInputDTO parentGroupInput = new GroupInputDTO();
    //   parentGroupInput.setName("Parent Group");
    //   parentGroupInput.setMemberIds(List.of(user1.getId()));
    //   Group parentGroup = groupService.create(parentGroupInput);
    //
    //   GroupInputDTO childGroupInput1 = new GroupInputDTO();
    //   childGroupInput1.setName("Child Group 1");
    //   childGroupInput1.setParentGroupId(parentGroup.getId());
    //   childGroupInput1.setMemberIds(List.of(user1.getId()));
    //   Group childGroup1 = groupService.create(childGroupInput1);
    //
    //   GroupInputDTO childGroupInput2 = new GroupInputDTO();
    //   childGroupInput2.setName("Child Group 2");
    //   childGroupInput2.setParentGroupId(parentGroup.getId());
    //   childGroupInput2.setMemberIds(List.of(user2.getId(), user3.getId()));
    //   Group childGroup2 = groupService.create(childGroupInput2);
    //
    //   return Triple.of(parentGroup, childGroup1, childGroup2);
    // }
  }

  @Nested
  @DisplayName("Update Role Tests")
  class UpdateRoleTests {

    @Test
    @DisplayName("Should update role successfully with PUT")
    void shouldUpdateRoleWithPut() {
      UUID roleId = createTestEntity();

      RoleInputDTO updateInput = createUpdateInput();
      ResponseEntity<RoleOutputDTO> response = performUpdate(roleId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RoleOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(roleId);
      assertThat(output.getName()).as("Name should be updated").isEqualTo(updateInput.getName());
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo(updateInput.getDescription());
      assertThat(output.getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should partially update role with PATCH - single field")
    void shouldPartiallyUpdateRoleWithPatch() {
      UUID roleId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Only description updated");

      ResponseEntity<RoleOutputDTO> response = performPatch(roleId, patchMap);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be updated")
          .isEqualTo("Only description updated");
    }

    @Test
    @DisplayName("Should update multiple fields with PATCH")
    void shouldUpdateMultipleFieldsWithPatch() {
      UUID roleId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("name", "PatchedRole");
      patchMap.put("description", "Patched description");

      ResponseEntity<RoleOutputDTO> response = performPatch(roleId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("PatchedRole");
      assertThat(response.getBody().getDescription()).isEqualTo("Patched description");
    }

    @Test
    @DisplayName("Should set description to null with PATCH")
    void shouldSetDescriptionToNullWithPatch() {
      UUID roleId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", null);

      ResponseEntity<RoleOutputDTO> response = performPatch(roleId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be set to null")
          .isNull();
    }

    @Test
    @DisplayName("Should leave omitted fields unchanged with PATCH")
    void shouldLeaveOmittedFieldsUnchangedWithPatch() {
      UUID roleId = createTestEntity();

      ResponseEntity<RoleOutputDTO> initialResponse = performGetById(roleId);
      RoleOutputDTO initialRole = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "New description");

      ResponseEntity<RoleOutputDTO> response = performPatch(roleId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isEqualTo("New description");
      assertThat(response.getBody().getName())
          .as("Name should remain unchanged")
          .isEqualTo(initialRole.getName());
    }

    @Test
    @DisplayName("Should handle empty PATCH (no changes)")
    void shouldHandleEmptyPatch() {
      UUID roleId = createTestEntity();

      ResponseEntity<RoleOutputDTO> initialResponse = performGetById(roleId);
      RoleOutputDTO initialRole = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();

      ResponseEntity<RoleOutputDTO> response = performPatch(roleId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(initialRole.getName());
      assertThat(response.getBody().getDescription()).isEqualTo(initialRole.getDescription());
    }

    @Test
    @DisplayName("Should be idempotent with PATCH")
    void shouldBeIdempotentWithPatch() {
      UUID roleId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Idempotent test");

      ResponseEntity<RoleOutputDTO> firstResponse = performPatch(roleId, patchMap);
      ResponseEntity<RoleOutputDTO> secondResponse = performPatch(roleId, patchMap);

      assertThat(firstResponse.getBody()).isNotNull();
      assertThat(secondResponse.getBody()).isNotNull();
      assertThat(firstResponse.getBody().getDescription())
          .isEqualTo(secondResponse.getBody().getDescription());
    }

    @Test
    @DisplayName("Should fail to update non-existent role")
    void shouldFailToUpdateNonExistentRole() {
      RoleInputDTO updateInput = createUpdateInput();

      ResponseEntity<RoleOutputDTO> response = performUpdate(UUID.randomUUID(), updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update role without authentication")
    void shouldFailToUpdateRoleWithoutAuth() {
      UUID roleId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + roleId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should update role permissions")
    void shouldUpdateRolePermissions() {
      UUID roleId = createTestEntity();

      RoleInputDTO updateInput = createUpdateInput();
      updateInput.setPermissionIds(Collections.emptyList());

      ResponseEntity<RoleOutputDTO> response = performUpdate(roleId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should fail to update protected role")
    void shouldFailToUpdateProtectedRole() {
      RoleInputDTO input = createValidInput();
      input.setReadonly(true);

      ResponseEntity<RoleOutputDTO> createResponse = performCreate(input);

      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(createResponse.getBody()).isNotNull();

      UUID protectedRoleId = createResponse.getBody().getId();
      RoleInputDTO updateInput = createUpdateInput();

      ResponseEntity<RoleOutputDTO> updateResponse = performUpdate(protectedRoleId, updateInput);
      assertThat(updateResponse.getStatusCode())
          .as("Should return FORBIDDEN status when updating protected role")
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Nested
  @DisplayName("Delete Role Tests")
  class DeleteRoleTests {

    @Test
    @DisplayName("Should delete role successfully")
    void shouldDeleteRoleSuccessfully() {
      UUID roleId = createTestEntity();

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
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete role without authentication")
    void shouldFailToDeleteRoleWithoutAuth() {
      UUID roleId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + roleId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to delete protected role")
    void shouldFailToDeleteProtectedRole() {
      RoleInputDTO input = createValidInput();
      input.setReadonly(true);

      ResponseEntity<RoleOutputDTO> createResponse = performCreate(input);

      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(createResponse.getBody()).isNotNull();

      UUID protectedRoleId = createResponse.getBody().getId();
      ResponseEntity<Void> deleteResponse = performDelete(protectedRoleId);

      assertThat(deleteResponse.getStatusCode())
          .as("Should return FORBIDDEN status when deleting protected role")
          .isEqualTo(HttpStatus.FORBIDDEN);
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

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getRoleType()).isEqualTo(RoleType.SYSTEM);
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
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle special characters in name")
    void shouldHandleSpecialCharactersInName() {
      RoleInputDTO input = createValidInput();
      input.setName("role_with_special_chars_äöü");
      input.setName("Role with special chars: äöü ß @#$%");

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
    @DisplayName("Should handle empty Name as invalid")
    void shouldHandleEmptyName() {
      RoleInputDTO input = createValidInput();
      input.setName("");

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Permission Type Validation Tests")
  class PermissionTypeValidationTests {

    @Test
    @DisplayName("Should reject creating DATA role with SYSTEM permissions")
    void shouldRejectDataRoleWithSystemPermissions() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.DATA);
      input.setPermissionIds(getPermissionIdsByType(PermissionType.SYSTEM));

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST for mismatched permission types")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject creating SYSTEM role with DATA permissions")
    void shouldRejectSystemRoleWithDataPermissions() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.SYSTEM);
      input.setPermissionIds(getPermissionIdsByType(PermissionType.DATA));

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST for mismatched permission types")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should create SYSTEM role with SYSTEM permissions successfully")
    void shouldCreateSystemRoleWithSystemPermissions() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.SYSTEM);
      input.setPermissionIds(getPermissionIdsByType(PermissionType.SYSTEM));

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED for matching permission types")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getPermissions()).isNotEmpty();
    }

    @Test
    @DisplayName("Should create DATA role with DATA permissions successfully")
    void shouldCreateDataRoleWithDataPermissions() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.DATA);
      input.setPermissionIds(getPermissionIdsByType(PermissionType.DATA));

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED for matching permission types")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getPermissions()).isNotEmpty();
    }

    @Test
    @DisplayName("Should reject updating role to add mismatched permissions")
    void shouldRejectUpdateWithMismatchedPermissions() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.DATA);
      ResponseEntity<RoleOutputDTO> createResponse = performCreate(input);
      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      UUID roleId = createResponse.getBody().getId();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("permissionIds", getPermissionIdsByType(PermissionType.SYSTEM));

      ResponseEntity<RoleOutputDTO> patchResponse = performPatch(roleId, patchMap);

      assertThat(patchResponse.getStatusCode())
          .as("Should return BAD_REQUEST when patching with mismatched permissions")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should allow creating role with empty permissions list")
    void shouldAllowEmptyPermissions() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.DATA);
      input.setPermissionIds(Collections.emptyList());

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED for empty permissions")
          .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should allow creating role with null permissions")
    void shouldAllowNullPermissions() {
      RoleInputDTO input = createValidInput();
      input.setRoleType(RoleType.SYSTEM);
      input.setPermissionIds(null);

      ResponseEntity<RoleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED for null permissions")
          .isEqualTo(HttpStatus.CREATED);
    }
  }
}
