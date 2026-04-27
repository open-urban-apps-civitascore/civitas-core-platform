package de.civitascore.portal.messaging.saga;

import de.civitascore.configadapter.model.dataset.DataPipeline;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.model.dataset.NamedApi;
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
import org.springframework.beans.factory.annotation.Value;
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

  @Value("${saga.trigger-topic:de.civitascore.dataset.saga.trigger}")
  private String triggerTopic;

  @Value("${saga.publish-timeout-seconds:10}")
  private int publishTimeoutSeconds;

  public DataSetSagaPublisher(
      KafkaTemplate<String, String> eventKafkaTemplate, ObjectMapper objectMapper) {
    this.eventKafkaTemplate = eventKafkaTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Publish a dataset creation saga trigger. Provisions FROST project, APISIX route, and Redpanda
   * pipelines.
   *
   * @param dataset the dataset to create infrastructure for
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
   * Publish a dataset update saga trigger. Computes pipeline diffs and sends targeted update
   * commands with existing infrastructure IDs.
   *
   * @param dataset the updated dataset
   * @param previousPipelines the pipelines before the update, used for diff computation
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

  /**
   * Publish a dataset deletion saga trigger. Tears down Redpanda pipelines, APISIX route, and FROST
   * project in reverse order.
   *
   * @param dataset the dataset whose infrastructure should be removed
   */
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

  /**
   * Maps the dataset's named APIs from the portal-model entity collection to the config-adapter-api
   * record list consumed by the orchestrator. Returns {@code null} (omitted from the JSON via
   * {@code @JsonInclude(NON_NULL)}) when the dataset has no named APIs.
   */
  private List<NamedApi> buildNamedApis(DataSet dataset) {
    if (dataset.getNamedApis() == null || dataset.getNamedApis().isEmpty()) {
      return null;
    }
    return dataset.getNamedApis().stream()
        .map(api -> new NamedApi(api.getName(), api.getSlug(), api.getStandard(), api.getVersion()))
        .toList();
  }

  /**
   * Projects the dataset's named-API entries into the slug-keyed {@code routeIds} map expected by
   * the saga payload. Skips entries whose {@code routeId} is null (not yet provisioned). Returns
   * {@code null} if no entry has a populated {@code routeId}, so the field is omitted from the JSON
   * via {@code @JsonInclude(NON_NULL)}.
   */
  private Map<String, String> buildRouteIds(DataSet dataset) {
    if (dataset.getNamedApis() == null || dataset.getNamedApis().isEmpty()) {
      return null;
    }
    Map<String, String> routeIds =
        dataset.getNamedApis().stream()
            .filter(api -> api.getRouteId() != null)
            .collect(Collectors.toMap(api -> api.getSlug(), api -> api.getRouteId()));
    return routeIds.isEmpty() ? null : routeIds;
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
          .send(triggerTopic, datasetId, json)
          .get(publishTimeoutSeconds, TimeUnit.SECONDS);
      log.info(
          "Published saga trigger: sagaType={}, datasetId={}, topic={}",
          Encode.forJava(sagaType),
          Encode.forJava(datasetId),
          Encode.forJava(triggerTopic));
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
          publishTimeoutSeconds,
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
