package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

@DisplayName("User Update Event Integration Tests")
class UserUpdateEventIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should update user in Keycloak when updating user without changing email")
  void shouldUpdateUserInKeycloakWhenUpdatingUser() {
    User existingUser = userService.create(createValidUserInput());
    String externalId = existingUser.getExternalId();

    // Simulate a user who has already verified their email
    UserRepresentation userRep =
        keycloakAdminClient.realm("civitas-core").users().get(externalId).toRepresentation();
    userRep.setEmailVerified(true);
    keycloakAdminClient.realm("civitas-core").users().get(externalId).update(userRep);

    // Update name only — email address stays the same
    UserInputDTO updateInput = new UserInputDTO();
    updateInput.setFirstName("Updated");
    updateInput.setLastName("Name");
    updateInput.setEmail(existingUser.getEmail());

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
              assertThat(keycloakUser.isEmailVerified())
                  .as("emailVerified should remain true after a non-email update")
                  .isTrue();
            });
  }

  @Test
  @DisplayName("Should reset emailVerified to false when email address changes")
  void shouldResetEmailVerifiedWhenEmailChanges() {
    User existingUser = userService.create(createValidUserInput());
    String externalId = existingUser.getExternalId();

    // Simulate a user who has already verified their email
    UserRepresentation userRep =
        keycloakAdminClient.realm("civitas-core").users().get(externalId).toRepresentation();
    userRep.setEmailVerified(true);
    keycloakAdminClient.realm("civitas-core").users().get(externalId).update(userRep);

    // Update with a new email address
    String newEmail = "new." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    UserInputDTO updateInput = new UserInputDTO();
    updateInput.setFirstName(existingUser.getFirstName());
    updateInput.setLastName(existingUser.getLastName());
    updateInput.setEmail(newEmail);

    userService.update(existingUser.getId(), updateInput);

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
              assertThat(keycloakUser.getEmail()).isEqualTo(newEmail);
              assertThat(keycloakUser.getRequiredActions())
                  .as("VERIFY_EMAIL should be required after an email address change")
                  .contains("VERIFY_EMAIL");
              assertThat(keycloakUser.isEmailVerified())
                  .as("emailVerified should be false after an email address change")
                  .isFalse();
            });
  }
}
