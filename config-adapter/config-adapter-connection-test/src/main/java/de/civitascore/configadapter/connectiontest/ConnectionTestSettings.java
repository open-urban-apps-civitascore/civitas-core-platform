/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import java.time.Duration;

/**
 * The limits of the connection test.
 *
 * @param timeout the longest a test may take before it is stopped
 * @param maxConcurrent the most tests that run at the same time
 */
record ConnectionTestSettings(Duration timeout, int maxConcurrent) {

  /** The clients end their own waits this much earlier, so their message wins over the cut-off. */
  private static final Duration CLIENT_MARGIN = Duration.ofSeconds(2);

  ConnectionTestSettings {
    if (timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("connection test timeout must be positive");
    }
    if (maxConcurrent < 1) {
      throw new IllegalArgumentException("connection test max-concurrent must be at least 1");
    }
  }

  /** The time a client may wait for the source, a little less than the test may take. */
  Duration clientTimeout() {
    Duration client = timeout.minus(CLIENT_MARGIN);
    return client.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : client;
  }
}
