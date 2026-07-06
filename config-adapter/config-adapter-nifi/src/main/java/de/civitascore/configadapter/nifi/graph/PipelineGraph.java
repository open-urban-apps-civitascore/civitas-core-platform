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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The parsed engine-neutral pipeline graph carried in {@code dataPipelines[].data} — the React-Flow
 * editor graph, the authoritative description of the pipeline's data flow. Construction enforces
 * only payload integrity (node ids, edge endpoints); the flow semantics — source/sink/transform
 * extraction, positions, trigger binding — are derived by {@link FlowPath} over the {@link
 * NodeKind} vocabulary.
 *
 * @param nodes the graph nodes
 * @param edges the graph edges
 */
public record PipelineGraph(List<GraphNode> nodes, List<GraphEdge> edges) {

  public PipelineGraph {
    nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
    edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
    // Graph-integrity checks run at construction so a corrupt payload always fails loud, regardless
    // of what a caller later derives from the graph.
    requireValidNodeIds(nodes);
    requireKnownEdgeEndpoints(nodes, edges);
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
      // Not Map.copyOf: a JSON node payload may carry null values. The unmodifiable copy keeps
      // the record's value semantics real instead of aliasing the parser's mutable map.
      data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
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
   * Rejects nodes with a missing/blank id or a duplicate id (a corrupt graph, not an NPE later).
   */
  private static void requireValidNodeIds(List<GraphNode> nodes) {
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

  /** Rejects any edge whose endpoints are not declared, non-null {@code nodes[].id} values. */
  private static void requireKnownEdgeEndpoints(List<GraphNode> nodes, List<GraphEdge> edges) {
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
}
