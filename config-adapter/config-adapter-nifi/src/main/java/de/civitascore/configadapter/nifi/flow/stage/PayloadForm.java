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

import java.util.Set;

/**
 * The shape of the data payload flowing between pipeline stages — declared as {@link
 * SourceStage#output()} and required via {@link SinkStage#acceptedInputs}. One shared vocabulary
 * for both sides so compatibility is a per-edge check instead of pairwise source/sink knowledge.
 * The portal-frontend pipeline editor mirrors these literals for its edit-time validation, so both
 * layers give the same answer on the same graph.
 */
public enum PayloadForm {
  /** Raw JSON bytes with no shape guarantee beyond well-formedness. */
  RAW_JSON,
  /**
   * Raw JSON bytes shaped as the SensorThings envelope ({@code $.things}/{@code $.observations}).
   */
  STA_ENVELOPE,
  /** NiFi records — the form UpdateRecord mappings and record-writing sinks operate on. */
  RECORDS;

  /**
   * Forms the implicit ConvertRecord step turns into {@link #RECORDS}. This is the single coercion
   * of the compatibility relation: a sink accepting {@code RECORDS} also accepts any of these,
   * because the flow builder inserts the convert structurally — it is never user-modelled.
   */
  public static final Set<PayloadForm> CONVERTIBLE_TO_RECORDS = Set.of(RAW_JSON, STA_ENVELOPE);
}
