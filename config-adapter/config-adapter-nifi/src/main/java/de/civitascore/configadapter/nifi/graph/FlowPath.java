/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.graph;

import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphEdge;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The linear data path derived from a {@link PipelineGraph}: exactly one datasource node, the
 * ordered mapping chain, exactly one datasink node, plus the (optional) cron trigger bound to the
 * source. Derivation walks the actual wiring — an edge between two functional nodes is data flow;
 * edges from/to the {@code start}/{@code end} control anchors carry no data; a {@code cron} edge is
 * a trigger binding, not data flow. Node existence on the canvas never decides anything, only
 * wiring does.
 *
 * <p>All structural constraints fail loud at derivation with node-anchored messages, mirroring the
 * editor's validation, so the saga/API path (which bypasses the editor) gets the same answer as the
 * user saw at edit time. The source/sink cardinality of exactly one each is a deliberate product
 * restriction, not a mechanical assumption — the walk itself handles any chain length.
 *
 * @param source the single {@code dataSource} node
 * @param mappings the {@code mapping} nodes in flow order (possibly empty)
 * @param sink the single sink node ({@code frost} or {@code geoPersistence})
 * @param triggerCron the trimmed cron expression of the trigger wired to the source, or empty
 */
public record FlowPath(
    GraphNode source, List<GraphNode> mappings, GraphNode sink, Optional<String> triggerCron) {

  /** The node kind carrying a datasource {@code entityId}. */
  public static final String TYPE_DATA_SOURCE = "dataSource";

  /** The sink node kinds, each carrying a datasink {@code entityId}. */
  public static final Set<String> SINK_TYPES = Set.of("frost", "geoPersistence");

  private static final String TYPE_CRON = "cron";
  private static final Set<String> CONTROL_TYPES = Set.of("start", "end");
  private static final String KEY_CRON_EXPRESSION = "cronExpression";

  public FlowPath {
    mappings = List.copyOf(mappings);
  }

  /**
   * Derives the flow path.
   *
   * @param graph the parsed pipeline graph
   * @return the derived path
   * @throws IllegalStateException if the graph violates a structural constraint (cardinality,
   *     position, connectivity, trigger binding); the message names the offending node
   */
  public static FlowPath derive(PipelineGraph graph) {
    Map<String, GraphNode> nodesById = new HashMap<>();
    for (GraphNode node : graph.nodes()) {
      nodesById.put(node.id(), node);
    }

    GraphNode source = requireExactlyOne(graph, TYPE_DATA_SOURCE::equals, "datasource");
    GraphNode sink = requireExactlyOne(graph, SINK_TYPES::contains, "datasink");
    rejectUnknownNodesInFlow(graph, nodesById);

    Map<String, List<GraphNode>> dataOut = dataEdges(graph, nodesById);
    requireTerminalPositions(graph, nodesById, source, sink, dataOut);

    List<GraphNode> mappings = walk(source, sink, dataOut);
    requireAllMappingsOnPath(graph, mappings);

    return new FlowPath(source, mappings, sink, triggerCron(graph, source));
  }

  // ─── Cardinality ────────────────────────────────────────────────────────────

  private static GraphNode requireExactlyOne(
      PipelineGraph graph, java.util.function.Predicate<String> typeMatch, String role) {
    List<GraphNode> matches =
        graph.nodes().stream().filter(n -> n.type() != null && typeMatch.test(n.type())).toList();
    if (matches.isEmpty()) {
      throw new IllegalStateException(
          "pipeline graph has no " + role + " node; exactly one wired " + role + " is required");
    }
    if (matches.size() > 1) {
      throw new IllegalStateException(
          "pipeline graph has " + matches.size() + " " + role + " nodes; exactly one is supported");
    }
    return matches.get(0);
  }

  /**
   * A node of a kind this adapter does not know, wired into the flow, must fail the deploy: walking
   * past it would silently deploy a different flow than the user modelled (e.g. a new editor node
   * kind released before adapter support). A loose unknown node on the canvas stays tolerated —
   * orphan hygiene is the editor's concern.
   */
  private static void rejectUnknownNodesInFlow(
      PipelineGraph graph, Map<String, GraphNode> nodesById) {
    for (GraphEdge edge : graph.edges()) {
      for (GraphNode node : List.of(nodesById.get(edge.source()), nodesById.get(edge.target()))) {
        if (!isFunctional(node)
            && !CONTROL_TYPES.contains(node.type())
            && !TYPE_CRON.equals(node.type())) {
          throw new IllegalStateException(
              "pipeline graph wires unsupported node type '"
                  + node.type()
                  + "' (node '"
                  + node.id()
                  + "') into the data flow");
        }
      }
    }
  }

  // ─── Data-flow topology ─────────────────────────────────────────────────────

  private static boolean isFunctional(GraphNode node) {
    String type = node.type();
    return TYPE_DATA_SOURCE.equals(type)
        || PipelineGraph.TYPE_MAPPING.equals(type)
        || (type != null && SINK_TYPES.contains(type));
  }

  /** Adjacency of the data edges only — edges whose both endpoints are functional nodes. */
  private static Map<String, List<GraphNode>> dataEdges(
      PipelineGraph graph, Map<String, GraphNode> nodesById) {
    Map<String, List<GraphNode>> out = new HashMap<>();
    for (GraphEdge edge : graph.edges()) {
      GraphNode from = nodesById.get(edge.source());
      GraphNode to = nodesById.get(edge.target());
      if (isFunctional(from) && isFunctional(to)) {
        out.computeIfAbsent(from.id(), k -> new ArrayList<>()).add(to);
      }
    }
    return out;
  }

  private static void requireTerminalPositions(
      PipelineGraph graph,
      Map<String, GraphNode> nodesById,
      GraphNode source,
      GraphNode sink,
      Map<String, List<GraphNode>> dataOut) {
    for (GraphEdge edge : graph.edges()) {
      GraphNode from = nodesById.get(edge.source());
      if (isFunctional(from) && edge.target().equals(source.id())) {
        throw unsupportedPosition(source);
      }
    }
    if (!dataOut.getOrDefault(sink.id(), List.of()).isEmpty()) {
      throw unsupportedPosition(sink);
    }
  }

  /** Walks the single data path source → mappings → sink and returns the mappings in order. */
  private static List<GraphNode> walk(
      GraphNode source, GraphNode sink, Map<String, List<GraphNode>> dataOut) {
    List<GraphNode> mappings = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    visited.add(source.id());
    GraphNode current = source;
    while (!current.id().equals(sink.id())) {
      List<GraphNode> next = dataOut.getOrDefault(current.id(), List.of());
      if (next.isEmpty()) {
        throw new IllegalStateException(
            "pipeline graph has no data path from the datasource to the datasink");
      }
      if (next.size() > 1) {
        throw new IllegalStateException(
            "pipeline graph branches at "
                + current.type()
                + " node '"
                + current.id()
                + "'; a linear source-to-sink flow is required");
      }
      GraphNode step = next.get(0);
      if (!visited.add(step.id())) {
        throw new IllegalStateException(
            "pipeline graph contains a cycle at " + step.type() + " node '" + step.id() + "'");
      }
      // The cardinality and position checks already ruled out every functional kind except
      // mapping and the sink itself, so no other kind can appear here.
      if (PipelineGraph.TYPE_MAPPING.equals(step.type())) {
        mappings.add(step);
      }
      current = step;
    }
    return mappings;
  }

  /**
   * Every mapping node must lie on the walked path — a mapping off the path would silently not be
   * applied. The message distinguishes an unwired mapping from one wired into a side component,
   * matching the pre-derivation checks these replace.
   */
  private static void requireAllMappingsOnPath(PipelineGraph graph, List<GraphNode> onPath) {
    Set<String> pathIds = new HashSet<>();
    for (GraphNode mapping : onPath) {
      pathIds.add(mapping.id());
    }
    for (GraphNode node : graph.nodes()) {
      if (!PipelineGraph.TYPE_MAPPING.equals(node.type()) || pathIds.contains(node.id())) {
        continue;
      }
      if (!isWired(graph, node.id())) {
        throw new IllegalStateException(
            "the mapping node is not wired into the flow (it needs both an incoming and an"
                + " outgoing edge)");
      }
      throw new IllegalStateException(
          "the mapping node is in a separate component from the pipeline's source/sink nodes");
    }
  }

  // ─── Trigger binding ────────────────────────────────────────────────────────

  /**
   * Resolves the cron trigger: every cron node must be wired, must feed the datasource node (a
   * trigger schedules the source's entry processor — feeding anything else is meaningless), and the
   * source's trigger port holds at most one schedule.
   */
  private static Optional<String> triggerCron(PipelineGraph graph, GraphNode source) {
    List<GraphNode> scheduling = new ArrayList<>();
    for (GraphNode cron : graph.nodes()) {
      if (!TYPE_CRON.equals(cron.type())) {
        continue;
      }
      requireCronFeedsSource(graph, cron, source);
      scheduling.add(cron);
    }
    if (scheduling.size() > 1) {
      throw new IllegalStateException(
          "datasource node '"
              + source.id()
              + "' has "
              + scheduling.size()
              + " cron triggers wired; at most one is supported");
    }
    if (scheduling.isEmpty()) {
      return Optional.empty();
    }
    Object expression = scheduling.get(0).data().get(KEY_CRON_EXPRESSION);
    if (!(expression instanceof String text) || text.isBlank()) {
      throw new IllegalStateException("cron node has no cronExpression");
    }
    return Optional.of(text.trim());
  }

  /** A cron must be wired and every outgoing edge must feed the datasource node. */
  private static void requireCronFeedsSource(
      PipelineGraph graph, GraphNode cron, GraphNode source) {
    // A detached cron must not silently schedule the source; reject it like the mapping check.
    if (!isWired(graph, cron.id())) {
      throw new IllegalStateException(
          "the cron node is not wired into the flow (it needs both an incoming and an outgoing"
              + " edge)");
    }
    for (GraphEdge edge : graph.edges()) {
      if (edge.source().equals(cron.id()) && !edge.target().equals(source.id())) {
        throw unsupportedPosition(cron);
      }
    }
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  /** Whether {@code nodeId} has at least one incoming and one outgoing edge of any kind. */
  private static boolean isWired(PipelineGraph graph, String nodeId) {
    boolean incoming = false;
    boolean outgoing = false;
    for (GraphEdge edge : graph.edges()) {
      incoming |= nodeId.equals(edge.target());
      outgoing |= nodeId.equals(edge.source());
    }
    return incoming && outgoing;
  }

  private static IllegalStateException unsupportedPosition(GraphNode node) {
    return new IllegalStateException(
        "pipeline graph wires "
            + node.type()
            + " node '"
            + node.id()
            + "' in an unsupported"
            + " position");
  }
}
