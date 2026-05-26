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

import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import java.util.Map;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.context.Context;

/**
 * Base class for saga delegates. Provides shared helper methods for Expression resolution, registry
 * lookup, and saga ID extraction.
 */
abstract class AbstractSagaDelegate implements JavaDelegate {

  private SagaHandlerRegistry sagaHandlerRegistry;

  protected String resolveString(Expression expression, DelegateExecution execution) {
    if (expression == null) {
      return null;
    }
    Object value = expression.getValue(execution);
    return value != null ? value.toString() : null;
  }

  protected String sagaIdFrom(Map<String, Object> variables, DelegateExecution execution) {
    Object sagaId = variables.get("sagaId");
    return sagaId != null ? sagaId.toString() : execution.getProcessInstanceId();
  }

  protected SagaHandlerRegistry resolveRegistry(DelegateExecution execution) {
    if (sagaHandlerRegistry != null) {
      return sagaHandlerRegistry;
    }
    var config = Context.getProcessEngineConfiguration();
    return (SagaHandlerRegistry) config.getBeans().get("sagaHandlerRegistry");
  }

  void setSagaHandlerRegistry(SagaHandlerRegistry sagaHandlerRegistry) {
    this.sagaHandlerRegistry = sagaHandlerRegistry;
  }
}
