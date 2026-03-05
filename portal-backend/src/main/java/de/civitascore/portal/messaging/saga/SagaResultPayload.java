package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Typed representation of the {@code result} sub-object in a {@code SAGA_COMPLETED} Kafka message.
 *
 * <p>Field names match the JSON keys produced by the config-adapter orchestrator. Unknown fields
 * are ignored for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaResultPayload(
    String datasetId,
    String projectId,
    String baseUrl,
    String routeId,
    String serviceId,
    String publicUrl,
    List<String> pipelineIds) {}
