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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.civitascore.configadapter.model.saga.SagaType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaDefinitionTest {

  private static final SagaStepDefinition STEP_FROST =
      SagaStepDefinition.mandatory(
          "create-project",
          "frost",
          "CREATE_PROJECT",
          "DELETE_PROJECT",
          "frost.commands",
          "frost.commands");

  private static final SagaStepDefinition STEP_APISIX =
      SagaStepDefinition.mandatory(
          "create-route",
          "apisix",
          "CREATE_ROUTE",
          "DELETE_ROUTE",
          "apisix.commands",
          "apisix.commands");

  private static final SagaStepDefinition STEP_REDPANDA_NO_COMP =
      SagaStepDefinition.conditional(
          "deploy-pipelines", "redpanda", "DEPLOY_PIPELINES", null, "redpanda.commands", null);

  private static final SagaDefinition DEFINITION =
      new SagaDefinition(
          SagaType.DATASET_CREATE, List.of(STEP_FROST, STEP_APISIX, STEP_REDPANDA_NO_COMP), true);

  @Nested
  @DisplayName("findStep")
  class FindStep {

    @Test
    @DisplayName("returns step definition when stepId exists")
    void shouldReturnStepWhenFound() {
      SagaStepDefinition result = DEFINITION.findStep("create-route");

      assertEquals(STEP_APISIX, result);
    }

    @Test
    @DisplayName("returns null when stepId does not exist")
    void shouldReturnNullWhenNotFound() {
      assertNull(DEFINITION.findStep("non-existent"));
    }
  }

  @Nested
  @DisplayName("stepAt")
  class StepAt {

    @Test
    @DisplayName("returns step at given index")
    void shouldReturnStepAtIndex() {
      assertEquals(STEP_FROST, DEFINITION.stepAt(0));
      assertEquals(STEP_APISIX, DEFINITION.stepAt(1));
      assertEquals(STEP_REDPANDA_NO_COMP, DEFINITION.stepAt(2));
    }

    @Test
    @DisplayName("throws IndexOutOfBoundsException for invalid index")
    void shouldThrowForInvalidIndex() {
      assertThrows(IndexOutOfBoundsException.class, () -> DEFINITION.stepAt(5));
    }
  }

  @Nested
  @DisplayName("stepCount")
  class StepCount {

    @Test
    @DisplayName("returns the number of steps")
    void shouldReturnStepCount() {
      assertEquals(3, DEFINITION.stepCount());
    }
  }

  @Nested
  @DisplayName("findStepIndex")
  class FindStepIndex {

    @Test
    @DisplayName("returns index when stepId exists")
    void shouldReturnIndexWhenFound() {
      assertEquals(0, DEFINITION.findStepIndex("create-project"));
      assertEquals(1, DEFINITION.findStepIndex("create-route"));
      assertEquals(2, DEFINITION.findStepIndex("deploy-pipelines"));
    }

    @Test
    @DisplayName("returns -1 when stepId does not exist")
    void shouldReturnMinusOneWhenNotFound() {
      assertEquals(-1, DEFINITION.findStepIndex("non-existent"));
    }
  }

  @Nested
  @DisplayName("getCompensationSteps")
  class GetCompensationSteps {

    @Test
    @DisplayName("returns compensation-eligible steps in reverse order")
    void shouldReturnCompensationStepsReversed() {
      List<SagaStepDefinition> result =
          DEFINITION.getCompensationSteps(Set.of("create-project", "create-route"));

      assertEquals(2, result.size());
      assertEquals("create-route", result.get(0).stepId());
      assertEquals("create-project", result.get(1).stepId());
    }

    @Test
    @DisplayName("excludes steps without compensation")
    void shouldExcludeStepsWithoutCompensation() {
      List<SagaStepDefinition> result =
          DEFINITION.getCompensationSteps(
              Set.of("create-project", "create-route", "deploy-pipelines"));

      assertEquals(2, result.size());
      assertEquals("create-route", result.get(0).stepId());
      assertEquals("create-project", result.get(1).stepId());
    }

    @Test
    @DisplayName("excludes steps not in completedStepIds")
    void shouldExcludeNonCompletedSteps() {
      List<SagaStepDefinition> result = DEFINITION.getCompensationSteps(Set.of("create-project"));

      assertEquals(1, result.size());
      assertEquals("create-project", result.get(0).stepId());
    }

    @Test
    @DisplayName("returns empty list when no steps match")
    void shouldReturnEmptyWhenNoMatch() {
      List<SagaStepDefinition> result = DEFINITION.getCompensationSteps(Set.of("deploy-pipelines"));

      assertEquals(0, result.size());
    }

    @Test
    @DisplayName("returns empty list for empty completedStepIds")
    void shouldReturnEmptyForEmptyInput() {
      List<SagaStepDefinition> result = DEFINITION.getCompensationSteps(Set.of());

      assertEquals(0, result.size());
    }
  }
}
