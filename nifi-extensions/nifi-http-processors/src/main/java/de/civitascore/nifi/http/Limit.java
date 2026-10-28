/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import de.civitascore.nifi.http.ApiCall.Parameter;

/** A limit that each request of a run sends as a query parameter. */
record Limit(String parameter, int size) {

  Limit {
    if (size <= 0) {
      throw new IllegalArgumentException("size must be at least 1");
    }
  }

  Parameter asParameter() {
    return new Parameter(parameter, Integer.toString(size));
  }
}
