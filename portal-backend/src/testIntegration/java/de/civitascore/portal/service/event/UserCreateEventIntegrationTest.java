package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.test.context.TestPropertySource;

@DisplayName("User Create Event Integration Tests")
class UserCreateEventIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should create user in Keycloak when creating user")
  void shouldCreateUserInKeycloakWhenCreatingUser() {
    UserInputDTO input = createValidUserInput();
    String email = input.getEmail();

    User createdUser = userService.create(input);

    assertThat(createdUser).isNotNull();
    assertThat(createdUser.getId()).isNotNull();
    assertThat(createdUser.getExternalId()).isNotNull();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              UserRepresentation keycloakUser = findKeycloakUserByEmail(email);
              assertThat(keycloakUser).as("User should be created in Keycloak").isNotNull();
              assertThat(keycloakUser.getEmail()).isEqualTo(email);
              assertThat(keycloakUser.getFirstName()).isEqualTo(input.getFirstName());
              assertThat(keycloakUser.getLastName()).isEqualTo(input.getLastName());
              assertThat(keycloakUser.getId()).isEqualTo(createdUser.getExternalId());
              assertThat(keycloakUser.isEmailVerified())
                  .as("emailVerified should be false for a newly created user")
                  .isFalse();
              assertThat(keycloakUser.getRequiredActions())
                  .as("New user should be required to verify email, update password and setup OTP")
                  .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");
            });
  }

  @Test
  @DisplayName("Should include correct user data in Keycloak")
  void shouldIncludeCorrectUserDataInKeycloak() {
    UserInputDTO input = createValidUserInput();
    input.setFirstName("John");
    input.setLastName("Doe");

    userService.create(input);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              UserRepresentation keycloakUser = findKeycloakUserByEmail(input.getEmail());
              assertThat(keycloakUser).isNotNull();
              assertThat(keycloakUser.getFirstName()).isEqualTo("John");
              assertThat(keycloakUser.getLastName()).isEqualTo("Doe");
              assertThat(keycloakUser.getUsername()).isEqualTo(input.getEmail());
              assertThat(keycloakUser.isEmailVerified())
                  .as("emailVerified should be false for a newly created user")
                  .isFalse();
              assertThat(keycloakUser.getRequiredActions())
                  .as("New user should be required to verify email, update password and setup OTP")
                  .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");
            });
  }

  @Nested
  @TestPropertySource(properties = "keycloak.enforce-otp=false")
  @DisplayName("With OTP enforcement disabled (KEYCLOAK_ENFORCE_OTP=false)")
  class OtpEnforcementDisabled {

    @Test
    @DisplayName("Should not require CONFIGURE_TOTP when OTP enforcement is disabled")
    void shouldNotRequireConfigureTotpWhenEnforcementDisabled() {
      UserInputDTO input = createValidUserInput();
      String email = input.getEmail();

      userService.create(input);

      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                UserRepresentation keycloakUser = findKeycloakUserByEmail(email);
                assertThat(keycloakUser).as("User should be created in Keycloak").isNotNull();
                assertThat(keycloakUser.getRequiredActions())
                    .as("New user should verify email and update password but not set up OTP")
                    .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD");
              });
    }
  }
}
