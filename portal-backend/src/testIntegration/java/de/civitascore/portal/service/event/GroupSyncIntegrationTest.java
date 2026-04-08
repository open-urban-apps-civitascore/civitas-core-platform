package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.GroupRepresentation;

@DisplayName("Group Sync Integration Tests")
class GroupSyncIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should create group in Keycloak when creating group")
  void shouldCreateGroupInKeycloakWhenCreatingGroup() {
    GroupInputDTO input = new GroupInputDTO();
    input.setName("Test Group " + System.currentTimeMillis());

    Group createdGroup = groupService.create(input);

    assertThat(createdGroup).isNotNull();
    assertThat(createdGroup.getId()).isNotNull();
    assertThat(createdGroup.getExternalId()).isNotNull();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              List<GroupRepresentation> keycloakGroups = findKeycloakGroups();
              assertThat(keycloakGroups)
                  .as("Group should be created in Keycloak")
                  .anyMatch(g -> g.getName().equals(createdGroup.getName()));
            });
  }

  @Test
  @DisplayName("Should store externalId on group after Keycloak sync")
  void shouldStoreExternalIdAfterSync() {
    GroupInputDTO input = new GroupInputDTO();
    input.setName("ExternalId Group " + System.currentTimeMillis());

    Group createdGroup = groupService.create(input);

    assertThat(createdGroup.getExternalId())
        .as("externalId should be set from Keycloak response")
        .isNotNull()
        .isNotBlank();
  }

  @Test
  @DisplayName("Should delete group from Keycloak when deleting group")
  void shouldDeleteGroupFromKeycloakWhenDeletingGroup() {
    GroupInputDTO input = new GroupInputDTO();
    input.setName("Delete Group " + System.currentTimeMillis());

    Group createdGroup = groupService.create(input);
    String groupName = createdGroup.getName();

    assertThat(createdGroup.getExternalId()).isNotNull();

    groupService.deleteById(createdGroup.getId());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              List<GroupRepresentation> keycloakGroups = findKeycloakGroups();
              assertThat(keycloakGroups)
                  .as("Group should be deleted from Keycloak")
                  .noneMatch(g -> g.getName().equals(groupName));
            });
  }

  @Test
  @DisplayName("Should update user group memberships in Keycloak when replacing groups")
  void shouldSyncUserGroupMembershipsToKeycloak() {
    // Create a group
    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("syncgrp" + System.currentTimeMillis());
    Group group = groupService.create(groupInput);
    assertThat(group.getExternalId()).as("Group must have externalId after creation").isNotNull();

    // Verify group exists in Keycloak
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(findKeycloakGroups())
                    .anyMatch(g -> g.getName().equals(group.getName())));

    // Create a user (triggers sync to Keycloak)
    var userInput = createValidUserInput();
    var user = userService.create(userInput);
    assertThat(user.getExternalId()).as("User must have externalId after creation").isNotNull();

    // Add user to group via replaceGroups (triggers USER_UPDATED event with groups field)
    userService.replaceGroups(user.getId(), List.of(group.getId()));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              var userGroups = findKeycloakUserGroups(user.getExternalId());
              assertThat(userGroups)
                  .as("User should be in group in Keycloak")
                  .hasSize(1)
                  .anyMatch(g -> g.getName().equals(group.getName()));
            });
  }
}
