/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.saga;

import java.util.List;
import java.util.Optional;

/**
 * Utility methods for navigating a {@link SagaContext}. Used by adapters to read previous step data
 * and by the orchestrator to query step status.
 */
public final class SagaContextHelper {

  private SagaContextHelper() {}

  /** Finds a step by its unique stepId. */
  public static Optional<SagaStep> findStep(SagaContext saga, String stepId) {
    return saga.steps().stream().filter(s -> s.stepId().equals(stepId)).findFirst();
  }

  /** Finds the index of a step by its stepId. Returns -1 if not found. */
  public static int findStepIndex(SagaContext saga, String stepId) {
    var steps = saga.steps();
    for (int i = 0; i < steps.size(); i++) {
      if (steps.get(i).stepId().equals(stepId)) {
        return i;
      }
    }
    return -1;
  }

  /** Returns all steps that completed successfully (status SUCCESS). */
  public static List<SagaStep> getCompletedSteps(SagaContext saga) {
    return saga.steps().stream().filter(s -> s.status() == SagaStepStatus.SUCCESS).toList();
  }

  /** Returns all steps still pending. */
  public static List<SagaStep> getPendingSteps(SagaContext saga) {
    return saga.steps().stream().filter(s -> s.status() == SagaStepStatus.PENDING).toList();
  }

  /**
   * Returns completed steps in reverse order — used to determine compensation order for
   * create/update sagas.
   */
  public static List<SagaStep> getCompletedStepsReversed(SagaContext saga) {
    var completed = getCompletedSteps(saga);
    return completed.reversed();
  }

  /**
   * Returns steps where compensation failed. These represent stale resources requiring manual
   * intervention.
   */
  public static List<SagaStep> getStaleSteps(SagaContext saga) {
    return saga.steps().stream()
        .filter(s -> s.status() == SagaStepStatus.COMPENSATION_FAILED)
        .toList();
  }

  /** Returns steps that were successfully compensated (cleaned up). */
  public static List<SagaStep> getCleanedSteps(SagaContext saga) {
    return saga.steps().stream().filter(s -> s.status() == SagaStepStatus.COMPENSATED).toList();
  }

  /** Returns the next step to compensate, or empty if all compensations are done. */
  public static Optional<SagaStep> getNextStepToCompensate(SagaContext saga) {
    return saga.steps().stream()
        .filter(s -> s.status() == SagaStepStatus.SUCCESS)
        .reduce(
            (first, second) -> second); // last SUCCESS step = next to compensate (reverse order)
  }

  /** Returns true if all compensation-eligible steps have been processed. */
  public static boolean isCompensationComplete(SagaContext saga) {
    return saga.steps().stream()
        .noneMatch(
            s -> s.status() == SagaStepStatus.SUCCESS || s.status() == SagaStepStatus.COMPENSATING);
  }

  /** Returns true if any compensation step failed. */
  public static boolean hasCompensationFailure(SagaContext saga) {
    return saga.steps().stream().anyMatch(s -> s.status() == SagaStepStatus.COMPENSATION_FAILED);
  }
}
