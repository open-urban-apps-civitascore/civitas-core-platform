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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.FlowableTestSupport;
import de.civitascore.configadapter.flowable.bpmn.BpmnProcessDeployer;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
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
 * conditional GeoServer teardown are tested together from a realistic trigger payload — not from a
 * preset process variable.
 *
 * <p>Because the teardown is gated on {@code hasGeoSink} (derived from {@code datasinks}), the
 * delete trigger must carry the geo sink for the workspace to be removed. A delete trigger without
 * {@code datasinks} (today's backend payload) therefore skips the GeoServer step — the backend must
 * include the geo sink in the delete trigger, symmetric with create/update, for teardown to run.
 */
class DatasetDeleteTriggerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ProcessEngine engine;
  private FlowableTriggerConsumer consumer;
  private SagaCommandHandler frost;
  private SagaCommandHandler apisix;
  private SagaCommandHandler redpanda;
  private SagaCommandHandler geoserver;

  @BeforeEach
  void setUp() {
    frost = FlowableTestSupport.mockHandler("frost");
    apisix = FlowableTestSupport.mockHandler("apisix");
    redpanda = FlowableTestSupport.mockHandler("redpanda");
    geoserver = FlowableTestSupport.mockHandler("geoserver");
    stubStepSuccess(frost, "delete-project");
    stubStepSuccess(apisix, "delete-route");
    stubStepSuccess(redpanda, "delete-pipelines");
    stubStepSuccess(geoserver, "delete-workspace");

    SagaHandlerRegistry registry = FlowableTestSupport.registry(frost, apisix, redpanda, geoserver);
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
    // Mirrors today's backend DatasetDelete payload: no datasinks → hasGeoSink derives false.
    TriggerTestSupport.processTrigger(consumer, deleteTrigger(false));
    FlowableTestSupport.executeAllJobs(engine);

    verify(apisix).handle(any());
    verify(frost).handle(any());
    verify(geoserver, never()).handle(any());
  }

  @Test
  void deleteWithPostgisSinkTearsDownWorkspace() throws Exception {
    // Required contract once geo is live: the delete trigger carries the POSTGIS sink, so
    // hasGeoSink derives true and the workspace is torn down.
    TriggerTestSupport.processTrigger(consumer, deleteTrigger(true));
    FlowableTestSupport.executeAllJobs(engine);

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(geoserver).handle(captor.capture());
    assertEquals("DELETE_WORKSPACE", captor.getValue().operation());
    assertEquals("ds-456", captor.getValue().payload().get("datasetId"));
  }

  private byte[] deleteTrigger(boolean withGeoSink) throws Exception {
    Map<String, Object> trigger =
        new java.util.HashMap<>(
            Map.of(
                "sagaType", "DATASET_DELETE",
                "datasetId", "ds-456",
                "projectId", "proj-789",
                "serviceId", "svc-1",
                "pipelineIds", List.of()));
    if (withGeoSink) {
      trigger.put("datasinks", List.of(Map.of("type", "POSTGIS")));
    }
    return objectMapper.writeValueAsBytes(trigger);
  }

  private static void stubStepSuccess(SagaCommandHandler handler, String step) {
    when(handler.handle(argThat(cmd -> cmd != null && "EXECUTE_STEP".equals(cmd.type()))))
        .thenReturn(SagaCommandResult.success("ds-456", step, Map.of(), Map.of()));
  }
}
