/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.graph.PipelineGraph;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns one resolved pipeline (graph + source + sink) into a {@link DeploymentPlan}: it compiles
 * the mapping to RecordPath, resolves the source/sink processor properties, decrypts secrets
 * (collected separately for a post-upload REST push), and delegates the NiFi flow assembly to
 * {@link NifiFlowBuilder} (programmatic composition of building blocks per the graph).
 */
public class FlowDeploymentPlanner {

  private static final String MQTT_PROCESSOR = "ConsumeMQTT";
  private static final String DBCP = "PostGISConnectionPool";

  /** A plain seconds value, optionally with a seconds unit suffix (e.g. {@code 5}, {@code 5s}). */
  private static final Pattern SECONDS =
      Pattern.compile("(\\d+)\\s*(?:s|sec|secs|second|seconds)?", Pattern.CASE_INSENSITIVE);

  private final ObjectMapper mapper = new ObjectMapper();
  private final GraphParser graphParser;
  private final MappingConfigParser mappingConfigParser;
  private final RecordPathCompiler recordPathCompiler;
  private final NifiFlowBuilder flowBuilder;
  private final CredentialResolver credentialResolver;
  private final PlatformSinkConfig platformSink;
  private final String frostBaseUrl;

  /** Platform-managed sink connection (not a tenant credential). */
  public record PlatformSinkConfig(String postgisUrl, String postgisUser, String postgisPassword) {}

  /**
   * Creates a planner.
   *
   * @param graphParser the graph parser
   * @param mappingConfigParser the mapping parser
   * @param recordPathCompiler the RecordPath compiler
   * @param flowBuilder the NiFi flow builder
   * @param credentialResolver the credential resolver
   * @param platformSink the platform sink connection (may be null)
   * @param frostBaseUrl the FROST SensorThings base URL for FROST sinks (may be null)
   */
  public FlowDeploymentPlanner(
      GraphParser graphParser,
      MappingConfigParser mappingConfigParser,
      RecordPathCompiler recordPathCompiler,
      NifiFlowBuilder flowBuilder,
      CredentialResolver credentialResolver,
      PlatformSinkConfig platformSink,
      String frostBaseUrl) {
    this.graphParser = graphParser;
    this.mappingConfigParser = mappingConfigParser;
    this.recordPathCompiler = recordPathCompiler;
    this.flowBuilder = flowBuilder;
    this.credentialResolver = credentialResolver;
    this.platformSink = platformSink;
    this.frostBaseUrl = frostBaseUrl;
  }

  /**
   * Plans the deployment of a single pipeline.
   *
   * @param request the resolved pipeline inputs
   * @return the deployment plan
   * @throws FatalAdapterException if the source/sink is unsupported or the mapping is invalid
   */
  public DeploymentPlan plan(PipelineDeploymentRequest request) throws FatalAdapterException {
    PipelineGraph graph = graphParser.parse(request.graphData());
    Optional<MappingConfig> mapping = parseMapping(graph);
    List<UpdateRecordProperty> mappingProperties =
        mapping.map(recordPathCompiler::compile).orElseGet(List::of);

    Datasource source = request.source();
    if (source == null) {
      throw template("<no source>");
    }
    SourceType sourceType =
        SourceType.fromRaw(source.getType()).orElseThrow(() -> template(source.getType()));
    SinkSpec sink = request.sink();

    Map<String, String> sourceProperties = new LinkedHashMap<>();
    Map<String, String> sinkProperties = new LinkedHashMap<>();
    Map<String, Map<String, String>> controllerServiceProperties = new LinkedHashMap<>();
    Map<String, Map<String, String>> sensitive = new LinkedHashMap<>();

    bindSource(sourceType, source, sourceProperties, sensitive);
    bindSink(sink, sinkProperties, controllerServiceProperties, sensitive);

    String processGroupName = "pipeline-" + request.pipelineId();
    String snapshot =
        flowBuilder.build(
            new FlowBuildSpec(
                processGroupName,
                sourceType,
                sourceProperties,
                sink.type(),
                sinkProperties,
                mappingProperties,
                controllerServiceProperties));

    return new DeploymentPlan(processGroupName, snapshot, Map.copyOf(sensitive));
  }

