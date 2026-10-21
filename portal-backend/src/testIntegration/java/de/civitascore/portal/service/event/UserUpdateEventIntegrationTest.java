package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;

@DisplayName("User Update Event Integration Tests")
class UserUpdateEventIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should update user in Keycloak when updating user without changing email")
  void shouldUpdateUserInKeycloakWhenUpdatingUser() {
    User existingUser = userService.create(createValidUserInput());
    completeOnboarding(existingUser);

    userService.update(existingUser.getId(), updateInput(existingUser, "Updated", null));

    awaitKeycloakUser(
        existingUser,
        keycloakUser -> {
          assertThat(keycloakUser.getFirstName()).isEqualTo("Updated");
          assertThat(keycloakUser.isEmailVerified())
              .as("emailVerified should remain true after a non-email update")
              .isTrue();
        });
  }

  @Test
  @DisplayName("Should reset emailVerified to false when email address changes")
  void shouldResetEmailVerifiedWhenEmailChanges() {
    User existingUser = userService.create(createValidUserInput());
    completeOnboarding(existingUser);
    String newEmail = newEmail();

    userService.update(existingUser.getId(), updateInput(existingUser, null, newEmail));

    awaitKeycloakUser(
        existingUser,
        keycloakUser -> {
          assertThat(keycloakUser.getEmail()).isEqualTo(newEmail);
          assertThat(keycloakUser.isEmailVerified())
              .as("emailVerified should be false after an email address change")
              .isFalse();
        });
  }

  @Test
  @DisplayName("Should keep pending onboarding actions when updating name before onboarding")
  void shouldKeepPendingOnboardingWhenNameChanges() {
    User pendingUser = userService.create(createValidUserInput());

    userService.update(pendingUser.getId(), updateInput(pendingUser, "Updated", null));

    awaitKeycloakUser(
        pendingUser,
        keycloakUser -> {
          assertThat(keycloakUser.getFirstName()).isEqualTo("Updated");
          assertThat(keycloakUser.getRequiredActions())
              .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");
          assertThat(keycloakUser.isEmailVerified()).isFalse();
        });
  }

  @Test
  @DisplayName("Should keep pending onboarding actions when changing email before onboarding")
  void shouldKeepPendingOnboardingWhenEmailChanges() {
    User pendingUser = userService.create(createValidUserInput());
    String newEmail = newEmail();

    userService.update(pendingUser.getId(), updateInput(pendingUser, null, newEmail));

    awaitKeycloakUser(
        pendingUser,
        keycloakUser -> {
          assertThat(keycloakUser.getEmail()).isEqualTo(newEmail);
          assertThat(keycloakUser.getRequiredActions())
              .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");
          assertThat(keycloakUser.isEmailVerified()).isFalse();
        });
  }

  private UserResource keycloakUser(User user) {
    return keycloakAdminClient.realm("civitas-core").users().get(user.getExternalId());
  }

  private void completeOnboarding(User user) {
    UserRepresentation userRep = keycloakUser(user).toRepresentation();
    userRep.setEmailVerified(true);
    userRep.setRequiredActions(List.of());
    keycloakUser(user).update(userRep);
  }

  private void awaitKeycloakUser(User user, Consumer<UserRepresentation> assertions) {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertions.accept(keycloakUser(user).toRepresentation()));
  }

  private static UserInputDTO updateInput(User user, String firstName, String email) {
    UserInputDTO input = new UserInputDTO();
    input.setFirstName(firstName != null ? firstName : user.getFirstName());
    input.setLastName(user.getLastName());
    input.setEmail(email != null ? email : user.getEmail());
    return input;
  }

  private static String newEmail() {
    return "new." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
  }
}
