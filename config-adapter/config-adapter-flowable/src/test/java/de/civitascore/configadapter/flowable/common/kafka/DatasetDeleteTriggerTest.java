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
 * against a real (H2) engine, so the {@code hasGeoSink} derivation and the delete saga's
 * conditional GeoServer/PostGIS teardown are tested together from a realistic trigger payload — not
 * from a preset process variable.
 *
 * <p>The teardown is gated on {@code hasGeoSink} (derived from {@code datasinks}): a delete trigger
 * carrying the POSTGIS sink runs workspace removal and sink deprovisioning, while a trigger without
 * {@code datasinks} (e.g. from a backend predating the field) skips the geo branch.
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
  void deleteWithoutDataSinksSkipsGeoServerTeardown() throws Exception {
    // No datasinks in the trigger → hasGeoSink derives false → the whole geo branch is skipped.
    TriggerTestSupport.processTrigger(consumer, deleteTrigger(false));
    FlowableTestSupport.executeAllJobs(engine);

    verify(apisix).handle(any());
    verify(frost).handle(any());
    verify(geoserver, never()).handle(any());
    verify(postgis, never()).handle(any());
  }

  @Test
  void deleteWithPostgisSinkTearsDownWorkspaceAndSink() throws Exception {
    // The delete trigger carries the POSTGIS sink (symmetric with create/update), so hasGeoSink
    // derives true: the workspace is removed and the sink deprovisioned — each exactly once.
    TriggerTestSupport.processTrigger(consumer, deleteTrigger(true));
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

    // The dataset carries no FROST sink, yet its recorded project is still torn down: teardown
    // follows what was provisioned, not what is configured now.
    ArgumentCaptor<SagaCommandMessage> frostCaptor =
        ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(frost, times(1)).handle(frostCaptor.capture());
    assertEquals("DELETE_PROJECT", frostCaptor.getValue().operation());
    assertEquals("proj-789", frostCaptor.getValue().payload().get("projectId"));
  }

  private byte[] deleteTrigger(boolean withGeoSink) throws Exception {
    Map<String, Object> trigger =
        new HashMap<>(
            Map.of(
                "sagaType", "DATASET_DELETE",
                "datasetId", "ds-456",
                "projectId", "proj-789",
                "serviceId", "svc-1",
                "pipelineIds", List.of()));
    if (withGeoSink) {
      trigger.put(
          "datasinks",
          List.of(
              Map.of(
                  "type",
                  "POSTGIS",
                  "configuration",
                  Map.of("schema", "ds_456", "tableName", "observations"))));
    }
    return objectMapper.writeValueAsBytes(trigger);
  }

  private static void stubStepSuccess(SagaCommandHandler handler, String step) {
    when(handler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.success("ds-456", step, Map.of(), Map.of()));
  }
}
