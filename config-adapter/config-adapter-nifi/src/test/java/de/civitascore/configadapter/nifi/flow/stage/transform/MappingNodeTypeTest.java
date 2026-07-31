/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkAuth;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkStage;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaProperties;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingNodeTypeTest {

  private final MappingNodeType mappingNodeType =
      new MappingNodeType(new MappingConfigParser(), new RecordPathCompiler());
  private final FrostSinkStage envelopeSink =
      new FrostSinkStage("https://frost.example/FROST", FrostSinkAuth.basicAuth("frost", "secret"));

  private static final String STRUCTURE_A =
      "urn:core:platform:civitas:datastructure:common:StructureA:0000000001:1.0.0";
  private static final String STRUCTURE_B =
      "urn:core:platform:civitas:datastructure:common:StructureB:0000000002:1.0.0";
  private static final String STRUCTURE_B_V2 =
      "urn:core:platform:civitas:datastructure:common:StructureB:0000000002:2.0.0";
  private static final String STRUCTURE_B_RENAMED =
      "urn:core:platform:civitas:datastructure:common:StructureBRenamed:0000000002:1.0.0";

  private static GraphNode mappingNode(String id, Map<String, Object> fields) {
    return new GraphNode(id, "mapping", Map.of("mappingConfig", Map.of("fields", fields)));
  }

  private static GraphNode mappingNode(
      String id, String source, String target, Map<String, Object> fields) {
    return new GraphNode(
        id,
        "mapping",
        Map.of("mappingConfig", Map.of("source", source, "target", target, "fields", fields)));
  }

  @Test
  void mappedFrostSinkWithoutTargetStructureIsRejected() {
    // The FROST compiler derives match keys from the mapping's target structure — a datasink
    // without it cannot deploy a mapped flow, only a passthrough.
    GraphNode mapping = mappingNode("m1", Map.of("$.properties.reference", "$.ref"));

    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                mappingNodeType.compile(
                    List.of(mapping), envelopeSink, new FrostSinkSpec("1", null)));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  @Test
  void mappingChainBeforeAFrostSinkCompilesIntermediatesAsRecordTransforms() throws Exception {
    // Earlier mappings of a chain are ordinary record transformations; only the last one compiles
    // against the FROST catalog.
    GraphNode first = mappingNode("m1", Map.of("$.stationName", "$.raw"));
    GraphNode last = mappingNode("m2", Map.of("$.properties.reference", "$.stationName"));

    var compilation =
        mappingNodeType.compile(
            List.of(first, last),
            envelopeSink,
            new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of())));

    assertEquals(2, compilation.units().size());
  }

  @Test
  void renamingTheStructureBetweenTwoNodesDeploysUnchanged() throws Exception {
    // The name segment is a display name; renaming a structure changes neither its identity nor its
    // shape. Comparing the URNs verbatim would fail a deploy that was correct before the rename,
    // and the remedy would be re-authoring both mappings for nothing.
    GraphNode first =
        mappingNode("m1", STRUCTURE_A, STRUCTURE_B_RENAMED, Map.of("$.name", "$.raw"));
    GraphNode last =
        mappingNode("m2", STRUCTURE_B, STRUCTURE_A, Map.of("$.properties.reference", "$.name"));

    var compilation =
        mappingNodeType.compile(
            List.of(first, last),
            envelopeSink,
            new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of())));

    assertEquals(2, compilation.units().size());
  }

  @Test
  void aFanOutWhoseKeyColumnsAllSitAboveTheArrayIsRejected() {
    // With a key the sink writes UPSERT, and PutDatabaseRecord batches each record as its own ON
    // CONFLICT DO UPDATE — so N elements sharing the parent's key overwrite each other down to one
    // row, last element winning, with no failure route and no bulletin.
    GraphNode mapping =
        mappingNode(
            "m1", Map.of("$.stationid", "$.stationid", "$.value", "$.measurements[].value"));

    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                mappingNodeType.compile(
                    List.of(mapping),
                    new PostgisSinkStage(null),
                    new PostgisSinkSpec("readings", List.of("stationid"))));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("every primary-key column"), ex.getMessage());
  }

  @Test
  void aFanOutWithOneElementLevelKeyColumnCompiles() throws Exception {
    // The counterpart: one key column read from below the array makes the rows distinct, which is
    // the shape the IT deploys.
    GraphNode mapping =
        mappingNode(
            "m1",
            Map.of(
                "$.stationid", "$.stationid",
                "$.measured_at", "$.measurements[].ts",
                "$.value", "$.measurements[].value"));

    var compilation =
        mappingNodeType.compile(
            List.of(mapping),
            new PostgisSinkStage(null),
            new PostgisSinkSpec("readings", List.of("stationid", "measured_at")));

    assertEquals(1, compilation.units().size());
  }

  private FatalAdapterException compileExpectingRejection(GraphNode... nodes) {
    return assertThrows(
        FatalAdapterException.class,
        () ->
            mappingNodeType.compile(
                List.of(nodes),
                envelopeSink,
                new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of()))));
  }

  @Test
  void aChainWhoseNeighboursDisagreeOnTheStructureBetweenThemIsRejected() {
    GraphNode first = mappingNode("m1", STRUCTURE_A, STRUCTURE_B, Map.of("$.name", "$.raw"));
    GraphNode last =
        mappingNode("m2", STRUCTURE_A, STRUCTURE_B, Map.of("$.properties.reference", "$.name"));

    FatalAdapterException ex = compileExpectingRejection(first, last);

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
    // Asserted on the message, not the code alone: four other compilers share NIFI_MAPPING_ERROR,
    // so the code by itself would stay green if this check were dropped entirely.
    assertTrue(ex.getMessage().contains("mapping node 'm2'"), ex.getMessage());
  }

  @Test
  void aNewStructureVersionBetweenTwoNodesIsRejected() {
    // A version bump is a different shape, so a comparison that ignored the version would pass.
    GraphNode first = mappingNode("m1", STRUCTURE_A, STRUCTURE_B_V2, Map.of("$.name", "$.raw"));
    GraphNode last =
        mappingNode("m2", STRUCTURE_B, STRUCTURE_A, Map.of("$.properties.reference", "$.name"));

    FatalAdapterException ex = compileExpectingRejection(first, last);

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("mapping node 'm2'"), ex.getMessage());
  }

  @Test
  void aBreakInTheSecondPairOfAThreeNodeChainIsRejected() {
    // The first pair matching must not end the walk, and the reported node must be the offending
    // one — a loop that only ever checks pair one stays green on every two-node fixture.
    GraphNode first = mappingNode("m1", STRUCTURE_A, STRUCTURE_B, Map.of("$.name", "$.raw"));
    GraphNode second = mappingNode("m2", STRUCTURE_B, STRUCTURE_A, Map.of("$.label", "$.name"));
    GraphNode third =
        mappingNode("m3", STRUCTURE_B, STRUCTURE_A, Map.of("$.properties.reference", "$.label"));

    FatalAdapterException ex = compileExpectingRejection(first, second, third);

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("mapping node 'm3'"), ex.getMessage());
  }

  @Test
  void aDownstreamNodeWithoutASourceStructureIsRejectedWhenTheUpstreamDeclaresOne() {
    // The editor writes both URNs or neither, so a half-declared pair is a corrupt payload, not a
    // legacy one — and its paths are as unverifiable as an outright mismatch.
    GraphNode first = mappingNode("m1", STRUCTURE_A, STRUCTURE_B, Map.of("$.name", "$.raw"));
    GraphNode last = mappingNode("m2", Map.of("$.properties.reference", "$.name"));

    FatalAdapterException ex = compileExpectingRejection(first, last);

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("no source structure"), ex.getMessage());
  }

  @Test
  void anUpstreamNodeWithoutATargetStructureIsRejectedWhenTheDownstreamDeclaresOne() {
    // The mirror case, which a single null-check on the downstream side alone would let through.
    GraphNode first = mappingNode("m1", Map.of("$.name", "$.raw"));
    GraphNode last =
        mappingNode("m2", STRUCTURE_B, STRUCTURE_A, Map.of("$.properties.reference", "$.name"));

    FatalAdapterException ex = compileExpectingRejection(first, last);

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("no target structure"), ex.getMessage());
  }

  @Test
  void aChainThatHandsOverTheSameStructureCompiles() throws Exception {
    GraphNode first = mappingNode("m1", STRUCTURE_A, STRUCTURE_B, Map.of("$.name", "$.raw"));
    GraphNode last =
        mappingNode("m2", STRUCTURE_B, STRUCTURE_A, Map.of("$.properties.reference", "$.name"));

    var compilation =
        mappingNodeType.compile(
            List.of(first, last),
            envelopeSink,
            new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of())));

    assertEquals(2, compilation.units().size());
  }

  @Test
  void aChainWithoutDeclaredStructuresStillCompiles() throws Exception {
    // The URNs are optional on a mapping, so a chain that declares none must keep deploying.
    GraphNode first = mappingNode("m1", Map.of("$.stationName", "$.raw"));
    GraphNode last = mappingNode("m2", Map.of("$.properties.reference", "$.stationName"));

    var compilation =
        mappingNodeType.compile(
            List.of(first, last),
            envelopeSink,
            new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of())));

    assertEquals(2, compilation.units().size());
  }
}
