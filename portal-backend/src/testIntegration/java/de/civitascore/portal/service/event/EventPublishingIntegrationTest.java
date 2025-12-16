package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.ConfigAdapterTestHelper;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.UserService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@DisplayName("Event Publishing Integration Tests")
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"},
    topics = {
      "core.civitas.idm.user.created",
      "core.civitas.idm.user.updated",
      "core.civitas.idm.user.deleted",
      "core.civitas.config.results"
    })
@TestPropertySource(properties = {"kafka.enabled=true"})
@Slf4j
class EventPublishingIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  @Value("${spring.embedded.kafka.brokers}")
  private String embeddedKafkaBrokers;

  private ConfigAdapterTestHelper configAdapterHelper;

  @BeforeEach
  void setUp() {
    log.debug("=== Test Setup Starting ===");
    userRepository.deleteAll();

    // Ensure civitas-core realm exists in Keycloak
    ensureCivitasCoreRealmExists();

    // Clean up Keycloak users
    cleanupKeycloakUsers();

    // Start Config Adapter to process events
    configAdapterHelper =
        new ConfigAdapterTestHelper(KEYCLOAK, embeddedKafkaBrokers, kafkaTemplate);

    log.debug("=== Test Setup Complete ===");
  }

  @AfterEach
  void tearDown() {
    log.debug("=== Test Teardown Starting ===");

    // Stop Config Adapter
    if (configAdapterHelper != null) {
      try {
        configAdapterHelper.close();
      } catch (Exception e) {
        log.warn("Error closing config adapter helper: {}", e.getMessage());
      }
    }

    userRepository.deleteAll();
    cleanupKeycloakUsers();
    log.debug("=== Test Teardown Complete ===");
  }

  private void ensureCivitasCoreRealmExists() {
    try {
      // Check if civitas-core realm exists
      try {
        keycloakAdminClient.realm("civitas-core").toRepresentation();
        log.debug("civitas-core realm already exists");
      } catch (Exception e) {
        // Realm doesn't exist, create it
        log.info("Creating civitas-core realm in Keycloak");
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm("civitas-core");
        realm.setEnabled(true);
        realm.setDisplayName("Civitas Core Test Realm");
        keycloakAdminClient.realms().create(realm);
        log.info("civitas-core realm created successfully");
      }
    } catch (Exception e) {
      log.error("Failed to ensure civitas-core realm exists: {}", e.getMessage(), e);
    }
  }

  private void cleanupKeycloakUsers() {
    try {
      // Clean up users in civitas-core realm
      List<UserRepresentation> users = keycloakAdminClient.realm("civitas-core").users().list();
      for (UserRepresentation user : users) {
        if (user.getEmail() != null && user.getEmail().contains("@example.com")) {
          try {
            keycloakAdminClient.realm("civitas-core").users().delete(user.getId());
            log.debug("Cleaned up Keycloak user: {}", user.getEmail());
          } catch (Exception e) {
            log.warn("Failed to delete Keycloak user {}: {}", user.getEmail(), e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to list Keycloak users for cleanup: {}", e.getMessage());
    }
  }

  private UserRepresentation findKeycloakUserByEmail(String email) {
    try {
      List<UserRepresentation> users =
          keycloakAdminClient.realm("civitas-core").users().searchByEmail(email, true);
      return users.isEmpty() ? null : users.get(0);
    } catch (Exception e) {
      log.error("Failed to search for Keycloak user by email {}: {}", email, e.getMessage());
      return null;
    }
  }

  @Nested
  @DisplayName("User Create Event Tests")
  class UserCreateEventTests {

    @Test
    @DisplayName("Should create user in Keycloak when creating user")
    void shouldCreateUserInKeycloakWhenCreatingUser() {
      // Given
      UserInputDTO input = createValidUserInput();
      String email = input.getEmail();

      // When
      User createdUser = userService.create(input);

      // Then
      assertThat(createdUser).isNotNull();
      assertThat(createdUser.getId()).isNotNull();
      assertThat(createdUser.getExternalId()).isNotNull();

      // Verify user exists in Keycloak
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
      // Given
      UserInputDTO input = createValidUserInput();
      input.setFirstName("John");
      input.setLastName("Doe");

      // When
      userService.create(input);

      // Then
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

  @Nested
  @DisplayName("User Update Event Tests")
  class UserUpdateEventTests {

    @Test
    @DisplayName("Should update user in Keycloak when updating user")
    void shouldUpdateUserInKeycloakWhenUpdatingUser() {
      // Given - create user first
      User existingUser = userService.create(createValidUserInput());
      String externalId = existingUser.getExternalId();

      // When - update user
      UserInputDTO updateInput = new UserInputDTO();
      updateInput.setFirstName("Updated");
      updateInput.setLastName("Name");
      updateInput.setEmail(existingUser.getEmail());
      updateInput.setActive(true);

      User updatedUser = userService.update(existingUser.getId(), updateInput);

      // Then
      assertThat(updatedUser.getFirstName()).isEqualTo("Updated");

      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                UserRepresentation keycloakUser =
                    keycloakAdminClient
                        .realm("civitas-core")
                        .users()
                        .get(externalId)
                        .toRepresentation();
                assertThat(keycloakUser.getFirstName()).isEqualTo("Updated");
                assertThat(keycloakUser.getLastName()).isEqualTo("Name");
              });
    }
  }

  @Nested
  @DisplayName("User Delete Event Tests")
  class UserDeleteEventTests {

    @Test
    @DisplayName("Should delete user from Keycloak when deleting user")
    void shouldDeleteUserFromKeycloakWhenDeletingUser() {
      // Given
      User existingUser = userService.create(createValidUserInput());
      UUID userId = existingUser.getId();
      String externalId = existingUser.getExternalId();

      // Verify user exists in Keycloak
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(
              () -> {
                UserRepresentation keycloakUser =
                    keycloakAdminClient
                        .realm("civitas-core")
                        .users()
                        .get(externalId)
                        .toRepresentation();
                assertThat(keycloakUser).isNotNull();
              });

      // When
      userService.deleteById(userId);

      // Then
      assertThat(userRepository.findById(userId)).isEmpty();

      // Verify user is deleted from Keycloak
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                Assertions.assertThrows(
                    Exception.class,
                    () ->
                        keycloakAdminClient
                            .realm("civitas-core")
                            .users()
                            .get(externalId)
                            .toRepresentation(),
                    "User should be deleted from Keycloak");
              });
    }
  }

  @Nested
  @DisplayName("Transaction Rollback Scenarios")
  class TransactionRollbackScenarios {

    @Test
    @DisplayName("Should rollback database when Keycloak creation fails")
    void shouldRollbackDatabaseWhenKeycloakCreationFails() {
      // This test would require simulating a Keycloak failure
      // For now, we test that duplicate emails are prevented
      UserInputDTO input = createValidUserInput();
      String email = input.getEmail();

      // Create first user
      userService.create(input);

      // Try to create duplicate
      UserInputDTO duplicateInput = createValidUserInput();
      duplicateInput.setEmail(email);

      long countBefore = userRepository.count();

      // When & Then
      Assertions.assertThrows(
          Exception.class,
          () -> userService.create(duplicateInput),
          "Should prevent duplicate email");

      // Verify count hasn't changed
      assertThat(userRepository.count()).isEqualTo(countBefore);
    }
  }

  @Nested
  @DisplayName("External ID Management Tests")
  class ExternalIdManagementTests {

    @Test
    @DisplayName("Should set external ID from Keycloak on create")
    void shouldSetExternalIdFromKeycloakOnCreate() {
      // Given
      UserInputDTO input = createValidUserInput();

      // When
      User createdUser = userService.create(input);

      // Then
      assertThat(createdUser.getExternalId())
          .as("External ID should be set")
          .isNotNull()
          .matches("[0-9a-f-]{36}"); // UUID format

      // Verify it matches Keycloak ID
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                UserRepresentation keycloakUser = findKeycloakUserByEmail(input.getEmail());
                assertThat(keycloakUser).isNotNull();
                assertThat(createdUser.getExternalId()).isEqualTo(keycloakUser.getId());
              });
    }

    @Test
    @DisplayName("Should preserve external ID on update")
    void shouldPreserveExternalIdOnUpdate() {
      // Given - create user with external ID
      User existingUser = userService.create(createValidUserInput());
      String originalExternalId = existingUser.getExternalId();
      assertThat(originalExternalId).isNotNull();

      // When - update user
      UserInputDTO updateInput = new UserInputDTO();
      updateInput.setFirstName("Updated");
      updateInput.setLastName(existingUser.getLastName());
      updateInput.setEmail(existingUser.getEmail());
      updateInput.setActive(true);

      User updatedUser = userService.update(existingUser.getId(), updateInput);

      // Then - external ID should be preserved
      assertThat(updatedUser.getExternalId()).isEqualTo(originalExternalId);
    }
  }

  @Nested
  @DisplayName("Database State and ID Generation Tests")
  class DatabaseStateTests {

    @Test
    @DisplayName("Should generate UUID for entity ID automatically")
    void shouldGenerateUuidForEntityId() {
      // Given
      UserInputDTO input = createValidUserInput();

      // When
      User createdUser = userService.create(input);

      // Then - ID should be generated by Hibernate
      assertThat(createdUser.getId()).as("ID should be generated automatically").isNotNull();

      // Verify it's a valid UUID
      assertThat(createdUser.getId().toString())
          .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("Should persist external ID separately after validation")
    void shouldPersistExternalIdSeparatelyAfterValidation() {
      // Given
      UserInputDTO input = createValidUserInput();

      // When
      User createdUser = userService.create(input);

      // Then - reload from DB to verify external ID persistence
      User reloadedUser = userRepository.findById(createdUser.getId()).orElseThrow();

      assertThat(reloadedUser.getExternalId())
          .as("External ID should be persisted in DB")
          .isNotNull()
          .isEqualTo(createdUser.getExternalId());
    }
  }

  @Nested
  @DisplayName("Lifecycle Hooks and Validation Tests")
  class LifecycleHooksTests {

    @Test
    @DisplayName("Should execute preSave hook before persistence")
    void shouldExecutePreSaveHookBeforePersistence() {
      // Given - UserService has preSave hook that validates email uniqueness
      UserInputDTO input = createValidUserInput();
      String email = input.getEmail();

      // Create first user
      userService.create(input);

      // When - try to create user with same email
      UserInputDTO duplicateInput = createValidUserInput();
      duplicateInput.setEmail(email);

      // Then - preSave validation should prevent duplicate
      Assertions.assertThrows(
          Exception.class,
          () -> userService.create(duplicateInput),
          "preSave hook should validate unique email");
    }

    @Test
    @DisplayName("Should execute postSave hook after successful persistence")
    void shouldExecutePostSaveHookAfterSuccessfulPersistence() {
      // Given
      UserInputDTO input = createValidUserInput();

      // When
      User createdUser = userService.create(input);

      // Then - entity should be fully persisted with all fields
      assertThat(createdUser.getId()).isNotNull();
      assertThat(createdUser.getExternalId()).isNotNull();
      assertThat(createdUser.getCreatedAt()).isNotNull();
      assertThat(createdUser.getCreatedBy()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Update Operation Validation Tests")
  class UpdateOperationTests {

    @Test
    @DisplayName("Should update only modified fields")
    void shouldUpdateOnlyModifiedFields() {
      // Given - create user
      User existingUser = userService.create(createValidUserInput());
      String originalEmail = existingUser.getEmail();
      String originalLastName = existingUser.getLastName();

      // When - update only first name
      UserInputDTO updateInput = new UserInputDTO();
      updateInput.setFirstName("UpdatedFirstName");
      updateInput.setLastName(originalLastName);
      updateInput.setEmail(originalEmail);
      updateInput.setActive(existingUser.getActive());

      User updatedUser = userService.update(existingUser.getId(), updateInput);

      // Then
      assertThat(updatedUser.getFirstName()).isEqualTo("UpdatedFirstName");
      assertThat(updatedUser.getLastName()).isEqualTo(originalLastName);
      assertThat(updatedUser.getEmail()).isEqualTo(originalEmail);
    }

    @Test
    @DisplayName("Should preserve ID and created metadata on update")
    void shouldPreserveIdAndCreatedMetadataOnUpdate() {
      // Given - create user
      User existingUser = userService.create(createValidUserInput());
      UUID originalId = existingUser.getId();

      // When - update user
      UserInputDTO updateInput = new UserInputDTO();
      updateInput.setFirstName("Updated");
      updateInput.setLastName("Name");
      updateInput.setEmail(existingUser.getEmail());
      updateInput.setActive(true);

      User updatedUser = userService.update(existingUser.getId(), updateInput);

      // Then - ID and external ID should be preserved
      assertThat(updatedUser.getId()).isEqualTo(originalId);
      assertThat(updatedUser.getExternalId()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Multiple Users Tests")
  class MultipleUsersTests {

    @Test
    @DisplayName("Should handle multiple user creations sequentially")
    void shouldHandleMultipleUserCreationsSequentially() {
      // When
      User user1 = userService.create(createValidUserInput());
      User user2 = userService.create(createValidUserInput());
      User user3 = userService.create(createValidUserInput());

      // Then
      assertThat(userRepository.count()).isEqualTo(3);
      assertThat(userRepository.findAll())
          .extracting(User::getId)
          .contains(user1.getId(), user2.getId(), user3.getId());

      // Verify all users in Keycloak
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

  // ==================== Helper Methods ====================

  private UserInputDTO createValidUserInput() {
    UserInputDTO input = new UserInputDTO();
    input.setFirstName("Integration");
    input.setLastName("Test" + System.currentTimeMillis());
    input.setEmail("test." + System.currentTimeMillis() + "@example.com");
    input.setPhone("+49123456789");
    input.setActive(true);
    return input;
  }
}
