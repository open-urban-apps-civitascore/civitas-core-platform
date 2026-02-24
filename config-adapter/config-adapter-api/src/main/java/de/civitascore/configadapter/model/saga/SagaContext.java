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
import de.civitascore.configadapter.util.PayloadConverter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Full saga context maintained by the orchestrator. This is the single source of truth for a saga's
 * current state.
 *
 * <p>Immutable — all mutation methods return a new instance. Serialized to the Kafka compacted
 * state topic for crash recovery.
 *
 * @param sagaId unique saga identifier
 * @param sagaType the workflow type (DATASET_CREATE, DATASET_UPDATE, DATASET_DELETE)
 * @param datasetId the dataset this saga operates on (used for duplicate detection)
 * @param currentStepId the step currently being executed or compensated
 * @param status overall saga status
 * @param steps ordered list of steps with their individual statuses and results
 * @param failure captures where and why the saga failed (null if no failure)
 * @param triggerPayload the original trigger event payload (preserved for command building)
 * @param createdAt when the saga was created
 * @param updatedAt last state transition timestamp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaContext(
    String sagaId,
    SagaType sagaType,
    String datasetId,
    String currentStepId,
    SagaStatus status,
    List<SagaStep> steps,
    SagaFailure failure,
    Map<String, Object> triggerPayload,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * Converts the trigger payload map to a typed object. Useful for type-safe access to well-known
   * payload structures like {@link de.civitascore.configadapter.model.dataset.Dataset}.
   *
   * <p>Example: {@code context.triggerPayloadAs(Dataset.class).name()}
   *
   * @param <T> the target type
   * @param type the target class
   * @return the converted payload
   */
  public <T> T triggerPayloadAs(Class<T> type) {
    return PayloadConverter.fromValue(triggerPayload, type);
  }

  /** Returns a copy with the given status and updated timestamp. */
  public SagaContext withStatus(SagaStatus newStatus, Instant now) {
    return new SagaContext(
        sagaId,
        sagaType,
        datasetId,
        currentStepId,
        newStatus,
        steps,
        failure,
        triggerPayload,
        createdAt,
        now);
  }

  /** Returns a copy with the given current step ID. */
  public SagaContext withCurrentStep(String stepId, Instant now) {
    return new SagaContext(
        sagaId,
        sagaType,
        datasetId,
        stepId,
        status,
        steps,
        failure,
        triggerPayload,
        createdAt,
        now);
  }

  /** Returns a copy with the given failure information. */
  public SagaContext withFailure(SagaFailure sagaFailure, Instant now) {
    return new SagaContext(
        sagaId,
        sagaType,
        datasetId,
        currentStepId,
        status,
        steps,
        sagaFailure,
        triggerPayload,
        createdAt,
        now);
  }

  /** Returns a copy with the step at the given index replaced. */
  public SagaContext withStepReplaced(int index, SagaStep newStep, Instant now) {
    var newSteps = new ArrayList<>(steps);
    newSteps.set(index, newStep);
    return new SagaContext(
        sagaId,
        sagaType,
        datasetId,
        currentStepId,
        status,
        List.copyOf(newSteps),
        failure,
        triggerPayload,
        createdAt,
        now);
  }
}
