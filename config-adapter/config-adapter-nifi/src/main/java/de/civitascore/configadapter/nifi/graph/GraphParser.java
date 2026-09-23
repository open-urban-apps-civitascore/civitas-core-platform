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

/** Parses the CORE Pipeline {@code data} map of a pipeline entry into a {@link PipelineGraph}. */
public class GraphParser {

  /**
   * Parses a pipeline graph. A {@code null} or empty map yields an empty graph rather than failing
   * here — the {@link FlowPath} derivation rejects it with its own message (an empty graph names no
   * datasource), which is more actionable than a parse error.
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
    for (Object item : listEntries(raw, "nodes")) {
      if (!(item instanceof Map<?, ?> node)) {
        throw malformedEntry("nodes", item);
      }
      Map<String, Object> typed = (Map<String, Object>) node;
      nodes.add(
          new GraphNode(
              asString(typed.get("id")),
              asString(typed.get("kind")),
              asString(typed.get("sourceRef")),
              asString(typed.get("sinkRef")),
              asString(typed.get("mappingRef")),
              asString(typed.get("cronExpression"))));
    }
    return List.copyOf(nodes);
  }

  @SuppressWarnings("unchecked")
  private List<GraphEdge> parseEdges(Object raw) {
    List<GraphEdge> edges = new ArrayList<>();
    for (Object item : listEntries(raw, "edges")) {
      if (!(item instanceof Map<?, ?> edge)) {
        throw malformedEntry("edges", item);
      }
      Map<String, Object> typed = (Map<String, Object>) edge;
      edges.add(
          new GraphEdge(
              asString(typed.get("id")),
              asString(typed.get("source")),
              asString(typed.get("target"))));
    }
    return List.copyOf(edges);
  }

  /**
   * An absent member is a valid (empty) graph half, but a present-but-non-list value or a non-map
   * entry is a corrupt payload: skipping it would let the derivation misreport the defect as a
   * modelling problem ("no datasink node") instead of naming the corrupt field.
   */
  private static List<?> listEntries(Object raw, String member) {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      throw new IllegalStateException("pipeline graph '" + member + "' must be a list");
    }
    return list;
  }

  private static IllegalStateException malformedEntry(String member, Object item) {
    return new IllegalStateException(
        "pipeline graph '"
            + member
            + "' must contain only objects, got: "
            + (item == null ? "null" : item.getClass().getSimpleName()));
  }

  private static String asString(Object value) {
    return value instanceof String s ? s : null;
  }
}
