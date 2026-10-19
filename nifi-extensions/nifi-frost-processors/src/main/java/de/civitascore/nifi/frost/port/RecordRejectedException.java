/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.port;

import java.io.Serial;

/**
 * A record the port cannot plan. It never reaches FROST: the processor routes it to the failure
 * relationship with the entity and the reason, so the defect is visible where the record is, rather
 * than as a status code the server returns for a request that should not have been sent.
 */
public class RecordRejectedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String entity;

  public RecordRejectedException(String entity, String message) {
    super(message);
    this.entity = entity;
  }

  /** The entity the defect belongs to, reported in the failure attributes. */
  public String entity() {
    return entity;
  }
}
