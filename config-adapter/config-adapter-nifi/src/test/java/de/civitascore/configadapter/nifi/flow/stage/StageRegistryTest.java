/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.SourceType;
import de.civitascore.configadapter.nifi.graph.NodeKind;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageRegistryTest {

  private static TransformNodeType transformNodeType(NodeKind kind) {
    return new TransformNodeType() {
      @Override
      public NodeKind kind() {
        return kind;
      }

      @Override
      public Compilation compile(List<GraphNode> ownNodes, SinkStage sink) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static SourceStage sourceStage(SourceType type) {
    return new SourceStage() {
      @Override
      public SourceType type() {
        return type;
      }

      @Override
      public PayloadForm output() {
        return PayloadForm.RECORDS;
      }

      @Override
      public String cronRejectionMessage() {
        return "no cron";
      }

      @Override
      public void bind(Datasource source, PlanContext out) {}

      @Override
      public StageResult build(BuildContext ctx) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static SinkStage sinkStage(SinkType type) {
    return new SinkStage() {
      @Override
      public SinkType type() {
        return type;
      }

      @Override
      public Set<PayloadForm> acceptedInputs(boolean mappedUpstream) {
        return Set.of(PayloadForm.RECORDS);
      }

      @Override
      public GeometryEncoding geometryEncoding() {
        return GeometryEncoding.WKT;
      }

      @Override
      public void bind(PipelineDeploymentRequest request, PlanContext out) {}

      @Override
      public void build(
          BuildContext ctx, Processor upstreamTail, List<Processor> upstreamFailureSources) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static List<TransformNodeType> allTransformKinds() {
    return List.of(transformNodeType(NodeKind.MAPPING));
  }

  @Test
  void resolvesStagesByType() throws Exception {
    SourceStage mqtt = sourceStage(SourceType.MQTT);
    SinkStage postgis = sinkStage(SinkType.POSTGIS);
    TransformNodeType mapping = transformNodeType(NodeKind.MAPPING);
    StageRegistry registry = new StageRegistry(List.of(mqtt), List.of(postgis), List.of(mapping));

    assertSame(mqtt, registry.source(SourceType.MQTT));
    assertSame(postgis, registry.sink(SinkType.POSTGIS));
    assertSame(mapping, registry.transformNodeType(NodeKind.MAPPING));
  }

  @Test
  void unknownTypesFailWithTemplateError() {
    StageRegistry registry = new StageRegistry(List.of(), List.of(), allTransformKinds());

    FatalAdapterException sourceMiss =
        assertThrows(FatalAdapterException.class, () -> registry.source(SourceType.SQL));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, sourceMiss.getErrorCode());
    FatalAdapterException sinkMiss =
        assertThrows(FatalAdapterException.class, () -> registry.sink(SinkType.FROST));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, sinkMiss.getErrorCode());
  }

  @Test
  void duplicateRegistrationsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StageRegistry(
                List.of(sourceStage(SourceType.MQTT), sourceStage(SourceType.MQTT)),
                List.of(),
                allTransformKinds()));
  }

  @Test
  void uncoveredTransformKindIsRejectedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class, () -> new StageRegistry(List.of(), List.of(), List.of()));
  }

  @Test
  void nonTransformKindIsRejectedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StageRegistry(
                List.of(),
                List.of(),
                List.of(transformNodeType(NodeKind.MAPPING), transformNodeType(NodeKind.CRON))));
  }

  @Test
  void duplicateTransformKindIsRejectedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StageRegistry(
                List.of(),
                List.of(),
                List.of(transformNodeType(NodeKind.MAPPING), transformNodeType(NodeKind.MAPPING))));
  }
}
