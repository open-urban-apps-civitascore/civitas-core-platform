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

  private static final String REF_1 = "urn:core:dataset:d:mapping:c:M1:0000000001:1.0.0";
  private static final String REF_2 = "urn:core:dataset:d:mapping:c:M2:0000000002:1.0.0";

  private static GraphNode mappingNode(String id, String mappingRef) {
    return new GraphNode(id, "mapping", null, null, mappingRef, null);
  }

  /** A shipped mapping document ({@code fields} only) for the catalog. */
  private static Map<String, Object> mappingDoc(Map<String, Object> fields) {
    return Map.of("fields", fields);
  }

  @Test
  void mappedFrostSinkWithoutTargetStructureIsRejected() {
    // The FROST compiler derives match keys from the mapping's target structure — a datasink
    // without it cannot deploy a mapped flow, only a passthrough.
    GraphNode mapping = mappingNode("m1", REF_1);
    Map<String, Object> mappings =
        Map.of(REF_1, mappingDoc(Map.of("$.properties.reference", "$.ref")));

    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                mappingNodeType.compile(
                    List.of(mapping), envelopeSink, new FrostSinkSpec("1", null), mappings));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  @Test
  void mappingChainBeforeAFrostSinkCompilesIntermediatesAsRecordTransforms() throws Exception {
    // Earlier mappings of a chain are ordinary record transformations; only the last one compiles
    // against the FROST catalog.
    GraphNode first = mappingNode("m1", REF_1);
    GraphNode last = mappingNode("m2", REF_2);
    Map<String, Object> mappings =
        Map.of(
            REF_1, mappingDoc(Map.of("$.stationName", "$.raw")),
            REF_2, mappingDoc(Map.of("$.properties.reference", "$.stationName")));

    var compilation =
        mappingNodeType.compile(
            List.of(first, last),
            envelopeSink,
            new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of())),
            mappings);

    assertEquals(2, compilation.units().size());
  }

  @Test
  void mappingNodeWithoutRefIsRejected() {
    // A wired mapping node that carries no mappingRef is a corrupted payload — it must not deploy
    // untransformed.
    GraphNode mapping = mappingNode("m1", null);

    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                mappingNodeType.compile(
                    List.of(mapping),
                    envelopeSink,
                    new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of())),
                    Map.of()));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  @Test
  void mappingRefNotShippedInCatalogIsRejected() {
    // The config-adapter is callback-free — a mappingRef whose document did not travel in the
    // pipeline's mappings catalog cannot be resolved and must fail the deploy.
    GraphNode mapping = mappingNode("m1", REF_1);

    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                mappingNodeType.compile(
                    List.of(mapping),
                    envelopeSink,
                    new FrostSinkSpec("1", StaProperties.ofKeys(List.of("reference"), List.of())),
                    Map.of()));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }
}
