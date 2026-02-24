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

/** Unit tests for ApisixConfigValue */
class ApisixConfigValueTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    ApisixConfigValue value = new ApisixConfigValue();
    assertNull(value.getType());
    assertNull(value.getNodes());
    assertNull(value.getTimeout());
    assertNull(value.getChecks());
    assertNull(value.getScheme());
    assertNull(value.getTls());
    assertNotNull(value.getAdditionalProperties());
    assertTrue(value.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("chash");
    value.setNodes(UpstreamNodes.ofMap(Map.of("backend:8080", 1)));
    value.setScheme("https");

    assertEquals("chash", value.getType());
    assertEquals(UpstreamNodes.ofMap(Map.of("backend:8080", 1)), value.getNodes());
    assertEquals("https", value.getScheme());
  }

  @Test
  void getType_whenTypeExists_shouldReturnType() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    assertEquals("roundrobin", value.getType());
  }

  @Test
  void getType_whenTypeNotSet_shouldReturnNull() {
    ApisixConfigValue value = new ApisixConfigValue();
    assertNull(value.getType());
  }

  @Test
  void getNodes_whenNodesExist_shouldReturnNodes() {
    UpstreamNodes nodes = UpstreamNodes.ofMap(Map.of("backend1:8080", 1, "backend2:8080", 2));
    ApisixConfigValue value = new ApisixConfigValue();
    value.setNodes(nodes);
    assertEquals(nodes, value.getNodes());
  }

  @Test
  void getNodes_whenNodesNotSet_shouldReturnNull() {
    ApisixConfigValue value = new ApisixConfigValue();
    assertNull(value.getNodes());
  }

  @Test
  void toApiMap_whenFieldsSet_shouldContainAllFields() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    value.setNodes(UpstreamNodes.ofMap(Map.of("backend1:8080", 1)));
    value.setScheme("https");

    Map<String, Object> map = value.toApiMap();
    assertEquals("roundrobin", map.get("type"));
    assertEquals(Map.of("backend1:8080", 1), map.get("nodes"));
    assertEquals("https", map.get("scheme"));
    assertNull(map.get("timeout"));
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    ApisixConfigValue value = new ApisixConfigValue();
    Map<String, Object> map = value.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    value.handleUnknownProperty("custom_key", "custom_value");

    Map<String, Object> map = value.toApiMap();
    assertEquals("roundrobin", map.get("type"));
    assertEquals("custom_value", map.get("custom_key"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    ApisixConfigValue value1 = new ApisixConfigValue();
    value1.setType("roundrobin");
    ApisixConfigValue value2 = new ApisixConfigValue();
    value2.setType("roundrobin");

    assertEquals(value1, value2);
    assertEquals(value1.hashCode(), value2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    assertEquals(value, value);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    assertNotEquals(null, value);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    assertNotEquals("string", value);
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    ApisixConfigValue value1 = new ApisixConfigValue();
    value1.setType("roundrobin");
    ApisixConfigValue value2 = new ApisixConfigValue();
    value2.setType("chash");

    assertNotEquals(value1, value2);
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    String str = value.toString();
    assertTrue(str.contains("ApisixConfigValue"));
    assertTrue(str.contains("type"));
    assertTrue(str.contains("roundrobin"));
  }

  @Test
  void jsonSerialization_whenValidData_shouldProduceValidJson() throws Exception {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    value.setNodes(UpstreamNodes.ofMap(Map.of("backend1:8080", 1, "backend2:8080", 2)));
    value.setScheme("http");

    String json = objectMapper.writeValueAsString(value);
    assertNotNull(json);
    assertTrue(json.contains("roundrobin"));
    assertTrue(json.contains("backend1:8080"));
  }

  @Test
  void jsonDeserialization_whenValidJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-upstream",
                  "type": "roundrobin",
                  "scheme": "https"
                }
                """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);

    assertEquals("roundrobin", value.getType());
    assertEquals("https", value.getScheme());
  }

  @Test
  void jsonDeserialization_whenMinimalJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-upstream",
                  "type": "roundrobin"
                }
                """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);

    assertEquals("roundrobin", value.getType());
  }

  @Test
  void jsonDeserialization_whenGrpcScheme_shouldParseCorrectly() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-upstream",
                  "type": "least_conn",
                  "scheme": "grpc"
                }
                """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);

    assertEquals("least_conn", value.getType());
    assertEquals("grpc", value.getScheme());
  }

  @Test
  void getType_whenAllLoadBalancingTypes_shouldReturnCorrectly() {
    String[] types = {"roundrobin", "chash", "ewma", "least_conn"};

    for (String type : types) {
      ApisixConfigValue value = new ApisixConfigValue();
      value.setType(type);
      assertEquals(type, value.getType());
    }
  }

  @Test
  void setTimeout_whenUpstreamTimeout_shouldStoreAndSerialize() {
    ApisixConfigValue value = new ApisixConfigValue();
    UpstreamTimeout timeout = new UpstreamTimeout(6, 10, 15);
    value.setTimeout(timeout);

    assertEquals(timeout, value.getTimeout());

    Map<String, Object> map = value.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> timeoutMap = (Map<String, Object>) map.get("timeout");
    assertNotNull(timeoutMap);
    assertEquals(6, timeoutMap.get("connect"));
    assertEquals(10, timeoutMap.get("send"));
    assertEquals(15, timeoutMap.get("read"));
  }

  @Test
  void setTimeout_whenPartialTimeout_shouldOmitNullFields() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setTimeout(new UpstreamTimeout(5, null, null));

    Map<String, Object> map = value.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> timeoutMap = (Map<String, Object>) map.get("timeout");
    assertNotNull(timeoutMap);
    assertEquals(1, timeoutMap.size());
    assertEquals(5, timeoutMap.get("connect"));
  }

  @Test
  void setTimeout_whenAllNull_shouldNotIncludeTimeout() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setTimeout(new UpstreamTimeout(null, null, null));

    Map<String, Object> map = value.toApiMap();
    assertNull(map.get("timeout"));
  }

  @Test
  void setTls_whenUpstreamTls_shouldStoreAndSerialize() {
    ApisixConfigValue value = new ApisixConfigValue();
    UpstreamTls tls = new UpstreamTls();
    tls.setSni("backend.example.com");
    tls.setVerify(true);
    value.setTls(tls);

    assertEquals(tls, value.getTls());

    Map<String, Object> map = value.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> tlsMap = (Map<String, Object>) map.get("tls");
    assertNotNull(tlsMap);
    assertEquals("backend.example.com", tlsMap.get("sni"));
    assertEquals(true, tlsMap.get("verify"));
  }

  @Test
  void setChecks_whenHealthCheck_shouldStoreAndSerialize() {
    ApisixConfigValue value = new ApisixConfigValue();
    HealthCheck checks = new HealthCheck();
    ActiveHealthCheck active = new ActiveHealthCheck();
    active.setType("http");
    active.setHttpPath("/health");
    checks.setActive(active);
    value.setChecks(checks);

    assertEquals(checks, value.getChecks());

    Map<String, Object> map = value.toApiMap();
    assertNotNull(map.get("checks"));
  }

  @Test
  void jsonDeserialization_whenFullUpstreamWithTypedFields_shouldParseAll() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-upstream",
                  "type": "roundrobin",
                  "nodes": {"backend1:8080": 1, "backend2:8080": 2},
                  "scheme": "https",
                  "timeout": {"connect": 6, "send": 10, "read": 15},
                  "tls": {"sni": "backend.example.com", "verify": true},
                  "checks": {
                    "active": {
                      "type": "http",
                      "http_path": "/health",
                      "healthy": {"interval": 2, "successes": 1}
                    }
                  }
                }
                """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);

    assertEquals("roundrobin", value.getType());
    assertEquals("https", value.getScheme());
    assertNotNull(value.getNodes());
    assertTrue(value.getNodes().isMapFormat());
    assertEquals(1, value.getNodes().asMap().get("backend1:8080"));
    assertEquals(2, value.getNodes().asMap().get("backend2:8080"));

    assertNotNull(value.getTimeout());
    assertEquals(6, value.getTimeout().connect());
    assertEquals(10, value.getTimeout().send());
    assertEquals(15, value.getTimeout().read());

    assertNotNull(value.getTls());
    assertEquals("backend.example.com", value.getTls().getSni());
    assertEquals(true, value.getTls().getVerify());

    assertNotNull(value.getChecks());
    assertNotNull(value.getChecks().getActive());
    assertEquals("http", value.getChecks().getActive().getType());
    assertEquals("/health", value.getChecks().getActive().getHttpPath());
  }
}
