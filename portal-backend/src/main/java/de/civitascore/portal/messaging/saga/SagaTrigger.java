package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.civitascore.configadapter.model.dataset.DataPipeline;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.model.saga.SagaType;
import java.util.List;

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
      List<DataPipeline> dataPipelines)
      implements SagaTrigger {

    public static DatasetCreate of(
        String datasetId,
        String datasetName,
        String description,
        boolean openDataAccess,
        List<Datasource> datasources,
        List<DataPipeline> dataPipelines) {
      return new DatasetCreate(
          SagaType.DATASET_CREATE,
          datasetId,
          datasetName,
          description,
          openDataAccess,
          datasources,
          dataPipelines);
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
      String routeId,
      String serviceId,
      List<String> pipelineIds,
      List<Datasource> datasources,
      List<DataPipeline> dataPipelines)
      implements SagaTrigger {

    public static DatasetUpdate of(
        String datasetId,
        String datasetName,
        String description,
        boolean openDataAccess,
        String projectId,
        String routeId,
        String serviceId,
        List<String> pipelineIds,
        List<Datasource> datasources,
        List<DataPipeline> dataPipelines) {
      return new DatasetUpdate(
          SagaType.DATASET_UPDATE,
          datasetId,
          datasetName,
          description,
          openDataAccess,
          projectId,
          routeId,
          serviceId,
          pipelineIds,
          datasources,
          dataPipelines);
    }
  }

  /**
   * Trigger for {@link SagaType#DATASET_DELETE}: tears down Redpanda pipeline → APISIX route →
   * FROST project (reverse order, best-effort). Only infrastructure IDs are needed — no dataset
   * content.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record DatasetDelete(
      SagaType sagaType,
      String datasetId,
      String projectId,
      String frostBaseUrl,
      String routeId,
      String serviceId,
      List<String> pipelineIds)
      implements SagaTrigger {

    public static DatasetDelete of(
        String datasetId,
        String projectId,
        String frostBaseUrl,
        String routeId,
        String serviceId,
        List<String> pipelineIds) {
      return new DatasetDelete(
          SagaType.DATASET_DELETE,
          datasetId,
          projectId,
          frostBaseUrl,
          routeId,
          serviceId,
          pipelineIds);
    }
  }
}
