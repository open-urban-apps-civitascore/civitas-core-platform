package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.configuration.OutboxConfig;
import de.civitascore.portal.model.embedded.OutboxStatus;
import de.civitascore.portal.model.entity.OutboxEvent;
import de.civitascore.portal.model.output.event.DomainEvent;
import de.civitascore.portal.model.output.event.EventMetadata;
import de.civitascore.portal.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventPublisherServiceTest {

  @Mock private OutboxEventRepository outboxRepositoryMock;

  @Mock private ObjectMapper objectMapperMock;

  @Mock private OutboxConfig configMock;

  @InjectMocks private EventPublisherService sut;

  private DomainEvent<TestPayload> testEvent;
  private static final String TEST_TOPIC = "User.create";
  private static final UUID TEST_ENTITY_ID = UUID.randomUUID();
  private static final String TEST_AGGREGATE_TYPE = "User";

  @BeforeEach
  void setUp() {
    TestPayload payload = new TestPayload("test-id", "test@example.com", "Test User");
    EventMetadata metadata = new EventMetadata("test-realm", Map.of("userId", "admin"));

    testEvent =
        new DomainEvent<>(
            UUID.randomUUID(),
            TEST_TOPIC,
            TEST_ENTITY_ID,
            TEST_AGGREGATE_TYPE,
            "create",
            payload,
            metadata,
            1,
            Instant.now());
  }

  @Test
  @DisplayName("publish should save event to outbox repository with PENDING status")
  void publishShouldSaveEventToOutbox() throws Exception {
    // Given
    String expectedPayload = "{\"id\":\"test-id\",\"email\":\"test@example.com\"}";
    when(objectMapperMock.writeValueAsString(testEvent)).thenReturn(expectedPayload);
    when(configMock.getPayloadSizeWarningBytes()).thenReturn(100000L);
    when(outboxRepositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.publish(TEST_TOPIC, testEvent);

    // Then
    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepositoryMock).save(eventCaptor.capture());

    OutboxEvent savedEvent = eventCaptor.getValue();
    assertNotNull(savedEvent);
    assertThat(savedEvent.getTopic()).isEqualTo(TEST_TOPIC);
    assertThat(savedEvent.getAggregateType()).isEqualTo(TEST_AGGREGATE_TYPE);
    assertThat(savedEvent.getAggregateId()).isEqualTo(TEST_ENTITY_ID);
    assertThat(savedEvent.getPayload()).isEqualTo(expectedPayload);
    assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(savedEvent.getRetryCount()).isZero();
  }

  @Test
  @DisplayName("publish should warn when payload exceeds size threshold")
  void publishShouldWarnOnLargePayload() throws Exception {
    // Given
    String largePayload = "x".repeat(150000); // 150KB payload
    when(objectMapperMock.writeValueAsString(testEvent)).thenReturn(largePayload);
    when(configMock.getPayloadSizeWarningBytes()).thenReturn(100000L); // 100KB threshold
    when(outboxRepositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.publish(TEST_TOPIC, testEvent);

    // Then
    verify(outboxRepositoryMock).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName("publish should not warn when payload is below size threshold")
  void publishShouldNotWarnOnSmallPayload() throws Exception {
    // Given
    String smallPayload = "{\"id\":\"test-id\"}"; // Small payload
    when(objectMapperMock.writeValueAsString(testEvent)).thenReturn(smallPayload);
    when(configMock.getPayloadSizeWarningBytes()).thenReturn(100000L);
    when(outboxRepositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.publish(TEST_TOPIC, testEvent);

    // Then
    verify(outboxRepositoryMock).save(any(OutboxEvent.class));
    // No warning should be logged
  }

  @Test
  @DisplayName("publish should throw EventSerializationException when serialization fails")
  void publishShouldThrowExceptionOnSerializationFailure() throws Exception {
    // Given
    when(objectMapperMock.writeValueAsString(testEvent))
        .thenThrow(new RuntimeException("Serialization failed"));

    // When & Then
    EventPublisherService.EventSerializationException exception =
        assertThrows(
            EventPublisherService.EventSerializationException.class,
            () -> sut.publish(TEST_TOPIC, testEvent));

    assertThat(exception.getMessage())
        .contains("Failed to serialize event")
        .contains(TEST_TOPIC)
        .contains(testEvent.eventId().toString());
    verify(outboxRepositoryMock, never()).save(any());
  }

  @Test
  @DisplayName("publish should handle null correlation IDs in metadata")
  void publishShouldHandleNullCorrelationIds() throws Exception {
    // Given
    EventMetadata metadataWithoutCorrelationIds = new EventMetadata("test-realm", Map.of());
    DomainEvent<TestPayload> eventWithoutCorrelationIds =
        new DomainEvent<>(
            UUID.randomUUID(),
            TEST_TOPIC,
            TEST_ENTITY_ID,
            TEST_AGGREGATE_TYPE,
            "create",
            testEvent.payload(),
            metadataWithoutCorrelationIds,
            1,
            Instant.now());

    String expectedPayload = "{\"id\":\"test-id\"}";
    when(objectMapperMock.writeValueAsString(eventWithoutCorrelationIds))
        .thenReturn(expectedPayload);
    when(configMock.getPayloadSizeWarningBytes()).thenReturn(100000L);
    when(outboxRepositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.publish(TEST_TOPIC, eventWithoutCorrelationIds);

    // Then
    verify(outboxRepositoryMock).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName("publish should preserve event metadata in payload")
  void publishShouldPreserveEventMetadata() throws Exception {
    // Given
    String expectedPayload =
        "{\"eventId\":\"" + testEvent.eventId() + "\",\"metadata\":{\"realm\":\"test-realm\"}}";
    when(objectMapperMock.writeValueAsString(testEvent)).thenReturn(expectedPayload);
    when(configMock.getPayloadSizeWarningBytes()).thenReturn(100000L);
    when(outboxRepositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.publish(TEST_TOPIC, testEvent);

    // Then
    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepositoryMock).save(eventCaptor.capture());

    OutboxEvent savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getPayload()).contains("test-realm");
    assertThat(savedEvent.getPayload()).contains(testEvent.eventId().toString());
  }

  // Test payload record for testing
  private record TestPayload(String id, String email, String name) {}
}
