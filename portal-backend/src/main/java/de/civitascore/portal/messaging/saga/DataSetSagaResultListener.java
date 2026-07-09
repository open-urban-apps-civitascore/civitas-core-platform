package de.civitascore.portal.messaging.saga;

import de.civitascore.portal.model.embedded.SagaResultType;
import de.civitascore.portal.service.DataSetService;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka listener for dataset saga result messages. Consumes {@code SAGA_COMPLETED} and {@code
 * SAGA_FAILED} messages from the saga result topic and delegates to {@link DataSetService} to
 * update dataset state with infrastructure IDs or record failure information.
 */
@Slf4j
@Component
public class DataSetSagaResultListener {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final DataSetService dataSetService;
  private final ObjectMapper objectMapper;

  public DataSetSagaResultListener(DataSetService dataSetService, ObjectMapper objectMapper) {
    this.dataSetService = dataSetService;
    this.objectMapper = objectMapper;
  }

  /**
   * Handle an incoming saga result message from Kafka. Parses the message type and delegates to the
   * appropriate handler for completed or failed sagas.
   *
   * @param payload the raw JSON payload from Kafka
   */
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
        case SAGA_COMPLETED -> {
          if (!(message.get("result") instanceof Map<?, ?> resultRaw)) {
            log.warn("SAGA_COMPLETED message missing or invalid result payload");
            return;
          }
          SagaResultPayload result = objectMapper.convertValue(resultRaw, SagaResultPayload.class);
          handleSagaCompleted(result);
        }
        case SAGA_FAILED -> {
          SagaResultPayload result = objectMapper.convertValue(message, SagaResultPayload.class);
          handleSagaFailed(result);
        }
      }
    } catch (JacksonException e) {
      log.error("Failed to deserialize saga result: {}", e.getMessage(), e);
      throw new IllegalStateException("Saga result deserialization failed", e);
    } catch (IllegalArgumentException e) {
      log.error("Invalid saga result payload: {}", e.getMessage(), e);
      throw new IllegalStateException("Saga result processing failed", e);
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

  private void handleSagaCompleted(SagaResultPayload result) {
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

  private void handleSagaFailed(SagaResultPayload result) {
    if (result.datasetId() == null) {
      log.warn("SAGA_FAILED message missing datasetId");
      return;
    }

    UUID datasetId = parseDatasetId(result.datasetId());
    if (datasetId == null) {
      return;
    }

    log.info(
        "Saga failed for dataset {}: step={}, error={}, compensated={}",
        datasetId,
        Encode.forJava(result.failedStep()),
        Encode.forJava(result.error()),
        result.compensated());

    dataSetService.handleSagaFailed(
        datasetId,
        result.failedStep(),
        result.error(),
        result.compensated() != null && result.compensated());
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
