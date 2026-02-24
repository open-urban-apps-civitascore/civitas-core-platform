/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UpstreamTimeout}. */
class UpstreamTimeoutTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_allFields_shouldStoreValues() {
    UpstreamTimeout timeout = new UpstreamTimeout(6, 10, 15);
    assertEquals(6, timeout.connect());
    assertEquals(10, timeout.send());
    assertEquals(15, timeout.read());
  }

  @Test
  void constructor_nullFields_shouldAllowNull() {
    UpstreamTimeout timeout = new UpstreamTimeout(null, null, null);
    assertNull(timeout.connect());
    assertNull(timeout.send());
    assertNull(timeout.read());
  }

  @Test
  void constructor_partialFields_shouldStoreNonNull() {
    UpstreamTimeout timeout = new UpstreamTimeout(5, null, 10);
    assertEquals(5, timeout.connect());
    assertNull(timeout.send());
    assertEquals(10, timeout.read());
  }

  @Test
  void constructor_fractionalValues_shouldStoreAsNumber() {
    UpstreamTimeout timeout = new UpstreamTimeout(0.5, 1.5, 2.0);
    assertEquals(0.5, timeout.connect());
    assertEquals(1.5, timeout.send());
    assertEquals(2.0, timeout.read());
  }

  @Test
  void jsonSerialization_allFields_shouldProduceCorrectJson() throws Exception {
    UpstreamTimeout timeout = new UpstreamTimeout(6, 10, 15);
    String json = objectMapper.writeValueAsString(timeout);
    assertNotNull(json);

    UpstreamTimeout deserialized = objectMapper.readValue(json, UpstreamTimeout.class);
    assertEquals(timeout.connect(), deserialized.connect());
    assertEquals(timeout.send(), deserialized.send());
    assertEquals(timeout.read(), deserialized.read());
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldCreateRecord() throws Exception {
    String json =
        """
                {"connect": 6, "send": 10, "read": 15}
                """;
    UpstreamTimeout timeout = objectMapper.readValue(json, UpstreamTimeout.class);
    assertEquals(6, timeout.connect());
    assertEquals(10, timeout.send());
    assertEquals(15, timeout.read());
  }

  @Test
  void jsonDeserialization_fromPartialJson_shouldHandleMissing() throws Exception {
    String json =
        """
                {"connect": 5}
                """;
    UpstreamTimeout timeout = objectMapper.readValue(json, UpstreamTimeout.class);
    assertEquals(5, timeout.connect());
    assertNull(timeout.send());
    assertNull(timeout.read());
  }

  @Test
  void jsonDeserialization_fromFractionalJson_shouldHandleDecimals() throws Exception {
    String json =
        """
                {"connect": 0.5, "send": 1.5, "read": 2.0}
                """;
    UpstreamTimeout timeout = objectMapper.readValue(json, UpstreamTimeout.class);
    assertEquals(0.5, timeout.connect().doubleValue(), 0.001);
    assertEquals(1.5, timeout.send().doubleValue(), 0.001);
    assertEquals(2.0, timeout.read().doubleValue(), 0.001);
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    UpstreamTimeout original = new UpstreamTimeout(6, 10, 15);
    String json = objectMapper.writeValueAsString(original);
    UpstreamTimeout deserialized = objectMapper.readValue(json, UpstreamTimeout.class);
    assertEquals(original, deserialized);
  }

  @Test
  void equals_sameValues_shouldBeEqual() {
    UpstreamTimeout timeout1 = new UpstreamTimeout(6, 10, 15);
    UpstreamTimeout timeout2 = new UpstreamTimeout(6, 10, 15);
    assertEquals(timeout1, timeout2);
    assertEquals(timeout1.hashCode(), timeout2.hashCode());
  }

  @Test
  void equals_differentValues_shouldNotBeEqual() {
    UpstreamTimeout timeout1 = new UpstreamTimeout(6, 10, 15);
    UpstreamTimeout timeout2 = new UpstreamTimeout(5, 10, 15);
    assertNotEquals(timeout1, timeout2);
  }

  @Test
  void toApiMap_allFields_shouldContainAllEntries() {
    UpstreamTimeout timeout = new UpstreamTimeout(6, 10, 15);
    Map<String, Object> map = timeout.toApiMap();
    assertNotNull(map);
    assertEquals(3, map.size());
    assertEquals(6, map.get("connect"));
    assertEquals(10, map.get("send"));
    assertEquals(15, map.get("read"));
  }

  @Test
  void toApiMap_partialFields_shouldOmitNullEntries() {
    UpstreamTimeout timeout = new UpstreamTimeout(5, null, null);
    Map<String, Object> map = timeout.toApiMap();
    assertNotNull(map);
    assertEquals(1, map.size());
    assertEquals(5, map.get("connect"));
  }

  @Test
  void toApiMap_allNull_shouldReturnEmptyMap() {
    UpstreamTimeout timeout = new UpstreamTimeout(null, null, null);
    Map<String, Object> map = timeout.toApiMap();
    assertNotNull(map);
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_fractionalValues_shouldPreserveDecimals() {
    UpstreamTimeout timeout = new UpstreamTimeout(0.5, 1.5, 2.0);
    Map<String, Object> map = timeout.toApiMap();
    assertNotNull(map);
    assertEquals(0.5, ((Number) map.get("connect")).doubleValue(), 0.001);
    assertEquals(1.5, ((Number) map.get("send")).doubleValue(), 0.001);
    assertEquals(2.0, ((Number) map.get("read")).doubleValue(), 0.001);
  }
}
