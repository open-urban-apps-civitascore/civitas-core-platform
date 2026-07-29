/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.adapter;

import java.util.Map;

/**
 * Result of processing a saga command, published back to the orchestrator on the adapter's result
 * topic (e.g. {@code de.civitascore.dataset.frost.result}).
 *
 * <p>The orchestrator's {@code SagaResultConsumer} expects the following {@code type} values:
 *
 * <ul>
 *   <li>{@code STEP_COMPLETED} — forward step succeeded
 *   <li>{@code STEP_FAILED} — forward step failed (triggers compensation)
 *   <li>{@code COMPENSATION_COMPLETED} — compensation step succeeded
 *   <li>{@code COMPENSATION_FAILED} — compensation step failed (triggers manual intervention)
 * </ul>
 *
 * @param type result type (see above)
 * @param sagaId saga instance identifier (echoed from the command)
 * @param stepId saga step identifier (echoed from the command)
 * @param resultData adapter-produced data (e.g. {@code {projectId, baseUrl}})
 * @param compensationData data needed to undo this step (e.g. {@code {projectId}})
 * @param error error message (only for {@code STEP_FAILED} / {@code COMPENSATION_FAILED})
 */
public record SagaCommandResult(
    String type,
    String sagaId,
    String stepId,
    Map<String, Object> resultData,
    Map<String, Object> compensationData,
    String error) {

  public static SagaCommandResult success(
      String sagaId,
      String stepId,
      Map<String, Object> resultData,
      Map<String, Object> compensationData) {
    return new SagaCommandResult(
        "STEP_COMPLETED", sagaId, stepId, resultData, compensationData, null);
  }

  public static SagaCommandResult failure(String sagaId, String stepId, String error) {
    return new SagaCommandResult("STEP_FAILED", sagaId, stepId, Map.of(), Map.of(), error);
  }

  public static SagaCommandResult failure(
      String sagaId, String stepId, Map<String, Object> resultData, String error) {
    return new SagaCommandResult("STEP_FAILED", sagaId, stepId, resultData, Map.of(), error);
  }

  public static SagaCommandResult compensationSuccess(String sagaId, String stepId) {
    return new SagaCommandResult(
        "COMPENSATION_COMPLETED", sagaId, stepId, Map.of(), Map.of(), null);
  }

  public static SagaCommandResult compensationFailure(String sagaId, String stepId, String error) {
    return new SagaCommandResult("COMPENSATION_FAILED", sagaId, stepId, Map.of(), Map.of(), error);
  }

  public static SagaCommandResult compensationFailure(
      String sagaId, String stepId, Map<String, Object> resultData, String error) {
    return new SagaCommandResult(
        "COMPENSATION_FAILED", sagaId, stepId, resultData, Map.of(), error);
  }
}
