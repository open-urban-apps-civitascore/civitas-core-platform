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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.FlowableTestSupport;
import de.civitascore.configadapter.flowable.common.FlowableEngineFactory;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import de.civitascore.configadapter.flowable.common.kafka.FlowableResultPublisher;
import java.util.HashMap;
import java.util.Map;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishFailureDoesNotReplayStepsTest {

  private ProcessEngine processEngine;
  private FlowableResultPublisher resultPublisher;
  private SagaCommandHandler frostHandler;
  private SagaCommandHandler apisixHandler;

  @BeforeEach
  void setUp() {
    frostHandler = FlowableTestSupport.mockHandler("frost");
    apisixHandler = FlowableTestSupport.mockHandler("apisix");
    SagaCommandHandler redpandaHandler = FlowableTestSupport.mockHandler("redpanda");

    SagaHandlerRegistry registry =
        FlowableTestSupport.registry(frostHandler, apisixHandler, redpandaHandler);
    resultPublisher = mock(FlowableResultPublisher.class);

    processEngine =
        FlowableEngineFactory.createWithH2(
            Map.of("sagaHandlerRegistry", registry, "resultPublisher", resultPublisher));

    processEngine
        .getRepositoryService()
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
  void publishFailure_doesNotReplayAdapterCalls() {
    when(frostHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "s",
                "create-project",
                Map.of("projectId", "p1", "baseUrl", "http://frost"),
                Map.of("projectId", "p1")));
    when(apisixHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "s", "create-route", Map.of("routeId", "r1"), Map.of("routeId", "r1")));

    doThrow(new RuntimeException("Kafka timeout"))
        .doNothing()
        .when(resultPublisher)
        .publishCompleted(any(), any());

    Map<String, Object> vars = new HashMap<>();
    vars.put("sagaId", "saga-publish-fail");
    vars.put("datasetId", "ds-1");
    vars.put("hasPipelines", false);

    processEngine.getRuntimeService().startProcessInstanceByKey("dataset-create", vars);
    FlowableTestSupport.executeAllJobs(processEngine);

    verify(frostHandler, times(1)).handle(argThat(cmd -> "EXECUTE_STEP".equals(cmd.type())));
    verify(apisixHandler, times(1)).handle(argThat(cmd -> "EXECUTE_STEP".equals(cmd.type())));
  }
}
