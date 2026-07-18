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

  private static final String CORE_GRAPH =
      """
      {
        "nodes": [
          { "id": "n-start", "kind": "start" },
          { "id": "n-map", "kind": "mapping",
            "mappingRef": "urn:core:dataset:d:mapping:c:M:0000000001:1.0.0" },
          { "id": "n-end", "kind": "end" }
        ],
        "edges": [
          { "id": "e1", "source": "n-start", "target": "n-map" },
          { "id": "e2", "source": "n-map", "target": "n-end" }
        ]
      }
      """;

  @Test
  void parsesNodesAndEdges() throws Exception {
    PipelineGraph graph = parser.parse(map(CORE_GRAPH));

    assertEquals(3, graph.nodes().size());
    assertEquals(2, graph.edges().size());
    assertEquals("mapping", graph.nodes().get(1).kind());
    assertEquals(
        "urn:core:dataset:d:mapping:c:M:0000000001:1.0.0", graph.nodes().get(1).mappingRef());
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
                { "kind": "source" },
                { "id": "n-map", "kind": "mapping",
                  "mappingRef": "urn:core:dataset:d:mapping:c:M:0000000001:1.0.0" } ],
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
                { "id": "n-1", "kind": "source" },
                { "id": "n-1", "kind": "mapping" } ],
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
                { "id": "n-src", "kind": "source" },
                { "id": "n-map", "kind": "mapping",
                  "mappingRef": "urn:core:dataset:d:mapping:c:M:0000000001:1.0.0" } ],
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
            { "nodes": [ { "id": "n-src", "kind": "source" } ],
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
