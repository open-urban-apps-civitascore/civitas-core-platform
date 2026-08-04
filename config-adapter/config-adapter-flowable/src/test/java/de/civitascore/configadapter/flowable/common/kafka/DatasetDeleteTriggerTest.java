/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.FlowableTestSupport;
import de.civitascore.configadapter.flowable.bpmn.BpmnProcessDeployer;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Exercises the {@code DATASET_DELETE} teardown end-to-end through {@link FlowableTriggerConsumer}
 * against a real (H2) engine, so the flag derivations and the delete saga's conditional teardown
 * branches are tested together from a realistic trigger payload — not from preset process
 * variables.
 *
 * <p>Both teardowns are gated on the sink types the trigger carries: {@code hasGeoSink} (a POSTGIS
 * sink) runs workspace removal and sink deprovisioning, {@code hasFrostSink} runs project removal.
 * A trigger without {@code datasinks} touches no sink infrastructure at all.
 */
class DatasetDeleteTriggerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ProcessEngine engine;
  private FlowableTriggerConsumer consumer;
  private SagaCommandHandler frost;
  private SagaCommandHandler apisix;
  private SagaCommandHandler pipeline;
  private SagaCommandHandler geoserver;
  private SagaCommandHandler postgis;

  @BeforeEach
  void setUp() {
    frost = FlowableTestSupport.mockHandler("frost");
    apisix = FlowableTestSupport.mockHandler("apisix");
    pipeline = FlowableTestSupport.mockHandler("nifi");
    geoserver = FlowableTestSupport.mockHandler("geoserver");
    postgis = FlowableTestSupport.mockHandler("postgis");
    stubStepSuccess(frost, "delete-project");
    stubStepSuccess(apisix, "delete-route");
    stubStepSuccess(pipeline, "delete-pipelines");
    stubStepSuccess(geoserver, "delete-workspace");
    stubStepSuccess(postgis, "deprovision-sink");

    SagaHandlerRegistry registry =
        FlowableTestSupport.registry(frost, apisix, pipeline, geoserver, postgis);
    engine = FlowableTestSupport.createTestEngine(Map.of("sagaHandlerRegistry", registry));
    BpmnProcessDeployer.deploy(engine.getRepositoryService());
    consumer = new FlowableTriggerConsumer(engine.getRuntimeService(), engine.getHistoryService());
  }

  @AfterEach
  void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  @Test
  void deleteWithoutDataSinksSkipsAllSinkTeardown() throws Exception {
    // No datasinks in the trigger → both sink flags derive false → neither branch runs. The route
    // teardown is unconditional and still has to happen.
    TriggerTestSupport.processTrigger(consumer, deleteTrigger(false, false));
    FlowableTestSupport.executeAllJobs(engine);

    verify(apisix).handle(any());
    verify(frost, never()).handle(any());
    verify(geoserver, never()).handle(any());
    verify(postgis, never()).handle(any());
  }

  @Test
  void deleteWithFrostSinkTearsDownTheProject() throws Exception {
    // A FROST-only dataset tears down its project and nothing geo-related.
    TriggerTestSupport.processTrigger(consumer, deleteTrigger(false, true));
    FlowableTestSupport.executeAllJobs(engine);

    ArgumentCaptor<SagaCommandMessage> frostCaptor =
        ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(frost, times(1)).handle(frostCaptor.capture());
    assertEquals("DELETE_PROJECT", frostCaptor.getValue().operation());
    assertEquals("proj-789", frostCaptor.getValue().payload().get("projectId"));

    verify(geoserver, never()).handle(any());
    verify(postgis, never()).handle(any());
  }

  @Test
  void deleteWithPostgisSinkTearsDownWorkspaceAndSink() throws Exception {
    // The delete trigger carries the POSTGIS sink (symmetric with create/update), so hasGeoSink
    // derives true: the workspace is removed and the sink deprovisioned — each exactly once. No
    // FROST sink means no project teardown.
    TriggerTestSupport.processTrigger(consumer, deleteTrigger(true, false));
    FlowableTestSupport.executeAllJobs(engine);

    ArgumentCaptor<SagaCommandMessage> geoCaptor =
        ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(geoserver, times(1)).handle(geoCaptor.capture());
    assertEquals("DELETE_WORKSPACE", geoCaptor.getValue().operation());
    assertEquals("ds-456", geoCaptor.getValue().payload().get("datasetId"));

    ArgumentCaptor<SagaCommandMessage> sinkCaptor =
        ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(postgis, times(1)).handle(sinkCaptor.capture());
    SagaCommandMessage sinkCommand = sinkCaptor.getValue();
    assertEquals("DEPROVISION_SINK", sinkCommand.operation());
    List<?> datasinks = (List<?>) sinkCommand.payload().get("datasinks");
    Map<?, ?> sink = (Map<?, ?>) datasinks.get(0);
    assertEquals("POSTGIS", sink.get("type"));
    assertEquals(
        "observations",
        ((Map<?, ?>) sink.get("configuration")).get("tableName"),
        "the sink configuration must reach the handler so it knows which table to drop");

    verify(frost, never()).handle(any());
  }

  private byte[] deleteTrigger(boolean withGeoSink, boolean withFrostSink) throws Exception {
    Map<String, Object> trigger =
        new HashMap<>(
            Map.of(
                "sagaType", "DATASET_DELETE",
                "datasetId", "ds-456",
                "projectId", "proj-789",
                "serviceId", "svc-1",
                "pipelineIds", List.of()));
    List<Map<String, Object>> datasinks = new ArrayList<>();
    if (withGeoSink) {
      datasinks.add(
          Map.of(
              "type",
              "POSTGIS",
              "configuration",
              Map.of("schema", "ds_456", "tableName", "observations")));
    }
    if (withFrostSink) {
      datasinks.add(Map.of("type", "FROST", "configuration", Map.of()));
    }
    if (!datasinks.isEmpty()) {
      trigger.put("datasinks", datasinks);
    }
    return objectMapper.writeValueAsBytes(trigger);
  }

  private static void stubStepSuccess(SagaCommandHandler handler, String step) {
    when(handler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.success("ds-456", step, Map.of(), Map.of()));
  }
}
