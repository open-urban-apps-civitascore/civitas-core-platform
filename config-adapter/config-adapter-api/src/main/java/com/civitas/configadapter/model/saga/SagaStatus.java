/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.saga;

/**
 * Overall saga status. State transitions:
 *
 * <pre>
 * PENDING → EXECUTING → COMPLETED
 *                     → COMPENSATING → COMPENSATED
 *                                    → COMPENSATION_FAILED
 *           EXECUTING → FAILED (delete: best-effort, no compensation)
 * </pre>
 */
public enum SagaStatus {

  /** Saga created but first step not yet started. */
  PENDING,

  /** At least one step is in progress. */
  EXECUTING,

  /** All steps completed successfully. Terminal state. */
  COMPLETED,

  /** A step failed and no completed steps exist to compensate (or delete best-effort done). Terminal state. */
  FAILED,

  /** A step failed; compensation of completed steps is in progress. */
  COMPENSATING,

  /** All completed steps were successfully compensated. Terminal state. */
  COMPENSATED,

  /** Compensation was attempted but at least one compensation step failed. Requires manual intervention. Terminal state. */
  COMPENSATION_FAILED;

  /** Returns true if this is a terminal state (no further transitions possible). */
  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == COMPENSATED || this == COMPENSATION_FAILED;
  }
}
