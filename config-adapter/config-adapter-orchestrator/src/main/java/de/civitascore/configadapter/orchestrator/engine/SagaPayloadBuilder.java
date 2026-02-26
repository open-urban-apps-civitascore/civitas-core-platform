/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.engine;

import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaStep;
import de.civitascore.configadapter.model.saga.SagaStepStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds payloads and aggregates results for saga step commands. Extracted from {@link
 * SagaStateMachine} to keep the state machine focused on state transitions.
 */
final class SagaPayloadBuilder {

  private SagaPayloadBuilder() {}

  /**
   * Build the payload for a forward step command. Returns the trigger payload augmented with
   * previous step results and saga envelope fields.
   */
  static Map<String, Object> buildStepPayload(SagaContext context, SagaStepDefinition stepDef) {
    var payload = new HashMap<>(context.triggerPayload());
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SUCCESS && step.result() != null) {
        payload.putAll(step.result());
      }
    }
    payload.put("sagaId", context.sagaId());
    payload.put("datasetId", context.datasetId());
    payload.put("_operation", stepDef.operation());
    payload.put("_stepId", stepDef.stepId());
    return Map.copyOf(payload);
  }

  /** Build the compensation payload from the step's stored compensation data. */
  static Map<String, Object> buildCompensationPayload(SagaContext context, SagaStep step) {
    var payload = new HashMap<String, Object>();
    if (step.compensationData() != null) {
      payload.putAll(step.compensationData());
    }
    if (step.result() != null) {
      payload.putAll(step.result());
    }
    payload.put("sagaId", context.sagaId());
    payload.put("datasetId", context.datasetId());
    payload.put("_operation", step.operation());
    payload.put("_stepId", step.stepId());
    return Map.copyOf(payload);
  }

  /** Aggregate results from all successful steps for the final CompleteSaga action. */
  static Map<String, Object> aggregateResults(SagaContext context) {
    var results = new HashMap<String, Object>();
    results.put("sagaId", context.sagaId());
    results.put("datasetId", context.datasetId());
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SUCCESS && step.result() != null) {
        results.putAll(step.result());
      }
    }
    return Map.copyOf(results);
  }

  /** Collect stale and cleaned resources from compensation results. */
  static void collectCompensationResults(
      SagaContext context,
      List<SagaAction.StaleResource> stale,
      List<SagaAction.CleanedResource> cleaned) {
    for (SagaStep step : context.steps()) {
      String resourceId = extractResourceId(step);
      if (step.status() == SagaStepStatus.COMPENSATION_FAILED) {
        stale.add(new SagaAction.StaleResource(step.adapter(), resourceId, step.error()));
      } else if (step.status() == SagaStepStatus.COMPENSATED) {
        cleaned.add(new SagaAction.CleanedResource(step.adapter(), resourceId));
      }
    }
  }

  /** Collect stale and deleted resources from delete execution results. */
  static void collectDeleteResults(
      SagaContext context,
      List<SagaAction.StaleResource> stale,
      List<SagaAction.CleanedResource> cleaned) {
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SKIPPED) {
        continue;
      }
      String resourceId = extractResourceId(step);
      if (step.status() == SagaStepStatus.FAILED) {
        stale.add(new SagaAction.StaleResource(step.adapter(), resourceId, step.error()));
      } else if (step.status() == SagaStepStatus.SUCCESS) {
        cleaned.add(new SagaAction.CleanedResource(step.adapter(), resourceId));
      }
    }
  }

  /** Extract a representative resource ID from a step's result data. */
  static String extractResourceId(SagaStep step) {
    if (step.result() == null || step.result().isEmpty()) {
      return step.stepId();
    }
    for (String key : List.of("projectId", "routeId", "serviceId", "pipelineIds")) {
      Object value = step.result().get(key);
      if (value != null) {
        return value.toString();
      }
    }
    return step.stepId();
  }
}
