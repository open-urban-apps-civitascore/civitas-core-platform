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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.bpmn.BpmnProcessDeployer;
import de.civitascore.configadapter.flowable.common.SagaFailure;
import de.civitascore.configadapter.flowable.common.kafka.FlowableResultPublisher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResultPublishingTest {

  private ProcessEngine processEngine;
  private FlowableResultPublisher resultPublisher;
  private SagaCommandHandler frostHandler;
  private SagaCommandHandler apisixHandler;
  private SagaCommandHandler pipelineHandler;

  @BeforeEach
  void setUp() {
    frostHandler = FlowableTestSupport.mockHandler("frost");
    apisixHandler = FlowableTestSupport.mockHandler("apisix");
    pipelineHandler = FlowableTestSupport.mockHandler("nifi");
    resultPublisher = mock(FlowableResultPublisher.class);

    processEngine =
        FlowableTestSupport.createTestEngine(
            Map.of(
                "sagaHandlerRegistry",
                FlowableTestSupport.registry(frostHandler, apisixHandler, pipelineHandler),
                "resultPublisher",
                resultPublisher));

    BpmnProcessDeployer.deploy(processEngine.getRepositoryService());
  }

  @AfterEach
  void tearDown() {
    if (processEngine != null) {
      processEngine.close();
    }
  }

  @Nested
  class Create {
    @Test
    void success_publishesCompleted() {
      stubSuccess(frostHandler, "create-project", Map.of("projectId", "p1", "baseUrl", "http://f"));
      stubSuccess(apisixHandler, "create-route", Map.of("routeId", "r1"));

      start("dataset-create", createVars("saga-c1"));

      verify(resultPublisher).publishCompleted(eq("saga-c1"), any());
    }

    @Test
    void failure_publishesFailed() {
      stubSuccess(frostHandler, "create-project", Map.of("projectId", "p1"));
      stubFailure(apisixHandler, "timeout");
      stubCompensation(frostHandler);

      start("dataset-create", createVars("saga-c2"));

      verify(resultPublisher)
          .publishFailed(
              argThat(f -> "saga-c2".equals(f.sagaId()) && "ds-1".equals(f.datasetId())));
    }

    @Test
    void success_doesNotPublishFailed() {
      stubSuccess(frostHandler, "create-project", Map.of("projectId", "p1", "baseUrl", "http://f"));
      stubSuccess(apisixHandler, "create-route", Map.of("routeId", "r1"));

      start("dataset-create", createVars("saga-c3"));

      verify(resultPublisher, never()).publishFailed(any(SagaFailure.class));
    }

    @Test
    void threeStepSuccess_publishesResultKeysFromAllSteps() {
      stubSuccess(frostHandler, "create-project", Map.of("projectId", "p1", "baseUrl", "http://f"));
      stubSuccess(apisixHandler, "create-route", Map.of("routeId", "r1"));
      stubSuccess(pipelineHandler, "deploy-pipelines", Map.of("pipelineIds", List.of("pl1")));

      Map<String, Object> vars = new HashMap<>();
      vars.put("sagaId", "saga-3step");
      vars.put("datasetId", "ds-1");
      vars.put("hasPipelines", true);
      vars.put("hasFrostSink", true);
      start("dataset-create", vars);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
      verify(resultPublisher).publishCompleted(eq("saga-3step"), captor.capture());
      Map<String, Object> results = captor.getValue();
      assertTrue(results.containsKey("projectId"), "FROST result key missing");
      assertTrue(results.containsKey("routeId"), "APISIX result key missing");
      assertTrue(results.containsKey("pipelineIds"), "Pipeline result key missing");
    }

    @Test
    void slugKeyedRouteIdsMapSurvivesResultRoundTrip() {
      stubSuccess(frostHandler, "create-project", Map.of("projectId", "p1", "baseUrl", "http://f"));
      // The per-named-API APISIX handler returns a nested slug→routeId map (issue #1368). Assert it
      // survives storage as a Flowable process variable and the result aggregation untouched.
      Map<String, Object> routeIds = Map.of("traffic", "rid-traffic", "weather", "rid-weather");
      stubSuccess(apisixHandler, "create-route", Map.of("routeIds", routeIds, "serviceId", "ds-1"));

      start("dataset-create", createVars("saga-cmap"));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
      verify(resultPublisher).publishCompleted(eq("saga-cmap"), captor.capture());
      assertEquals(
          routeIds,
          captor.getValue().get("routeIds"),
          "slug-keyed routeIds map must round-trip through the Flowable result path");
    }
  }

  @Nested
  class Update {
    @Test
    void success_publishesCompleted() {
      stubSuccess(frostHandler, "update-project", Map.of());
      stubSuccess(apisixHandler, "update-route", Map.of());

      start("dataset-update", updateVars("saga-u1"));

      verify(resultPublisher).publishCompleted(eq("saga-u1"), any());
    }

    @Test
    void failure_publishesFailed() {
      stubSuccess(frostHandler, "update-project", Map.of());
      stubFailure(apisixHandler, "timeout");
      stubCompensation(frostHandler);

      start("dataset-update", updateVars("saga-u2"));

      verify(resultPublisher)
          .publishFailed(
              argThat(f -> "saga-u2".equals(f.sagaId()) && "ds-1".equals(f.datasetId())));
    }
  }

  @Nested
  class Delete {
    @Test
    void allSuccess_publishesCompleted() {
      when(apisixHandler.handle(any()))
          .thenReturn(SagaCommandResult.success("s", "delete-route", Map.of(), Map.of()));
      when(frostHandler.handle(any()))
          .thenReturn(SagaCommandResult.success("s", "delete-project", Map.of(), Map.of()));

      start("dataset-delete", deleteVars("saga-d1"));

      verify(resultPublisher).publishCompleted(eq("saga-d1"), any());
    }

    @Test
    void partialFailure_publishesFailed() {
      when(apisixHandler.handle(any()))
          .thenReturn(SagaCommandResult.failure("s", "delete-route", "down"));
      when(frostHandler.handle(any()))
          .thenReturn(SagaCommandResult.success("s", "delete-project", Map.of(), Map.of()));

      start("dataset-delete", deleteVars("saga-d2"));

      verify(resultPublisher)
          .publishFailed(
              argThat(
                  f ->
                      "saga-d2".equals(f.sagaId())
                          && "ds-1".equals(f.datasetId())
                          && f.compensated() == false));
    }

    @Test
    void allFail_publishesFailed() {
      when(apisixHandler.handle(any()))
          .thenReturn(SagaCommandResult.failure("s", "delete-route", "down"));
      when(frostHandler.handle(any()))
          .thenReturn(SagaCommandResult.failure("s", "delete-project", "down"));

      start("dataset-delete", deleteVars("saga-d3"));

      verify(resultPublisher)
          .publishFailed(
              argThat(
                  f ->
                      "saga-d3".equals(f.sagaId())
                          && "ds-1".equals(f.datasetId())
                          && f.compensated() == false));
    }
  }

  private void start(String processKey, Map<String, Object> vars) {
    processEngine.getRuntimeService().startProcessInstanceByKey(processKey, vars);
    FlowableTestSupport.executeAllJobs(processEngine);
  }

  private Map<String, Object> createVars(String sagaId) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("sagaId", sagaId);
    vars.put("datasetId", "ds-1");
    vars.put("hasPipelines", false);
    vars.put("hasFrostSink", true);
    return vars;
  }

  private Map<String, Object> updateVars(String sagaId) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("sagaId", sagaId);
    vars.put("datasetId", "ds-1");
    vars.put("hasPipelines", false);
    vars.put("hasFrostSink", true);
    vars.put("projectId", "p-1");
    vars.put("routeId", "r-1");
    return vars;
  }

  private Map<String, Object> deleteVars(String sagaId) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("sagaId", sagaId);
    vars.put("datasetId", "ds-1");
    vars.put("hasPipelines", false);
    vars.put("projectId", "p-1");
    vars.put("routeId", "r-1");
    vars.put("serviceId", "s-1");
    return vars;
  }

  private void stubSuccess(SagaCommandHandler handler, String stepId, Map<String, Object> result) {
    when(handler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.success("s", stepId, result, Map.of()));
  }

  private void stubFailure(SagaCommandHandler handler, String error) {
    when(handler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.failure("s", "step", error));
  }

  private void stubCompensation(SagaCommandHandler handler) {
    when(handler.handle(argThat(cmd -> cmd != null && "COMPENSATE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("s", "step"));
  }
}
