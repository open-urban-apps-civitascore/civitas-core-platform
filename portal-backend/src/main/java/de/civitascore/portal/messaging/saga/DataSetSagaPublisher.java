package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.model.dataset.DataPipeline;
import de.civitascore.configadapter.model.dataset.Datasource;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
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

  public void publishCreateRequested(DataSet dataset) {
    var trigger =
        SagaTrigger.DatasetCreate.of(
            dataset.getId().toString(),
            dataset.getName(),
            dataset.getDescription(),
            dataset.getOpenDataAccess(),
            buildDatasources(dataset),
            buildPipelines(dataset.getPipelines(), "ADD"));
    sendTrigger(trigger);
  }

  public void publishUpdateRequested(DataSet dataset, Set<Pipeline> previousPipelines) {
    var trigger =
        SagaTrigger.DatasetUpdate.of(
            dataset.getId().toString(),
            dataset.getName(),
            dataset.getDescription(),
            dataset.getOpenDataAccess(),
            dataset.getProjectId(),
            dataset.getRouteId(),
            dataset.getServiceId(),
            dataset.getPipelineIds(),
            buildDatasources(dataset),
            buildPipelineDiff(previousPipelines, dataset.getPipelines()));
    sendTrigger(trigger);
  }

  public void publishDeleteRequested(DataSet dataset) {
    var trigger =
        SagaTrigger.DatasetDelete.of(
            dataset.getId().toString(),
            dataset.getProjectId(),
            dataset.getFrostBaseUrl(),
            dataset.getRouteId(),
            dataset.getServiceId(),
            dataset.getPipelineIds());
    sendTrigger(trigger);
  }

  private List<Datasource> buildDatasources(DataSet dataset) {
    if (dataset.getPipelines() == null) {
      return List.of();
    }

    Set<UUID> seen = new HashSet<>();
    List<Datasource> datasources = new ArrayList<>();

    for (var pipeline : dataset.getPipelines()) {
      if (pipeline.getDataSources() == null) {
        continue;
      }
      for (var ds : pipeline.getDataSources()) {
        if (seen.add(ds.getId())) {
          var datasource = new Datasource();
          datasource.setId(ds.getId().toString());
          datasource.setName(ds.getName());
          datasource.setType(ds.getConnectorType() != null ? ds.getConnectorType().name() : null);
          if (ds.getConfiguration() != null) {
            ds.getConfiguration().forEach(datasource::handleUnknownProperty);
          }
          datasources.add(datasource);
        }
      }
    }

    return datasources;
  }

  private List<DataPipeline> buildPipelines(Set<Pipeline> pipelines, String action) {
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
      String action = previousIds.contains(entry.getKey()) ? "UPDATE" : "ADD";
      result.add(toPipelineEntry(entry.getValue(), action));
    }

    for (UUID id : previousIds) {
      if (!currentMap.containsKey(id)) {
        result.add(new DataPipeline(id.toString(), "1", "DELETE", null));
      }
    }

    return result;
  }

  private DataPipeline toPipelineEntry(Pipeline pipeline, String action) {
    return new DataPipeline(pipeline.getId().toString(), "1", action, pipeline.getModel());
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
          sagaType,
          datasetId,
          triggerTopic);
    } catch (JsonProcessingException e) {
      log.error(
          "Failed to serialize saga trigger for dataset {}: {}",
          Encode.forJava(datasetId),
          e.getMessage(),
          e);
      throw new IllegalStateException("Failed to serialize saga trigger", e);
    } catch (ExecutionException e) {
      log.error(
          "Kafka broker rejected saga trigger for dataset {}, sagaType={}: {}",
          datasetId,
          sagaType,
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage(),
          e);
      throw new IllegalStateException("Failed to send saga trigger to Kafka", e);
    } catch (TimeoutException e) {
      log.error(
          "Timed out after {}s waiting for Kafka ack for saga trigger: sagaType={}, datasetId={}",
          publishTimeoutSeconds,
          sagaType,
          datasetId,
          e);
      throw new IllegalStateException("Timeout sending saga trigger to Kafka", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error(
          "Interrupted while waiting for Kafka ack for saga trigger: sagaType={}, datasetId={}",
          sagaType,
          datasetId,
          e);
      throw new RuntimeException(e);
    }
  }
}
