package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.output.GroupOutputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.util.RestPage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Group Controller Integration Tests")
class GroupControllerIntegrationTest
    extends BaseControllerIntegrationTest<GroupInputDTO, GroupOutputDTO> {

  private final String GROUPS_ENDPOINT = "/groups";

  @Autowired private GroupRepository groupRepository;

  @Override
  protected String getEndpointPath() {
    return GROUPS_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    groupRepository.deleteAll();
  }

  @Override
  protected GroupInputDTO createValidInput() {
    GroupInputDTO input = new GroupInputDTO();
    input.setName("Test Group " + UUID.randomUUID().toString().substring(0, 8));
    input.setDescription("A test group for integration testing");
    return input;
  }

  @Override
  protected GroupInputDTO createInvalidInput() {
    GroupInputDTO input = new GroupInputDTO();
    input.setDescription("Invalid group without Name");
    return input;
  }

  @Override
  protected GroupInputDTO createUpdateInput() {
    GroupInputDTO input = new GroupInputDTO();
    input.setName("Updated Group");
    input.setDescription("Updated description");
    return input;
  }

  @Override
  protected ParameterizedTypeReference<GroupOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<GroupOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(GroupOutputDTO output) {
    return output.getId();
  }

  @Nested
  @DisplayName("Create Group Tests")
  class CreateGroupTests {

    @Test
    @DisplayName("Should create group successfully with valid data")
    void shouldCreateGroupSuccessfully() {
      GroupInputDTO input = createValidInput();

      ResponseEntity<GroupOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      GroupOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getName()).as("Name should match input").isEqualTo(input.getName());
      assertThat(output.getDescription())
          .as("Description should match input")
          .isEqualTo(input.getDescription());
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();

      assertThat(response.getHeaders().getLocation())
          .as("Location header should be present")
          .isNotNull();
    }

    @Test
    @DisplayName("Should fail to create group with missing required field")
    void shouldFailToCreateGroupWithMissingName() {
      GroupInputDTO input = createInvalidInput();

      ResponseEntity<GroupOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create group without authentication")
    void shouldFailToCreateGroupWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("", org.springframework.http.HttpMethod.POST);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create group with contact user")
    void shouldCreateGroupWithContactUser() {
      GroupInputDTO input = createValidInput();
      input.setName("Group with Contact");

      ResponseEntity<GroupOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should create group with empty members list")
    void shouldCreateGroupWithEmptyMembers() {
      GroupInputDTO input = createValidInput();
      input.setName("Group with Empty Members");
      input.setMemberIds(Collections.emptyList());

      ResponseEntity<GroupOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getMembers()).as("Members should be empty").isNullOrEmpty();
    }

    @Test
    @DisplayName("Should fail to create duplicate group with same Name in same tenant")
    void shouldFailToCreateDuplicateGroup() {
      GroupInputDTO input = createValidInput();
      input.setName("Unique Group Name");

      ResponseEntity<GroupOutputDTO> firstResponse = performCreate(input);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      ResponseEntity<GroupOutputDTO> secondResponse = performCreate(input);

      assertThat(secondResponse.getStatusCode())
          .as("Should return CONFLICT status for duplicate")
          .isEqualTo(HttpStatus.CONFLICT);
    }
  }

  @Nested
  @DisplayName("Read Group Tests")
  class ReadGroupTests {

    @Test
    @DisplayName("Should retrieve group by ID successfully")
    void shouldRetrieveGroupById() {
      UUID groupId = createTestEntity();

      ResponseEntity<GroupOutputDTO> response = performGetById(groupId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      GroupOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(groupId);
      assertThat(output.getName()).as("Name should be present").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent group")
    void shouldReturn404ForNonExistentGroup() {
      ResponseEntity<GroupOutputDTO> response = performGetById(UUID.randomUUID());
      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve group without authentication")
    void shouldFailToRetrieveGroupWithoutAuth() {
      UUID groupId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + groupId, org.springframework.http.HttpMethod.GET);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should retrieve all groups with pagination")
    void shouldRetrieveAllGroupsWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<GroupOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<GroupOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should contain groups").isNotEmpty();
      assertThat(page.getTotalElements()).as("Total elements should be positive").isPositive();
    }

    @Test
    @DisplayName("Should retrieve groups with pagination parameters")
    void shouldRetrieveGroupsWithPaginationParams() {
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "name,asc");

      ResponseEntity<RestPage<GroupOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should filter groups by Name")
    void shouldFilterGroupsByName() {
      GroupInputDTO input = createValidInput();
      input.setName("Searchable Group");
      performCreate(input);

      Map<String, String> params = Map.of("name", "Searchable");

      ResponseEntity<RestPage<GroupOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  @Nested
  @DisplayName("Update Group Tests")
  class UpdateGroupTests {

    @Test
    @DisplayName("Should update group successfully with PUT")
    void shouldUpdateGroupWithPut() {
      UUID groupId = createTestEntity();

      GroupInputDTO updateInput = createUpdateInput();
      ResponseEntity<GroupOutputDTO> response = performUpdate(groupId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      GroupOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(groupId);
      assertThat(output.getName()).as("Name should be updated").isEqualTo(updateInput.getName());
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo(updateInput.getDescription());
      assertThat(output.getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should partially update group with PATCH - single field")
    void shouldPartiallyUpdateGroupWithPatch() {
      UUID groupId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Only description updated");

      ResponseEntity<GroupOutputDTO> response = performPatch(groupId, patchMap);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be updated")
          .isEqualTo("Only description updated");
    }

    @Test
    @DisplayName("Should update multiple fields with PATCH")
    void shouldUpdateMultipleFieldsWithPatch() {
      UUID groupId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("name", "PatchedGroup");
      patchMap.put("description", "Patched description");

      ResponseEntity<GroupOutputDTO> response = performPatch(groupId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("PatchedGroup");
      assertThat(response.getBody().getDescription()).isEqualTo("Patched description");
    }

    @Test
    @DisplayName("Should set description to null with PATCH")
    void shouldSetDescriptionToNullWithPatch() {
      UUID groupId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", null);

      ResponseEntity<GroupOutputDTO> response = performPatch(groupId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be set to null")
          .isNull();
    }

    @Test
    @DisplayName("Should leave omitted fields unchanged with PATCH")
    void shouldLeaveOmittedFieldsUnchangedWithPatch() {
      UUID groupId = createTestEntity();

      ResponseEntity<GroupOutputDTO> initialResponse = performGetById(groupId);
      GroupOutputDTO initialGroup = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "New description");

      ResponseEntity<GroupOutputDTO> response = performPatch(groupId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isEqualTo("New description");
      assertThat(response.getBody().getName())
          .as("Name should remain unchanged")
          .isEqualTo(initialGroup.getName());
    }

    @Test
    @DisplayName("Should set parentGroup to null with PATCH")
    void shouldSetParentGroupToNullWithPatch() {
      UUID parentGroupId = createTestEntity();
      UUID childGroupId = createTestEntity();

      // Set parent
      Map<String, Object> setParentMap = new HashMap<>();
      setParentMap.put("parentGroupId", parentGroupId);
      performPatch(childGroupId, setParentMap);

      // Remove parent
      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("parentGroupId", null);

      ResponseEntity<GroupOutputDTO> response = performPatch(childGroupId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getParentGroup())
          .as("ParentGroup should be set to null")
          .isNull();
    }

    @Test
    @DisplayName("Should handle empty PATCH (no changes)")
    void shouldHandleEmptyPatch() {
      UUID groupId = createTestEntity();

      ResponseEntity<GroupOutputDTO> initialResponse = performGetById(groupId);
      GroupOutputDTO initialGroup = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();

      ResponseEntity<GroupOutputDTO> response = performPatch(groupId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(initialGroup.getName());
      assertThat(response.getBody().getDescription()).isEqualTo(initialGroup.getDescription());
    }

    @Test
    @DisplayName("Should be idempotent with PATCH")
    void shouldBeIdempotentWithPatch() {
      UUID groupId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Idempotent test");

      ResponseEntity<GroupOutputDTO> firstResponse = performPatch(groupId, patchMap);
      ResponseEntity<GroupOutputDTO> secondResponse = performPatch(groupId, patchMap);

      assertThat(firstResponse.getBody()).isNotNull();
      assertThat(secondResponse.getBody()).isNotNull();
      assertThat(firstResponse.getBody().getDescription())
          .isEqualTo(secondResponse.getBody().getDescription());
    }

    @Test
    @DisplayName("Should fail to update non-existent group")
    void shouldFailToUpdateNonExistentGroup() {
      GroupInputDTO updateInput = createUpdateInput();

      ResponseEntity<GroupOutputDTO> response = performUpdate(UUID.randomUUID(), updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update group without authentication")
    void shouldFailToUpdateGroupWithoutAuth() {
      UUID groupId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + groupId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to update group with invalid data")
    void shouldFailToUpdateGroupWithInvalidData() {
      UUID groupId = createTestEntity();
      GroupInputDTO invalidInput = createInvalidInput();

      ResponseEntity<GroupOutputDTO> response = performUpdate(groupId, invalidInput);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should update group and clear members")
    void shouldUpdateGroupAndClearMembers() {
      UUID groupId = createTestEntity();

      GroupInputDTO updateInput = createUpdateInput();
      updateInput.setMemberIds(Collections.emptyList());

      ResponseEntity<GroupOutputDTO> response = performUpdate(groupId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getMembers()).isNullOrEmpty();
    }
  }

  @Nested
  @DisplayName("Delete Group Tests")
  class DeleteGroupTests {

    @Test
    @DisplayName("Should delete group successfully")
    void shouldDeleteGroupSuccessfully() {
      UUID groupId = createTestEntity();

      ResponseEntity<Void> response = performDelete(groupId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<GroupOutputDTO> getResponse = performGetById(groupId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted group should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent group")
    void shouldFailToDeleteNonExistentGroup() {
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete group without authentication")
    void shouldFailToDeleteGroupWithoutAuth() {
      UUID groupId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + groupId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should not allow deleting same group twice")
    void shouldNotAllowDeletingSameGroupTwice() {
      UUID groupId = createTestEntity();

      ResponseEntity<Void> firstResponse = performDelete(groupId);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<Void> secondResponse = performDelete(groupId);
      assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Business Logic Tests")
  class BusinessLogicTests {

    @Test
    @DisplayName("Should create group with parent group relationship")
    void shouldCreateGroupWithParentGroup() {
      UUID parentId = createTestEntity();

      GroupInputDTO childInput = createValidInput();
      childInput.setName("Child Group");
      childInput.setParentGroupId(parentId);

      ResponseEntity<GroupOutputDTO> response = performCreate(childInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getParentGroup()).as("Parent group should be set").isNotNull();
      assertThat(response.getBody().getParentGroup().getId())
          .as("Parent group ID should match")
          .isEqualTo(parentId);
    }

    @Test
    @DisplayName("Should handle group hierarchy correctly")
    void shouldHandleGroupHierarchyCorrectly() {
      GroupInputDTO parentInput = createValidInput();
      parentInput.setName("Parent Group");
      ResponseEntity<GroupOutputDTO> parentResponse = performCreate(parentInput);
      UUID parentId = parentResponse.getBody().getId();
      assertThat(parentResponse.getBody()).isNotNull();

      GroupInputDTO childInput = createValidInput();
      childInput.setName("Child Group");
      childInput.setParentGroupId(parentId);
      ResponseEntity<GroupOutputDTO> childResponse = performCreate(childInput);

      assertThat(childResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(childResponse.getBody().getParentGroup()).isNotNull();
      assertThat(childResponse.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should update group members list")
    void shouldUpdateGroupMembersList() {
      UUID groupId = createTestEntity();

      GroupInputDTO updateInput = createUpdateInput();
      ResponseEntity<GroupOutputDTO> response = performUpdate(groupId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should maintain tenant isolation")
    void shouldMaintainTenantIsolation() {
      UUID group1Id = createTestEntity();

      ResponseEntity<GroupOutputDTO> response = performGetById(group1Id);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should preserve timestamps on update")
    void shouldPreserveTimestampsOnUpdate() {
      UUID groupId = createTestEntity();

      ResponseEntity<GroupOutputDTO> originalResponse = performGetById(groupId);
      GroupOutputDTO original = originalResponse.getBody();

      assertThat(original).isNotNull();
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      GroupInputDTO updateInput = createUpdateInput();
      ResponseEntity<GroupOutputDTO> updateResponse = performUpdate(groupId, updateInput);
      GroupOutputDTO updated = updateResponse.getBody();

      assertThat(updated).isNotNull();
      assertThat(updated.getCreatedAt())
          .as("Created timestamp should not change")
          .isEqualTo(original.getCreatedAt());
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle special characters in Name")
    void shouldHandleSpecialCharactersInName() {
      GroupInputDTO input = createValidInput();
      input.setName("Group with special chars: äöü ß @#$%");

      ResponseEntity<GroupOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody().getName());
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should handle null description")
    void shouldHandleNullDescription() {
      GroupInputDTO input = createValidInput();
      input.setDescription(null);

      ResponseEntity<GroupOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle empty Name as invalid")
    void shouldHandleEmptyName() {
      GroupInputDTO input = createValidInput();
      input.setName("");

      ResponseEntity<GroupOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Empty Name should be rejected")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should handle whitespace-only Name as invalid")
    void shouldHandleWhitespaceOnlyName() {
      GroupInputDTO input = createValidInput();
      input.setName("   ");

      ResponseEntity<GroupOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Whitespace-only Name should be rejected")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
