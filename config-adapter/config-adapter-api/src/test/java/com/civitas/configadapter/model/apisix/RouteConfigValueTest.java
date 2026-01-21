/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for RouteConfigValue */
class RouteConfigValueTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldCreateEmptyData() {
    RouteConfigValue value = new RouteConfigValue();
    assertNotNull(value.data());
    assertTrue(value.data().isEmpty());
  }

  @Test
  void constructor_whenValidMap_shouldPopulateData() {
    Map<String, Object> data =
        Map.of(
            "uri", "/api/v1/users/*",
            "methods", List.of("GET", "POST"),
            "upstream_id", "backend-users");

    RouteConfigValue value = new RouteConfigValue(data);
    assertNotNull(value.data());
    assertEquals("/api/v1/users/*", value.getUri());
    assertEquals(List.of("GET", "POST"), value.getMethods());
    assertEquals("backend-users", value.getUpstreamId());
  }

  @Test
  void constructor_whenNullMap_shouldCreateEmptyData() {
    RouteConfigValue value = new RouteConfigValue(null);
    assertNotNull(value.data());
    assertTrue(value.data().isEmpty());
  }

  @Test
  void setProperty_whenCalled_shouldStoreValue() {
    RouteConfigValue value = new RouteConfigValue();
    value.setProperty("uri", "/api/v1/test");
    value.setProperty("methods", List.of("GET"));

    assertEquals("/api/v1/test", value.get("uri"));
    assertEquals(List.of("GET"), value.get("methods"));
  }

  @Test
  void getUri_whenUriExists_shouldReturnUri() {
    RouteConfigValue value = new RouteConfigValue(Map.of("uri", "/api/v1/users/*"));
    assertEquals("/api/v1/users/*", value.getUri());
  }

  @Test
  void getUris_whenUrisExist_shouldReturnUris() {
    List<String> uris = List.of("/api/v1/users/*", "/api/v1/admin/*");
    RouteConfigValue value = new RouteConfigValue(Map.of("uris", uris));
    assertEquals(uris, value.getUris());
  }

  @Test
  void getMethods_whenMethodsExist_shouldReturnMethods() {
    List<String> methods = List.of("GET", "POST", "PUT", "DELETE");
    RouteConfigValue value = new RouteConfigValue(Map.of("methods", methods));
    assertEquals(methods, value.getMethods());
  }

  @Test
  void getUpstreamId_whenUpstreamIdExists_shouldReturnUpstreamId() {
    RouteConfigValue value = new RouteConfigValue(Map.of("upstream_id", "my-upstream"));
    assertEquals("my-upstream", value.getUpstreamId());
  }

  @Test
  void getUpstream_whenUpstreamExists_shouldReturnUpstream() {
    Map<String, Object> upstream = Map.of("type", "roundrobin", "nodes", Map.of("backend:8080", 1));
    RouteConfigValue value = new RouteConfigValue(Map.of("upstream", upstream));
    assertEquals(upstream, value.getUpstream());
  }

  @Test
  void getPlugins_whenPluginsExist_shouldReturnPlugins() {
    Map<String, Object> plugins =
        Map.of(
            "prometheus", Map.of(),
            "openid-connect", Map.of("client_id", "api-gateway"));
    RouteConfigValue value = new RouteConfigValue(Map.of("plugins", plugins));
    assertEquals(plugins, value.getPlugins());
  }

  @Test
  void getPlugin_whenPluginExists_shouldReturnPluginConfig() {
    Map<String, Object> openidConfig =
        Map.of(
            "client_id", "api-gateway",
            "discovery", "https://keycloak.example.com/.well-known/openid-configuration");
    Map<String, Object> plugins = Map.of("openid-connect", openidConfig, "prometheus", Map.of());
    RouteConfigValue value = new RouteConfigValue(Map.of("plugins", plugins));

    assertEquals(openidConfig, value.getPlugin("openid-connect"));
    assertEquals(Map.of(), value.getPlugin("prometheus"));
    assertNull(value.getPlugin("nonexistent"));
  }

  @Test
  void hasPlugin_whenPluginExists_shouldReturnTrue() {
    Map<String, Object> plugins = Map.of("prometheus", Map.of(), "loki", Map.of());
    RouteConfigValue value = new RouteConfigValue(Map.of("plugins", plugins));

    assertTrue(value.hasPlugin("prometheus"));
    assertTrue(value.hasPlugin("loki"));
    assertFalse(value.hasPlugin("openid-connect"));
  }

  @Test
  void hasPlugin_whenNoPlugins_shouldReturnFalse() {
    RouteConfigValue value = new RouteConfigValue(Map.of("uri", "/test"));
    assertFalse(value.hasPlugin("prometheus"));
  }

  @Test
  void getPriority_whenPriorityExists_shouldReturnPriority() {
    RouteConfigValue value = new RouteConfigValue(Map.of("priority", 100));
    assertEquals(100, value.getPriority());
  }

  @Test
  void getPriority_whenPriorityNotSet_shouldReturnNull() {
    RouteConfigValue value = new RouteConfigValue(Map.of("uri", "/test"));
    assertNull(value.getPriority());
  }

  @Test
  void getStatus_whenStatusExists_shouldReturnStatus() {
    RouteConfigValue enabledValue = new RouteConfigValue(Map.of("status", 1));
    assertEquals(1, enabledValue.getStatus());

    RouteConfigValue disabledValue = new RouteConfigValue(Map.of("status", 0));
    assertEquals(0, disabledValue.getStatus());
  }

  @Test
  void getStatus_whenStatusNotSet_shouldReturnNull() {
    RouteConfigValue value = new RouteConfigValue(Map.of("uri", "/test"));
    assertNull(value.getStatus());
  }

  @Test
  void has_whenKeyExists_shouldReturnTrue() {
    RouteConfigValue value =
        new RouteConfigValue(Map.of("uri", "/test", "methods", List.of("GET")));
    assertTrue(value.has("uri"));
    assertTrue(value.has("methods"));
    assertFalse(value.has("upstream_id"));
  }

  @Test
  void get_whenKeyExists_shouldReturnValue() {
    RouteConfigValue value = new RouteConfigValue(Map.of("uri", "/test", "priority", 50));
    assertEquals("/test", value.get("uri"));
    assertEquals(50, value.get("priority"));
    assertNull(value.get("nonexistent"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    Map<String, Object> data = Map.of("uri", "/test");
    RouteConfigValue value1 = new RouteConfigValue(data);
    RouteConfigValue value2 = new RouteConfigValue(data);

    assertEquals(value1, value2);
    assertEquals(value1.hashCode(), value2.hashCode());
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    RouteConfigValue value1 = new RouteConfigValue(Map.of("uri", "/test1"));
    RouteConfigValue value2 = new RouteConfigValue(Map.of("uri", "/test2"));

    assertNotEquals(value1, value2);
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    RouteConfigValue value = new RouteConfigValue(Map.of("uri", "/test"));
    String str = value.toString();
    assertTrue(str.contains("RouteConfigValue"));
    assertTrue(str.contains("uri"));
    assertTrue(str.contains("/test"));
  }

  @Test
  void jsonSerialization_whenValidData_shouldProduceValidJson() throws Exception {
    Map<String, Object> data = new HashMap<>();
    data.put("uri", "/api/v1/users/*");
    data.put("methods", List.of("GET", "POST"));
    data.put("upstream_id", "backend-users");
    data.put("plugins", Map.of("prometheus", Map.of()));

    RouteConfigValue value = new RouteConfigValue(data);

    String json = objectMapper.writeValueAsString(value);
    assertNotNull(json);
    assertTrue(json.contains("/api/v1/users/*"));
    assertTrue(json.contains("backend-users"));
  }

  @Test
  void jsonDeserialization_whenValidJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-route",
                  "uri": "/api/v1/users/*",
                  "methods": ["GET", "POST"],
                  "upstream_id": "backend-users",
                  "plugins": {
                    "prometheus": {}
                  }
                }
                """;

    RouteConfigValue value = objectMapper.readValue(json, RouteConfigValue.class);

    assertEquals("/api/v1/users/*", value.getUri());
    assertEquals(List.of("GET", "POST"), value.getMethods());
    assertEquals("backend-users", value.getUpstreamId());
    assertTrue(value.hasPlugin("prometheus"));
  }

  @Test
  void jsonDeserialization_whenFullPluginConfig_shouldParseAllPlugins() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-route",
                  "uri": "/api/v1/data/*",
                  "methods": ["GET", "POST", "PUT", "DELETE"],
                  "upstream_id": "data-service",
                  "plugins": {
                    "openid-connect": {
                      "discovery": "https://keycloak.example.com/.well-known/openid-configuration",
                      "client_id": "api-gateway",
                      "client_secret": "secret"
                    },
                    "serverless-pre-function": {
                      "phase": "rewrite",
                      "functions": ["return function() end"]
                    },
                    "serverless-post-function": {
                      "phase": "log",
                      "functions": ["return function() end"]
                    },
                    "response-rewrite": {
                      "headers": {"X-Custom-Header": "value"}
                    },
                    "proxy-rewrite": {
                      "regex_uri": ["/api/v1/(.*)", "/$1"]
                    },
                    "prometheus": {},
                    "loki": {
                      "endpoint": "http://loki:3100"
                    }
                  }
                }
                """;

    RouteConfigValue value = objectMapper.readValue(json, RouteConfigValue.class);

    // Verify all CIVITAS/CORE V1 plugins are present
    assertTrue(value.hasPlugin("openid-connect"));
    assertTrue(value.hasPlugin("serverless-pre-function"));
    assertTrue(value.hasPlugin("serverless-post-function"));
    assertTrue(value.hasPlugin("response-rewrite"));
    assertTrue(value.hasPlugin("proxy-rewrite"));
    assertTrue(value.hasPlugin("prometheus"));
    assertTrue(value.hasPlugin("loki"));

    // Verify plugin configuration
    Map<String, Object> openidConnect = value.getPlugin("openid-connect");
    assertEquals("api-gateway", openidConnect.get("client_id"));
  }
}
