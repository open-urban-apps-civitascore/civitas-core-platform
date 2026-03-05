package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.embedded.SagaResultType;
import de.civitascore.portal.service.DataSetService;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class DataSetSagaResultListener {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final DataSetService dataSetService;
  private final ObjectMapper objectMapper;

  public DataSetSagaResultListener(DataSetService dataSetService, ObjectMapper objectMapper) {
    this.dataSetService = dataSetService;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = "${saga.result-topic:de.civitascore.saga.result}",
      groupId = "${spring.kafka.consumer.group-id:civitas-portal-backend}-saga",
      containerFactory = "sagaKafkaListenerContainerFactory")
  public void handleSagaResult(@Payload String payload) {
    try {
      Map<String, Object> message = objectMapper.readValue(payload, MAP_TYPE);
      String typeStr = (String) message.get("type");

      SagaResultType type = parseResultType(typeStr);
      if (type == null) {
        return;
      }

      switch (type) {
        case SAGA_COMPLETED -> handleSagaCompleted(message);
        case SAGA_FAILED -> handleSagaFailed(message);
      }
    } catch (Exception e) {
      log.error("Failed to process saga result: {}", e.getMessage(), e);
      // Re-throw so the DLQ error handler can retry and eventually dead-letter the message
      throw new RuntimeException("Saga result processing failed", e);
    }
  }

  private SagaResultType parseResultType(String typeStr) {
    if (typeStr == null) {
      log.debug("Ignoring saga result message with null type");
      return null;
    }
    try {
      return SagaResultType.valueOf(typeStr);
    } catch (IllegalArgumentException e) {
      log.debug("Ignoring saga result message with unknown type: {}", Encode.forJava(typeStr));
      return null;
    }
  }

  private void handleSagaCompleted(Map<String, Object> message) {
    if (!(message.get("result") instanceof Map<?, ?> resultRaw)) {
      log.warn("SAGA_COMPLETED message missing or invalid result payload");
      return;
    }

    SagaResultPayload result = objectMapper.convertValue(resultRaw, SagaResultPayload.class);
    if (result.datasetId() == null) {
      log.warn("SAGA_COMPLETED result missing datasetId");
      return;
    }

    UUID datasetId = parseDatasetId(result.datasetId());
    if (datasetId == null) {
      return;
    }

    log.info("Saga completed for dataset {}", datasetId);
    dataSetService.handleSagaCompleted(datasetId, result);
  }

  private void handleSagaFailed(Map<String, Object> message) {
    String datasetIdStr = (String) message.get("datasetId");
    if (datasetIdStr == null) {
      log.warn("SAGA_FAILED message missing datasetId");
      return;
    }

    UUID datasetId = parseDatasetId(datasetIdStr);
    if (datasetId == null) {
      return;
    }

    String failedStep = (String) message.get("failedStep");
    String error = (String) message.get("error");
    Boolean compensated = (Boolean) message.get("compensated");

    log.info(
        "Saga failed for dataset {}: step={}, error={}, compensated={}",
        datasetId,
        failedStep,
        error,
        compensated);

    dataSetService.handleSagaFailed(
        datasetId, failedStep, error, compensated != null && compensated);
  }

  private UUID parseDatasetId(String datasetIdStr) {
    try {
      return UUID.fromString(datasetIdStr);
    } catch (IllegalArgumentException e) {
      log.error(
          "Saga result message contains invalid datasetId '{}': {}",
          Encode.forJava(datasetIdStr),
          e.getMessage());
      return null;
    }
  }
}
