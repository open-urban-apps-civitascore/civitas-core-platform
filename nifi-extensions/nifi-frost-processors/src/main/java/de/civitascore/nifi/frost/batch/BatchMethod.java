/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.batch;

import java.util.Locale;

/** The HTTP methods a sub-request of a FROST JSON batch uses. */
public enum BatchMethod {
  GET,
  POST,
  PATCH;

  /** The method as the batch document spells it: lower case. */
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }
}
