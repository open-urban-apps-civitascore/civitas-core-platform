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
 * The payload shape a sink consumes. Drives the convert and record-mapping decisions structurally:
 * a {@code RECORDS} sink gets a ConvertRecord step whenever the source does not already emit
 * records; a {@code RAW_JSON} sink suppresses the record chain unless a mapping with an envelope
 * rebuild plan ({@link MappingSupport#ENVELOPE}) pulls it in.
 */
public enum SinkInput {
  RECORDS,
  RAW_JSON
}
