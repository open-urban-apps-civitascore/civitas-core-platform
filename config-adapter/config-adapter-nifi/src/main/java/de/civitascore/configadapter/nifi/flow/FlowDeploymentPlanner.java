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
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns one resolved pipeline (graph + source + sink) into a {@link DeploymentPlan}: it compiles
 * the mapping to RecordPath, resolves the source/sink processor properties, decrypts secrets
 * (collected separately for a post-upload REST push), and delegates the NiFi flow assembly to
 * {@link NifiFlowBuilder} (programmatic composition of building blocks per the graph).
 */
public class FlowDeploymentPlanner {

  private static final String MQTT_PROCESSOR = "ConsumeMQTT";
  private static final String DBCP = "PostGISConnectionPool";

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
    requireUniformStrategy(mappingProperties);

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
    Optional<GraphNode> mappingNode = graph.mappingNode();
    if (mappingNode.isEmpty()) {
      return Optional.empty();
    }
    Object rawConfig = mappingNode.get().data().get("mappingConfig");
    if (rawConfig == null) {
      return Optional.empty();
    }
    return Optional.of(mappingConfigParser.parse(mapper.valueToTree(rawConfig)));
  }

  private void requireUniformStrategy(List<UpdateRecordProperty> properties)
      throws FatalAdapterException {
    Set<ReplacementStrategy> strategies =
        properties.stream().map(UpdateRecordProperty::strategy).collect(Collectors.toSet());
    if (strategies.size() > 1) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_MAPPING_ERROR,
          "a single UpdateRecord cannot mix literal and record-path values");
    }
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
      putIfPresent(sourceProperties, "Broker URI", decrypted.get("brokerUrl"));
      putIfPresent(sourceProperties, "Topic Filter", decrypted.get("topic"));
      putIfPresent(sourceProperties, "Username", decrypted.get("username"));
      if (isEncrypted(original.get("password"))) {
        sensitive
            .computeIfAbsent(MQTT_PROCESSOR, k -> new LinkedHashMap<>())
            .put("Password", String.valueOf(decrypted.get("password")));
      }
    }
  }

  private void bindSink(
      SinkSpec sink,
      Map<String, String> sinkProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    switch (sink.type()) {
      case POSTGIS -> {
        putIfPresent(sinkProperties, "Table Name", sink.tableName());
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
