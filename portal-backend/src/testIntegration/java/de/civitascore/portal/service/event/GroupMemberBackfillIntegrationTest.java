package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.configuration.KeycloakProperties;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.service.ConfigEventPublisherService;
import de.civitascore.portal.service.initializer.GroupInitializer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("Group Member Backfill Integration Tests")
class GroupMemberBackfillIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private ConfigEventPublisherService configEventPublisher;
  @Autowired private KeycloakProperties keycloakProperties;
  @Autowired private EventProperties eventProperties;
  @Autowired private PlatformTransactionManager transactionManager;

  // The backfill is gated by keycloak.group-member-backfill on the injected properties. Build a
  // GroupInitializer with the flag set explicitly rather than reloading the context per case.
  private GroupInitializer backfillInitializer(boolean enabled) {
    KeycloakProperties props =
        new KeycloakProperties(
            keycloakProperties.targetRealm(),
            keycloakProperties.authServerUrl(),
            keycloakProperties.realm(),
            keycloakProperties.enforceOtp(),
            enabled);
    return new GroupInitializer(
        groupRepository,
        roleRepository,
        assignmentRepository,
        configEventPublisher,
        Optional.empty(),
        props,
        eventProperties,
        transactionManager);
  }

  /**
   * Reproduce the pre-feature gap: a group already synced (has externalId) whose member exists in
   * the portal DB but was dropped from the Keycloak group. The flag-on backfill must re-add it.
   */
  @Test
  @DisplayName("Backfill re-adds a portal member missing from the Keycloak group when enabled")
  void shouldBackfillMissingMemberWhenEnabled() {
    var user = userService.create(createValidUserInput());
    assertThat(user.getExternalId()).isNotNull();

    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("backfillgrp" + System.currentTimeMillis());
    groupInput.setMemberIds(List.of(user.getId()));
    Group group = groupService.create(groupInput);
    String externalId = group.getExternalId();
    assertThat(externalId).isNotNull();

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(findKeycloakGroupMembers(externalId)).hasSize(1));

    // Simulate the pre-feature state: drop the member from Keycloak only; the portal group_members
    // row and the group's external_id stay intact.
    keycloakAdminClient
        .realm("civitas-core")
        .users()
        .get(user.getExternalId())
        .leaveGroup(externalId);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(findKeycloakGroupMembers(externalId)).isEmpty());

    backfillInitializer(true).initialize();

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(findKeycloakGroupMembers(externalId))
                    .as("Backfill should re-add the portal member to the Keycloak group")
                    .hasSize(1)
                    .anyMatch(m -> m.getId().equals(user.getExternalId())));
  }

  @Test
  @DisplayName("Backfill leaves Keycloak membership unchanged when disabled")
  void shouldNotBackfillWhenDisabled() {
    var user = userService.create(createValidUserInput());

    GroupInputDTO groupInput = new GroupInputDTO();
    groupInput.setName("nobackfillgrp" + System.currentTimeMillis());
    groupInput.setMemberIds(List.of(user.getId()));
    Group group = groupService.create(groupInput);
    String externalId = group.getExternalId();
    assertThat(externalId).isNotNull();

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(findKeycloakGroupMembers(externalId)).hasSize(1));

    keycloakAdminClient
        .realm("civitas-core")
        .users()
        .get(user.getExternalId())
        .leaveGroup(externalId);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(findKeycloakGroupMembers(externalId)).isEmpty());

    backfillInitializer(false).initialize();

    // Flag off publishes nothing, so the membership must stay removed for the whole window.
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(findKeycloakGroupMembers(externalId))
                    .as("Disabled backfill must not touch Keycloak membership")
                    .isEmpty());
  }
}
