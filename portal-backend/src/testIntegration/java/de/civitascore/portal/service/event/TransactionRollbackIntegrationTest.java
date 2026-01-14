package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.input.UserInputDTO;
import org.junit.jupiter.api.Assertions;
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

    Assertions.assertThrows(
        Exception.class,
        () -> userService.create(duplicateInput),
        "Should prevent duplicate email");

    assertThat(userRepository.count()).isEqualTo(countBefore);
  }
}
