/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.civitas.configadapter.util.PayloadConverter;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaCommandResultTest {

  @Nested
  @DisplayName("Factory methods")
  class FactoryMethods {

    @Test
    @DisplayName("success creates STEP_COMPLETED result")
    void shouldCreateSuccess() {
      SagaCommandResult result =
          SagaCommandResult.success(
              "saga-001",
              "create-project",
              Map.of("projectId", "proj-123", "baseUrl", "http://frost/v1.1"),
              Map.of("projectId", "proj-123"));

      assertEquals("STEP_COMPLETED", result.type());
      assertEquals("saga-001", result.sagaId());
      assertEquals("create-project", result.stepId());
      assertEquals("proj-123", result.resultData().get("projectId"));
      assertEquals("http://frost/v1.1", result.resultData().get("baseUrl"));
      assertEquals("proj-123", result.compensationData().get("projectId"));
      assertNull(result.error());
    }

    @Test
    @DisplayName("failure creates STEP_FAILED result")
    void shouldCreateFailure() {
      SagaCommandResult result =
          SagaCommandResult.failure("saga-001", "create-project", "Connection refused");

      assertEquals("STEP_FAILED", result.type());
      assertEquals("saga-001", result.sagaId());
      assertEquals("create-project", result.stepId());
      assertEquals("Connection refused", result.error());
    }

    @Test
    @DisplayName("compensationSuccess creates COMPENSATION_COMPLETED result")
    void shouldCreateCompensationSuccess() {
      SagaCommandResult result =
          SagaCommandResult.compensationSuccess("saga-001", "create-project");

      assertEquals("COMPENSATION_COMPLETED", result.type());
      assertEquals("saga-001", result.sagaId());
      assertEquals("create-project", result.stepId());
      assertNull(result.error());
    }

    @Test
    @DisplayName("compensationFailure creates COMPENSATION_FAILED result")
    void shouldCreateCompensationFailure() {
      SagaCommandResult result =
          SagaCommandResult.compensationFailure("saga-001", "create-project", "Resource not found");

      assertEquals("COMPENSATION_FAILED", result.type());
      assertEquals("saga-001", result.sagaId());
      assertEquals("create-project", result.stepId());
      assertEquals("Resource not found", result.error());
    }
  }

  @Nested
  @DisplayName("JSON serialization")
  class JsonSerialization {

    @Test
    @DisplayName("success result serializes to format expected by SagaResultConsumer")
    void shouldSerializeSuccessResult() throws IOException {
      SagaCommandResult result =
          SagaCommandResult.success(
              "saga-001",
              "create-project",
              Map.of("projectId", "proj-123"),
              Map.of("projectId", "proj-123"));

      byte[] json = PayloadConverter.writeValueAsBytes(result);
      Map<String, Object> map = PayloadConverter.readMap(json);

      assertEquals("STEP_COMPLETED", map.get("type"));
      assertEquals("saga-001", map.get("sagaId"));
      assertEquals("create-project", map.get("stepId"));
      @SuppressWarnings("unchecked")
      Map<String, Object> resultData = (Map<String, Object>) map.get("resultData");
      assertEquals("proj-123", resultData.get("projectId"));
    }

    @Test
    @DisplayName("failure result serializes to format expected by SagaResultConsumer")
    void shouldSerializeFailureResult() throws IOException {
      SagaCommandResult result = SagaCommandResult.failure("saga-001", "create-project", "Timeout");

      byte[] json = PayloadConverter.writeValueAsBytes(result);
      Map<String, Object> map = PayloadConverter.readMap(json);

      assertEquals("STEP_FAILED", map.get("type"));
      assertEquals("saga-001", map.get("sagaId"));
      assertEquals("Timeout", map.get("error"));
    }
  }
}
