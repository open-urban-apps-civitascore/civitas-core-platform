package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

@DisplayName("User Update Event Integration Tests")
class UserUpdateEventIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should update user in Keycloak when updating user")
  void shouldUpdateUserInKeycloakWhenUpdatingUser() {
    User existingUser = userService.create(createValidUserInput());
    String externalId = existingUser.getExternalId();

    UserInputDTO updateInput = new UserInputDTO();
    updateInput.setFirstName("Updated");
    updateInput.setLastName("Name");
    updateInput.setEmail(existingUser.getEmail());
    updateInput.setActive(true);

    User updatedUser = userService.update(existingUser.getId(), updateInput);

    assertThat(updatedUser.getFirstName()).isEqualTo("Updated");

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              UserRepresentation keycloakUser =
                  keycloakAdminClient
                      .realm("civitas-core")
                      .users()
                      .get(externalId)
                      .toRepresentation();
              assertThat(keycloakUser.getFirstName()).isEqualTo("Updated");
              assertThat(keycloakUser.getLastName()).isEqualTo("Name");
            });
  }
}
