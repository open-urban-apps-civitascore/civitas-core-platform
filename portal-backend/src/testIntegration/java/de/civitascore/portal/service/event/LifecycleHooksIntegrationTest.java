package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Lifecycle Hooks and Validation Integration Tests")
class LifecycleHooksIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should execute preSave hook before persistence")
  void shouldExecutePreSaveHookBeforePersistence() {
    UserInputDTO input = createValidUserInput();
    String email = input.getEmail();

    userService.create(input);

    UserInputDTO duplicateInput = createValidUserInput();
    duplicateInput.setEmail(email);

    Assertions.assertThrows(
        Exception.class,
        () -> userService.create(duplicateInput),
        "preSave hook should validate unique email");
  }

  @Test
  @DisplayName("Should execute postSave hook after successful persistence")
  void shouldExecutePostSaveHookAfterSuccessfulPersistence() {
    UserInputDTO input = createValidUserInput();

    User createdUser = userService.create(input);

    assertThat(createdUser.getId()).isNotNull();
    assertThat(createdUser.getExternalId()).isNotNull();
    assertThat(createdUser.getCreatedAt()).isNotNull();
    assertThat(createdUser.getCreatedBy()).isNull();
  }
}
