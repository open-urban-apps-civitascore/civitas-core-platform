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

import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphEdge;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The derivation contract of {@link FlowPath}: every structural rule with its exact message — these
 * strings are the adapter half of the editor/adapter validation mirror, so tests pin them.
 */
class FlowPathTest {

  private static GraphNode node(String id, String type) {
    return new GraphNode(id, type, Map.of());
  }

  private static GraphNode cron(String id, String expression) {
    return new GraphNode(
        id, "cron", expression == null ? Map.of() : Map.of("cronExpression", expression));
  }

  private static GraphEdge edge(String from, String to) {
    return new GraphEdge("e-" + from + "-" + to, from, to);
  }

  private static PipelineGraph graph(List<GraphNode> nodes, GraphEdge... edges) {
    return new PipelineGraph(nodes, List.of(edges));
  }

  private static String derivationError(PipelineGraph graph) {
    return assertThrows(IllegalStateException.class, () -> FlowPath.derive(graph)).getMessage();
  }

  // ─── Happy paths ────────────────────────────────────────────────────────────

  @Test
  void derivesLinearPathWithoutMappingOrCron() {
    PipelineGraph graph =
        graph(
            List.of(
                node("start", "start"),
                node("src", "dataSource"),
                node("sink", "frost"),
                node("end", "end")),
            edge("start", "src"),
            edge("src", "sink"),
            edge("sink", "end"));

    FlowPath path = FlowPath.derive(graph);

    assertEquals("src", path.source().id());
    assertEquals("sink", path.sink().id());
    assertTrue(path.transforms().isEmpty());
    assertEquals(Optional.empty(), path.triggerCron());
  }

  @Test
  void derivesMappingChainInFlowOrder() {
    PipelineGraph graph =
        graph(
            List.of(
                node("src", "dataSource"),
                node("m2", "mapping"),
                node("m1", "mapping"),
                node("sink", "geoPersistence")),
            edge("src", "m1"),
            edge("m1", "m2"),
            edge("m2", "sink"));

    FlowPath path = FlowPath.derive(graph);

    assertEquals(List.of("m1", "m2"), path.transforms().stream().map(GraphNode::id).toList());
  }

  @Test
  void bindsTrimmedCronToSource() {
    PipelineGraph graph =
        graph(
            List.of(
                node("start", "start"),
                cron("c", " 0 0 6 * * ? "),
                node("src", "dataSource"),
                node("sink", "frost")),
            edge("start", "c"),
            edge("c", "src"),
            edge("src", "sink"));

    assertEquals(Optional.of("0 0 6 * * ?"), FlowPath.derive(graph).triggerCron());
  }

  @Test
  void toleratesLooseUnknownNode() {
    PipelineGraph graph =
        graph(
            List.of(node("src", "dataSource"), node("sink", "frost"), node("note", "annotation")),
            edge("src", "sink"));

    assertEquals("sink", FlowPath.derive(graph).sink().id());
  }

  // ─── Cardinality ────────────────────────────────────────────────────────────

  @Test
  void missingSourceIsRejected() {
    PipelineGraph graph = graph(List.of(node("sink", "frost")));

    assertEquals(
        "pipeline graph has no datasource node; exactly one wired datasource is required",
        derivationError(graph));
  }

