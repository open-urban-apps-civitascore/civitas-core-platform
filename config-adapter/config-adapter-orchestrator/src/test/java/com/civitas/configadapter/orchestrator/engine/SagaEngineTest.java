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
package com.civitas.configadapter.orchestrator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.civitas.configadapter.model.saga.SagaType;

/**
 * Integration tests for {@link SagaEngine}. Uses {@link InMemorySagaStateStore} and a collecting
 * test dispatcher — no Kafka, no mocks.
 */
class SagaEngineTest {

  private static final String DATASET_ID = "ds-001";

  private static final Predicate<Map<String, Object>> HAS_PIPELINES =
      payload -> {
        Object pipelines = payload.get("dataPipelines");
        return pipelines instanceof List<?> list && !list.isEmpty();
      };

  private InMemorySagaStateStore stateStore;
  private CollectingDispatcher dispatcher;
  private SagaEngine engine;

  @BeforeEach
  void setUp() {
    stateStore = new InMemorySagaStateStore();
    dispatcher = new CollectingDispatcher();
    var stateMachine = new SagaStateMachine(HAS_PIPELINES);
    engine = new SagaEngine(stateMachine, stateStore, dispatcher);
  }

  private Map<String, Object> triggerWithPipelines() {
    return Map.of(
        "id", DATASET_ID,
        "name", "Test Dataset",
        "openDataAccess", true,
        "dataPipelines", List.of(Map.of("id", "pl-001", "action", "ADD")));
  }

  private Map<String, Object> triggerWithoutPipelines() {
    return Map.of(
        "id", DATASET_ID,
        "name", "Test Dataset",
        "openDataAccess", true,
        "dataPipelines", List.of());
  }

  private Map<String, Object> deleteTrigger() {
    return Map.of(
        "id", DATASET_ID,
        "dataPipelines", List.of(Map.of("id", "pl-001")),
        "pipelineIds", List.of("pl-001"));
  }

  // ─── Happy Path ─────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Happy Path")
  class HappyPath {

    @Test
    @DisplayName("Create saga: 3 steps → COMPLETED, state store cleaned up")
    void startSaga_createWith3Steps_shouldComplete() {
      var ctx = engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithPipelines());
      assertTrue(ctx.isPresent());
      String sagaId = ctx.get().sagaId();

      // State persisted
      assertTrue(stateStore.findById(sagaId).isPresent());

      // ExecuteStep dispatched for create-project
      var exec = dispatcher.lastOfType(SagaAction.ExecuteStep.class);
      assertNotNull(exec);
      assertEquals("create-project", exec.stepId());

      // FROST succeeds
      dispatcher.clear();
      engine.handleStepCompleted(sagaId, "create-project",
          Map.of("projectId", "proj-123", "baseUrl", "http://frost/proj-123"),
          Map.of("projectId", "proj-123"));

      exec = dispatcher.lastOfType(SagaAction.ExecuteStep.class);
      assertEquals("create-route", exec.stepId());

      // APISIX succeeds
      dispatcher.clear();
      engine.handleStepCompleted(sagaId, "create-route",
          Map.of("routeId", "r-456"), Map.of("routeId", "r-456"));

      exec = dispatcher.lastOfType(SagaAction.ExecuteStep.class);
      assertEquals("deploy-pipelines", exec.stepId());

      // Redpanda succeeds
      dispatcher.clear();
      engine.handleStepCompleted(sagaId, "deploy-pipelines",
          Map.of("pipelineIds", List.of("pl-001")), Map.of());

      var complete = dispatcher.lastOfType(SagaAction.CompleteSaga.class);
      assertNotNull(complete);
      assertEquals(sagaId, complete.sagaId());

      // State store cleaned up (terminal saga removed)
      assertFalse(stateStore.findById(sagaId).isPresent());
    }

    @Test
    @DisplayName("Create saga without pipelines: 2 steps → COMPLETED")
    void startSaga_createWithoutPipelines_shouldComplete() {
      var ctx = engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithoutPipelines());
      String sagaId = ctx.get().sagaId();

      engine.handleStepCompleted(sagaId, "create-project",
          Map.of("projectId", "proj-123"), Map.of());
      engine.handleStepCompleted(sagaId, "create-route",
          Map.of("routeId", "r-456"), Map.of());

