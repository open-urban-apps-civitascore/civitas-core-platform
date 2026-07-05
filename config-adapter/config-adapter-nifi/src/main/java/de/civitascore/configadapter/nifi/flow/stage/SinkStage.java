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
import de.civitascore.configadapter.nifi.flow.SinkResolutionContext;
import de.civitascore.configadapter.nifi.flow.SinkSpec;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A self-describing pipeline sink. One implementation per {@link SinkType}, owning its whole
 * lifecycle: parsing its catalog entry into the typed spec, plan-time binding (validation, property
 * maps), and build-time wiring of the entire tail region — terminal processor or sub-graph, the
 * shared error sink, and all failure routing.
 *
 * @param <S> the sink's typed specification
 */
public interface SinkStage<S extends SinkSpec> {

  /** The registry key. */
  SinkType type();

  /**
   * The spec variant this stage parses and binds — the checked-cast anchor at wildcard call sites.
   */
  Class<S> specType();

  /**
   * Resolution half: parses this sink's raw catalog entry (the trigger payload's {@code
   * datasinks[]} element) into the typed spec, enforcing the sink's own payload invariants.
   *
   * @param datasink the raw catalog entry
   * @param ctx saga-level values not carried by the entry itself
   * @throws FatalAdapterException if the entry or the saga context is invalid for this sink
   */
  S parseSpec(Map<String, Object> datasink, SinkResolutionContext ctx) throws FatalAdapterException;

  /**
   * The payload forms this sink can consume — drives the compatibility check and the convert and
   * mapping insertion structurally. May depend on whether a record mapping runs upstream, because a
   * mapping can synthesize a form the source alone would have to emit. A sink accepting {@link
   * PayloadForm#RECORDS} implicitly also accepts every {@link PayloadForm#CONVERTIBLE_TO_RECORDS}
   * form (the flow builder inserts the convert step).
   *
   * @param mappedUpstream whether the pipeline graph carries a record mapping
   */
  Set<PayloadForm> acceptedInputs(boolean mappedUpstream);

  /**
   * The rejection message when the source's output form is neither accepted nor convertible to an
   * accepted form.
   */
  default String inputRejectionMessage(boolean mappedUpstream) {
    return "sink " + type() + " cannot consume the payload form emitted by the source";
  }

  /** The geometry encoding the mapping compiler must emit for this sink's target format. */
  GeometryEncoding geometryEncoding();

  /** How a record mapping may run in front of this sink. */
  default MappingSupport mappingSupport() {
    return acceptedInputs(false).contains(PayloadForm.RECORDS)
        ? MappingSupport.RECORD_PATH
        : MappingSupport.NONE;
  }

  /**
   * The rejection message when the graph carries a mapping but {@link #mappingSupport} is {@link
   * MappingSupport#NONE}.
   */
  default String mappingRejectionMessage() {
    return "sink does not support a record mapping";
  }

  /**
   * Plan-time half: validates the typed spec and fills the property maps. Secrets go only into
   * {@link PlanContext#putSensitive}, never into snapshot-bound properties.
   *
   * @throws FatalAdapterException if the sink spec is invalid or unsafe to deploy
   */
  void bind(S spec, PlanContext out) throws FatalAdapterException;

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
