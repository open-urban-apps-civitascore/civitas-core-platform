package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.GroupInputDTO;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("syncgrp" + System.currentTimeMillis());
    Group group = groupService.create(groupInput);
    assertThat(group.getExternalId()).as("Group must have externalId after creation").isNotNull();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(findKeycloakGroups())
                    .anyMatch(g -> g.getName().equals(group.getName())));

    var userInput = createValidUserInput();
    var user = userService.create(userInput);
    assertThat(user.getExternalId()).as("User must have externalId after creation").isNotNull();

    // replaceGroups publishes USER_UPDATED with the groups field; config-adapter's
    // GroupSyncHelper diff-syncs memberships in Keycloak.
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

  @Test
  @DisplayName("Should preserve user memberships when group is renamed (sync keyed on externalId)")
  void shouldPreserveMembershipsAcrossGroupRename() {
    // Regression guard for the rename-race issue: membership sync now keys on the stable
    // Keycloak externalId, not on the group name. A portal-side rename must NOT cause the user
    // to lose their existing membership in Keycloak — the externalId stays the same.
    GroupInputDTO groupInput = new GroupInputDTO();
    String originalName = "renamegrp" + System.currentTimeMillis();
    groupInput.setName(originalName);
    Group group = groupService.create(groupInput);
    String externalId = group.getExternalId();
    assertThat(externalId).isNotNull();

    var user = userService.create(createValidUserInput());
    userService.replaceGroups(user.getId(), List.of(group.getId()));
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(findKeycloakUserGroups(user.getExternalId())).hasSize(1));

    // Rename the group: externalId stays the same, only the display name changes.
    GroupInputDTO renameInput = new GroupInputDTO();
    String newName = originalName + "-renamed";
    renameInput.setName(newName);
    groupService.update(group.getId(), renameInput);

    // A subsequent user update must not drop the membership — sync keys on externalId.
    userService.replaceGroups(user.getId(), List.of(group.getId()));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              var userGroups = findKeycloakUserGroups(user.getExternalId());
              assertThat(userGroups)
                  .as("User must still be a member after portal-side rename")
                  .hasSize(1)
                  .anyMatch(g -> g.getName().equals(newName));
            });
  }

  @Test
  @DisplayName("Should sync group members to Keycloak when creating a group with members")
  void shouldSyncGroupMembersToKeycloakOnCreate() {
    var user = userService.create(createValidUserInput());
    assertThat(user.getExternalId()).as("User must have externalId after creation").isNotNull();

    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("membergrp" + System.currentTimeMillis());
    groupInput.setMemberIds(List.of(user.getId()));
    Group group = groupService.create(groupInput);
    assertThat(group.getExternalId()).isNotNull();

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(findKeycloakGroupMembers(group.getExternalId()))
                    .as("Created group should contain the member in Keycloak")
                    .hasSize(1)
                    .anyMatch(m -> m.getId().equals(user.getExternalId())));
  }

  @Test
  @DisplayName("Should diff group members on update — adds new, removes dropped")
  void shouldDiffGroupMembersOnUpdate() {
    var user1 = userService.create(createValidUserInput());
    var user2 = userService.create(createValidUserInput());

    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("diffgrp" + System.currentTimeMillis());
    groupInput.setMemberIds(List.of(user1.getId()));
    Group group = groupService.create(groupInput);
    assertThat(group.getExternalId()).isNotNull();

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(findKeycloakGroupMembers(group.getExternalId()))
                    .hasSize(1)
                    .anyMatch(m -> m.getId().equals(user1.getExternalId())));

    // Replace members with user2 only: user1 must be removed, user2 added.
    GroupInputDTO updateInput = new GroupInputDTO();
    updateInput.setName(group.getName());
    updateInput.setMemberIds(List.of(user2.getId()));
    groupService.update(group.getId(), updateInput);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(findKeycloakGroupMembers(group.getExternalId()))
                    .as("Update should add user2 and remove user1")
                    .hasSize(1)
                    .anyMatch(m -> m.getId().equals(user2.getExternalId())));
  }

  @Test
  @DisplayName("Should skip an unsynced member (null externalId) without failing the event")
  void shouldSkipUnsyncedMemberWithoutFailingEvent() {
    var synced = userService.create(createValidUserInput());
    assertThat(synced.getExternalId()).isNotNull();

    // Persist a user directly with no Keycloak reference — simulates a member not yet synced.
    User unsynced =
        userRepository.save(
            User.builder()
                .firstName("Unsynced")
                .lastName("Member")
                .email("unsynced." + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .build());
    assertThat(unsynced.getExternalId()).isNull();

    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("skipgrp" + System.currentTimeMillis());
    groupInput.setMemberIds(List.of(synced.getId(), unsynced.getId()));
    Group group = groupService.create(groupInput);

    // The event must succeed (group synced) even though one member has no externalId.
    assertThat(group.getExternalId())
        .as("Group create must not fail on unsynced member")
        .isNotNull();

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(findKeycloakGroupMembers(group.getExternalId()))
                    .as("Only the synced member is added; the unsynced one is skipped")
                    .hasSize(1)
                    .anyMatch(m -> m.getId().equals(synced.getExternalId())));
  }

  @Test
  @DisplayName("Should remove user from all Keycloak groups when replacing with an empty list")
  void shouldRemoveAllUserGroupMembershipsInKeycloak() {
    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("removegrp" + System.currentTimeMillis());
    Group group = groupService.create(groupInput);
    assertThat(group.getExternalId()).isNotNull();

    var user = userService.create(createValidUserInput());
    assertThat(user.getExternalId()).isNotNull();

    userService.replaceGroups(user.getId(), List.of(group.getId()));
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(findKeycloakUserGroups(user.getExternalId())).hasSize(1));

    // Replacing with an empty list publishes USER_UPDATED with groups=[]; GroupSyncHelper
    // should leave every current Keycloak membership.
    userService.replaceGroups(user.getId(), List.of());

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(findKeycloakUserGroups(user.getExternalId()))
                    .as("All Keycloak memberships must be removed")
                    .isEmpty());
  }
}
