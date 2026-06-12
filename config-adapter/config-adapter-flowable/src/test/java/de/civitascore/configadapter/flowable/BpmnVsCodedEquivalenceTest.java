/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.bpmn.BpmnProcessDeployer;
import de.civitascore.configadapter.flowable.coded.CodedProcessDeployer;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class BpmnVsCodedEquivalenceTest {

  static Stream<Arguments> approaches() {
    return Stream.of(Arguments.of("BPMN XML", true), Arguments.of("Java Coded", false));
  }

  private ProcessEngine createEngine(boolean useBpmn, SagaHandlerRegistry registry) {
    ProcessEngine engine =
        FlowableTestSupport.createTestEngine(Map.of("sagaHandlerRegistry", registry));
    if (useBpmn) {
      BpmnProcessDeployer.deploy(engine.getRepositoryService());
    } else {
      CodedProcessDeployer.deploy(engine.getRepositoryService());
    }
    return engine;
  }

  @ParameterizedTest(name = "{0}: Dataset Create happy path with pipelines")
  @MethodSource("approaches")
  void datasetCreateHappyPath(String approach, boolean useBpmn) {
    SagaCommandHandler frost = FlowableTestSupport.mockHandler("frost");
    SagaCommandHandler apisix = FlowableTestSupport.mockHandler("apisix");
    SagaCommandHandler redpanda = FlowableTestSupport.mockHandler("redpanda");

    when(frost.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "s",
                "create-project",
                Map.of("projectId", "p1", "baseUrl", "http://frost"),
                Map.of("projectId", "p1")));
    when(apisix.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "s", "create-route", Map.of("routeId", "r1"), Map.of("routeId", "r1")));
    when(redpanda.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "s", "deploy-pipelines", Map.of("pipelineIds", List.of("p1")), Map.of()));

    SagaHandlerRegistry reg = FlowableTestSupport.registry(frost, apisix, redpanda);
    ProcessEngine engine = createEngine(useBpmn, reg);

    try {
      ProcessInstance instance =
          engine
              .getRuntimeService()
              .startProcessInstanceByKey("dataset-create", createVariables(true));
      FlowableTestSupport.executeAllJobs(engine);

      FlowableTestSupport.assertProcessCompleted(engine.getHistoryService(), instance.getId());
      var inOrder = inOrder(frost, apisix, redpanda);
      inOrder.verify(frost).handle(any());
      inOrder.verify(apisix).handle(any());
      inOrder.verify(redpanda).handle(any());
    } finally {
      engine.close();
    }
  }

  @ParameterizedTest(name = "{0}: Dataset Create skip redpanda")
  @MethodSource("approaches")
  void datasetCreateSkipRedpanda(String approach, boolean useBpmn) {
    SagaCommandHandler frost = FlowableTestSupport.mockHandler("frost");
    SagaCommandHandler apisix = FlowableTestSupport.mockHandler("apisix");
    SagaCommandHandler redpanda = FlowableTestSupport.mockHandler("redpanda");

    when(frost.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "s",
                "create-project",
                Map.of("projectId", "p1", "baseUrl", "http://frost"),
                Map.of()));
    when(apisix.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success("s", "create-route", Map.of("routeId", "r1"), Map.of()));

    SagaHandlerRegistry reg = FlowableTestSupport.registry(frost, apisix, redpanda);
    ProcessEngine engine = createEngine(useBpmn, reg);

    try {
      ProcessInstance instance =
          engine
              .getRuntimeService()
              .startProcessInstanceByKey("dataset-create", createVariables(false));
      FlowableTestSupport.executeAllJobs(engine);

      var inOrder = inOrder(frost, apisix);
      inOrder.verify(frost).handle(any());
      inOrder.verify(apisix).handle(any());
      verify(redpanda, never()).handle(any());
    } finally {
      engine.close();
    }
  }

  @ParameterizedTest(name = "{0}: Dataset Delete best-effort continues on failure")
  @MethodSource("approaches")
  void datasetDeleteBestEffort(String approach, boolean useBpmn) {
    SagaCommandHandler frost = FlowableTestSupport.mockHandler("frost");
    SagaCommandHandler apisix = FlowableTestSupport.mockHandler("apisix");
    SagaCommandHandler redpanda = FlowableTestSupport.mockHandler("redpanda");

    when(redpanda.handle(any()))
        .thenReturn(SagaCommandResult.success("s", "delete-pipelines", Map.of(), Map.of()));
    when(apisix.handle(any()))
        .thenReturn(SagaCommandResult.failure("s", "delete-route", "APISIX down"));
    when(frost.handle(any()))
        .thenReturn(SagaCommandResult.success("s", "delete-project", Map.of(), Map.of()));

    SagaHandlerRegistry reg = FlowableTestSupport.registry(frost, apisix, redpanda);
    ProcessEngine engine = createEngine(useBpmn, reg);

    try {
      Map<String, Object> vars = new HashMap<>();
      vars.put("sagaId", "s");
      vars.put("datasetId", "ds");
      vars.put("projectId", "p1");
      vars.put("routeId", "r1");
      vars.put("serviceId", "s1");
      vars.put("hasPipelines", true);
      vars.put("pipelineIds", List.of("p1"));

      ProcessInstance instance =
          engine.getRuntimeService().startProcessInstanceByKey("dataset-delete", vars);
      FlowableTestSupport.executeAllJobs(engine);

      var inOrder = inOrder(redpanda, apisix, frost);
      inOrder.verify(redpanda).handle(any());
      inOrder.verify(apisix).handle(any());
      inOrder.verify(frost).handle(any());
      FlowableTestSupport.assertProcessFinished(engine.getHistoryService(), instance.getId());
    } finally {
      engine.close();
    }
  }

  @ParameterizedTest(name = "{0}: Dataset Create with geo sink runs GeoServer steps")
  @MethodSource("approaches")
  void datasetCreateWithGeoSink(String approach, boolean useBpmn) {
    SagaCommandHandler frost = FlowableTestSupport.mockHandler("frost");
    SagaCommandHandler apisix = FlowableTestSupport.mockHandler("apisix");
    SagaCommandHandler geoserver = FlowableTestSupport.mockHandler("geoserver");
    SagaCommandHandler postgis = FlowableTestSupport.mockHandler("postgis");

    when(frost.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "s",
                "create-project",
                Map.of("projectId", "p1", "baseUrl", "http://frost"),
                Map.of()));
    when(apisix.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success("s", "create-route", Map.of("routeId", "r1"), Map.of()));
    when(postgis.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.success("s", "provision-sink", Map.of(), Map.of()));
    when(geoserver.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "s", "create-workspace", Map.of("workspaceName", "ds"), Map.of()));

    SagaHandlerRegistry reg = FlowableTestSupport.registry(frost, apisix, geoserver, postgis);
    ProcessEngine engine = createEngine(useBpmn, reg);

    try {
      Map<String, Object> vars = createVariables(false);
      vars.put("hasGeoSink", true);
      vars.put("hasLayers", true);
      vars.put(
          "datasinks",
          List.of(Map.of("type", "POSTGIS", "configuration", Map.of("tableName", "t1"))));
      vars.put("layers", List.of(Map.of("layerName", "t1", "crs", "EPSG:4326")));

      ProcessInstance instance =
          engine.getRuntimeService().startProcessInstanceByKey("dataset-create", vars);
      FlowableTestSupport.executeAllJobs(engine);

      FlowableTestSupport.assertProcessCompleted(engine.getHistoryService(), instance.getId());

      // Order across adapters: FROST → APISIX → PostGIS sink → GeoServer (3 geo steps).
      var inOrder = inOrder(frost, apisix, postgis, geoserver);
      inOrder.verify(frost).handle(any());
      inOrder.verify(apisix).handle(any());
      inOrder.verify(postgis).handle(any());
      inOrder.verify(geoserver, times(3)).handle(any());

      // ...and the exact operations dispatched — BPMN and coded must produce the same sequence,
      // and the GeoServer branch must run workspace → datastore → layers.
      ArgumentCaptor<SagaCommandMessage> frostCmd =
          ArgumentCaptor.forClass(SagaCommandMessage.class);
      verify(frost).handle(frostCmd.capture());
      assertEquals("CREATE_PROJECT", frostCmd.getValue().operation());

      ArgumentCaptor<SagaCommandMessage> apisixCmd =
          ArgumentCaptor.forClass(SagaCommandMessage.class);
      verify(apisix).handle(apisixCmd.capture());
      assertEquals("CREATE_ROUTE", apisixCmd.getValue().operation());

      ArgumentCaptor<SagaCommandMessage> geoCmd = ArgumentCaptor.forClass(SagaCommandMessage.class);
      verify(geoserver, times(3)).handle(geoCmd.capture());
      assertEquals(
          List.of("CREATE_WORKSPACE", "CREATE_DATASTORE", "PROVISION_LAYERS"),
          geoCmd.getAllValues().stream().map(SagaCommandMessage::operation).toList());
    } finally {
      engine.close();
    }
  }

  private Map<String, Object> createVariables(boolean hasPipelines) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("sagaId", "s");
    vars.put("datasetId", "ds");
    vars.put("datasetName", "Test");
    vars.put("description", "Test");
    vars.put("hasPipelines", hasPipelines);
    if (hasPipelines) {
      vars.put("dataPipelines", List.of(Map.of("id", "p1")));
      vars.put("datasources", List.of(Map.of("id", "src1")));
    }
    return vars;
  }
}
