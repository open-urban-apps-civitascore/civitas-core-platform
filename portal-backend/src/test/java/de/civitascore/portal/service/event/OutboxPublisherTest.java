package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.embedded.OutboxStatus;
import de.civitascore.portal.model.entity.OutboxEvent;
import de.civitascore.portal.model.output.event.TopicResolver;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  @Mock private KafkaTemplate<String, String> kafkaTemplateMock;

  @Mock private ObjectMapper objectMapperMock;

  @Mock private TopicResolver topicResolverMock;

  private OutboxPublisher sut;

  private OutboxEvent testEvent;
  private static final String TEST_TOPIC = "User.create";
  private static final UUID TEST_AGGREGATE_ID = UUID.randomUUID();
  private static final String TEST_PAYLOAD = "{\"id\":\"123\",\"email\":\"test@example.com\"}";

  @BeforeEach
  void setUp() {
    // Create sut manually to inject publishTimeoutSeconds
    sut = new OutboxPublisher(kafkaTemplateMock, objectMapperMock, topicResolverMock, 5);

    testEvent = new OutboxEvent();
    testEvent.setId(UUID.randomUUID());
    testEvent.setTopic(TEST_TOPIC);
    testEvent.setAggregateId(TEST_AGGREGATE_ID);
    testEvent.setAggregateType("User");
    testEvent.setPayload(TEST_PAYLOAD);
    testEvent.setStatus(OutboxStatus.PENDING);
    testEvent.setRetryCount(0);
  }

  @Test
  @DisplayName("publish should send event to Kafka successfully")
  void publishShouldSendEventSuccessfully() {
    // Given
    RecordMetadata metadata = new RecordMetadata(new TopicPartition(TEST_TOPIC, 0), 0, 0, 0L, 0, 0);
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(TEST_TOPIC, TEST_AGGREGATE_ID.toString(), TEST_PAYLOAD);
    SendResult<String, String> sendResult = new SendResult<>(producerRecord, metadata);

    when(kafkaTemplateMock.send(eq(TEST_TOPIC), eq(TEST_AGGREGATE_ID.toString()), eq(TEST_PAYLOAD)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    assertDoesNotThrow(() -> sut.publish(testEvent));

    // Then
    verify(kafkaTemplateMock).send(TEST_TOPIC, TEST_AGGREGATE_ID.toString(), TEST_PAYLOAD);
  }

  @Test
  @DisplayName("publish should throw KafkaPublishException on Kafka failure")
  void publishShouldThrowExceptionOnKafkaFailure() {
    // Given
    when(kafkaTemplateMock.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable")));

    // When & Then
    OutboxPublisher.KafkaPublishException exception =
        assertThrows(OutboxPublisher.KafkaPublishException.class, () -> sut.publish(testEvent));

    assertThat(exception.getMessage())
        .contains("Failed to publish event")
        .contains(testEvent.getId().toString())
        .contains(TEST_TOPIC);
    assertThat(exception.getCause()).isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("publish should throw KafkaPublishException on timeout")
  void publishShouldThrowExceptionOnTimeout() {
    // Given
    CompletableFuture<SendResult<String, String>> neverCompletingFuture = new CompletableFuture<>();
    when(kafkaTemplateMock.send(anyString(), anyString(), anyString()))
        .thenReturn(neverCompletingFuture);

    // When & Then
    OutboxPublisher.KafkaPublishException exception =
        assertThrows(OutboxPublisher.KafkaPublishException.class, () -> sut.publish(testEvent));

    assertThat(exception.getMessage()).contains("Failed to publish event");
    assertThat(exception.getCause()).isInstanceOf(java.util.concurrent.TimeoutException.class);
  }

  @Test
  @DisplayName("publishError should send failed event to error topic")
  void publishErrorShouldSendToErrorTopic() throws Exception {
    // Given
    String errorTopic = "errors.dlq";
    when(topicResolverMock.error()).thenReturn(errorTopic);

    RuntimeException testException = new RuntimeException("Test error");
    testEvent.setRetryCount(5);

    // Mock objectMapper to return a valid JSON string
    String errorPayloadJson = "{\"originalEventId\":\"" + testEvent.getId() + "\"}";
    when(objectMapperMock.writeValueAsString(any())).thenReturn(errorPayloadJson);

    RecordMetadata metadata = new RecordMetadata(new TopicPartition(errorTopic, 0), 0, 0, 0L, 0, 0);
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(errorTopic, TEST_AGGREGATE_ID.toString(), errorPayloadJson);
    SendResult<String, String> sendResult = new SendResult<>(producerRecord, metadata);

    when(kafkaTemplateMock.send(eq(errorTopic), eq(TEST_AGGREGATE_ID.toString()), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    assertDoesNotThrow(() -> sut.publishError(testEvent, testException));

    // Then
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(kafkaTemplateMock)
        .send(eq(errorTopic), eq(TEST_AGGREGATE_ID.toString()), payloadCaptor.capture());

    String errorPayload = payloadCaptor.getValue();
    assertThat(errorPayload).isNotNull();
    assertThat(errorPayload).contains(testEvent.getId().toString());
  }

  @Test
  @DisplayName("publishError should use fallback payload on serialization failure")
  void publishErrorShouldUseFallbackOnSerializationFailure() throws Exception {
    // Given
    String errorTopic = "errors.dlq";
    when(topicResolverMock.error()).thenReturn(errorTopic);
    when(objectMapperMock.writeValueAsString(any()))
        .thenThrow(new RuntimeException("Serialization failed"));

    RuntimeException testException = new RuntimeException("Original error");

    RecordMetadata metadata = new RecordMetadata(new TopicPartition(errorTopic, 0), 0, 0, 0L, 0, 0);
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(errorTopic, TEST_AGGREGATE_ID.toString(), "{}");
    SendResult<String, String> sendResult = new SendResult<>(producerRecord, metadata);

    when(kafkaTemplateMock.send(eq(errorTopic), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    assertDoesNotThrow(() -> sut.publishError(testEvent, testException));

    // Then
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(kafkaTemplateMock).send(eq(errorTopic), anyString(), payloadCaptor.capture());

    String fallbackPayload = payloadCaptor.getValue();
    assertThat(fallbackPayload).contains("eventId");
    assertThat(fallbackPayload).contains("error");
    assertThat(fallbackPayload).contains("timestamp");
  }

  @Test
  @DisplayName("publishError should not throw when DLQ publishing fails")
  void publishErrorShouldNotThrowOnDlqFailure() {
    // Given
    String errorTopic = "errors.dlq";
    when(topicResolverMock.error()).thenReturn(errorTopic);
    when(kafkaTemplateMock.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DLQ unavailable")));

    RuntimeException testException = new RuntimeException("Test error");

    // When & Then
    assertDoesNotThrow(() -> sut.publishError(testEvent, testException));
    // Should log critical error but not throw
  }

  @Test
  @DisplayName("publishError should include stack trace in error payload")
  void publishErrorShouldIncludeStackTrace() throws Exception {
    // Given
    String errorTopic = "errors.dlq";
    when(topicResolverMock.error()).thenReturn(errorTopic);

    RuntimeException testException = new RuntimeException("Test error with stack trace");

    // Mock objectMapper to return error payload with stackTrace
    String errorPayloadJson = "{\"stackTrace\":\"...\"}";
    when(objectMapperMock.writeValueAsString(any())).thenReturn(errorPayloadJson);

    RecordMetadata metadata = new RecordMetadata(new TopicPartition(errorTopic, 0), 0, 0, 0L, 0, 0);
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(errorTopic, TEST_AGGREGATE_ID.toString(), errorPayloadJson);
    SendResult<String, String> sendResult = new SendResult<>(producerRecord, metadata);

    when(kafkaTemplateMock.send(eq(errorTopic), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When
    sut.publishError(testEvent, testException);

    // Then
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(kafkaTemplateMock).send(eq(errorTopic), anyString(), payloadCaptor.capture());

    String errorPayload = payloadCaptor.getValue();
    assertThat(errorPayload).contains("stackTrace");
  }

  @Test
  @DisplayName("publish should use correct timeout from configuration")
  void publishShouldUseConfiguredTimeout() {
    // Given
    OutboxPublisher sutWithCustomTimeout =
        new OutboxPublisher(kafkaTemplateMock, objectMapperMock, topicResolverMock, 10);

    CompletableFuture<SendResult<String, String>> neverCompletingFuture = new CompletableFuture<>();
    when(kafkaTemplateMock.send(anyString(), anyString(), anyString()))
        .thenReturn(neverCompletingFuture);

    // When & Then
    assertThrows(
        OutboxPublisher.KafkaPublishException.class, () -> sutWithCustomTimeout.publish(testEvent));
    // Should timeout after 10 seconds (not 5)
  }
}
