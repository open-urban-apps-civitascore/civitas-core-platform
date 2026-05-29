package de.civitascore.portal.messaging.saga;

import de.civitascore.configadapter.model.dataset.ApiStandard;
import de.civitascore.configadapter.model.dataset.DataPipeline;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.model.dataset.NamedApi;
import de.civitascore.portal.configuration.SagaProperties;
import de.civitascore.portal.model.embedded.PipelineAction;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Pipeline;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes dataset saga trigger messages to Kafka for the config-adapter orchestrator. Supports
 * create, update, and delete saga triggers that provision or tear down FROST, APISIX, and Redpanda
 * infrastructure. Sends synchronously to ensure Kafka acceptance before the database transaction
 * commits.
 */
@Slf4j
@Service
public class DataSetSagaPublisher {

  private final KafkaTemplate<String, String> eventKafkaTemplate;
  private final ObjectMapper objectMapper;
  private final SagaProperties sagaProperties;

  public DataSetSagaPublisher(
      KafkaTemplate<String, String> eventKafkaTemplate,
      ObjectMapper objectMapper,
      SagaProperties sagaProperties) {
    this.eventKafkaTemplate = eventKafkaTemplate;
    this.objectMapper = objectMapper;
    this.sagaProperties = sagaProperties;
  }

  /**
   * Publishes a {@code DATASET_CREATE} saga trigger. See {@link SagaTrigger} for the contract. The
   * dataset's {@code openDataAccess} flag is propagated downstream: FROST sets it as the project's
   * {@code public} flag (anonymous read access) and APISIX attaches the auth plugin only for
   * protected datasets.
   */
  public void publishCreateRequested(DataSet dataset) {
    var trigger =
        SagaTrigger.DatasetCreate.of(
            dataset.getId().toString(),
            dataset.getName(),
            dataset.getDescription(),
            dataset.getOpenDataAccess(),
            buildDatasources(dataset),
            buildPipelines(dataset.getPipelines(), PipelineAction.ADD),
            buildNamedApis(dataset));
    sendTrigger(trigger);
  }

  /**
   * Publishes a {@code DATASET_UPDATE} saga trigger with a pipeline diff against {@code
   * previousPipelines}. Toggling {@code openDataAccess} re-applies the FROST {@code public} flag and
   * the APISIX auth-plugin attachment.
   */
  public void publishUpdateRequested(DataSet dataset, Set<Pipeline> previousPipelines) {
    var trigger =
        SagaTrigger.DatasetUpdate.of(
            dataset.getId().toString(),
            dataset.getName(),
            dataset.getDescription(),
            dataset.getOpenDataAccess(),
            dataset.getProjectId(),
            buildRouteIds(dataset),
            dataset.getServiceId(),
            dataset.getPipelineIds(),
            buildDatasources(dataset),
            buildPipelineDiff(previousPipelines, dataset.getPipelines()),
            buildNamedApis(dataset));
    sendTrigger(trigger);
  }

  /** Publishes a {@code DATASET_DELETE} saga trigger. */
  public void publishDeleteRequested(DataSet dataset) {
    var trigger =
        SagaTrigger.DatasetDelete.of(
            dataset.getId().toString(),
            dataset.getProjectId(),
            dataset.getFrostBaseUrl(),
            buildRouteIds(dataset),
            dataset.getServiceId(),
            dataset.getPipelineIds(),
            buildNamedApis(dataset));
    sendTrigger(trigger);
  }

  private List<Datasource> buildDatasources(DataSet dataset) {
    if (dataset.getPipelines() == null) {
      return List.of();
    }

    Set<UUID> seen = new HashSet<>();
    return dataset.getPipelines().stream()
        .filter(p -> p.getDataSources() != null)
        .flatMap(p -> p.getDataSources().stream())
        .filter(ds -> seen.add(ds.getId()))
        .map(
            ds -> {
              var datasource = new Datasource();
              datasource.setId(ds.getId().toString());
              datasource.setName(ds.getName());
              datasource.setType(
                  ds.getConnectorType() != null ? ds.getConnectorType().name() : null);
              if (ds.getConfiguration() != null) {
                ds.getConfiguration().forEach(datasource::handleUnknownProperty);
              }
              return datasource;
            })
        .toList();
  }

  private List<DataPipeline> buildPipelines(Set<Pipeline> pipelines, PipelineAction action) {
    return pipelines.stream().map(p -> toPipelineEntry(p, action)).toList();
  }

  private List<DataPipeline> buildPipelineDiff(Set<Pipeline> previous, Set<Pipeline> current) {
    Set<UUID> previousIds =
        previous == null
            ? Set.of()
            : previous.stream().map(Pipeline::getId).collect(Collectors.toSet());
    Map<UUID, Pipeline> currentMap =
        current.stream().collect(Collectors.toMap(Pipeline::getId, p -> p));

    List<DataPipeline> result = new ArrayList<>();

    for (var entry : currentMap.entrySet()) {
      PipelineAction action =
          previousIds.contains(entry.getKey()) ? PipelineAction.UPDATE : PipelineAction.ADD;
      result.add(toPipelineEntry(entry.getValue(), action));
    }

    for (UUID id : previousIds) {
      if (!currentMap.containsKey(id)) {
        result.add(new DataPipeline(id.toString(), "0", PipelineAction.DELETE.name(), null));
      }
    }

    return result;
  }

