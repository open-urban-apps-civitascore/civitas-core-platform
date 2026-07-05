/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi;

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.exception.AdapterException;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.DataStructureSchema;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner;
import de.civitascore.configadapter.nifi.flow.FrostSinkSpec;
import de.civitascore.configadapter.nifi.flow.JdbcSqlSourceProbe;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.PlatformSinkConfig;
import de.civitascore.configadapter.nifi.flow.PostgisSinkSpec;
import de.civitascore.configadapter.nifi.flow.SinkSpec;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.stage.FrostSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.MappingNodeType;
import de.civitascore.configadapter.nifi.flow.stage.MqttSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.PostgisSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.SqlSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.graph.FlowPath;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.rest.NifiRestClient;
import de.civitascore.configadapter.util.PayloadConverter;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.owasp.encoder.Encode;

/**
 * Saga command handler that provisions dataset pipelines on Apache NiFi. It transforms the
 * engine-neutral pipeline graph into a curated NiFi flow and drives the NiFi REST API. Mirrors the
 * operations the orchestrator dispatches to the pipeline adapter:
 *
 * <ul>
 *   <li>{@code DEPLOY_PIPELINES} — deploy pipelines for a new dataset
 *   <li>{@code UPDATE_PIPELINES} — ADD/UPDATE/DELETE per pipeline
 *   <li>{@code DELETE_PIPELINES} — tear down all pipelines for a dataset
 *   <li>{@code RESTORE_PIPELINES} — re-deploy (compensation for UPDATE)
 * </ul>
 */
