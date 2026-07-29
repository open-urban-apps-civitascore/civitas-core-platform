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

/**
 * Unrelease tears down only the ingest/consumer-access layer (NiFi pipeline + APISIX route) and
 * leaves the data-holding sink (PostGIS table, FROST project) untouched — the data-loss guarantee
 * of #1923. These are the sink-preservation and pipeline-gating checks that the removed
 * BpmnVsCodedEquivalenceTest last covered as a running process.
 */
class DatasetUnreleaseBpmnTest {

  private ProcessEngine processEngine;
  private RuntimeService runtimeService;
  private HistoryService historyService;

  private SagaCommandHandler frostHandler;
  private SagaCommandHandler apisixHandler;
  private SagaCommandHandler pipelineHandler;
  private SagaCommandHandler geoserverHandler;
  private SagaCommandHandler postgisHandler;

  @BeforeEach
  void setUp() {
    frostHandler = mock(SagaCommandHandler.class);
    apisixHandler = mock(SagaCommandHandler.class);
    pipelineHandler = mock(SagaCommandHandler.class);
    geoserverHandler = mock(SagaCommandHandler.class);
    postgisHandler = mock(SagaCommandHandler.class);
    when(frostHandler.adapter()).thenReturn("frost");
    when(apisixHandler.adapter()).thenReturn("apisix");
    when(pipelineHandler.adapter()).thenReturn("nifi");
    when(geoserverHandler.adapter()).thenReturn("geoserver");
    when(postgisHandler.adapter()).thenReturn("postgis");

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);
    registry.register(apisixHandler);
    registry.register(pipelineHandler);
    registry.register(geoserverHandler);
    registry.register(postgisHandler);

    processEngine = FlowableTestSupport.createTestEngine(Map.of("sagaHandlerRegistry", registry));
    runtimeService = processEngine.getRuntimeService();
    historyService = processEngine.getHistoryService();

    processEngine
        .getRepositoryService()
        .createDeployment()
        .addClasspathResource("processes/dataset-unrelease.bpmn")
        .deploy();
  }

  @AfterEach
  void tearDown() {
    if (processEngine != null) {
      processEngine.close();
    }
  }

  @Test
  void tearsDownPipelineAndRouteButKeepsSink() {
    stubPipelineDeleteSuccess();
    // APISIX fails to prove the teardown is best-effort: the process still finishes.
    when(apisixHandler.handle(any()))
        .thenReturn(SagaCommandResult.failure("saga-test-123", "delete-route", "APISIX down"));

    ProcessInstance instance = startProcess(true);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    var inOrder = inOrder(pipelineHandler, apisixHandler);
    inOrder.verify(pipelineHandler).handle(any());
    inOrder.verify(apisixHandler).handle(any());
    // The sink-holding steps must NOT run — the PostGIS table and FROST project survive.
    verify(frostHandler, never()).handle(any());
    verify(postgisHandler, never()).handle(any());
    verify(geoserverHandler, never()).handle(any());
  }

  @Test
  void skipsPipelineWhenNoPipelines() {
    when(apisixHandler.handle(any()))
        .thenReturn(SagaCommandResult.success("saga-test-123", "delete-route", Map.of(), Map.of()));

    ProcessInstance instance = startProcess(false);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    verify(pipelineHandler, never()).handle(any());
    verify(apisixHandler).handle(any());
    verify(frostHandler, never()).handle(any());
    verify(postgisHandler, never()).handle(any());
    verify(geoserverHandler, never()).handle(any());
  }

  private ProcessInstance startProcess(boolean hasPipelines) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-test-123");
    variables.put("datasetId", "ds-456");
    variables.put("serviceId", "s-1");
    variables.put("hasPipelines", hasPipelines);
    if (hasPipelines) {
      variables.put("pipelineIds", List.of("p-1"));
    }
    return runtimeService.startProcessInstanceByKey("dataset-unrelease", variables);
  }

  private void executeAllJobs() {
    FlowableTestSupport.executeAllJobs(processEngine);
  }

  private void assertProcessFinished(String processInstanceId) {
    FlowableTestSupport.assertProcessFinished(historyService, processInstanceId);
  }

  private void stubPipelineDeleteSuccess() {
    when(pipelineHandler.handle(any()))
        .thenReturn(
            SagaCommandResult.success("saga-test-123", "delete-pipelines", Map.of(), Map.of()));
  }
}
