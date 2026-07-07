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

/**
 * How a sink supports a record mapping in front of it. A boolean cannot distinguish "accepts a
 * mapping" from "accepts a mapping but consumes the flat-compiled fields through its own sink-owned
 * region", so the third operating mode is explicit instead of encoded in {@link
 * SinkStage#acceptedInputs} special cases.
 */
public enum MappingSupport {
  /** No mapping may run in front of this sink; the planner rejects the combination. */
  NONE,
  /** The compiled RecordPath mapping writes the sink's record shape directly (records sink). */
  RECORD_PATH,
  /**
   * The mapping compiles to flat intermediate fields a sink-owned region consumes through generated
   * templates (the FROST entity plan).
   */
  ENVELOPE
}
