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

import static de.civitascore.configadapter.flowable.coded.ProcessBuilderUtils.compensationStep;
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
 * Builds the Dataset Update saga process programmatically. Same structure as Create but with
 * UPDATE/RESTORE operations. Produces identical behavior to {@code dataset-update.bpmn20.xml}.
 */
public final class DatasetUpdateProcessBuilder {

  private DatasetUpdateProcessBuilder() {}

  public static BpmnModel build() {
    BpmnModel model = new BpmnModel();

    Process process = new Process();
    process.setId("dataset-update");
    process.setName("Dataset Update Saga (Coded)");
    process.setExecutable(true);
    model.addProcess(process);

    StartEvent start = new StartEvent();
    start.setId("start");
    process.addFlowElement(start);

    ServiceTask frost =
        sagaStep("update-project", "Update FROST Project", "frost", "UPDATE_PROJECT");
    process.addFlowElement(frost);
    BoundaryEvent frostError = errorBoundary("frost-error", frost);
    process.addFlowElement(frostError);

    ServiceTask apisix = sagaStep("update-route", "Update APISIX Route", "apisix", "UPDATE_ROUTE");
    process.addFlowElement(apisix);
    BoundaryEvent apisixError = errorBoundary("apisix-error", apisix);
    process.addFlowElement(apisixError);

    ExclusiveGateway gateway = new ExclusiveGateway();
    gateway.setId("pipeline-gateway");
    process.addFlowElement(gateway);

    ServiceTask redpanda =
        sagaStep("update-pipelines", "Update Pipelines", "redpanda", "UPDATE_PIPELINES");
    process.addFlowElement(redpanda);
    BoundaryEvent redpandaError = errorBoundary("redpanda-error", redpanda);
    process.addFlowElement(redpandaError);

    process.addFlowElement(
        compensationStep(
            "compensate-frost-after-apisix", "frost", "RESTORE_PROJECT", "update-project"));
    process.addFlowElement(
        compensationStep(
            "compensate-apisix-after-redpanda", "apisix", "RESTORE_ROUTE", "update-route"));
    process.addFlowElement(
        compensationStep(
            "compensate-frost-after-redpanda", "frost", "RESTORE_PROJECT", "update-project"));

    process.addFlowElement(resultPublishTask("publish-success", "success"));
    process.addFlowElement(resultPublishTask("publish-failure", "failure"));

    EndEvent end = new EndEvent();
    end.setId("end");
    process.addFlowElement(end);

    EndEvent errorEnd = new EndEvent();
    errorEnd.setId("error-end");
    errorEnd.setName("Saga Failed");
    process.addFlowElement(errorEnd);

    process.addFlowElement(flow("flow-start", "start", "update-project"));
    process.addFlowElement(flow("flow-frost-apisix", "update-project", "update-route"));
    process.addFlowElement(flow("flow-apisix-gateway", "update-route", "pipeline-gateway"));
    process.addFlowElement(
        conditionalFlow(
            "flow-gw-redpanda", "pipeline-gateway", "update-pipelines", "${hasPipelines == true}"));
    process.addFlowElement(
        conditionalFlow(
            "flow-gw-skip", "pipeline-gateway", "publish-success", "${hasPipelines == false}"));
    process.addFlowElement(flow("flow-redpanda-success", "update-pipelines", "publish-success"));
    process.addFlowElement(flow("flow-publish-success-end", "publish-success", "end"));

    process.addFlowElement(flow("flow-frost-error", "frost-error", "publish-failure"));
    process.addFlowElement(
        flow("flow-apisix-error", "apisix-error", "compensate-frost-after-apisix"));
    process.addFlowElement(
        flow("flow-comp-frost-after-apisix", "compensate-frost-after-apisix", "publish-failure"));
    process.addFlowElement(
        flow("flow-redpanda-error", "redpanda-error", "compensate-apisix-after-redpanda"));
    process.addFlowElement(
        flow(
            "flow-comp-apisix",
            "compensate-apisix-after-redpanda",
            "compensate-frost-after-redpanda"));
    process.addFlowElement(
        flow("flow-comp-frost", "compensate-frost-after-redpanda", "publish-failure"));
    process.addFlowElement(flow("flow-publish-failure-end", "publish-failure", "error-end"));

    return model;
  }
}
