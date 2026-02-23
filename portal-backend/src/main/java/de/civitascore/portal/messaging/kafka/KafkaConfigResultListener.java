package de.civitascore.portal.messaging.kafka;

import de.civitascore.configadapter.model.ConfigResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.messaging.CloudEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for Config Adapter result events.
 *
 * <p>This listener:
 *
 * <ul>
 *   <li>Consumes ConfigResultEvent messages from the result topic
 *   <li>Parses the result event
 *   <li>Notifies the KafkaCloudEventPublisher to complete pending requests
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaConfigResultListener {

  private final KafkaCloudEventPublisher kafkaPublisher;
  private final ObjectMapper objectMapper;

  public KafkaConfigResultListener(
      @Autowired(required = false) CloudEventPublisher cloudEventPublisher,
      ObjectMapper objectMapper) {
    if (cloudEventPublisher instanceof KafkaCloudEventPublisher kp) {
      this.kafkaPublisher = kp;
      log.info("Kafka Config Result Listener initialized");
    } else {
      this.kafkaPublisher = null;
      log.warn("CloudEventPublisher is not KafkaCloudEventPublisher - result listening disabled");
    }
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = "${kafka.result-topic:core.civitas.config.results}",
      groupId = "${spring.kafka.consumer.group-id:portal-backend-group}")
  public void handleConfigResult(@Payload String payload) {
    if (kafkaPublisher == null) {
      log.debug("Kafka publisher not available - ignoring result");
      return;
    }

    try {
      ConfigResultEvent resultEvent = objectMapper.readValue(payload, ConfigResultEvent.class);

      log.debug(
          "Received Config Adapter result: messageId={}, status={}, operation={}",
          resultEvent.originalMessageId(),
          resultEvent.status(),
          resultEvent.operation());

      kafkaPublisher.handleResult(resultEvent);

    } catch (Exception e) {
      log.error("Failed to process Config Adapter result: {}", e.getMessage(), e);
    }
  }
}
