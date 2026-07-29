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

import de.civitascore.configadapter.flowable.common.SagaFailure;
import de.civitascore.configadapter.flowable.common.SagaResultPublisher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.context.Context;

/**
 * Publishes the saga result (completed or failed) to Kafka at the end of a BPMN process. Placed as
 * the final service task in both the success and error paths.
 */
public class ResultPublishDelegate extends AbstractSagaDelegate {

  private static final String RESULT_TYPE_SUCCESS = "success";

  private Expression resultType;

  /** "true" if this process supports compensation (create/update), "false" for delete. */
  private Expression supportsCompensation;

  @Override
  public void execute(DelegateExecution execution) {
    SagaResultPublisher publisher = resolvePublisher();
    if (publisher == null) {
      throw new IllegalStateException(
          "No SagaResultPublisher bean found — saga result cannot be published");
    }

    String sagaId = stringVar(execution, "sagaId");
    String type = resolveString(resultType, execution);

    if (RESULT_TYPE_SUCCESS.equals(type)) {
      Map<String, Object> results = collectResults(execution);
      publisher.publishCompleted(sagaId, results);
    } else {
      String datasetId = stringVar(execution, "datasetId");
      String failedStep = stringVar(execution, "failedStep");
      String error = stringVar(execution, "sagaError");
      boolean hasCompensation = !"false".equals(resolveString(supportsCompensation, execution));
      boolean compensated = hasCompensation && !hasCompensationErrors(execution);
      Object status = execution.getVariable("pipelineStatus");
      @SuppressWarnings("unchecked")
      Map<String, Object> pipelineStatus =
          status instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
      publisher.publishFailed(
          new SagaFailure(sagaId, datasetId, failedStep, error, compensated, pipelineStatus));
    }
  }

  /**
   * Collects step result data as result payload. Matches the custom orchestrator's
   * aggregateResults() behavior: only sagaId, datasetId, and keys produced by adapter handlers are
   * included — NOT the original trigger payload fields.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> collectResults(DelegateExecution execution) {
    Map<String, Object> results = new HashMap<>();
    results.put("sagaId", execution.getVariable("sagaId"));
    results.put("datasetId", execution.getVariable("datasetId"));

    Set<String> resultKeys = (Set<String>) execution.getVariable("_resultKeys");
    if (resultKeys != null) {
      for (String key : resultKeys) {
        Object value = execution.getVariable(key);
        if (value != null) {
          results.put(key, value);
        }
      }
    }
    return results;
  }

  private boolean hasCompensationErrors(DelegateExecution execution) {
    Object errors = execution.getVariable("compensationErrors");
    return errors instanceof List<?> list && !list.isEmpty();
  }

  private SagaResultPublisher resolvePublisher() {
    ProcessEngineConfigurationImpl config = Context.getProcessEngineConfiguration();
    if (config == null || config.getBeans() == null) {
      return null;
    }
    Object bean = config.getBeans().get("resultPublisher");
    if (bean == null) {
      return null;
    }
    if (!(bean instanceof SagaResultPublisher publisher)) {
      throw new IllegalStateException(
          "resultPublisher bean is of wrong type: " + bean.getClass().getName());
    }
    return publisher;
  }

  private String stringVar(DelegateExecution execution, String name) {
    Object value = execution.getVariable(name);
    return value != null ? value.toString() : null;
  }

  public void setResultType(Expression resultType) {
    this.resultType = resultType;
  }

  public void setSupportsCompensation(Expression supportsCompensation) {
    this.supportsCompensation = supportsCompensation;
  }
}
