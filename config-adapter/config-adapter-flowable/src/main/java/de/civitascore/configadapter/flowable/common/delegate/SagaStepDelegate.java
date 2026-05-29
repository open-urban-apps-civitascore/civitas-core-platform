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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic Flowable {@link JavaDelegate} that bridges BPMN service tasks to existing {@link
 * SagaCommandHandler} implementations. Configured via Flowable field injection for adapter name,
 * operation, and step ID.
 *
 * <p>Reads process variables to build a {@link SagaCommandMessage}, calls the handler, and writes
 * result/compensation data back as process variables. On handler failure, throws a {@link
 * BpmnError} to trigger the saga rollback path (compensation steps routed via error boundary
 * events, not BPMN 2.0 compensation).
 */
public class SagaStepDelegate extends AbstractAdapterCallDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(SagaStepDelegate.class);
  private static final String TYPE_STEP_COMPLETED = "STEP_COMPLETED";

  private static final Set<String> INTERNAL_VARIABLE_PREFIXES =
      Set.of("compensationData_", "compensationError_");

  private static final Set<String> INTERNAL_VARIABLE_NAMES =
      Set.of("compensationErrors", "failedStep", "sagaError", "_resultKeys");

  @Override
  @SuppressWarnings("PMD.CloseResource") // Handler lifecycle managed by ServiceLoader, not callers
  public void execute(DelegateExecution execution) {
    String adapter = resolveString(adapterName, execution);
    String op = resolveString(operation, execution);
    String step = resolveString(stepId, execution);

    SagaHandlerRegistry registry = resolveRegistry(execution);
    SagaCommandHandler handler = registry.getHandler(adapter);

    Map<String, Object> allVariables = execution.getVariables();
    Map<String, Object> payload = buildPayload(allVariables, handler);
    String saga = sagaIdFrom(allVariables, execution);
    SagaCommandMessage command =
        new SagaCommandMessage(
            "EXECUTE_STEP", UUID.randomUUID().toString(), saga, step, adapter, op, payload);

    LOG.info(
        "Executing saga step: sagaId={}, step={}, adapter={}, operation={}",
        Encode.forJava(command.sagaId()),
        Encode.forJava(step),
        Encode.forJava(adapter),
        Encode.forJava(op));

    SagaCommandResult result = handler.handle(command);
    handleResult(execution, result, step);
  }

  @SuppressWarnings("unchecked")
  private void handleResult(DelegateExecution execution, SagaCommandResult result, String step) {
    if (TYPE_STEP_COMPLETED.equals(result.type())) {
      if (result.resultData() != null) {
        result.resultData().forEach(execution::setVariable);
        Set<String> resultKeys = (Set<String>) execution.getVariable("_resultKeys");
        if (resultKeys == null) {
          resultKeys = new HashSet<>();
        }
        resultKeys.addAll(result.resultData().keySet());
        execution.setVariable("_resultKeys", (Object) resultKeys);
      }
      if (result.compensationData() != null && !result.compensationData().isEmpty()) {
        execution.setVariable("compensationData_" + step, result.compensationData());
      }
      LOG.info(
          "Saga step completed: sagaId={}, step={}",
          Encode.forJava(result.sagaId()),
          Encode.forJava(step));
    } else {
      LOG.error(
          "Saga step failed: sagaId={}, step={}, error={}",
          Encode.forJava(result.sagaId()),
          Encode.forJava(step),
          Encode.forJava(result.error()));
      execution.setVariable(step + "_error", result.error());
      // Only record the FIRST failure — subsequent failures in best-effort delete don't overwrite
      if (execution.getVariable("sagaError") == null) {
        execution.setVariable("failedStep", step);
        execution.setVariable("sagaError", result.error());
      }
      throw new BpmnError("STEP_FAILED", result.error());
    }
  }

  private Map<String, Object> buildPayload(
      Map<String, Object> allVariables, SagaCommandHandler handler) {
    Map<String, Object> payload = new HashMap<>();
    for (var entry : allVariables.entrySet()) {
      if (isPayloadVariable(entry.getKey())) {
        payload.put(entry.getKey(), entry.getValue());
      }
    }
    // Adapters declare their own field aliases (e.g. APISIX renames baseUrl → upstreamUrl).
    // Existing target keys win over aliased ones, so callers can override the adapter's default.
    handler
        .fieldAliases()
        .forEach(
            (source, target) -> {
              Object value = payload.get(source);
              if (value != null && !payload.containsKey(target)) {
                payload.put(target, value);
              }
            });
    return payload;
  }

  private boolean isPayloadVariable(String key) {
    if (INTERNAL_VARIABLE_NAMES.contains(key)) {
      return false;
    }
    for (String prefix : INTERNAL_VARIABLE_PREFIXES) {
      if (key.startsWith(prefix)) {
        return false;
      }
    }
    return true;
  }
}
