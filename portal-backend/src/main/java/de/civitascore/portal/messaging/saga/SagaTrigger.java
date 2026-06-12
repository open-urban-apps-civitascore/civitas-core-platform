package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.civitascore.configadapter.model.dataset.DataPipeline;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.model.dataset.NamedApi;
import de.civitascore.configadapter.model.saga.SagaType;
import de.civitascore.portal.model.saga.DataSinkPayload;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed saga trigger payloads sent to the orchestrator topic. Each record carries {@code sagaType}
 * as an explicit field so Jackson serializes it into the flat JSON that the orchestrator's {@code
 * SagaTriggerConsumer} expects ({@code SagaType.valueOf(sagaTypeStr)}).
 *
 * <p>The three permitted subtypes map 1:1 to {@link SagaType} variants. Construct via the static
 * {@code of(...)} factories; the canonical constructors enforce that the supplied {@code sagaType}
 * matches the variant and that {@code datasetId} is non-null.
 *
 * <p>The orchestrator provisions FROST project → APISIX routes (one per named API) → Redpanda
 * pipelines on CREATE, runs targeted updates on UPDATE, and tears down in reverse order on DELETE.
 *
 * <p>{@code routeIds} is keyed by named-API slug so per-route infrastructure state is addressable
 * independently.
 *
 * <p>{@code NON_NULL} is required because the orchestrator's {@code SagaPayloadBuilder}
 * deserializes the JSON into {@code Map<String, Object>} and calls {@code Map.copyOf()}, which
 * rejects null values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface SagaTrigger
    permits SagaTrigger.DatasetCreate, SagaTrigger.DatasetUpdate, SagaTrigger.DatasetDelete {

  /** Dataset UUID used as the Kafka message key and for duplicate detection. */
  String datasetId();

  /** The saga type serialized into the JSON payload for the orchestrator. */
  SagaType sagaType();

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record DatasetCreate(
      SagaType sagaType,
      String datasetId,
      String datasetName,
      String description,
      boolean openDataAccess,
      List<Datasource> datasources,
      List<DataSinkPayload> datasinks,
      List<DataPipeline> dataPipelines,
      List<NamedApi> namedApis)
      implements SagaTrigger {

    public DatasetCreate {
      if (sagaType != SagaType.DATASET_CREATE) {
        throw new IllegalArgumentException(
            "DatasetCreate requires sagaType=DATASET_CREATE, got " + sagaType);
      }
      Objects.requireNonNull(datasetId, "datasetId");
    }

    public static DatasetCreate of(
        String datasetId,
        String datasetName,
        String description,
        boolean openDataAccess,
        List<Datasource> datasources,
        List<DataSinkPayload> datasinks,
        List<DataPipeline> dataPipelines,
        List<NamedApi> namedApis) {
      return new DatasetCreate(
          SagaType.DATASET_CREATE,
          datasetId,
          datasetName,
          description,
          openDataAccess,
          datasources,
          datasinks,
          dataPipelines,
          namedApis);
    }
  }

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
      List<DataSinkPayload> datasinks,
      List<DataPipeline> dataPipelines,
      List<NamedApi> namedApis)
      implements SagaTrigger {

    public DatasetUpdate {
      if (sagaType != SagaType.DATASET_UPDATE) {
        throw new IllegalArgumentException(
            "DatasetUpdate requires sagaType=DATASET_UPDATE, got " + sagaType);
      }
      Objects.requireNonNull(datasetId, "datasetId");
    }

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
        List<DataSinkPayload> datasinks,
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
          datasinks,
          dataPipelines,
          namedApis);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record DatasetDelete(
      SagaType sagaType,
      String datasetId,
      String projectId,
      String frostBaseUrl,
      Map<String, String> routeIds,
      String serviceId,
      List<String> pipelineIds,
      List<DataSinkPayload> datasinks,
      List<NamedApi> namedApis)
      implements SagaTrigger {

    public DatasetDelete {
      if (sagaType != SagaType.DATASET_DELETE) {
        throw new IllegalArgumentException(
            "DatasetDelete requires sagaType=DATASET_DELETE, got " + sagaType);
      }
      Objects.requireNonNull(datasetId, "datasetId");
    }

    public static DatasetDelete of(
        String datasetId,
        String projectId,
        String frostBaseUrl,
        Map<String, String> routeIds,
        String serviceId,
        List<String> pipelineIds,
        List<DataSinkPayload> datasinks,
        List<NamedApi> namedApis) {
      return new DatasetDelete(
          SagaType.DATASET_DELETE,
          datasetId,
          projectId,
          frostBaseUrl,
          routeIds,
          serviceId,
          pipelineIds,
          datasinks,
          namedApis);
    }
  }
}