  @Test
  void secondSourceIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(node("s1", "dataSource"), node("s2", "dataSource"), node("sink", "frost")),
            edge("s1", "sink"));

    assertEquals(
        "pipeline graph has 2 datasource nodes; exactly one is supported", derivationError(graph));
  }

  @Test
  void missingSinkIsRejected() {
    PipelineGraph graph = graph(List.of(node("src", "dataSource")));

    assertEquals(
        "pipeline graph has no datasink node; exactly one wired datasink is required",
        derivationError(graph));
  }

  @Test
  void secondSinkIsRejectedAcrossSinkKinds() {
    PipelineGraph graph =
        graph(
            List.of(node("src", "dataSource"), node("k1", "frost"), node("k2", "geoPersistence")),
            edge("src", "k1"));

    assertEquals(
        "pipeline graph has 2 datasink nodes; exactly one is supported", derivationError(graph));
  }

  // ─── Positions & connectivity ───────────────────────────────────────────────

  @Test
  void sourceWithIncomingDataEdgeIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(node("src", "dataSource"), node("m", "mapping"), node("sink", "frost")),
            edge("m", "src"),
            edge("src", "sink"));

    assertEquals(
        "pipeline graph wires dataSource node 'src' in an unsupported position",
        derivationError(graph));
  }

  @Test
  void sinkWithOutgoingDataEdgeIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(node("src", "dataSource"), node("sink", "frost"), node("m", "mapping")),
            edge("src", "sink"),
            edge("sink", "m"));

    assertEquals(
        "pipeline graph wires frost node 'sink' in an unsupported position",
        derivationError(graph));
  }

  @Test
  void branchingFlowIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(
                node("src", "dataSource"),
                node("m1", "mapping"),
                node("m2", "mapping"),
                node("sink", "frost")),
            edge("src", "m1"),
            edge("src", "m2"),
            edge("m1", "sink"),
            edge("m2", "sink"));

    assertEquals(
        "pipeline graph branches at dataSource node 'src'; a linear source-to-sink flow is"
            + " required",
        derivationError(graph));
  }

  @Test
  void deadEndBeforeSinkIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(
                node("src", "dataSource"),
                node("m", "mapping"),
                node("end", "end"),
                node("sink", "frost")),
            edge("src", "m"),
            edge("m", "end"));

    assertEquals(
        "pipeline graph has no data path from the datasource to the datasink",
        derivationError(graph));
  }

  @Test
  void cycleIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(
                node("src", "dataSource"),
                node("m1", "mapping"),
                node("m2", "mapping"),
                node("sink", "frost")),
            edge("src", "m1"),
            edge("m1", "m2"),
            edge("m2", "m1"));

    assertEquals("pipeline graph contains a cycle at mapping node 'm1'", derivationError(graph));
  }

  @Test
  void unknownNodeKindWiredIntoFlowIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(node("src", "dataSource"), node("x", "aggregate"), node("sink", "frost")),
            edge("src", "x"),
            edge("x", "sink"));

    assertEquals(
        "pipeline graph wires unsupported node type 'aggregate' (node 'x') into the data flow",
        derivationError(graph));
  }

  // ─── Mapping connectivity ───────────────────────────────────────────────────

  @Test
  void unwiredMappingIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(node("src", "dataSource"), node("sink", "frost"), node("m", "mapping")),
            edge("src", "sink"));

    assertEquals(
        "the mapping node is not wired into the flow (it needs both an incoming and an outgoing"
            + " edge)",
        derivationError(graph));
  }

  @Test
  void mappingWiredIntoSideComponentIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(
                node("start", "start"),
                node("src", "dataSource"),
                node("sink", "frost"),
                node("m", "mapping"),
                node("end", "end")),
            edge("src", "sink"),
            edge("start", "m"),
            edge("m", "end"));

    assertEquals(
        "the mapping node is in a separate component from the pipeline's source/sink nodes",
        derivationError(graph));
  }

  // ─── Trigger binding ────────────────────────────────────────────────────────

  @Test
  void cronFeedingANonSourceNodeIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(
                node("start", "start"),
                cron("c", "0 0 6 * * ?"),
                node("src", "dataSource"),
                node("m", "mapping"),
                node("sink", "frost")),
            edge("start", "c"),
            edge("c", "m"),
            edge("src", "m"),
            edge("m", "sink"));

    assertEquals(
        "pipeline graph wires cron node 'c' in an unsupported position", derivationError(graph));
  }

  @Test
  void secondCronOnTheSourceIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(
                node("start", "start"),
                cron("c1", "0 0 6 * * ?"),
                cron("c2", "0 0 7 * * ?"),
                node("src", "dataSource"),
                node("sink", "frost")),
            edge("start", "c1"),
            edge("start", "c2"),
            edge("c1", "src"),
            edge("c2", "src"),
            edge("src", "sink"));

    assertEquals(
        "datasource node 'src' has 2 cron triggers wired; at most one is supported",
        derivationError(graph));
  }

  @Test
  void cronWithoutExpressionIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(
                node("start", "start"),
                cron("c", null),
                node("src", "dataSource"),
                node("sink", "frost")),
            edge("start", "c"),
            edge("c", "src"),
            edge("src", "sink"));

    assertEquals("cron node has no cronExpression", derivationError(graph));
  }

  @Test
  void detachedCronIsRejected() {
    PipelineGraph graph =
        graph(
            List.of(cron("c", "0 0 6 * * ?"), node("src", "dataSource"), node("sink", "frost")),
            edge("src", "sink"));

    assertEquals(
        "the cron node is not wired into the flow (it needs both an incoming and an outgoing"
            + " edge)",
        derivationError(graph));
  }
}
