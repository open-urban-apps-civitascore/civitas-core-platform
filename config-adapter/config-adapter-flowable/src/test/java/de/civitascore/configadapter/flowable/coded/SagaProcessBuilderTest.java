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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.junit.jupiter.api.Test;

class SagaProcessBuilderTest {

  @Test
  void create_setsProcessIdNameAndExecutable() {
    BpmnModel model = SagaProcessBuilder.create("my-saga", "My Saga").build();

    Process process = model.getProcesses().get(0);
    assertEquals("my-saga", process.getId());
    assertEquals("My Saga", process.getName());
    assertEquals(true, process.isExecutable());
  }

  @Test
  void sagaStep_addsTaskAndErrorBoundary() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");

    SagaStepRef step = b.sagaStep("create-thing", "Create Thing", "frost", "CREATE_THING");

    Process process = b.build().getProcesses().get(0);
    assertNotNull(process.getFlowElement("create-thing"));
    assertNotNull(process.getFlowElement("create-thing-error"));
    assertEquals("frost", step.adapter());
    assertEquals("create-thing", step.task().getId());
    assertEquals("create-thing-error", step.boundary().getId());
  }

  @Test
  void compensation_inheritsAdapterFromOriginalStep() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");
    SagaStepRef original = b.sagaStep("create", "Create", "apisix", "CREATE_ROUTE");

    ServiceTask comp = b.compensation("comp", original, "DELETE_ROUTE");

    Process process = b.build().getProcesses().get(0);
    assertNotNull(process.getFlowElement("comp"));
    assertEquals("comp", comp.getId());
    // Adapter inherited from original step: encoded as "adapterName" field extension
    assertEquals(
        "apisix",
        comp.getFieldExtensions().stream()
            .filter(f -> "adapterName".equals(f.getFieldName()))
            .findFirst()
            .orElseThrow()
            .getStringValue());
  }

  @Test
  void flow_generatesIdFromSourceAndTarget() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");
    StartEvent start = b.startEvent("start");
    EndEvent end = b.endEvent("end");

    b.flow(start, end);

    Process process = b.build().getProcesses().get(0);
    SequenceFlow flow = (SequenceFlow) process.getFlowElement("flow-start-end");
    assertNotNull(flow);
    assertEquals("start", flow.getSourceRef());
    assertEquals("end", flow.getTargetRef());
    assertNull(flow.getConditionExpression());
  }

  @Test
  void flow_chainsMultipleTargetsInOneCall() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");
    StartEvent a = b.startEvent("a");
    ExclusiveGateway bGate = b.exclusiveGateway("b");
    EndEvent c = b.endEvent("c");

    b.flow(a, bGate, c);

    Process process = b.build().getProcesses().get(0);
    assertNotNull(process.getFlowElement("flow-a-b"));
    assertNotNull(process.getFlowElement("flow-b-c"));
  }

  @Test
  void when_setsConditionExpressionOnReturnedFlow() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");
    ExclusiveGateway gw = b.exclusiveGateway("gw");
    EndEvent end = b.endEvent("end");

    b.flow(gw, end).when("${ready == true}");

    SequenceFlow flow =
        (SequenceFlow) b.build().getProcesses().get(0).getFlowElement("flow-gw-end");
    assertEquals("${ready == true}", flow.getConditionExpression());
  }

  @Test
  void asDefault_setsGatewayDefaultFlowToTheFlowId() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");
    ExclusiveGateway gw = b.exclusiveGateway("gw");
    EndEvent end = b.endEvent("end");

    b.flow(gw, end).asDefault();

    assertEquals("flow-gw-end", gw.getDefaultFlow());
  }

  @Test
  void asDefault_onNonGatewaySource_throws() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");
    StartEvent start = b.startEvent("start");
    EndEvent end = b.endEvent("end");

    assertThrows(IllegalStateException.class, () -> b.flow(start, end).asDefault());
  }

  @Test
  void errorFlow_routesFromBoundaryEventOfStep() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");
    SagaStepRef step = b.sagaStep("call", "Call", "frost", "OP");
    EndEvent failureEnd = b.endEvent("failure");

    b.errorFlow(step, failureEnd);

    Process process = b.build().getProcesses().get(0);
    SequenceFlow flow = (SequenceFlow) process.getFlowElement("flow-call-error-failure");
    assertNotNull(flow);
    assertEquals("call-error", flow.getSourceRef());
    assertEquals("failure", flow.getTargetRef());
  }

  @Test
  void endEvent_withName_setsName() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");

    EndEvent end = b.endEvent("error-end", "Saga Failed");

    assertEquals("Saga Failed", end.getName());
  }

  @Test
  void publishResult_defaultsToSupportingCompensation() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");

    ServiceTask publish = b.publishResult("pub", "success");

    assertEquals(
        "true",
        publish.getFieldExtensions().stream()
            .filter(f -> "supportsCompensation".equals(f.getFieldName()))
            .findFirst()
            .orElseThrow()
            .getStringValue());
  }

  @Test
  void publishResult_canDisableCompensationSupport() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");

    ServiceTask publish = b.publishResult("pub", "failure", false);

    assertEquals(
        "false",
        publish.getFieldExtensions().stream()
            .filter(f -> "supportsCompensation".equals(f.getFieldName()))
            .findFirst()
            .orElseThrow()
            .getStringValue());
  }

  @Test
  void flow_duplicateSourceTargetPair_throws() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");
    ExclusiveGateway gw = b.exclusiveGateway("gw");
    EndEvent end = b.endEvent("end");
    b.flow(gw, end);

    assertThrows(IllegalStateException.class, () -> b.flow(gw, end));
  }

  @Test
  void boundaryEvent_isCorrectlyAttachedToTask() {
    SagaProcessBuilder b = SagaProcessBuilder.create("p", "P");

    SagaStepRef step = b.sagaStep("s", "S", "frost", "OP");

    BoundaryEvent boundary =
        (BoundaryEvent) b.build().getProcesses().get(0).getFlowElement("s-error");
    assertEquals("s", boundary.getAttachedToRefId());
    assertEquals(step.task(), boundary.getAttachedToRef());
  }
}
