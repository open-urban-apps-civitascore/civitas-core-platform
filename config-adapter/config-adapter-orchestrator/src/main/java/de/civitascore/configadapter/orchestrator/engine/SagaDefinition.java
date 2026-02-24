/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.engine;

import de.civitascore.configadapter.model.saga.SagaType;
import java.util.List;
import java.util.Set;

/**
 * Blueprint for a complete saga workflow. Defines the ordered list of steps and the saga type.
 * Immutable — one instance per saga type, created at startup.
 *
 * @param sagaType the workflow type (DATASET_CREATE, DATASET_UPDATE, DATASET_DELETE)
 * @param steps ordered list of step definitions
 * @param supportsCompensation whether this saga type supports compensation (false for DELETE)
 */
public record SagaDefinition(
    SagaType sagaType, List<SagaStepDefinition> steps, boolean supportsCompensation) {

  /** Returns the step definition for the given stepId, or null if not found. */
  public SagaStepDefinition findStep(String stepId) {
    return steps.stream().filter(s -> s.stepId().equals(stepId)).findFirst().orElse(null);
  }

  /** Returns the step definition at the given index. */
  public SagaStepDefinition stepAt(int index) {
    return steps.get(index);
  }

  /** Returns the number of steps in this saga. */
  public int stepCount() {
    return steps.size();
  }

  /**
   * Returns the compensation steps for the given set of completed step IDs, in reverse execution
   * order. Only includes steps that support compensation.
   *
   * @param completedStepIds step IDs that completed successfully and need to be rolled back
   * @return compensation-eligible steps in reverse order
   */
  public List<SagaStepDefinition> getCompensationSteps(Set<String> completedStepIds) {
    return steps.reversed().stream()
        .filter(s -> completedStepIds.contains(s.stepId()))
        .filter(SagaStepDefinition::hasCompensation)
        .toList();
  }

  /** Returns the index of the step with the given stepId, or -1 if not found. */
  public int findStepIndex(String stepId) {
    for (int i = 0; i < steps.size(); i++) {
      if (steps.get(i).stepId().equals(stepId)) {
        return i;
      }
    }
    return -1;
  }
}
