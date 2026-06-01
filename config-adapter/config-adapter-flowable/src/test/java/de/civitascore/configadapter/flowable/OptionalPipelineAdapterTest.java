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
import de.civitascore.configadapter.flowable.coded.CodedProcessDeployer;
import de.civitascore.configadapter.flowable.common.SagaFailure;
import de.civitascore.configadapter.flowable.common.kafka.FlowableResultPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies that the pipeline adapter (redpanda) is optional: the engine runs without it, a
 * pipeline-free saga completes, and a saga that does need it fails gracefully (saga failure +
 * compensation) instead of crashing. Run across both the BPMN-XML and coded process variants.
 */
class OptionalPipelineAdapterTest {

  private ProcessEngine processEngine;
  private FlowableResultPublisher resultPublisher;
  private SagaCommandHandler frostHandler;
  private SagaCommandHandler apisixHandler;

  static Stream<Arguments> approaches() {
    return Stream.of(Arguments.of("BPMN", true), Arguments.of("Coded", false));
  }

  /** Builds an engine with ONLY frost + apisix registered — no redpanda/pipeline handler. */
  void setUp(boolean useBpmn) {
    frostHandler = FlowableTestSupport.mockHandler("frost");
    apisixHandler = FlowableTestSupport.mockHandler("apisix");
    resultPublisher = mock(FlowableResultPublisher.class);

    processEngine =
        FlowableTestSupport.createTestEngine(
            Map.of(
                "sagaHandlerRegistry",
                FlowableTestSupport.registry(frostHandler, apisixHandler),
                "resultPublisher",
                resultPublisher));

    if (useBpmn) {
      BpmnProcessDeployer.deploy(processEngine.getRepositoryService());
    } else {
      CodedProcessDeployer.deploy(processEngine.getRepositoryService());
    }
  }

  @AfterEach
  void tearDown() {
    if (processEngine != null) {
      processEngine.close();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.civitascore.configadapter.flowable.OptionalPipelineAdapterTest#approaches")
  void noPipelineSaga_completesWithoutRedpandaHandler(String label, boolean useBpmn) {
    setUp(useBpmn);
    stubSuccess(frostHandler, "create-project", Map.of("projectId", "p1", "baseUrl", "http://f"));
    stubSuccess(apisixHandler, "create-route", Map.of("routeId", "r1"));

    start("dataset-create", createVars("saga-nopipe", false));

    verify(resultPublisher).publishCompleted(eq("saga-nopipe"), any());
    verify(resultPublisher, never()).publishFailed(any(SagaFailure.class));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.civitascore.configadapter.flowable.OptionalPipelineAdapterTest#approaches")
  void pipelineSaga_withoutRedpandaHandler_failsGracefully(String label, boolean useBpmn) {
    setUp(useBpmn);
    stubSuccess(frostHandler, "create-project", Map.of("projectId", "p1", "baseUrl", "http://f"));
    stubSuccess(apisixHandler, "create-route", Map.of("routeId", "r1"));
    // compensations succeed so the rollback path can complete
    when(frostHandler.handle(argThat(c -> c != null && "COMPENSATE_STEP".equals(c.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-pipe", "create-project"));
    when(apisixHandler.handle(argThat(c -> c != null && "COMPENSATE_STEP".equals(c.type()))))
        .thenReturn(SagaCommandResult.compensationSuccess("saga-pipe", "create-route"));

    start("dataset-create", createVars("saga-pipe", true));

    // The missing pipeline handler must surface as a saga failure, not a technical crash.
    verify(resultPublisher).publishFailed(argThat(f -> "saga-pipe".equals(f.sagaId())));
  }

  private void start(String processKey, Map<String, Object> vars) {
    processEngine.getRuntimeService().startProcessInstanceByKey(processKey, vars);
    FlowableTestSupport.executeAllJobs(processEngine);
  }

  private Map<String, Object> createVars(String sagaId, boolean hasPipelines) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("sagaId", sagaId);
    vars.put("datasetId", "ds-1");
    vars.put("hasPipelines", hasPipelines);
    return vars;
  }

  private void stubSuccess(SagaCommandHandler handler, String stepId, Map<String, Object> result) {
    when(handler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.success("s", stepId, result, Map.of()));
  }
}
