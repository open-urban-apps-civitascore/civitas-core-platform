package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Update Operation Validation Integration Tests")
class UpdateOperationIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should update only modified fields")
  void shouldUpdateOnlyModifiedFields() {
    User existingUser = userService.create(createValidUserInput());
    String originalEmail = existingUser.getEmail();
    String originalLastName = existingUser.getLastName();

    UserInputDTO updateInput = new UserInputDTO();
    updateInput.setFirstName("UpdatedFirstName");
    updateInput.setLastName(originalLastName);
    updateInput.setEmail(originalEmail);
    updateInput.setActive(existingUser.getActive());

    User updatedUser = userService.update(existingUser.getId(), updateInput);

    assertThat(updatedUser.getFirstName()).isEqualTo("UpdatedFirstName");
    assertThat(updatedUser.getLastName()).isEqualTo(originalLastName);
    assertThat(updatedUser.getEmail()).isEqualTo(originalEmail);
  }

  @Test
  @DisplayName("Should preserve ID and created metadata on update")
  void shouldPreserveIdAndCreatedMetadataOnUpdate() {
    User existingUser = userService.create(createValidUserInput());
    UUID originalId = existingUser.getId();

    UserInputDTO updateInput = new UserInputDTO();
    updateInput.setFirstName("Updated");
    updateInput.setLastName("Name");
    updateInput.setEmail(existingUser.getEmail());
    updateInput.setActive(true);

    User updatedUser = userService.update(existingUser.getId(), updateInput);

    assertThat(updatedUser.getId()).isEqualTo(originalId);
    assertThat(updatedUser.getExternalId()).isNotNull();
  }
}
