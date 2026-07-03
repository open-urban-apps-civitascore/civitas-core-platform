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
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import java.util.List;
import java.util.Map;

/**
 * A self-describing pipeline sink. One implementation per {@link SinkType}, owning both halves of
 * its lifecycle: plan-time binding (validation, property maps) and build-time wiring of the entire
 * tail region — terminal processor or sub-graph, the shared error sink, and all failure routing.
 */
public interface SinkStage {

  /** The registry key. */
  SinkType type();

  /** The payload shape this sink consumes — drives convert and mapping insertion structurally. */
  SinkInput input();

  /**
   * Capabilities the source must declare for this sink to work, each with the exact rejection
   * message thrown when it is missing.
   */
  Map<SourceCapability, String> requiredSourceCapabilities();

  /** The geometry encoding the mapping compiler must emit for this sink's target format. */
  GeometryEncoding geometryEncoding();

  /** Whether a record mapping may run in front of this sink. */
  default boolean acceptsMapping() {
    return input() == SinkInput.RECORDS;
  }

  /** The rejection message when the graph carries a mapping but {@link #acceptsMapping} is false. */
  default String mappingRejectionMessage() {
    return "sink does not support a record mapping";
  }

  /**
   * Plan-time half: validates the sink spec and fills the property maps. Secrets go only into
   * {@link PlanContext#putSensitive}, never into snapshot-bound properties.
   *
   * @throws FatalAdapterException if the sink spec is invalid or unsafe to deploy
   */
  void bind(PipelineDeploymentRequest request, PlanContext out) throws FatalAdapterException;

  /** Build-time half, phase A: registers sink-side controller services (before processors). */
  default void registerControllerServices(BuildContext ctx) throws FatalAdapterException {}

  /**
   * Build-time half, phase B: wires the whole tail region onto {@code upstreamTail} — the terminal
   * processor(s) or sub-graph, the error sink, and the {@code failure} edges of {@code
   * upstreamFailureSources} (convert/mapping processors). Every failure relationship must route to
   * the error sink: a silent drop is undiagnosable data loss.
   *
   * <p>The sink owns append order inside its region because it is part of the byte-stable snapshot
   * contract — sinks differ in where the error sink lands relative to their own processors.
   */
  void build(BuildContext ctx, Processor upstreamTail, List<Processor> upstreamFailureSources)
      throws FatalAdapterException;
}
