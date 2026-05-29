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

import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;

/**
 * Fluent builder for the saga BPMN processes. Wraps the lower-level {@link ProcessBuilderUtils}
 * helpers and the Flowable {@link BpmnModel} API so that each dataset workflow can be expressed in
 * a top-to-bottom narrative: declare activities once, then wire happy path and error/compensation
 * flows. Sequence-flow IDs are generated from source/target (e.g. {@code flow-<from>-<to>}); the
 * equivalent {@code .bpmn} XML files use short semantic IDs in the same spirit but do not follow
 * that scheme verbatim.
 */
@SuppressWarnings("PMD.TooManyMethods") // Builder with one method per BPMN element type — expected
final class SagaProcessBuilder {

  private final BpmnModel model;
  private final Process process;

  private SagaProcessBuilder(String id, String name) {
    model = new BpmnModel();
    process = new Process();
    process.setId(id);
    process.setName(name);
    process.setExecutable(true);
    model.addProcess(process);
  }

  static SagaProcessBuilder create(String id, String name) {
    return new SagaProcessBuilder(id, name);
  }

  StartEvent startEvent(String id) {
    StartEvent event = new StartEvent();
    event.setId(id);
    process.addFlowElement(event);
    return event;
  }

  EndEvent endEvent(String id) {
    return endEvent(id, null);
  }

  EndEvent endEvent(String id, String name) {
    EndEvent event = new EndEvent();
    event.setId(id);
    if (name != null) {
      event.setName(name);
    }
    process.addFlowElement(event);
    return event;
  }

  ExclusiveGateway exclusiveGateway(String id) {
    return exclusiveGateway(id, null);
  }

  ExclusiveGateway exclusiveGateway(String id, String name) {
    ExclusiveGateway gateway = new ExclusiveGateway();
    gateway.setId(id);
    if (name != null) {
      gateway.setName(name);
    }
    process.addFlowElement(gateway);
    return gateway;
  }

  /**
   * Adds a saga forward step plus its attached error boundary event. The boundary's id is {@code
   * <id>-error}; routing it elsewhere is done via {@link #errorFlow}.
   */
  SagaStepRef sagaStep(String id, String name, String adapter, String operation) {
    ServiceTask task = ProcessBuilderUtils.sagaStep(id, name, adapter, operation);
    process.addFlowElement(task);
    BoundaryEvent boundary = ProcessBuilderUtils.errorBoundary(id + "-error", task);
    process.addFlowElement(boundary);
    return new SagaStepRef(task, boundary, adapter);
  }

  /**
   * Adds a compensation step that calls {@code operation} on the same adapter the {@code original}
   * forward step targeted.
   */
  ServiceTask compensation(String id, SagaStepRef original, String operation) {
    ServiceTask task =
        ProcessBuilderUtils.compensationStep(
            id, original.adapter(), operation, original.task().getId());
    process.addFlowElement(task);
    return task;
  }

  ServiceTask publishResult(String id, String resultType) {
    ServiceTask task = ProcessBuilderUtils.resultPublishTask(id, resultType);
    process.addFlowElement(task);
    return task;
  }

  ServiceTask publishResult(String id, String resultType, boolean supportsCompensation) {
    ServiceTask task = ProcessBuilderUtils.resultPublishTask(id, resultType, supportsCompensation);
    process.addFlowElement(task);
    return task;
  }

  /**
   * Wires one or more sequence flows in a chain: {@code flow(a, b, c, d)} creates {@code a→b},
   * {@code b→c}, {@code c→d}. Returns the handle for the LAST flow so it can carry a condition.
   */
  SequenceFlowRef flow(FlowElement first, FlowElement... rest) {
    if (rest.length == 0) {
      throw new IllegalArgumentException("flow() requires at least one target");
    }
    FlowElement prev = first;
    FlowElement lastSource = null;
    SequenceFlow last = null;
    for (FlowElement next : rest) {
      last = createFlow(prev, next);
      lastSource = prev;
      prev = next;
    }
    return new SequenceFlowRef(last, lastSource);
  }

  /** Wires a flow from {@code step}'s error boundary event to {@code target}. */
  SequenceFlowRef errorFlow(SagaStepRef step, FlowElement target) {
    return flow(step.boundary(), target);
  }

  BpmnModel build() {
    return model;
  }

  private SequenceFlow createFlow(FlowElement source, FlowElement target) {
    String id = "flow-" + source.getId() + "-" + target.getId();
    if (process.getFlowElement(id) != null) {
      throw new IllegalStateException(
          "Duplicate sequence flow id: "
              + id
              + " — the (source, target) pair already has a flow. Disambiguate by adding an"
              + " intermediate element.");
    }
    SequenceFlow flow = new SequenceFlow(source.getId(), target.getId());
    flow.setId(id);
    process.addFlowElement(flow);
    return flow;
  }
}
