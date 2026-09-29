package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.GroupOutputDTO;
import de.civitascore.portal.model.output.summary.RoleSummaryDTO;
import de.civitascore.portal.util.RestPage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Group Controller Integration Tests")
class GroupControllerIntegrationTest
    extends BaseControllerIntegrationTest<GroupInputDTO, GroupOutputDTO> {

  private final String GROUPS_ENDPOINT = "/groups";

  @Autowired protected PortalTestDataFactory portalData;

  @Override
  protected String getEndpointPath() {
    return GROUPS_ENDPOINT;
  }

  @BeforeEach
  void cleanupBeforeTest() {
    performAdditionalCleanup();
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  private User createTestUser() {
    return portalData.user();
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

  private ResponseEntity<GroupOutputDTO> performReplaceAssignments(
      UUID groupId, List<AssignmentGroupInputDTO> assignments) {
    String url = getEndpointPath() + "/" + groupId + "/assignments";
    return exchange(
        url, HttpMethod.PUT, createAuthHeaders(), assignments, getOutputTypeReference());
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

    @Test
    @DisplayName("Should filter groups by single memberId")
    void shouldFilterGroupsByMemberId() {
      User user = createTestUser();

      GroupInputDTO groupWithMember = createValidInput();
      groupWithMember.setMemberIds(List.of(user.getId()));
      performCreate(groupWithMember);

      // Create a group without this member
      performCreate(createValidInput());

      Map<String, String> params = Map.of("memberIds", user.getId().toString());
      ResponseEntity<RestPage<GroupOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should filter groups by multiple memberIds")
    void shouldFilterGroupsByMultipleMemberIds() {
      User user1 = createTestUser();
      User user2 = createTestUser();

      GroupInputDTO groupA = createValidInput();
      groupA.setMemberIds(List.of(user1.getId()));
      performCreate(groupA);

      GroupInputDTO groupB = createValidInput();
      groupB.setMemberIds(List.of(user2.getId()));
      performCreate(groupB);

      // Create a group without either member
      performCreate(createValidInput());

      Map<String, String> params = Map.of("memberIds", user1.getId() + "," + user2.getId());
      ResponseEntity<RestPage<GroupOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return empty result when filtering by non-existent memberId")
    void shouldReturnEmptyForNonExistentMemberId() {
      performCreate(createValidInput());

      Map<String, String> params = Map.of("memberIds", UUID.randomUUID().toString());
      ResponseEntity<RestPage<GroupOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalElements()).isZero();
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
    @DisplayName("Should return assignments with role description and readonly in group response")
    void shouldReturnAssignmentsWithRoleDetails() {
      // Create group
      UUID groupId = createTestEntity();

      // Create role with description and readonly
      Role role =
          portalData.role(
              b -> b.description("Role for testing assignments").roleType(RoleType.DATA));

      // Create assignment via dedicated endpoint
      AssignmentGroupInputDTO assignmentInput = new AssignmentGroupInputDTO();
      assignmentInput.setRoleId(role.getId());
      assignmentInput.setScopeType(ScopeType.TENANT);
      ResponseEntity<GroupOutputDTO> assignResponse =
          performReplaceAssignments(groupId, List.of(assignmentInput));
      assertThat(assignResponse.getStatusCode())
          .as("Replace assignments should succeed: %s", assignResponse.getBody())
          .isEqualTo(HttpStatus.OK);

      // Fetch group and verify assignments
      ResponseEntity<GroupOutputDTO> groupResponse = performGetById(groupId);
      assertThat(groupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

      GroupOutputDTO output = groupResponse.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getAssignments()).as("Assignments should not be empty").isNotEmpty();

      AssignmentOutputDTO assignment = output.getAssignments().get(0);
      assertThat(assignment.getRole()).as("Role should be present").isNotNull();

      RoleSummaryDTO roleSummary = assignment.getRole();
      assertThat(roleSummary.getName()).isEqualTo(role.getName());
      assertThat(roleSummary.getDescription())
          .as("Role description should be mapped")
          .isEqualTo("Role for testing assignments");
      assertThat(roleSummary.isReadonly()).as("Role readonly should be mapped").isFalse();
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
  @DisplayName("Assignment Management via Dedicated Endpoint Tests")
  class AssignmentManagementTests {

    private Role createDataRole(String name) {
      return portalData.role(
          b ->
              b.name(name + " " + UUID.randomUUID().toString().substring(0, 8))
                  .description("Test role: " + name)
                  .roleType(RoleType.DATA));
    }

    @Test
    @DisplayName("Should replace assignments for a group via PUT")
    void shouldReplaceAssignmentsViaPut() {
      UUID groupId = createTestEntity();
      Role role1 = createDataRole("Role A");
      Role role2 = createDataRole("Role B");

      AssignmentGroupInputDTO a1 = new AssignmentGroupInputDTO();
      a1.setRoleId(role1.getId());
      a1.setScopeType(ScopeType.TENANT);

      AssignmentGroupInputDTO a2 = new AssignmentGroupInputDTO();
      a2.setRoleId(role2.getId());
      a2.setScopeType(ScopeType.TENANT);

      ResponseEntity<GroupOutputDTO> response = performReplaceAssignments(groupId, List.of(a1, a2));

      assertThat(response.getStatusCode())
          .as("PUT assignments should succeed: %s", response.getBody())
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getAssignments()).as("Should have 2 assignments").hasSize(2);
      assertThat(response.getBody().getAssignments())
          .extracting(a -> a.getRole().getName())
          .containsExactlyInAnyOrder(role1.getName(), role2.getName());
    }

    @Test
    @DisplayName("Should remove assignment when PUT sends fewer assignments")
    void shouldRemoveAssignmentWhenPutSendsFewerAssignments() {
      UUID groupId = createTestEntity();
      Role role1 = createDataRole("Keep Role");
      Role role2 = createDataRole("Remove Role");

      // First PUT: create 2 assignments
      AssignmentGroupInputDTO a1 = new AssignmentGroupInputDTO();
      a1.setRoleId(role1.getId());
      a1.setScopeType(ScopeType.TENANT);

      AssignmentGroupInputDTO a2 = new AssignmentGroupInputDTO();
      a2.setRoleId(role2.getId());
      a2.setScopeType(ScopeType.TENANT);

      ResponseEntity<GroupOutputDTO> firstResponse =
          performReplaceAssignments(groupId, List.of(a1, a2));
      assertThat(firstResponse.getStatusCode())
          .as("First PUT should succeed: %s", firstResponse.getBody())
          .isEqualTo(HttpStatus.OK);
      assertThat(firstResponse.getBody().getAssignments()).hasSize(2);

      // Second PUT: send only 1 assignment (remove role2)
      AssignmentGroupInputDTO keepAssignment = new AssignmentGroupInputDTO();
      keepAssignment.setRoleId(role1.getId());
      keepAssignment.setScopeType(ScopeType.TENANT);

      ResponseEntity<GroupOutputDTO> secondResponse =
          performReplaceAssignments(groupId, List.of(keepAssignment));

      assertThat(secondResponse.getStatusCode())
          .as("Second PUT should succeed: %s", secondResponse.getBody())
          .isEqualTo(HttpStatus.OK);
      assertThat(secondResponse.getBody()).isNotNull();
      assertThat(secondResponse.getBody().getAssignments())
          .as("Should have only 1 assignment after removal")
          .hasSize(1);
      assertThat(secondResponse.getBody().getAssignments().get(0).getRole().getName())
          .as("Remaining assignment should be the kept role")
          .isEqualTo(role1.getName());

      // Verify via GET that removal persisted
      ResponseEntity<GroupOutputDTO> getResponse = performGetById(groupId);
      assertThat(getResponse.getBody().getAssignments())
          .as("GET should also show only 1 assignment")
          .hasSize(1);
    }

    @Test
    @DisplayName("Should replace a scoped role without leaving the old assignment behind")
    void shouldReplaceScopedAssignmentOnGroupWithoutStaleRow() {
      UUID groupId = createTestEntity();
      DataSet dataSet = portalData.dataSet();
      Role roleA = createDataRole("Scoped A");
      Role roleB = createDataRole("Scoped B");

      AssignmentGroupInputDTO assignA = new AssignmentGroupInputDTO();
      assignA.setRoleId(roleA.getId());
      assignA.setScopeType(ScopeType.DATASET);
      assignA.setScopeId(dataSet.getId());
      assertThat(performReplaceAssignments(groupId, List.of(assignA)).getStatusCode())
          .isEqualTo(HttpStatus.OK);

      // Replace role A with role B on the same scope in one PUT.
      AssignmentGroupInputDTO assignB = new AssignmentGroupInputDTO();
      assignB.setRoleId(roleB.getId());
      assignB.setScopeType(ScopeType.DATASET);
      assignB.setScopeId(dataSet.getId());
      ResponseEntity<GroupOutputDTO> response =
          performReplaceAssignments(groupId, List.of(assignB));

      assertThat(response.getStatusCode())
          .as("PUT should succeed: %s", response.getBody())
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getAssignments())
          .extracting(a -> a.getRole().getName())
          .containsExactly(roleB.getName());

      // Re-fetch: the stale role-A assignment row must be gone, not merely hidden.
      ResponseEntity<GroupOutputDTO> getResponse = performGetById(groupId);
      assertThat(getResponse.getBody().getAssignments())
          .as("Only role B should remain after replacement")
          .extracting(a -> a.getRole().getName())
          .containsExactly(roleB.getName());
    }

    @Test
    @DisplayName("Should add assignment when PUT sends additional assignments")
    void shouldAddAssignmentWhenPutSendsMore() {
      UUID groupId = createTestEntity();
      Role role1 = createDataRole("Original Role");
      Role role2 = createDataRole("Added Role");

      // First PUT: create 1 assignment
      AssignmentGroupInputDTO a1 = new AssignmentGroupInputDTO();
      a1.setRoleId(role1.getId());
      a1.setScopeType(ScopeType.TENANT);
      ResponseEntity<GroupOutputDTO> firstResponse =
          performReplaceAssignments(groupId, List.of(a1));
      assertThat(firstResponse.getStatusCode())
          .as("First PUT should succeed: %s", firstResponse.getBody())
          .isEqualTo(HttpStatus.OK);

      // Second PUT: send 2 assignments
      AssignmentGroupInputDTO keep = new AssignmentGroupInputDTO();
      keep.setRoleId(role1.getId());
      keep.setScopeType(ScopeType.TENANT);

      AssignmentGroupInputDTO added = new AssignmentGroupInputDTO();
      added.setRoleId(role2.getId());
      added.setScopeType(ScopeType.TENANT);

      ResponseEntity<GroupOutputDTO> response =
          performReplaceAssignments(groupId, List.of(keep, added));

      assertThat(response.getStatusCode())
          .as("PUT assignments should succeed: %s", response.getBody())
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getAssignments())
          .as("Should have 2 assignments after adding")
          .hasSize(2);
    }

    @Test
    @DisplayName("Should remove all assignments when PUT sends empty list")
    void shouldRemoveAllAssignmentsWhenPutSendsEmptyList() {
      UUID groupId = createTestEntity();
      Role role = createDataRole("To Remove");

      // First PUT: create assignment
      AssignmentGroupInputDTO a = new AssignmentGroupInputDTO();
      a.setRoleId(role.getId());
      a.setScopeType(ScopeType.TENANT);
      performReplaceAssignments(groupId, List.of(a));

      // Second PUT: empty assignments list
      ResponseEntity<GroupOutputDTO> response = performReplaceAssignments(groupId, List.of());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getAssignments())
          .as("Should have no assignments")
          .isNullOrEmpty();

      // Verify via GET
      ResponseEntity<GroupOutputDTO> getResponse = performGetById(groupId);
      assertThat(getResponse.getBody().getAssignments())
          .as("GET should confirm no assignments")
          .isNullOrEmpty();
    }

    @Test
    @DisplayName("Should not modify assignments when group is updated via PUT")
    void shouldNotModifyAssignmentsWhenGroupIsUpdatedViaPut() {
      UUID groupId = createTestEntity();
      Role role = createDataRole("Untouched Role");

      // Create assignment via dedicated endpoint
      AssignmentGroupInputDTO a = new AssignmentGroupInputDTO();
      a.setRoleId(role.getId());
      a.setScopeType(ScopeType.TENANT);
      ResponseEntity<GroupOutputDTO> assignResponse =
          performReplaceAssignments(groupId, List.of(a));
      assertThat(assignResponse.getStatusCode())
          .as("Replace assignments should succeed: %s", assignResponse.getBody())
          .isEqualTo(HttpStatus.OK);

      // Update group name via regular PUT (no assignments field)
      GroupInputDTO updateInput = createUpdateInput();
      ResponseEntity<GroupOutputDTO> response = performUpdate(groupId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getAssignments())
          .as("Assignments should be preserved when group is updated")
          .hasSize(1);
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
