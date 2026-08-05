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
 * infrastructure state is addressable independently. {@code dashboards} and {@code
 * dashboardSources} are deliberately two independent lists, not nested: a source is anchored to a
 * dataset, not to a dashboard (dashboard-sync event contract).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaResultPayload(
    String datasetId,
    // SAGA_COMPLETED infrastructure fields
    String projectId,
    String baseUrl,
    Map<String, String> routeIds,
    // One entry per materialised dashboard, reported back by the dashboard engine.
    List<DashboardResult> dashboards,
    // Engine source projections for this dataset, reported back by the dashboard engine.
    List<DashboardSourceResult> dashboardSources,
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

  /** {@code dashboardId} is the platform UUID echoed back as the correlation key for the result. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DashboardResult(String dashboardId, String externalDashboardId) {}

  /**
   * {@code mappedDatasetId} is carried per source (not taken from {@code
   * SagaResultPayload.datasetId}) because a source binds to a <em>dataset</em>, not to a dashboard:
   * a dashboard is foreseen to span several datasets (Dashboard↔DataSet N:M), so a source surfacing
   * in one dataset's saga need not map to that dataset. The engine must state it explicitly.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DashboardSourceResult(
      String externalSourceId, String physicalSourceIdentifier, String mappedDatasetId) {}

  /**
   * Pre-dashboard constructor. Kept so existing call sites that build the payload positionally
   * without dashboards keep compiling; Jackson always uses the canonical constructor.
   */
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
        null,
        null,
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
        null,
        null,
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
