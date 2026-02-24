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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaStepTest {

  private static final Instant T1 = Instant.parse("2026-01-15T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-01-15T10:01:00Z");

  @Nested
  @DisplayName("pending factory")
  class Pending {

    @Test
    @DisplayName("creates step with PENDING status and no timestamps")
    void shouldCreatePendingStep() {
      SagaStep step = SagaStep.pending("create-project", "frost", "CREATE_PROJECT");

      assertEquals("create-project", step.stepId());
      assertEquals("frost", step.adapter());
      assertEquals("CREATE_PROJECT", step.operation());
      assertEquals(SagaStepStatus.PENDING, step.status());
      assertTrue(step.result().isEmpty());
      assertTrue(step.compensationData().isEmpty());
      assertNull(step.error());
      assertNull(step.startedAt());
      assertNull(step.completedAt());
    }
  }

  @Nested
  @DisplayName("asInProgress")
  class AsInProgress {

    @Test
    @DisplayName("sets IN_PROGRESS status and startedAt, clears completedAt")
    void shouldTransitionToInProgress() {
      SagaStep step = SagaStep.pending("create-project", "frost", "CREATE_PROJECT");

      SagaStep inProgress = step.asInProgress(T1);

      assertEquals(SagaStepStatus.IN_PROGRESS, inProgress.status());
      assertEquals(T1, inProgress.startedAt());
      assertNull(inProgress.completedAt());
      assertEquals("create-project", inProgress.stepId());
    }
  }

  @Nested
  @DisplayName("asSucceeded")
  class AsSucceeded {

    @Test
    @DisplayName("sets SUCCESS status with result data and clears error")
    void shouldTransitionToSuccess() {
      SagaStep step =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT").asInProgress(T1);
      Map<String, Object> resultData =
          Map.of("projectId", "proj-123", "baseUrl", "http://frost/v1.1");
      Map<String, Object> compData = Map.of("projectId", "proj-123");

      SagaStep succeeded = step.asSucceeded(resultData, compData, T2);

      assertEquals(SagaStepStatus.SUCCESS, succeeded.status());
      assertEquals("proj-123", succeeded.result().get("projectId"));
      assertEquals("proj-123", succeeded.compensationData().get("projectId"));
      assertNull(succeeded.error());
      assertEquals(T1, succeeded.startedAt());
      assertEquals(T2, succeeded.completedAt());
    }
  }

  @Nested
  @DisplayName("asFailed")
  class AsFailed {

    @Test
    @DisplayName("sets FAILED status with error message and preserves startedAt")
    void shouldTransitionToFailed() {
      SagaStep step =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT").asInProgress(T1);

      SagaStep failed = step.asFailed("Connection refused", T2);

      assertEquals(SagaStepStatus.FAILED, failed.status());
      assertEquals("Connection refused", failed.error());
      assertEquals(T1, failed.startedAt());
      assertEquals(T2, failed.completedAt());
    }
  }

  @Nested
  @DisplayName("asSkipped")
  class AsSkipped {

    @Test
    @DisplayName("sets SKIPPED status and clears all data")
    void shouldTransitionToSkipped() {
      SagaStep step = SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES");

      SagaStep skipped = step.asSkipped();

      assertEquals(SagaStepStatus.SKIPPED, skipped.status());
      assertTrue(skipped.result().isEmpty());
      assertTrue(skipped.compensationData().isEmpty());
      assertNull(skipped.error());
      assertNull(skipped.startedAt());
      assertNull(skipped.completedAt());
    }
  }

  @Nested
  @DisplayName("asCompensating")
  class AsCompensating {

    @Test
    @DisplayName("sets COMPENSATING status, sets startedAt to now, clears completedAt")
    void shouldTransitionToCompensating() {
      Map<String, Object> resultData = Map.of("projectId", "proj-123");
      Map<String, Object> compData = Map.of("projectId", "proj-123");
      SagaStep step =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
              .asInProgress(T1)
              .asSucceeded(resultData, compData, T2);

      SagaStep compensating = step.asCompensating(T2);

      assertEquals(SagaStepStatus.COMPENSATING, compensating.status());
      assertEquals(T2, compensating.startedAt());
      assertNull(compensating.completedAt());
      // Preserves result and compensationData
      assertEquals("proj-123", compensating.result().get("projectId"));
      assertEquals("proj-123", compensating.compensationData().get("projectId"));
    }
  }

  @Nested
  @DisplayName("asCompensated")
  class AsCompensated {

    @Test
    @DisplayName("sets COMPENSATED status, clears error, sets completedAt")
    void shouldTransitionToCompensated() {
      Map<String, Object> resultData = Map.of("projectId", "proj-123");
      SagaStep step =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
              .asInProgress(T1)
              .asSucceeded(resultData, Map.of("projectId", "proj-123"), T2)
              .asCompensating(T2);

      SagaStep compensated = step.asCompensated(T2);

      assertEquals(SagaStepStatus.COMPENSATED, compensated.status());
      assertNull(compensated.error());
      assertEquals(T2, compensated.startedAt());
      assertEquals(T2, compensated.completedAt());
      // Preserves result data
      assertEquals("proj-123", compensated.result().get("projectId"));
    }
  }

  @Nested
  @DisplayName("asCompensationFailed")
  class AsCompensationFailed {

    @Test
    @DisplayName("sets COMPENSATION_FAILED status with error message")
    void shouldTransitionToCompensationFailed() {
      Map<String, Object> resultData = Map.of("routeId", "r-456");
      SagaStep step =
          SagaStep.pending("create-route", "apisix", "CREATE_ROUTE")
              .asInProgress(T1)
              .asSucceeded(resultData, Map.of("routeId", "r-456"), T2)
              .asCompensating(T2);

      SagaStep compFailed = step.asCompensationFailed("APISIX unreachable", T2);

      assertEquals(SagaStepStatus.COMPENSATION_FAILED, compFailed.status());
      assertEquals("APISIX unreachable", compFailed.error());
      assertEquals(T2, compFailed.startedAt());
      assertEquals(T2, compFailed.completedAt());
    }
  }

  @Nested
  @DisplayName("withStatus")
  class WithStatus {

    @Test
    @DisplayName("returns copy with only the status changed")
    void shouldCopyWithNewStatus() {
      SagaStep step = SagaStep.pending("create-project", "frost", "CREATE_PROJECT");

      SagaStep updated = step.withStatus(SagaStepStatus.IN_PROGRESS);

      assertEquals(SagaStepStatus.IN_PROGRESS, updated.status());
      assertEquals("create-project", updated.stepId());
      assertEquals("frost", updated.adapter());
    }
  }
}
