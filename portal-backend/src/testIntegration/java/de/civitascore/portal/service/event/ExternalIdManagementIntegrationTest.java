package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

@DisplayName("External ID Management Integration Tests")
class ExternalIdManagementIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should set external ID from Keycloak on create")
  void shouldSetExternalIdFromKeycloakOnCreate() {
    UserInputDTO input = createValidUserInput();

    User createdUser = userService.create(input);

    assertThat(createdUser.getExternalId())
        .as("External ID should be set")
        .isNotNull()
        .matches("[0-9a-f-]{36}");

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              UserRepresentation keycloakUser = findKeycloakUserByEmail(input.getEmail());
              assertThat(keycloakUser).isNotNull();
              assertThat(createdUser.getExternalId()).isEqualTo(keycloakUser.getId());
            });
  }

  @Test
  @DisplayName("Should preserve external ID on update")
  void shouldPreserveExternalIdOnUpdate() {
    User existingUser = userService.create(createValidUserInput());
    String originalExternalId = existingUser.getExternalId();
    assertThat(originalExternalId).isNotNull();

    UserInputDTO updateInput = new UserInputDTO();
    updateInput.setFirstName("Updated");
    updateInput.setLastName(existingUser.getLastName());
    updateInput.setEmail(existingUser.getEmail());

    User updatedUser = userService.update(existingUser.getId(), updateInput);

    assertThat(updatedUser.getExternalId()).isEqualTo(originalExternalId);
  }
}
