package de.civitascore.portal.messaging.saga;

import de.civitascore.configadapter.model.dataset.DataPipeline;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.model.dataset.NamedApi;
import de.civitascore.portal.configuration.SagaProperties;
import de.civitascore.portal.model.datasink.PostgisConfiguration;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.PipelineAction;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.saga.DataSinkPayload;
import de.civitascore.portal.model.saga.LayerPayload;
import de.civitascore.portal.model.saga.StylePayload;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.util.InvalidInputException;
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
 * create, update, and delete saga triggers that provision or tear down FROST, APISIX, and
 * pipeline-engine infrastructure. Sends synchronously to ensure Kafka acceptance before the
 * database transaction commits.
 */
@Slf4j
@Service
public class DataSetSagaPublisher {

  private final KafkaTemplate<String, String> eventKafkaTemplate;
  private final ObjectMapper objectMapper;
  private final SagaProperties sagaProperties;
  private final DataSinkRepository dataSinkRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  public DataSetSagaPublisher(
      KafkaTemplate<String, String> eventKafkaTemplate,
      ObjectMapper objectMapper,
      SagaProperties sagaProperties,
      DataSinkRepository dataSinkRepository,
      ModelRegistryGateway modelRegistryGateway) {
    this.eventKafkaTemplate = eventKafkaTemplate;
    this.objectMapper = objectMapper;
    this.sagaProperties = sagaProperties;
    this.dataSinkRepository = dataSinkRepository;
    this.modelRegistryGateway = modelRegistryGateway;
  }

  /**
   * Publishes a {@code DATASET_CREATE} saga trigger. See {@link SagaTrigger} for the contract.
   *
   * <p>{@code openDataAccess} is intentionally NOT part of the saga payload: routes are always
   * provisioned protected and the FROST project is always private. Open data access is decided by
   * OPA at request time from the persisted {@code openDataAccess} flag (read via the AuthZ
   * Repository), not by saga-time route/FROST configuration.
   */
  public void publishCreateRequested(DataSet dataset) {
    verifyLayerStyleReferences(dataset);
    var trigger =
        SagaTrigger.DatasetCreate.of(
            dataset.getId().toString(),
            dataset.getName(),
            dataset.getDescription(),
            buildDatasources(dataset),
            buildDatasinks(dataset),
            buildLayers(dataset),
            buildStyles(dataset),
            buildPipelines(dataset.getPipelines(), PipelineAction.ADD),
            buildNamedApis(dataset));
    sendTrigger(trigger);
  }

