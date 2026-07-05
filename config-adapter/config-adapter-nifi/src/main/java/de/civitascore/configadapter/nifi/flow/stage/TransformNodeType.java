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
import de.civitascore.configadapter.nifi.graph.NodeKind;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.CompiledTransform;
import de.civitascore.configadapter.nifi.mapping.FrostEnvelopePlan;
import java.util.List;
import java.util.Objects;

/**
 * One on-path transform node kind's plan half: it owns parsing its nodes' payload and compiling
 * them into the chain units the flow builder materializes. Registered per {@link NodeKind} in the
 * {@link StageRegistry} — a new transform kind is one implementation plus one registration line,
 * the same discipline as the source/sink stages.
 */
public interface TransformNodeType {

  /** The node kind this type compiles; its role must be {@link NodeKind.Role#TRANSFORM}. */
  NodeKind kind();

  /**
   * Compiles this kind's on-path nodes against the resolved sink.
   *
   * @param ownNodes this kind's nodes in flow order, never empty
   * @param sink the pipeline's sink stage (target namespace, geometry encoding, mapping support)
   * @return the compilation; its units align 1:1 with {@code ownNodes}
   * @throws FatalAdapterException if a node payload is missing or invalid
   */
  Compilation compile(List<GraphNode> ownNodes, SinkStage sink) throws FatalAdapterException;

  /**
   * The compiled units of one kind, aligned 1:1 with the nodes passed to {@link #compile}.
   *
   * @param units the chain units, one per node, in flow order
   * @param staEnvelope the envelope rebuild plan for a mapped FROST sink, or {@code null}. The
   *     mapped-FROST flow rebuilds records into the SensorThings envelope inside the sink's build
   *     region, but the plan is compiled from the last mapping — so the mapping kind produces it
   *     and the spec carries it to the sink.
   */
  record Compilation(List<CompiledTransform> units, FrostEnvelopePlan staEnvelope) {
    public Compilation {
      units = List.copyOf(Objects.requireNonNull(units, "units"));
    }
  }
}