  /** Returns {@code null} when the dataset has no named APIs, so the JSON field is omitted. */
  private List<NamedApi> buildNamedApis(DataSet dataset) {
    if (dataset.getNamedApis().isEmpty()) {
      return null;
    }
    return dataset.getNamedApis().stream()
        .map(
            api ->
                new NamedApi(
                    api.getSlug(), ApiStandard.valueOf(api.getStandard().name()), api.getVersion()))
        .toList();
  }

  /**
   * Builds the {@code slug -> routeId} map describing APISIX routes the orchestrator currently owns
   * for this dataset. Returns null when no entry has a populated {@code routeId} so
   * {@code @JsonInclude(NON_NULL)} drops the field; defense-in-depth against the DB {@code NOT
   * NULL} / {@code UNIQUE(dataset_id, slug)} constraints.
   *
   * <p><b>Contract with the orchestrator:</b>
   *
   * <ul>
   *   <li>{@code null} / field omitted — no existing routes; orchestrator provisions all namedApis
   *       from scratch.
   *   <li>Non-empty map — keyed by slug for each route the orchestrator already owns; slugs absent
   *       from the map (but present in {@code namedApis}) are treated as new and provisioned. Slugs
   *       present here but absent from {@code namedApis} are torn down on DELETE / left alone on
   *       UPDATE.
   * </ul>
   */
  private Map<String, String> buildRouteIds(DataSet dataset) {
    if (dataset.getNamedApis().isEmpty()) {
      return null;
    }
    Map<String, String> routeIds =
        dataset.getNamedApis().stream()
            .filter(api -> api.getRouteId() != null)
            .collect(
                Collectors.toMap(
                    api -> {
                      if (api.getSlug() == null) {
                        throw invariant(
                            "NamedApi %s on dataset %s has null slug",
                            api.getId(), dataset.getId());
                      }
                      return api.getSlug();
                    },
                    api -> api.getRouteId(),
                    (existing, duplicate) -> {
                      throw invariant(
                          "Dataset %s has duplicate slug; routeIds %s and %s",
                          dataset.getId(), existing, duplicate);
                    }));
    return routeIds.isEmpty() ? null : routeIds;
  }

  private static IllegalStateException invariant(String fmt, Object... args) {
    return new IllegalStateException(
        String.format(fmt, args) + " (DB constraint should prevent this)");
  }

  private DataPipeline toPipelineEntry(Pipeline pipeline, PipelineAction action) {
    return new DataPipeline(
        pipeline.getId().toString(),
        String.valueOf(pipeline.getVersion()),
        action.name(),
        pipeline.getModel());
  }

  /**
   * Serializes and sends the saga trigger to Kafka synchronously (blocking). If Kafka rejects or
   * times out, the exception propagates before {@code @Transactional} commits, rolling back the DB
   * change. This guards against the "DB committed, Kafka missed" case. The inverse risk remains: if
   * Kafka accepts the message but the DB commit subsequently fails, the trigger is already in
   * flight with no corresponding DB state. Eliminating that would require
   * {@code @TransactionalEventListener(AFTER_COMMIT)}, which is a larger structural change.
   */
  private void sendTrigger(SagaTrigger trigger) {
    String datasetId = trigger.datasetId();
    String sagaType = trigger.sagaType().name();
    try {
      String json = objectMapper.writeValueAsString(trigger);
      eventKafkaTemplate
          .send(sagaProperties.triggerTopic(), datasetId, json)
          .get(sagaProperties.publishTimeoutSeconds(), TimeUnit.SECONDS);
      log.info(
          "Published saga trigger: sagaType={}, datasetId={}, topic={}",
          Encode.forJava(sagaType),
          Encode.forJava(datasetId),
          Encode.forJava(sagaProperties.triggerTopic()));
    } catch (JacksonException e) {
      log.error(
          "Failed to serialize saga trigger for dataset {}: {}",
          Encode.forJava(datasetId),
          e.getMessage(),
          e);
      throw new IllegalStateException("Failed to serialize saga trigger", e);
    } catch (ExecutionException e) {
      log.error(
          "Kafka broker rejected saga trigger for dataset {}, sagaType={}: {}",
          Encode.forJava(datasetId),
          Encode.forJava(sagaType),
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage(),
          e);
      throw new IllegalStateException("Failed to send saga trigger to Kafka", e);
    } catch (TimeoutException e) {
      log.error(
          "Timed out after {}s waiting for Kafka ack for saga trigger: sagaType={}, datasetId={}",
          sagaProperties.publishTimeoutSeconds(),
          Encode.forJava(sagaType),
          Encode.forJava(datasetId),
          e);
      throw new IllegalStateException("Timeout sending saga trigger to Kafka", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error(
          "Interrupted while waiting for Kafka ack for saga trigger: sagaType={}, datasetId={}",
          Encode.forJava(sagaType),
          Encode.forJava(datasetId),
          e);
      throw new IllegalStateException(
          "Interrupted while waiting for Kafka ack for saga trigger", e);
    }
  }
}
