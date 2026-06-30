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
 * kinds. A {@code cron} scheduling node carries the {@code cronExpression} that drives the source
 * processor's schedule (see {@link #triggerCron()}).
 *
 * @param nodes the graph nodes
 * @param edges the graph edges
 */
public record PipelineGraph(List<GraphNode> nodes, List<GraphEdge> edges) {

  /** The node kind that carries a {@code mappingConfig}. */
  public static final String TYPE_MAPPING = "mapping";

  /** A scheduling trigger node — carries the {@code cronExpression} that schedules the source. */
  private static final String TYPE_CRON = "cron";

  /** The node-data key holding a {@code cron} node's schedule. */
  private static final String KEY_CRON_EXPRESSION = "cronExpression";

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
   * The schedule of the (optional, single) {@code cron} trigger node, used to drive the source
   * processor's NiFi schedule. A pipeline with no cron node returns empty (the source keeps its
   * built-in schedule). More than one cron node is rejected (ambiguous schedule), as is a cron node
   * with a missing/blank {@code cronExpression} (a corrupt payload that would otherwise deploy
   * unscheduled).
   *
   * @return the cron expression, or empty if the pipeline has no cron node
   * @throws IllegalStateException on multiple cron nodes or a blank/missing cron expression
   */
  public Optional<String> triggerCron() {
    List<GraphNode> crons = nodes.stream().filter(n -> TYPE_CRON.equals(n.type())).toList();
    if (crons.size() > 1) {
      throw new IllegalStateException(
          "pipeline graph has " + crons.size() + " cron nodes; at most one is supported");
    }
    if (crons.isEmpty()) {
      return Optional.empty();
    }
    GraphNode cron = crons.get(0);
    // A cron node only schedules the source when it is actually wired into the flow. A detached
    // (unwired) cron must not silently schedule the source — or make an MQTT plan fail with "cron
    // not supported" — so reject it, mirroring the mapping connectivity check.
    if (!isWired(cron.id())) {
      throw new IllegalStateException(
          "the cron node is not wired into the flow (it needs both an incoming and an outgoing"
              + " edge)");
    }
    Object expression = cron.data().get(KEY_CRON_EXPRESSION);
    if (!(expression instanceof String text) || text.isBlank()) {
      throw new IllegalStateException("cron node has no cronExpression");
    }
    return Optional.of(text.trim());
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
