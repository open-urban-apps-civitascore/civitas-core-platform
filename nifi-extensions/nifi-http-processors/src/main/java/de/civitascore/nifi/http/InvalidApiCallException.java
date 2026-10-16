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

import java.util.List;

/** The {@code API Call} property breaks one or more rules. No violation contains a value. */
final class InvalidApiCallException extends Exception {

  private static final long serialVersionUID = 1L;

  private final List<String> violations;

  InvalidApiCallException(List<String> violations) {
    super(String.join("; ", violations));
    this.violations = List.copyOf(violations);
  }

  List<String> violations() {
    return violations;
  }
}
