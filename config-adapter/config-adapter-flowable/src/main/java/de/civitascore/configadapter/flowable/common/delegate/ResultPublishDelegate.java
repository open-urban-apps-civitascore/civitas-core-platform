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

import de.civitascore.configadapter.flowable.common.kafka.FlowableResultPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes the saga result (completed or failed) to Kafka at the end of a BPMN process. Placed as
 * the final service task in both the success and error paths.
 */
public class ResultPublishDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(ResultPublishDelegate.class);

  /** "success" or "failure" — set via Flowable field injection in BPMN. */
  private Expression resultType;

  /** "true" if this process supports compensation (create/update), "false" for delete. */
  private Expression supportsCompensation;

  @Override
  public void execute(DelegateExecution execution) {
    FlowableResultPublisher publisher = resolvePublisher();
    if (publisher == null) {
      throw new IllegalStateException(
          "No FlowableResultPublisher bean found — saga result cannot be published");
    }

    String sagaId = stringVar(execution, "sagaId");
    String type = resolveString(resultType, execution);

    if ("success".equals(type)) {
      Map<String, Object> results = collectResults(execution);
      publisher.publishCompleted(sagaId, results);
    } else {
      String datasetId = stringVar(execution, "datasetId");
      String failedStep = stringVar(execution, "failedStep");
      String error = stringVar(execution, "sagaError");
      boolean hasCompensation = !"false".equals(resolveString(supportsCompensation, execution));
      boolean compensated = hasCompensation && !hasCompensationErrors(execution);
      publisher.publishFailed(sagaId, datasetId, failedStep, error, compensated);
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
    return errors instanceof java.util.List<?> list && !list.isEmpty();
  }

  private FlowableResultPublisher resolvePublisher() {
    var config = Context.getProcessEngineConfiguration();
    if (config == null || config.getBeans() == null) {
      return null;
    }
    return (FlowableResultPublisher) config.getBeans().get("resultPublisher");
  }

  private String stringVar(DelegateExecution execution, String name) {
    Object value = execution.getVariable(name);
    return value != null ? value.toString() : null;
  }

  private String resolveString(Expression expression, DelegateExecution execution) {
    if (expression == null) {
      return null;
    }
    Object value = expression.getValue(execution);
    return value != null ? value.toString() : null;
  }

  public void setResultType(Expression resultType) {
    this.resultType = resultType;
  }

  public void setSupportsCompensation(Expression supportsCompensation) {
    this.supportsCompensation = supportsCompensation;
  }
}