  /**
   * Publishes a {@code DATASET_UPDATE} saga trigger with a pipeline diff against {@code
   * previousPipelines}. Toggling {@code openDataAccess} does not trigger any route/FROST change —
   * it only changes what OPA reads per request (see {@link #publishCreateRequested}).
   */
  public void publishUpdateRequested(DataSet dataset, Set<Pipeline> previousPipelines) {
    verifyLayerStyleReferences(dataset);
    var trigger =
        SagaTrigger.DatasetUpdate.of(
            dataset.getId().toString(),
            dataset.getName(),
            dataset.getDescription(),
            dataset.getProjectId(),
            buildRouteIds(dataset),
            dataset.getServiceId(),
            dataset.getPipelineIds(),
            buildDatasources(dataset),
            buildDatasinks(dataset),
            buildLayers(dataset),
            buildStyles(dataset),
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
            buildDatasinks(dataset),
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
              Map<String, Object> configuration =
                  fetchConfiguration(ds.getConfigurationUrn(), "DataSource", ds.getId());
              if (configuration != null) {
                configuration.forEach(datasource::handleUnknownProperty);
              }
              return datasource;
            })
        .toList();
  }

  /**
   * Reads a configuration payload from the registry by its versioned pin. Returns {@code null} when
   * no configuration was ever stored ({@code urn} null — e.g. FROST sinks); fails the publish when
   * a stored pin cannot be resolved (host/registry inconsistency).
   */
  private Map<String, Object> fetchConfiguration(String urn, String entityName, UUID entityId) {
    if (urn == null) {
      return null;
    }
    return modelRegistryGateway
        .fetchPayload(urn)
        .map(ModelRegistryGateway.RegistryDocument::content)
        .orElseThrow(
            () ->
                new InvalidInputException(
                    entityName,
                    entityId,
                    "Stored configuration not found in the model registry: " + urn));
  }

  /** All datasinks belonging to the dataset, regardless of pipeline attachment. */
  private List<DataSinkPayload> buildDatasinks(DataSet dataset) {
    return dataSinkRepository.findByDataSetId(dataset.getId()).stream()
        .map(this::toDataSinkPayload)
        .toList();
  }

  private DataSinkPayload toDataSinkPayload(DataSink sink) {
    Map<String, Object> configuration =
        fetchConfiguration(sink.getConfigurationUrn(), "DataSink", sink.getId());
    return new DataSinkPayload(
        sink.getId().toString(),
        sink.getDataSinkType() != null ? sink.getDataSinkType().name() : null,
        configuration,
        resolveDataStructure(sink, configuration));
  }

  /**
   * All WFS/WMS layers attached to the dataset, mapped to the payload shape. Returns {@code null}
   * when the dataset has no layers so {@code @JsonInclude(NON_NULL)} drops the field — keeps {@code
   * hasLayers=false} on the consumer side when no layers are configured.
   */
  private List<LayerPayload> buildLayers(DataSet dataset) {
    if (dataset.getLayers() == null || dataset.getLayers().isEmpty()) {
      return null;
    }
    return dataset.getLayers().stream().map(this::toLayerPayload).toList();
  }

  private LayerPayload toLayerPayload(Layer layer) {
    return new LayerPayload(
        layer.getId().toString(),
        layer.getLayerName(),
        resolveNativeName(layer),
        layer.getCrs(),
        layer.getGeometryColumnRef(),
        layer.getDefaultStyle() != null ? layer.getDefaultStyle().getName() : null,
        buildAlternativeStyleNames(layer));
  }

  /**
   * Sorted list of style names a layer references in addition to its default. Sorted so the JSON
   * output is stable across runs (the underlying {@link java.util.Set} has no defined iteration
   * order). Returns {@code null} when empty so {@code @JsonInclude(NON_NULL)} drops the field.
   */
  private List<String> buildAlternativeStyleNames(Layer layer) {
    if (layer.getAlternativeStyles() == null || layer.getAlternativeStyles().isEmpty()) {
      return null;
    }
    return layer.getAlternativeStyles().stream().map(Style::getName).sorted().toList();
  }

  /**
   * All SLD styles attached to the dataset, mapped to the payload shape. Carried once at the
   * dataset level; layers reference these by name. Returns {@code null} when the dataset has no
   * styles so {@code @JsonInclude(NON_NULL)} drops the field — keeps {@code hasStyles=false} on the
   * consumer side.
   */
  private List<StylePayload> buildStyles(DataSet dataset) {
    if (dataset.getStyles() == null || dataset.getStyles().isEmpty()) {
      return null;
    }
    return dataset.getStyles().stream()
        .map(s -> new StylePayload(s.getName(), s.getSldContent()))
        .toList();
  }

  /**
   * Fails the publish if a layer references a style that is not owned by the same dataset. Identity
   * is checked by {@code Style.id} (not name) so a layer cannot pick up a style from another
   * dataset that happens to share a name. The DB does not enforce this cross-table invariant, so
   * this check is defense-in-depth. Failing here propagates as an {@link InvalidInputException}
   * before the trigger is serialized or sent to Kafka.
   */
  private void verifyLayerStyleReferences(DataSet dataset) {
    if (dataset.getLayers() == null || dataset.getLayers().isEmpty()) {
      return;
    }
    Set<UUID> ownedStyleIds =
        dataset.getStyles() == null
            ? Set.of()
            : dataset.getStyles().stream().map(Style::getId).collect(Collectors.toSet());
    for (Layer layer : dataset.getLayers()) {
      if (layer.getDefaultStyle() != null) {
        checkStyleOwnership(layer, layer.getDefaultStyle(), ownedStyleIds, "defaultStyle");
      }
      if (layer.getAlternativeStyles() != null) {
        for (Style s : layer.getAlternativeStyles()) {
          checkStyleOwnership(layer, s, ownedStyleIds, "alternativeStyles");
        }
      }
    }
  }

  private void checkStyleOwnership(
      Layer layer, Style style, Set<UUID> ownedStyleIds, String field) {
    if (style.getId() == null || !ownedStyleIds.contains(style.getId())) {
      throw new InvalidInputException(
          "Layer",
          field,
          "Layer " + layer.getId() + " references style not owned by dataset: " + style.getName());
    }
  }

  /**
   * Resolves the PostGIS table name for a layer attached to a POSTGIS sink. Returns {@code null}
   * for layers on non-POSTGIS sinks; the adapter then falls back to the sole POSTGIS table on the
   * dataset or to the layer name.
   */
  private String resolveNativeName(Layer layer) {
    DataSink sink = layer.getDataSink();
    if (sink == null || sink.getDataSinkType() != DataSinkType.POSTGIS) {
      return null;
    }
    Map<String, Object> configuration =
        fetchConfiguration(sink.getConfigurationUrn(), "DataSink", sink.getId());
    if (configuration == null) {
      return null;
    }
    return objectMapper.convertValue(configuration, PostgisConfiguration.class).getTableName();
  }

  /**
   * Resolves the sink's referenced data-structure model (JSON Schema) from the Model Forge registry
   * directly via the configuration's {@code element} CORE URN. The inlined view (every
   * CORE-URN {@code $ref} inlined, {@code x-ui-styles} stripped) keeps the CloudEvent contract a
   * pure schema document — the config-adapter keeps reading {@code x-core-primaryKey} from {@code
   * datasinks[].dataStructure} unchanged. {@code null} when no element is referenced (e.g. FROST
   * passthrough); throws {@link InvalidInputException} if the referenced element is missing or
   * carries no model, failing the publish. The URN is already validated at sink save time.
   */
  private Map<String, Object> resolveDataStructure(DataSink sink, Map<String, Object> sinkConfig) {
    if (sinkConfig == null) {
      return null;
    }
    String element =
        objectMapper.convertValue(sinkConfig, PostgisConfiguration.class).getElement();
    if (element == null) {
      return null; // e.g. FROST passthrough sink — no referenced element
    }
    return modelRegistryGateway
        .fetchInlinedModel(element)
        .filter(model -> !model.isEmpty())
        .orElseThrow(
            () ->
                new InvalidInputException(
                    "DataSink",
                    "configuration.element",
                    "Referenced Element " + element + " not found or has no model"));
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
        result.add(
            new DataPipeline(
                id.toString(), "0", PipelineAction.DELETE.name(), null, List.of(), List.of()));
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
        .map(api -> new NamedApi(api.getSlug(), api.getStandard().name(), api.getVersion()))
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
   *   <li>{@code null} / field omitted — no existing routes; the CREATE saga provisions one route
   *       per named API from scratch.
   *   <li>Non-empty map — one entry per named-API route the dataset already owns, keyed by slug. It
   *       is 1:1 with {@code namedApis} because named APIs are immutable once the dataset is
   *       released (#1379/#1384): the APISIX handler iterates this map to UPDATE (re-apply auth) or
   *       DELETE/RESTORE each route — it does NOT add routes for new slugs. New named APIs are only
   *       ever provisioned by a fresh CREATE saga (unrelease → edit in DRAFT → re-release).
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
    // The relation is a @Builder.Default set and Hibernate never loads it as null (empty for a
    // pipeline without sources), so null can only mean a corrupted entity reached the publisher —
    // fail loud rather than publish a silently empty association.
    if (pipeline.getDataSources() == null) {
      throw invariant("pipeline %s has a null dataSources relation", pipeline.getId());
    }
    List<String> dataSourceIds =
        pipeline.getDataSources().stream().map(ds -> ds.getId().toString()).sorted().toList();
    // The model holds the editor-built, engine-neutral pipeline graph (nodes/edges + inline
    // mappingConfig), stored in the Model Forge registry and pinned by the pipeline's modelUrn.
    // It is forwarded to the config-adapter with the same shape as before — the gateway strips
    // the x-ui-styles keyword (React Flow layout), which was never part of the saga payload.
    // The config-adapter (NiFi) is the only place engine specifics appear.
    Map<String, Object> model =
        pipeline.getModelUrn() == null
            ? null
            : modelRegistryGateway
                .fetchPayload(pipeline.getModelUrn())
                .map(ModelRegistryGateway.RegistryDocument::content)
                .orElseThrow(
                    () ->
                        new InvalidInputException(
                            "Pipeline",
                            pipeline.getId(),
                            "Stored pipeline definition not found in the model registry: "
                                + pipeline.getModelUrn()));
    return new DataPipeline(
        pipeline.getId().toString(),
        String.valueOf(pipeline.getVersion()),
        action.name(),
        model,
        // Sorted so the payload is deterministic — the entity relations are unordered sets.
        dataSourceIds,
        dataSinkRepository.findByPipelineId(pipeline.getId()).stream()
            .map(sink -> sink.getId().toString())
            .sorted()
            .toList());
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
