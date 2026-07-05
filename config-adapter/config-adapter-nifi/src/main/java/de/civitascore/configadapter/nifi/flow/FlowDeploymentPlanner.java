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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import de.civitascore.configadapter.nifi.flow.stage.SinkStage;
import de.civitascore.configadapter.nifi.flow.stage.SourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.flow.stage.TransformNodeType;
import de.civitascore.configadapter.nifi.flow.stage.TransformNodeType.Compilation;
import de.civitascore.configadapter.nifi.graph.FlowPath;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.graph.NodeKind;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.CompiledTransform;
import de.civitascore.configadapter.nifi.mapping.SinkPreRegionPlan;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns one resolved pipeline (graph + source + sink) into a {@link DeploymentPlan}: it derives the
 * flow path, has each transform node kind compile its own nodes against the sink, resolves the
 * source/sink processor properties, decrypts secrets (collected separately for a post-upload REST
 * push), and delegates the NiFi flow assembly to {@link NifiFlowBuilder} (programmatic composition
 * of building blocks per the graph).
 */
public class FlowDeploymentPlanner {

  private final GraphParser graphParser;
  private final NifiFlowBuilder flowBuilder;
  private final StageRegistry registry;

  /**
   * Creates a planner.
   *
   * @param graphParser the graph parser
   * @param flowBuilder the NiFi flow builder
   * @param registry the deployable source/sink stages and transform node kinds
   */
  public FlowDeploymentPlanner(
      GraphParser graphParser, NifiFlowBuilder flowBuilder, StageRegistry registry) {
    this.graphParser = graphParser;
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
      // deploys silently.
      path = FlowPath.derive(graphParser.parse(request.graphData()));
    } catch (IllegalStateException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    Optional<String> sourceCron = validatedTriggerCron(path);
    SinkSpec sink = request.sink();
    SinkStage<?> sinkStage = registry.sink(sink.type());
    Compilation chain = compileTransforms(path, sinkStage);

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
        && sink instanceof PostgisSinkSpec postgis
        && postgis.primaryKeyColumns().isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "a SQL source writing to PostGIS requires a primary key on the target data structure (mark"
              + " the identifying attribute with the UML {id} flag) so re-read rows are"
              + " de-duplicated; none was resolved");
    }

    PlanContext out = new PlanContext();
    sourceStage.bind(source, out);
    bindSink(sinkStage, sink, out);

    String processGroupName = "pipeline-" + request.pipelineId();
    String snapshot =
        flowBuilder.build(
            new FlowBuildSpec(
                processGroupName,
                sourceType,
                out.sourceProperties(),
                sink.type(),
                out.sinkProperties(),
                chain.units(),
                out.controllerServiceProperties(),
                sourceCron.orElse(null),
                chain.sinkPreRegion()));

    return new DeploymentPlan(processGroupName, snapshot, Map.copyOf(out.sensitive()));
  }

  /**
   * Has each transform node kind compile its own on-path nodes, then reassembles the units in flow
   * order (a kind compiles its nodes as one chain, but kinds may interleave on the path). At most
   * one kind produces a sink pre-region plan — today the mapping kind's STA envelope for a mapped
   * FROST sink.
   */
  private Compilation compileTransforms(FlowPath path, SinkStage sinkStage)
      throws FatalAdapterException {
    Map<NodeKind, List<GraphNode>> byKind = new LinkedHashMap<>();
    for (GraphNode node : path.transforms()) {
      byKind.computeIfAbsent(kindOf(node), k -> new ArrayList<>()).add(node);
    }
    Map<NodeKind, Iterator<CompiledTransform>> unitsByKind = new LinkedHashMap<>();
    SinkPreRegionPlan sinkPreRegion = null;
    for (Map.Entry<NodeKind, List<GraphNode>> entry : byKind.entrySet()) {
      TransformNodeType nodeType = registry.transformNodeType(entry.getKey());
      Compilation compilation = nodeType.compile(entry.getValue(), sinkStage);
      unitsByKind.put(entry.getKey(), compilation.units().iterator());
      if (compilation.sinkPreRegion() != null) {
        sinkPreRegion = compilation.sinkPreRegion();
      }
    }
    List<CompiledTransform> ordered = new ArrayList<>();
    for (GraphNode node : path.transforms()) {
      ordered.add(unitsByKind.get(kindOf(node)).next());
    }
    return new Compilation(ordered, sinkPreRegion);
  }

  /**
   * Binds the sink through its typed spec. The checked cast is safe by construction: the spec was
   * parsed by the same stage the registry resolves for its type.
   */
  private static <S extends SinkSpec> void bindSink(
      SinkStage<S> stage, SinkSpec spec, PlanContext out) throws FatalAdapterException {
    stage.bind(stage.specType().cast(spec), out);
  }

  /** The kind of an on-path transform node; the derivation already rejected unknown kinds. */
  private static NodeKind kindOf(GraphNode node) {
    return NodeKind.fromTypeString(node.type()).orElseThrow();
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
   * which dropped Quartz's optional 7th "year" field — so the check requires exactly 6 fields, not
   * Quartz's 6-or-7: a year-qualified expression would otherwise pass here and then fail inside
   * NiFi at deploy as an opaque saga failure. NiFi itself validates the field syntax; this only
   * guards the field count so an obviously malformed value fails the plan early rather than the
   * remote NiFi REST call. Mirrors the 6-field check in the editor's {@code
   * validationService.isValidNifiCron}, which additionally bounds the field values. Package-private
   * for unit testing.
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
