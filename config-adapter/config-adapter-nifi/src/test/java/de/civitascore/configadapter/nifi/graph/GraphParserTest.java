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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  void findsMappingNodeWiredBetweenStartAndEnd() throws Exception {
    PipelineGraph graph = parser.parse(map(WI1513_GRAPH));

    Optional<GraphNode> mapping = graph.transformNode();
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

    assertFalse(graph.transformNode().isPresent());
    assertEquals(1, graph.nodes().size());
  }

  @Test
  void emptyGraphYieldsNoNodes() throws Exception {
    PipelineGraph graph = parser.parse(map("{}"));

    assertTrue(graph.nodes().isEmpty());
    assertTrue(graph.edges().isEmpty());
    assertFalse(graph.transformNode().isPresent());
  }

  @Test
  void toleratesRealFrontendNodeTypesAndFindsMapping() throws Exception {
    // the editor emits dataSource/frost/geoPersistence nodes too; the adapter consumes only the
    // mapping node and must tolerate the rest rather than reject the graph
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "id": "n-src", "type": "dataSource", "data": {} },
                    { "id": "n-map", "type": "mapping",
                      "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } },
                    { "id": "n-geo", "type": "geoPersistence", "data": {} } ],
                  "edges": [
                    { "id": "e1", "source": "n-src", "target": "n-map" },
                    { "id": "e2", "source": "n-map", "target": "n-geo" } ] }
                """));

    Optional<GraphNode> mapping = graph.transformNode();
    assertTrue(mapping.isPresent());
    assertEquals("n-map", mapping.get().id());
  }

  @Test
  void cronTriggerNodeIsRejected() throws Exception {
    // a cron node carries a schedule the adapter cannot honor yet — reject rather than deploy a
    // pipeline that silently ignores the cron expression
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "id": "n-cron", "type": "cron", "data": { "cronExpression": "0 0 * * *" } },
                    { "id": "n-src", "type": "dataSource", "data": {} },
                    { "id": "n-map", "type": "mapping",
                      "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } },
                    { "id": "n-frost", "type": "frost", "data": {} } ],
                  "edges": [
                    { "id": "e1", "source": "n-src", "target": "n-map" },
                    { "id": "e2", "source": "n-map", "target": "n-frost" } ] }
                """));

    assertThrows(IllegalStateException.class, graph::transformNode);
  }

  @Test
  void missingNodeIdIsRejectedCleanly() throws Exception {
    // a node without an id must fail with a clean error, not an NPE deeper in the wiring checks
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "type": "dataSource", "data": {} },
                    { "id": "n-map", "type": "mapping",
                      "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } } ],
                  "edges": [] }
                """));

    assertThrows(IllegalStateException.class, graph::transformNode);
  }

  @Test
  void multipleMappingNodesAreRejected() throws Exception {
    // the adapter builds a single transform; two mapping nodes are ambiguous and must fail loudly
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "id": "n-map1", "type": "mapping", "data": { "mappingConfig": {} } },
                    { "id": "n-map2", "type": "mapping", "data": { "mappingConfig": {} } } ],
                  "edges": [] }
                """));

    assertThrows(IllegalStateException.class, graph::transformNode);
  }

  @Test
  void disconnectedMappingNodeIsRejected() throws Exception {
    // a mapping node not wired between a source and a sink must not be silently applied
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "id": "n-src", "type": "dataSource", "data": {} },
                    { "id": "n-frost", "type": "frost", "data": {} },
                    { "id": "n-map", "type": "mapping",
                      "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } } ],
                  "edges": [ { "id": "e1", "source": "n-src", "target": "n-frost" } ] }
                """));

    assertThrows(IllegalStateException.class, graph::transformNode);
  }

  @Test
  void mappingMissingOutgoingEdgeIsRejected() throws Exception {
    // wired in but not out (dangling) is also not a valid source→sink path
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "id": "n-src", "type": "dataSource", "data": {} },
                    { "id": "n-map", "type": "mapping",
                      "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } } ],
                  "edges": [ { "id": "e1", "source": "n-src", "target": "n-map" } ] }
                """));

    assertThrows(IllegalStateException.class, graph::transformNode);
  }

  @Test
  void mappingInSeparateComponentFromSourceSinkIsRejected() throws Exception {
    // dataSource→frost is one component; the mapping is wired only between control nodes in a
    // SECOND component — it is not actually in the data flow, so it must be rejected
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "id": "n-src", "type": "dataSource", "data": {} },
                    { "id": "n-frost", "type": "frost", "data": {} },
                    { "id": "n-start", "type": "start", "data": {} },
                    { "id": "n-map", "type": "mapping",
                      "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } },
                    { "id": "n-end", "type": "end", "data": {} } ],
                  "edges": [
                    { "id": "e1", "source": "n-src", "target": "n-frost" },
                    { "id": "e2", "source": "n-start", "target": "n-map" },
                    { "id": "e3", "source": "n-map", "target": "n-end" } ] }
                """));

    assertThrows(IllegalStateException.class, graph::transformNode);
  }

  @Test
  void edgeToUnknownNodeIdIsRejected() throws Exception {
    // an edge endpoint that is not a declared node (e.g. a deleted node) must not be treated as a
    // valid terminal
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "id": "n-src", "type": "dataSource", "data": {} },
                    { "id": "n-map", "type": "mapping",
                      "data": { "mappingConfig": { "fields": { "$.a": "$.b" } } } } ],
                  "edges": [
                    { "id": "e1", "source": "n-src", "target": "n-map" },
                    { "id": "e2", "source": "n-map", "target": "ghost-deleted" } ] }
                """));

    assertThrows(IllegalStateException.class, graph::transformNode);
  }

  @Test
  void edgeToUnknownNodeIsRejectedEvenWithoutMapping() throws Exception {
    // graph-integrity checks apply to provide-style/no-mapping graphs too — a dangling edge must
    // fail loud, consistent with the mapping case
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [ { "id": "n-src", "type": "dataSource", "data": {} } ],
                  "edges": [ { "id": "e1", "source": "n-src", "target": "ghost-deleted" } ] }
                """));

    assertThrows(IllegalStateException.class, graph::transformNode);
  }

  @Test
  void linearGraphWithoutMappingYieldsNoTransform() throws Exception {
    PipelineGraph graph =
        parser.parse(
            map(
                """
                { "nodes": [
                    { "id": "n-start", "type": "start", "data": {} },
                    { "id": "n-end", "type": "end", "data": {} } ],
                  "edges": [ { "id": "e1", "source": "n-start", "target": "n-end" } ] }
                """));

    assertFalse(graph.transformNode().isPresent());
  }

  @Test
  void nullDataYieldsEmptyGraph() {
    PipelineGraph graph = parser.parse(null);

    assertTrue(graph.nodes().isEmpty());
  }
}
