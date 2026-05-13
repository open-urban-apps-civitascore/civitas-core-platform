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

import static de.civitascore.configadapter.flowable.coded.ProcessBuilderUtils.conditionalFlow;
import static de.civitascore.configadapter.flowable.coded.ProcessBuilderUtils.errorBoundary;
import static de.civitascore.configadapter.flowable.coded.ProcessBuilderUtils.flow;
import static de.civitascore.configadapter.flowable.coded.ProcessBuilderUtils.resultPublishTask;
import static de.civitascore.configadapter.flowable.coded.ProcessBuilderUtils.sagaStep;

import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;

/**
 * Builds the Dataset Delete saga process programmatically. Reverse order (Redpanda → APISIX →
 * FROST), best-effort: on failure, continue to next step. No compensation. Produces identical
 * behavior to {@code dataset-delete.bpmn20.xml}.
 */
public final class DatasetDeleteProcessBuilder {

  private DatasetDeleteProcessBuilder() {}

  public static BpmnModel build() {
    BpmnModel model = new BpmnModel();

    Process process = new Process();
    process.setId("dataset-delete");
    process.setName("Dataset Delete Saga (Coded)");
    process.setExecutable(true);
    model.addProcess(process);

    StartEvent start = new StartEvent();
    start.setId("start");
    process.addFlowElement(start);

    ExclusiveGateway gateway = new ExclusiveGateway();
    gateway.setId("pipeline-gateway");
    gateway.setName("Has Pipelines?");
    process.addFlowElement(gateway);

    ServiceTask redpanda =
        sagaStep("delete-pipelines", "Delete Pipelines", "redpanda", "DELETE_PIPELINES");
    process.addFlowElement(redpanda);
    BoundaryEvent redpandaError = errorBoundary("redpanda-error", redpanda);
    process.addFlowElement(redpandaError);

    ServiceTask apisix = sagaStep("delete-route", "Delete APISIX Route", "apisix", "DELETE_ROUTE");
    process.addFlowElement(apisix);
    BoundaryEvent apisixError = errorBoundary("apisix-error", apisix);
    process.addFlowElement(apisixError);

    ServiceTask frost =
        sagaStep("delete-project", "Delete FROST Project", "frost", "DELETE_PROJECT");
    process.addFlowElement(frost);
    BoundaryEvent frostError = errorBoundary("frost-error", frost);
    process.addFlowElement(frostError);

    process.addFlowElement(resultPublishTask("publish-success", "success"));
    process.addFlowElement(resultPublishTask("publish-failure", "failure", false));

    ExclusiveGateway resultGateway = new ExclusiveGateway();
    resultGateway.setId("result-gateway");
    resultGateway.setName("Has Errors?");
    resultGateway.setDefaultFlow("flow-result-success");
    process.addFlowElement(resultGateway);

    EndEvent end = new EndEvent();
    end.setId("end");
    process.addFlowElement(end);

    process.addFlowElement(flow("flow-start", "start", "pipeline-gateway"));
    process.addFlowElement(
        conditionalFlow(
            "flow-gw-redpanda", "pipeline-gateway", "delete-pipelines", "${hasPipelines == true}"));
    process.addFlowElement(
        conditionalFlow(
            "flow-gw-skip", "pipeline-gateway", "delete-route", "${hasPipelines == false}"));
    process.addFlowElement(flow("flow-redpanda-apisix", "delete-pipelines", "delete-route"));
    process.addFlowElement(flow("flow-apisix-frost", "delete-route", "delete-project"));
    process.addFlowElement(flow("flow-frost-result", "delete-project", "result-gateway"));

    process.addFlowElement(
        conditionalFlow(
            "flow-result-failure",
            "result-gateway",
            "publish-failure",
            "${execution.getVariable('sagaError') != null}"));
    process.addFlowElement(flow("flow-result-success", "result-gateway", "publish-success"));

    process.addFlowElement(flow("flow-publish-success-end", "publish-success", "end"));
    process.addFlowElement(flow("flow-publish-failure-end", "publish-failure", "end"));

    process.addFlowElement(flow("flow-redpanda-error", "redpanda-error", "delete-route"));
    process.addFlowElement(flow("flow-apisix-error", "apisix-error", "delete-project"));
    process.addFlowElement(flow("flow-frost-error", "frost-error", "result-gateway"));

    return model;
  }
}
