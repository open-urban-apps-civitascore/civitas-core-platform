package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

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
            });
  }
}