public class NifiSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "nifi";
  private static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";

  private static final String OP_DEPLOY = "DEPLOY_PIPELINES";
  private static final String OP_UPDATE = "UPDATE_PIPELINES";
  private static final String OP_DELETE = "DELETE_PIPELINES";
  private static final String OP_RESTORE = "RESTORE_PIPELINES";
  private static final String TYPE_COMPENSATE = "COMPENSATE_STEP";

  private static final String FIELD_DATA_PIPELINES = "dataPipelines";
  private static final String FIELD_PIPELINE_IDS = "pipelineIds";
  private static final String FIELD_DATASOURCES = "datasources";
  private static final String FIELD_DATASINKS = "datasinks";
  private static final String FIELD_PROCESS_GROUP_IDS = "nifiProcessGroupIds";
  private static final String FIELD_ID = "id";
  private static final String FIELD_ACTION = "action";
  private static final String FIELD_DATA = "data";
  private static final String FIELD_DATA_SOURCE_IDS = "dataSourceIds";
  private static final String FIELD_DATA_SINK_IDS = "dataSinkIds";
  // Saga-wide variable set by the FROST create/update-project step and propagated into every later
  // step's payload; scopes a FROST flow to the dataset's project.
  private static final String FIELD_PROJECT_ID = "projectId";

  private static final String ACTION_ADD = "ADD";
  private static final String ACTION_UPDATE = "UPDATE";
  private static final String ACTION_DELETE = "DELETE";

  private final GraphParser graphParser = new GraphParser();
  private boolean tlsInsecure = true;
  private CredentialResolver credentialResolver;
  private FlowDeploymentPlanner planner;
  private NifiRestClient nifiClient;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public NifiSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  public Map<String, String> fieldAliases() {
    return Map.of();
  }

  @Override
  @SuppressWarnings("PMD.CloseResource") // the JAX-RS Client is long-lived; closed in close()
  protected void doInitialize(AdapterConfig config) {
    this.tlsInsecure = Boolean.parseBoolean(getProperty("tls.insecure", "true"));
    if (tlsInsecure) {
      log.warn(
          "NiFi TLS verification is DISABLED (nifi.tls.insecure=true) — acceptable for dev only;"
              + " set it to false in non-dev environments");
    }
    String url = getProperty("url", "https://localhost:8443");
    String username = getProperty("username", "admin");
    String password = getProperty("password", "");

    byte[] stretchedKey = loadStretchedKey();
    if (stretchedKey.length == 0) {
      log.warn("{} not set — encrypted credentials cannot be decrypted", MASTER_KEY_ENV);
    }
    this.credentialResolver = new CredentialResolver(stretchedKey);
    Arrays.fill(stretchedKey, (byte) 0);

    PlatformSinkConfig platformSink =
        new PlatformSinkConfig(
            getProperty("postgis.url", null),
            getProperty("postgis.user", null),
            getProperty("postgis.password", null));

    StageRegistry stages =
        new StageRegistry(
            List.of(
                new MqttSourceStage(credentialResolver),
                new SqlSourceStage(credentialResolver, new JdbcSqlSourceProbe())),
            List.of(
                new PostgisSinkStage(platformSink),
                new FrostSinkStage(getProperty("frost.url", null))),
            List.of(new MappingNodeType(new MappingConfigParser(), new RecordPathCompiler())));
    this.planner =
        new FlowDeploymentPlanner(new GraphParser(), new NifiFlowBuilder(stages), stages);

    if (this.nifiClient == null) {
      Client jaxrs = client() != null ? client() : createClient();
      setClient(jaxrs);
      this.nifiClient = new NifiRestClient(url, username, password, jaxrs);
    }
    log.info("NifiSagaHandler initialized for: {}", Encode.forJava(url));
  }

  private byte[] loadStretchedKey() {
    String masterKeyHex = getProperty("master-key", null);
    if (masterKeyHex == null) {
      return CryptoKeyLoader.loadAndStretchKeyFromEnv(MASTER_KEY_ENV);
    }
    try {
      return CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(masterKeyHex));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Invalid master key in adapter config", e);
    }
  }

  @Override
  protected Client createClient() {
    ClientBuilder builder =
        ClientBuilder.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .register(MultiPartFeature.class);
    if (tlsInsecure) {
      builder.sslContext(trustAllContext()).hostnameVerifier((host, session) -> true);
    }
    return builder.build();
  }

  void setTestNifiClient(NifiRestClient client) {
    this.nifiClient = client;
  }

  @Override
  protected SagaCommandResult doHandle(SagaCommandMessage command) {
    return switch (command.operation()) {
      case OP_DEPLOY -> handleDeploy(command);
      case OP_UPDATE -> handleUpdate(command);
      case OP_DELETE -> handleDelete(command);
      case OP_RESTORE -> handleRestore(command);
      default -> unknownOperation(command);
    };
  }

  private SagaCommandResult handleDeploy(SagaCommandMessage command) {
    try {
      List<Datasource> datasources = extractDatasources(command);
      List<Map<String, Object>> datasinks = extractMaps(command, FIELD_DATASINKS);
      String projectId = optionalProjectId(command);
      List<String> pipelineIds = new ArrayList<>();
      List<String> processGroupIds = new ArrayList<>();

      for (Map<String, Object> pipeline : extractMaps(command, FIELD_DATA_PIPELINES)) {
        String id = requireString(pipeline, FIELD_ID);
        processGroupIds.add(deployPipeline(id, pipeline, datasources, datasinks, projectId));
        pipelineIds.add(id);
      }

      Map<String, Object> data =
          Map.of(FIELD_PIPELINE_IDS, pipelineIds, FIELD_PROCESS_GROUP_IDS, processGroupIds);
      return SagaCommandResult.success(command.sagaId(), command.stepId(), data, data);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_DEPLOY, false, e);
    }
  }

  private SagaCommandResult handleUpdate(SagaCommandMessage command) {
    boolean isCompensation = TYPE_COMPENSATE.equals(command.type());
    try {
      List<Datasource> datasources = extractDatasources(command);
      List<Map<String, Object>> datasinks = extractMaps(command, FIELD_DATASINKS);
      String projectId = optionalProjectId(command);
      List<String> processedIds = new ArrayList<>();

      for (Map<String, Object> pipeline : extractMaps(command, FIELD_DATA_PIPELINES)) {
        processedIds.add(applyPipelineAction(pipeline, datasources, datasinks, projectId));
      }

      Map<String, Object> data = Map.of(FIELD_PIPELINE_IDS, processedIds);
      return SagaCommandResult.success(command.sagaId(), command.stepId(), data, data);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_UPDATE, isCompensation, e);
    }
  }

  /**
   * Applies one pipeline's action (ADD/UPDATE deploy, DELETE remove) and returns its id. Extracted
   * from {@link #handleUpdate} so the unknown-action {@code FatalAdapterException} is thrown in a
   * different method than the catch that converts it (no exception-as-flow-control).
   */
  private String applyPipelineAction(
      Map<String, Object> pipeline,
      List<Datasource> datasources,
      List<Map<String, Object>> datasinks,
      String projectId)
      throws FatalAdapterException, RetryableAdapterException {
    String id = requireString(pipeline, FIELD_ID);
    String action = requireString(pipeline, FIELD_ACTION);
    switch (action) {
      case ACTION_ADD, ACTION_UPDATE ->
          deployPipeline(id, pipeline, datasources, datasinks, projectId);
      case ACTION_DELETE -> nifiClient.deleteFlowByName(processGroupName(id));
      default ->
          throw new FatalAdapterException(
              AdapterErrorCode.INVALID_PAYLOAD, "Unknown pipeline action: " + action);
    }
    return id;
  }

  private SagaCommandResult handleDelete(SagaCommandMessage command) {
    boolean isCompensation = TYPE_COMPENSATE.equals(command.type());
    try {
      for (String id : extractStrings(command, FIELD_PIPELINE_IDS)) {
        nifiClient.deleteFlowByName(processGroupName(id));
      }
      if (isCompensation) {
        return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
      }
      return SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_DELETE, isCompensation, e);
    }
  }

  private SagaCommandResult handleRestore(SagaCommandMessage command) {
    try {
      List<Datasource> datasources = extractDatasources(command);
      List<Map<String, Object>> datasinks = extractMaps(command, FIELD_DATASINKS);
      String projectId = optionalProjectId(command);
      for (Map<String, Object> pipeline : extractMaps(command, FIELD_DATA_PIPELINES)) {
        deployPipeline(
            requireString(pipeline, FIELD_ID), pipeline, datasources, datasinks, projectId);
      }
      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_RESTORE, true, e);
    }
  }

  // ─── Deployment ─────────────────────────────────────────────────────────────

  private String deployPipeline(
      String id,
      Map<String, Object> pipeline,
      List<Datasource> datasources,
      List<Map<String, Object>> datasinks,
      String projectId)
      throws FatalAdapterException, RetryableAdapterException {
    Object rawData = pipeline.get(FIELD_DATA);
    // A present-but-non-map graph is a corrupt payload: silently treating it as empty would deploy
    // a bare flow the user never described, so reject it. A missing graph (null) parses to an
    // empty graph, which the path derivation below rejects with its own message — the graph is
    // the authoritative data-flow description, a pipeline without one cannot deploy.
    Map<String, Object> graphData;
    if (rawData == null) {
      graphData = Map.of();
    } else if (rawData instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      graphData = typed;
    } else {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "pipeline '" + FIELD_DATA + "' must be an object");
    }
    // The path is derived once more inside the planner for chain building; the derivation is a
    // deterministic function of the graph, so both see the same source/sink nodes.
    FlowPath path;
    try {
      path = FlowPath.derive(graphParser.parse(graphData));
    } catch (IllegalStateException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    Datasource source =
        resolveSource(path.source(), stringList(pipeline.get(FIELD_DATA_SOURCE_IDS)), datasources);
    SinkSpec sink =
        resolveSink(
            path.sink(), stringList(pipeline.get(FIELD_DATA_SINK_IDS)), datasinks, projectId);
    PipelineDeploymentRequest request;
    try {
      request = new PipelineDeploymentRequest(id, graphData, source, sink);
    } catch (IllegalArgumentException e) {
      // e.g. a blank pipeline id; keep the raw detail internal and publish only the safe
      // external message for the error code.
      throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, e, e.getMessage());
    }
    return nifiClient.deployFlow(planner.plan(request));
  }

  /** The saga's FROST project id from the command payload, or null when absent. */
  private static String optionalProjectId(SagaCommandMessage command) {
    Object value = command.payload().get(FIELD_PROJECT_ID);
    return value == null ? null : String.valueOf(value);
  }

  /**
   * Resolves the pipeline's own datasource: the graph's source node names the entity, the
   * pipeline's {@code dataSourceIds} confirm the association, and the trigger's dataset-wide {@code
   * datasources} array is the configuration catalog the id resolves against. Entries in the catalog
   * that this pipeline does not reference are simply not consulted — other pipelines of the dataset
   * may use them.
   */
  private static Datasource resolveSource(
      GraphNode sourceNode, List<String> dataSourceIds, List<Datasource> datasources)
      throws FatalAdapterException {
    String entityId = entityId(sourceNode, "datasource");
    if (!dataSourceIds.contains(entityId)) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "pipeline references datasource '"
              + entityId
              + "' that is not part of the trigger"
              + " payload");
    }
    return datasources.stream()
        .filter(ds -> entityId.equals(ds.getId()))
        .findFirst()
        .orElseThrow(
            () ->
                new FatalAdapterException(
                    AdapterErrorCode.NIFI_TEMPLATE_ERROR,
                    "pipeline references datasource '"
                        + entityId
                        + "' that is not part of the"
                        + " trigger payload"));
  }

  /** Resolves the pipeline's own datasink — same id-based catalog lookup as the source. */
  @SuppressWarnings("unchecked")
  private static SinkSpec resolveSink(
      GraphNode sinkNode,
      List<String> dataSinkIds,
      List<Map<String, Object>> datasinks,
      String projectId)
      throws FatalAdapterException {
    String entityId = entityId(sinkNode, "datasink");
    Map<String, Object> sink =
        dataSinkIds.contains(entityId)
            ? datasinks.stream()
                .filter(s -> entityId.equals(asString(s.get(FIELD_ID))))
                .findFirst()
                .orElse(null)
            : null;
    if (sink == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "pipeline references datasink '"
              + entityId
              + "' that is not part of the trigger"
              + " payload");
    }
    SinkType type =
        SinkType.fromRaw(asString(sink.get("type")))
            .orElseThrow(
                () ->
                    new FatalAdapterException(
                        AdapterErrorCode.NIFI_TEMPLATE_ERROR,
                        "unsupported sink type: " + sink.get("type")));
    return switch (type) {
      case POSTGIS -> {
        String tableName = null;
        if (sink.get("configuration") instanceof Map<?, ?> config) {
          tableName = asString(((Map<String, Object>) config).get("tableName"));
        }
        yield new PostgisSinkSpec(tableName, resolvePrimaryKey(sink));
      }
      case FROST -> {
        // A FROST flow must be scoped to the dataset's project — unscoped it would post to the
        // server root, invisible to the project-scoped named API. The id is a saga-wide variable
        // from the FROST create/update-project step, so its absence means a mis-ordered or
        // hand-crafted saga.
        if (projectId == null || projectId.isBlank()) {
          throw new FatalAdapterException(
              AdapterErrorCode.INVALID_PAYLOAD,
              "FROST sink requires the saga's '"
                  + FIELD_PROJECT_ID
                  + "' (result of the FROST create-project step)");
        }
        try {
          yield new FrostSinkSpec(projectId);
        } catch (IllegalArgumentException e) {
          // e.g. a non-numeric projectId; keep the raw detail internal and publish only the safe
          // external message for the error code.
          throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, e, e.getMessage());
        }
      }
    };
  }

  /** The graph node's configured entity id — an unconfigured node cannot be resolved. */
  private static String entityId(GraphNode node, String role) throws FatalAdapterException {
    Object entityId = node.data().get("entityId");
    if (entityId instanceof String id && !id.isBlank()) {
      return id;
    }
    throw new FatalAdapterException(
        AdapterErrorCode.NIFI_TEMPLATE_ERROR,
        role + " node '" + node.id() + "' carries no entityId; the node is not configured");
  }

  /** The payload member as a list of its non-null string items (absent/other yields empty). */
  private static List<String> stringList(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof String text) {
        values.add(text);
      }
    }
    return List.copyOf(values);
  }

  /**
   * The sink's primary-key columns via the shared {@link DataStructureSchema#resolvePrimaryKey}
   * "explicit wins, else marker" rule, so the PutDatabaseRecord UPSERT {@code Update Keys} are
   * identical to the PostGIS table's PRIMARY KEY (single source of truth, no divergence).
   */
  @SuppressWarnings("unchecked")
  private static List<String> resolvePrimaryKey(Map<String, Object> sink)
      throws FatalAdapterException {
    Object explicit =
        sink.get("configuration") instanceof Map<?, ?> config
            ? ((Map<String, Object>) config).get("primaryKey")
            : null;
    Map<String, Object> schema =
        sink.get("dataStructure") instanceof Map<?, ?> ds ? (Map<String, Object>) ds : null;
    try {
      return DataStructureSchema.resolvePrimaryKey(explicit, schema);
    } catch (IllegalArgumentException e) {
      throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, e, e.getMessage());
    }
  }

  private static String processGroupName(String pipelineId) {
    return "pipeline-" + pipelineId;
  }

  // ─── Payload extraction ─────────────────────────────────────────────────────

  private static List<Datasource> extractDatasources(SagaCommandMessage command)
      throws FatalAdapterException {
    List<Datasource> result = new ArrayList<>();
    for (Map<String, Object> raw : extractMaps(command, FIELD_DATASOURCES)) {
      try {
        result.add(PayloadConverter.fromValue(raw, Datasource.class));
      } catch (IllegalArgumentException e) {
        throw new FatalAdapterException(
            AdapterErrorCode.INVALID_PAYLOAD, e, "invalid datasource entry");
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> extractMaps(SagaCommandMessage command, String key)
      throws FatalAdapterException {
    Object value = command.payload().getOrDefault(key, List.of());
    if (!(value instanceof List<?> list)) {
      throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, key + " must be a list");
    }
    // Validate every element up front rather than silently dropping a non-Map entry: a malformed
    // entry must fail the whole command (INVALID_PAYLOAD), not yield a partial deployment that the
    // saga would still report as success.
    List<Map<String, Object>> result = new ArrayList<>(list.size());
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new FatalAdapterException(
            AdapterErrorCode.INVALID_PAYLOAD,
            key
                + " must contain only objects, got: "
                + (item == null ? "null" : item.getClass().getSimpleName()));
      }
      result.add((Map<String, Object>) map);
    }
    return result;
  }

  private static List<String> extractStrings(SagaCommandMessage command, String key)
      throws FatalAdapterException {
    Object value = command.payload().getOrDefault(key, List.of());
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    // Validate every element up front: an unchecked (List<String>) cast defers the
    // ClassCastException to the consuming loop, which for DELETE would throw partway through after
    // some pipelines are already gone. Fail cleanly with INVALID_PAYLOAD before any side effect.
    List<String> result = new ArrayList<>(list.size());
    for (Object item : list) {
      if (!(item instanceof String s)) {
        throw new FatalAdapterException(
            AdapterErrorCode.INVALID_PAYLOAD,
            key
                + " must contain only strings, got: "
                + (item == null ? "null" : item.getClass().getSimpleName()));
      }
      result.add(s);
    }
    return result;
  }

  private static String requireString(Map<String, Object> map, String field)
      throws FatalAdapterException {
    Object value = map.get(field);
    if (!(value instanceof String s) || s.isBlank()) {
      // a blank id/action is as unusable as a missing one (e.g. a blank id → "pipeline-" group name
      // that could collide or mis-target), so reject it up front
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "pipeline entry missing string field: " + field);
    }
    return s;
  }

  private static String asString(Object value) {
    return value instanceof String s ? s : null;
  }

  private SagaCommandResult pipelineError(
      SagaCommandMessage command, String operation, boolean isCompensation, AdapterException e) {
    // The published failure event crosses the trust boundary, so it must carry only the safe
    // external message — never e.getInternalMessage(), which for HTTP failures includes NiFi's raw
    // response body (hostnames, the DB URL, validation detail). The full internal text stays in the
    // local log below.
    String error = operation + " failed: " + e.getSafeExternalMessage();
    // Pass the exception last so SLF4J logs the full cause chain (a FatalAdapterException often
    // wraps the underlying GeneralSecurityException/IOException that explains the real failure).
    log.error(
        "{} failed for saga {}: {}",
        operation,
        Encode.forJava(command.sagaId()),
        Encode.forJava(e.getInternalMessage()),
        e);
    return isCompensation
        ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
        : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
  }

  @Override
  public void close() {
    if (credentialResolver != null) {
      credentialResolver.close();
    }
    super.close();
  }

  private static SSLContext trustAllContext() {
    try {
      TrustManager[] trustAll = {
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String type) {}

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String type) {}

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        }
      };
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustAll, new SecureRandom());
      return context;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to build insecure TLS context", e);
    }
  }
}
