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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UpstreamNode}. */
class UpstreamNodeTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_allFields_shouldStoreValues() {
    UpstreamNode node = new UpstreamNode("backend1.example.com", 8080, 1, 0, Map.of("idc", "dc1"));
    assertEquals("backend1.example.com", node.host());
    assertEquals(8080, node.port());
    assertEquals(1, node.weight());
    assertEquals(0, node.priority());
    assertEquals(Map.of("idc", "dc1"), node.metadata());
  }

  @Test
  void constructor_requiredFieldsOnly_shouldAllowNullOptionals() {
    UpstreamNode node = new UpstreamNode("backend1", 8080, 1, null, null);
    assertEquals("backend1", node.host());
    assertEquals(8080, node.port());
    assertEquals(1, node.weight());
    assertNull(node.priority());
    assertNull(node.metadata());
  }

  @Test
  void jsonSerialization_allFields_shouldProduceCorrectJson() throws Exception {
    UpstreamNode node = new UpstreamNode("backend1.example.com", 8080, 1, 0, Map.of("idc", "dc1"));

    String json = objectMapper.writeValueAsString(node);
    assertNotNull(json);

    UpstreamNode deserialized = objectMapper.readValue(json, UpstreamNode.class);
    assertEquals(node, deserialized);
  }

  @Test
  void jsonSerialization_nullOptionals_shouldOmitNullFields() throws Exception {
    UpstreamNode node = new UpstreamNode("backend1", 8080, 1, null, null);

    String json = objectMapper.writeValueAsString(node);
    assertNotNull(json);
    assertEquals(false, json.contains("priority"));
    assertEquals(false, json.contains("metadata"));
  }

  @Test
  void jsonDeserialization_fromApisixExample_shouldCreateRecord() throws Exception {
    String json =
        """
                {
                  "host": "backend1.example.com",
                  "port": 8080,
                  "weight": 2,
                  "priority": 10,
                  "metadata": {"idc": "dc1"}
                }
                """;

    UpstreamNode node = objectMapper.readValue(json, UpstreamNode.class);
    assertEquals("backend1.example.com", node.host());
    assertEquals(8080, node.port());
    assertEquals(2, node.weight());
    assertEquals(10, node.priority());
    assertEquals("dc1", node.metadata().get("idc"));
  }

  @Test
  void jsonDeserialization_minimalFields_shouldHandleMissing() throws Exception {
    String json =
        """
                {
                  "host": "backend1",
                  "port": 8080,
                  "weight": 1
                }
                """;

    UpstreamNode node = objectMapper.readValue(json, UpstreamNode.class);
    assertEquals("backend1", node.host());
    assertEquals(8080, node.port());
    assertEquals(1, node.weight());
    assertNull(node.priority());
    assertNull(node.metadata());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    UpstreamNode original =
        new UpstreamNode("backend1.example.com", 8080, 2, 10, Map.of("idc", "dc1"));
    String json = objectMapper.writeValueAsString(original);
    UpstreamNode deserialized = objectMapper.readValue(json, UpstreamNode.class);
    assertEquals(original, deserialized);
  }

  @Test
  void equals_sameValues_shouldBeEqual() {
    UpstreamNode node1 = new UpstreamNode("backend1", 8080, 1, null, null);
    UpstreamNode node2 = new UpstreamNode("backend1", 8080, 1, null, null);
    assertEquals(node1, node2);
    assertEquals(node1.hashCode(), node2.hashCode());
  }

  @Test
  void equals_differentValues_shouldNotBeEqual() {
    UpstreamNode node1 = new UpstreamNode("backend1", 8080, 1, null, null);
    UpstreamNode node2 = new UpstreamNode("backend2", 8080, 1, null, null);
    assertNotEquals(node1, node2);
  }
}
