/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.dataset.Dataset;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PayloadConverterTest {

  @Nested
  @DisplayName("toDataset(Map)")
  class ToDatasetFromMap {

    @Test
    @DisplayName("converts trigger payload map to Dataset")
    void shouldConvertMapToDataset() {
      Map<String, Object> trigger =
          Map.of(
              "id", "ds-001",
              "name", "Test Dataset",
              "openDataAccess", true);

      Dataset dataset = PayloadConverter.toDataset(trigger);

      assertEquals("ds-001", dataset.id());
      assertEquals("Test Dataset", dataset.name());
      assertTrue(dataset.openDataAccess());
    }

    @Test
    @DisplayName("ignores unknown properties")
    void shouldIgnoreUnknownProperties() {
      Map<String, Object> trigger =
          Map.of(
              "id", "ds-001",
              "name", "Test",
              "openDataAccess", false,
              "description", "will be ignored by Dataset record",
              "someOtherField", 42);

      Dataset dataset = PayloadConverter.toDataset(trigger);

      assertEquals("ds-001", dataset.id());
      assertFalse(dataset.openDataAccess());
    }

    @Test
    @DisplayName("defaults openDataAccess to false when missing")
    void shouldDefaultOpenDataAccessToFalse() {
      Map<String, Object> trigger = Map.of("id", "ds-001", "name", "Test");

      Dataset dataset = PayloadConverter.toDataset(trigger);

      assertFalse(dataset.openDataAccess());
    }
  }

  @Nested
  @DisplayName("toDataset(byte[])")
  class ToDatasetFromBytes {

    @Test
    @DisplayName("deserializes JSON bytes directly to Dataset")
    void shouldDeserializeJsonToDataset() throws IOException {
      String json =
          """
          {"id":"ds-002","name":"From JSON","openDataAccess":true}""";

      Dataset dataset = PayloadConverter.toDataset(json.getBytes());

      assertEquals("ds-002", dataset.id());
      assertEquals("From JSON", dataset.name());
      assertTrue(dataset.openDataAccess());
    }

    @Test
    @DisplayName("ignores unknown JSON fields")
    void shouldIgnoreUnknownJsonFields() throws IOException {
      String json =
          """
          {"id":"ds-003","name":"Test","openDataAccess":false,"description":"ignored"}""";

      Dataset dataset = PayloadConverter.toDataset(json.getBytes());

      assertEquals("ds-003", dataset.id());
    }
  }

  @Nested
  @DisplayName("Generic helpers")
  class GenericHelpers {

    @Test
    @DisplayName("readValue deserializes JSON bytes to target type")
    void shouldReadValue() throws IOException {
      String json =
          """
          {"id":"ds-004","name":"Generic","openDataAccess":false}""";

      Dataset dataset = PayloadConverter.readValue(json.getBytes(), Dataset.class);

      assertEquals("ds-004", dataset.id());
    }

    @Test
    @DisplayName("readMap deserializes JSON bytes to Map")
    void shouldReadMap() throws IOException {
      String json =
          """
          {"sagaId":"s-001","stepId":"create-project","type":"STEP_COMPLETED"}""";

      Map<String, Object> map = PayloadConverter.readMap(json.getBytes());

      assertEquals("s-001", map.get("sagaId"));
      assertEquals("create-project", map.get("stepId"));
      assertEquals("STEP_COMPLETED", map.get("type"));
    }

    @Test
    @DisplayName("writeValueAsBytes serializes object to JSON bytes")
    void shouldWriteValueAsBytes() throws IOException {
      Map<String, Object> data = Map.of("sagaId", "s-001", "success", true);

      byte[] json = PayloadConverter.writeValueAsBytes(data);

      assertNotNull(json);
      Map<String, Object> roundTripped = PayloadConverter.readMap(json);
      assertEquals("s-001", roundTripped.get("sagaId"));
      assertEquals(true, roundTripped.get("success"));
    }

    @Test
    @DisplayName("fromValue converts Map to typed object")
    void shouldConvertFromValue() {
      Map<String, Object> map = Map.of("id", "ds-005", "name", "Converted", "openDataAccess", true);

      Dataset dataset = PayloadConverter.fromValue(map, Dataset.class);

      assertEquals("ds-005", dataset.id());
      assertEquals("Converted", dataset.name());
      assertTrue(dataset.openDataAccess());
    }
  }
}
