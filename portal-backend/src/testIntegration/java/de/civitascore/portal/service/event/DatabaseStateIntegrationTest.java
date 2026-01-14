package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Database State and ID Generation Integration Tests")
class DatabaseStateIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should generate UUID for entity ID automatically")
  void shouldGenerateUuidForEntityId() {
    UserInputDTO input = createValidUserInput();

    User createdUser = userService.create(input);

    assertThat(createdUser.getId()).as("ID should be generated automatically").isNotNull();

    assertThat(createdUser.getId().toString())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  @DisplayName("Should persist external ID separately after validation")
  void shouldPersistExternalIdSeparatelyAfterValidation() {
    UserInputDTO input = createValidUserInput();

    User createdUser = userService.create(input);

    User reloadedUser = userRepository.findById(createdUser.getId()).orElseThrow();

    assertThat(reloadedUser.getExternalId())
        .as("External ID should be persisted in DB")
        .isNotNull()
        .isEqualTo(createdUser.getExternalId());
  }
}
