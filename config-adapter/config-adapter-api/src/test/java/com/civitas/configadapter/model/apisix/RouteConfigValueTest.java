/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.model.apisix.plugins.ProxyRewritePlugin;
import com.civitas.configadapter.model.apisix.plugins.RoutePlugins;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  void constructor_whenNoArgs_shouldHaveNullFields() {
    RouteConfigValue value = new RouteConfigValue();
    assertNull(value.getUri());
    assertNull(value.getUris());
    assertNull(value.getMethods());
    assertNull(value.getUpstreamId());
    assertNull(value.getUpstream());
    assertNull(value.getPlugins());
    assertNull(value.getPriority());
    assertNull(value.getStatus());
    assertNull(value.getPluginConfigId());
    assertNotNull(value.getAdditionalProperties());
    assertTrue(value.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/api/v1/users/*");
    value.setMethods(List.of("GET", "POST"));
    value.setUpstreamId("backend-users");

    assertEquals("/api/v1/users/*", value.getUri());
    assertEquals(List.of("GET", "POST"), value.getMethods());
    assertEquals("backend-users", value.getUpstreamId());
  }

  @Test
  void getUri_whenUriExists_shouldReturnUri() {
    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/api/v1/users/*");
    assertEquals("/api/v1/users/*", value.getUri());
  }

  @Test
  void getUris_whenUrisExist_shouldReturnUris() {
    List<String> uris = List.of("/api/v1/users/*", "/api/v1/admin/*");
    RouteConfigValue value = new RouteConfigValue();
    value.setUris(uris);
    assertEquals(uris, value.getUris());
  }

  @Test
  void getMethods_whenMethodsExist_shouldReturnMethods() {
    List<String> methods = List.of("GET", "POST", "PUT", "DELETE");
    RouteConfigValue value = new RouteConfigValue();
    value.setMethods(methods);
    assertEquals(methods, value.getMethods());
  }

  @Test
  void getUpstreamId_whenUpstreamIdExists_shouldReturnUpstreamId() {
    RouteConfigValue value = new RouteConfigValue();
    value.setUpstreamId("my-upstream");
    assertEquals("my-upstream", value.getUpstreamId());
  }

  @Test
  void getUpstream_whenUpstreamExists_shouldReturnUpstream() {
    ApisixConfigValue upstream = new ApisixConfigValue();
    upstream.setType("roundrobin");
    upstream.setNodes(UpstreamNodes.ofMap(Map.of("backend:8080", 1)));
    RouteConfigValue value = new RouteConfigValue();
    value.setUpstream(upstream);
    assertEquals(upstream, value.getUpstream());
  }

  @Test
  void toApiMap_whenUpstreamSet_shouldSerializeViaToApiMap() {
    ApisixConfigValue upstream = new ApisixConfigValue();
    upstream.setType("roundrobin");
    upstream.setNodes(UpstreamNodes.ofMap(Map.of("backend:8080", 1)));

    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/test");
    value.setUpstream(upstream);

    Map<String, Object> map = value.toApiMap();
    assertNotNull(map.get("upstream"));
    @SuppressWarnings("unchecked")
    Map<String, Object> upstreamMap = (Map<String, Object>) map.get("upstream");
    assertEquals("roundrobin", upstreamMap.get("type"));
    assertEquals(Map.of("backend:8080", 1), upstreamMap.get("nodes"));
  }

  @Test
  void getPlugins_whenPluginsExist_shouldReturnPlugins() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of());
    plugins.handleUnknownPlugin("openid-connect", Map.of("client_id", "api-gateway"));

    RouteConfigValue value = new RouteConfigValue();
    value.setPlugins(plugins);
    assertNotNull(value.getPlugins());
    assertTrue(value.getPlugins().hasPlugin("prometheus"));
    assertTrue(value.getPlugins().hasPlugin("openid-connect"));
  }

  @Test
  void getPlugins_whenTypedPluginSet_shouldReturnPluginConfig() {
    RoutePlugins plugins = new RoutePlugins();
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/users");
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue value = new RouteConfigValue();
    value.setPlugins(plugins);

    assertNotNull(value.getPlugins().getProxyRewrite());
    assertEquals("/users", value.getPlugins().getProxyRewrite().getUri());
  }

  @Test
  void plugins_hasPlugin_whenPluginExists_shouldReturnTrue() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of());
    plugins.handleUnknownPlugin("loki", Map.of());

    RouteConfigValue value = new RouteConfigValue();
    value.setPlugins(plugins);

    assertTrue(value.getPlugins().hasPlugin("prometheus"));
    assertTrue(value.getPlugins().hasPlugin("loki"));
    assertFalse(value.getPlugins().hasPlugin("openid-connect"));
  }

  @Test
  void getPriority_whenPriorityExists_shouldReturnPriority() {
    RouteConfigValue value = new RouteConfigValue();
    value.setPriority(100);
    assertEquals(100, value.getPriority());
  }

  @Test
  void getPriority_whenPriorityNotSet_shouldReturnNull() {
    RouteConfigValue value = new RouteConfigValue();
    assertNull(value.getPriority());
  }

  @Test
  void getStatus_whenStatusExists_shouldReturnStatus() {
    RouteConfigValue enabledValue = new RouteConfigValue();
    enabledValue.setStatus(1);
    assertEquals(1, enabledValue.getStatus());

    RouteConfigValue disabledValue = new RouteConfigValue();
    disabledValue.setStatus(0);
    assertEquals(0, disabledValue.getStatus());
  }

  @Test
  void getStatus_whenStatusNotSet_shouldReturnNull() {
    RouteConfigValue value = new RouteConfigValue();
    assertNull(value.getStatus());
  }

  @Test
  void toApiMap_whenFieldsSet_shouldContainAllFields() {
    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/test");
    value.setMethods(List.of("GET"));
    value.setUpstreamId("backend");

    Map<String, Object> map = value.toApiMap();
    assertEquals("/test", map.get("uri"));
    assertEquals(List.of("GET"), map.get("methods"));
    assertEquals("backend", map.get("upstream_id"));
  }

  @Test
  void toApiMap_whenPluginsSet_shouldIncludePlugins() {
    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/test");
    RoutePlugins plugins = new RoutePlugins();
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/rewritten");
    plugins.setProxyRewrite(proxyRewrite);
    value.setPlugins(plugins);

    Map<String, Object> map = value.toApiMap();
    assertNotNull(map.get("plugins"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    RouteConfigValue value1 = new RouteConfigValue();
    value1.setUri("/test");
    RouteConfigValue value2 = new RouteConfigValue();
    value2.setUri("/test");

    assertEquals(value1, value2);
    assertEquals(value1.hashCode(), value2.hashCode());
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    RouteConfigValue value1 = new RouteConfigValue();
    value1.setUri("/test1");
    RouteConfigValue value2 = new RouteConfigValue();
    value2.setUri("/test2");

    assertNotEquals(value1, value2);
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/test");
    String str = value.toString();
    assertTrue(str.contains("RouteConfigValue"));
    assertTrue(str.contains("uri"));
    assertTrue(str.contains("/test"));
  }

  @Test
  void jsonSerialization_whenValidData_shouldProduceValidJson() throws Exception {
    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/api/v1/users/*");
    value.setMethods(List.of("GET", "POST"));
    value.setUpstreamId("backend-users");
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of());
    value.setPlugins(plugins);

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
    assertNotNull(value.getPlugins());
    assertTrue(value.getPlugins().hasPlugin("prometheus"));
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
                      "headers": {
                        "set": {"X-Custom-Header": "value"}
                      }
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

    // Verify typed plugins
    assertNotNull(value.getPlugins());
    assertNotNull(value.getPlugins().getServerlessPreFunction());
    assertNotNull(value.getPlugins().getServerlessPostFunction());
    assertNotNull(value.getPlugins().getResponseRewrite());
    assertNotNull(value.getPlugins().getProxyRewrite());

    // Verify untyped plugins in additionalPlugins
    assertTrue(value.getPlugins().hasPlugin("openid-connect"));
    assertTrue(value.getPlugins().hasPlugin("prometheus"));
    assertTrue(value.getPlugins().hasPlugin("loki"));

    // Verify plugin configuration
    Map<String, Object> openidConnect = value.getPlugins().getAdditionalPlugin("openid-connect");
    assertNotNull(openidConnect);
    assertEquals("api-gateway", openidConnect.get("client_id"));
  }

  // ─── plugin_config_id tests ──────────────────────────────────────────────────

  @Test
  void pluginConfigId_whenSetAsInteger_shouldReturnInteger() {
    RouteConfigValue value = new RouteConfigValue();
    value.setPluginConfigId(1);
    assertEquals(1, value.getPluginConfigId());
  }

  @Test
  void pluginConfigId_whenSetAsString_shouldReturnString() {
    RouteConfigValue value = new RouteConfigValue();
    value.setPluginConfigId("auth-plugins");
    assertEquals("auth-plugins", value.getPluginConfigId());
  }

  @Test
  void toApiMap_whenPluginConfigIdSet_shouldContainField() {
    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/api/dataspace/ds-001/*");
    value.setServiceId("svc-frost-server");
    value.setPluginConfigId(1);

    Map<String, Object> map = value.toApiMap();
    assertEquals(1, map.get("plugin_config_id"));
    assertEquals("svc-frost-server", map.get("service_id"));
  }

  @Test
  void toApiMap_whenPluginConfigIdNull_shouldNotContainField() {
    RouteConfigValue value = new RouteConfigValue();
    value.setUri("/api/dataspace/ds-001/*");
    value.setServiceId("svc-frost-server");

    Map<String, Object> map = value.toApiMap();
    assertFalse(map.containsKey("plugin_config_id"));
    assertEquals("svc-frost-server", map.get("service_id"));
  }

  @Test
  void jsonDeserialization_protectedRoute_shouldParsePluginConfigId() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-route",
                  "uri": "/api/dataspace/ds-001/*",
                  "service_id": "svc-frost-server",
                  "plugin_config_id": 1
                }
                """;

    RouteConfigValue value = objectMapper.readValue(json, RouteConfigValue.class);
    assertEquals("/api/dataspace/ds-001/*", value.getUri());
    assertEquals("svc-frost-server", value.getServiceId());
    assertEquals(1, value.getPluginConfigId());
    assertNull(value.getPlugins());
    assertNull(value.getPriority());
  }

  @Test
  void jsonDeserialization_publicRoute_shouldHaveNullPluginConfigId() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-route",
                  "uri": "/api/dataspace/ds-002/*",
                  "service_id": "svc-frost-server",
                  "priority": 1
                }
                """;

    RouteConfigValue value = objectMapper.readValue(json, RouteConfigValue.class);
    assertEquals("/api/dataspace/ds-002/*", value.getUri());
    assertEquals("svc-frost-server", value.getServiceId());
    assertNull(value.getPluginConfigId());
    assertEquals(1, value.getPriority());
  }

  @Test
  void toApiMap_protectedToPublicUpdate_shouldOmitPluginConfigId() {
    // Simulate UPDATE_ROUTE: openDataAccess changed from false to true
    // The route should lose plugin_config_id and gain priority
    RouteConfigValue publicRoute = new RouteConfigValue();
    publicRoute.setUri("/api/dataspace/ds-001/*");
    publicRoute.setServiceId("svc-frost-server");
    publicRoute.setPriority(1);
    // pluginConfigId deliberately NOT set (null) -> public

    Map<String, Object> map = publicRoute.toApiMap();
    assertFalse(map.containsKey("plugin_config_id"));
    assertEquals(1, map.get("priority"));
    assertEquals("svc-frost-server", map.get("service_id"));
  }

  @Test
  void toApiMap_publicToProtectedUpdate_shouldIncludePluginConfigId() {
    // Simulate UPDATE_ROUTE: openDataAccess changed from true to false
    // The route should gain plugin_config_id and lose priority
    RouteConfigValue protectedRoute = new RouteConfigValue();
    protectedRoute.setUri("/api/dataspace/ds-001/*");
    protectedRoute.setServiceId("svc-frost-server");
    protectedRoute.setPluginConfigId(1);
    // priority deliberately NOT set (null) -> protected, default priority

    Map<String, Object> map = protectedRoute.toApiMap();
    assertEquals(1, map.get("plugin_config_id"));
    assertFalse(map.containsKey("priority"));
    assertEquals("svc-frost-server", map.get("service_id"));
  }

  @Test
  void equals_whenDifferentPluginConfigId_shouldReturnFalse() {
    RouteConfigValue protectedRoute = new RouteConfigValue();
    protectedRoute.setUri("/test");
    protectedRoute.setPluginConfigId(1);

    RouteConfigValue publicRoute = new RouteConfigValue();
    publicRoute.setUri("/test");

    assertNotEquals(protectedRoute, publicRoute);
  }
}
