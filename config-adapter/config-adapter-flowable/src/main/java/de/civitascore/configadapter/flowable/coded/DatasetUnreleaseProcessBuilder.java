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
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.StartEvent;

/**
 * Builds the Dataset Unrelease saga process programmatically. Tears down only the ingest and
 * consumer-access layer (Pipeline → APISIX route/upstream), leaving the data-holding sink (PostGIS
 * table, FROST project) untouched so a later re-release reuses it. Best-effort: on failure,
 * continue to the next step. No compensation. Produces equivalent behavior to {@code
 * dataset-unrelease.bpmn}.
 *
 * <p>Deliberately has no FROST step: the FROST project is always {@code public=false}, so the only
 * consumer path to it is the APISIX upstream — {@code DELETE_ROUTE} removes that upstream, making
 * FROST unreachable while its data container survives. Deliberately has no {@code
 * DEPROVISION_SINK}: the PostGIS table must persist across an unrelease.
 */
public final class DatasetUnreleaseProcessBuilder {

  private DatasetUnreleaseProcessBuilder() {}

  public static BpmnModel build() {
    SagaProcessBuilder saga =
        SagaProcessBuilder.create("dataset-unrelease", "Dataset Unrelease Saga (Coded)");

    StartEvent start = saga.startEvent("start");
    ExclusiveGateway pipelineGw =
        saga.exclusiveGateway(ProcessBuilderUtils.PIPELINE_GATEWAY_ID, "Has Pipelines?");
    SagaStepRef pipeline =
        saga.sagaStep("delete-pipelines", "Delete Pipelines", "nifi", "DELETE_PIPELINES");
    SagaStepRef apisix =
        saga.sagaStep("delete-route", "Delete APISIX Route", "apisix", "DELETE_ROUTE");

    ExclusiveGateway resultGw = saga.resultRouting();

    // Happy path
    saga.flow(start, pipelineGw);
    saga.flow(pipelineGw, pipeline.task()).when("${hasPipelines == true}");
    saga.flow(pipelineGw, apisix.task()).asDefault();
    saga.flow(pipeline.task(), apisix.task(), resultGw);

    // Best-effort: error on any step continues to the next
    saga.errorFlow(pipeline, apisix.task());
    saga.errorFlow(apisix, resultGw);

    return saga.build();
  }
}
