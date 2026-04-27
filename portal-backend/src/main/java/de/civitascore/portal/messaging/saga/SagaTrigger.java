package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.civitascore.configadapter.model.dataset.DataPipeline;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.model.dataset.NamedApi;
import de.civitascore.configadapter.model.saga.SagaType;
import java.util.List;
import java.util.Map;

/**
 * Typed saga trigger payloads sent to the orchestrator topic. Each record carries {@code sagaType}
 * as an explicit field so Jackson serializes it into the flat JSON that the orchestrator's {@code
 * SagaTriggerConsumer} expects ({@code SagaType.valueOf(sagaTypeStr)}).
 *
 * <p>The three permitted subtypes map 1:1 to {@link SagaType} variants and are constructed via
 * their static factory methods, which fix the correct {@code sagaType} value automatically.
 *
 * <p>{@code NON_NULL} is required because the orchestrator's {@code SagaPayloadBuilder}
 * deserializes the JSON into {@code Map<String, Object>} and calls {@code Map.copyOf()}, which
 * rejects null values. Omitting null fields from the JSON prevents null map entries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface SagaTrigger
    permits SagaTrigger.DatasetCreate, SagaTrigger.DatasetUpdate, SagaTrigger.DatasetDelete {

  /** Dataset UUID used as the Kafka message key and for duplicate detection. */
  String datasetId();

  /** The saga type serialized into the JSON payload for the orchestrator. */
  SagaType sagaType();

  /**
   * Trigger for {@link SagaType#DATASET_CREATE}: provisions FROST project → APISIX route → Redpanda
   * pipeline (conditional).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record DatasetCreate(
      SagaType sagaType,
      String datasetId,
      String datasetName,
      String description,
      boolean openDataAccess,
      List<Datasource> datasources,
      List<DataPipeline> dataPipelines,
      List<NamedApi> namedApis)
      implements SagaTrigger {

    /**
     * Factory method that creates a {@link DatasetCreate} trigger with the correct saga type.
     *
     * @param datasetId the dataset UUID
     * @param datasetName the dataset name
     * @param description the dataset description
     * @param openDataAccess whether the dataset has open data access
     * @param datasources the data sources referenced by pipelines
     * @param dataPipelines the pipelines to provision
     * @param namedApis the named API endpoints to expose (one APISIX route per entry)
     * @return a new create trigger
     */
    public static DatasetCreate of(
        String datasetId,
        String datasetName,
        String description,
        boolean openDataAccess,
        List<Datasource> datasources,
        List<DataPipeline> dataPipelines,
        List<NamedApi> namedApis) {
      return new DatasetCreate(
          SagaType.DATASET_CREATE,
          datasetId,
          datasetName,
          description,
          openDataAccess,
          datasources,
          dataPipelines,
          namedApis);
    }
  }

  /**
   * Trigger for {@link SagaType#DATASET_UPDATE}: updates FROST project → APISIX route → Redpanda
   * pipeline diff (conditional). Carries existing infrastructure IDs so the orchestrator can issue
   * targeted update commands without re-querying.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record DatasetUpdate(
      SagaType sagaType,
      String datasetId,
      String datasetName,
      String description,
      boolean openDataAccess,
      String projectId,
      Map<String, String> routeIds,
      String serviceId,
      List<String> pipelineIds,
      List<Datasource> datasources,
      List<DataPipeline> dataPipelines,
      List<NamedApi> namedApis)
      implements SagaTrigger {

    /**
     * Factory method that creates a {@link DatasetUpdate} trigger with the correct saga type.
     *
     * @param datasetId the dataset UUID
     * @param datasetName the dataset name
     * @param description the dataset description
     * @param openDataAccess whether the dataset has open data access
     * @param projectId the existing FROST project ID
     * @param routeIds existing APISIX route IDs keyed by named-API slug
     * @param serviceId the existing APISIX service ID (shared upstream)
     * @param pipelineIds the existing Redpanda pipeline IDs
     * @param datasources the data sources referenced by pipelines
     * @param dataPipelines the pipeline diff (ADD, UPDATE, DELETE actions)
     * @param namedApis the named API endpoints in the desired state
     * @return a new update trigger
     */
    public static DatasetUpdate of(
        String datasetId,
        String datasetName,
        String description,
        boolean openDataAccess,
        String projectId,
        Map<String, String> routeIds,
        String serviceId,
        List<String> pipelineIds,
        List<Datasource> datasources,
        List<DataPipeline> dataPipelines,
        List<NamedApi> namedApis) {
      return new DatasetUpdate(
          SagaType.DATASET_UPDATE,
          datasetId,
          datasetName,
          description,
          openDataAccess,
          projectId,
          routeIds,
          serviceId,
          pipelineIds,
          datasources,
          dataPipelines,
          namedApis);
    }
  }

  /**
   * Trigger for {@link SagaType#DATASET_DELETE}: tears down Redpanda pipeline → APISIX routes →
   * FROST project (reverse order, best-effort). Only infrastructure IDs are needed — no dataset
   * content.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record DatasetDelete(
      SagaType sagaType,
      String datasetId,
      String projectId,
      String frostBaseUrl,
      Map<String, String> routeIds,
      String serviceId,
      List<String> pipelineIds,
      List<NamedApi> namedApis)
      implements SagaTrigger {

    /**
     * Factory method that creates a {@link DatasetDelete} trigger with the correct saga type.
     *
     * @param datasetId the dataset UUID
     * @param projectId the FROST project ID to tear down
     * @param frostBaseUrl the FROST base URL
     * @param routeIds APISIX route IDs to remove, keyed by named-API slug
     * @param serviceId the APISIX service ID to remove
     * @param pipelineIds the Redpanda pipeline IDs to remove
     * @param namedApis the named API endpoints whose routes are being torn down
     * @return a new delete trigger
     */
    public static DatasetDelete of(
        String datasetId,
        String projectId,
        String frostBaseUrl,
        Map<String, String> routeIds,
        String serviceId,
        List<String> pipelineIds,
        List<NamedApi> namedApis) {
      return new DatasetDelete(
          SagaType.DATASET_DELETE,
          datasetId,
          projectId,
          frostBaseUrl,
          routeIds,
          serviceId,
          pipelineIds,
          namedApis);
    }
  }
}
