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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.model.saga.SagaContext;
import com.civitas.configadapter.model.saga.SagaStatus;
import com.civitas.configadapter.model.saga.SagaStepStatus;
import com.civitas.configadapter.model.saga.SagaType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for {@link SagaStateMachine}. Covers all combinations of success, failure,
 * and timeout at each step for each saga type (Create, Update, Delete), including compensation
 * flows, best-effort delete, and edge cases.
 *
 * <p>Test structure mirrors the scenarios from SAGA-DATASET-USE-CASES.md Sections 6–8.
 */
class SagaStateMachineTest {

  private static final String DATASET_ID = "ds-001";
  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  private static final Map<String, Object> FROST_RESULT =
      Map.of("projectId", "proj-123", "baseUrl", "http://frost/v1.1/projects/proj-123");
  private static final Map<String, Object> FROST_COMP_DATA = Map.of("projectId", "proj-123");
  private static final Map<String, Object> APISIX_RESULT =
      Map.of("routeId", "r-456", "serviceId", "svc-frost");
  private static final Map<String, Object> APISIX_COMP_DATA = Map.of("routeId", "r-456");
  private static final Map<String, Object> REDPANDA_RESULT =
      Map.of("pipelineIds", List.of("pl-001"));
  private static final Map<String, Object> REDPANDA_COMP_DATA =
      Map.of("pipelineIds", List.of("pl-001"));
  private static final Map<String, Object> EMPTY_RESULT = Map.of();
  private static final Map<String, Object> EMPTY_COMP = Map.of();

  private static final Predicate<Map<String, Object>> HAS_PIPELINES =
      payload -> {
        Object pipelines = payload.get("dataPipelines");
        return pipelines instanceof List<?> list && !list.isEmpty();
      };

  private SagaStateMachine sm;

  @BeforeEach
  void setUp() {
    sm = new SagaStateMachine(HAS_PIPELINES);
  }

  // ─── Trigger Payloads ─────────────────────────────────────────────────────

  private Map<String, Object> triggerWithPipelines() {
    return Map.of(
        "id",
        DATASET_ID,
        "name",
        "Test Dataset",
        "openDataAccess",
        true,
        "dataPipelines",
        List.of(Map.of("id", "pl-001", "action", "ADD")));
  }

  private Map<String, Object> triggerWithoutPipelines() {
    return Map.of(
        "id",
        DATASET_ID,
        "name",
        "Test Dataset",
        "openDataAccess",
        true,
        "dataPipelines",
        List.of());
  }

  private Map<String, Object> deleteTriggerWithPipelines() {
    return Map.of(
        "id", DATASET_ID,
        "dataPipelines", List.of(Map.of("id", "pl-001")),
        "pipelineIds", List.of("pl-001"));
  }

  private Map<String, Object> deleteTriggerWithoutPipelines() {
    return Map.of("id", DATASET_ID, "dataPipelines", List.of());
  }

  // ─── Action Helpers ───────────────────────────────────────────────────────

  private <T extends SagaAction> T findAction(List<SagaAction> actions, Class<T> type) {
    return actions.stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
  }

  private SagaStepStatus stepStatus(SagaContext ctx, String stepId) {
    return ctx.steps().stream()
        .filter(s -> s.stepId().equals(stepId))
        .findFirst()
        .orElseThrow()
        .status();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Section 6: Dataset Create
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("6. Dataset Create")
  class DatasetCreate {

    private final SagaDefinition def = SagaDefinitions.forType(SagaType.DATASET_CREATE);

    // ─── 6.1–6.3: Success Scenarios ───────────────────────────────────────

    @Test
    @DisplayName("6.1: Success with pipelines — 3 steps → COMPLETED")
    void startSaga_createWithPipelines_shouldCompleteAllSteps() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      assertEquals(SagaStatus.EXECUTING, r.context().status());
      assertEquals("create-project", r.context().currentStepId());
      assertNotNull(findAction(r.actions(), SagaAction.ExecuteStep.class));

      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      assertEquals(SagaStatus.EXECUTING, r.context().status());
      assertEquals("create-route", r.context().currentStepId());

      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);
      assertEquals(SagaStatus.EXECUTING, r.context().status());
      assertEquals("deploy-pipelines", r.context().currentStepId());

      r =
          sm.handleStepCompleted(
              r.context(), def, "deploy-pipelines", REDPANDA_RESULT, REDPANDA_COMP_DATA, NOW);
      assertEquals(SagaStatus.COMPLETED, r.context().status());
      assertNotNull(findAction(r.actions(), SagaAction.CompleteSaga.class));

      // Verify all step statuses
      assertEquals(SagaStepStatus.SUCCESS, stepStatus(r.context(), "create-project"));
      assertEquals(SagaStepStatus.SUCCESS, stepStatus(r.context(), "create-route"));
      assertEquals(SagaStepStatus.SUCCESS, stepStatus(r.context(), "deploy-pipelines"));
    }

