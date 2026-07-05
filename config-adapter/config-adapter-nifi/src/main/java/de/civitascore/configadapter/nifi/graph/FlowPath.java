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

import de.civitascore.configadapter.nifi.graph.NodeKind.Role;
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
 * The linear data path derived from a {@link PipelineGraph}: exactly one {@link Role#SOURCE} node,
 * the ordered on-path {@link Role#TRANSFORM} nodes, exactly one {@link Role#SINK} node, plus the
 * (optional) cron trigger bound to the source. Derivation dispatches on {@link NodeKind} roles,
 * never on raw type strings, and walks the actual wiring — an edge between two functional nodes is
 * data flow; edges from/to the {@link Role#CONTROL} anchors carry no data; a {@link Role#TRIGGER}
 * edge is a trigger binding, not data flow. Node existence on the canvas never decides anything,
 * only wiring does.
 *
 * <p>All structural constraints fail loud at derivation with node-anchored messages, mirroring the
 * editor's validation, so the saga/API path (which bypasses the editor) gets the same answer as the
 * user saw at edit time. The source/sink cardinality of exactly one each is a deliberate product
 * restriction, not a mechanical assumption — the walk itself handles any chain length, any
 * transform kind, and any number of transform instances.
 *
 * @param source the single {@link Role#SOURCE} node
 * @param transforms the on-path {@link Role#TRANSFORM} nodes in flow order (possibly empty)
 * @param sink the single {@link Role#SINK} node
 * @param triggerCron the trimmed cron expression of the trigger wired to the source, or empty
 */
public record FlowPath(
    GraphNode source, List<GraphNode> transforms, GraphNode sink, Optional<String> triggerCron) {

  private static final String KEY_CRON_EXPRESSION = "cronExpression";

  public FlowPath {
    transforms = List.copyOf(transforms);
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

    GraphNode source = requireExactlyOne(graph, Role.SOURCE, "datasource");
    GraphNode sink = requireExactlyOne(graph, Role.SINK, "datasink");
    rejectUnknownNodesInFlow(graph, nodesById);

    Map<String, List<GraphNode>> dataOut = dataEdges(graph, nodesById);
    requireTerminalPositions(graph, nodesById, source, sink, dataOut);

    List<GraphNode> transforms = walk(source, sink, dataOut);
    requireAllTransformsOnPath(graph, transforms);

    return new FlowPath(source, transforms, sink, triggerCron(graph, source));
  }

  // ─── Cardinality ────────────────────────────────────────────────────────────

  private static GraphNode requireExactlyOne(PipelineGraph graph, Role role, String label) {
    List<GraphNode> matches = graph.nodes().stream().filter(n -> hasRole(n, role)).toList();
    if (matches.isEmpty()) {
      throw new IllegalStateException(
          "pipeline graph has no " + label + " node; exactly one wired " + label + " is required");
    }
    if (matches.size() > 1) {
      throw new IllegalStateException(
          "pipeline graph has "
              + matches.size()
              + " "
              + label
              + " nodes; exactly one is supported");
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
        if (NodeKind.roleOf(node).isEmpty()) {
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

  private static boolean hasRole(GraphNode node, Role role) {
    return NodeKind.roleOf(node).filter(role::equals).isPresent();
  }

  /** Whether the node carries data: it emits, transforms, or consumes the flow. */
  private static boolean isFunctional(GraphNode node) {
    return NodeKind.roleOf(node)
        .filter(r -> r == Role.SOURCE || r == Role.TRANSFORM || r == Role.SINK)
        .isPresent();
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

  /** Walks the single data path source → transforms → sink and returns the transforms in order. */
  private static List<GraphNode> walk(
      GraphNode source, GraphNode sink, Map<String, List<GraphNode>> dataOut) {
    List<GraphNode> transforms = new ArrayList<>();
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
      if (hasRole(step, Role.TRANSFORM)) {
        transforms.add(step);
      }
      current = step;
    }
    return transforms;
  }

  /**
   * Every transform node must lie on the walked path — a transform off the path would silently not
   * be applied. The message distinguishes an unwired node from one wired into a side component,
   * matching the pre-derivation checks these replace.
   */
  private static void requireAllTransformsOnPath(PipelineGraph graph, List<GraphNode> onPath) {
    Set<String> pathIds = new HashSet<>();
    for (GraphNode transform : onPath) {
      pathIds.add(transform.id());
    }
    for (GraphNode node : graph.nodes()) {
      if (!hasRole(node, Role.TRANSFORM) || pathIds.contains(node.id())) {
        continue;
      }
      if (!isWired(graph, node.id())) {
        throw new IllegalStateException(
            "the "
                + node.type()
                + " node is not wired into the flow (it needs both an incoming and an"
                + " outgoing edge)");
      }
      throw new IllegalStateException(
          "the "
              + node.type()
              + " node is in a separate component from the pipeline's source/sink nodes");
    }
  }

  // ─── Trigger binding ────────────────────────────────────────────────────────

  /**
   * Resolves the cron trigger: every trigger node must be wired, must feed the datasource node (a
   * trigger schedules the source's entry processor — feeding anything else is meaningless), and the
   * source's trigger port holds at most one schedule.
   */
  private static Optional<String> triggerCron(PipelineGraph graph, GraphNode source) {
    List<GraphNode> scheduling = new ArrayList<>();
    for (GraphNode cron : graph.nodes()) {
      if (!hasRole(cron, Role.TRIGGER)) {
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
    // A detached cron must not silently schedule the source; reject it like the transform check.
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
