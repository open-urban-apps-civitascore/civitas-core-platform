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

/**
 * Blueprint for a single saga step. Defines what adapter handles this step, what operation to
 * execute, and where to send commands. Immutable — one instance per step in a saga definition.
 *
 * @param stepId unique identifier (e.g. "create-project", "create-route", "deploy-pipelines")
 * @param adapter adapter name for routing (e.g. "frost", "apisix", "redpanda")
 * @param operation the forward operation (e.g. "CREATE_PROJECT", "CREATE_ROUTE")
 * @param compensationOperation the operation used to undo this step (e.g. "DELETE_PROJECT"), null
 *     if compensation is not supported
 * @param executeTopic Kafka topic to send forward commands to
 * @param compensateTopic Kafka topic to send compensation commands to, null if no compensation
 * @param conditional true if this step should be skipped when its precondition is not met (e.g.
 *     Redpanda step when no pipelines exist)
 */
public record SagaStepDefinition(
    String stepId,
    String adapter,
    String operation,
    String compensationOperation,
    String executeTopic,
    String compensateTopic,
    boolean conditional) {

  /** Creates a mandatory (non-conditional) step definition. */
  public static SagaStepDefinition mandatory(
      String stepId,
      String adapter,
      String operation,
      String compensationOperation,
      String executeTopic,
      String compensateTopic) {
    return new SagaStepDefinition(
        stepId, adapter, operation, compensationOperation, executeTopic, compensateTopic, false);
  }

  /** Creates a conditional step definition (skipped when precondition is not met). */
  public static SagaStepDefinition conditional(
      String stepId,
      String adapter,
      String operation,
      String compensationOperation,
      String executeTopic,
      String compensateTopic) {
    return new SagaStepDefinition(
        stepId, adapter, operation, compensationOperation, executeTopic, compensateTopic, true);
  }

  /** Returns true if this step supports compensation. */
  public boolean hasCompensation() {
    return compensationOperation != null && compensateTopic != null;
  }
}
