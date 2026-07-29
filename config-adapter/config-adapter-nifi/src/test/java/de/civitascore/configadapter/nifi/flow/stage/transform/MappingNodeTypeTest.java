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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkAuth;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkStage;
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
  void aChainWhoseNeighboursDisagreeOnTheStructureBetweenThemIsRejected() {
    // The second node was authored against the structure the first one no longer writes, so its
    // paths resolve against a shape the chain never produces: fields silently become NULL, and an
    // array selector makes the fan-out target an array the incoming record has lost, dropping every
    // record without a bulletin.
    GraphNode first = mappingNode("m1", STRUCTURE_A, STRUCTURE_B, Map.of("$.name", "$.raw"));
    GraphNode last =
        mappingNode("m2", STRUCTURE_A, STRUCTURE_B, Map.of("$.properties.reference", "$.name"));

    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                mappingNodeType.compile(
                    List.of(first, last),
                    envelopeSink,
                    new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of()))));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void aNewStructureVersionBetweenTwoNodesIsRejected() {
    // A version bump is a different shape, so matching everything but the version must not pass —
    // the stale node's paths are exactly as unanchored as against an unrelated structure.
    GraphNode first = mappingNode("m1", STRUCTURE_A, STRUCTURE_B_V2, Map.of("$.name", "$.raw"));
    GraphNode last =
        mappingNode("m2", STRUCTURE_B, STRUCTURE_A, Map.of("$.properties.reference", "$.name"));

    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                mappingNodeType.compile(
                    List.of(first, last),
                    envelopeSink,
                    new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of()))));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
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
    // The URNs are optional on a mapping, so a chain that declares none must keep deploying —
    // the check guards a stale declaration, it does not introduce a new requirement.
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