    @Test
    @DisplayName("6.2: Success without pipelines — 2 steps, Redpanda SKIPPED → COMPLETED")
    void startSaga_createWithoutPipelines_shouldSkipRedpanda() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithoutPipelines(), NOW);
      assertEquals("create-project", r.context().currentStepId());

      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      assertEquals("create-route", r.context().currentStepId());

      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);
      assertEquals(SagaStatus.COMPLETED, r.context().status());

      assertEquals(SagaStepStatus.SUCCESS, stepStatus(r.context(), "create-project"));
      assertEquals(SagaStepStatus.SUCCESS, stepStatus(r.context(), "create-route"));
      assertEquals(SagaStepStatus.SKIPPED, stepStatus(r.context(), "deploy-pipelines"));

      assertNotNull(findAction(r.actions(), SagaAction.CompleteSaga.class));
      assertNotNull(findAction(r.actions(), SagaAction.SkipStep.class));
    }

    // ─── 6.4–6.6: Failure at Each Step ────────────────────────────────────

    @Test
    @DisplayName("6.4: Failure at step 1 (FROST) — no completed steps → no compensation needed")
    void handleStepFailed_createStep1_shouldTerminateWithoutCompensation() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r = sm.handleStepFailed(r.context(), def, "create-project", "FROST unavailable", NOW);

      assertTrue(r.context().status().isTerminal());
      assertNotNull(r.context().failure());
      assertEquals("create-project", r.context().failure().stepId());
      assertEquals("FROST unavailable", r.context().failure().error());
      assertEquals(SagaStepStatus.FAILED, stepStatus(r.context(), "create-project"));
    }

    @Test
    @DisplayName("6.5: Failure at step 2 (APISIX) — compensate FROST → COMPENSATED")
    void handleStepFailed_createStep2_shouldCompensateFrost() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "create-route", "Connection refused", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());
      assertEquals(SagaStepStatus.FAILED, stepStatus(r.context(), "create-route"));

      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertNotNull(comp);
      assertEquals("create-project", comp.stepId());
      assertEquals("DELETE_PROJECT", comp.operation());

      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertEquals(SagaStatus.COMPENSATED, r.context().status());
      assertEquals(SagaStepStatus.COMPENSATED, stepStatus(r.context(), "create-project"));

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertNotNull(fail);
      assertTrue(fail.compensated());
      assertEquals("create-route", fail.failedStep());
    }

    @Test
    @DisplayName("6.6: Failure at step 3 (Redpanda) — compensate APISIX → FROST → COMPENSATED")
    void handleStepFailed_createStep3_shouldCompensateApisixThenFrost() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);
      r =
          sm.handleStepFailed(
              r.context(), def, "deploy-pipelines", "Schema registry unavailable", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      // First: compensate APISIX (last SUCCESS in reverse order)
      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("create-route", comp.stepId());
      assertEquals("DELETE_ROUTE", comp.operation());

      r = sm.handleCompensationCompleted(r.context(), def, "create-route", NOW);
      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      // Then: compensate FROST
      comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("create-project", comp.stepId());
      assertEquals("DELETE_PROJECT", comp.operation());

      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertEquals(SagaStatus.COMPENSATED, r.context().status());

      assertEquals(SagaStepStatus.COMPENSATED, stepStatus(r.context(), "create-project"));
      assertEquals(SagaStepStatus.COMPENSATED, stepStatus(r.context(), "create-route"));
      assertEquals(SagaStepStatus.FAILED, stepStatus(r.context(), "deploy-pipelines"));
    }

    // ─── 6.7: Compensation Failure Combinations ──────────────────────────

    @Test
    @DisplayName(
        "6.7a: Compensation failure — APISIX comp fails, FROST comp succeeds → COMPENSATION_FAILED")
    void handleCompensationFailed_apisixFails_shouldReportStaleApisixCleanedFrost() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "deploy-pipelines", "Error", NOW);

      // Compensate APISIX → FAILS
      r = sm.handleCompensationFailed(r.context(), def, "create-route", "APISIX unreachable", NOW);
      assertEquals(SagaStatus.COMPENSATING, r.context().status());
      assertEquals(SagaStepStatus.COMPENSATION_FAILED, stepStatus(r.context(), "create-route"));

      // Continue: compensate FROST → succeeds (best-effort)
      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertNotNull(comp, "Should continue compensating remaining steps after failure");
      assertEquals("create-project", comp.stepId());

      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertEquals(SagaStatus.COMPENSATION_FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertFalse(fail.compensated());
      assertEquals(1, fail.staleResources().size());
      assertEquals("apisix", fail.staleResources().get(0).adapter());
      assertEquals(1, fail.cleanedResources().size());
      assertEquals("frost", fail.cleanedResources().get(0).adapter());

      assertNotNull(findAction(r.actions(), SagaAction.PublishManualIntervention.class));
    }

    @Test
    @DisplayName(
        "6.7b: Compensation failure — APISIX comp succeeds, FROST comp fails → COMPENSATION_FAILED")
    void handleCompensationFailed_frostFails_shouldReportStaleFrostCleanedApisix() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "deploy-pipelines", "Error", NOW);

      // Compensate APISIX → succeeds
      r = sm.handleCompensationCompleted(r.context(), def, "create-route", NOW);
      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      // Compensate FROST → FAILS
      r = sm.handleCompensationFailed(r.context(), def, "create-project", "FROST unreachable", NOW);
      assertEquals(SagaStatus.COMPENSATION_FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertFalse(fail.compensated());
      assertEquals(1, fail.staleResources().size());
      assertEquals("frost", fail.staleResources().get(0).adapter());
      assertEquals(1, fail.cleanedResources().size());
      assertEquals("apisix", fail.cleanedResources().get(0).adapter());
    }

    @Test
    @DisplayName("6.7c: All compensations fail → COMPENSATION_FAILED, both stale")
    void handleCompensationFailed_allFail_shouldReportAllStale() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "deploy-pipelines", "Error", NOW);

      r = sm.handleCompensationFailed(r.context(), def, "create-route", "APISIX down", NOW);
      r = sm.handleCompensationFailed(r.context(), def, "create-project", "FROST down", NOW);

      assertEquals(SagaStatus.COMPENSATION_FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(2, fail.staleResources().size());
      assertEquals(0, fail.cleanedResources().size());
    }

    @Test
    @DisplayName("6.7d: Step 2 fails, single compensation (FROST) fails → COMPENSATION_FAILED")
    void handleCompensationFailed_step2FailsSingleComp_shouldReportStaleFrost() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "create-route", "Error", NOW);

      // Only FROST to compensate, and it fails
      r = sm.handleCompensationFailed(r.context(), def, "create-project", "FROST unreachable", NOW);

      assertEquals(SagaStatus.COMPENSATION_FAILED, r.context().status());
      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(1, fail.staleResources().size());
      assertEquals("frost", fail.staleResources().get(0).adapter());
    }

    // ─── 6.8: Timeout at Each Step ────────────────────────────────────────

    @Test
    @DisplayName("6.8a: Timeout at step 1 (FROST) — no compensation needed")
    void handleStepTimeout_createStep1_shouldTerminateWithoutCompensation() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r = sm.handleStepTimeout(r.context(), def, "create-project", NOW);

      assertTrue(r.context().status().isTerminal());
      assertEquals("create-project", r.context().failure().stepId());
      assertTrue(r.context().failure().error().contains("Timeout"));
      assertEquals(SagaStepStatus.FAILED, stepStatus(r.context(), "create-project"));
    }

    @Test
    @DisplayName("6.8b: Timeout at step 2 (APISIX) — compensate FROST")
    void handleStepTimeout_createStep2_shouldCompensateFrost() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepTimeout(r.context(), def, "create-route", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());
      assertTrue(r.context().failure().error().contains("Timeout"));
      assertEquals("create-route", r.context().failure().stepId());

      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("create-project", comp.stepId());

      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertEquals(SagaStatus.COMPENSATED, r.context().status());
    }

    @Test
    @DisplayName("6.8c: Timeout at step 3 (Redpanda) — compensate APISIX → FROST")
    void handleStepTimeout_createStep3_shouldCompensateApisixThenFrost() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);
      r = sm.handleStepTimeout(r.context(), def, "deploy-pipelines", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());
      assertEquals("deploy-pipelines", r.context().failure().stepId());

      // Compensate APISIX
      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("create-route", comp.stepId());
      r = sm.handleCompensationCompleted(r.context(), def, "create-route", NOW);

      // Compensate FROST
      comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("create-project", comp.stepId());
      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);

      assertEquals(SagaStatus.COMPENSATED, r.context().status());
    }

    // ─── Timeout during Compensation ──────────────────────────────────────

    @Test
    @DisplayName("6.8d: Timeout during APISIX compensation — best-effort, continue to FROST")
    void handleCompensationFailed_timeout_shouldContinueBestEffort() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "deploy-pipelines", "Error", NOW);

      // APISIX compensation times out (treated as compensation failure)
      r =
          sm.handleCompensationFailed(
              r.context(), def, "create-route", "Compensation timeout", NOW);
      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      // Should still try FROST compensation
      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertNotNull(comp);
      assertEquals("create-project", comp.stepId());

      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertEquals(SagaStatus.COMPENSATION_FAILED, r.context().status());
    }

    // ─── Without Pipelines: Failure/Timeout ───────────────────────────────

    @Test
    @DisplayName("Create without pipelines: failure at step 2 → compensate FROST only")
    void handleStepFailed_noPipelinesStep2_shouldCompensateFrostOnly() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithoutPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "create-route", "Error", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("create-project", comp.stepId());

      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertEquals(SagaStatus.COMPENSATED, r.context().status());
      // Redpanda step was never reached — marked SKIPPED when entering compensation
      assertEquals(SagaStepStatus.SKIPPED, stepStatus(r.context(), "deploy-pipelines"));
    }

    @Test
    @DisplayName("Create without pipelines: timeout at step 1 → no compensation")
    void handleStepTimeout_noPipelinesStep1_shouldTerminate() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithoutPipelines(), NOW);
      r = sm.handleStepTimeout(r.context(), def, "create-project", NOW);

      assertTrue(r.context().status().isTerminal());
      assertTrue(r.context().failure().error().contains("Timeout"));
    }

    @Test
    @DisplayName("Create without pipelines: timeout at step 2 → compensate FROST")
    void handleStepTimeout_noPipelinesStep2_shouldCompensateFrost() {
      var r = sm.startSaga(def, DATASET_ID, triggerWithoutPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepTimeout(r.context(), def, "create-route", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());
      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("create-project", comp.stepId());

      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertEquals(SagaStatus.COMPENSATED, r.context().status());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Section 7: Dataset Update
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("7. Dataset Update")
  class DatasetUpdate {

    private final SagaDefinition def = SagaDefinitions.forType(SagaType.DATASET_UPDATE);

    private Map<String, Object> updateTriggerWithPipelines() {
      return Map.of(
          "id",
          DATASET_ID,
          "name",
          "Updated Dataset",
          "openDataAccess",
          false,
          "dataPipelines",
          List.of(Map.of("id", "pl-001", "action", "UPDATE")));
    }

    private Map<String, Object> updateTriggerWithoutPipelines() {
      return Map.of(
          "id",
          DATASET_ID,
          "name",
          "Updated Dataset",
          "openDataAccess",
          false,
          "dataPipelines",
          List.of());
    }

    // ─── Success Scenarios ────────────────────────────────────────────────

    @Test
    @DisplayName("7.1a: Success with pipelines — all 3 steps → COMPLETED")
    void startSaga_updateWithPipelines_shouldCompleteAllSteps() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      assertEquals("update-project", r.context().currentStepId());

      r =
          sm.handleStepCompleted(
              r.context(),
              def,
              "update-project",
              FROST_RESULT,
              Map.of("previousName", "Old Name"),
              NOW);
      assertEquals("update-route", r.context().currentStepId());

      r =
          sm.handleStepCompleted(
              r.context(),
              def,
              "update-route",
              APISIX_RESULT,
              Map.of("previousOpenDataAccess", true),
              NOW);
      assertEquals("update-pipelines", r.context().currentStepId());

      r =
          sm.handleStepCompleted(
              r.context(), def, "update-pipelines", REDPANDA_RESULT, Map.of(), NOW);
      assertEquals(SagaStatus.COMPLETED, r.context().status());
    }

    @Test
    @DisplayName("7.1b: Success without pipelines — 2 steps, Redpanda SKIPPED → COMPLETED")
    void startSaga_updateWithoutPipelines_shouldSkipRedpanda() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithoutPipelines(), NOW);
      r = sm.handleStepCompleted(r.context(), def, "update-project", FROST_RESULT, Map.of(), NOW);
      r = sm.handleStepCompleted(r.context(), def, "update-route", APISIX_RESULT, Map.of(), NOW);

      assertEquals(SagaStatus.COMPLETED, r.context().status());
      assertEquals(SagaStepStatus.SKIPPED, stepStatus(r.context(), "update-pipelines"));
    }

    // ─── Failure at Each Step ─────────────────────────────────────────────

    @Test
    @DisplayName("7.2: Failure at step 1 (FROST) — no compensation needed")
    void handleStepFailed_updateStep1_shouldTerminateWithoutCompensation() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r = sm.handleStepFailed(r.context(), def, "update-project", "Project not found", NOW);

      assertTrue(r.context().status().isTerminal());
      assertEquals("update-project", r.context().failure().stepId());
      assertEquals(SagaStepStatus.FAILED, stepStatus(r.context(), "update-project"));
    }

    @Test
    @DisplayName("7.3: Failure at step 2 (APISIX) — revert FROST (RESTORE_PROJECT)")
    void handleStepFailed_updateStep2_shouldCompensateFrost() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(),
              def,
              "update-project",
              FROST_RESULT,
              Map.of("previousName", "Old Name"),
              NOW);
      r = sm.handleStepFailed(r.context(), def, "update-route", "Error", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("update-project", comp.stepId());
      assertEquals("RESTORE_PROJECT", comp.operation());

      r = sm.handleCompensationCompleted(r.context(), def, "update-project", NOW);
      assertEquals(SagaStatus.COMPENSATED, r.context().status());
    }

    @Test
    @DisplayName(
        "7.4: Failure at step 3 (Redpanda) — revert APISIX (RESTORE_ROUTE) → FROST (RESTORE_PROJECT)")
    void handleStepFailed_updateStep3_shouldCompensateApisixThenFrost() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-project", FROST_RESULT, Map.of("previousName", "Old"), NOW);
      r =
          sm.handleStepCompleted(
              r.context(),
              def,
              "update-route",
              APISIX_RESULT,
              Map.of("previousOpenDataAccess", false),
              NOW);
      r = sm.handleStepFailed(r.context(), def, "update-pipelines", "Error", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      // Compensate APISIX
      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("update-route", comp.stepId());
      assertEquals("RESTORE_ROUTE", comp.operation());
      r = sm.handleCompensationCompleted(r.context(), def, "update-route", NOW);

      // Compensate FROST
      comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("update-project", comp.stepId());
      assertEquals("RESTORE_PROJECT", comp.operation());
      r = sm.handleCompensationCompleted(r.context(), def, "update-project", NOW);

      assertEquals(SagaStatus.COMPENSATED, r.context().status());
    }

    // ─── Timeout at Each Step ─────────────────────────────────────────────

    @Test
    @DisplayName("7.5a: Timeout at step 1 (FROST) — no compensation needed")
    void handleStepTimeout_updateStep1_shouldTerminateWithoutCompensation() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r = sm.handleStepTimeout(r.context(), def, "update-project", NOW);

      assertTrue(r.context().status().isTerminal());
      assertEquals("update-project", r.context().failure().stepId());
      assertTrue(r.context().failure().error().contains("Timeout"));
    }

    @Test
    @DisplayName("7.5b: Timeout at step 2 (APISIX) — revert FROST")
    void handleStepTimeout_updateStep2_shouldCompensateFrost() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-project", FROST_RESULT, Map.of("previousName", "Old"), NOW);
      r = sm.handleStepTimeout(r.context(), def, "update-route", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());
      assertEquals("update-route", r.context().failure().stepId());

      var comp = findAction(r.actions(), SagaAction.CompensateStep.class);
      assertEquals("update-project", comp.stepId());
      assertEquals("RESTORE_PROJECT", comp.operation());

      r = sm.handleCompensationCompleted(r.context(), def, "update-project", NOW);
      assertEquals(SagaStatus.COMPENSATED, r.context().status());
    }

    @Test
    @DisplayName("7.5c: Timeout at step 3 (Redpanda) — revert APISIX → FROST")
    void handleStepTimeout_updateStep3_shouldCompensateApisixThenFrost() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-project", FROST_RESULT, Map.of("previousName", "Old"), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-route", APISIX_RESULT, Map.of("previousODA", true), NOW);
      r = sm.handleStepTimeout(r.context(), def, "update-pipelines", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      // APISIX
      r = sm.handleCompensationCompleted(r.context(), def, "update-route", NOW);
      // FROST
      r = sm.handleCompensationCompleted(r.context(), def, "update-project", NOW);

      assertEquals(SagaStatus.COMPENSATED, r.context().status());
    }

    // ─── Compensation Failure ─────────────────────────────────────────────

    @Test
    @DisplayName("7.6a: Update step 3 fails, APISIX compensation fails → COMPENSATION_FAILED")
    void handleCompensationFailed_updateApisixFails_shouldReportStale() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-project", FROST_RESULT, Map.of("prev", "x"), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-route", APISIX_RESULT, Map.of("prev", "y"), NOW);
      r = sm.handleStepFailed(r.context(), def, "update-pipelines", "Error", NOW);

      // APISIX compensation fails
      r = sm.handleCompensationFailed(r.context(), def, "update-route", "APISIX down", NOW);
      assertEquals(SagaStatus.COMPENSATING, r.context().status());

      // FROST compensation succeeds (best-effort continues)
      r = sm.handleCompensationCompleted(r.context(), def, "update-project", NOW);
      assertEquals(SagaStatus.COMPENSATION_FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(1, fail.staleResources().size());
      assertEquals("apisix", fail.staleResources().get(0).adapter());
    }

    @Test
    @DisplayName("7.6b: Update step 2 fails, FROST compensation also fails → COMPENSATION_FAILED")
    void handleCompensationFailed_step2FailsFrostComp_shouldReportStaleFrost() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-project", FROST_RESULT, Map.of("prev", "x"), NOW);
      r = sm.handleStepFailed(r.context(), def, "update-route", "Error", NOW);

      r = sm.handleCompensationFailed(r.context(), def, "update-project", "FROST unreachable", NOW);
      assertEquals(SagaStatus.COMPENSATION_FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertFalse(fail.compensated());
      assertEquals(1, fail.staleResources().size());
      assertEquals("frost", fail.staleResources().get(0).adapter());
    }

    // ─── Timeout during Compensation ──────────────────────────────────────

    @Test
    @DisplayName("7.7: Timeout during FROST compensation → COMPENSATION_FAILED")
    void handleCompensationFailed_updateTimeout_shouldContinueBestEffort() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-project", FROST_RESULT, Map.of("prev", "x"), NOW);
      r = sm.handleStepFailed(r.context(), def, "update-route", "Error", NOW);

      // FROST compensation times out (treated as comp failure)
      r =
          sm.handleCompensationFailed(
              r.context(), def, "update-project", "Compensation timeout", NOW);
      assertEquals(SagaStatus.COMPENSATION_FAILED, r.context().status());

      assertNotNull(findAction(r.actions(), SagaAction.PublishManualIntervention.class));
    }

    // ─── Without Pipelines: Failure/Timeout ───────────────────────────────

    @Test
    @DisplayName("Update without pipelines: timeout at step 2 → compensate FROST")
    void handleStepTimeout_noPipelinesUpdateStep2_shouldCompensateFrost() {
      var r = sm.startSaga(def, DATASET_ID, updateTriggerWithoutPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "update-project", FROST_RESULT, Map.of("prev", "x"), NOW);
      r = sm.handleStepTimeout(r.context(), def, "update-route", NOW);

      assertEquals(SagaStatus.COMPENSATING, r.context().status());
      r = sm.handleCompensationCompleted(r.context(), def, "update-project", NOW);
      assertEquals(SagaStatus.COMPENSATED, r.context().status());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Section 8: Dataset Delete (Best-Effort Forward Execution)
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("8. Dataset Delete (Best-Effort)")
  class DatasetDelete {

    private final SagaDefinition def = SagaDefinitions.forType(SagaType.DATASET_DELETE);

    // ─── Success Scenarios ────────────────────────────────────────────────

    @Test
    @DisplayName("8.1a: Success with pipelines — all 3 steps → COMPLETED")
    void startSaga_deleteWithPipelines_shouldCompleteAllSteps() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      assertEquals("delete-pipelines", r.context().currentStepId());

      r =
          sm.handleStepCompleted(
              r.context(), def, "delete-pipelines", EMPTY_RESULT, EMPTY_COMP, NOW);
      assertEquals("delete-route", r.context().currentStepId());

      r = sm.handleStepCompleted(r.context(), def, "delete-route", EMPTY_RESULT, EMPTY_COMP, NOW);
      assertEquals("delete-project", r.context().currentStepId());

      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);
      assertEquals(SagaStatus.COMPLETED, r.context().status());
      assertNotNull(findAction(r.actions(), SagaAction.CompleteSaga.class));
    }

    @Test
    @DisplayName("8.1b: Success without pipelines — Redpanda skipped, 2 steps → COMPLETED")
    void startSaga_deleteWithoutPipelines_shouldSkipRedpanda() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithoutPipelines(), NOW);
      assertEquals("delete-route", r.context().currentStepId());
      assertEquals(SagaStepStatus.SKIPPED, stepStatus(r.context(), "delete-pipelines"));

      r = sm.handleStepCompleted(r.context(), def, "delete-route", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);
      assertEquals(SagaStatus.COMPLETED, r.context().status());
    }

    // ─── Failure at Each Step (Best-Effort Continues) ─────────────────────

    @Test
    @DisplayName("8.2: Failure at step 1 (Redpanda) — continue APISIX + FROST → partial FAILED")
    void handleStepFailed_deleteStep1_shouldContinueNextSteps() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);

      r = sm.handleStepFailed(r.context(), def, "delete-pipelines", "Redpanda unavailable", NOW);

      // Should continue to APISIX (best-effort, no compensation)
      var exec = findAction(r.actions(), SagaAction.ExecuteStep.class);
      assertNotNull(exec, "Delete saga must continue after step failure");
      assertEquals("delete-route", exec.stepId());
      assertNull(
          findAction(r.actions(), SagaAction.CompensateStep.class),
          "Delete saga must NOT compensate");

      r = sm.handleStepCompleted(r.context(), def, "delete-route", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(1, fail.staleResources().size());
      assertEquals("redpanda", fail.staleResources().get(0).adapter());
      assertEquals(2, fail.cleanedResources().size());
    }

    @Test
    @DisplayName("8.3: Failure at step 2 (APISIX) — continue with FROST → partial FAILED")
    void handleStepFailed_deleteStep2_shouldContinueNextStep() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "delete-pipelines", EMPTY_RESULT, EMPTY_COMP, NOW);

      r = sm.handleStepFailed(r.context(), def, "delete-route", "APISIX unreachable", NOW);

      var exec = findAction(r.actions(), SagaAction.ExecuteStep.class);
      assertNotNull(exec);
      assertEquals("delete-project", exec.stepId());

      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(1, fail.staleResources().size());
      assertEquals("apisix", fail.staleResources().get(0).adapter());
      assertEquals(2, fail.cleanedResources().size());
    }

    @Test
    @DisplayName("8.4: Failure at step 3 (FROST) — steps 1+2 deleted, FROST stale")
    void handleStepFailed_deleteStep3_shouldReportPartialFailure() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "delete-pipelines", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepCompleted(r.context(), def, "delete-route", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-project", "FROST unavailable", NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(1, fail.staleResources().size());
      assertEquals("frost", fail.staleResources().get(0).adapter());
      assertEquals(2, fail.cleanedResources().size());

      assertNotNull(findAction(r.actions(), SagaAction.PublishManualIntervention.class));
    }

    // ─── Multiple Failures (Best-Effort) ──────────────────────────────────

    @Test
    @DisplayName("8.5a: Steps 1+2 fail, step 3 succeeds → FAILED, 2 stale + 1 cleaned")
    void handleStepFailed_deleteTwoFailOneSuccess_shouldReportPartial() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);

      // Redpanda fails
      r = sm.handleStepFailed(r.context(), def, "delete-pipelines", "RP down", NOW);
      // APISIX fails
      r = sm.handleStepFailed(r.context(), def, "delete-route", "APISIX down", NOW);
      // FROST succeeds
      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(2, fail.staleResources().size());
      assertEquals(1, fail.cleanedResources().size());
      assertEquals("frost", fail.cleanedResources().get(0).adapter());
    }

    @Test
    @DisplayName("8.5b: All 3 steps fail → FAILED, all 3 stale")
    void handleStepFailed_deleteAllFail_shouldReportAllFailed() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-pipelines", "RP down", NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-route", "APISIX down", NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-project", "FROST down", NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(3, fail.staleResources().size());
      assertEquals(0, fail.cleanedResources().size());
    }

    @Test
    @DisplayName("8.5c: Step 1 succeeds, steps 2+3 fail → FAILED, 2 stale + 1 cleaned")
    void handleStepFailed_deleteStep1OkSteps2And3Fail_shouldReportPartial() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "delete-pipelines", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-route", "APISIX down", NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-project", "FROST down", NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(2, fail.staleResources().size());
      assertEquals(1, fail.cleanedResources().size());
      assertEquals("redpanda", fail.cleanedResources().get(0).adapter());
    }

    // ─── Timeout at Each Step (Best-Effort) ───────────────────────────────

    @Test
    @DisplayName("8.6a: Timeout at step 1 (Redpanda) — continue with APISIX + FROST")
    void handleStepTimeout_deleteStep1_shouldContinueNextSteps() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r = sm.handleStepTimeout(r.context(), def, "delete-pipelines", NOW);

      assertTrue(r.context().failure().error().contains("Timeout"));

      var exec = findAction(r.actions(), SagaAction.ExecuteStep.class);
      assertNotNull(exec);
      assertEquals("delete-route", exec.stepId());

      r = sm.handleStepCompleted(r.context(), def, "delete-route", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());
      assertEquals(SagaStepStatus.FAILED, stepStatus(r.context(), "delete-pipelines"));
    }

    @Test
    @DisplayName("8.6b: Timeout at step 2 (APISIX) — continue with FROST")
    void handleStepTimeout_deleteStep2_shouldContinueNextStep() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "delete-pipelines", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepTimeout(r.context(), def, "delete-route", NOW);

      var exec = findAction(r.actions(), SagaAction.ExecuteStep.class);
      assertNotNull(exec);
      assertEquals("delete-project", exec.stepId());

      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);
      assertEquals(SagaStatus.FAILED, r.context().status());
    }

    @Test
    @DisplayName("8.6c: Timeout at step 3 (FROST) — steps 1+2 deleted, FROST stale")
    void handleStepTimeout_deleteStep3_shouldReportPartialTimeout() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "delete-pipelines", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepCompleted(r.context(), def, "delete-route", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepTimeout(r.context(), def, "delete-project", NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());
      assertTrue(r.context().failure().error().contains("Timeout"));
    }

    // ─── Mixed Timeout + Failure ──────────────────────────────────────────

    @Test
    @DisplayName("8.7a: Step 1 timeout, step 2 failure, step 3 success → 2 stale, 1 cleaned")
    void handleStepFailed_deleteMixedTimeoutAndFailure_shouldReportPartial() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r = sm.handleStepTimeout(r.context(), def, "delete-pipelines", NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-route", "APISIX down", NOW);
      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(2, fail.staleResources().size());
      assertEquals(1, fail.cleanedResources().size());
    }

    @Test
    @DisplayName("8.7b: Step 1 success, step 2 timeout, step 3 timeout → 2 stale, 1 cleaned")
    void handleStepTimeout_deleteTwoTimeouts_shouldReportPartial() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "delete-pipelines", EMPTY_RESULT, EMPTY_COMP, NOW);
      r = sm.handleStepTimeout(r.context(), def, "delete-route", NOW);
      r = sm.handleStepTimeout(r.context(), def, "delete-project", NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(2, fail.staleResources().size());
      assertEquals(1, fail.cleanedResources().size());
    }

    // ─── Without Pipelines: Failure/Timeout ───────────────────────────────

    @Test
    @DisplayName("Delete without pipelines: timeout at step 2 → continue FROST → partial FAILED")
    void handleStepTimeout_noPipelinesDeleteStep2_shouldReportPartial() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithoutPipelines(), NOW);
      assertEquals("delete-route", r.context().currentStepId());

      r = sm.handleStepTimeout(r.context(), def, "delete-route", NOW);

      var exec = findAction(r.actions(), SagaAction.ExecuteStep.class);
      assertNotNull(exec);
      assertEquals("delete-project", exec.stepId());

      r = sm.handleStepCompleted(r.context(), def, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);
      assertEquals(SagaStatus.FAILED, r.context().status());
    }

    @Test
    @DisplayName("Delete without pipelines: both steps fail → all stale")
    void handleStepFailed_noPipelinesDeleteBothFail_shouldReportAllFailed() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithoutPipelines(), NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-route", "Error", NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-project", "Error", NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(2, fail.staleResources().size());
    }

    @Test
    @DisplayName("Delete without pipelines: step 2 timeout, step 3 fail → all stale")
    void handleStepFailed_noPipelinesDeleteTimeoutThenFail_shouldReportPartial() {
      var r = sm.startSaga(def, DATASET_ID, deleteTriggerWithoutPipelines(), NOW);
      r = sm.handleStepTimeout(r.context(), def, "delete-route", NOW);
      r = sm.handleStepFailed(r.context(), def, "delete-project", "FROST down", NOW);

      assertEquals(SagaStatus.FAILED, r.context().status());

      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals(2, fail.staleResources().size());
      assertEquals(0, fail.cleanedResources().size());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Structural / Cross-Cutting Tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Structural Guarantees")
  class StructuralGuarantees {

    @Test
    @DisplayName("PersistState is always the first action on startSaga")
    void startSaga_anyType_shouldPersistStateBeforeDispatch() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      assertInstanceOf(SagaAction.PersistState.class, r.actions().get(0));
    }

    @Test
    @DisplayName("PersistState is always the first action on handleStepCompleted")
    void handleStepCompleted_anyStep_shouldPersistStateBeforeNextDispatch() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      assertInstanceOf(SagaAction.PersistState.class, r.actions().get(0));
    }

    @Test
    @DisplayName("PersistState is always the first action on handleStepFailed")
    void handleStepFailed_anyStep_shouldPersistStateBeforeCompensation() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "create-route", "Error", NOW);
      assertInstanceOf(SagaAction.PersistState.class, r.actions().get(0));
    }

    @Test
    @DisplayName("PersistState is always the first action on compensation events")
    void handleCompensationCompleted_anyStep_shouldPersistStateBeforeNextCompensation() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "create-route", "Error", NOW);
      // Compensation completed
      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertInstanceOf(SagaAction.PersistState.class, r.actions().get(0));
    }

    @Test
    @DisplayName("Saga context always has a non-null sagaId after startSaga")
    void startSaga_anyType_shouldAlwaysSetSagaId() {
      for (SagaType type : SagaType.values()) {
        var def = SagaDefinitions.forType(type);
        var trigger =
            type == SagaType.DATASET_DELETE ? deleteTriggerWithPipelines() : triggerWithPipelines();
        var r = sm.startSaga(def, DATASET_ID, trigger, NOW);
        assertNotNull(r.context().sagaId());
        assertFalse(r.context().sagaId().isEmpty());
      }
    }

    @Test
    @DisplayName("Saga context preserves datasetId throughout lifecycle")
    void startSaga_withDatasetId_shouldPreserveInContext() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      assertEquals(DATASET_ID, r.context().datasetId());

      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      assertEquals(DATASET_ID, r.context().datasetId());

      r = sm.handleStepFailed(r.context(), def, "create-route", "Error", NOW);
      assertEquals(DATASET_ID, r.context().datasetId());

      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      assertEquals(DATASET_ID, r.context().datasetId());
    }

    @Test
    @DisplayName("Saga context preserves triggerPayload throughout lifecycle")
    void startSaga_withTriggerPayload_shouldPreserveInContext() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var trigger = triggerWithPipelines();

      var r = sm.startSaga(def, DATASET_ID, trigger, NOW);
      assertEquals(trigger, r.context().triggerPayload());

      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      assertEquals(trigger, r.context().triggerPayload());
    }

    @Test
    @DisplayName("Unknown stepId in handleStepCompleted returns context unchanged")
    void handleStepCompleted_unknownStepId_shouldNoOp() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      var before = r.context();

      r = sm.handleStepCompleted(before, def, "nonexistent-step", Map.of(), Map.of(), NOW);
      assertEquals(before, r.context());
      assertTrue(r.actions().isEmpty());
    }

    @Test
    @DisplayName("Unknown stepId in handleStepFailed returns context unchanged")
    void handleStepFailed_unknownStepId_shouldNoOp() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      var before = r.context();

      r = sm.handleStepFailed(before, def, "nonexistent-step", "Error", NOW);
      assertEquals(before, r.context());
    }

    @Test
    @DisplayName("All three saga types produce correct initial step counts")
    void startSaga_anyType_shouldHaveCorrectStepCounts() {
      var createR =
          sm.startSaga(
              SagaDefinitions.forType(SagaType.DATASET_CREATE),
              DATASET_ID,
              triggerWithPipelines(),
              NOW);
      assertEquals(3, createR.context().steps().size());

      var updateR =
          sm.startSaga(
              SagaDefinitions.forType(SagaType.DATASET_UPDATE),
              DATASET_ID,
              triggerWithPipelines(),
              NOW);
      assertEquals(3, updateR.context().steps().size());

      var deleteR =
          sm.startSaga(
              SagaDefinitions.forType(SagaType.DATASET_DELETE),
              DATASET_ID,
              deleteTriggerWithPipelines(),
              NOW);
      assertEquals(3, deleteR.context().steps().size());
    }

    @Test
    @DisplayName("CompleteSaga action contains sagaId and datasetId")
    void handleStepCompleted_allSteps_shouldContainResourceIds() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithoutPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-route", APISIX_RESULT, APISIX_COMP_DATA, NOW);

      var complete = findAction(r.actions(), SagaAction.CompleteSaga.class);
      assertNotNull(complete);
      assertEquals(r.context().sagaId(), complete.sagaId());
      assertTrue(complete.resultPayload().containsKey("datasetId"));
      assertEquals(DATASET_ID, complete.resultPayload().get("datasetId"));
    }

    @Test
    @DisplayName("FailSaga action reports correct failedStep for all failure points")
    void handleStepFailed_anyStep_shouldReportCorrectFailedStepId() {
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);

      // Failure at step 1
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r = sm.handleStepFailed(r.context(), def, "create-project", "Error", NOW);
      var fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertNotNull(fail);
      assertEquals("create-project", fail.failedStep());

      // Failure at step 2
      r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "create-route", "Error", NOW);
      r = sm.handleCompensationCompleted(r.context(), def, "create-project", NOW);
      fail = findAction(r.actions(), SagaAction.FailSaga.class);
      assertEquals("create-route", fail.failedStep());
    }

    @Test
    @DisplayName("ManualIntervention is published when COMPENSATION_FAILED and on delete failures")
    void handleCompensationFailed_staleResources_shouldPublishManualIntervention() {
      // COMPENSATION_FAILED
      var def = SagaDefinitions.forType(SagaType.DATASET_CREATE);
      var r = sm.startSaga(def, DATASET_ID, triggerWithPipelines(), NOW);
      r =
          sm.handleStepCompleted(
              r.context(), def, "create-project", FROST_RESULT, FROST_COMP_DATA, NOW);
      r = sm.handleStepFailed(r.context(), def, "create-route", "Error", NOW);
      r = sm.handleCompensationFailed(r.context(), def, "create-project", "Comp failed", NOW);

      assertNotNull(findAction(r.actions(), SagaAction.PublishManualIntervention.class));

      // Delete partial failure
      var delDef = SagaDefinitions.forType(SagaType.DATASET_DELETE);
      r = sm.startSaga(delDef, DATASET_ID, deleteTriggerWithPipelines(), NOW);
      r = sm.handleStepFailed(r.context(), delDef, "delete-pipelines", "Error", NOW);
      r =
          sm.handleStepCompleted(
              r.context(), delDef, "delete-route", EMPTY_RESULT, EMPTY_COMP, NOW);
      r =
          sm.handleStepCompleted(
              r.context(), delDef, "delete-project", EMPTY_RESULT, EMPTY_COMP, NOW);

      assertNotNull(findAction(r.actions(), SagaAction.PublishManualIntervention.class));
    }
  }
}
