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
import java.util.List;
import java.util.Map;

/** Parses the raw {@code data} map of a pipeline entry into a {@link PipelineGraph}. */
public class GraphParser {

  /**
   * Parses a pipeline graph. A {@code null} or empty map yields an empty graph rather than failing,
   * so provide-style or malformed entries are handled gracefully by the caller.
   *
   * @param data the raw graph map ({@code viewport}/{@code nodes}/{@code edges})
   * @return the parsed graph
   */
  public PipelineGraph parse(Map<String, Object> data) {
    if (data == null) {
      return new PipelineGraph(List.of(), List.of());
    }
    return new PipelineGraph(parseNodes(data.get("nodes")), parseEdges(data.get("edges")));
  }

  @SuppressWarnings("unchecked")
  private List<GraphNode> parseNodes(Object raw) {
    List<GraphNode> nodes = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> node) {
          Map<String, Object> typed = (Map<String, Object>) node;
          nodes.add(
              new GraphNode(
                  asString(typed.get("id")),
                  asString(typed.get("type")),
                  nodeData(typed.get("data"))));
        }
      }
    }
    return List.copyOf(nodes);
  }

  @SuppressWarnings("unchecked")
  private List<GraphEdge> parseEdges(Object raw) {
    List<GraphEdge> edges = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> edge) {
          Map<String, Object> typed = (Map<String, Object>) edge;
          edges.add(
              new GraphEdge(
                  asString(typed.get("id")),
                  asString(typed.get("source")),
                  asString(typed.get("target"))));
        }
      }
    }
    return List.copyOf(edges);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> nodeData(Object raw) {
    return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private static String asString(Object value) {
    return value instanceof String s ? s : null;
  }
}
