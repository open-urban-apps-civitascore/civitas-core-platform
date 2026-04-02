package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.User;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

@DisplayName("User Delete Event Integration Tests")
class UserDeleteEventIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should delete user from Keycloak when deleting user")
  void shouldDeleteUserFromKeycloakWhenDeletingUser() {
    User existingUser = userService.create(createValidUserInput());
    UUID userId = existingUser.getId();
    String externalId = existingUser.getExternalId();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              UserRepresentation keycloakUser =
                  keycloakAdminClient
                      .realm("civitas-core")
                      .users()
                      .get(externalId)
                      .toRepresentation();
              assertThat(keycloakUser).isNotNull();
            });

    userService.deleteById(userId);

    assertThat(userRepository.findById(userId)).isEmpty();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              Assertions.assertThrows(
                  Exception.class,
                  () ->
                      keycloakAdminClient
                          .realm("civitas-core")
                          .users()
                          .get(externalId)
                          .toRepresentation(),
                  "User should be deleted from Keycloak");
            });
  }
}
