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
import de.civitascore.configadapter.adapter.PipelineStatusPublisher;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.exception.AdapterException;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.CoreUrn;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.auth.NifiTokenProvider;
import de.civitascore.configadapter.nifi.auth.OidcClientCredentialsTokenProvider;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner;
import de.civitascore.configadapter.nifi.flow.JdbcSqlSourceProbe;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.PlatformSinkConfig;
import de.civitascore.configadapter.nifi.flow.SinkResolutionContext;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkAuth;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.sink.SinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttTruststoreConfig;
import de.civitascore.configadapter.nifi.flow.stage.source.SqlSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.transform.MappingNodeType;
import de.civitascore.configadapter.nifi.graph.FlowPath;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.rest.NifiRestClient;
import de.civitascore.configadapter.util.PayloadConverter;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
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
  private static final String FIELD_MAPPINGS = "mappings";
  // Saga-wide variable set by the FROST create/update-project step and propagated into every later
  // step's payload; scopes a FROST flow to the dataset's project.
  private static final String FIELD_PROJECT_ID = "projectId";
  // The dataset's technical id, carried by every trigger; used to derive the dedicated per-DataSet
  // PostGIS schema a POSTGIS sink writes into (same WorkspaceNames rule as the GeoServer
  // workspace).
  private static final String FIELD_DATASET_ID = "datasetId";

  private static final String ACTION_ADD = "ADD";
  private static final String ACTION_UPDATE = "UPDATE";
  private static final String ACTION_DELETE = "DELETE";

  private final GraphParser graphParser = new GraphParser();
  private boolean tlsInsecure = true;
  private CredentialResolver credentialResolver;
  private StageRegistry stages;
  private FlowDeploymentPlanner planner;
  private NifiRestClient nifiClient;
  // Dedicated (cert-validating) client for the Keycloak token endpoint; see oidcTokenProvider().
  private OkHttpClient oidcClient;
  private NifiRuntimeMonitor runtimeMonitor;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public NifiSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  public Map<String, String> fieldAliases() {
    return Map.of();
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    this.tlsInsecure = Boolean.parseBoolean(getProperty("tls.insecure", "true"));
    if (tlsInsecure) {
      log.warn(
          "NiFi TLS verification is DISABLED (nifi.tls.insecure=true) — acceptable for dev only;"
              + " set it to false in non-dev environments");
    }
    String url = getProperty("url", "https://localhost:8443");

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

    MqttTruststoreConfig mqttTruststore = mqttTruststore();
    this.stages =
        new StageRegistry(
            List.of(
                new MqttSourceStage(credentialResolver, mqttTruststore),
                new SqlSourceStage(credentialResolver, new JdbcSqlSourceProbe())),
            List.of(
                new PostgisSinkStage(platformSink),
                new FrostSinkStage(getProperty("frost.url", null), resolveFrostSinkAuth(config))),
            List.of(new MappingNodeType(new MappingConfigParser(), new RecordPathCompiler())));
    this.planner =
        new FlowDeploymentPlanner(new GraphParser(), new NifiFlowBuilder(stages), stages);

    if (this.nifiClient == null) {
      OkHttpClient httpClient = client() != null ? client() : createClient();
      setClient(httpClient);
      this.nifiClient = new NifiRestClient(url, oidcTokenProvider(), httpClient, mqttTruststore);
    }
    this.runtimeMonitor =
        new NifiRuntimeMonitor(
            nifiClient, Long.parseLong(getProperty("runtime-monitor.interval-ms", "5000")));
    log.info("NifiSagaHandler initialized for: {}", Encode.forJava(url));
  }

  /**
   * Trust anchor for MQTT broker certificates. Defaults to the JVM's own trust store, which carries
   * the public root CAs, so a broker with a publicly trusted certificate works with no
   * configuration at all. Deliberately not NiFi's node truststore: that one holds no public roots
   * and also backs cluster-internal TLS and the OIDC back-channel. An environment that provisions a
   * dedicated truststore — the only way to trust a private CA — points these at it instead.
   */
  private MqttTruststoreConfig mqttTruststore() {
    MqttTruststoreConfig defaults = MqttTruststoreConfig.jdkTruststore();
    String path = getProperty("mqtt.truststore.path", defaults.path());
    String passwordParameter = getProperty("mqtt.truststore.password-parameter", "");
    // `changeit` belongs to the JDK store alone. A deployment that names its own store and says
    // nothing about the password gets none, rather than a password its store never had — and a
    // named password parameter, the `none` sentinel included, is the complete answer either way.
    String passwordDefault =
        passwordParameter.isEmpty() && path.equals(defaults.path()) ? defaults.password() : "";
    return new MqttTruststoreConfig(
        path,
        getProperty("mqtt.truststore.type", defaults.type()),
        getProperty("mqtt.truststore.password", passwordDefault),
        passwordParameter,
        getProperty("mqtt.truststore.parameter-context", defaults.parameterContext()));
  }

  private FrostSinkAuth resolveFrostSinkAuth(AdapterConfig config) {
    Map<String, Object> properties = new LinkedHashMap<>();
    putIfNotNull(
        properties,
        FrostSinkAuth.BASIC_AUTH_USERNAME,
        getProperty("frost.basic.auth.username", config.getProperty("frost.basic.auth.username")));
    putIfNotNull(
        properties,
        FrostSinkAuth.BASIC_AUTH_PASSWORD,
        getProperty("frost.basic.auth.password", config.getProperty("frost.basic.auth.password")));
    return FrostSinkAuth.fromProperties(credentialResolver, properties);
  }

  private static void putIfNotNull(Map<String, Object> properties, String key, Object value) {
    if (value != null) {
      properties.put(key, value);
    }
  }

  /**
   * Builds the OIDC token provider the NiFi client authenticates with. NiFi 2.x validates these
   * client-credentials tokens against the configured OpenID Connect provider (Keycloak), replacing
   * the former single-user login.
   *
   * <p>It gets its OWN HTTP client, deliberately NOT the NiFi client: disabling TLS verification
   * for a self-signed dev NiFi ({@code nifi.tls.insecure=true}) must never also disable it for the
   * Keycloak token endpoint, or the client secret could be posted over an unverified connection.
   */
  private NifiTokenProvider oidcTokenProvider() {
    String tokenUri =
        getProperty(
            "oidc.token-uri",
            "http://localhost:8080/realms/civitas-core/protocol/openid-connect/token");
    String clientId = getProperty("oidc.client-id", "nifi");
    String clientSecret = getProperty("oidc.client-secret", "");
    String scope = getProperty("oidc.scope", null);
    if (clientSecret.isBlank()) {
      log.warn(
          "nifi.oidc.client-secret is not set — NiFi authentication will fail until"
              + " NIFI_OIDC_CLIENT_SECRET is provided");
    }
    this.oidcClient =
        new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    return new OidcClientCredentialsTokenProvider(
        tokenUri, clientId, clientSecret, scope, oidcClient);
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
  protected OkHttpClient createClient() {
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS);
    if (tlsInsecure) {
      builder
          .sslSocketFactory(trustAllContext().getSocketFactory(), TRUST_ALL_MANAGER)
          .hostnameVerifier((host, session) -> true);
    }
    return builder.build();
  }

  void setTestNifiClient(NifiRestClient client) {
    this.nifiClient = client;
  }

  @Override
  public void setPipelineStatusPublisher(PipelineStatusPublisher publisher) {
    if (runtimeMonitor != null) {
      runtimeMonitor.setPublisher(publisher);
    }
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
    String currentPipelineId = null;
    try {
      List<Datasource> datasources = extractDatasources(command);
      List<Map<String, Object>> datasinks = extractMaps(command, FIELD_DATASINKS);
      String projectId = optionalProjectId(command);
      String datasetId = optionalDatasetId(command);
      List<String> pipelineIds = new ArrayList<>();
      List<String> processGroupIds = new ArrayList<>();

      for (Map<String, Object> pipeline : extractMaps(command, FIELD_DATA_PIPELINES)) {
        String id = requireString(pipeline, FIELD_ID);
        currentPipelineId = id;
        processGroupIds.add(
            deployPipeline(id, pipeline, datasources, datasinks, projectId, datasetId));
        pipelineIds.add(id);
      }

      Map<String, Object> data =
          Map.of(FIELD_PIPELINE_IDS, pipelineIds, FIELD_PROCESS_GROUP_IDS, processGroupIds);
      return SagaCommandResult.success(command.sagaId(), command.stepId(), data, data);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_DEPLOY, false, currentPipelineId, e);
    }
  }

  private SagaCommandResult handleUpdate(SagaCommandMessage command) {
    boolean isCompensation = TYPE_COMPENSATE.equals(command.type());
    String currentPipelineId = null;
    try {
      List<Datasource> datasources = extractDatasources(command);
      List<Map<String, Object>> datasinks = extractMaps(command, FIELD_DATASINKS);
      String projectId = optionalProjectId(command);
      String datasetId = optionalDatasetId(command);
      List<String> processedIds = new ArrayList<>();

      for (Map<String, Object> pipeline : extractMaps(command, FIELD_DATA_PIPELINES)) {
        currentPipelineId = requireString(pipeline, FIELD_ID);
        processedIds.add(
            applyPipelineAction(pipeline, datasources, datasinks, projectId, datasetId));
      }

      Map<String, Object> data = Map.of(FIELD_PIPELINE_IDS, processedIds);
      return SagaCommandResult.success(command.sagaId(), command.stepId(), data, data);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_UPDATE, isCompensation, currentPipelineId, e);
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
      String projectId,
      String datasetId)
      throws FatalAdapterException, RetryableAdapterException {
    String id = requireString(pipeline, FIELD_ID);
    String action = requireString(pipeline, FIELD_ACTION);
    switch (action) {
      case ACTION_ADD, ACTION_UPDATE ->
          deployPipeline(id, pipeline, datasources, datasinks, projectId, datasetId);
      case ACTION_DELETE -> {
        nifiClient.deleteFlowByName(processGroupName(id));
        if (runtimeMonitor != null) {
          runtimeMonitor.unregister(id);
        }
      }
      default ->
          throw new FatalAdapterException(
              AdapterErrorCode.INVALID_PAYLOAD, "Unknown pipeline action: " + action);
    }
    return id;
  }

  private SagaCommandResult handleDelete(SagaCommandMessage command) {
    boolean isCompensation = TYPE_COMPENSATE.equals(command.type());
    String currentPipelineId = null;
    try {
      for (String id : extractStrings(command, FIELD_PIPELINE_IDS)) {
        currentPipelineId = id;
        nifiClient.deleteFlowByName(processGroupName(id));
        if (runtimeMonitor != null) {
          runtimeMonitor.unregister(id);
        }
      }
      if (isCompensation) {
        return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
      }
      return SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_DELETE, isCompensation, currentPipelineId, e);
    }
  }

  private SagaCommandResult handleRestore(SagaCommandMessage command) {
    String currentPipelineId = null;
    try {
      List<Datasource> datasources = extractDatasources(command);
      List<Map<String, Object>> datasinks = extractMaps(command, FIELD_DATASINKS);
      String projectId = optionalProjectId(command);
      String datasetId = optionalDatasetId(command);
      for (Map<String, Object> pipeline : extractMaps(command, FIELD_DATA_PIPELINES)) {
        currentPipelineId = requireString(pipeline, FIELD_ID);
        deployPipeline(currentPipelineId, pipeline, datasources, datasinks, projectId, datasetId);
      }
      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_RESTORE, true, currentPipelineId, e);
    }
  }

  // ─── Deployment ─────────────────────────────────────────────────────────────

  private String deployPipeline(
      String id,
      Map<String, Object> pipeline,
      List<Datasource> datasources,
      List<Map<String, Object>> datasinks,
      String projectId,
      String datasetId)
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
        resolveSource(
            path.source(),
            stringList(FIELD_DATA_SOURCE_IDS, pipeline.get(FIELD_DATA_SOURCE_IDS)),
            datasources);
    SinkSpec sink =
        resolveSink(
            path.sink(),
            stringList(FIELD_DATA_SINK_IDS, pipeline.get(FIELD_DATA_SINK_IDS)),
            datasinks,
            projectId,
            datasetId);
    Map<String, Object> mappings = mappingsCatalog(pipeline.get(FIELD_MAPPINGS));
    PipelineDeploymentRequest request;
    try {
      request = new PipelineDeploymentRequest(id, graphData, source, sink, mappings);
    } catch (IllegalArgumentException e) {
      // e.g. a blank pipeline id; keep the raw detail internal and publish only the safe
      // external message for the error code.
      throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, e, e.getMessage());
    }
    String processGroupId = nifiClient.deployFlow(planner.plan(request));
    if (runtimeMonitor != null) {
      runtimeMonitor.register(id, datasetId, processGroupId);
    }
    return processGroupId;
  }

  /** The saga's FROST project id from the command payload, or null when absent. */
  private static String optionalProjectId(SagaCommandMessage command) {
    Object value = command.payload().get(FIELD_PROJECT_ID);
    return value == null ? null : String.valueOf(value);
  }

  /** The dataset's technical id from the command payload, or null when absent. */
  private static String optionalDatasetId(SagaCommandMessage command) {
    Object value = command.payload().get(FIELD_DATASET_ID);
    return value == null ? null : String.valueOf(value);
  }

  /**
   * Resolves the pipeline's own datasource: the graph's source node names the entity by its
   * configuration CORE URN ({@code sourceRef}), the pipeline's {@code dataSourceIds} confirm the
   * association, and the trigger's dataset-wide {@code datasources} array is the URN-keyed
   * configuration catalog the ref resolves against. Matching tolerates a version drift between the
   * pinned ref and the catalog key ({@link CoreUrn#sameArtifact}). Entries in the catalog that this
   * pipeline does not reference are simply not consulted — other pipelines of the dataset may use
   * them.
   */
  private static Datasource resolveSource(
      GraphNode sourceNode, List<String> dataSourceIds, List<Datasource> datasources)
      throws FatalAdapterException {
    String sourceRef = requireRef(sourceNode.sourceRef(), sourceNode, "datasource", "sourceRef");
    if (dataSourceIds.isEmpty()) {
      // Distinct from the ref-mismatch below: an empty list means the trigger carries no
      // per-pipeline association at all — it predates the dataSourceIds contract or the
      // datasource was never associated with the pipeline. Naming a ref mismatch here would
      // send an operator hunting a divergence that does not exist.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "pipeline carries no dataSourceIds: the trigger predates per-pipeline associations or"
              + " the datasource is not associated with the pipeline; re-publish the dataset");
    }
    if (!containsUrn(dataSourceIds, sourceRef)) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "pipeline references datasource '"
              + sourceRef
              + "' that is not part of the trigger"
              + " payload");
    }
    return datasources.stream()
        .filter(ds -> CoreUrn.sameArtifact(ds.getId(), sourceRef))
        .findFirst()
        .orElseThrow(
            () ->
                new FatalAdapterException(
                    AdapterErrorCode.NIFI_TEMPLATE_ERROR,
                    "pipeline references datasource '"
                        + sourceRef
                        + "' that is not part of the"
                        + " trigger payload"));
  }

  /** Resolves the pipeline's own datasink — same URN-based catalog lookup as the source. */
  private SinkSpec resolveSink(
      GraphNode sinkNode,
      List<String> dataSinkIds,
      List<Map<String, Object>> datasinks,
      String projectId,
      String datasetId)
      throws FatalAdapterException {
    String sinkRef = requireRef(sinkNode.sinkRef(), sinkNode, "datasink", "sinkRef");
    if (dataSinkIds.isEmpty()) {
      // Same distinction as resolveSource: no association at all is a different defect than a
      // diverging ref.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "pipeline carries no dataSinkIds: the trigger predates per-pipeline associations or"
              + " the datasink is not associated with the pipeline; re-publish the dataset");
    }
    Map<String, Object> sink =
        containsUrn(dataSinkIds, sinkRef)
            ? datasinks.stream()
                .filter(s -> CoreUrn.sameArtifact(asString(s.get(FIELD_ID)), sinkRef))
                .findFirst()
                .orElse(null)
            : null;
    if (sink == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "pipeline references datasink '"
              + sinkRef
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
    return stages.sink(type).parseSpec(sink, new SinkResolutionContext(projectId, datasetId));
  }

  /** The graph node's configured configuration URN — an unconfigured node cannot be resolved. */
  private static String requireRef(String ref, GraphNode node, String role, String refField)
      throws FatalAdapterException {
    if (ref != null && !ref.isBlank()) {
      return ref;
    }
    throw new FatalAdapterException(
        AdapterErrorCode.NIFI_TEMPLATE_ERROR,
        role + " node '" + node.id() + "' carries no " + refField + "; the node is not configured");
  }

  /** Whether {@code urns} contains a reference to the same artifact as {@code ref}. */
  private static boolean containsUrn(List<String> urns, String ref) {
    return urns.stream().anyMatch(u -> CoreUrn.sameArtifact(u, ref));
  }

  /**
   * The pipeline entry's shipped mappings catalog (Mapping CORE URN → mapping document). Absent
   * yields empty; a present-but-non-object value is a corrupt payload (INVALID_PAYLOAD) rather than
   * silently coerced to empty, which would later fail the deploy with a misleading "not shipped"
   * mapping error.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> mappingsCatalog(Object raw) throws FatalAdapterException {
    if (raw == null) {
      return Map.of();
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, FIELD_MAPPINGS + " must be an object");
    }
    return (Map<String, Object>) map;
  }

  /**
   * The payload member as a list of strings (absent yields empty). A present-but-non-list value or
   * a non-string item is a corrupt payload: coercing it to empty would fail the deploy later with a
   * misleading "not part of the trigger payload" id-association error instead of naming the actual
   * field defect.
   */
  private static List<String> stringList(String key, Object raw) throws FatalAdapterException {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, key + " must be a list of strings");
    }
    return requireStringItems(key, list);
  }

  /** Every item as a string; a non-string item is a corrupt payload (INVALID_PAYLOAD). */
  private static List<String> requireStringItems(String key, List<?> list)
      throws FatalAdapterException {
    List<String> values = new ArrayList<>(list.size());
    for (Object item : list) {
      if (!(item instanceof String text)) {
        throw new FatalAdapterException(
            AdapterErrorCode.INVALID_PAYLOAD,
            key
                + " must contain only strings, got: "
                + (item == null ? "null" : item.getClass().getSimpleName()));
      }
      values.add(text);
    }
    return List.copyOf(values);
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
      throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, key + " must be a list");
    }
    // Validate every element up front: an unchecked (List<String>) cast defers the
    // ClassCastException to the consuming loop, which for DELETE would throw partway through after
    // some pipelines are already gone. Fail cleanly with INVALID_PAYLOAD before any side effect.
    return requireStringItems(key, list);
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
      SagaCommandMessage command,
      String operation,
      boolean isCompensation,
      String pipelineId,
      AdapterException e) {
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
    Map<String, Object> status = new LinkedHashMap<>();
    if (pipelineId != null) {
      status.put("type", "PIPELINE_STATUS_CHANGED");
      status.put("eventId", UUID.randomUUID().toString());
      status.put("datasetId", optionalDatasetId(command));
      status.put("pipelineId", pipelineId);
      status.put("status", "ERROR");
      status.put("source", "DEPLOYMENT");
      status.put("message", e.getSafeExternalMessage());
      status.put("stacktrace", PipelineMessageSanitizer.sanitize(e.getInternalMessage()));
      status.put("occurredAt", Instant.now().toString());
      status.put("correlationId", command.sagaId());
    }
    Map<String, Object> resultData =
        pipelineId == null ? Map.of() : Map.of("pipelineStatus", status);
    return isCompensation
        ? SagaCommandResult.compensationFailure(
            command.sagaId(), command.stepId(), resultData, error)
        : SagaCommandResult.failure(command.sagaId(), command.stepId(), resultData, error);
  }

  @Override
  public void close() {
    if (runtimeMonitor != null) {
      runtimeMonitor.close();
    }
    if (credentialResolver != null) {
      credentialResolver.close();
    }
    if (oidcClient != null) {
      oidcClient.dispatcher().executorService().shutdown();
      oidcClient.connectionPool().evictAll();
    }
    super.close();
  }

  private static final X509TrustManager TRUST_ALL_MANAGER =
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String type) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String type) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };

  private static SSLContext trustAllContext() {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {TRUST_ALL_MANAGER}, new SecureRandom());
      return context;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to build insecure TLS context", e);
    }
  }
}
