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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectionTestSettingsTest {

  private static ConnectionTestSettings settings(Duration timeout, int maxConcurrent) {
    return new ConnectionTestSettings(timeout, maxConcurrent);
  }

  @Test
  void clientTimeoutIsTimeoutMinusTwoSeconds() {
    assertEquals(Duration.ofSeconds(13), settings(Duration.ofSeconds(15), 1).clientTimeout());
  }

  @Test
  void clientTimeoutIsAtLeastOneSecond() {
    assertEquals(Duration.ofSeconds(1), settings(Duration.ofSeconds(2), 1).clientTimeout());
    assertEquals(Duration.ofSeconds(1), settings(Duration.ofMillis(500), 1).clientTimeout());
    assertEquals(Duration.ofSeconds(1), settings(Duration.ofSeconds(3), 1).clientTimeout());
  }

  @Test
  void zeroOrNegativeTimeoutIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> settings(Duration.ZERO, 1));
    assertThrows(IllegalArgumentException.class, () -> settings(Duration.ofSeconds(-1), 1));
  }

  @Test
  void maxConcurrentBelowOneIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> settings(Duration.ofSeconds(15), 0));
    assertThrows(IllegalArgumentException.class, () -> settings(Duration.ofSeconds(15), -1));
  }
}
