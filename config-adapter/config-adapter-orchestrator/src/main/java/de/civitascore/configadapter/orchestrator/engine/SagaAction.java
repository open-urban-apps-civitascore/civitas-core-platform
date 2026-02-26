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

import de.civitascore.configadapter.model.saga.SagaContext;
import java.util.List;
import java.util.Map;

/**
 * Actions produced by the {@link SagaStateMachine}. The state machine returns a list of actions
 * that the engine must dispatch (send commands, persist state, publish results). This keeps the
 * state machine pure — it computes what to do, the engine does it.
 */
public sealed interface SagaAction {

  /** Persist the saga state before proceeding with other actions. Always emitted first. */
  record PersistState(SagaContext sagaContext) implements SagaAction {}

  /** Send a forward execution command to an adapter. */
  record ExecuteStep(
      String stepId, String adapter, String operation, String topic, Map<String, Object> payload)
      implements SagaAction {}

  /** Send a compensation command to an adapter. */
  record CompensateStep(
      String stepId, String adapter, String operation, String topic, Map<String, Object> payload)
      implements SagaAction {}

  /** Skip a conditional step (e.g. Redpanda when no pipelines). */
  record SkipStep(String stepId, String reason) implements SagaAction {}

  /** Saga completed successfully. Publish result to backend. */
  record CompleteSaga(String sagaId, Map<String, Object> resultPayload) implements SagaAction {}

  /** Saga failed. Publish failure result to backend with cleanup details. */
  record FailSaga(
      String sagaId,
      String datasetId,
      String failedStep,
      String error,
      boolean compensated,
      List<StaleResource> staleResources,
      List<CleanedResource> cleanedResources)
      implements SagaAction {}

  /** Publish to manual-intervention topic when compensation failed. */
  record PublishManualIntervention(String sagaId, SagaContext sagaContext) implements SagaAction {}

  /** A resource that could not be cleaned up (compensation or delete failed). */
  record StaleResource(String adapter, String resourceId, String error) {}

  /** A resource that was successfully cleaned up. */
  record CleanedResource(String adapter, String resourceId) {}
}
