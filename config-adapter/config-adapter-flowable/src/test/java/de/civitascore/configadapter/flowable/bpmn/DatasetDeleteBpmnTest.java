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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.FlowableTestSupport;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatasetDeleteBpmnTest {

  private ProcessEngine processEngine;
  private RuntimeService runtimeService;
  private HistoryService historyService;

  private SagaCommandHandler frostHandler;
  private SagaCommandHandler apisixHandler;
  private SagaCommandHandler pipelineHandler;
  private SagaCommandHandler geoserverHandler;

  @BeforeEach
  void setUp() {
    frostHandler = mock(SagaCommandHandler.class);
    apisixHandler = mock(SagaCommandHandler.class);
    pipelineHandler = mock(SagaCommandHandler.class);
    geoserverHandler = mock(SagaCommandHandler.class);
    when(frostHandler.adapter()).thenReturn("frost");
    when(apisixHandler.adapter()).thenReturn("apisix");
    when(pipelineHandler.adapter()).thenReturn("nifi");
    when(geoserverHandler.adapter()).thenReturn("geoserver");

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);
    registry.register(apisixHandler);
    registry.register(pipelineHandler);
    registry.register(geoserverHandler);

    processEngine = FlowableTestSupport.createTestEngine(Map.of("sagaHandlerRegistry", registry));
    runtimeService = processEngine.getRuntimeService();
    historyService = processEngine.getHistoryService();

    processEngine
        .getRepositoryService()
        .createDeployment()
        .addClasspathResource("processes/dataset-delete.bpmn")
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
    stubPipelineDeleteSuccess();
    stubApisixDeleteSuccess();
    stubFrostDeleteSuccess();

    ProcessInstance instance = startProcess(true);
    executeAllJobs();

    assertProcessCompleted(instance.getId());

    verify(pipelineHandler).handle(any());
    verify(apisixHandler).handle(any());
    verify(frostHandler).handle(any());

    var inOrder = inOrder(pipelineHandler, apisixHandler, frostHandler);
    inOrder.verify(pipelineHandler).handle(any());
    inOrder.verify(apisixHandler).handle(any());
    inOrder.verify(frostHandler).handle(any());
  }

  @Test
  void shouldSkipPipelineWhenNoPipelines() {
    stubApisixDeleteSuccess();
    stubFrostDeleteSuccess();

    ProcessInstance instance = startProcess(false);
    executeAllJobs();

    assertProcessCompleted(instance.getId());

    verify(pipelineHandler, never()).handle(any());

    var inOrder = inOrder(apisixHandler, frostHandler);
    inOrder.verify(apisixHandler).handle(any());
    inOrder.verify(frostHandler).handle(any());
  }

  @Test
  void shouldContinueOnStepFailureBestEffort() {
    stubPipelineDeleteSuccess();
    when(apisixHandler.handle(any()))
        .thenReturn(SagaCommandResult.failure("saga-test-123", "delete-route", "APISIX down"));
    stubFrostDeleteSuccess();

    ProcessInstance instance = startProcess(true);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    verify(pipelineHandler).handle(any());
    verify(apisixHandler).handle(any());
    verify(frostHandler).handle(any());
  }

  @Test
  void shouldContinueEvenWhenMultipleStepsFail() {
    when(pipelineHandler.handle(any()))
        .thenReturn(
            SagaCommandResult.failure("saga-test-123", "delete-pipelines", "Pipeline down"));
    when(apisixHandler.handle(any()))
        .thenReturn(SagaCommandResult.failure("saga-test-123", "delete-route", "APISIX down"));
    when(frostHandler.handle(any()))
        .thenReturn(SagaCommandResult.failure("saga-test-123", "delete-project", "FROST down"));

    ProcessInstance instance = startProcess(true);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    verify(pipelineHandler).handle(any());
    verify(apisixHandler).handle(any());
    verify(frostHandler).handle(any());
  }

  // GeoServer teardown on delete (hasGeoSink derived from a realistic trigger, not a preset
  // variable) is covered end-to-end through FlowableTriggerConsumer in DatasetDeleteTriggerTest.

  private ProcessInstance startProcess(boolean hasPipelines) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-test-123");
    variables.put("datasetId", "ds-456");
    variables.put("projectId", "proj-789");
    variables.put("routeId", "r-1");
    variables.put("serviceId", "s-1");
    variables.put("hasPipelines", hasPipelines);
    if (hasPipelines) {
      variables.put("pipelineIds", List.of("p-1"));
    }
    return runtimeService.startProcessInstanceByKey("dataset-delete", variables);
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

  private void stubPipelineDeleteSuccess() {
    when(pipelineHandler.handle(any()))
        .thenReturn(
            SagaCommandResult.success("saga-test-123", "delete-pipelines", Map.of(), Map.of()));
  }

  private void stubApisixDeleteSuccess() {
    when(apisixHandler.handle(any()))
        .thenReturn(SagaCommandResult.success("saga-test-123", "delete-route", Map.of(), Map.of()));
  }

  private void stubFrostDeleteSuccess() {
    when(frostHandler.handle(any()))
        .thenReturn(
            SagaCommandResult.success("saga-test-123", "delete-project", Map.of(), Map.of()));
  }
}
