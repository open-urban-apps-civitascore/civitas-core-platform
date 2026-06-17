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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The parsed engine-neutral pipeline graph carried in {@code dataPipelines[].data} — the React-Flow
 * transformation flow ({@code start → … → end}). Sources and sinks are NOT nodes here; they ride in
 * the trigger's top-level {@code datasources}/{@code datasinks} arrays. The graph contributes the
 * transform, primarily the {@code mapping} node's inline {@code mappingConfig}.
 *
 * @param nodes the graph nodes
 * @param edges the graph edges
 */
public record PipelineGraph(List<GraphNode> nodes, List<GraphEdge> edges) {

  /** The node kind that carries a {@code mappingConfig}. */
  public static final String TYPE_MAPPING = "mapping";

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
   * Returns the single {@code mapping} node, if present. Provide-style pipelines without a
   * transform have none.
   *
   * @return the mapping node, or empty
   */
  public Optional<GraphNode> mappingNode() {
    return nodes.stream().filter(n -> TYPE_MAPPING.equals(n.type())).findFirst();
  }
}
