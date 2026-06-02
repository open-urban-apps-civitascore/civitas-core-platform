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

import java.util.Objects;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;

/**
 * Shared scaffold for the dataset provisioning sagas (Create and Update). Both share the same
 * activity sequence (FROST → APISIX → optional Redpanda) and the same compensation topology; only
 * the step IDs and adapter operation codes differ. The varying parts are captured in {@link
 * OpVerbs}, the invariant wiring lives here.
 */
final class DatasetProvisioningSagaTemplate {

  static final OpVerbs CREATE =
      new OpVerbs(
          "create",
          "Create",
          "CREATE",
          "deploy-pipelines",
          "Deploy Pipelines",
          "DEPLOY_PIPELINES",
          "DELETE_PROJECT",
          "DELETE_ROUTE");

  static final OpVerbs UPDATE =
      new OpVerbs(
          "update",
          "Update",
          "UPDATE",
          "update-pipelines",
          "Update Pipelines",
          "UPDATE_PIPELINES",
          "RESTORE_PROJECT",
          "RESTORE_ROUTE");

  private DatasetProvisioningSagaTemplate() {}

  /** Verbs and operation codes that differ between Create and Update sagas. */
  record OpVerbs(
      String stepPrefix,
      String forwardDisplayVerb,
      String forwardOpSuffix,
      String pipelinesStepId,
      String pipelinesDisplayName,
      String pipelinesForwardOp,
      String frostCompensationOp,
      String apisixCompensationOp) {
    OpVerbs {
      Objects.requireNonNull(stepPrefix, "stepPrefix");
      Objects.requireNonNull(forwardDisplayVerb, "forwardDisplayVerb");
      Objects.requireNonNull(forwardOpSuffix, "forwardOpSuffix");
      Objects.requireNonNull(pipelinesStepId, "pipelinesStepId");
      Objects.requireNonNull(pipelinesDisplayName, "pipelinesDisplayName");
      Objects.requireNonNull(pipelinesForwardOp, "pipelinesForwardOp");
      Objects.requireNonNull(frostCompensationOp, "frostCompensationOp");
      Objects.requireNonNull(apisixCompensationOp, "apisixCompensationOp");
    }
  }

  static BpmnModel build(String processId, String processName, OpVerbs v) {
    SagaProcessBuilder saga = SagaProcessBuilder.create(processId, processName);

    StartEvent start = saga.startEvent("start");
    SagaStepRef frost =
        saga.sagaStep(
            v.stepPrefix() + "-project",
            v.forwardDisplayVerb() + " FROST Project",
            "frost",
            v.forwardOpSuffix() + "_PROJECT");
    SagaStepRef apisix =
        saga.sagaStep(
            v.stepPrefix() + "-route",
            v.forwardDisplayVerb() + " APISIX Route",
            "apisix",
            v.forwardOpSuffix() + "_ROUTE");
    ExclusiveGateway pipelineGw =
        saga.exclusiveGateway(ProcessBuilderUtils.PIPELINE_GATEWAY_ID, "Has Pipelines?");
    SagaStepRef redpanda =
        saga.sagaStep(
            v.pipelinesStepId(), v.pipelinesDisplayName(), "redpanda", v.pipelinesForwardOp());

    // Two FROST compensation tasks (after-apisix, after-redpanda) currently delegate to the same
    // adapter operation but are kept as distinct activities so the two error paths remain visually
    // separate in the BPMN diagram and can diverge later (e.g. richer rollback for the redpanda
    // case) without restructuring the flow.
    ServiceTask compFrostAfterApisix =
        saga.compensation("compensate-frost-after-apisix", frost, v.frostCompensationOp());
    ServiceTask compApisixAfterRedpanda =
        saga.compensation("compensate-apisix-after-redpanda", apisix, v.apisixCompensationOp());
    ServiceTask compFrostAfterRedpanda =
        saga.compensation("compensate-frost-after-redpanda", frost, v.frostCompensationOp());

    ServiceTask publishOk = saga.publishResult(ProcessBuilderUtils.PUBLISH_SUCCESS_ID, "success");
    ServiceTask publishFail = saga.publishResult(ProcessBuilderUtils.PUBLISH_FAILURE_ID, "failure");
    EndEvent end = saga.endEvent("end");
    EndEvent errorEnd = saga.endEvent("error-end", "Saga Failed");

    saga.flow(start, frost.task(), apisix.task(), pipelineGw);
    saga.flow(pipelineGw, redpanda.task()).when("${hasPipelines == true}");
    saga.flow(pipelineGw, publishOk).asDefault();
    saga.flow(redpanda.task(), publishOk);
    saga.flow(publishOk, end);

    saga.errorFlow(frost, publishFail);
    saga.errorFlow(apisix, compFrostAfterApisix);
    saga.flow(compFrostAfterApisix, publishFail);
    saga.errorFlow(redpanda, compApisixAfterRedpanda);
    saga.flow(compApisixAfterRedpanda, compFrostAfterRedpanda, publishFail);
    saga.flow(publishFail, errorEnd);

    return saga.build();
  }
}
