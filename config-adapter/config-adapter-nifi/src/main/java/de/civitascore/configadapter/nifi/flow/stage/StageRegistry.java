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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.SourceType;
import de.civitascore.configadapter.nifi.graph.NodeKind;
import de.civitascore.configadapter.nifi.graph.NodeKind.Role;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The closed set of deployable stages and transform node kinds, hand-wired at adapter
 * initialization. Deliberately not ServiceLoader-discovered: stages mint flow components, so an
 * open classpath registration would let any JAR bypass the curated-fragment review. A new stage or
 * transform kind is one implementation plus one entry in {@code NifiSagaHandler.doInitialize}.
 */
public final class StageRegistry {

  private final Map<SourceType, SourceStage> sources = new EnumMap<>(SourceType.class);
  private final Map<SinkType, SinkStage> sinks = new EnumMap<>(SinkType.class);
  private final Map<NodeKind, TransformNodeType> transformNodeTypes = new EnumMap<>(NodeKind.class);

  public StageRegistry(
      List<SourceStage> sourceStages,
      List<SinkStage> sinkStages,
      List<TransformNodeType> transformKinds) {
    for (SourceStage stage : sourceStages) {
      if (sources.putIfAbsent(stage.type(), stage) != null) {
        throw new IllegalArgumentException("duplicate source stage for " + stage.type());
      }
    }
    for (SinkStage stage : sinkStages) {
      if (sinks.putIfAbsent(stage.type(), stage) != null) {
        throw new IllegalArgumentException("duplicate sink stage for " + stage.type());
      }
    }
    // The transform vocabulary is checked at construction so a declared-but-unregistered kind
    // kills the adapter start, never a deploy: NodeKind and this registry are two files, and only
    // this fail-fast keeps them from drifting apart.
    for (TransformNodeType kind : transformKinds) {
      if (kind.kind().role() != Role.TRANSFORM) {
        throw new IllegalArgumentException(
            "node kind " + kind.kind() + " does not have the TRANSFORM role");
      }
      if (transformNodeTypes.putIfAbsent(kind.kind(), kind) != null) {
        throw new IllegalArgumentException("duplicate transform node type for " + kind.kind());
      }
    }
    for (NodeKind kind : NodeKind.values()) {
      if (kind.role() == Role.TRANSFORM && !transformNodeTypes.containsKey(kind)) {
        throw new IllegalArgumentException("no transform node type registered for " + kind);
      }
    }
  }

  public SourceStage source(SourceType type) throws FatalAdapterException {
    SourceStage stage = sources.get(type);
    if (stage == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "no source stage registered for type: " + type);
    }
    return stage;
  }

  public SinkStage sink(SinkType type) throws FatalAdapterException {
    SinkStage stage = sinks.get(type);
    if (stage == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "no sink stage registered for type: " + type);
    }
    return stage;
  }

  /**
   * The transform node type for {@code kind}. Unlike source/sink types (parsed from the payload),
   * the kind comes from the derived path, and construction guarantees every TRANSFORM kind is
   * registered — a miss is a programming error, not a payload error.
   */
  public TransformNodeType transformNodeType(NodeKind kind) {
    TransformNodeType nodeType = transformNodeTypes.get(kind);
    if (nodeType == null) {
      throw new IllegalStateException("no transform node type registered for " + kind);
    }
    return nodeType;
  }
}
