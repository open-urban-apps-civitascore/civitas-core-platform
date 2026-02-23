/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.redpanda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigValue;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.redpanda.PipelineConfigValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RedpandaAdapterTest {

  private RedpandaAdapter adapter;
  private RedpandaConnectClient mockClient;

  @BeforeEach
  void setUp() {
    adapter = new RedpandaAdapter();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("redpanda.topics"))
        .thenReturn(
            "de.civitascore.data.pipeline.created,de.civitascore.data.pipeline.updated,de.civitascore.data.pipeline.deleted");
    when(mockConfig.getProperty("redpanda.url", "http://localhost:4195"))
        .thenReturn("http://localhost:4195");

    mockClient = mock(RedpandaConnectClient.class);
    adapter.setRedpandaClient(mockClient);
    adapter.initialize(mockConfig);
  }

  @Test
  @DisplayName("getName returns 'redpanda'")
  void getName_default_returnsRedpanda() {
    assertEquals("redpanda", adapter.getName());
  }

  @Test
  @DisplayName("getSubscribedTopics returns configured topics")
  void getSubscribedTopics_configured_returnsThreeTopics() {
    assertEquals(3, adapter.getSubscribedTopics().size());
  }

  @Nested
  @DisplayName("CREATE operation")
  class CreateOperation {

    @Test
    @DisplayName("delegates to client.createPipeline")
    void processConfigEvent_createOperation_delegatesToClient() throws Exception {
      doNothing().when(mockClient).createPipeline(eq("pipeline-1"), anyMap());
      ConfigEvent event = createEvent(Operation.CREATE, "pipeline-1");

      adapter.processConfigEvent("de.civitascore.data.pipeline.created", event);

      verify(mockClient).createPipeline(eq("pipeline-1"), anyMap());
    }

    @Test
    @DisplayName("throws FatalAdapterException on client fatal error")
    void processConfigEvent_clientThrowsFatal_throwsFatalAdapterException() throws Exception {
      doThrow(
              new FatalAdapterException(
                  com.civitas.configadapter.model.AdapterErrorCode.REDPANDA_PIPELINE_ERROR, "test"))
          .when(mockClient)
          .createPipeline(any(), anyMap());
      ConfigEvent event = createEvent(Operation.CREATE, "pipeline-1");

      assertThrows(
          FatalAdapterException.class,
          () -> adapter.processConfigEvent("de.civitascore.data.pipeline.created", event));
    }

    @Test
    @DisplayName("throws RetryableAdapterException on client retryable error")
    void processConfigEvent_clientThrowsRetryable_throwsRetryableAdapterException()
        throws Exception {
      doThrow(
              new RetryableAdapterException(
                  com.civitas.configadapter.model.AdapterErrorCode.REDPANDA_ERROR,
                  null,
                  "redpanda",
                  "error"))
          .when(mockClient)
          .createPipeline(any(), anyMap());
      ConfigEvent event = createEvent(Operation.CREATE, "pipeline-1");

      assertThrows(
          RetryableAdapterException.class,
          () -> adapter.processConfigEvent("de.civitascore.data.pipeline.created", event));
    }
  }

  @Nested
  @DisplayName("UPDATE operation")
  class UpdateOperation {

    @Test
    @DisplayName("delegates to client.updatePipeline")
    void processConfigEvent_updateOperation_delegatesToClient() throws Exception {
      doNothing().when(mockClient).updatePipeline(eq("pipeline-1"), anyMap());
      ConfigEvent event = createEvent(Operation.UPDATE, "pipeline-1");

      adapter.processConfigEvent("de.civitascore.data.pipeline.updated", event);

      verify(mockClient).updatePipeline(eq("pipeline-1"), anyMap());
    }
  }

  @Nested
  @DisplayName("DELETE operation")
  class DeleteOperation {

    @Test
    @DisplayName("delegates to client.deletePipeline")
    void processConfigEvent_deleteOperation_delegatesToClient() throws Exception {
      doNothing().when(mockClient).deletePipeline("pipeline-1");
      ConfigEvent event = createEvent(Operation.DELETE, "pipeline-1");

      adapter.processConfigEvent("de.civitascore.data.pipeline.deleted", event);

      verify(mockClient).deletePipeline("pipeline-1");
    }
  }

  @Nested
  @DisplayName("extractPipelineId validation")
  class ExtractPipelineId {

    @Test
    @DisplayName("null pipeline ID throws FatalAdapterException")
    void processConfigEvent_nullPipelineId_throwsFatalAdapterException() {
      ConfigEvent event = createEvent(Operation.CREATE, null);

      assertThrows(
          FatalAdapterException.class,
          () -> adapter.processConfigEvent("de.civitascore.data.pipeline.created", event));
    }

    @Test
    @DisplayName("empty pipeline ID throws FatalAdapterException")
    void processConfigEvent_emptyPipelineId_throwsFatalAdapterException() {
      ConfigEvent event = createEvent(Operation.CREATE, "");

      assertThrows(
          FatalAdapterException.class,
          () -> adapter.processConfigEvent("de.civitascore.data.pipeline.created", event));
    }

    @Test
    @DisplayName("blank pipeline ID throws FatalAdapterException")
    void processConfigEvent_blankPipelineId_throwsFatalAdapterException() {
      ConfigEvent event = createEvent(Operation.CREATE, "   ");

      assertThrows(
          FatalAdapterException.class,
          () -> adapter.processConfigEvent("de.civitascore.data.pipeline.created", event));
    }

    @Test
    @DisplayName("wrong ConfigValue type throws FatalAdapterException")
    void processConfigEvent_wrongConfigValueType_throwsFatalAdapterException() {
      ConfigValue wrongValue = mock(ConfigValue.class);
      Config config = new Config("wrong-type", wrongValue);
      Payload payload = new Payload("redpanda-connect", "pipelines", Operation.CREATE, config);
      Metadata metadata =
          new Metadata(
              "msg-1",
              java.time.OffsetDateTime.now(),
              "test-source",
              "correlation-1",
              "1.0",
              "de.civitascore.data.pipeline.processing.result");
      ConfigEvent event = new ConfigEvent(metadata, payload);

      assertThrows(
          FatalAdapterException.class,
          () -> adapter.processConfigEvent("de.civitascore.data.pipeline.created", event));
    }
  }

  private ConfigEvent createEvent(Operation operation, String pipelineId) {
    PipelineConfigValue configValue = new PipelineConfigValue();
    configValue.setPipelineId(pipelineId);

    Config config = new Config("redpanda-pipeline", configValue);
    Payload payload = new Payload("redpanda-connect", "pipelines", operation, config);
    Metadata metadata =
        new Metadata(
            "msg-1",
            java.time.OffsetDateTime.now(),
            "test-source",
            "correlation-1",
            "1.0",
            "de.civitascore.data.pipeline.processing.result");
    return new ConfigEvent(metadata, payload);
  }
}