  private Optional<MappingConfig> parseMapping(PipelineGraph graph) throws FatalAdapterException {
    Optional<GraphNode> mappingNode;
    try {
      mappingNode = graph.transformNode();
    } catch (IllegalStateException e) {
      // an unbuildable graph topology (unsupported node kind, multiple/disconnected mapping nodes)
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    if (mappingNode.isEmpty()) {
      return Optional.empty();
    }
    Object rawConfig = mappingNode.get().data().get("mappingConfig");
    if (rawConfig == null) {
      // A wired mapping node must carry a config; a missing one is a corrupted payload that would
      // otherwise deploy untransformed. (A pipeline with no mapping node at all is fine — handled
      // above by the empty Optional.)
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "mapping node has no mappingConfig");
    }
    return Optional.of(mappingConfigParser.parse(mapper.valueToTree(rawConfig)));
  }

  private void bindSource(
      SourceType sourceType,
      Datasource source,
      Map<String, String> sourceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    Map<String, Object> original = source.getAdditionalProperties();
    Map<String, Object> decrypted = credentialResolver.decrypt(original);
    if (sourceType == SourceType.MQTT) {
      bindMqttSource(decrypted, original, sourceProperties, sensitive);
    }
  }

  /**
   * Binds an MQTT datasource to ConsumeMQTT, using the portal's connector field names ({@code
   * urls}/{@code topics} as lists, {@code user}, {@code client_id}, {@code qos}). Broker URI and
   * Topic Filter are required — without them ConsumeMQTT would fall back to the fragment's demo
   * broker/topic and silently consume from the wrong source, so a missing value fails the deploy.
   */
  private void bindMqttSource(
      Map<String, Object> decrypted,
      Map<String, Object> original,
      Map<String, String> sourceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    // NiFi's Broker URI accepts a comma-separated list; Topic Filter is a single filter, so one
    // ConsumeMQTT cannot subscribe to multiple distinct topics — reject rather than mis-build.
    List<String> brokers = trimmedNonBlank(decrypted.get("urls"));
    List<String> topics = trimmedNonBlank(decrypted.get("topics"));
    if (brokers.isEmpty() || topics.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT source requires non-empty 'urls' and 'topics'");
    }
    rejectTlsSource(decrypted.get("tls"), brokers);
    if (topics.size() > 1) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "multiple MQTT topics are not supported (one ConsumeMQTT subscribes to a single topic"
              + " filter): "
              + topics);
    }
    sourceProperties.put("Broker URI", String.join(",", brokers));
    sourceProperties.put("Topic Filter", topics.get(0));
    putIfPresent(sourceProperties, "Username", decrypted.get("user"));
    putIfPresent(sourceProperties, "Client ID", decrypted.get("client_id"));
    putIfPresent(sourceProperties, "Quality of Service", decrypted.get("qos"));
    bindSeconds(sourceProperties, "Connection Timeout", decrypted.get("connect_timeout"));
    bindSeconds(sourceProperties, "Keep Alive", decrypted.get("keepalive"));
    if (isEncrypted(original.get("password"))) {
      sensitive
          .computeIfAbsent(MQTT_PROCESSOR, k -> new LinkedHashMap<>())
          .put("Password", String.valueOf(decrypted.get("password")));
    }
  }

  /**
   * Rejects a TLS MQTT source: NiFi requires an SSL Context Service for a TLS broker, which this
   * adapter does not provision, so deploying would silently fall back to a plaintext connection.
   * Both an explicit {@code tls.enabled=true} and a TLS broker scheme ({@code ssl://}/{@code
   * mqtts://}/{@code wss://}) are rejected.
   */
  private void rejectTlsSource(Object tls, List<String> brokers) throws FatalAdapterException {
    boolean tlsEnabled =
        tls instanceof Map<?, ?> map && "true".equalsIgnoreCase(String.valueOf(map.get("enabled")));
    boolean tlsScheme =
        brokers.stream()
            .map(b -> b.toLowerCase(Locale.ROOT))
            .anyMatch(
                b -> b.startsWith("ssl://") || b.startsWith("mqtts://") || b.startsWith("wss://"));
    if (tlsEnabled || tlsScheme) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT TLS is not supported yet (needs a NiFi SSL Context Service)");
    }
  }

  /** A scalar or list value as trimmed, non-blank strings (empty for null/all-blank). */
  private static List<String> trimmedNonBlank(Object value) {
    List<String> raw =
        value instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : value == null ? List.of() : List.of(String.valueOf(value));
    return raw.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /**
   * Binds a portal duration (the connector sends e.g. {@code "5s"}/{@code "30s"}) to a NiFi
   * property as the plain integer seconds it expects. An absent/blank value is skipped; a non-blank
   * value that is not a simple seconds duration is rejected rather than silently dropped (which
   * would leave NiFi's default).
   */
  private void bindSeconds(Map<String, String> properties, String nifiKey, Object value)
      throws FatalAdapterException {
    if (value == null) {
      return;
    }
    String text = String.valueOf(value).trim();
    if (text.isEmpty()) {
      return;
    }
    Matcher matcher = SECONDS.matcher(text);
    if (!matcher.matches()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT '" + nifiKey + "' is not a valid seconds duration: " + text);
    }
    properties.put(nifiKey, matcher.group(1));
  }

  private void bindSink(
      SinkSpec sink,
      Map<String, String> sinkProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    switch (sink.type()) {
      case POSTGIS -> {
        // SinkSpec guarantees a non-blank tableName for POSTGIS (the invalid state is rejected at
        // construction), so PutDatabaseRecord always has a target here.
        sinkProperties.put("Table Name", sink.tableName());
        bindPlatformDbcp(controllerServiceProperties, sensitive);
      }
      case FROST -> bindFrost(sinkProperties);
    }
  }

  private void bindFrost(Map<String, String> sinkProperties) throws FatalAdapterException {
    if (frostBaseUrl == null || frostBaseUrl.isBlank()) {
      // A localhost fallback would deploy a flow that silently posts observations into the void.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires the FROST base URL (nifi.frost.url) to be configured");
    }
    sinkProperties.put("HTTP Method", "POST");
    sinkProperties.put("HTTP URL", frostBaseUrl + "/Observations");
    sinkProperties.put("Request Content-Type", "application/json");
  }

  private void bindPlatformDbcp(
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    if (platformSink == null
        || platformSink.postgisUrl() == null
        || platformSink.postgisUrl().isBlank()) {
      // A POSTGIS flow without a DB connection URL would deploy but never enable its DBCP service.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "POSTGIS sink configured but no platform database connection URL is available");
    }
    Map<String, String> dbcp = new LinkedHashMap<>();
    putIfPresent(dbcp, "Database Connection URL", platformSink.postgisUrl());
    putIfPresent(dbcp, "Database User", platformSink.postgisUser());
    if (!dbcp.isEmpty()) {
      controllerServiceProperties.put(DBCP, dbcp);
    }
    if (platformSink.postgisPassword() != null) {
      sensitive
          .computeIfAbsent(DBCP, k -> new LinkedHashMap<>())
          .put("Password", platformSink.postgisPassword());
    }
  }

  private static void putIfPresent(Map<String, String> target, String key, Object value) {
    if (value != null) {
      target.put(key, String.valueOf(value));
    }
  }

  private static boolean isEncrypted(Object value) {
    return value instanceof String text && text.startsWith("ENC(") && text.endsWith(")");
  }

  private static FatalAdapterException template(String combination) {
    return new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, combination);
  }
}
