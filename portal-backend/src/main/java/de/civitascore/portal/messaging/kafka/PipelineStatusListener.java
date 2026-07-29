package de.civitascore.portal.messaging.kafka;

import de.civitascore.portal.model.embedded.PipelineRuntimeSource;
import de.civitascore.portal.model.embedded.PipelineRuntimeState;
import de.civitascore.portal.service.PipelineRuntimeStatusService;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineStatusListener {
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private final ObjectMapper mapper;
  private final PipelineRuntimeStatusService service;

  @KafkaListener(
      topics = "${pipeline.status-topic}",
      groupId = "${spring.kafka.consumer.group-id:civitas-portal-backend}-pipeline-status",
      containerFactory = "sagaKafkaListenerContainerFactory")
  public void handle(@Payload String payload) {
    try {
      Map<String, Object> event = mapper.readValue(payload, MAP);
      if (!"PIPELINE_STATUS_CHANGED".equals(event.get("type"))) {
        return;
      }
      service.apply(
          UUID.fromString(String.valueOf(event.get("pipelineId"))),
          PipelineRuntimeState.valueOf(String.valueOf(event.get("status"))),
          PipelineRuntimeSource.valueOf(String.valueOf(event.get("source"))),
          (String) event.get("message"),
          (String) event.get("stacktrace"),
          event.get("occurredAt") == null
              ? null
              : Instant.parse(String.valueOf(event.get("occurredAt"))),
          parseUuid(event.get("correlationId")),
          parseUuid(event.get("eventId")));
    } catch (JacksonException e) {
      log.error("Failed to deserialize pipeline status event: {}", Encode.forJava(e.getMessage()));
    } catch (IllegalArgumentException | DateTimeException e) {
      log.error("Ignoring invalid pipeline status event: {}", Encode.forJava(e.getMessage()));
    }
  }

  private UUID parseUuid(Object value) {
    return value == null ? null : UUID.fromString(String.valueOf(value));
  }
}
