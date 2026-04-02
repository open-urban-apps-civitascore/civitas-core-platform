/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.util;

/** Calculates exponential backoff delays capped at a configurable maximum. */
public class BackoffCalculator {
  private final long initialBackoffMs;
  private final long maxBackoffMs;

  public BackoffCalculator(long initialBackoffMs, long maxBackoffMs) {
    if (initialBackoffMs <= 0) {
      throw new IllegalArgumentException(
          "initialBackoffMs must be positive, got: " + initialBackoffMs);
    }
    if (maxBackoffMs <= 0) {
      throw new IllegalArgumentException("maxBackoffMs must be positive, got: " + maxBackoffMs);
    }
    this.initialBackoffMs = initialBackoffMs;
    this.maxBackoffMs = maxBackoffMs;
  }

  /**
   * Calculates exponential backoff delay.
   *
   * @param attempt the current attempt number (1-based)
   * @return the backoff delay in milliseconds
   */
  public long calculate(int attempt) {
    if (attempt <= 0) {
      throw new IllegalArgumentException("attempt must be positive, got: " + attempt);
    }
    long backoff = initialBackoffMs * (long) Math.pow(2, attempt - 1);
    return Math.min(backoff, maxBackoffMs);
  }
}
