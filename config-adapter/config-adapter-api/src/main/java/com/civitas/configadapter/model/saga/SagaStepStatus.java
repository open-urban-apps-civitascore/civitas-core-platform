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
 * Status of an individual saga step. State transitions:
 *
 * <pre>
 * PENDING → IN_PROGRESS → SUCCESS → COMPENSATING → COMPENSATED
 *                                                 → COMPENSATION_FAILED
 *                       → FAILED
 * PENDING → SKIPPED (conditional step not applicable)
 * </pre>
 */
public enum SagaStepStatus {

  /** Step not yet started. */
  PENDING,

  /** Step command sent to adapter, waiting for reply. */
  IN_PROGRESS,

  /** Adapter returned success. */
  SUCCESS,

  /** Adapter returned failure or step timed out. */
  FAILED,

  /** Step was skipped because its condition was not met (e.g. no pipelines for Redpanda). */
  SKIPPED,

  /** Compensation command sent for this step. */
  COMPENSATING,

  /** Compensation completed successfully. */
  COMPENSATED,

  /** Compensation failed. Resource may be stale and require manual intervention. */
  COMPENSATION_FAILED
}