      assertNotNull(dispatcher.lastOfType(SagaAction.CompleteSaga.class));
      assertFalse(stateStore.findById(sagaId).isPresent());
    }

    @Test
    @DisplayName("Delete saga: 3 steps → COMPLETED")
    void startSaga_deleteWith3Steps_shouldComplete() {
      var ctx = engine.startSaga(SagaType.DATASET_DELETE, DATASET_ID, deleteTrigger());
      String sagaId = ctx.get().sagaId();

      engine.handleStepCompleted(sagaId, "delete-pipelines", Map.of(), Map.of());
      engine.handleStepCompleted(sagaId, "delete-route", Map.of(), Map.of());
      engine.handleStepCompleted(sagaId, "delete-project", Map.of(), Map.of());

      assertNotNull(dispatcher.lastOfType(SagaAction.CompleteSaga.class));
      assertFalse(stateStore.findById(sagaId).isPresent());
    }
  }

  // ─── Failure + Compensation ─────────────────────────────────────────────

  @Nested
  @DisplayName("Failure + Compensation")
  class FailureCompensation {

    @Test
    @DisplayName("Create step 2 fails → compensate FROST → COMPENSATED, store cleaned up")
    void handleStepFailed_createStep2Fails_shouldCompensateFrost() {
      var ctx = engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithPipelines());
      String sagaId = ctx.get().sagaId();

      engine.handleStepCompleted(sagaId, "create-project",
          Map.of("projectId", "proj-123"), Map.of("projectId", "proj-123"));

      dispatcher.clear();
      engine.handleStepFailed(sagaId, "create-route", "Connection refused");

      // Compensation dispatched
      var comp = dispatcher.lastOfType(SagaAction.CompensateStep.class);
      assertNotNull(comp);
      assertEquals("create-project", comp.stepId());

      // Compensation succeeds
      dispatcher.clear();
      engine.handleCompensationCompleted(sagaId, "create-project");

      var fail = dispatcher.lastOfType(SagaAction.FailSaga.class);
      assertNotNull(fail);
      assertTrue(fail.compensated());

      // Store cleaned up
      assertFalse(stateStore.findById(sagaId).isPresent());
    }

    @Test
    @DisplayName("Create step 3 fails, compensation also fails → COMPENSATION_FAILED, manual intervention")
    void handleCompensationFailed_createStep3Fails_shouldReportCompensationFailed() {
      var ctx = engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithPipelines());
      String sagaId = ctx.get().sagaId();

      engine.handleStepCompleted(sagaId, "create-project",
          Map.of("projectId", "proj-123"), Map.of("projectId", "proj-123"));
      engine.handleStepCompleted(sagaId, "create-route",
          Map.of("routeId", "r-456"), Map.of("routeId", "r-456"));

      engine.handleStepFailed(sagaId, "deploy-pipelines", "Error");

      // APISIX compensation fails
      engine.handleCompensationFailed(sagaId, "create-route", "APISIX unreachable");

      // FROST compensation succeeds
      dispatcher.clear();
      engine.handleCompensationCompleted(sagaId, "create-project");

      var fail = dispatcher.lastOfType(SagaAction.FailSaga.class);
      assertNotNull(fail);
      assertFalse(fail.compensated());

      assertNotNull(dispatcher.lastOfType(SagaAction.PublishManualIntervention.class));
      assertFalse(stateStore.findById(sagaId).isPresent());
    }
  }

  // ─── Delete Best-Effort ─────────────────────────────────────────────────

  @Nested
  @DisplayName("Delete Best-Effort")
  class DeleteBestEffort {

    @Test
    @DisplayName("Delete step 1 fails → continues with steps 2+3 → partial FAILED")
    void handleStepFailed_deleteStep1Fails_shouldContinueBestEffort() {
      var ctx = engine.startSaga(SagaType.DATASET_DELETE, DATASET_ID, deleteTrigger());
      String sagaId = ctx.get().sagaId();

      engine.handleStepFailed(sagaId, "delete-pipelines", "RP down");
      engine.handleStepCompleted(sagaId, "delete-route", Map.of(), Map.of());

      dispatcher.clear();
      engine.handleStepCompleted(sagaId, "delete-project", Map.of(), Map.of());

      var fail = dispatcher.lastOfType(SagaAction.FailSaga.class);
      assertNotNull(fail);
      assertEquals(1, fail.staleResources().size());
      assertEquals("redpanda", fail.staleResources().get(0).adapter());

      assertFalse(stateStore.findById(sagaId).isPresent());
    }

    @Test
    @DisplayName("Delete all steps fail → FAILED, all stale")
    void handleStepFailed_deleteAllFail_shouldReportAllStale() {
      var ctx = engine.startSaga(SagaType.DATASET_DELETE, DATASET_ID, deleteTrigger());
      String sagaId = ctx.get().sagaId();

      engine.handleStepFailed(sagaId, "delete-pipelines", "Error");
      engine.handleStepFailed(sagaId, "delete-route", "Error");

      dispatcher.clear();
      engine.handleStepFailed(sagaId, "delete-project", "Error");

      var fail = dispatcher.lastOfType(SagaAction.FailSaga.class);
      assertEquals(3, fail.staleResources().size());
    }
  }

  // ─── Timeout ────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Timeout")
  class Timeout {

    @Test
    @DisplayName("Create step 2 timeout → compensate FROST")
    void handleStepTimeout_createStep2_shouldCompensateFrost() {
      var ctx = engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithPipelines());
      String sagaId = ctx.get().sagaId();

      engine.handleStepCompleted(sagaId, "create-project",
          Map.of("projectId", "proj-123"), Map.of("projectId", "proj-123"));

      dispatcher.clear();
      engine.handleStepTimeout(sagaId, "create-route");

      var comp = dispatcher.lastOfType(SagaAction.CompensateStep.class);
      assertNotNull(comp);
      assertEquals("create-project", comp.stepId());
    }

    @Test
    @DisplayName("Delete step 1 timeout → continues with next step")
    void handleStepTimeout_deleteStep1_shouldContinueNextStep() {
      var ctx = engine.startSaga(SagaType.DATASET_DELETE, DATASET_ID, deleteTrigger());
      String sagaId = ctx.get().sagaId();

      dispatcher.clear();
      engine.handleStepTimeout(sagaId, "delete-pipelines");

      var exec = dispatcher.lastOfType(SagaAction.ExecuteStep.class);
      assertNotNull(exec);
      assertEquals("delete-route", exec.stepId());
    }
  }

  // ─── Duplicate Rejection ────────────────────────────────────────────────

  @Nested
  @DisplayName("Duplicate Rejection")
  class DuplicateRejection {

    @Test
    @DisplayName("Second saga for same dataset is rejected while first is active")
    void startSaga_duplicateDataset_shouldRejectSecond() {
      var first = engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithPipelines());
      assertTrue(first.isPresent());

      var second = engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithPipelines());
      assertTrue(second.isEmpty());
    }

    @Test
    @DisplayName("New saga allowed after previous saga completes")
    void startSaga_afterPreviousCompletes_shouldAllowNew() {
      var first = engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithoutPipelines());
      String sagaId = first.get().sagaId();

      engine.handleStepCompleted(sagaId, "create-project", Map.of("projectId", "p1"), Map.of());
      engine.handleStepCompleted(sagaId, "create-route", Map.of("routeId", "r1"), Map.of());

      // First saga completed and removed → new one allowed
      var second = engine.startSaga(SagaType.DATASET_UPDATE, DATASET_ID, triggerWithoutPipelines());
      assertTrue(second.isPresent());
    }
  }

  // ─── Unknown Saga ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("Unknown Saga Handling")
  class UnknownSaga {

    @Test
    @DisplayName("Event for unknown sagaId is silently ignored")
    void handleStepCompleted_unknownSagaId_shouldIgnore() {
      int dispatchedBefore = dispatcher.allActions().size();
      engine.handleStepCompleted("nonexistent-saga", "some-step", Map.of(), Map.of());
      assertEquals(dispatchedBefore, dispatcher.allActions().size());
    }

    @Test
    @DisplayName("Timeout for unknown sagaId is silently ignored")
    void handleStepTimeout_unknownSagaId_shouldIgnore() {
      int dispatchedBefore = dispatcher.allActions().size();
      engine.handleStepTimeout("nonexistent-saga", "some-step");
      assertEquals(dispatchedBefore, dispatcher.allActions().size());
    }
  }

  // ─── Recovery ───────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Startup Recovery")
  class Recovery {

    @Test
    @DisplayName("recoverActiveSagas returns count of in-flight sagas")
    void recoverActiveSagas_withOneActive_shouldReturnOne() {
      // Start two sagas, complete one
      engine.startSaga(SagaType.DATASET_CREATE, "ds-001", triggerWithoutPipelines());
      var second = engine.startSaga(SagaType.DATASET_CREATE, "ds-002",
          Map.of("id", "ds-002", "name", "Second", "openDataAccess", false, "dataPipelines", List.of()));
      String secondId = second.get().sagaId();

      engine.handleStepCompleted(secondId, "create-project", Map.of("projectId", "p2"), Map.of());
      engine.handleStepCompleted(secondId, "create-route", Map.of("routeId", "r2"), Map.of());
      // ds-002 completed and removed

      assertEquals(1, engine.recoverActiveSagas());
    }
  }

  // ─── Test Double ────────────────────────────────────────────────────────

  /** Test dispatcher that collects all dispatched actions for assertions. */
  static class CollectingDispatcher implements SagaActionDispatcher {

    private final List<SagaAction> actions = new ArrayList<>();

    @Override
    public void dispatch(SagaAction action) {
      actions.add(action);
    }

    List<SagaAction> allActions() {
      return List.copyOf(actions);
    }

    <T extends SagaAction> T lastOfType(Class<T> type) {
      for (int i = actions.size() - 1; i >= 0; i--) {
        if (type.isInstance(actions.get(i))) {
          return type.cast(actions.get(i));
        }
      }
      return null;
    }

    void clear() {
      actions.clear();
    }
  }
}
