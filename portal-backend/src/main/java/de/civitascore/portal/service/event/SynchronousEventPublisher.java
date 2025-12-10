package de.civitascore.portal.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.output.event.ConfigEventDTO;
import de.civitascore.portal.model.output.event.DomainEvent;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Synchronous event publisher - publishes to Kafka and waits for config-adapter results. */
@Slf4j
@Service
public class SynchronousEventPublisher {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final Map<String, CompletableFuture<ConfigAdapterResult>> pendingResults =
      new ConcurrentHashMap<>();

  @Value("${event.publish-timeout-seconds:5}")
  private int publishTimeoutSeconds;

  @Value("${event.config-adapter-timeout-seconds:10}")
  private int configAdapterTimeoutSeconds;

  @Value("${kafka.topics.config-adapter-result:civitas.config.result}")
  private String resultTopic;

  @Value("${event.target-component:keycloak}")
  private String targetComponent;

  public SynchronousEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  public ConfigAdapterResult publishAndWaitForResult(String topic, DomainEvent<?> event)
      throws TimeoutException {

    try {
      ConfigEventDTO configEvent =
          ConfigEventDTO.fromDomainEvent(event, resultTopic, targetComponent);
      String payload = objectMapper.writeValueAsString(configEvent);
      String correlationId = configEvent.metadata().correlationId();

      kafkaTemplate
          .send(topic, event.entityId().toString(), payload)
          .get(publishTimeoutSeconds, TimeUnit.SECONDS);

      log.debug("Published event to {} with correlation {}", topic, correlationId);

      CompletableFuture<ConfigAdapterResult> resultFuture = new CompletableFuture<>();
      pendingResults.put(correlationId, resultFuture);

      ConfigAdapterResult result = resultFuture.get(configAdapterTimeoutSeconds, TimeUnit.SECONDS);

      log.info("Received config-adapter result for {}: {}", correlationId, result.status());
      return result;

    } catch (TimeoutException e) {
      log.error("Timeout waiting for config-adapter result (pending: {})", pendingResults.size());
      throw e;
    } catch (Exception e) {
      log.error("Failed to publish event", e);
      throw new EventPublishException("Failed to publish event", e);
    }
  }

  public void notifyResult(String correlationId, ConfigAdapterResult result) {
    CompletableFuture<ConfigAdapterResult> future = pendingResults.remove(correlationId);
    if (future != null) {
      future.complete(result);
    } else {
      log.warn("Received result for unknown correlation ID: {}", correlationId);
    }
  }

  public record ConfigAdapterResult(
      String correlationId, String status, String message, String resourceId, String errorCode) {
    public boolean isSuccess() {
      return "SUCCESS".equals(status);
    }
  }

  public static class EventPublishException extends RuntimeException {
    public EventPublishException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
