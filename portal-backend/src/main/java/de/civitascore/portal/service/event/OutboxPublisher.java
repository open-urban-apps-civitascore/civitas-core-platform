package de.civitascore.portal.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.entity.OutboxEvent;
import de.civitascore.portal.model.output.event.TopicResolver;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OutboxPublisher {
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final TopicResolver topicResolver;
  private final int publishTimeoutSeconds;

  public OutboxPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper,
      TopicResolver topicResolver,
      @Value("${outbox.publish-timeout-seconds:5}") int publishTimeoutSeconds) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.topicResolver = topicResolver;
    this.publishTimeoutSeconds = publishTimeoutSeconds;
  }

  /**
   * Publishes an event to Kafka synchronously.
   *
   * <p>Waits for Kafka acknowledgment to ensure the event was successfully sent. Throws exception
   * if publishing fails.
   *
   * @param event the event to publish
   * @throws KafkaPublishException if publishing fails
   */
  public void publish(OutboxEvent event) {
    try {
      SendResult<String, String> result =
          kafkaTemplate
              .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
              .get(publishTimeoutSeconds, TimeUnit.SECONDS);

      log.debug(
          "Event {} sent to topic {} partition {}",
          event.getId(),
          event.getTopic(),
          result.getRecordMetadata().partition());
    } catch (Exception e) {
      throw new KafkaPublishException(
          "Failed to publish event " + event.getId() + " to topic " + event.getTopic(), e);
    }
  }

  /**
   * Publishes a failed event to the error topic (Dead Letter Queue).
   *
   * <p>Creates a detailed error payload with original event metadata and error information.
   *
   * @param event the failed event
   * @param ex the exception that caused the failure
   */
  public void publishError(OutboxEvent event, Throwable ex) {
    try {
      String errorPayload = createErrorPayload(event, ex);
      kafkaTemplate
          .send(topicResolver.error(), event.getAggregateId().toString(), errorPayload)
          .get(publishTimeoutSeconds, TimeUnit.SECONDS);
      log.info(
          "Event {} sent to error topic after {} retries", event.getId(), event.getRetryCount());
    } catch (Exception dlqError) {
      log.error(
          "CRITICAL: Failed to publish event {} to Dead Letter Queue: {}",
          event.getId(),
          dlqError.getMessage(),
          dlqError);
      // Event bleibt in FAILED Status in der DB für manuelle Intervention
    }
  }

  /**
   * Creates a JSON error payload with event metadata and error details.
   *
   * @param event the failed event
   * @param ex the exception
   * @return JSON string with error information
   */
  private String createErrorPayload(OutboxEvent event, Throwable ex) {
    try {
      var errorInfo =
          new ErrorPayload(
              event.getId().toString(),
              event.getTopic(),
              event.getAggregateId().toString(),
              event.getAggregateType(),
              ex.getMessage(),
              ex.getClass().getName(),
              Arrays.toString(ex.getStackTrace()),
              event.getRetryCount(),
              Instant.now().toString());
      return objectMapper.writeValueAsString(errorInfo);
    } catch (Exception e) {
      log.error("Failed to serialize error payload, using fallback", e);
      return String.format(
          "{\"eventId\":\"%s\",\"error\":\"%s\",\"timestamp\":\"%s\"}",
          event.getId(), ex.getMessage(), Instant.now());
    }
  }

  private record ErrorPayload(
      String originalEventId,
      String originalTopic,
      String aggregateId,
      String aggregateType,
      String errorMessage,
      String exceptionType,
      String stackTrace,
      int retryCount,
      String timestamp) {}

  /** Exception thrown when Kafka publishing fails. */
  public static class KafkaPublishException extends RuntimeException {
    public KafkaPublishException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
