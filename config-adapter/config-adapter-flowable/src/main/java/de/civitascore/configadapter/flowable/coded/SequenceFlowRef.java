/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.coded;

import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.SequenceFlow;

/**
 * Handle for a sequence flow added via {@link SagaProcessBuilder#flow}. Allows post-creation
 * decoration with a condition expression or marking as the default flow of its source gateway.
 */
final class SequenceFlowRef {

  private final SequenceFlow flow;
  private final FlowElement source;

  SequenceFlowRef(SequenceFlow flow, FlowElement source) {
    this.flow = flow;
    this.source = source;
  }

  SequenceFlowRef when(String conditionExpression) {
    flow.setConditionExpression(conditionExpression);
    return this;
  }

  SequenceFlowRef asDefault() {
    if (!(source instanceof Gateway gateway)) {
      throw new IllegalStateException(
          "asDefault() is only valid for flows whose source is a Gateway; was: "
              + source.getClass().getSimpleName());
    }
    gateway.setDefaultFlow(flow.getId());
    return this;
  }
}
