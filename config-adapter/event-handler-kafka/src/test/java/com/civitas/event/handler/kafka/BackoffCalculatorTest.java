/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2026 Civitas Connect e. V. and others.
 */
package com.civitas.event.handler.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BackoffCalculatorTest {

  @ParameterizedTest(name = "Attempt {0} should verify backoff {1}ms (Initial: 1000ms)")
  @CsvSource({
    "1, 1000", // 1000 * 2^0
    "2, 2000", // 1000 * 2^1
    "3, 4000", // 1000 * 2^2
    "4, 8000", // 1000 * 2^3
    "5, 16000" // 1000 * 2^4
  })
  void shouldCalculateExponentialBackoff(int attempt, long expectedBackoff) {
    // Given
    long initialMs = 1000L;
    long maxMs = 60000L;
    BackoffCalculator calculator = new BackoffCalculator(initialMs, maxMs);

    // When
    long result = calculator.calculate(attempt);

    // Then
    assertEquals(expectedBackoff, result);
  }

  @Test
  @DisplayName("Should cap backoff at defined maximum")
  void shouldCapAtMaxBackoff() {
    // Given
    long initialMs = 1000L;
    long maxMs = 5000L;
    BackoffCalculator calculator = new BackoffCalculator(initialMs, maxMs);

    // When
    long result = calculator.calculate(5);

    // Then
    assertEquals(5000L, result);
  }
}
