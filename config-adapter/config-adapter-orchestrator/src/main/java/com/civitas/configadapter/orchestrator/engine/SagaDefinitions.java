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

import com.civitas.configadapter.model.saga.SagaType;
import java.util.List;
import java.util.Map;

/**
 * Registry of all saga workflow definitions. Provides the step blueprints for each saga type.
 *
 * <p>Step order per saga type:
 *
 * <ul>
 *   <li>DATASET_CREATE: FROST → APISIX → Redpanda (conditional)
 *   <li>DATASET_UPDATE: FROST → APISIX → Redpanda (conditional)
 *   <li>DATASET_DELETE: Redpanda (conditional) → APISIX → FROST (reverse, best-effort, no
 *       compensation)
 * </ul>
 */
public final class SagaDefinitions {

  // Topic prefixes
  private static final String FROST_EXECUTE = "core.civitas.dataset.frost.execute";
  private static final String FROST_COMPENSATE = "core.civitas.dataset.frost.compensate";
  private static final String APISIX_EXECUTE = "core.civitas.dataset.apisix.execute";
  private static final String APISIX_COMPENSATE = "core.civitas.dataset.apisix.compensate";
  private static final String REDPANDA_EXECUTE = "core.civitas.dataset.redpanda.execute";
  private static final String REDPANDA_COMPENSATE = "core.civitas.dataset.redpanda.compensate";

  private static final Map<SagaType, SagaDefinition> DEFINITIONS =
      Map.of(
          SagaType.DATASET_CREATE, datasetCreate(),
          SagaType.DATASET_UPDATE, datasetUpdate(),
          SagaType.DATASET_DELETE, datasetDelete());

  private SagaDefinitions() {}

  /** Returns the saga definition for the given saga type. */
  public static SagaDefinition forType(SagaType sagaType) {
    var definition = DEFINITIONS.get(sagaType);
    if (definition == null) {
      throw new IllegalArgumentException("No saga definition for type: " + sagaType);
    }
    return definition;
  }

  /**
   * Dataset Create: FROST → APISIX → Redpanda (conditional).
   *
   * <p>Compensation reverses completed steps (delete created resources).
   */
  private static SagaDefinition datasetCreate() {
    return new SagaDefinition(
        SagaType.DATASET_CREATE,
        List.of(
            SagaStepDefinition.mandatory(
                "create-project",
                "frost",
                "CREATE_PROJECT",
                "DELETE_PROJECT",
                FROST_EXECUTE,
                FROST_COMPENSATE),
            SagaStepDefinition.mandatory(
                "create-route",
                "apisix",
                "CREATE_ROUTE",
                "DELETE_ROUTE",
                APISIX_EXECUTE,
                APISIX_COMPENSATE),
            SagaStepDefinition.conditional(
                "deploy-pipelines",
                "redpanda",
                "DEPLOY_PIPELINES",
                "DELETE_PIPELINES",
                REDPANDA_EXECUTE,
                REDPANDA_COMPENSATE)),
        true);
  }

  /**
   * Dataset Update: FROST → APISIX → Redpanda (conditional).
   *
   * <p>Compensation reverses completed steps (restore previous state).
   */
  private static SagaDefinition datasetUpdate() {
    return new SagaDefinition(
        SagaType.DATASET_UPDATE,
        List.of(
            SagaStepDefinition.mandatory(
                "update-project",
                "frost",
                "UPDATE_PROJECT",
                "RESTORE_PROJECT",
                FROST_EXECUTE,
                FROST_COMPENSATE),
            SagaStepDefinition.mandatory(
                "update-route",
                "apisix",
                "UPDATE_ROUTE",
                "RESTORE_ROUTE",
                APISIX_EXECUTE,
                APISIX_COMPENSATE),
            SagaStepDefinition.conditional(
                "update-pipelines",
                "redpanda",
                "UPDATE_PIPELINES",
                "RESTORE_PIPELINES",
                REDPANDA_EXECUTE,
                REDPANDA_COMPENSATE)),
        true);
  }

  /**
   * Dataset Delete: Redpanda (conditional) → APISIX → FROST.
   *
   * <p>Reverse order compared to create. Best-effort execution (continues on failure). No
   * compensation — deleted resources cannot be reliably recreated.
   */
  private static SagaDefinition datasetDelete() {
    return new SagaDefinition(
        SagaType.DATASET_DELETE,
        List.of(
            SagaStepDefinition.conditional(
                "delete-pipelines", "redpanda", "DELETE_PIPELINES", null, REDPANDA_EXECUTE, null),
            SagaStepDefinition.mandatory(
                "delete-route", "apisix", "DELETE_ROUTE", null, APISIX_EXECUTE, null),
            SagaStepDefinition.mandatory(
                "delete-project", "frost", "DELETE_PROJECT", null, FROST_EXECUTE, null)),
        false);
  }
}
