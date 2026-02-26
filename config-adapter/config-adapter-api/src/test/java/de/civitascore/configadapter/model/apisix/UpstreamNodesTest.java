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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UpstreamNodes}. */
class UpstreamNodesTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  // ---- Factory methods ----

  @Test
  void ofMap_whenValid_shouldCreateMapFormat() {
    UpstreamNodes nodes = UpstreamNodes.ofMap(Map.of("backend1:8080", 1, "backend2:8080", 2));
    assertTrue(nodes.isMapFormat());
    assertFalse(nodes.isListFormat());
    assertNotNull(nodes.asMap());
    assertNull(nodes.asList());
    assertEquals(1, nodes.asMap().get("backend1:8080"));
    assertEquals(2, nodes.asMap().get("backend2:8080"));
  }

  @Test
  void ofList_whenValid_shouldCreateListFormat() {
    List<UpstreamNode> nodeList =
        List.of(
            new UpstreamNode("backend1", 8080, 1, null, null),
            new UpstreamNode("backend2", 8080, 2, null, null));
    UpstreamNodes nodes = UpstreamNodes.ofList(nodeList);
    assertFalse(nodes.isMapFormat());
    assertTrue(nodes.isListFormat());
    assertNull(nodes.asMap());
    assertNotNull(nodes.asList());
    assertEquals(2, nodes.asList().size());
  }

  @Test
  void ofMap_whenNull_shouldThrowNullPointerException() {
    assertThrows(NullPointerException.class, () -> UpstreamNodes.ofMap(null));
  }

  @Test
  void ofList_whenNull_shouldThrowNullPointerException() {
    assertThrows(NullPointerException.class, () -> UpstreamNodes.ofList(null));
  }

  // ---- toApiValue ----

  @Test
  void toApiValue_mapFormat_shouldReturnMap() {
    UpstreamNodes nodes = UpstreamNodes.ofMap(Map.of("backend:8080", 1));
    Object apiValue = nodes.toApiValue();
    assertTrue(apiValue instanceof Map);
    @SuppressWarnings("unchecked")
    Map<String, Integer> map = (Map<String, Integer>) apiValue;
    assertEquals(1, map.get("backend:8080"));
  }

  @Test
  void toApiValue_listFormat_shouldReturnList() {
    UpstreamNodes nodes =
        UpstreamNodes.ofList(List.of(new UpstreamNode("backend", 8080, 1, null, null)));
    Object apiValue = nodes.toApiValue();
    assertTrue(apiValue instanceof List);
    @SuppressWarnings("unchecked")
    List<UpstreamNode> list = (List<UpstreamNode>) apiValue;
    assertEquals(1, list.size());
    assertEquals("backend", list.getFirst().host());
  }

  // ---- JSON Deserialization ----

  @Test
  void jsonDeserialization_mapFormat_shouldParseAsMapNodes() throws Exception {
    String json =
        """
            {"backend1:8080": 1, "backend2:8080": 2}
            """;

    UpstreamNodes nodes = objectMapper.readValue(json, UpstreamNodes.class);
    assertTrue(nodes.isMapFormat());
    assertEquals(1, nodes.asMap().get("backend1:8080"));
    assertEquals(2, nodes.asMap().get("backend2:8080"));
  }

  @Test
  void jsonDeserialization_arrayFormat_shouldParseAsListNodes() throws Exception {
    String json =
        """
            [
              {"host": "backend1", "port": 8080, "weight": 1},
              {"host": "backend2", "port": 8080, "weight": 2, "priority": 10}
            ]
            """;

    UpstreamNodes nodes = objectMapper.readValue(json, UpstreamNodes.class);
    assertTrue(nodes.isListFormat());
    assertEquals(2, nodes.asList().size());
    assertEquals("backend1", nodes.asList().get(0).host());
    assertEquals(8080, nodes.asList().get(0).port());
    assertEquals(1, nodes.asList().get(0).weight());
    assertNull(nodes.asList().get(0).priority());
    assertEquals("backend2", nodes.asList().get(1).host());
    assertEquals(10, nodes.asList().get(1).priority());
  }

  @Test
  void jsonDeserialization_arrayFormatWithMetadata_shouldParseMetadata() throws Exception {
    String json =
        """
            [
              {"host": "backend1", "port": 8080, "weight": 1, "metadata": {"idc": "dc1"}}
            ]
            """;

    UpstreamNodes nodes = objectMapper.readValue(json, UpstreamNodes.class);
    assertTrue(nodes.isListFormat());
    assertEquals("dc1", nodes.asList().getFirst().metadata().get("idc"));
  }

  @Test
  void jsonDeserialization_nullValue_shouldReturnNull() throws Exception {
    String json =
        """
            {
              "resourceType": "apisix-upstream",
              "type": "roundrobin",
              "nodes": null
            }
            """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);
    assertNull(value.getNodes());
  }

  // ---- JSON Serialization ----

  @Test
  void jsonSerialization_mapFormat_shouldSerializeAsObject() throws Exception {
    UpstreamNodes nodes = UpstreamNodes.ofMap(Map.of("backend:8080", 1));
    String json = objectMapper.writeValueAsString(nodes);
    assertTrue(json.contains("backend:8080"));
    assertTrue(json.startsWith("{"));
  }

  @Test
  void jsonSerialization_listFormat_shouldSerializeAsArray() throws Exception {
    UpstreamNodes nodes =
        UpstreamNodes.ofList(List.of(new UpstreamNode("backend", 8080, 1, null, null)));
    String json = objectMapper.writeValueAsString(nodes);
    assertTrue(json.startsWith("["));
    assertTrue(json.contains("\"host\":\"backend\""));
  }

  // ---- JSON Round-Trip ----

  @Test
  void jsonRoundTrip_mapFormat_shouldPreserveValues() throws Exception {
    UpstreamNodes original = UpstreamNodes.ofMap(Map.of("backend1:8080", 1, "backend2:8080", 2));
    String json = objectMapper.writeValueAsString(original);
    UpstreamNodes deserialized = objectMapper.readValue(json, UpstreamNodes.class);
    assertEquals(original, deserialized);
  }

  @Test
  void jsonRoundTrip_listFormat_shouldPreserveValues() throws Exception {
    UpstreamNodes original =
        UpstreamNodes.ofList(
            List.of(
                new UpstreamNode("backend1", 8080, 1, 0, Map.of("idc", "dc1")),
                new UpstreamNode("backend2", 8080, 2, null, null)));
    String json = objectMapper.writeValueAsString(original);
    UpstreamNodes deserialized = objectMapper.readValue(json, UpstreamNodes.class);
    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_invalidToken_shouldThrowException() {
    String json = "\"just-a-string\"";
    assertThrows(Exception.class, () -> objectMapper.readValue(json, UpstreamNodes.class));
  }

  @Test
  void jsonDeserialization_numericToken_shouldThrowException() {
    String json = "42";
    assertThrows(Exception.class, () -> objectMapper.readValue(json, UpstreamNodes.class));
  }

  // ---- JSON Deserialization in ApisixConfigValue context ----

  @Test
  void jsonDeserialization_inApisixConfigValue_mapFormat_shouldParse() throws Exception {
    String json =
        """
            {
              "resourceType": "apisix-upstream",
              "type": "roundrobin",
              "nodes": {"backend1:8080": 1, "backend2:8080": 2}
            }
            """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);
    assertNotNull(value.getNodes());
    assertTrue(value.getNodes().isMapFormat());
    assertEquals(1, value.getNodes().asMap().get("backend1:8080"));
  }

  @Test
  void jsonDeserialization_inApisixConfigValue_arrayFormat_shouldParse() throws Exception {
    String json =
        """
            {
              "resourceType": "apisix-upstream",
              "type": "roundrobin",
              "nodes": [
                {"host": "backend1", "port": 8080, "weight": 1},
                {"host": "backend2", "port": 8080, "weight": 2}
              ]
            }
            """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);
    assertNotNull(value.getNodes());
    assertTrue(value.getNodes().isListFormat());
    assertEquals(2, value.getNodes().asList().size());
    assertEquals("backend1", value.getNodes().asList().get(0).host());
  }

  // ---- equals/hashCode ----

  @Test
  void equals_sameMapValues_shouldBeEqual() {
    UpstreamNodes nodes1 = UpstreamNodes.ofMap(Map.of("backend:8080", 1));
    UpstreamNodes nodes2 = UpstreamNodes.ofMap(Map.of("backend:8080", 1));
    assertEquals(nodes1, nodes2);
    assertEquals(nodes1.hashCode(), nodes2.hashCode());
  }

  @Test
  void equals_sameListValues_shouldBeEqual() {
    UpstreamNodes nodes1 =
        UpstreamNodes.ofList(List.of(new UpstreamNode("backend", 8080, 1, null, null)));
    UpstreamNodes nodes2 =
        UpstreamNodes.ofList(List.of(new UpstreamNode("backend", 8080, 1, null, null)));
    assertEquals(nodes1, nodes2);
    assertEquals(nodes1.hashCode(), nodes2.hashCode());
  }

  @Test
  void equals_differentFormats_shouldNotBeEqual() {
    UpstreamNodes mapNodes = UpstreamNodes.ofMap(Map.of("backend:8080", 1));
    UpstreamNodes listNodes =
        UpstreamNodes.ofList(List.of(new UpstreamNode("backend", 8080, 1, null, null)));
    assertNotEquals(mapNodes, listNodes);
  }

  @Test
  void equals_differentValues_shouldNotBeEqual() {
    UpstreamNodes nodes1 = UpstreamNodes.ofMap(Map.of("backend1:8080", 1));
    UpstreamNodes nodes2 = UpstreamNodes.ofMap(Map.of("backend2:8080", 2));
    assertNotEquals(nodes1, nodes2);
  }

  // ---- toString ----

  @Test
  void toString_mapFormat_shouldContainMap() {
    UpstreamNodes nodes = UpstreamNodes.ofMap(Map.of("backend:8080", 1));
    String str = nodes.toString();
    assertTrue(str.contains("map="));
    assertTrue(str.contains("backend:8080"));
  }

  @Test
  void toString_listFormat_shouldContainList() {
    UpstreamNodes nodes =
        UpstreamNodes.ofList(List.of(new UpstreamNode("backend", 8080, 1, null, null)));
    String str = nodes.toString();
    assertTrue(str.contains("list="));
  }

  // ---- Immutability ----

  @Test
  void ofMap_shouldReturnDefensiveCopy() {
    java.util.HashMap<String, Integer> mutable = new java.util.HashMap<>();
    mutable.put("backend:8080", 1);
    UpstreamNodes nodes = UpstreamNodes.ofMap(mutable);
    mutable.put("backend:9090", 2);
    assertEquals(1, nodes.asMap().size());
  }

  @Test
  void ofList_shouldReturnDefensiveCopy() {
    java.util.ArrayList<UpstreamNode> mutable = new java.util.ArrayList<>();
    mutable.add(new UpstreamNode("backend", 8080, 1, null, null));
    UpstreamNodes nodes = UpstreamNodes.ofList(mutable);
    mutable.add(new UpstreamNode("backend2", 8080, 2, null, null));
    assertEquals(1, nodes.asList().size());
  }
}
