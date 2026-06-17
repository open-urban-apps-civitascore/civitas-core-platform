/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.saga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/**
 * Tracks an individual adapter step within a saga. Each step is identified by a unique {@code
 * stepId} (e.g. "create-project") rather than a positional index, making step references stable
 * across saga types.
 *
 * @param stepId unique identifier (e.g. "create-project", "create-route")
 * @param adapter adapter name for routing (e.g. "frost", "apisix", "nifi")
 * @param operation what to do (e.g. "CREATE_PROJECT", "DELETE_ROUTE")
 * @param status current step status
 * @param result adapter-specific output (e.g. projectId, routeId, baseUrl)
 * @param compensationData data needed to undo this step (e.g. previous state for restore)
 * @param error error message if the step or its compensation failed
 * @param startedAt when the step command was sent
 * @param completedAt when the step result was received
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaStep(
    String stepId,
    String adapter,
    String operation,
    SagaStepStatus status,
    Map<String, Object> result,
    Map<String, Object> compensationData,
    String error,
    Instant startedAt,
    Instant completedAt) {

  /** Creates a new step in PENDING status with no result data. */
  public static SagaStep pending(String stepId, String adapter, String operation) {
    return new SagaStep(
        stepId, adapter, operation, SagaStepStatus.PENDING, Map.of(), Map.of(), null, null, null);
  }

  /** Returns a copy of this step with the given status. */
  public SagaStep withStatus(SagaStepStatus newStatus) {
    return new SagaStep(
        stepId,
        adapter,
        operation,
        newStatus,
        result,
        compensationData,
        error,
        startedAt,
        completedAt);
  }

  /** Returns a copy of this step transitioned to IN_PROGRESS. */
  public SagaStep asInProgress(Instant now) {
    return new SagaStep(
        stepId,
        adapter,
        operation,
        SagaStepStatus.IN_PROGRESS,
        result,
        compensationData,
        error,
        now,
        null);
  }

  /** Returns a copy of this step transitioned to SUCCESS with result data. */
  public SagaStep asSucceeded(
      Map<String, Object> resultData, Map<String, Object> compData, Instant now) {
    return new SagaStep(
        stepId,
        adapter,
        operation,
        SagaStepStatus.SUCCESS,
        resultData,
        compData,
        null,
        startedAt,
        now);
  }

  /** Returns a copy of this step transitioned to FAILED. */
  public SagaStep asFailed(String errorMessage, Instant now) {
    return new SagaStep(
        stepId,
        adapter,
        operation,
        SagaStepStatus.FAILED,
        result,
        compensationData,
        errorMessage,
        startedAt,
        now);
  }

  /** Returns a copy of this step transitioned to SKIPPED. */
  public SagaStep asSkipped() {
    return new SagaStep(
        stepId, adapter, operation, SagaStepStatus.SKIPPED, Map.of(), Map.of(), null, null, null);
  }

  /** Returns a copy of this step transitioned to COMPENSATING. */
  public SagaStep asCompensating(Instant now) {
    return new SagaStep(
        stepId,
        adapter,
        operation,
        SagaStepStatus.COMPENSATING,
        result,
        compensationData,
        error,
        now,
        null);
  }

  /** Returns a copy of this step transitioned to COMPENSATED. */
  public SagaStep asCompensated(Instant now) {
    return new SagaStep(
        stepId,
        adapter,
        operation,
        SagaStepStatus.COMPENSATED,
        result,
        compensationData,
        null,
        startedAt,
        now);
  }

  /** Returns a copy of this step transitioned to COMPENSATION_FAILED. */
  public SagaStep asCompensationFailed(String errorMessage, Instant now) {
    return new SagaStep(
        stepId,
        adapter,
        operation,
        SagaStepStatus.COMPENSATION_FAILED,
        result,
        compensationData,
        errorMessage,
        startedAt,
        now);
  }
}
