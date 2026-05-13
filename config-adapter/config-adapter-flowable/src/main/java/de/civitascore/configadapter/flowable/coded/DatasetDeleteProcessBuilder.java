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

  private static final String STEP_DELETE_ROUTE = "delete-route";
  private static final String STEP_DELETE_PROJECT = "delete-project";

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
    gateway.setId(ProcessBuilderUtils.PIPELINE_GATEWAY_ID);
    gateway.setName("Has Pipelines?");
    process.addFlowElement(gateway);

    ServiceTask redpanda =
        sagaStep("delete-pipelines", "Delete Pipelines", "redpanda", "DELETE_PIPELINES");
    process.addFlowElement(redpanda);
    BoundaryEvent redpandaError = errorBoundary("redpanda-error", redpanda);
    process.addFlowElement(redpandaError);

    ServiceTask apisix =
        sagaStep(STEP_DELETE_ROUTE, "Delete APISIX Route", "apisix", "DELETE_ROUTE");
    process.addFlowElement(apisix);
    BoundaryEvent apisixError = errorBoundary("apisix-error", apisix);
    process.addFlowElement(apisixError);

    ServiceTask frost =
        sagaStep(STEP_DELETE_PROJECT, "Delete FROST Project", "frost", "DELETE_PROJECT");
    process.addFlowElement(frost);
    BoundaryEvent frostError = errorBoundary("frost-error", frost);
    process.addFlowElement(frostError);

    process.addFlowElement(resultPublishTask(ProcessBuilderUtils.PUBLISH_SUCCESS_ID, "success"));
    process.addFlowElement(
        resultPublishTask(ProcessBuilderUtils.PUBLISH_FAILURE_ID, "failure", false));

    ExclusiveGateway resultGateway = new ExclusiveGateway();
    resultGateway.setId(ProcessBuilderUtils.RESULT_GATEWAY_ID);
    resultGateway.setName("Has Errors?");
    resultGateway.setDefaultFlow("flow-result-success");
    process.addFlowElement(resultGateway);

    EndEvent end = new EndEvent();
    end.setId("end");
    process.addFlowElement(end);

    process.addFlowElement(flow("flow-start", "start", ProcessBuilderUtils.PIPELINE_GATEWAY_ID));
    process.addFlowElement(
        conditionalFlow(
            "flow-gw-redpanda",
            ProcessBuilderUtils.PIPELINE_GATEWAY_ID,
            "delete-pipelines",
            "${hasPipelines == true}"));
    process.addFlowElement(
        conditionalFlow(
            "flow-gw-skip",
            ProcessBuilderUtils.PIPELINE_GATEWAY_ID,
            STEP_DELETE_ROUTE,
            "${hasPipelines == false}"));
    process.addFlowElement(flow("flow-redpanda-apisix", "delete-pipelines", STEP_DELETE_ROUTE));
    process.addFlowElement(flow("flow-apisix-frost", STEP_DELETE_ROUTE, STEP_DELETE_PROJECT));
    process.addFlowElement(
        flow("flow-frost-result", STEP_DELETE_PROJECT, ProcessBuilderUtils.RESULT_GATEWAY_ID));

    process.addFlowElement(
        conditionalFlow(
            "flow-result-failure",
            ProcessBuilderUtils.RESULT_GATEWAY_ID,
            ProcessBuilderUtils.PUBLISH_FAILURE_ID,
            "${execution.getVariable('sagaError') != null}"));
    process.addFlowElement(
        flow(
            "flow-result-success",
            ProcessBuilderUtils.RESULT_GATEWAY_ID,
            ProcessBuilderUtils.PUBLISH_SUCCESS_ID));

    process.addFlowElement(
        flow("flow-publish-success-end", ProcessBuilderUtils.PUBLISH_SUCCESS_ID, "end"));
    process.addFlowElement(
        flow("flow-publish-failure-end", ProcessBuilderUtils.PUBLISH_FAILURE_ID, "end"));

    process.addFlowElement(flow("flow-redpanda-error", "redpanda-error", STEP_DELETE_ROUTE));
    process.addFlowElement(flow("flow-apisix-error", "apisix-error", STEP_DELETE_PROJECT));
    process.addFlowElement(
        flow("flow-frost-error", "frost-error", ProcessBuilderUtils.RESULT_GATEWAY_ID));

    return model;
  }
}
