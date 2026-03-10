package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Typed representation of a saga result Kafka message payload. Used for both {@code SAGA_COMPLETED}
 * (infrastructure fields from the nested {@code result} object) and {@code SAGA_FAILED} (failure
 * details from the top-level message).
 *
 * <p>Field names match the JSON keys produced by the config-adapter orchestrator. Unknown fields
 * are ignored for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaResultPayload(
    String datasetId,
    // SAGA_COMPLETED infrastructure fields
    String projectId,
    String baseUrl,
    String routeId,
    String serviceId,
    String publicUrl,
    List<String> pipelineIds,
    // SAGA_FAILED fields
    String failedStep,
    String error,
    Boolean compensated) {}
