/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.orchestrator.engine;

import com.civitas.configadapter.model.saga.SagaContext;
import com.civitas.configadapter.model.saga.SagaType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facade that orchestrates the saga lifecycle by combining the pure {@link SagaStateMachine} with
 * stateful components ({@link SagaStateStore}, {@link SagaActionDispatcher}).
 *
 * <p>This class is Kafka-free and fully testable with {@link InMemorySagaStateStore} and a test
 * dispatcher.
 */
public class SagaEngine {

  private static final Logger LOG = LoggerFactory.getLogger(SagaEngine.class);

  private final SagaStateMachine stateMachine;
  private final SagaStateStore stateStore;
  private final SagaActionDispatcher dispatcher;

  public SagaEngine(
      SagaStateMachine stateMachine, SagaStateStore stateStore, SagaActionDispatcher dispatcher) {
    this.stateMachine = stateMachine;
    this.stateStore = stateStore;
    this.dispatcher = dispatcher;
  }

  /**
   * Start a new saga for the given type and trigger payload.
   *
   * @return the created SagaContext, or empty if a saga is already active for this dataset
   */
  public Optional<SagaContext> startSaga(
      SagaType sagaType, String datasetId, Map<String, Object> triggerPayload) {

    if (stateStore.existsForDataset(datasetId)) {
      LOG.warn(
          "Rejecting duplicate saga for dataset {}: active saga already exists",
          Encode.forJava(datasetId));
      return Optional.empty();
    }

    SagaDefinition definition = SagaDefinitions.forType(sagaType);
    SagaTransitionResult result =
        stateMachine.startSaga(definition, datasetId, triggerPayload, Instant.now());

    processActions(result);
    return Optional.of(result.context());
  }

  /**
   * Handle an adapter reporting step success.
   *
   * @param sagaId the saga this result belongs to
   * @param stepId the step that completed
   * @param resultData adapter-specific output (e.g. projectId, baseUrl)
   * @param compensationData data needed to undo this step
   */
  public void handleStepCompleted(
      String sagaId,
      String stepId,
      Map<String, Object> resultData,
      Map<String, Object> compensationData) {

    SagaContext context = loadContext(sagaId);
    if (context == null) {
      return;
    }

    SagaDefinition definition = SagaDefinitions.forType(context.sagaType());
    SagaTransitionResult result =
        stateMachine.handleStepCompleted(
            context, definition, stepId, resultData, compensationData, Instant.now());

    processActions(result);
  }

  /**
   * Handle an adapter reporting step failure.
   *
   * @param sagaId the saga this result belongs to
   * @param stepId the step that failed
   * @param error error description
   */
  public void handleStepFailed(String sagaId, String stepId, String error) {

    SagaContext context = loadContext(sagaId);
    if (context == null) {
      return;
    }

    SagaDefinition definition = SagaDefinitions.forType(context.sagaType());
    SagaTransitionResult result =
        stateMachine.handleStepFailed(context, definition, stepId, error, Instant.now());

    processActions(result);
  }

  /**
   * Handle a step timeout (adapter never responded within the configured timeout).
   *
   * @param sagaId the saga this timeout belongs to
   * @param stepId the step that timed out
   */
  public void handleStepTimeout(String sagaId, String stepId) {

    SagaContext context = loadContext(sagaId);
    if (context == null) {
      return;
    }

    SagaDefinition definition = SagaDefinitions.forType(context.sagaType());
    SagaTransitionResult result =
        stateMachine.handleStepTimeout(context, definition, stepId, Instant.now());

    processActions(result);
  }

  /**
   * Handle an adapter reporting compensation success.
   *
   * @param sagaId the saga this result belongs to
   * @param stepId the step whose compensation completed
   */
  public void handleCompensationCompleted(String sagaId, String stepId) {

    SagaContext context = loadContext(sagaId);
    if (context == null) {
      return;
    }

    SagaDefinition definition = SagaDefinitions.forType(context.sagaType());
    SagaTransitionResult result =
        stateMachine.handleCompensationCompleted(context, definition, stepId, Instant.now());

    processActions(result);
  }

  /**
   * Handle an adapter reporting compensation failure.
   *
   * @param sagaId the saga this result belongs to
   * @param stepId the step whose compensation failed
   * @param error error description
   */
  public void handleCompensationFailed(String sagaId, String stepId, String error) {

    SagaContext context = loadContext(sagaId);
    if (context == null) {
      return;
    }

    SagaDefinition definition = SagaDefinitions.forType(context.sagaType());
    SagaTransitionResult result =
        stateMachine.handleCompensationFailed(context, definition, stepId, error, Instant.now());

    processActions(result);
  }

  /**
   * Recover active sagas from the state store on startup. Resumes in-flight sagas that were
   * interrupted by a crash.
   *
   * @return number of recovered sagas
   */
  public int recoverActiveSagas() {
    Map<String, SagaContext> activeSagas = stateStore.findActiveSagas();
    LOG.info("Recovered {} active sagas from state store", activeSagas.size());
    return activeSagas.size();
  }

  /** Load a saga context from the state store, logging a warning if not found. */
  private SagaContext loadContext(String sagaId) {
    Optional<SagaContext> opt = stateStore.findById(sagaId);
    if (opt.isEmpty()) {
      LOG.warn("Received event for unknown saga {}, ignoring", Encode.forJava(sagaId));
      return null;
    }
    return opt.get();
  }

  /**
   * Process the actions returned by the state machine. PersistState is handled here via the state
   * store; all other actions are forwarded to the dispatcher. Terminal sagas are removed from the
   * store.
   */
  private void processActions(SagaTransitionResult result) {
    for (SagaAction action : result.actions()) {
      if (action instanceof SagaAction.PersistState persist) {
        stateStore.save(persist.sagaContext());
      } else {
        dispatcher.dispatch(action);
      }
    }

    // If saga reached a terminal state, clean up the store
    if (result.context().status().isTerminal()) {
      stateStore.remove(result.context().sagaId());
    }
  }
}
