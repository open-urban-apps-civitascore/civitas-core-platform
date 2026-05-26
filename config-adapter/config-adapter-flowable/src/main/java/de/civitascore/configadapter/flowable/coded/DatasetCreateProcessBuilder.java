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

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;

/**
 * Builds the Dataset Create saga process programmatically using Flowable's BpmnModel API. Produces
 * equivalent behavior to {@code processes/dataset-create.bpmn} (Approach A).
 *
 * <p>Flow: FROST → APISIX → Redpanda (conditional). On failure: explicit reverse-order compensation
 * chain.
 */
public final class DatasetCreateProcessBuilder {

  private DatasetCreateProcessBuilder() {}

  /** Builds the BpmnModel for the dataset-create saga process. */
  public static BpmnModel build() {
    SagaProcessBuilder saga =
        SagaProcessBuilder.create("dataset-create", "Dataset Create Saga (Coded)");

    StartEvent start = saga.startEvent("start");
    SagaStepRef frost =
        saga.sagaStep("create-project", "Create FROST Project", "frost", "CREATE_PROJECT");
    SagaStepRef apisix =
        saga.sagaStep("create-route", "Create APISIX Route", "apisix", "CREATE_ROUTE");
    ExclusiveGateway pipelineGw =
        saga.exclusiveGateway(ProcessBuilderUtils.PIPELINE_GATEWAY_ID, "Has Pipelines?");
    SagaStepRef redpanda =
        saga.sagaStep("deploy-pipelines", "Deploy Pipelines", "redpanda", "DEPLOY_PIPELINES");

    // Two FROST compensation tasks (after-apisix, after-redpanda) currently delegate to the same
    // adapter operation but are kept as distinct activities so the two error paths remain visually
    // separate in the BPMN diagram and can diverge later (e.g. richer rollback for the redpanda
    // case) without restructuring the flow.
    ServiceTask compFrostAfterApisix =
        saga.compensation("compensate-frost-after-apisix", frost, "DELETE_PROJECT");
    ServiceTask compApisixAfterRedpanda =
        saga.compensation("compensate-apisix-after-redpanda", apisix, "DELETE_ROUTE");
    ServiceTask compFrostAfterRedpanda =
        saga.compensation("compensate-frost-after-redpanda", frost, "DELETE_PROJECT");

    ServiceTask publishOk = saga.publishResult(ProcessBuilderUtils.PUBLISH_SUCCESS_ID, "success");
    ServiceTask publishFail = saga.publishResult(ProcessBuilderUtils.PUBLISH_FAILURE_ID, "failure");
    EndEvent end = saga.endEvent("end");
    EndEvent errorEnd = saga.endEvent("error-end", "Saga Failed");

    // Happy path
    saga.flow(start, frost.task(), apisix.task(), pipelineGw);
    saga.flow(pipelineGw, redpanda.task()).when("${hasPipelines == true}");
    saga.flow(pipelineGw, publishOk).when("${hasPipelines == false}");
    saga.flow(redpanda.task(), publishOk);
    saga.flow(publishOk, end);

    // Error/compensation paths
    saga.errorFlow(frost, publishFail);
    saga.errorFlow(apisix, compFrostAfterApisix);
    saga.flow(compFrostAfterApisix, publishFail);
    saga.errorFlow(redpanda, compApisixAfterRedpanda);
    saga.flow(compApisixAfterRedpanda, compFrostAfterRedpanda, publishFail);
    saga.flow(publishFail, errorEnd);

    return saga.build();
  }
}
