package de.civitascore.portal.messaging.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    var trigger = new LinkedHashMap<String, Object>();
    trigger.put("sagaType", "DATASET_CREATE");
    trigger.put("datasetId", dataset.getId().toString());
    trigger.put("datasetName", dataset.getName());
    trigger.put("description", dataset.getDescription());
    trigger.put("openDataAccess", dataset.getOpenDataAccess());
    trigger.put("datasources", buildDatasourcesList(dataset));
    trigger.put("dataPipelines", buildPipelinesList(dataset.getPipelines(), "ADD"));
    sendTrigger(dataset.getId().toString(), trigger);
  }

  public void publishUpdateRequested(DataSet dataset, Set<Pipeline> previousPipelines) {
    var trigger = new LinkedHashMap<String, Object>();
    trigger.put("sagaType", "DATASET_UPDATE");
    trigger.put("datasetId", dataset.getId().toString());
    trigger.put("datasetName", dataset.getName());
    trigger.put("description", dataset.getDescription());
    trigger.put("openDataAccess", dataset.getOpenDataAccess());
    trigger.put("projectId", dataset.getProjectId());
    trigger.put("routeId", dataset.getRouteId());
    trigger.put("serviceId", dataset.getServiceId());
    trigger.put("pipelineIds", dataset.getPipelineIds());
    trigger.put("datasources", buildDatasourcesList(dataset));
    trigger.put("dataPipelines", buildPipelineDiff(previousPipelines, dataset.getPipelines()));
    sendTrigger(dataset.getId().toString(), trigger);
  }

  public void publishDeleteRequested(DataSet dataset) {
    var trigger = new LinkedHashMap<String, Object>();
    trigger.put("sagaType", "DATASET_DELETE");
    trigger.put("datasetId", dataset.getId().toString());
    trigger.put("projectId", dataset.getProjectId());
    trigger.put("frostBaseUrl", dataset.getFrostBaseUrl());
    trigger.put("routeId", dataset.getRouteId());
    trigger.put("serviceId", dataset.getServiceId());
    trigger.put("pipelineIds", dataset.getPipelineIds());
    sendTrigger(dataset.getId().toString(), trigger);
  }

  private List<Map<String, Object>> buildDatasourcesList(DataSet dataset) {
    Set<UUID> seen = new HashSet<>();
    List<Map<String, Object>> datasources = new ArrayList<>();

    if (dataset.getPipelines() == null) {
      log.warn("buildDatasourcesList: pipelines is null for dataset {}", dataset.getId());
      return datasources;
    }

    log.info(
        "buildDatasourcesList: dataset {} has {} pipelines",
        dataset.getId(),
        dataset.getPipelines().size());

    for (Pipeline pipeline : dataset.getPipelines()) {
      log.info(
          "buildDatasourcesList: pipeline {} dataSources={}",
          pipeline.getId(),
          pipeline.getDataSources());
      if (pipeline.getDataSources() == null) {
        continue;
      }
      for (DataSource ds : pipeline.getDataSources()) {
        if (seen.add(ds.getId())) {
          var entry = new LinkedHashMap<String, Object>();
          entry.put("id", ds.getId().toString());
          entry.put("name", ds.getName());
          entry.put("type", ds.getConnectorType() != null ? ds.getConnectorType().name() : null);
          entry.put("configuration", ds.getConfiguration());
          datasources.add(entry);
        }
      }
    }

    log.info("buildDatasourcesList: returning {} datasources", datasources.size());
    return datasources;
  }

  private List<Map<String, Object>> buildPipelinesList(Set<Pipeline> pipelines, String action) {
    return pipelines.stream().map(p -> pipelineEntry(p, action)).toList();
  }

  private List<Map<String, Object>> buildPipelineDiff(
      Set<Pipeline> previous, Set<Pipeline> current) {
    Set<UUID> previousIds =
        previous == null
            ? Set.of()
            : previous.stream().map(Pipeline::getId).collect(Collectors.toSet());
    Map<UUID, Pipeline> currentMap =
        current.stream().collect(Collectors.toMap(Pipeline::getId, p -> p));

    List<Map<String, Object>> result = new ArrayList<>();

    for (Map.Entry<UUID, Pipeline> e : currentMap.entrySet()) {
      String action = previousIds.contains(e.getKey()) ? "UPDATE" : "ADD";
      result.add(pipelineEntry(e.getValue(), action));
    }

    for (UUID id : previousIds) {
      if (!currentMap.containsKey(id)) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("id", id.toString());
        entry.put("version", "1");
        entry.put("action", "DELETE");
        result.add(entry);
      }
    }

    return result;
  }

  private Map<String, Object> pipelineEntry(Pipeline p, String action) {
    var entry = new LinkedHashMap<String, Object>();
    entry.put("id", p.getId().toString());
    entry.put("version", "1");
    entry.put("action", action);
    entry.put("data", p.getModel());
    return entry;
  }

  /**
   * Serializes and sends the saga trigger to Kafka synchronously (blocking). The blocking {@code
   * .get()} ensures that if Kafka is unavailable, an exception is thrown before the calling
   * {@code @Transactional} method returns, causing the transaction to roll back. This prevents the
   * dual-write problem where the DB state is committed but the Kafka message is never delivered.
   */
  private void sendTrigger(String datasetId, Map<String, Object> trigger) {
    String sagaType = (String) trigger.get("sagaType");
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
