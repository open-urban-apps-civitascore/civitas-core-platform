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
 * Builds the Dataset Create saga process programmatically using Flowable's BpmnModel API. Produces
 * an identical process to {@code processes/dataset-create.bpmn} (Approach A).
 *
 * <p>Flow: FROST → APISIX → Redpanda (conditional). On failure: explicit reverse-order compensation
 * chain.
 */
public final class DatasetCreateProcessBuilder {

  private static final String STEP_CREATE_PROJECT = "create-project";
  private static final String STEP_CREATE_ROUTE = "create-route";

  private DatasetCreateProcessBuilder() {}

  /** Builds the BpmnModel for the dataset-create saga process. */
  public static BpmnModel build() {
    BpmnModel model = new BpmnModel();

    Process process = new Process();
    process.setId("dataset-create");
    process.setName("Dataset Create Saga (Coded)");
    process.setExecutable(true);
    model.addProcess(process);

    StartEvent start = new StartEvent();
    start.setId("start");
    process.addFlowElement(start);

    ServiceTask frost =
        sagaStep(STEP_CREATE_PROJECT, "Create FROST Project", "frost", "CREATE_PROJECT");
    process.addFlowElement(frost);
    BoundaryEvent frostError = errorBoundary("frost-error", frost);
    process.addFlowElement(frostError);

    ServiceTask apisix =
        sagaStep(STEP_CREATE_ROUTE, "Create APISIX Route", "apisix", "CREATE_ROUTE");
    process.addFlowElement(apisix);
    BoundaryEvent apisixError = errorBoundary("apisix-error", apisix);
    process.addFlowElement(apisixError);

    ExclusiveGateway gateway = new ExclusiveGateway();
    gateway.setId(ProcessBuilderUtils.PIPELINE_GATEWAY_ID);
    gateway.setName("Has Pipelines?");
    process.addFlowElement(gateway);

    ServiceTask redpanda =
        sagaStep("deploy-pipelines", "Deploy Pipelines", "redpanda", "DEPLOY_PIPELINES");
    process.addFlowElement(redpanda);
    BoundaryEvent redpandaError = errorBoundary("redpanda-error", redpanda);
    process.addFlowElement(redpandaError);

    ServiceTask compFrostAfterApisix =
        compensationStep(
            "compensate-frost-after-apisix", "frost", "DELETE_PROJECT", STEP_CREATE_PROJECT);
    process.addFlowElement(compFrostAfterApisix);

    ServiceTask compApisixAfterRedpanda =
        compensationStep(
            "compensate-apisix-after-redpanda", "apisix", "DELETE_ROUTE", STEP_CREATE_ROUTE);
    process.addFlowElement(compApisixAfterRedpanda);

    ServiceTask compFrostAfterRedpanda =
        compensationStep(
            "compensate-frost-after-redpanda", "frost", "DELETE_PROJECT", STEP_CREATE_PROJECT);
    process.addFlowElement(compFrostAfterRedpanda);

    process.addFlowElement(resultPublishTask(ProcessBuilderUtils.PUBLISH_SUCCESS_ID, "success"));
    process.addFlowElement(resultPublishTask(ProcessBuilderUtils.PUBLISH_FAILURE_ID, "failure"));

    EndEvent end = new EndEvent();
    end.setId("end");
    process.addFlowElement(end);

    EndEvent errorEnd = new EndEvent();
    errorEnd.setId("error-end");
    errorEnd.setName("Saga Failed");
    process.addFlowElement(errorEnd);

    process.addFlowElement(flow("flow-start", "start", STEP_CREATE_PROJECT));
    process.addFlowElement(flow("flow-frost-apisix", STEP_CREATE_PROJECT, STEP_CREATE_ROUTE));
    process.addFlowElement(
        flow("flow-apisix-gateway", STEP_CREATE_ROUTE, ProcessBuilderUtils.PIPELINE_GATEWAY_ID));
    process.addFlowElement(
        conditionalFlow(
            "flow-gw-redpanda",
            ProcessBuilderUtils.PIPELINE_GATEWAY_ID,
            "deploy-pipelines",
            "${hasPipelines == true}"));
    process.addFlowElement(
        conditionalFlow(
            "flow-gw-skip",
            ProcessBuilderUtils.PIPELINE_GATEWAY_ID,
            ProcessBuilderUtils.PUBLISH_SUCCESS_ID,
            "${hasPipelines == false}"));
    process.addFlowElement(
        flow("flow-redpanda-success", "deploy-pipelines", ProcessBuilderUtils.PUBLISH_SUCCESS_ID));
    process.addFlowElement(
        flow("flow-publish-success-end", ProcessBuilderUtils.PUBLISH_SUCCESS_ID, "end"));

    process.addFlowElement(
        flow("flow-frost-error", "frost-error", ProcessBuilderUtils.PUBLISH_FAILURE_ID));

    process.addFlowElement(
        flow("flow-apisix-error", "apisix-error", "compensate-frost-after-apisix"));
    process.addFlowElement(
        flow(
            "flow-comp-frost-after-apisix",
            "compensate-frost-after-apisix",
            ProcessBuilderUtils.PUBLISH_FAILURE_ID));

    process.addFlowElement(
        flow("flow-redpanda-error", "redpanda-error", "compensate-apisix-after-redpanda"));
    process.addFlowElement(
        flow(
            "flow-comp-apisix-after-redpanda",
            "compensate-apisix-after-redpanda",
            "compensate-frost-after-redpanda"));
    process.addFlowElement(
        flow(
            "flow-comp-frost-after-redpanda",
            "compensate-frost-after-redpanda",
            ProcessBuilderUtils.PUBLISH_FAILURE_ID));

    process.addFlowElement(
        flow("flow-publish-failure-end", ProcessBuilderUtils.PUBLISH_FAILURE_ID, "error-end"));

    return model;
  }
}
