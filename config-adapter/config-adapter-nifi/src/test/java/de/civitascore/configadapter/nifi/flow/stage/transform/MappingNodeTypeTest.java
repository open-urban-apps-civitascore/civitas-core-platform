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
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkStage;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingNodeTypeTest {

  private final MappingNodeType mappingNodeType =
      new MappingNodeType(new MappingConfigParser(), new RecordPathCompiler());
  private final FrostSinkStage envelopeSink = new FrostSinkStage("https://frost.example/FROST");

  private static GraphNode mappingNode(String id, Map<String, Object> fields) {
    return new GraphNode(id, "mapping", Map.of("mappingConfig", Map.of("fields", fields)));
  }

  @Test
  void staTargetsInAnEarlierMappingBeforeAnEnvelopeSinkAreRejected() {
    // Only the final mapping compiles into the STA envelope; a catalog path in an earlier mapping
    // would otherwise be treated as a plain record field named after the STA path.
    GraphNode first = mappingNode("m1", Map.of("$.things[].name", "$.stationName"));
    GraphNode last = mappingNode("m2", Map.of("$.value", "$.raw"));

    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> mappingNodeType.compile(List.of(first, last), envelopeSink));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }
}
