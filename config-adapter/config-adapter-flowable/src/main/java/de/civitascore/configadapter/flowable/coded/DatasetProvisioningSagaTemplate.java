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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;

/**
 * Shared scaffold for the dataset provisioning sagas (Create and Update). Both share the same
 * activity sequence (FROST → APISIX → conditional GeoServer → conditional Redpanda) and the same
 * reverse-order compensation chain; only the step IDs and adapter operation codes differ. The
 * varying parts are captured in {@link OpVerbs}, the invariant wiring lives here.
 *
 * <p>The GeoServer branch is gated on {@code hasGeoSink} and is dormant until the saga trigger
 * carries a {@code POSTGIS} data sink. Create models it fine-grained (workspace → datastore →
 * conditional layers); Update collapses it to a single {@code UPDATE_WORKSPACE} step.
 */
final class DatasetProvisioningSagaTemplate {

  private static final String GEOSERVER_ADAPTER = "geoserver";
  private static final String POSTGIS_ADAPTER = "postgis";
  private static final String HAS_GEO_SINK_CONDITION =
      "${execution.getVariable('hasGeoSink') == true}";

  static final OpVerbs CREATE =
      new OpVerbs(
          "create",
          "Create",
          "CREATE",
          "deploy-pipelines",
          "Deploy Pipelines",
          "DEPLOY_PIPELINES",
          "DELETE_PROJECT",
          "DELETE_ROUTE",
          true,
          "DELETE_WORKSPACE");

