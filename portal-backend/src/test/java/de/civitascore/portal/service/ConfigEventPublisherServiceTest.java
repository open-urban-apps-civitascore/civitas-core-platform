package de.civitascore.portal.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.idm.UserConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigEventPublisherServiceTest {

  private ConfigEventPublisherService configEventPublisher;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    // Initialize without Kafka producer (will only log events)
    configEventPublisher = new ConfigEventPublisherService(null, objectMapper);
  }

  @Test
  void publishUserCreated_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertDoesNotThrow(() -> configEventPublisher.publishUserCreated("civitas-core", userConfig));
  }

  @Test
  void publishUserUpdated_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertDoesNotThrow(() -> configEventPublisher.publishUserUpdated("civitas-core", userConfig));
  }

  @Test
  void publishUserDeleted_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertDoesNotThrow(() -> configEventPublisher.publishUserDeleted("civitas-core", userConfig));
  }

  @Test
  void publishConfigEvent_shouldHandleNullResultTopic() {
    UserConfig userConfig = createTestUserConfig();

    assertDoesNotThrow(
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

    assertDoesNotThrow(() -> configEventPublisher.publishGroupCreated("civitas-core", userConfig));
  }

  @Test
  void publishRoleCreated_shouldNotThrowException() {
    UserConfig userConfig = createTestUserConfig();

    assertDoesNotThrow(() -> configEventPublisher.publishRoleCreated("civitas-core", userConfig));
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
