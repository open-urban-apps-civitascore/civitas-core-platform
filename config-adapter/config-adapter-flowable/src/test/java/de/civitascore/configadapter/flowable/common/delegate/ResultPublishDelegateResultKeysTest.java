/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common.delegate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.mockito.ArgumentCaptor;

class ResultPublishDelegateResultKeysTest {

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
  void publishedResults_includePublicUrlFromApisix() {
    when(frostHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "saga-1",
                "create-project",
                Map.of("projectId", "p1", "baseUrl", "http://frost/v1.1/Projects(1)"),
                Map.of("projectId", "p1")));
    when(apisixHandler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(
            SagaCommandResult.success(
                "saga-1",
                "create-route",
                Map.of(
                    "routeId", "r1",
                    "serviceId", "s1",
                    "publicUrl", "https://gateway.example.com/datasets/ds-1"),
                Map.of("routeId", "r1", "serviceId", "s1")));

    Map<String, Object> vars = new HashMap<>();
    vars.put("sagaId", "saga-1");
    vars.put("datasetId", "ds-1");
    vars.put("datasetName", "Test");
    vars.put("description", "A test dataset");
    vars.put("openDataAccess", true);
    vars.put("hasPipelines", false);
    vars.put("datasources", java.util.List.of());
    vars.put("dataPipelines", java.util.List.of());

    processEngine.getRuntimeService().startProcessInstanceByKey("dataset-create", vars);
    FlowableTestSupport.executeAllJobs(processEngine);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(resultPublisher).publishCompleted(eq("saga-1"), captor.capture());

    Map<String, Object> results = captor.getValue();
    assertEquals(
        "https://gateway.example.com/datasets/ds-1",
        results.get("publicUrl"),
        "publicUrl from APISIX handler must be in published results");
    assertEquals("p1", results.get("projectId"));
    assertEquals("r1", results.get("routeId"));
    assertEquals("http://frost/v1.1/Projects(1)", results.get("baseUrl"));
    assertEquals("saga-1", results.get("sagaId"));
    assertEquals("ds-1", results.get("datasetId"));

    assertFalse(
        results.containsKey("datasetName"), "Trigger field 'datasetName' must not be in results");
    assertFalse(
        results.containsKey("hasPipelines"),
        "Internal field 'hasPipelines' must not be in results");
    assertFalse(
        results.containsKey("dataPipelines"),
        "Trigger field 'dataPipelines' must not be in results");
    assertFalse(
        results.containsKey("datasources"), "Trigger field 'datasources' must not be in results");
  }
}
