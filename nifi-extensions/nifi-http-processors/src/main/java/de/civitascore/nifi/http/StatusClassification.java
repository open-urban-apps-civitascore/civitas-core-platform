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

import java.util.Optional;

/** Which HTTP status of a response is an error, and of which kind. */
final class StatusClassification {

  private StatusClassification() {}

  /**
   * The error kind of a status, empty for a 2xx.
   *
   * <p>A 3xx is fatal: the processor expects a web client service that does not follow redirects,
   * so a redirect shows a wrong host or path. 408 and 429 are transient like a 5xx: the server asks
   * the client to come back later.
   */
  static Optional<ErrorKind> errorKind(int status) {
    if (status >= 200 && status < 300) {
      return Optional.empty();
    }
    if (status == 408 || status == 429) {
      return Optional.of(ErrorKind.TRANSIENT);
    }
    if (status >= 300 && status < 500) {
      return Optional.of(ErrorKind.FATAL);
    }
    return Optional.of(ErrorKind.TRANSIENT);
  }
}
