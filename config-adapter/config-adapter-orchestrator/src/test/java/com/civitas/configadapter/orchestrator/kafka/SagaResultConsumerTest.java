/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.orchestrator.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.civitas.configadapter.orchestrator.engine.SagaEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaResultConsumerTest {

  private SagaEngine engine;
  private SagaResultConsumer consumer;
  private ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    var kafkaConsumer = mock(org.apache.kafka.clients.consumer.KafkaConsumer.class);
    engine = mock(SagaEngine.class);
    consumer = new SagaResultConsumer(kafkaConsumer, engine);
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  private void invokeProcessRecord(Map<String, Object> message) throws Exception {
    byte[] json = objectMapper.writeValueAsBytes(message);
    Method method = SagaResultConsumer.class.getDeclaredMethod("processRecord", byte[].class);
    method.setAccessible(true);
    method.invoke(consumer, json);
  }

  @Nested
  @DisplayName("STEP_COMPLETED")
  class StepCompleted {

    @Test
    @DisplayName("routes to engine with resultData and compensationData")
    void shouldRouteWithData() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");
      msg.put("resultData", Map.of("projectId", "proj-123"));
      msg.put("compensationData", Map.of("projectId", "proj-123"));

      invokeProcessRecord(msg);

      verify(engine)
          .handleStepCompleted(
              eq("saga-1"),
              eq("create-project"),
              eq(Map.of("projectId", "proj-123")),
              eq(Map.of("projectId", "proj-123")));
    }

    @Test
    @DisplayName("uses empty maps when resultData and compensationData are absent")
    void shouldUseEmptyMapsForMissingData() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine)
          .handleStepCompleted(eq("saga-1"), eq("create-project"), eq(Map.of()), eq(Map.of()));
    }
  }

  @Nested
  @DisplayName("STEP_FAILED")
  class StepFailed {

    @Test
    @DisplayName("routes to engine with error message")
    void shouldRouteWithError() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_FAILED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-route");
      msg.put("error", "Connection refused");

      invokeProcessRecord(msg);

      verify(engine).handleStepFailed("saga-1", "create-route", "Connection refused");
    }

    @Test
    @DisplayName("uses 'Unknown error' when error field is absent")
    void shouldUseDefaultError() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_FAILED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-route");

      invokeProcessRecord(msg);

      verify(engine).handleStepFailed("saga-1", "create-route", "Unknown error");
    }
  }

  @Nested
  @DisplayName("COMPENSATION_COMPLETED")
  class CompensationCompleted {

    @Test
    @DisplayName("routes to engine")
    void shouldRouteToEngine() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "COMPENSATION_COMPLETED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine).handleCompensationCompleted("saga-1", "create-project");
    }
  }

  @Nested
  @DisplayName("COMPENSATION_FAILED")
  class CompensationFailed {

    @Test
    @DisplayName("routes to engine with error message")
    void shouldRouteWithError() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "COMPENSATION_FAILED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");
      msg.put("error", "FROST unreachable");

      invokeProcessRecord(msg);

      verify(engine).handleCompensationFailed("saga-1", "create-project", "FROST unreachable");
    }
  }

  @Nested
  @DisplayName("malformed messages")
  class Malformed {

    @Test
    @DisplayName("ignores message with missing type")
    void shouldIgnoreMissingType() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine, never()).handleStepCompleted(any(), any(), any(), any());
      verify(engine, never()).handleStepFailed(any(), any(), any());
    }

    @Test
    @DisplayName("ignores message with missing sagaId")
    void shouldIgnoreMissingSagaId() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine, never()).handleStepCompleted(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ignores message with missing stepId")
    void shouldIgnoreMissingStepId() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("sagaId", "saga-1");

      invokeProcessRecord(msg);

      verify(engine, never()).handleStepCompleted(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ignores unknown message type")
    void shouldIgnoreUnknownType() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "UNKNOWN_TYPE");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine, never()).handleStepCompleted(any(), any(), any(), any());
      verify(engine, never()).handleStepFailed(any(), any(), any());
      verify(engine, never()).handleCompensationCompleted(any(), any());
      verify(engine, never()).handleCompensationFailed(any(), any(), any());
    }
  }
}
