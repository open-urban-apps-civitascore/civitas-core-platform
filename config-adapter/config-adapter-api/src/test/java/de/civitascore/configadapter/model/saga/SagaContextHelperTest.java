/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaContextHelperTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  private static SagaStep step(String stepId, String adapter, SagaStepStatus status) {
    return new SagaStep(stepId, adapter, "OP", status, Map.of(), Map.of(), null, NOW, NOW);
  }

  private static SagaStep stepWithError(String stepId, String adapter, SagaStepStatus status) {
    return new SagaStep(stepId, adapter, "OP", status, Map.of(), Map.of(), "error", NOW, NOW);
  }

  private static SagaContext saga(SagaStep... steps) {
    return new SagaContext(
        "saga-1",
        SagaType.DATASET_CREATE,
        "ds-1",
        null,
        SagaStatus.EXECUTING,
        List.of(steps),
        null,
        Map.of(),
        NOW,
        NOW);
  }

  @Nested
  @DisplayName("findStep")
  class FindStep {

    @Test
    @DisplayName("returns step when found")
    void shouldFindExistingStep() {
      SagaStep frost = step("create-project", "frost", SagaStepStatus.SUCCESS);
      SagaStep apisix = step("create-route", "apisix", SagaStepStatus.PENDING);
      SagaContext context = saga(frost, apisix);

      Optional<SagaStep> result = SagaContextHelper.findStep(context, "create-route");

      assertTrue(result.isPresent());
      assertEquals("apisix", result.get().adapter());
    }

    @Test
    @DisplayName("returns empty when step not found")
    void shouldReturnEmptyForMissingStep() {
      SagaContext context = saga(step("create-project", "frost", SagaStepStatus.SUCCESS));

      Optional<SagaStep> result = SagaContextHelper.findStep(context, "nonexistent");

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("findStepIndex")
  class FindStepIndex {

    @Test
    @DisplayName("returns correct index")
    void shouldReturnCorrectIndex() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.SUCCESS),
              step("create-route", "apisix", SagaStepStatus.PENDING),
              step("deploy-pipelines", "nifi", SagaStepStatus.PENDING));

      assertEquals(0, SagaContextHelper.findStepIndex(context, "create-project"));
      assertEquals(1, SagaContextHelper.findStepIndex(context, "create-route"));
      assertEquals(2, SagaContextHelper.findStepIndex(context, "deploy-pipelines"));
    }

    @Test
    @DisplayName("returns -1 when step not found")
    void shouldReturnMinusOneForMissingStep() {
      SagaContext context = saga(step("create-project", "frost", SagaStepStatus.SUCCESS));

      assertEquals(-1, SagaContextHelper.findStepIndex(context, "nonexistent"));
    }
  }

  @Nested
  @DisplayName("getCompletedSteps")
  class GetCompletedSteps {

    @Test
    @DisplayName("returns only SUCCESS steps")
    void shouldReturnOnlySuccessSteps() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.SUCCESS),
              step("create-route", "apisix", SagaStepStatus.SUCCESS),
              step("deploy-pipelines", "nifi", SagaStepStatus.FAILED));

      List<SagaStep> completed = SagaContextHelper.getCompletedSteps(context);

      assertEquals(2, completed.size());
      assertEquals("create-project", completed.get(0).stepId());
      assertEquals("create-route", completed.get(1).stepId());
    }

    @Test
    @DisplayName("returns empty list when no steps completed")
    void shouldReturnEmptyListWhenNoneCompleted() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.PENDING),
              step("create-route", "apisix", SagaStepStatus.PENDING));

      assertTrue(SagaContextHelper.getCompletedSteps(context).isEmpty());
    }
  }

  @Nested
  @DisplayName("getPendingSteps")
  class GetPendingSteps {

    @Test
    @DisplayName("returns only PENDING steps")
    void shouldReturnOnlyPendingSteps() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.SUCCESS),
              step("create-route", "apisix", SagaStepStatus.PENDING),
              step("deploy-pipelines", "nifi", SagaStepStatus.PENDING));

      List<SagaStep> pending = SagaContextHelper.getPendingSteps(context);

      assertEquals(2, pending.size());
      assertEquals("create-route", pending.get(0).stepId());
      assertEquals("deploy-pipelines", pending.get(1).stepId());
    }
  }

  @Nested
  @DisplayName("getCompletedStepsReversed")
  class CompensationOrder {

    @Test
    @DisplayName("returns completed steps in reverse order")
    void shouldReturnReversedCompletedSteps() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.SUCCESS),
              step("create-route", "apisix", SagaStepStatus.SUCCESS),
              step("deploy-pipelines", "nifi", SagaStepStatus.FAILED));

      List<SagaStep> reversed = SagaContextHelper.getCompletedStepsReversed(context);

      assertEquals(2, reversed.size());
      assertEquals("create-route", reversed.get(0).stepId());
      assertEquals("create-project", reversed.get(1).stepId());
    }

    @Test
    @DisplayName("returns empty list when no steps completed")
    void shouldReturnEmptyWhenNoneCompleted() {
      SagaContext context = saga(step("create-project", "frost", SagaStepStatus.FAILED));

      assertTrue(SagaContextHelper.getCompletedStepsReversed(context).isEmpty());
    }
  }

  @Nested
  @DisplayName("getStaleSteps")
  class GetStaleSteps {

    @Test
    @DisplayName("returns only COMPENSATION_FAILED steps")
    void shouldReturnOnlyCompensationFailedSteps() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.COMPENSATED),
              stepWithError("create-route", "apisix", SagaStepStatus.COMPENSATION_FAILED));

      List<SagaStep> stale = SagaContextHelper.getStaleSteps(context);

      assertEquals(1, stale.size());
      assertEquals("create-route", stale.get(0).stepId());
    }
  }

  @Nested
  @DisplayName("getCleanedSteps")
  class GetCleanedSteps {

    @Test
    @DisplayName("returns only COMPENSATED steps")
    void shouldReturnOnlyCompensatedSteps() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.COMPENSATED),
              stepWithError("create-route", "apisix", SagaStepStatus.COMPENSATION_FAILED));

      List<SagaStep> cleaned = SagaContextHelper.getCleanedSteps(context);

      assertEquals(1, cleaned.size());
      assertEquals("create-project", cleaned.get(0).stepId());
    }
  }

  @Nested
  @DisplayName("getNextStepToCompensate")
  class GetNextStepToCompensate {

    @Test
    @DisplayName("returns the LAST SUCCESS step (reverse compensation order)")
    void shouldReturnLastSuccessStep() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.SUCCESS),
              step("create-route", "apisix", SagaStepStatus.SUCCESS),
              step("deploy-pipelines", "nifi", SagaStepStatus.FAILED));

      Optional<SagaStep> next = SagaContextHelper.getNextStepToCompensate(context);

      assertTrue(next.isPresent());
      assertEquals("create-route", next.get().stepId());
    }

    @Test
    @DisplayName("returns the only SUCCESS step when just one exists")
    void shouldReturnSingleSuccessStep() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.SUCCESS),
              step("create-route", "apisix", SagaStepStatus.FAILED));

      Optional<SagaStep> next = SagaContextHelper.getNextStepToCompensate(context);

      assertTrue(next.isPresent());
      assertEquals("create-project", next.get().stepId());
    }

    @Test
    @DisplayName("returns empty when no SUCCESS steps remain")
    void shouldReturnEmptyWhenAllCompensated() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.COMPENSATED),
              step("create-route", "apisix", SagaStepStatus.COMPENSATED));

      assertTrue(SagaContextHelper.getNextStepToCompensate(context).isEmpty());
    }

    @Test
    @DisplayName("skips non-SUCCESS steps in the middle")
    void shouldSkipNonSuccessSteps() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.SUCCESS),
              step("create-route", "apisix", SagaStepStatus.COMPENSATED),
              step("deploy-pipelines", "nifi", SagaStepStatus.FAILED));

      Optional<SagaStep> next = SagaContextHelper.getNextStepToCompensate(context);

      assertTrue(next.isPresent());
      assertEquals("create-project", next.get().stepId());
    }
  }

  @Nested
  @DisplayName("isCompensationComplete")
  class IsCompensationComplete {

    @Test
    @DisplayName("returns true when no SUCCESS or COMPENSATING steps remain")
    void shouldReturnTrueWhenAllDone() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.COMPENSATED),
              stepWithError("create-route", "apisix", SagaStepStatus.COMPENSATION_FAILED),
              step("deploy-pipelines", "nifi", SagaStepStatus.FAILED));

      assertTrue(SagaContextHelper.isCompensationComplete(context));
    }

    @Test
    @DisplayName("returns false when SUCCESS steps still exist")
    void shouldReturnFalseWhenSuccessStepsRemain() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.COMPENSATED),
              step("create-route", "apisix", SagaStepStatus.SUCCESS));

      assertFalse(SagaContextHelper.isCompensationComplete(context));
    }

    @Test
    @DisplayName("returns false when COMPENSATING steps exist")
    void shouldReturnFalseWhenCompensatingInProgress() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.COMPENSATED),
              step("create-route", "apisix", SagaStepStatus.COMPENSATING));

      assertFalse(SagaContextHelper.isCompensationComplete(context));
    }
  }

  @Nested
  @DisplayName("hasCompensationFailure")
  class HasCompensationFailure {

    @Test
    @DisplayName("returns true when at least one COMPENSATION_FAILED step exists")
    void shouldReturnTrueWhenCompensationFailed() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.COMPENSATED),
              stepWithError("create-route", "apisix", SagaStepStatus.COMPENSATION_FAILED));

      assertTrue(SagaContextHelper.hasCompensationFailure(context));
    }

    @Test
    @DisplayName("returns false when all compensations succeeded")
    void shouldReturnFalseWhenAllCompensated() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.COMPENSATED),
              step("create-route", "apisix", SagaStepStatus.COMPENSATED));

      assertFalse(SagaContextHelper.hasCompensationFailure(context));
    }

    @Test
    @DisplayName("returns false when no compensation was attempted")
    void shouldReturnFalseWhenNoCompensation() {
      SagaContext context =
          saga(
              step("create-project", "frost", SagaStepStatus.SUCCESS),
              step("create-route", "apisix", SagaStepStatus.PENDING));

      assertFalse(SagaContextHelper.hasCompensationFailure(context));
    }
  }
}
