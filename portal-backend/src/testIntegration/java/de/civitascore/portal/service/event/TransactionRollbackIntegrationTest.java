package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.model.input.UserInputDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Transaction Rollback Integration Tests")
class TransactionRollbackIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should rollback database when Keycloak creation fails")
  void shouldRollbackDatabaseWhenKeycloakCreationFails() {
    UserInputDTO input = createValidUserInput();
    String email = input.getEmail();

    userService.create(input);

    UserInputDTO duplicateInput = createValidUserInput();
    duplicateInput.setEmail(email);

    long countBefore = userRepository.count();

    assertThatThrownBy(() -> userService.create(duplicateInput))
        .as("Should prevent duplicate email")
        .isInstanceOf(Exception.class);

    assertThat(userRepository.count()).isEqualTo(countBefore);
  }
}
