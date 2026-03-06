package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.UserTitleType;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.UserOutputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.UserRepository;
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

@DisplayName("User Controller Integration Tests")
class UserControllerIntegrationTest
    extends BaseControllerIntegrationTest<UserInputDTO, UserOutputDTO> {

  private final String USERS_ENDPOINT = "/users";

  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;

  @Override
  protected String getEndpointPath() {
    return USERS_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    groupRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Override
  protected UserInputDTO createValidInput() {
    UserInputDTO input = new UserInputDTO();
    input.setFirstName("Test");
    input.setLastName("User " + System.currentTimeMillis());
    input.setEmail("testuser" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
    input.setPhone("+49123456789");
    input.setActive(true);
    return input;
  }

  @Override
  protected UserInputDTO createInvalidInput() {
    UserInputDTO input = new UserInputDTO();
    input.setPhone("+49123456789");
    return input;
  }

  @Override
  protected UserInputDTO createUpdateInput() {
    UserInputDTO input = new UserInputDTO();
    input.setTitle(UserTitleType.MS);
    input.setFirstName("Updated");
    input.setLastName("User");
    input.setEmail("updated.user@example.com");
    input.setPhone("+49987654321");
    input.setActive(true);
    return input;
  }

  @Override
  protected ParameterizedTypeReference<UserOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<UserOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(UserOutputDTO output) {
    return output.getId();
  }

  @Nested
  @DisplayName("Create User Tests")
  class CreateUserTests {

    @Test
    @DisplayName("Should create user successfully with valid data")
    void shouldCreateUserSuccessfully() {
      UserInputDTO input = createValidInput();

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      UserOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getTitle()).as("Title should match input").isEqualTo(input.getTitle());
      assertThat(output.getFirstName())
          .as("First name should match input")
          .isEqualTo(input.getFirstName());
      assertThat(output.getLastName())
          .as("Last name should match input")
          .isEqualTo(input.getLastName());
      assertThat(output.getEmail()).as("Email should match input").isEqualTo(input.getEmail());
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();
    }

    @Test
    @DisplayName("Should fail to create user with missing required fields")
    void shouldFailToCreateUserWithMissingFields() {
      UserInputDTO input = createInvalidInput();

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create user with invalid email")
    void shouldFailToCreateUserWithInvalidEmail() {
      UserInputDTO input = createValidInput();
      input.setEmail("invalid-email");

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create user without authentication")
    void shouldFailToCreateUserWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("", org.springframework.http.HttpMethod.POST);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create user with groups")
    void shouldCreateUserWithGroups() {
      UserInputDTO input = createValidInput();
      input.setGroupIds(Collections.emptyList());

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should fail to create duplicate user with same email in same tenant")
    void shouldFailToCreateDuplicateUser() {
      UserInputDTO input = createValidInput();
      input.setEmail("unique.email@example.com");

      // Create first user
      ResponseEntity<UserOutputDTO> firstResponse = performCreate(input);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      // Try to create duplicate
      ResponseEntity<UserOutputDTO> secondResponse = performCreate(input);

      assertThat(secondResponse.getStatusCode())
          .as("Should return CONFLICT status for duplicate")
          .isEqualTo(HttpStatus.CONFLICT);
    }
  }

  @Nested
  @DisplayName("Read User Tests")
  class ReadUserTests {

    @Test
    @DisplayName("Should retrieve user by ID successfully")
    void shouldRetrieveUserById() {
      UUID userId = createTestEntity();

      ResponseEntity<UserOutputDTO> response = performGetById(userId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      UserOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(userId);
      assertThat(output.getEmail()).as("Email should be present").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent user")
    void shouldReturn404ForNonExistentUser() {
      ResponseEntity<UserOutputDTO> response = performGetById(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve user without authentication")
    void shouldFailToRetrieveUserWithoutAuth() {
      UUID userId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + userId, org.springframework.http.HttpMethod.GET);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should retrieve all users with pagination")
    void shouldRetrieveAllUsersWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<UserOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<UserOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should contain users").isNotEmpty();
      assertThat(page.getTotalElements()).as("Total elements should be positive").isPositive();
    }

    @Test
    @DisplayName("Should retrieve users with pagination parameters")
    void shouldRetrieveUsersWithPaginationParams() {
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "lastName,asc");

      ResponseEntity<RestPage<UserOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Update User Tests")
  class UpdateUserTests {

    @Test
    @DisplayName("Should update user successfully with PUT")
    void shouldUpdateUserWithPut() {
      UUID userId = createTestEntity();

      UserInputDTO updateInput = createUpdateInput();
      ResponseEntity<UserOutputDTO> response = performUpdate(userId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      UserOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(userId);
      assertThat(output.getFirstName())
          .as("First name should be updated")
          .isEqualTo(updateInput.getFirstName());
      assertThat(output.getEmail()).as("Email should be updated").isEqualTo(updateInput.getEmail());
      assertThat(output.getModifiedAt()).isNotNull();
      assertThat(output.getTitle()).as("Title should be updated").isEqualTo(updateInput.getTitle());
    }

    @Test
    @DisplayName("Should partially update user with PATCH - single field")
    void shouldPartiallyUpdateUserWithPatch() {
      UUID userId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("phone", "+49111222333");

      ResponseEntity<UserOutputDTO> response = performPatch(userId, patchMap);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getPhone())
          .as("Phone should be updated")
          .isEqualTo("+49111222333");
      // Other fields should remain unchanged
      assertThat(response.getBody().getFirstName())
          .as("First name should remain unchanged")
          .isEqualTo("Test");
    }

    @Test
    @DisplayName("Should update multiple fields with PATCH")
    void shouldPartiallyUpdateUserWithPatchMultipleFields() {
      UUID userId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("phone", null);
      patchMap.put("firstName", "TestPatch");

      ResponseEntity<UserOutputDTO> response = performPatch(userId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getPhone()).as("Phone should be updated to null").isNull();
      assertThat(response.getBody().getFirstName())
          .as("First name should be updated")
          .isEqualTo("TestPatch");
      // Other fields should remain unchanged
      assertThat(response.getBody().getEmail()).as("Email should remain unchanged").isNotNull();
    }

    @Test
    @DisplayName("Should set field to null with PATCH")
    void shouldSetFieldToNullWithPatch() {
      UUID userId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("phone", null);

      ResponseEntity<UserOutputDTO> response = performPatch(userId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getPhone()).as("Phone should be set to null").isNull();
    }

    @Test
    @DisplayName("Should leave omitted fields unchanged with PATCH")
    void shouldLeaveOmittedFieldsUnchangedWithPatch() {
      UUID userId = createTestEntity();

      // Get initial state
      ResponseEntity<UserOutputDTO> initialResponse = performGetById(userId);
      UserOutputDTO initialUser = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("firstName", "NewFirstName");
      // lastName, email, phone, active are omitted

      ResponseEntity<UserOutputDTO> response = performPatch(userId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getFirstName())
          .as("First name should be updated")
          .isEqualTo("NewFirstName");
      assertThat(response.getBody().getLastName())
          .as("Last name should remain unchanged")
          .isEqualTo(initialUser.getLastName());
      assertThat(response.getBody().getEmail())
          .as("Email should remain unchanged")
          .isEqualTo(initialUser.getEmail());
      assertThat(response.getBody().getPhone())
          .as("Phone should remain unchanged")
          .isEqualTo(initialUser.getPhone());
      assertThat(response.getBody().getActive())
          .as("Active should remain unchanged")
          .isEqualTo(initialUser.getActive());
      assertThat(response.getBody().getTitle())
          .as("Title should remain unchanged")
          .isEqualTo(initialUser.getTitle());
    }

    @Test
    @DisplayName("Should handle empty PATCH (no changes)")
    void shouldHandleEmptyPatch() {
      UUID userId = createTestEntity();

      ResponseEntity<UserOutputDTO> initialResponse = performGetById(userId);
      UserOutputDTO initialUser = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();

      ResponseEntity<UserOutputDTO> response = performPatch(userId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getFirstName()).isEqualTo(initialUser.getFirstName());
      assertThat(response.getBody().getLastName()).isEqualTo(initialUser.getLastName());
      assertThat(response.getBody().getEmail()).isEqualTo(initialUser.getEmail());
      assertThat(response.getBody().getPhone()).isEqualTo(initialUser.getPhone());
      assertThat(response.getBody().getTitle()).isEqualTo(initialUser.getTitle());
    }

    @Test
    @DisplayName("Should update boolean field with PATCH")
    void shouldUpdateBooleanFieldWithPatch() {
      UUID userId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("active", false);

      ResponseEntity<UserOutputDTO> response = performPatch(userId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getActive()).as("Active should be updated to false").isFalse();
    }

    @Test
    @DisplayName("Should handle PATCH with all fields")
    void shouldHandlePatchWithAllFields() {
      UUID userId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("firstName", "PatchedFirst");
      patchMap.put("lastName", "PatchedLast");
      patchMap.put("email", "patched@example.com");
      patchMap.put("phone", "+49999999999");
      patchMap.put("active", false);
      patchMap.put("title", "MS");

      ResponseEntity<UserOutputDTO> response = performPatch(userId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getFirstName()).isEqualTo("PatchedFirst");
      assertThat(response.getBody().getLastName()).isEqualTo("PatchedLast");
      assertThat(response.getBody().getEmail()).isEqualTo("patched@example.com");
      assertThat(response.getBody().getPhone()).isEqualTo("+49999999999");
      assertThat(response.getBody().getActive()).isFalse();
      assertThat(response.getBody().getTitle()).isEqualTo(UserTitleType.MS);
    }

    @Test
    @DisplayName("Should be idempotent with PATCH")
    void shouldBeIdempotentWithPatch() {
      UUID userId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("firstName", "IdempotentTest");

      // Apply patch twice
      ResponseEntity<UserOutputDTO> firstResponse = performPatch(userId, patchMap);
      ResponseEntity<UserOutputDTO> secondResponse = performPatch(userId, patchMap);

      assertThat(firstResponse.getBody()).isNotNull();
      assertThat(secondResponse.getBody()).isNotNull();
      assertThat(firstResponse.getBody().getFirstName())
          .isEqualTo(secondResponse.getBody().getFirstName());
      assertThat(firstResponse.getBody().getFirstName()).isEqualTo("IdempotentTest");
    }

    @Test
    @DisplayName("Should fail to update non-existent user")
    void shouldFailToUpdateNonExistentUser() {
      UserInputDTO updateInput = createUpdateInput();

      ResponseEntity<UserOutputDTO> response = performUpdate(UUID.randomUUID(), updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update user without authentication")
    void shouldFailToUpdateUserWithoutAuth() {
      UUID userId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + userId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to update user with invalid email")
    void shouldFailToUpdateUserWithInvalidEmail() {
      UUID userId = createTestEntity();
      UserInputDTO invalidInput = createUpdateInput();
      invalidInput.setEmail("not-an-email");

      ResponseEntity<UserOutputDTO> response = performUpdate(userId, invalidInput);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Delete User Tests")
  class DeleteUserTests {

    @Test
    @DisplayName("Should delete user successfully")
    void shouldDeleteUserSuccessfully() {
      UUID userId = createTestEntity();

      ResponseEntity<Void> response = performDelete(userId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<UserOutputDTO> getResponse = performGetById(userId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted user should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent user")
    void shouldFailToDeleteNonExistentUser() {
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete user without authentication")
    void shouldFailToDeleteUserWithoutAuth() {
      UUID userId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + userId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Business Logic Tests")
  class BusinessLogicTests {

    @Test
    @DisplayName("Should deactivate user")
    void shouldDeactivateUser() {
      UUID userId = createTestEntity();

      UserInputDTO updateInput = createUpdateInput();
      updateInput.setActive(false);

      ResponseEntity<UserOutputDTO> response = performUpdate(userId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getActive()).isFalse();
    }

    @Test
    @DisplayName(
        "Should not accept externalId via API — field is read-only, set only by Kafka sync")
    void shouldHandleExternalId() {
      UserInputDTO input = createValidInput();
      input.setExternalId("ext-123");

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getExternalId()).isNull();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle special characters in name")
    void shouldHandleSpecialCharactersInName() {
      UserInputDTO input = createValidInput();
      input.setFirstName("Hans-Peter");
      input.setLastName("Müller-Schmitt");

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should handle null phone")
    void shouldHandleNullPhone() {
      UserInputDTO input = createValidInput();
      input.setPhone(null);

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle empty first name as invalid")
    void shouldHandleEmptyFirstName() {
      UserInputDTO input = createValidInput();
      input.setFirstName("");

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Group Membership Tests")
  class GroupMembershipTests {

    private Group createTestGroup(String name) {
      Group group = new Group();
      group.setName(name);
      return groupRepository.save(group);
    }

    @Test
    @DisplayName("Should create user with group memberships")
    void shouldCreateUserWithGroups() {
      Group group1 =
          createTestGroup("Test Group 1 " + UUID.randomUUID().toString().substring(0, 8));
      Group group2 =
          createTestGroup("Test Group 2 " + UUID.randomUUID().toString().substring(0, 8));

      UserInputDTO input = createValidInput();
      input.setGroupIds(List.of(group1.getId(), group2.getId()));

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getGroups()).as("User should have 2 groups").hasSize(2);
      assertThat(response.getBody().getGroups())
          .extracting("id")
          .containsExactlyInAnyOrder(group1.getId(), group2.getId());
    }

    @Test
    @DisplayName("Should update user groups with PUT")
    void shouldUpdateUserGroupsWithPut() {
      // Create initial groups
      Group group1 =
          createTestGroup("Initial Group " + UUID.randomUUID().toString().substring(0, 8));

      // Create user with initial group
      UserInputDTO createInput = createValidInput();
      createInput.setGroupIds(List.of(group1.getId()));
      ResponseEntity<UserOutputDTO> createResponse = performCreate(createInput);
      UUID userId = createResponse.getBody().getId();

      // Create new groups for update
      Group group2 =
          createTestGroup("Updated Group 1 " + UUID.randomUUID().toString().substring(0, 8));
      Group group3 =
          createTestGroup("Updated Group 2 " + UUID.randomUUID().toString().substring(0, 8));

      // Update user with new groups
      UserInputDTO updateInput = createUpdateInput();
      updateInput.setGroupIds(List.of(group2.getId(), group3.getId()));
      ResponseEntity<UserOutputDTO> updateResponse = performUpdate(userId, updateInput);

      assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(updateResponse.getBody()).isNotNull();
      assertThat(updateResponse.getBody().getGroups())
          .as("User should have 2 new groups")
          .hasSize(2);
      assertThat(updateResponse.getBody().getGroups())
          .extracting("id")
          .containsExactlyInAnyOrder(group2.getId(), group3.getId());
      assertThat(updateResponse.getBody().getGroups())
          .extracting("id")
          .doesNotContain(group1.getId());
    }

    @Test
    @DisplayName("Should update user groups with PATCH")
    void shouldUpdateUserGroupsWithPatch() {
      // Create initial group
      Group group1 =
          createTestGroup("Initial Group " + UUID.randomUUID().toString().substring(0, 8));

      // Create user with initial group
      UserInputDTO createInput = createValidInput();
      createInput.setGroupIds(List.of(group1.getId()));
      ResponseEntity<UserOutputDTO> createResponse = performCreate(createInput);
      UUID userId = createResponse.getBody().getId();

      // Create new group for update
      Group group2 = createTestGroup("New Group " + UUID.randomUUID().toString().substring(0, 8));

      // PATCH user with new group
      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("groupIds", List.of(group2.getId()));
      ResponseEntity<UserOutputDTO> patchResponse = performPatch(userId, patchMap);

      assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patchResponse.getBody()).isNotNull();
      assertThat(patchResponse.getBody().getGroups()).as("User should have 1 new group").hasSize(1);
      assertThat(patchResponse.getBody().getGroups())
          .extracting("id")
          .containsExactly(group2.getId());
    }

    @Test
    @DisplayName("Should remove all groups from user with empty list")
    void shouldRemoveAllGroupsFromUser() {
      // Create groups
      Group group1 =
          createTestGroup("Group to Remove " + UUID.randomUUID().toString().substring(0, 8));

      // Create user with group
      UserInputDTO createInput = createValidInput();
      createInput.setGroupIds(List.of(group1.getId()));
      ResponseEntity<UserOutputDTO> createResponse = performCreate(createInput);
      UUID userId = createResponse.getBody().getId();

      assertThat(createResponse.getBody().getGroups()).hasSize(1);

      // Update user with empty groups
      UserInputDTO updateInput = createUpdateInput();
      updateInput.setGroupIds(Collections.emptyList());
      ResponseEntity<UserOutputDTO> updateResponse = performUpdate(userId, updateInput);

      assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(updateResponse.getBody()).isNotNull();
      assertThat(updateResponse.getBody().getGroups())
          .as("User should have no groups")
          .isNullOrEmpty();
    }

    @Test
    @DisplayName("Should add groups to user who had none")
    void shouldAddGroupsToUserWithNone() {
      // Create user without groups
      UserInputDTO createInput = createValidInput();
      ResponseEntity<UserOutputDTO> createResponse = performCreate(createInput);
      UUID userId = createResponse.getBody().getId();

      assertThat(createResponse.getBody().getGroups()).isNullOrEmpty();

      // Create group and add to user
      Group group1 = createTestGroup("New Group " + UUID.randomUUID().toString().substring(0, 8));

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("groupIds", List.of(group1.getId()));
      ResponseEntity<UserOutputDTO> patchResponse = performPatch(userId, patchMap);

      assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patchResponse.getBody()).isNotNull();
      assertThat(patchResponse.getBody().getGroups()).as("User should have 1 group").hasSize(1);
    }

    @Test
    @DisplayName("Should not modify groups when groupIds field is not provided in PATCH")
    void shouldNotModifyGroupsWhenNotProvided() {
      // Create group and user with that group
      Group group1 =
          createTestGroup("Existing Group " + UUID.randomUUID().toString().substring(0, 8));

      UserInputDTO createInput = createValidInput();
      createInput.setGroupIds(List.of(group1.getId()));
      ResponseEntity<UserOutputDTO> createResponse = performCreate(createInput);
      UUID userId = createResponse.getBody().getId();

      assertThat(createResponse.getBody().getGroups()).hasSize(1);

      // PATCH user with firstName only (no groupIds)
      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("firstName", "UpdatedFirstName");
      ResponseEntity<UserOutputDTO> patchResponse = performPatch(userId, patchMap);

      assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patchResponse.getBody()).isNotNull();
      assertThat(patchResponse.getBody().getFirstName()).isEqualTo("UpdatedFirstName");
      assertThat(patchResponse.getBody().getGroups())
          .as("User should still have the original group")
          .hasSize(1);
      assertThat(patchResponse.getBody().getGroups())
          .extracting("id")
          .containsExactly(group1.getId());
    }

    @Test
    @DisplayName("Should fail to create user with non-existent group ID")
    void shouldFailToCreateUserWithNonExistentGroupId() {
      UUID nonExistentGroupId = UUID.randomUUID();

      UserInputDTO input = createValidInput();
      input.setGroupIds(List.of(nonExistentGroupId));

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST for non-existent group")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to update user with non-existent group ID")
    void shouldFailToUpdateUserWithNonExistentGroupId() {
      // Create a user first
      UserInputDTO createInput = createValidInput();
      ResponseEntity<UserOutputDTO> createResponse = performCreate(createInput);
      UUID userId = createResponse.getBody().getId();

      // Try to update with non-existent group
      UUID nonExistentGroupId = UUID.randomUUID();
      UserInputDTO updateInput = createUpdateInput();
      updateInput.setGroupIds(List.of(nonExistentGroupId));

      ResponseEntity<UserOutputDTO> updateResponse = performUpdate(userId, updateInput);

      assertThat(updateResponse.getStatusCode())
          .as("Should return BAD_REQUEST for non-existent group")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail when one of multiple group IDs does not exist")
    void shouldFailWhenOneGroupIdDoesNotExist() {
      // Create one valid group
      Group validGroup =
          createTestGroup("Valid Group " + UUID.randomUUID().toString().substring(0, 8));
      UUID nonExistentGroupId = UUID.randomUUID();

      UserInputDTO input = createValidInput();
      input.setGroupIds(List.of(validGroup.getId(), nonExistentGroupId));

      ResponseEntity<UserOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST when any group ID is invalid")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
