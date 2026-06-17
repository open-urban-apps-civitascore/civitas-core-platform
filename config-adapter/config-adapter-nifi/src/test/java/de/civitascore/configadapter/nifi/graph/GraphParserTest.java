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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphParserTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final GraphParser parser = new GraphParser();

  private Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private static final String WI1513_GRAPH =
      """
      {
        "viewport": { "x": 0, "y": 0, "zoom": 1 },
        "nodes": [
          { "id": "n-start", "type": "start", "data": { "nodeType": "start" } },
          { "id": "n-map", "type": "mapping",
            "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } },
          { "id": "n-end", "type": "end", "data": { "nodeType": "end" } }
        ],
        "edges": [
          { "id": "e1", "source": "n-start", "target": "n-map" },
          { "id": "e2", "source": "n-map", "target": "n-end" }
        ]
      }
      """;

  @Test
  void parsesNodesAndEdges() throws Exception {
    PipelineGraph graph = parser.parse(map(WI1513_GRAPH));

    assertEquals(3, graph.nodes().size());
    assertEquals(2, graph.edges().size());
  }

  @Test
  void findsMappingNode() throws Exception {
    PipelineGraph graph = parser.parse(map(WI1513_GRAPH));

    Optional<GraphNode> mapping = graph.mappingNode();
    assertTrue(mapping.isPresent());
    assertEquals("n-map", mapping.get().id());
    assertTrue(mapping.get().data().containsKey("mappingConfig"));
  }

  @Test
  void provideStyleWithoutMappingNodeDoesNotThrow() throws Exception {
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [ { "id": "n-start", "type": "start", "data": {} } ], "edges": [] }
                """));

    assertFalse(graph.mappingNode().isPresent());
    assertEquals(1, graph.nodes().size());
  }

  @Test
  void emptyGraphYieldsNoNodes() throws Exception {
    PipelineGraph graph = parser.parse(map("{}"));

    assertTrue(graph.nodes().isEmpty());
    assertTrue(graph.edges().isEmpty());
    assertFalse(graph.mappingNode().isPresent());
  }

  @Test
  void nullDataYieldsEmptyGraph() {
    PipelineGraph graph = parser.parse(null);

    assertTrue(graph.nodes().isEmpty());
  }
}
