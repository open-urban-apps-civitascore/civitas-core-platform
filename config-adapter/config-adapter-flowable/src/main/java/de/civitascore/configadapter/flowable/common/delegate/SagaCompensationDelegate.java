/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common.delegate;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flowable delegate for saga rollback (compensation) steps. Reads stored compensation data from
 * process variables and calls the adapter handler's compensation operation. This is a plain service
 * task on an error-boundary path, not BPMN 2.0 compensation (there are no {@code
 * compensateEventDefinition} markers in the process models).
 *
 * <p>Best-effort: does NOT throw on compensation failure. Instead, errors are collected in a {@code
 * compensationErrors} process variable for final reporting.
 */
public class SagaCompensationDelegate extends AbstractAdapterCallDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(SagaCompensationDelegate.class);
  private static final String TYPE_COMPENSATION_COMPLETED = "COMPENSATION_COMPLETED";

  @Override
  @SuppressWarnings({
    "PMD.CloseResource", // Handler lifecycle managed by ServiceLoader, not callers
    "PMD.AvoidCatchingGenericException" // Best-effort: a throwing handler must not break the chain
  })
  public void execute(DelegateExecution execution) {
    String adapter = resolveString(adapterName, execution);
    String op = resolveString(operation, execution);
    String step = resolveString(stepId, execution);

    SagaHandlerRegistry registry = resolveRegistry(execution);
    SagaCommandHandler handler = registry.getHandler(adapter);

    Map<String, Object> allVariables = execution.getVariables();
    Map<String, Object> payload = buildCompensationPayload(allVariables, step);
    String saga = sagaIdFrom(allVariables, execution);

    SagaCommandMessage command =
        new SagaCommandMessage(
            "COMPENSATE_STEP", UUID.randomUUID().toString(), saga, step, adapter, op, payload);

    LOG.info(
        "Compensating saga step: sagaId={}, step={}, adapter={}, operation={}",
        Encode.forJava(saga),
        Encode.forJava(step),
        Encode.forJava(adapter),
        Encode.forJava(op));

    SagaCommandResult result;
    try {
      result = handler.handle(command);
    } catch (RuntimeException e) {
      // Best-effort contract (see class Javadoc): a handler that throws must not abort the
      // compensation chain. Record the failure and continue.
      LOG.error(
          "Compensation handler threw: sagaId={}, step={}, error={}",
          Encode.forJava(saga),
          Encode.forJava(step),
          Encode.forJava(e.getMessage()),
          e);
      collectError(execution, step, adapter, e.getMessage());
      return;
    }

    if (TYPE_COMPENSATION_COMPLETED.equals(result.type())) {
      LOG.info(
          "Compensation completed: sagaId={}, step={}", Encode.forJava(saga), Encode.forJava(step));
    } else {
      LOG.error(
          "Compensation failed: sagaId={}, step={}, error={}",
          Encode.forJava(saga),
          Encode.forJava(step),
          Encode.forJava(result.error()));
      collectError(execution, step, adapter, result.error());
    }
  }

  private Map<String, Object> buildCompensationPayload(
      Map<String, Object> allVariables, String step) {
    Map<String, Object> payload = new HashMap<>();

    @SuppressWarnings("unchecked")
    Map<String, Object> compensationData =
        (Map<String, Object>) allVariables.get("compensationData_" + step);
    if (compensationData != null) {
      payload.putAll(compensationData);
    }

    for (var entry : allVariables.entrySet()) {
      if (!entry.getKey().startsWith("compensationData_")
          && !entry.getKey().startsWith("compensationError_")
          && !"compensationErrors".equals(entry.getKey())) {
        payload.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }

    return payload;
  }

  @SuppressWarnings("unchecked")
  private void collectError(
      DelegateExecution execution, String step, String adapter, String error) {
    Object existing = execution.getVariable("compensationErrors");
    List<Map<String, String>> errors;
    if (existing instanceof List) {
      errors = (List<Map<String, String>>) existing;
    } else {
      errors = new ArrayList<>();
    }
    errors.add(Map.of("step", step, "adapter", adapter, "error", error));
    execution.setVariable("compensationErrors", errors);
  }
}
