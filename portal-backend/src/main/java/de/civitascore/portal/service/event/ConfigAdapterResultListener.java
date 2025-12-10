package de.civitascore.portal.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.service.event.SynchronousEventPublisher.ConfigAdapterResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Listens for config-adapter results and notifies waiting threads. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigAdapterResultListener {

  private final SynchronousEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @KafkaListener(
      topics = "${kafka.topics.config-adapter-result:civitas.config.result}",
      groupId = "${spring.kafka.consumer.group-id:portal-backend-group}")
  public void handleConfigAdapterResult(@Payload String payload) {
    try {
      JsonNode rootNode = objectMapper.readTree(payload);

      String correlationId = getText(rootNode, "correlationId");
      if (correlationId == null) {
        log.warn("Received result without correlationId");
        return;
      }

      ConfigAdapterResult result =
          new ConfigAdapterResult(
              correlationId,
              getText(rootNode, "status"),
              getText(rootNode, "message"),
              getText(rootNode, "resourceId"),
              getText(rootNode, "errorCode"));

      eventPublisher.notifyResult(correlationId, result);
      log.info(
          "Processed config-adapter result for correlation {}: {}", correlationId, result.status());

    } catch (Exception e) {
      log.error("Failed to process config-adapter result", e);
    }
  }

  private String getText(JsonNode node, String field) {
    JsonNode fieldNode = node.get(field);
    return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
  }
}
