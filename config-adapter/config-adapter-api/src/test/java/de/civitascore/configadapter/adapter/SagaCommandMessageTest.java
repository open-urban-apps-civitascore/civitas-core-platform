/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.civitascore.configadapter.util.PayloadConverter;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaCommandMessageTest {

  @Nested
  @DisplayName("fromMap()")
  class FromMap {

    @Test
    @DisplayName("extracts envelope fields and separates payload")
    void shouldExtractEnvelopeAndPayload() {
      Map<String, Object> flat =
          Map.of(
              "type", "EXECUTE_STEP",
              "messageId", "msg-001",
              "sagaId", "saga-001",
              "stepId", "create-project",
              "adapter", "frost",
              "operation", "CREATE_PROJECT",
              "datasetId", "ds-001",
              "datasetName", "Test Dataset");

      SagaCommandMessage msg = SagaCommandMessage.fromMap(flat);

      assertEquals("EXECUTE_STEP", msg.type());
      assertEquals("msg-001", msg.messageId());
      assertEquals("saga-001", msg.sagaId());
      assertEquals("create-project", msg.stepId());
      assertEquals("frost", msg.adapter());
      assertEquals("CREATE_PROJECT", msg.operation());
      assertEquals("ds-001", msg.payload().get("datasetId"));
      assertEquals("Test Dataset", msg.payload().get("datasetName"));
      assertFalse(msg.payload().containsKey("type"));
      assertFalse(msg.payload().containsKey("adapter"));
    }

    @Test
    @DisplayName("handles compensate step type")
    void shouldHandleCompensateStep() {
      Map<String, Object> flat =
          Map.of(
              "type", "COMPENSATE_STEP",
              "messageId", "msg-002",
              "sagaId", "saga-001",
              "stepId", "create-project",
              "adapter", "frost",
              "operation", "DELETE_PROJECT",
              "projectId", "proj-123");

      SagaCommandMessage msg = SagaCommandMessage.fromMap(flat);

      assertEquals("COMPENSATE_STEP", msg.type());
      assertEquals("DELETE_PROJECT", msg.operation());
      assertEquals("proj-123", msg.payload().get("projectId"));
    }

    @Test
    @DisplayName("handles missing optional fields gracefully")
    void shouldHandleMissingFields() {
      Map<String, Object> flat =
          Map.of(
              "type", "EXECUTE_STEP",
              "stepId", "create-project",
              "adapter", "frost",
              "operation", "CREATE_PROJECT");

      SagaCommandMessage msg = SagaCommandMessage.fromMap(flat);

      assertNull(msg.messageId());
      assertNull(msg.sagaId());
      assertNotNull(msg.payload());
    }
  }

  @Nested
  @DisplayName("JSON round-trip")
  class JsonRoundTrip {

    @Test
    @DisplayName("deserializes dispatcher JSON and creates message via fromMap")
    void shouldDeserializeDispatcherJson() throws IOException {
      String json =
          """
          {"type":"EXECUTE_STEP","messageId":"msg-001","sagaId":"saga-001",\
          "stepId":"create-project","adapter":"frost","operation":"CREATE_PROJECT",\
          "datasetId":"ds-001","datasetName":"Test Dataset","description":"A test"}""";

      Map<String, Object> map = PayloadConverter.readMap(json.getBytes());
      SagaCommandMessage msg = SagaCommandMessage.fromMap(map);

      assertEquals("EXECUTE_STEP", msg.type());
      assertEquals("frost", msg.adapter());
      assertEquals("CREATE_PROJECT", msg.operation());
      assertEquals("ds-001", msg.payload().get("datasetId"));
      assertEquals("Test Dataset", msg.payload().get("datasetName"));
      assertEquals("A test", msg.payload().get("description"));
    }
  }
}
