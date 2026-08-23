package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Typed representation of a saga result Kafka message payload. Used for both {@code SAGA_COMPLETED}
 * (infrastructure fields from the nested {@code result} object) and {@code SAGA_FAILED} (failure
 * details from the top-level message).
 *
 * <p>Field names match the JSON keys produced by the config-adapter orchestrator. Unknown fields
 * are ignored for forward compatibility. {@code routeIds} is keyed by named-API slug so per-route
 * infrastructure state is addressable independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaResultPayload(
    String datasetId,
    // SAGA_COMPLETED infrastructure fields
    String projectId,
    String baseUrl,
    Map<String, String> routeIds,
    String serviceId,
    String publicUrl,
    List<String> pipelineIds,
    // SAGA_FAILED fields
    String failedStep,
    String error,
    Boolean compensated,
    Map<String, Object> pipelineStatus,
    // Reported by a step that succeeded only in part
    List<String> staleFeatureTypes) {
  public SagaResultPayload(
      String datasetId,
      String projectId,
      String baseUrl,
      Map<String, String> routeIds,
      String serviceId,
      String publicUrl,
      List<String> pipelineIds,
      String failedStep,
      String error,
      Boolean compensated) {
    this(
        datasetId,
        projectId,
        baseUrl,
        routeIds,
        serviceId,
        publicUrl,
        pipelineIds,
        failedStep,
        error,
        compensated,
        null,
        null);
  }

  public SagaResultPayload(
      String datasetId,
      String projectId,
      String baseUrl,
      Map<String, String> routeIds,
      String serviceId,
      String publicUrl,
      List<String> pipelineIds,
      String failedStep,
      String error,
      Boolean compensated,
      Map<String, Object> pipelineStatus) {
    this(
        datasetId,
        projectId,
        baseUrl,
        routeIds,
        serviceId,
        publicUrl,
        pipelineIds,
        failedStep,
        error,
        compensated,
        pipelineStatus,
        null);
  }
}
