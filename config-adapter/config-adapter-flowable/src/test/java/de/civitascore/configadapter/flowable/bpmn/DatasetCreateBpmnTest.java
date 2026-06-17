/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.bpmn;

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
import de.civitascore.configadapter.flowable.FlowableTestSupport;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatasetCreateBpmnTest {

  private ProcessEngine processEngine;
  private RuntimeService runtimeService;
  private HistoryService historyService;

  private SagaCommandHandler frostHandler;
  private SagaCommandHandler apisixHandler;
  private SagaCommandHandler redpandaHandler;
  private SagaCommandHandler geoserverHandler;
  private SagaCommandHandler postgisHandler;

  @BeforeEach
  void setUp() {
    frostHandler = FlowableTestSupport.mockHandler("frost");
    apisixHandler = FlowableTestSupport.mockHandler("apisix");
    redpandaHandler = FlowableTestSupport.mockHandler("nifi");
    geoserverHandler = FlowableTestSupport.mockHandler("geoserver");
    postgisHandler = FlowableTestSupport.mockHandler("postgis");
    stubGeoserverSuccess();
    stubPostgisSuccess();

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);
    registry.register(apisixHandler);
    registry.register(redpandaHandler);
    registry.register(geoserverHandler);
    registry.register(postgisHandler);

    processEngine = FlowableTestSupport.createTestEngine(Map.of("sagaHandlerRegistry", registry));
    runtimeService = processEngine.getRuntimeService();
    historyService = processEngine.getHistoryService();

    RepositoryService repositoryService = processEngine.getRepositoryService();
    repositoryService
        .createDeployment()
        .addClasspathResource("processes/dataset-create.bpmn")
        .deploy();
  }

  @AfterEach
  void tearDown() {
    if (processEngine != null) {
      processEngine.close();
    }
  }

  @Test
  void shouldCompleteHappyPathWithPipelines() {
    stubFrostSuccess();
    stubApisixSuccess();
    stubRedpandaSuccess();

    ProcessInstance instance = startProcess(true);
    executeAllJobs();

    assertProcessCompleted(instance.getId());

    var inOrder = inOrder(frostHandler, apisixHandler, redpandaHandler);
    inOrder.verify(frostHandler).handle(any());
    inOrder.verify(apisixHandler).handle(any());
    inOrder.verify(redpandaHandler).handle(any());
  }

  @Test
  void shouldSkipRedpandaWhenNoPipelines() {
    stubFrostSuccess();
    stubApisixSuccess();

    ProcessInstance instance = startProcess(false);
    executeAllJobs();

    assertProcessCompleted(instance.getId());

    var inOrder = inOrder(frostHandler, apisixHandler);
    inOrder.verify(frostHandler).handle(any());
    inOrder.verify(apisixHandler).handle(any());
    verify(redpandaHandler, never()).handle(any());
  }

  @Test
  void shouldCompensateFrostWhenApisixFails() {
    stubFrostSuccess();
    stubApisixFailure("APISIX connection refused");
    stubFrostCompensationSuccess();

    ProcessInstance instance = startProcess(false);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(frostHandler, times(2)).handle(captor.capture());

    List<SagaCommandMessage> frostCalls = captor.getAllValues();
    assertEquals("EXECUTE_STEP", frostCalls.get(0).type());
    assertEquals("COMPENSATE_STEP", frostCalls.get(1).type());
    assertEquals("DELETE_PROJECT", frostCalls.get(1).operation());
  }

  @Test
  void shouldCompensateInReverseOrderWhenRedpandaFails() {
    stubFrostSuccess();
    stubApisixSuccess();
    stubRedpandaFailure("Pipeline deployment failed");
    stubApisixCompensationSuccess();
    stubFrostCompensationSuccess();

    ProcessInstance instance = startProcess(true);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    ArgumentCaptor<SagaCommandMessage> apisixCaptor =
        ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(apisixHandler, times(2)).handle(apisixCaptor.capture());

    List<SagaCommandMessage> apisixCalls = apisixCaptor.getAllValues();
    SagaCommandMessage apisixCompensation =
        apisixCalls.stream()
            .filter(c -> "COMPENSATE_STEP".equals(c.type()))
            .findFirst()
            .orElseThrow();
    assertEquals("DELETE_ROUTE", apisixCompensation.operation());

    ArgumentCaptor<SagaCommandMessage> frostCaptor =
        ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(frostHandler, times(2)).handle(frostCaptor.capture());

    List<SagaCommandMessage> frostCalls = frostCaptor.getAllValues();
    SagaCommandMessage frostCompensation =
        frostCalls.stream()
            .filter(c -> "COMPENSATE_STEP".equals(c.type()))
            .findFirst()
            .orElseThrow();
    assertEquals("DELETE_PROJECT", frostCompensation.operation());

    // hasGeoSink=false → the pipeline-failure compensation must skip GeoServer entirely.
    verify(geoserverHandler, never()).handle(any());
  }

  @Test
  void shouldPassBaseUrlFromFrostToApisixAsUpstreamUrl() {
    stubFrostSuccess();
    stubApisixSuccess();

    ProcessInstance instance = startProcess(false);
    executeAllJobs();

    assertProcessCompleted(instance.getId());

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(apisixHandler).handle(captor.capture());

    assertEquals(
        "http://frost/v1.1/Projects(1)",
        captor.getValue().payload().get("upstreamUrl"),
        "FROST baseUrl should be mapped to upstreamUrl for APISIX");
  }

  @Test
  void shouldPassFrostBaseUrlToPipelineStep() {
    stubFrostSuccess();
    stubApisixSuccess();
    stubRedpandaSuccess();

    ProcessInstance instance = startProcess(true);
    executeAllJobs();

    assertProcessCompleted(instance.getId());

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(redpandaHandler).handle(captor.capture());

    // The NiFi pipeline adapter declares no field aliases, so the FROST baseUrl reaches the
    // pipeline step under its original key (no baseUrl->targetUrl rename).
    assertEquals(
        "http://frost/v1.1/Projects(1)",
        captor.getValue().payload().get("baseUrl"),
        "FROST baseUrl should reach the pipeline step unrenamed");
  }

  @Test
  void shouldRunGeoServerStepsWhenHasGeoSink() {
    stubFrostSuccess();
    stubApisixSuccess();

    ProcessInstance instance = startProcessWithGeo(false, true, true);
    executeAllJobs();

    assertProcessCompleted(instance.getId());

    // Assert execution order via the handlers' actual invocation order (deterministic). Sorting
    // HistoricActivityInstances by start time is flaky: sequential synchronous tasks can share a
    // millisecond timestamp, so workspace/datastore can appear swapped.
    // PostGIS sink is provisioned first (so the table exists), then the GeoServer steps run.
    var inOrder = inOrder(frostHandler, apisixHandler, postgisHandler, geoserverHandler);
    inOrder.verify(frostHandler).handle(any());
    inOrder.verify(apisixHandler).handle(any());
    inOrder.verify(postgisHandler).handle(any());
    inOrder.verify(geoserverHandler, times(3)).handle(any());

    ArgumentCaptor<SagaCommandMessage> sink = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(postgisHandler).handle(sink.capture());
    assertEquals("PROVISION_SINK", sink.getValue().operation());

    ArgumentCaptor<SagaCommandMessage> geo = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(geoserverHandler, times(3)).handle(geo.capture());
    assertEquals(
        List.of("CREATE_WORKSPACE", "CREATE_DATASTORE", "PROVISION_LAYERS"),
        geo.getAllValues().stream().map(SagaCommandMessage::operation).toList());
  }

  @Test
  void shouldSkipLayerProvisioningWhenNoLayers() {
    stubFrostSuccess();
    stubApisixSuccess();

    ProcessInstance instance = startProcessWithGeo(false, true, false);
    executeAllJobs();

    assertProcessCompleted(instance.getId());

    var inOrder = inOrder(frostHandler, apisixHandler, postgisHandler, geoserverHandler);
    inOrder.verify(frostHandler).handle(any());
    inOrder.verify(apisixHandler).handle(any());
    inOrder.verify(postgisHandler).handle(any());

    // Only workspace + datastore run; provision-layers is skipped — so exactly 2 geoserver calls,
    // in invocation order (deterministic, unlike a HistoricActivityInstance start-time sort).
    ArgumentCaptor<SagaCommandMessage> geo = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(geoserverHandler, times(2)).handle(geo.capture());
    assertEquals(
        List.of("CREATE_WORKSPACE", "CREATE_DATASTORE"),
        geo.getAllValues().stream().map(SagaCommandMessage::operation).toList());
  }

  @Test
  void shouldCompensateGeoServerWhenRedpandaFailsWithGeoSink() {
    stubFrostSuccess();
    stubApisixSuccess();
    stubRedpandaFailure("Pipeline deployment failed");
    stubApisixCompensationSuccess();
    stubFrostCompensationSuccess();

    ProcessInstance instance = startProcessWithGeo(true, true, true);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    // GeoServer ran (hasGeoSink), so the pipeline failure must compensate it: 3 forward steps
    // (workspace, datastore, layers) followed by the DELETE_WORKSPACE compensation.
    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(geoserverHandler, times(4)).handle(captor.capture());
    SagaCommandMessage compensation = captor.getAllValues().get(3);
    assertEquals("COMPENSATE_STEP", compensation.type());
    assertEquals("DELETE_WORKSPACE", compensation.operation());
  }

  @Test
  void shouldDeleteWorkspaceWhenGeoServerStepFails() {
    stubFrostSuccess();
    stubApisixSuccess();
    stubFrostCompensationSuccess();
    stubApisixCompensationSuccess();
    stubGeoserverDatastoreFailure();

    ProcessInstance instance = startProcessWithGeo(false, true, true);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    // create-workspace (execute) + create-datastore (execute, fails) + compensate
    // (DELETE_WORKSPACE)
    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(geoserverHandler, times(3)).handle(captor.capture());
    SagaCommandMessage geoCompensation =
        captor.getAllValues().stream()
            .filter(c -> "COMPENSATE_STEP".equals(c.type()))
            .findFirst()
            .orElseThrow();
    assertEquals("DELETE_WORKSPACE", geoCompensation.operation());

    SagaCommandMessage apisixCompensation = captureCompensation(apisixHandler);
    assertEquals("DELETE_ROUTE", apisixCompensation.operation());
    SagaCommandMessage frostCompensation = captureCompensation(frostHandler);
    assertEquals("DELETE_PROJECT", frostCompensation.operation());

    // The PostGIS sink was provisioned (forward) and dropped during compensation.
    ArgumentCaptor<SagaCommandMessage> sink = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(postgisHandler, times(2)).handle(sink.capture());
    assertEquals(
        List.of("PROVISION_SINK", "DEPROVISION_SINK"),
        sink.getAllValues().stream().map(SagaCommandMessage::operation).toList());
  }

  private SagaCommandMessage captureCompensation(SagaCommandHandler handler) {
    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(handler, times(2)).handle(captor.capture());
    return captor.getAllValues().stream()
        .filter(c -> "COMPENSATE_STEP".equals(c.type()))
        .findFirst()
        .orElseThrow();
  }

  private ProcessInstance startProcess(boolean hasPipelines) {
    return startProcessWithGeo(hasPipelines, false, false);
  }

  private ProcessInstance startProcessWithGeo(
      boolean hasPipelines, boolean hasGeoSink, boolean hasLayers) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-test-123");
    variables.put("datasetId", "ds-456");
    variables.put("datasetName", "Test Dataset");
    variables.put("description", "A test dataset");
    variables.put("hasPipelines", hasPipelines);
    variables.put("hasGeoSink", hasGeoSink);
    variables.put("hasLayers", hasLayers);
    if (hasPipelines) {
      variables.put("dataPipelines", List.of(Map.of("id", "p-1", "data", "{}")));
      variables.put("datasources", List.of(Map.of("id", "src-1", "type", "postgresql")));
    }
    if (hasGeoSink) {
      variables.put(
          "datasinks",
          List.of(Map.of("type", "POSTGIS", "configuration", Map.of("tableName", "t1"))));
    }
    if (hasLayers) {
      variables.put("layers", List.of(Map.of("layerName", "t1", "crs", "EPSG:4326")));
    }
    return runtimeService.startProcessInstanceByKey("dataset-create", variables);
  }

  private void executeAllJobs() {
    FlowableTestSupport.executeAllJobs(processEngine);
  }

  private void assertProcessCompleted(String processInstanceId) {
    FlowableTestSupport.assertProcessCompleted(historyService, processInstanceId);
  }

  private void assertProcessFinished(String processInstanceId) {
    FlowableTestSupport.assertProcessFinished(historyService, processInstanceId);
  }

  private void stubFrostSuccess() {
    when(frostHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "saga-test-123",
                "create-project",
                Map.of("projectId", "proj-789", "baseUrl", "http://frost/v1.1/Projects(1)"),
                Map.of("projectId", "proj-789")));
  }

  private void stubApisixSuccess() {
    when(apisixHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "saga-test-123",
                "create-route",
                Map.of("routeId", "r-1", "serviceId", "s-1"),
                Map.of("routeId", "r-1", "serviceId", "s-1")));
  }

  private void stubRedpandaSuccess() {
    when(redpandaHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "saga-test-123",
                "deploy-pipelines",
                Map.of("pipelineIds", List.of("p-1")),
                Map.of("pipelineIds", List.of("p-1"))));
  }

  private void stubApisixFailure(String error) {
    when(apisixHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.failure("saga-test-123", "create-route", error));
  }

  private void stubRedpandaFailure(String error) {
    when(redpandaHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.failure("saga-test-123", "deploy-pipelines", error));
  }

  private void stubFrostCompensationSuccess() {
    when(frostHandler.handle(argThat(cmd -> cmd != null && "COMPENSATE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-test-123", "create-project"));
  }

  private void stubApisixCompensationSuccess() {
    when(apisixHandler.handle(argThat(cmd -> cmd != null && "COMPENSATE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-test-123", "create-route"));
  }

  private void stubPostgisSuccess() {
    when(postgisHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "saga-test-123",
                "provision-sink",
                Map.of("provisionedSinks", List.of(Map.of("schema", "ds_456", "table", "t1"))),
                Map.of("provisionedSinks", List.of(Map.of("schema", "ds_456", "table", "t1")))));
    when(postgisHandler.handle(argThat(cmd -> cmd != null && "COMPENSATE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-test-123", "provision-sink"));
  }

  private void stubGeoserverSuccess() {
    when(geoserverHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "saga-test-123",
                "create-workspace",
                Map.of("workspaceName", "ds_456"),
                Map.of("workspaceName", "ds_456")));
    when(geoserverHandler.handle(
            argThat(cmd -> cmd != null && "COMPENSATE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-test-123", "create-workspace"));
  }

  private void stubGeoserverDatastoreFailure() {
    when(geoserverHandler.handle(
            argThat(
                cmd ->
                    cmd != null
                        && "EXECUTE_STEP".equals(cmd.type())
                        && "CREATE_DATASTORE".equals(cmd.operation()))))
        .thenReturn(
            SagaCommandResult.failure("saga-test-123", "create-datastore", "datastore failed"));
  }
}
