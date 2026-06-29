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
 * Builds the Dataset Delete saga process programmatically. Reverse order (Pipeline → APISIX →
 * conditional GeoServer → conditional PostGIS sink → FROST), best-effort: on failure, continue to
 * next step. No compensation. Produces equivalent behavior to {@code dataset-delete.bpmn}.
 *
 * <p>The GeoServer teardown ({@code DELETE_WORKSPACE}, recursive) and the PostGIS sink teardown
 * ({@code DEPROVISION_SINK}) are gated on {@code hasGeoSink} and run after the APISIX route is
 * removed. GeoServer stops publishing before the PostGIS table it reads is dropped.
 */
public final class DatasetDeleteProcessBuilder {

  private DatasetDeleteProcessBuilder() {}

  public static BpmnModel build() {
    SagaProcessBuilder saga =
        SagaProcessBuilder.create("dataset-delete", "Dataset Delete Saga (Coded)");

    StartEvent start = saga.startEvent("start");
    ExclusiveGateway pipelineGw =
        saga.exclusiveGateway(ProcessBuilderUtils.PIPELINE_GATEWAY_ID, "Has Pipelines?");
    SagaStepRef pipeline =
        saga.sagaStep("delete-pipelines", "Delete Pipelines", "nifi", "DELETE_PIPELINES");
    SagaStepRef apisix =
        saga.sagaStep("delete-route", "Delete APISIX Route", "apisix", "DELETE_ROUTE");
    ExclusiveGateway geoGw =
        saga.exclusiveGateway(ProcessBuilderUtils.GEO_GATEWAY_ID, "Has Geo Sink?");
    SagaStepRef geoserver =
        saga.sagaStep(
            "delete-workspace", "Delete GeoServer Workspace", "geoserver", "DELETE_WORKSPACE");
    SagaStepRef sqlSink =
        saga.sagaStep(
            "deprovision-sink", "Deprovision PostGIS Sink", "postgis", "DEPROVISION_SINK");
    SagaStepRef frost =
        saga.sagaStep("delete-project", "Delete FROST Project", "frost", "DELETE_PROJECT");

    ExclusiveGateway resultGw =
        saga.exclusiveGateway(ProcessBuilderUtils.RESULT_GATEWAY_ID, "Has Errors?");
    ServiceTask publishOk = saga.publishResult(ProcessBuilderUtils.PUBLISH_SUCCESS_ID, "success");
    ServiceTask publishFail =
        saga.publishResult(ProcessBuilderUtils.PUBLISH_FAILURE_ID, "failure", false);
    EndEvent end = saga.endEvent("end");

    // Happy path
    saga.flow(start, pipelineGw);
    saga.flow(pipelineGw, pipeline.task()).when("${hasPipelines == true}");
    saga.flow(pipelineGw, apisix.task()).asDefault();
    saga.flow(pipeline.task(), apisix.task(), geoGw);
    saga.flow(geoGw, geoserver.task()).when("${execution.getVariable('hasGeoSink') == true}");
    saga.flow(geoGw, frost.task()).asDefault();
    // GeoServer stops publishing before the PostGIS table it reads is dropped.
    saga.flow(geoserver.task(), sqlSink.task(), frost.task());
    saga.flow(frost.task(), resultGw);
    saga.flow(resultGw, publishFail).when("${execution.getVariable('sagaError') != null}");
    saga.flow(resultGw, publishOk).asDefault();
    saga.flow(publishOk, end);
    saga.flow(publishFail, end);

    // Best-effort: error on any step continues to the next
    saga.errorFlow(pipeline, apisix.task());
    saga.errorFlow(apisix, geoGw);
    saga.errorFlow(geoserver, sqlSink.task());
    saga.errorFlow(sqlSink, frost.task());
    saga.errorFlow(frost, resultGw);

    return saga.build();
  }
}
