package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.User;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Multiple Users Integration Tests")
class MultipleUsersIntegrationTest extends BaseEventPublishingIntegrationTest {

  @Test
  @DisplayName("Should handle multiple user creations sequentially")
  void shouldHandleMultipleUserCreationsSequentially() {
    User user1 = userService.create(createValidUserInput());
    User user2 = userService.create(createValidUserInput());
    User user3 = userService.create(createValidUserInput());

    assertThat(userRepository.count()).isEqualTo(3);
    assertThat(userRepository.findAll())
        .extracting(User::getId)
        .contains(user1.getId(), user2.getId(), user3.getId());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(findKeycloakUserByEmail(user1.getEmail())).isNotNull();
              assertThat(findKeycloakUserByEmail(user2.getEmail())).isNotNull();
              assertThat(findKeycloakUserByEmail(user3.getEmail())).isNotNull();
            });
  }
}