  static final OpVerbs UPDATE =
      new OpVerbs(
          "update",
          "Update",
          "UPDATE",
          "update-pipelines",
          "Update Pipelines",
          "UPDATE_PIPELINES",
          "RESTORE_PROJECT",
          "RESTORE_ROUTE",
          false,
          "RESTORE_WORKSPACE");

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
      String apisixCompensationOp,
      boolean fineGrainedGeo,
      String geoCompensationOp) {
    OpVerbs {
      Objects.requireNonNull(stepPrefix, "stepPrefix");
      Objects.requireNonNull(forwardDisplayVerb, "forwardDisplayVerb");
      Objects.requireNonNull(forwardOpSuffix, "forwardOpSuffix");
      Objects.requireNonNull(pipelinesStepId, "pipelinesStepId");
      Objects.requireNonNull(pipelinesDisplayName, "pipelinesDisplayName");
      Objects.requireNonNull(pipelinesForwardOp, "pipelinesForwardOp");
      Objects.requireNonNull(frostCompensationOp, "frostCompensationOp");
      Objects.requireNonNull(apisixCompensationOp, "apisixCompensationOp");
      Objects.requireNonNull(geoCompensationOp, "geoCompensationOp");
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

    ExclusiveGateway geoGw =
        saga.exclusiveGateway(ProcessBuilderUtils.GEO_GATEWAY_ID, "Has Geo Sink?");
    ExclusiveGateway pipelineGw =
        saga.exclusiveGateway(ProcessBuilderUtils.PIPELINE_GATEWAY_ID, "Has Pipelines?");
    SagaStepRef redpanda =
        saga.sagaStep(
            v.pipelinesStepId(), v.pipelinesDisplayName(), "redpanda", v.pipelinesForwardOp());

    // SQL sink provisioning (Create only): the PostGIS table the GeoServer datastore reads must
    // exist before the workspace/datastore/layers steps, so it runs first in the geo branch.
    SagaStepRef sqlSink = null;
    List<SagaStepRef> geoSteps = new ArrayList<>();
    if (v.fineGrainedGeo()) {
      sqlSink =
          saga.sagaStep(
              "provision-sink", "Provision PostGIS Sink", POSTGIS_ADAPTER, "PROVISION_SINK");
      geoSteps.add(
          saga.sagaStep(
              "create-workspace",
              "Create GeoServer Workspace",
              GEOSERVER_ADAPTER,
              "CREATE_WORKSPACE"));
      geoSteps.add(
          saga.sagaStep(
              "create-datastore",
              "Create GeoServer Datastore",
              GEOSERVER_ADAPTER,
              "CREATE_DATASTORE"));
      geoSteps.add(
          saga.sagaStep(
              "provision-layers",
              "Provision GeoServer Layers",
              GEOSERVER_ADAPTER,
              "PROVISION_LAYERS"));
    } else {
      geoSteps.add(
          saga.sagaStep(
              "update-workspace",
              "Update GeoServer Workspace",
              GEOSERVER_ADAPTER,
              "UPDATE_WORKSPACE"));
    }
    // The compensation undoes the whole GeoServer branch by deleting/restoring the workspace; it
    // references the first geo step so it reads that step's compensationData (the workspaceName).
    SagaStepRef geoCompTarget = geoSteps.get(0);

    ServiceTask compGeoserver =
        saga.compensation("compensate-geoserver", geoCompTarget, v.geoCompensationOp());
    // SQL sink compensation drops the table/role created by PROVISION_SINK (Create only).
    ServiceTask compSql =
        sqlSink == null ? null : saga.compensation("compensate-sink", sqlSink, "DEPROVISION_SINK");
    ServiceTask compApisix =
        saga.compensation("compensate-apisix", apisix, v.apisixCompensationOp());
    ServiceTask compFrost = saga.compensation("compensate-frost", frost, v.frostCompensationOp());

    ServiceTask publishOk = saga.publishResult(ProcessBuilderUtils.PUBLISH_SUCCESS_ID, "success");
    ServiceTask publishFail = saga.publishResult(ProcessBuilderUtils.PUBLISH_FAILURE_ID, "failure");
    EndEvent end = saga.endEvent("end");
    EndEvent errorEnd = saga.endEvent("error-end", "Saga Failed");

    // Happy path: frost → apisix → geo-gateway
    saga.flow(start, frost.task(), apisix.task(), geoGw);
    wireGeoBranch(saga, v, geoGw, sqlSink, geoSteps, pipelineGw);
    saga.flow(pipelineGw, redpanda.task()).when("${hasPipelines == true}");
    saga.flow(pipelineGw, publishOk).asDefault();
    saga.flow(redpanda.task(), publishOk);
    saga.flow(publishOk, end);

    // Compensation: one reverse chain (geoserver → sink → apisix → frost), entered at the right
    // point. The SQL sink compensation sits between geoserver and apisix (Create only).
    saga.errorFlow(frost, publishFail);
    saga.errorFlow(apisix, compFrost);
    if (sqlSink != null) {
      // PROVISION_SINK is transactional: on failure nothing was committed, so there is no sink or
      // GeoServer state to undo — go straight to the APISIX/FROST rollback.
      saga.errorFlow(sqlSink, compApisix);
    }
    // A GeoServer step only runs when hasGeoSink, so its failure always compensates the workspace.
    for (SagaStepRef geo : geoSteps) {
      saga.errorFlow(geo, compGeoserver);
    }
    // A pipeline failure compensates the GeoServer branch only if it actually ran (hasGeoSink);
    // otherwise it skips straight to the APISIX/FROST compensation — no spurious DELETE_WORKSPACE.
    ExclusiveGateway redpandaCompGw =
        saga.exclusiveGateway("redpanda-comp-gateway", "Compensate GeoServer?");
    saga.errorFlow(redpanda, redpandaCompGw);
    saga.flow(redpandaCompGw, compGeoserver).when(HAS_GEO_SINK_CONDITION);
    saga.flow(redpandaCompGw, compApisix).asDefault();
    if (compSql != null) {
      saga.flow(compGeoserver, compSql, compApisix, compFrost, publishFail);
    } else {
      saga.flow(compGeoserver, compApisix, compFrost, publishFail);
    }
    saga.flow(publishFail, errorEnd);

    return saga.build();
  }

  private static void wireGeoBranch(
      SagaProcessBuilder saga,
      OpVerbs v,
      ExclusiveGateway geoGw,
      SagaStepRef sqlSink,
      List<SagaStepRef> geoSteps,
      ExclusiveGateway pipelineGw) {
    // execution.getVariable(...) is null-safe: an absent flag evaluates to false (default flow),
    // matching the engine's behavior when the trigger carries no geo data.
    saga.flow(geoGw, pipelineGw).asDefault();
    if (v.fineGrainedGeo()) {
      // geo-gateway → provision-sink → workspace → datastore → [hasLayers] layers → pipelines
      saga.flow(geoGw, sqlSink.task()).when(HAS_GEO_SINK_CONDITION);
      ServiceTask workspace = geoSteps.get(0).task();
      ServiceTask datastore = geoSteps.get(1).task();
      ServiceTask layers = geoSteps.get(2).task();
      ExclusiveGateway layersGw =
          saga.exclusiveGateway(ProcessBuilderUtils.LAYERS_GATEWAY_ID, "Has Layers?");
      saga.flow(sqlSink.task(), workspace);
      saga.flow(workspace, datastore, layersGw);
      saga.flow(layersGw, layers).when("${execution.getVariable('hasLayers') == true}");
      saga.flow(layersGw, pipelineGw).asDefault();
      saga.flow(layers, pipelineGw);
    } else {
      saga.flow(geoGw, geoSteps.get(0).task()).when(HAS_GEO_SINK_CONDITION);
      saga.flow(geoSteps.get(0).task(), pipelineGw);
    }
  }
}
