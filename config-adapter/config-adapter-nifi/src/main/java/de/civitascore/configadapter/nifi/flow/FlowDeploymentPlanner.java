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
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.MappingSupport;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import de.civitascore.configadapter.nifi.flow.stage.SinkStage;
import de.civitascore.configadapter.nifi.flow.stage.SourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.graph.FlowPath;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.FrostEnvelopePlan;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.StaEnvelopeCompiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns one resolved pipeline (graph + source + sink) into a {@link DeploymentPlan}: it compiles
 * the mapping to RecordPath (directly for a records sink, or via the STA envelope compiler for a
 * mapped FROST sink), resolves the source/sink processor properties, decrypts secrets (collected
 * separately for a post-upload REST push), and delegates the NiFi flow assembly to {@link
 * NifiFlowBuilder} (programmatic composition of building blocks per the graph).
 */
public class FlowDeploymentPlanner {

  private final ObjectMapper mapper = new ObjectMapper();
  private final GraphParser graphParser;
  private final MappingConfigParser mappingConfigParser;
  private final RecordPathCompiler recordPathCompiler;
  private final StaEnvelopeCompiler staEnvelopeCompiler;
  private final NifiFlowBuilder flowBuilder;
  private final StageRegistry registry;

  /**
   * Creates a planner.
   *
   * @param graphParser the graph parser
   * @param mappingConfigParser the mapping parser
   * @param recordPathCompiler the RecordPath compiler
   * @param flowBuilder the NiFi flow builder
   * @param registry the deployable source/sink stages
   */
  public FlowDeploymentPlanner(
      GraphParser graphParser,
      MappingConfigParser mappingConfigParser,
      RecordPathCompiler recordPathCompiler,
      NifiFlowBuilder flowBuilder,
      StageRegistry registry) {
    this.graphParser = graphParser;
    this.mappingConfigParser = mappingConfigParser;
    this.recordPathCompiler = recordPathCompiler;
    this.staEnvelopeCompiler = new StaEnvelopeCompiler(recordPathCompiler);
    this.flowBuilder = flowBuilder;
    this.registry = registry;
  }

  /**
   * Plans the deployment of a single pipeline.
   *
   * @param request the resolved pipeline inputs
   * @return the deployment plan
   * @throws FatalAdapterException if the source/sink is unsupported or the mapping is invalid
   */
  public DeploymentPlan plan(PipelineDeploymentRequest request) throws FatalAdapterException {
    FlowPath path;
    try {
      // parse() rejects a corrupt payload (missing/duplicate node id, edge to an unknown node);
      // derive() rejects an unbuildable topology — both fail loud so a malformed graph never
      // deploys
      // silently.
      path = FlowPath.derive(graphParser.parse(request.graphData()));
    } catch (IllegalStateException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    List<MappingConfig> mappingConfigs = parseMappings(path);
    Optional<String> sourceCron = validatedTriggerCron(path);
    SinkSpec sink = request.sink();
    SinkStage sinkStage = registry.sink(sink.type());
    CompiledChain chain = compileChain(mappingConfigs, sinkStage);

    Datasource source = request.source();
    if (source == null) {
      throw template("<no source>");
    }
    SourceType sourceType =
        SourceType.fromRaw(source.getType()).orElseThrow(() -> template(source.getType()));
    SourceStage sourceStage = registry.source(sourceType);

    if (sourceCron.isPresent() && !sourceStage.acceptsSchedule()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, sourceStage.cronRejectionMessage());
    }

    // A SQL source ALWAYS re-reads the whole table on a recurring schedule — an explicit cron, or
    // the QueryDatabaseTableRecord fragment's built-in default (every 5 min) when no cron node is
    // present — and it tracks no max-value column. So without a sink primary key the PostGIS write
    // stays INSERT and every run duplicates all rows. Require a key for ANY SQL→PostGIS pipeline
    // (from x-core-primaryKey on the target, or an explicit configuration.primaryKey); this also
    // surfaces the case where a marker exists but the schema is too ambiguous to resolve one.
    if (sourceType == SourceType.SQL
        && sink.type() == SinkType.POSTGIS
        && sink.primaryKeyColumns().isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "a SQL source writing to PostGIS requires a primary key on the target data structure (mark"
              + " the identifying attribute with the UML {id} flag) so re-read rows are"
              + " de-duplicated; none was resolved");
    }

