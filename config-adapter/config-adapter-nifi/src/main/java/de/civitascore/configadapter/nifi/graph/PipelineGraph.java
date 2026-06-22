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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The parsed engine-neutral pipeline graph carried in {@code dataPipelines[].data} — the React-Flow
 * editor graph. Besides the {@code mapping} transform node, the editor also emits source/sink nodes
 * ({@code dataSource}, {@code frost}, {@code geoPersistence}) and control nodes ({@code start},
 * {@code end}); the adapter consumes only the {@code mapping} node here (sources and sinks ride in
 * the trigger's top-level {@code datasources}/{@code datasinks} arrays), so it tolerates those node
 * kinds. A {@code cron} scheduling node is rejected, since the adapter cannot yet honor its
 * schedule.
 *
 * @param nodes the graph nodes
 * @param edges the graph edges
 */
public record PipelineGraph(List<GraphNode> nodes, List<GraphEdge> edges) {

  /** The node kind that carries a {@code mappingConfig}. */
  public static final String TYPE_MAPPING = "mapping";

  /** A scheduling trigger node — carries a {@code cronExpression} the adapter cannot yet honor. */
  private static final String TYPE_CRON = "cron";

  /**
   * Pure control node kinds (no data flows through them). The remaining kinds — {@code dataSource},
   * {@code frost}, {@code geoPersistence}, {@code mapping} and any future functional kind — must
   * all live in one connected component so the mapping is wired into the real source→sink flow.
   */
  private static final Set<String> CONTROL_TYPES = Set.of("start", "end");

  public PipelineGraph {
    nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
    edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
  }

  /**
   * A graph node.
   *
   * @param id the node id
   * @param type the node kind (e.g. {@code start}, {@code mapping}, {@code end})
   * @param data the node payload (e.g. {@code mappingConfig} for a mapping node)
   */
  public record GraphNode(String id, String type, Map<String, Object> data) {
    public GraphNode {
      data = data == null ? Map.of() : data;
    }
  }

  /**
   * A directed graph edge.
   *
   * @param id the edge id
   * @param source the source node id
   * @param target the target node id
   */
  public record GraphEdge(String id, String source, String target) {}

  /**
   * Returns the single transform ({@code mapping}) node, or empty for a pipeline with no transform.
   * The adapter consumes only this node from the graph and tolerates all node kinds, but it does
   * honor the edges: the mapping must be wired into the flow (an incoming AND an outgoing edge) and
   * must share one connected component with all other functional (non-control) nodes — so a mapping
   * sitting in a separate component from the pipeline's real source/sink nodes is rejected rather
   * than silently applied. More than one mapping node is also rejected (ambiguous transform).
   *
   * @return the mapping node, or empty if the pipeline has none
   * @throws IllegalStateException on multiple mapping nodes, an edge to an unknown node, or a
   *     mapping that is not wired into the single functional component
   */
  public Optional<GraphNode> transformNode() {
    // graph-integrity checks run for every graph (mapping or not) so a corrupt payload always
    // fails loud, never silently
    requireValidNodeIds();
    requireKnownEdgeEndpoints();
    rejectUnsupportedTriggers();
    List<GraphNode> mappings = nodes.stream().filter(n -> TYPE_MAPPING.equals(n.type())).toList();
    if (mappings.size() > 1) {
      throw new IllegalStateException(
          "pipeline graph has "
              + mappings.size()
              + " mapping nodes; exactly one transform is supported");
    }
    if (mappings.isEmpty()) {
      return Optional.empty();
    }
    GraphNode mapping = mappings.get(0);
    if (!isWired(mapping.id())) {
      throw new IllegalStateException(
          "the mapping node is not wired into the flow (it needs both an incoming and an outgoing"
              + " edge)");
    }
    if (!functionalNodesShareOneComponent(mapping.id())) {
      throw new IllegalStateException(
          "the mapping node is in a separate component from the pipeline's source/sink nodes");
    }
    return Optional.of(mapping);
  }

  /**
   * Rejects nodes with a missing/blank id or a duplicate id (a corrupt graph, not an NPE later).
   */
  private void requireValidNodeIds() {
    Set<String> seen = new HashSet<>();
    for (GraphNode node : nodes) {
      String id = node.id();
      if (id == null || id.isBlank()) {
        throw new IllegalStateException("pipeline graph has a node with a missing id");
      }
      if (!seen.add(id)) {
        throw new IllegalStateException("pipeline graph has a duplicate node id: " + id);
      }
    }
  }

  /**
   * Rejects scheduling triggers the adapter cannot yet honor. A {@code cron} node carries a {@code
   * cronExpression}, but the flow runs timer-driven at {@code 0 sec} — deploying would silently
   * ignore the schedule, so fail loudly until NiFi scheduling is wired.
   */
  private void rejectUnsupportedTriggers() {
    if (nodes.stream().anyMatch(n -> TYPE_CRON.equals(n.type()))) {
      throw new IllegalStateException("cron scheduling is not supported yet");
    }
  }

  /** Rejects any edge whose endpoints are not declared, non-null {@code nodes[].id} values. */
  private void requireKnownEdgeEndpoints() {
    Set<String> nodeIds = new HashSet<>();
    for (GraphNode node : nodes) {
      nodeIds.add(node.id());
    }
    for (GraphEdge edge : edges) {
      if (edge.source() == null
          || edge.target() == null
          || !nodeIds.contains(edge.source())
          || !nodeIds.contains(edge.target())) {
        throw new IllegalStateException(
            "pipeline graph edge references an unknown node: "
                + edge.source()
                + " -> "
                + edge.target());
      }
    }
  }

  /** Whether {@code nodeId} has at least one incoming and one outgoing edge. */
  private boolean isWired(String nodeId) {
    boolean incoming = false;
    boolean outgoing = false;
    for (GraphEdge edge : edges) {
      incoming |= nodeId.equals(edge.target());
      outgoing |= nodeId.equals(edge.source());
    }
    return incoming && outgoing;
  }

  /**
   * Whether every functional (non-control) node is reachable from {@code mappingId} through edges
   * that connect two functional nodes — i.e. they all lie in one undirected component.
   */
  private boolean functionalNodesShareOneComponent(String mappingId) {
    Set<String> functional = new HashSet<>();
    for (GraphNode node : nodes) {
      if (!CONTROL_TYPES.contains(node.type())) {
        functional.add(node.id());
      }
    }
    if (functional.size() <= 1) {
      return true;
    }
    Map<String, List<String>> undirected = new HashMap<>();
    for (GraphEdge edge : edges) {
      if (functional.contains(edge.source()) && functional.contains(edge.target())) {
        undirected.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge.target());
        undirected.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge.source());
      }
    }
    Deque<String> queue = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    queue.add(mappingId);
    seen.add(mappingId);
    while (!queue.isEmpty()) {
      for (String next : undirected.getOrDefault(queue.poll(), List.of())) {
        if (seen.add(next)) {
          queue.add(next);
        }
      }
    }
    return seen.containsAll(functional);
  }
}
