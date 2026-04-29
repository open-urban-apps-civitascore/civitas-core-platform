package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThatNoException;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.idm.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ConfigEventPublisherServiceTest {

  private ConfigEventPublisherService configEventPublisher;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new JsonMapper();
    // Initialize without Kafka producer (will only log events)
    configEventPublisher = new ConfigEventPublisherService(null, objectMapper);
  }

  @Test
  void publishUserCreated_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertThatNoException()
        .isThrownBy(() -> configEventPublisher.publishUserCreated("civitas-core", userConfig));
  }

  @Test
  void publishUserUpdated_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertThatNoException()
        .isThrownBy(() -> configEventPublisher.publishUserUpdated("civitas-core", userConfig));
  }

  @Test
  void publishUserDeleted_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertThatNoException()
        .isThrownBy(() -> configEventPublisher.publishUserDeleted("civitas-core", userConfig));
  }

  @Test
  void publishConfigEvent_shouldHandleNullResultTopic() {
    UserConfig userConfig = createTestUserConfig();

    assertThatNoException()
        .isThrownBy(
            () ->
                configEventPublisher.publishConfigEvent(
                    Topics.USER_CREATED,
                    "keycloak",
                    "civitas-core",
                    Operation.CREATE,
                    "/users",
                    userConfig));
  }

  @Test
  void publishGroupCreated_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertThatNoException()
        .isThrownBy(() -> configEventPublisher.publishGroupCreated("civitas-core", userConfig));
  }

  @Test
  void publishRoleCreated_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertThatNoException()
        .isThrownBy(() -> configEventPublisher.publishRoleCreated("civitas-core", userConfig));
  }

  private UserConfig createTestUserConfig() {
    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("test.user@example.com");
    userConfig.setEmail("test.user@example.com");
    userConfig.setFirstName("Test");
    userConfig.setLastName("User");
    userConfig.setEnabled(true);
    userConfig.setEmailVerified(false);
    return userConfig;
  }
}