    PlanContext out = new PlanContext();
    sourceStage.bind(source, out);
    sinkStage.bind(request, out);

    String processGroupName = "pipeline-" + request.pipelineId();
    String snapshot =
        flowBuilder.build(
            new FlowBuildSpec(
                processGroupName,
                sourceType,
                out.sourceProperties(),
                sink.type(),
                out.sinkProperties(),
                chain.mappings(),
                out.controllerServiceProperties(),
                sourceCron.orElse(null),
                chain.staEnvelope()));

    return new DeploymentPlan(processGroupName, snapshot, Map.copyOf(out.sensitive()));
  }

  /** The compiled mapping chain plus the envelope rebuild plan (FROST sink only, else null). */
  private record CompiledChain(List<CompiledMapping> mappings, FrostEnvelopePlan staEnvelope) {}

  /**
   * Compiles the mapping chain against the sink's declaration. For an envelope sink the
   * intermediate mappings stay plain record transforms while the LAST mapping before the sink
   * carries the STA target paths and compiles into flat intermediate fields plus the envelope
   * rebuild plan the sink's build half turns into the split/capture/ReplaceText pre-region.
   */
  private CompiledChain compileChain(List<MappingConfig> mappingConfigs, SinkStage sinkStage)
      throws FatalAdapterException {
    if (mappingConfigs.isEmpty()) {
      return new CompiledChain(List.of(), null);
    }
    if (sinkStage.mappingSupport() == MappingSupport.NONE) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, sinkStage.mappingRejectionMessage());
    }
    List<CompiledMapping> mappings = new ArrayList<>();
    if (sinkStage.mappingSupport() == MappingSupport.ENVELOPE) {
      for (MappingConfig config : mappingConfigs.subList(0, mappingConfigs.size() - 1)) {
        mappings.add(
            new CompiledMapping(recordPathCompiler.compile(config, sinkStage.geometryEncoding())));
      }
      StaEnvelopeCompiler.EnvelopeCompilation compilation =
          staEnvelopeCompiler.compile(mappingConfigs.get(mappingConfigs.size() - 1));
      mappings.add(new CompiledMapping(compilation.flatProperties()));
      return new CompiledChain(mappings, compilation.plan());
    }
    for (MappingConfig config : mappingConfigs) {
      mappings.add(
          new CompiledMapping(recordPathCompiler.compile(config, sinkStage.geometryEncoding())));
    }
    return new CompiledChain(mappings, null);
  }

  /** Parses each on-path mapping node's config, in flow order. */
  private List<MappingConfig> parseMappings(FlowPath path) throws FatalAdapterException {
    List<MappingConfig> configs = new ArrayList<>();
    for (GraphNode node : path.transforms()) {
      Object rawConfig = node.data().get("mappingConfig");
      if (rawConfig == null) {
        // A wired mapping node must carry a config; a missing one is a corrupted payload that
        // would otherwise deploy untransformed. (A pipeline with no mapping node at all is fine.)
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR, "mapping node has no mappingConfig");
      }
      configs.add(mappingConfigParser.parse(mapper.valueToTree(rawConfig)));
    }
    return configs;
  }

  private Optional<String> validatedTriggerCron(FlowPath path) throws FatalAdapterException {
    Optional<String> cron = path.triggerCron();
    if (cron.isPresent() && !isValidNifiCron(cron.get())) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "invalid NiFi cron expression: " + cron.get());
    }
    return cron;
  }

  /**
   * Whether {@code expression} is a NiFi cron — exactly 6 whitespace-separated fields ({@code sec
   * min hour day-of-month month day-of-week}). NiFi 2.x replaced Quartz with Spring's cron parser,
   * which dropped Quartz's optional 7th "year" field; a year-qualified expression passes a 7-field
   * count here but is then rejected inside NiFi at deploy as an opaque saga failure. NiFi itself
   * validates the field syntax; this only guards the field count so an obviously malformed value
   * fails the plan early rather than the remote NiFi REST call. Mirrors the 6-field check in the
   * editor's {@code validationService.isValidQuartzCron}. Package-private for unit testing.
   */
  static boolean isValidNifiCron(String expression) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    return expression.trim().split("\\s+").length == 6;
  }

  private static FatalAdapterException template(String combination) {
    return new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, combination);
  }
}
