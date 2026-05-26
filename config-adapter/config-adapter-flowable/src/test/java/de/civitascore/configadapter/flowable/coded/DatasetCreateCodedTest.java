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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
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
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatasetCreateCodedTest {

  private ProcessEngine processEngine;
  private RuntimeService runtimeService;
  private HistoryService historyService;

  private SagaCommandHandler frostHandler;
  private SagaCommandHandler apisixHandler;
  private SagaCommandHandler redpandaHandler;

  @BeforeEach
  void setUp() {
    frostHandler = mock(SagaCommandHandler.class);
    apisixHandler = mock(SagaCommandHandler.class);
    redpandaHandler = mock(SagaCommandHandler.class);
    when(frostHandler.adapter()).thenReturn("frost");
    when(apisixHandler.adapter()).thenReturn("apisix");
    when(redpandaHandler.adapter()).thenReturn("redpanda");

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);
    registry.register(apisixHandler);
    registry.register(redpandaHandler);

    processEngine = FlowableTestSupport.createTestEngine(Map.of("sagaHandlerRegistry", registry));
    runtimeService = processEngine.getRuntimeService();
    historyService = processEngine.getHistoryService();

    processEngine
        .getRepositoryService()
        .createDeployment()
        .addBpmnModel("dataset-create-coded.bpmn", DatasetCreateProcessBuilder.build())
        .deploy();
  }

  @AfterEach
  void tearDown() {
    if (processEngine != null) {
      processEngine.close();
    }
  }

  // Happy path and skip-redpanda are covered by BpmnVsCodedEquivalenceTest.
  // Only coded-specific compensation tests remain here.

  @Test
  void shouldCompensateFrostWhenApisixFails() {
    stubFrostSuccess();
    stubApisixFailure("APISIX connection refused");
    when(frostHandler.handle(argThat(cmd -> cmd != null && "COMPENSATE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-test-123", "create-project"));

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
    when(apisixHandler.handle(argThat(cmd -> cmd != null && "COMPENSATE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-test-123", "create-route"));
    when(frostHandler.handle(argThat(cmd -> cmd != null && "COMPENSATE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-test-123", "create-project"));

    ProcessInstance instance = startProcess(true);
    executeAllJobs();

    assertProcessFinished(instance.getId());

    ArgumentCaptor<SagaCommandMessage> apisixCaptor =
        ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(apisixHandler, atLeast(2)).handle(apisixCaptor.capture());
    assertTrue(
        apisixCaptor.getAllValues().stream().anyMatch(c -> "DELETE_ROUTE".equals(c.operation())));

    ArgumentCaptor<SagaCommandMessage> frostCaptor =
        ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(frostHandler, atLeast(2)).handle(frostCaptor.capture());
    assertTrue(
        frostCaptor.getAllValues().stream().anyMatch(c -> "DELETE_PROJECT".equals(c.operation())));
  }

  private ProcessInstance startProcess(boolean hasPipelines) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-test-123");
    variables.put("datasetId", "ds-456");
    variables.put("datasetName", "Test Dataset");
    variables.put("description", "A test");
    variables.put("hasPipelines", hasPipelines);
    if (hasPipelines) {
      variables.put("dataPipelines", List.of(Map.of("id", "p-1", "data", "{}")));
      variables.put("datasources", List.of(Map.of("id", "src-1", "type", "postgresql")));
    }
    return runtimeService.startProcessInstanceByKey("dataset-create", variables);
  }

  private void executeAllJobs() {
    FlowableTestSupport.executeAllJobs(processEngine);
  }

  private void assertProcessCompleted(String id) {
    FlowableTestSupport.assertProcessCompleted(historyService, id);
  }

  private void assertProcessFinished(String id) {
    FlowableTestSupport.assertProcessFinished(historyService, id);
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
}
