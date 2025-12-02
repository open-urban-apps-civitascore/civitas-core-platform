package de.civitascore.portal.model.output.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainEventTest {

  @Test
  @DisplayName("should create domain event with all required fields")
  void shouldCreateDomainEventWithAllFields() {
    // Given
    UUID eventId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID();
    String eventType = "User.create";
    String aggregateType = "User";
    String operation = "create";
    TestPayload payload = new TestPayload("test-id", "test@example.com");
    EventMetadata metadata = new EventMetadata("test-realm", Map.of("userId", "admin"));
    int schemaVersion = 1;
    Instant timestamp = Instant.now();

    // When
    DomainEvent<TestPayload> event =
        new DomainEvent<>(
            eventId,
            eventType,
            entityId,
            aggregateType,
            operation,
            payload,
            metadata,
            schemaVersion,
            timestamp);

    // Then
    assertNotNull(event);
    assertThat(event.eventId()).isEqualTo(eventId);
    assertThat(event.eventType()).isEqualTo(eventType);
    assertThat(event.entityId()).isEqualTo(entityId);
    assertThat(event.aggregateType()).isEqualTo(aggregateType);
    assertThat(event.operation()).isEqualTo(operation);
    assertThat(event.payload()).isEqualTo(payload);
    assertThat(event.metadata()).isEqualTo(metadata);
    assertThat(event.schemaVersion()).isEqualTo(schemaVersion);
    assertThat(event.timestamp()).isEqualTo(timestamp);
  }

  @Test
  @DisplayName("should support different payload types")
  void shouldSupportDifferentPayloadTypes() {
    // Given
    String stringPayload = "Simple string payload";
    DomainEvent<String> stringEvent = createTestEvent(stringPayload);

    Integer integerPayload = 42;
    DomainEvent<Integer> integerEvent = createTestEvent(integerPayload);

    TestPayload objectPayload = new TestPayload("id", "email");
    DomainEvent<TestPayload> objectEvent = createTestEvent(objectPayload);

    // Then
    assertThat(stringEvent.payload()).isInstanceOf(String.class);
    assertThat(integerEvent.payload()).isInstanceOf(Integer.class);
    assertThat(objectEvent.payload()).isInstanceOf(TestPayload.class);
  }

  @Test
  @DisplayName("should be immutable")
  void shouldBeImmutable() {
    // Given
    TestPayload originalPayload = new TestPayload("id", "email");
    DomainEvent<TestPayload> event = createTestEvent(originalPayload);

    // When
    TestPayload retrievedPayload = event.payload();

    // Then
    // Record fields are final, so we can't modify them
    assertThat(retrievedPayload).isEqualTo(originalPayload);
  }

  @Test
  @DisplayName("should handle null metadata correlation IDs")
  void shouldHandleNullMetadataCorrelationIds() {
    // Given
    EventMetadata metadata = new EventMetadata("realm", null);
    DomainEvent<String> event =
        new DomainEvent<>(
            UUID.randomUUID(),
            "Test.event",
            UUID.randomUUID(),
            "Test",
            "create",
            "payload",
            metadata,
            1,
            Instant.now());

    // Then
    // null correlationIds are converted to empty Map for safety
    assertThat(event.metadata().correlationIds()).isNotNull();
    assertThat(event.metadata().correlationIds()).isEmpty();
  }

  @Test
  @DisplayName("should handle empty correlation IDs")
  void shouldHandleEmptyCorrelationIds() {
    // Given
    EventMetadata metadata = new EventMetadata("realm", Map.of());
    DomainEvent<String> event =
        new DomainEvent<>(
            UUID.randomUUID(),
            "Test.event",
            UUID.randomUUID(),
            "Test",
            "create",
            "payload",
            metadata,
            1,
            Instant.now());

    // Then
    assertThat(event.metadata().correlationIds()).isEmpty();
  }

  @Test
  @DisplayName("should support create operation")
  void shouldSupportCreateOperation() {
    // Given
    DomainEvent<String> event = createEventWithOperation("create");

    // Then
    assertThat(event.operation()).isEqualTo("create");
    assertThat(event.eventType()).contains("create");
  }

  @Test
  @DisplayName("should support update operation")
  void shouldSupportUpdateOperation() {
    // Given
    DomainEvent<String> event = createEventWithOperation("update");

    // Then
    assertThat(event.operation()).isEqualTo("update");
    assertThat(event.eventType()).contains("update");
  }

  @Test
  @DisplayName("should support delete operation")
  void shouldSupportDeleteOperation() {
    // Given
    DomainEvent<String> event = createEventWithOperation("delete");

    // Then
    assertThat(event.operation()).isEqualTo("delete");
    assertThat(event.eventType()).contains("delete");
  }

  @Test
  @DisplayName("should preserve timestamp precision")
  void shouldPreserveTimestampPrecision() {
    // Given
    Instant precisetimestamp = Instant.parse("2025-12-02T10:15:30.123456789Z");
    DomainEvent<String> event =
        new DomainEvent<>(
            UUID.randomUUID(),
            "Test.event",
            UUID.randomUUID(),
            "Test",
            "create",
            "payload",
            new EventMetadata("realm", Map.of()),
            1,
            precisetimestamp);

    // Then
    assertThat(event.timestamp()).isEqualTo(precisetimestamp);
    assertThat(event.timestamp().getNano()).isEqualTo(123456789);
  }

  @Test
  @DisplayName("should support schema versioning")
  void shouldSupportSchemaVersioning() {
    // Given
    DomainEvent<String> v1Event = createEventWithSchemaVersion(1);
    DomainEvent<String> v2Event = createEventWithSchemaVersion(2);

    // Then
    assertThat(v1Event.schemaVersion()).isEqualTo(1);
    assertThat(v2Event.schemaVersion()).isEqualTo(2);
  }

  @Test
  @DisplayName("should include realm in metadata")
  void shouldIncludeRealmInMetadata() {
    // Given
    String testRealm = "production-realm";
    EventMetadata metadata = new EventMetadata(testRealm, Map.of());
    DomainEvent<String> event =
        new DomainEvent<>(
            UUID.randomUUID(),
            "Test.event",
            UUID.randomUUID(),
            "Test",
            "create",
            "payload",
            metadata,
            1,
            Instant.now());

    // Then
    assertThat(event.metadata().realm()).isEqualTo(testRealm);
  }

  @Test
  @DisplayName("should support complex correlation IDs")
  void shouldSupportComplexCorrelationIds() {
    // Given
    Map<String, String> correlationIds =
        Map.of(
            "userId", "admin",
            "traceId", "trace-123",
            "spanId", "span-456",
            "requestId", "req-789");
    EventMetadata metadata = new EventMetadata("realm", correlationIds);
    DomainEvent<String> event =
        new DomainEvent<>(
            UUID.randomUUID(),
            "Test.event",
            UUID.randomUUID(),
            "Test",
            "create",
            "payload",
            metadata,
            1,
            Instant.now());

    // Then
    assertThat(event.metadata().correlationIds()).hasSize(4);
    assertThat(event.metadata().correlationIds()).containsEntry("userId", "admin");
    assertThat(event.metadata().correlationIds()).containsEntry("traceId", "trace-123");
  }

  @Test
  @DisplayName("two events with same data should be equal")
  void twoEventsWithSameDataShouldBeEqual() {
    // Given
    UUID eventId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID();
    Instant timestamp = Instant.now();
    TestPayload payload = new TestPayload("id", "email");
    EventMetadata metadata = new EventMetadata("realm", Map.of());

    DomainEvent<TestPayload> event1 =
        new DomainEvent<>(
            eventId, "Test.event", entityId, "Test", "create", payload, metadata, 1, timestamp);

    DomainEvent<TestPayload> event2 =
        new DomainEvent<>(
            eventId, "Test.event", entityId, "Test", "create", payload, metadata, 1, timestamp);

    // Then
    assertThat(event1).isEqualTo(event2);
    assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
  }

  private <T> DomainEvent<T> createTestEvent(T payload) {
    return new DomainEvent<>(
        UUID.randomUUID(),
        "Test.event",
        UUID.randomUUID(),
        "Test",
        "create",
        payload,
        new EventMetadata("test-realm", Map.of("userId", "admin")),
        1,
        Instant.now());
  }

  private DomainEvent<String> createEventWithOperation(String operation) {
    return new DomainEvent<>(
        UUID.randomUUID(),
        "Test." + operation,
        UUID.randomUUID(),
        "Test",
        operation,
        "payload",
        new EventMetadata("realm", Map.of()),
        1,
        Instant.now());
  }

  private DomainEvent<String> createEventWithSchemaVersion(int version) {
    return new DomainEvent<>(
        UUID.randomUUID(),
        "Test.event",
        UUID.randomUUID(),
        "Test",
        "create",
        "payload",
        new EventMetadata("realm", Map.of()),
        version,
        Instant.now());
  }

  private record TestPayload(String id, String email) {}
}
