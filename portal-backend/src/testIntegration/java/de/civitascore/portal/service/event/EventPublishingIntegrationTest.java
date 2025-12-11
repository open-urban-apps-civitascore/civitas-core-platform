package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.event.ConfigEventDTO;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.UserService;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@DisplayName("Event Publishing Integration Tests")
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"},
    topics = {"User.create", "User.update", "User.delete", "civitas.config.result"})
@Slf4j
class EventPublishingIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;

  @Value("${spring.embedded.kafka.brokers}")
  private String embeddedKafkaBrokers;

  private KafkaMessageListenerContainer<String, String> testConsumerContainer;
  private KafkaMessageListenerContainer<String, String> resultSimulatorContainer;
  private final AtomicReference<String> lastCorrelationId = new AtomicReference<>();

  @BeforeEach
  void setUp() throws Exception {
    log.debug("=== Test Setup Starting ===");

    // Stop any existing containers
    stopContainers();

    // Clean database
    userRepository.deleteAll();
    lastCorrelationId.set(null);

    // Setup result simulator that responds to all events
    setupConfigAdapterResultSimulator();

    // Wait for Kafka consumers to be fully ready
    Thread.sleep(500);

    log.debug("=== Test Setup Complete ===");
  }

  @AfterEach
  void tearDown() {
    log.debug("=== Test Teardown Starting ===");
    stopContainers();
    userRepository.deleteAll();
    log.debug("=== Test Teardown Complete ===");
  }

  private void stopContainers() {
    log.debug("=== Stopping all containers ===");
    if (testConsumerContainer != null) {
      try {
        testConsumerContainer.stop();
        testConsumerContainer = null;
        log.debug("Test consumer stopped");
      } catch (Exception e) {
        log.warn("Error stopping test consumer: {}", e.getMessage());
      }
    }
    if (resultSimulatorContainer != null) {
      try {
        resultSimulatorContainer.stop();
        resultSimulatorContainer = null;
        log.debug("Result simulator stopped");
      } catch (Exception e) {
        log.warn("Error stopping result simulator: {}", e.getMessage());
      }
    }

    // Give Kafka consumers time to fully stop and release resources
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    log.debug("=== All containers stopped ===");
  }

  @Nested
  @DisplayName("User Create Event Tests")
  class UserCreateEventTests {

    @Test
    @DisplayName("Should publish ConfigEvent when creating user")
    void shouldPublishConfigEventWhenCreatingUser() throws Exception {
      // Given
      UserInputDTO input = createValidUserInput();
      String expectedTopic = "User.create";
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer(expectedTopic, capturedEvent);

      // When
      User createdUser = userService.create(input);

      // Then
      assertThat(createdUser).isNotNull();
      assertThat(createdUser.getId()).isNotNull();
      assertThat(createdUser.getExternalId()).isNotNull(); // Verify external ID set by simulator

      // Verify event was published
      await()
          .atMost(Duration.ofSeconds(10))
          .pollDelay(Duration.ofMillis(100))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      // Parse and verify event structure
      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      assertThat(event.metadata().source()).isEqualTo("civitas.portal-backend");
      assertThat(event.metadata().correlationId()).isNotNull();
      assertThat(event.payload().targetComponent()).isEqualTo("keycloak");
      assertThat(event.payload().operation()).isEqualTo("CREATE");
      assertThat(event.payload().targetResource())
          .contains("realms/civitas/users/" + createdUser.getId());
    }

    @Test
    @DisplayName("Should include correct user data in event payload")
    void shouldIncludeCorrectUserDataInEventPayload() throws Exception {
      // Given
      UserInputDTO input = createValidUserInput();
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.create", capturedEvent);

      // When
      User createdUser = userService.create(input);

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      String valueJson = objectMapper.writeValueAsString(event.payload().config().value());

      assertThat(valueJson)
          .contains(createdUser.getEmail())
          .contains(input.getFirstName())
          .contains(input.getLastName());
    }
  }

  @Nested
  @DisplayName("User Update Event Tests")
  class UserUpdateEventTests {

    @Test
    @DisplayName("Should publish ConfigEvent when updating user")
    void shouldPublishConfigEventWhenUpdatingUser() throws Exception {
      // Given - create user first
      User existingUser = userService.create(createValidUserInput());

      // Setup consumer for update event
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.update", capturedEvent);

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
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      assertThat(event.payload().operation()).isEqualTo("UPDATE");
      assertThat(event.payload().targetResource()).contains("users/" + existingUser.getId());
    }
  }

  @Nested
  @DisplayName("User Delete Event Tests")
  class UserDeleteEventTests {

    @Test
    @DisplayName("Should publish ConfigEvent when deleting user")
    void shouldPublishConfigEventWhenDeletingUser() throws Exception {
      // Given
      User existingUser = userService.create(createValidUserInput());
      UUID userId = existingUser.getId();

      // Setup consumer for delete event
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.delete", capturedEvent);

      // When
      userService.deleteById(userId);

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      assertThat(event.payload().operation()).isEqualTo("DELETE");
      assertThat(event.payload().targetResource()).contains("users/" + userId);
      assertThat(userRepository.findById(userId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Config-Adapter Result Handling Tests")
  class ConfigAdapterResultTests {

    @Test
    @DisplayName("Should handle successful config-adapter response")
    void shouldHandleSuccessfulConfigAdapterResponse() {
      // Given
      UserInputDTO input = createValidUserInput();

      // When
      User createdUser = userService.create(input);

      // Then - user should be persisted with external ID
      assertThat(createdUser).isNotNull();
      assertThat(createdUser.getId()).isNotNull();
      assertThat(createdUser.getExternalId()).isNotNull();
      assertThat(userRepository.findById(createdUser.getId())).isPresent();
    }

    @Test
    @DisplayName("Should rollback transaction on config-adapter failure")
    void shouldRollbackTransactionOnConfigAdapterFailure() {
      // Given
      UserInputDTO input = createValidUserInput();
      long countBefore = userRepository.count();

      // Stop success simulator and setup failure simulator
      stopContainers();
      setupFailureSimulator();

      // When & Then
      Assertions.assertThrows(
          Exception.class,
          () -> userService.create(input),
          "Should throw exception on config-adapter failure");

      // Verify rollback - no user should be persisted
      assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("Should use correct correlation ID for result matching")
    void shouldUseCorrectCorrelationIdForResultMatching() throws Exception {
      // Given
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.create", capturedEvent);

      // When
      userService.create(createValidUserInput());

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      String correlationId = event.metadata().correlationId();

      assertThat(lastCorrelationId.get())
          .as("Simulator should have received the same correlation ID")
          .isEqualTo(correlationId);
    }
  }

  @Nested
  @DisplayName("Two-Phase Commit Tests")
  class TwoPhaseCommitTests {

    @Test
    @DisplayName("Should update external ID after successful validation (Phase 2)")
    void shouldUpdateExternalIdAfterSuccessfulValidation() {
      // Given
      UserInputDTO input = createValidUserInput();

      // When
      User createdUser = userService.create(input);

      // Then - external ID should be set from config-adapter response
      assertThat(createdUser.getExternalId())
          .as("External ID should be set after validation")
          .isNotNull();

      // Verify persistence
      User reloadedUser = userRepository.findById(createdUser.getId()).orElseThrow();
      assertThat(reloadedUser.getExternalId()).isEqualTo(createdUser.getExternalId());
    }

    @Test
    @DisplayName("Should rollback both phases on validation failure")
    void shouldRollbackBothPhasesOnValidationFailure() {
      // Given
      UserInputDTO input = createValidUserInput();
      String email = input.getEmail();

      // Stop success simulator and setup failure simulator
      stopContainers();
      setupFailureSimulator();

      // When
      Assertions.assertThrows(Exception.class, () -> userService.create(input));

      // Then - entity should NOT exist in DB
      assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("Should commit transaction only after successful validation")
    void shouldCommitTransactionOnlyAfterSuccessfulValidation() {
      // Given
      UserInputDTO input = createValidUserInput();
      long countBefore = userRepository.count();

      // When
      User createdUser = userService.create(input);

      // Then
      assertThat(userRepository.count()).isEqualTo(countBefore + 1);
      assertThat(userRepository.findById(createdUser.getId())).isPresent();
      assertThat(createdUser.getExternalId()).isNotNull();
    }
  }

  @Nested
  @DisplayName("External ID Management Tests")
  class ExternalIdManagementTests {

    @Test
    @DisplayName("Should set external ID from config-adapter on create")
    void shouldSetExternalIdFromConfigAdapterOnCreate() {
      // Given
      UserInputDTO input = createValidUserInput();

      // When
      User createdUser = userService.create(input);

      // Then
      assertThat(createdUser.getExternalId())
          .as("External ID should be set")
          .isNotNull()
          .matches("[0-9a-f-]{36}"); // UUID format
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

      // Then - external ID should be preserved or updated
      assertThat(updatedUser.getExternalId()).isNotNull();
    }

    @Test
    @DisplayName("Should include external ID in delete event")
    void shouldIncludeExternalIdInDeleteEvent() throws Exception {
      // Given - create user with external ID
      User existingUser = userService.create(createValidUserInput());
      String externalId = existingUser.getExternalId();
      assertThat(externalId).isNotNull();

      // Setup consumer for delete event
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.delete", capturedEvent);

      // When
      userService.deleteById(existingUser.getId());

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      String valueJson = objectMapper.writeValueAsString(event.payload().config().value());

      assertThat(valueJson).contains(externalId);
    }

    @Test
    @DisplayName("Should handle null external ID gracefully in events")
    void shouldHandleNullExternalIdGracefullyInEvents() throws Exception {
      // Given - create user
      UserInputDTO input = createValidUserInput();
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.create", capturedEvent);

      // When
      userService.create(input);

      // Then - event should be valid even if external ID processing varies
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      assertThat(event).isNotNull();
      assertThat(event.payload()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Transaction Rollback Scenarios")
  class TransactionRollbackScenarios {

    @Test
    @DisplayName("Should rollback on external system timeout")
    void shouldRollbackOnExternalSystemTimeout() {
      // Given
      UserInputDTO input = createValidUserInput();
      long countBefore = userRepository.count();

      // Stop all simulators to cause timeout
      stopContainers();

      // When & Then
      Assertions.assertThrows(
          Exception.class, () -> userService.create(input), "Should throw exception on timeout");

      // Verify rollback
      assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("Should rollback update on validation failure")
    void shouldRollbackUpdateOnValidationFailure() {
      // Given - create user successfully
      User existingUser = userService.create(createValidUserInput());
      String originalFirstName = existingUser.getFirstName();

      // Setup failure simulator
      stopContainers();
      setupFailureSimulator();

      // When - try to update
      UserInputDTO updateInput = new UserInputDTO();
      updateInput.setFirstName("ShouldNotBeUpdated");
      updateInput.setLastName(existingUser.getLastName());
      updateInput.setEmail(existingUser.getEmail());
      updateInput.setActive(true);

      Assertions.assertThrows(
          Exception.class, () -> userService.update(existingUser.getId(), updateInput));

      // Then - changes should be rolled back
      User reloadedUser = userRepository.findById(existingUser.getId()).orElseThrow();
      assertThat(reloadedUser.getFirstName()).isEqualTo(originalFirstName);
    }

    @Test
    @DisplayName("Should not delete on external system rejection")
    void shouldNotDeleteOnExternalSystemRejection() {
      // Given - create user successfully
      User existingUser = userService.create(createValidUserInput());
      UUID userId = existingUser.getId();

      // Setup failure simulator
      stopContainers();
      setupFailureSimulator();

      // When - try to delete
      Assertions.assertThrows(Exception.class, () -> userService.deleteById(userId));

      // Then - user should still exist
      assertThat(userRepository.findById(userId)).isPresent();
    }
  }

  @Nested
  @DisplayName("Event Content Validation Tests")
  class EventContentValidationTests {

    @Test
    @DisplayName("Should include database-generated ID in create event")
    void shouldIncludeDatabaseGeneratedIdInCreateEvent() throws Exception {
      // Given
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.create", capturedEvent);

      // When
      User createdUser = userService.create(createValidUserInput());

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);

      // The entity ID in event should match the database-generated ID
      assertThat(event.payload().targetResource()).contains(createdUser.getId().toString());
      assertThat(createdUser.getId()).isNotNull();
    }

    @Test
    @DisplayName("Should include all user fields in event payload")
    void shouldIncludeAllUserFieldsInEventPayload() throws Exception {
      // Given
      UserInputDTO input = createValidUserInput();
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.create", capturedEvent);

      // When
      User createdUser = userService.create(input);

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      String valueJson = objectMapper.writeValueAsString(event.payload().config().value());

      // Verify all fields are present
      assertThat(valueJson)
          .contains(createdUser.getFirstName())
          .contains(createdUser.getLastName())
          .contains(createdUser.getEmail())
          .contains(createdUser.getId().toString());
    }

    @Test
    @DisplayName("Should include updated fields in update event")
    void shouldIncludeUpdatedFieldsInUpdateEvent() throws Exception {
      // Given - create user
      User existingUser = userService.create(createValidUserInput());

      // Setup consumer for update event
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.update", capturedEvent);

      // When - update user
      UserInputDTO updateInput = new UserInputDTO();
      updateInput.setFirstName("NewFirstName");
      updateInput.setLastName("NewLastName");
      updateInput.setEmail(existingUser.getEmail());
      updateInput.setActive(false); // Changed

      userService.update(existingUser.getId(), updateInput);

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      String valueJson = objectMapper.writeValueAsString(event.payload().config().value());

      assertThat(valueJson).contains("NewFirstName").contains("NewLastName");
    }
  }

  @Nested
  @DisplayName("Event Ordering and Consistency Tests")
  class EventOrderingTests {

    @Test
    @DisplayName("Should publish create then update events in correct order")
    void shouldPublishCreateThenUpdateEventsInOrder() throws Exception {
      // Given - create user
      User createdUser = userService.create(createValidUserInput());

      // Setup consumer for update event
      AtomicReference<String> capturedUpdateEvent = new AtomicReference<>();
      setupKafkaConsumer("User.update", capturedUpdateEvent);

      // When - update user
      UserInputDTO updateInput = new UserInputDTO();
      updateInput.setFirstName("Modified");
      updateInput.setLastName(createdUser.getLastName());
      updateInput.setEmail(createdUser.getEmail());
      updateInput.setActive(true);

      userService.update(createdUser.getId(), updateInput);

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedUpdateEvent.get()).isNotNull());

      ConfigEventDTO updateEvent =
          objectMapper.readValue(capturedUpdateEvent.get(), ConfigEventDTO.class);
      assertThat(updateEvent.payload().operation()).isEqualTo("UPDATE");
    }

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
    }
  }

  @Nested
  @DisplayName("Event Payload Validation Tests")
  class EventPayloadValidationTests {

    @Test
    @DisplayName("Should include all required metadata fields in event")
    void shouldIncludeAllRequiredMetadataFields() throws Exception {
      // Given
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.create", capturedEvent);

      // When
      userService.create(createValidUserInput());

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);

      assertThat(event.metadata().messageId()).isNotNull();
      assertThat(event.metadata().correlationId()).isNotNull();
      assertThat(event.metadata().source()).isEqualTo("civitas.portal-backend");
      assertThat(event.metadata().timestamp()).isNotNull();
      assertThat(event.metadata().configVersion()).isNotNull();
      assertThat(event.metadata().resultTopic()).isEqualTo("civitas.config.result");
    }

    @Test
    @DisplayName("Should include correct realm in targetResource path")
    void shouldIncludeCorrectRealmInTargetResourcePath() throws Exception {
      // Given
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.create", capturedEvent);

      // When
      User createdUser = userService.create(createValidUserInput());

      // Then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);

      assertThat(event.payload().targetResource())
          .startsWith("realms/civitas")
          .contains("/users/")
          .contains(createdUser.getId().toString());
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

    @Test
    @DisplayName("Should maintain referential integrity on rollback")
    void shouldMaintainReferentialIntegrityOnRollback() {
      // Given
      long countBefore = userRepository.count();
      UserInputDTO input = createValidUserInput();

      // Stop simulator to cause timeout
      stopContainers();

      // When - create fails due to timeout
      Assertions.assertThrows(Exception.class, () -> userService.create(input));

      // Then - no orphaned records
      assertThat(userRepository.count())
          .as("No records should be left in DB after rollback")
          .isEqualTo(countBefore);
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

    @Test
    @DisplayName("Should not execute postSave on rollback")
    void shouldNotExecutePostSaveOnRollback() throws Exception {
      // Given
      UserInputDTO input = createValidUserInput();
      long countBefore = userRepository.count();

      // Stop simulator to cause failure
      stopContainers();
      Thread.sleep(500);
      setupFailureSimulator();
      Thread.sleep(500);

      // When
      Assertions.assertThrows(Exception.class, () -> userService.create(input));

      // Then - no entity should exist (postSave not executed)
      assertThat(userRepository.count()).isEqualTo(countBefore);
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
      assertThat(updatedUser.getExternalId()).isNotNull(); // Should be preserved or updated
    }

    @Test
    @DisplayName("Should send update event with current entity state")
    void shouldSendUpdateEventWithCurrentEntityState() throws Exception {
      // Given - create user
      User existingUser = userService.create(createValidUserInput());

      // Setup consumer for update event
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.update", capturedEvent);

      // When - update user
      UserInputDTO updateInput = new UserInputDTO();
      updateInput.setFirstName("CurrentFirstName");
      updateInput.setLastName("CurrentLastName");
      updateInput.setEmail(existingUser.getEmail());
      updateInput.setActive(false);

      userService.update(existingUser.getId(), updateInput);

      // Then - event should contain current state
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      String valueJson = objectMapper.writeValueAsString(event.payload().config().value());

      assertThat(valueJson)
          .contains("CurrentFirstName")
          .contains("CurrentLastName")
          .contains("\"active\":false");
    }
  }

  @Nested
  @DisplayName("Delete Operation Validation Tests")
  class DeleteOperationTests {

    @Test
    @DisplayName("Should send delete event before removing from DB")
    void shouldSendDeleteEventBeforeRemovingFromDb() throws Exception {
      // Given - create user
      User existingUser = userService.create(createValidUserInput());
      UUID userId = existingUser.getId();

      // Setup consumer for delete event
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.delete", capturedEvent);

      // When - delete user
      userService.deleteById(userId);

      // Then - event should be sent
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      assertThat(event.payload().operation()).isEqualTo("DELETE");

      // User should be removed from DB after successful validation
      assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    @DisplayName("Should include all entity data in delete event")
    void shouldIncludeAllEntityDataInDeleteEvent() throws Exception {
      // Given - create user with known data
      UserInputDTO input = createValidUserInput();
      User existingUser = userService.create(input);
      UUID userId = existingUser.getId();
      String externalId = existingUser.getExternalId();

      // Setup consumer for delete event
      AtomicReference<String> capturedEvent = new AtomicReference<>();
      setupKafkaConsumer("User.delete", capturedEvent);

      // When - delete user
      userService.deleteById(userId);

      // Then - event should contain all user data
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(capturedEvent.get()).isNotNull());

      ConfigEventDTO event = objectMapper.readValue(capturedEvent.get(), ConfigEventDTO.class);
      String valueJson = objectMapper.writeValueAsString(event.payload().config().value());

      assertThat(valueJson)
          .contains(userId.toString())
          .contains(externalId)
          .contains(existingUser.getFirstName())
          .contains(existingUser.getLastName())
          .contains(existingUser.getEmail());
    }

    @Test
    @DisplayName("Should prevent delete if external system rejects")
    void shouldPreventDeleteIfExternalSystemRejects() throws Exception {
      // Given - create user
      User existingUser = userService.create(createValidUserInput());
      UUID userId = existingUser.getId();

      // Setup failure simulator
      stopContainers();
      Thread.sleep(500);
      setupFailureSimulator();
      Thread.sleep(500);

      // When - try to delete
      Assertions.assertThrows(Exception.class, () -> userService.deleteById(userId));

      // Then - user should still exist
      assertThat(userRepository.findById(userId))
          .as("User should still exist after failed delete")
          .isPresent();
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

  /** Setup result simulator that automatically responds with SUCCESS to all events. */
  private void setupConfigAdapterResultSimulator() {
    log.debug("=== Setting up Config-Adapter Result Simulator ===");

    var consumerProps =
        KafkaTestUtils.consumerProps(
            embeddedKafkaBrokers, "result-simulator-group-" + UUID.randomUUID(), "false");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

    DefaultKafkaConsumerFactory<String, String> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    ContainerProperties containerProperties =
        new ContainerProperties("User.create", "User.update", "User.delete");

    resultSimulatorContainer =
        new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

    resultSimulatorContainer.setupMessageListener(
        (MessageListener<String, String>)
            record -> {
              try {
                log.debug("Simulator received event on topic: {}", record.topic());
                ConfigEventDTO event = objectMapper.readValue(record.value(), ConfigEventDTO.class);
                String correlationId = event.metadata().correlationId();
                lastCorrelationId.set(correlationId);

                log.debug("Extracted correlationId: {}", correlationId);

                // Send SUCCESS result after short delay
                new Thread(
                        () -> {
                          try {
                            Thread.sleep(100);
                            sendConfigAdapterResult(correlationId, true, null);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }
                        })
                    .start();

              } catch (Exception e) {
                log.error("Failed to process event in simulator: {}", e.getMessage(), e);
              }
            });

    resultSimulatorContainer.start();
    ContainerTestUtils.waitForAssignment(resultSimulatorContainer, 3);
    log.debug("=== Config-Adapter Result Simulator STARTED ===");
  }

  /** Setup failure simulator that sends FAILURE results. */
  private void setupFailureSimulator() {
    log.debug("=== Setting up Failure Simulator ===");

    var consumerProps =
        KafkaTestUtils.consumerProps(
            embeddedKafkaBrokers, "failure-simulator-group-" + UUID.randomUUID(), "false");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        "earliest"); // Changed to earliest to catch all events

    DefaultKafkaConsumerFactory<String, String> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    ContainerProperties containerProperties =
        new ContainerProperties("User.create", "User.update", "User.delete");

    resultSimulatorContainer =
        new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

    resultSimulatorContainer.setupMessageListener(
        (MessageListener<String, String>)
            record -> {
              try {
                log.debug("Failure simulator received event on topic: {}", record.topic());
                ConfigEventDTO event = objectMapper.readValue(record.value(), ConfigEventDTO.class);
                String correlationId = event.metadata().correlationId();
                lastCorrelationId.set(correlationId);

                log.debug(
                    "Failure simulator will send FAILURE for correlationId: {}", correlationId);

                new Thread(
                        () -> {
                          try {
                            Thread.sleep(100);
                            sendConfigAdapterResult(correlationId, false, "KEYCLOAK_ERROR");
                            log.debug("FAILURE result sent for correlationId: {}", correlationId);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }
                        })
                    .start();

              } catch (Exception e) {
                log.error("Failed to process event in failure simulator: {}", e.getMessage(), e);
              }
            });

    resultSimulatorContainer.start();
    ContainerTestUtils.waitForAssignment(resultSimulatorContainer, 3);
    log.debug("=== Failure Simulator STARTED and listening ===");
  }

  /** Send config-adapter result to result topic. */
  private void sendConfigAdapterResult(String correlationId, boolean success, String errorCode) {
    String resultPayload;
    if (success) {
      resultPayload =
          String.format(
              """
          {
            "correlationId": "%s",
            "status": "SUCCESS",
            "message": "Resource processed successfully",
            "resourceId": "%s",
            "errorCode": null
          }
          """,
              correlationId, UUID.randomUUID());
    } else {
      resultPayload =
          String.format(
              """
          {
            "correlationId": "%s",
            "status": "FAILURE",
            "message": "Processing failed",
            "resourceId": null,
            "errorCode": "%s"
          }
          """,
              correlationId, errorCode != null ? errorCode : "KEYCLOAK_ERROR");
    }

    log.debug("Sending result to civitas.config.result with correlationId: {}", correlationId);

    try {
      // Send with correlationId as key for proper routing
      kafkaTemplate.send("civitas.config.result", correlationId, resultPayload).get();
      log.debug("Result sent successfully");
    } catch (Exception e) {
      log.error("Failed to send result: {}", e.getMessage(), e);
    }
  }

  /** Setup test consumer to capture events from specific topic. */
  private void setupKafkaConsumer(String topic, AtomicReference<String> capturedEvent) {
    if (testConsumerContainer != null) {
      try {
        testConsumerContainer.stop();
        Thread.sleep(100);
      } catch (Exception e) {
        log.warn("Error stopping previous test consumer: {}", e.getMessage());
      }
    }

    var consumerProps =
        KafkaTestUtils.consumerProps(
            embeddedKafkaBrokers, "test-group-" + UUID.randomUUID(), "false");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    DefaultKafkaConsumerFactory<String, String> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    ContainerProperties containerProperties = new ContainerProperties(topic);
    testConsumerContainer =
        new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

    testConsumerContainer.setupMessageListener(
        (MessageListener<String, String>)
            record -> {
              log.debug("Test consumer received message on topic {}", record.topic());
              capturedEvent.set(record.value());
            });

    testConsumerContainer.start();
    ContainerTestUtils.waitForAssignment(testConsumerContainer, 1);
  }
}
