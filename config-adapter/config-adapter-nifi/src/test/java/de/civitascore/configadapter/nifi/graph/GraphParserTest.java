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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Parsing and payload-integrity contract of {@link GraphParser}/{@link PipelineGraph}. The flow
 * semantics derived from a parsed graph are covered by {@link FlowPathTest}.
 */
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
    assertTrue(graph.nodes().get(1).data().containsKey("mappingConfig"));
  }

  @Test
  void emptyGraphYieldsNoNodes() throws Exception {
    PipelineGraph graph = parser.parse(map("{}"));

    assertTrue(graph.nodes().isEmpty());
    assertTrue(graph.edges().isEmpty());
  }

  @Test
  void missingNodeIdIsRejectedCleanly() throws Exception {
    // a node without an id must fail with a clean error at construction, not an NPE deeper in the
    // wiring checks
    Map<String, Object> data =
        map(
            """
            { "nodes": [
                { "type": "dataSource", "data": {} },
                { "id": "n-map", "type": "mapping",
                  "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } } ],
              "edges": [] }
            """);

    assertThrows(IllegalStateException.class, () -> parser.parse(data));
  }

  @Test
  void duplicateNodeIdIsRejected() throws Exception {
    Map<String, Object> data =
        map(
            """
            { "nodes": [
                { "id": "n-1", "type": "dataSource", "data": {} },
                { "id": "n-1", "type": "mapping", "data": {} } ],
              "edges": [] }
            """);

    assertThrows(IllegalStateException.class, () -> parser.parse(data));
  }

  @Test
  void edgeToUnknownNodeIdIsRejected() throws Exception {
    // an edge endpoint that is not a declared node (e.g. a deleted node) must not be treated as a
    // valid terminal — rejected at construction
    Map<String, Object> data =
        map(
            """
            { "nodes": [
                { "id": "n-src", "type": "dataSource", "data": {} },
                { "id": "n-map", "type": "mapping",
                  "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } } ],
              "edges": [
                { "id": "e1", "source": "n-src", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "ghost-deleted" } ] }
            """);

    assertThrows(IllegalStateException.class, () -> parser.parse(data));
  }

  @Test
  void edgeToUnknownNodeIsRejectedEvenWithoutMapping() throws Exception {
    // graph-integrity checks apply to no-mapping graphs too — a dangling edge must fail loud at
    // construction, consistent with the mapping case
    Map<String, Object> data =
        map(
            """
            { "nodes": [ { "id": "n-src", "type": "dataSource", "data": {} } ],
              "edges": [ { "id": "e1", "source": "n-src", "target": "ghost-deleted" } ] }
            """);

    assertThrows(IllegalStateException.class, () -> parser.parse(data));
  }

  @Test
  void nullDataYieldsEmptyGraph() {
    PipelineGraph graph = parser.parse(null);

    assertTrue(graph.nodes().isEmpty());
  }
}
