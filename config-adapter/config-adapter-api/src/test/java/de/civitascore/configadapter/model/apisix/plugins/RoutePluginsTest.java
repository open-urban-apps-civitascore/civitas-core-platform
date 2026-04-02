/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.apisix.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RoutePlugins}. */
class RoutePluginsTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void hasPlugin_whenProxyRewriteSet_shouldReturnTrue() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(new ProxyRewritePlugin());
    assertTrue(plugins.hasPlugin("proxy-rewrite"));
  }

  @Test
  void hasPlugin_whenProxyRewriteNull_shouldReturnFalse() {
    RoutePlugins plugins = new RoutePlugins();
    assertFalse(plugins.hasPlugin("proxy-rewrite"));
  }

  @Test
  void hasPlugin_whenResponseRewriteSet_shouldReturnTrue() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(new ResponseRewritePlugin());
    assertTrue(plugins.hasPlugin("response-rewrite"));
  }

  @Test
  void hasPlugin_whenResponseRewriteNull_shouldReturnFalse() {
    RoutePlugins plugins = new RoutePlugins();
    assertFalse(plugins.hasPlugin("response-rewrite"));
  }

  @Test
  void hasPlugin_whenServerlessPreFunctionSet_shouldReturnTrue() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPreFunction(new ServerlessFunctionPlugin());
    assertTrue(plugins.hasPlugin("serverless-pre-function"));
  }

  @Test
  void hasPlugin_whenServerlessPostFunctionSet_shouldReturnTrue() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPostFunction(new ServerlessFunctionPlugin());
    assertTrue(plugins.hasPlugin("serverless-post-function"));
  }

  @Test
  void hasPlugin_whenAdditionalPluginPresent_shouldReturnTrue() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of());
    assertTrue(plugins.hasPlugin("prometheus"));
  }

  @Test
  void hasPlugin_whenAdditionalPluginAbsent_shouldReturnFalse() {
    RoutePlugins plugins = new RoutePlugins();
    assertFalse(plugins.hasPlugin("prometheus"));
  }

  @Test
  void hasPlugin_whenUnknownName_shouldReturnFalse() {
    RoutePlugins plugins = new RoutePlugins();
    assertFalse(plugins.hasPlugin("nonexistent-plugin"));
  }

  @Test
  void toApiMap_withTypedAndUntypedPlugins_shouldContainAll() {
    RoutePlugins plugins = new RoutePlugins();

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/rewritten");
    plugins.setProxyRewrite(proxyRewrite);

    plugins.handleUnknownPlugin("prometheus", Map.of());
    plugins.handleUnknownPlugin("loki", Map.of("endpoint", "http://loki:3100"));

    Map<String, Object> map = plugins.toApiMap();
    assertNotNull(map.get("proxy-rewrite"));
    assertNotNull(map.get("prometheus"));
    assertNotNull(map.get("loki"));
  }

  @Test
  void toApiMap_shouldReturnUnmodifiableMap() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(new ProxyRewritePlugin());

    Map<String, Object> map = plugins.toApiMap();
    assertThrows(UnsupportedOperationException.class, () -> map.put("new-key", "value"));
  }

  @Test
  void toApiMap_additionalPluginsShouldNotOverrideTypedPlugins() {
    RoutePlugins plugins = new RoutePlugins();

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/typed");
    plugins.setProxyRewrite(proxyRewrite);

    plugins.handleUnknownPlugin("proxy-rewrite", Map.of("uri", "/untyped"));

    Map<String, Object> map = plugins.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> proxyRewriteMap = (Map<String, Object>) map.get("proxy-rewrite");
    assertEquals("/typed", proxyRewriteMap.get("uri"));
  }

  @Test
  void getAdditionalPlugin_whenPluginExists_shouldReturnMap() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of("enable", true));

    Map<String, Object> result = plugins.getAdditionalPlugin("prometheus");
    assertNotNull(result);
    assertEquals(true, result.get("enable"));
  }

  @Test
  void getAdditionalPlugin_whenPluginAbsent_shouldReturnNull() {
    RoutePlugins plugins = new RoutePlugins();
    assertNull(plugins.getAdditionalPlugin("nonexistent"));
  }

  @Test
  void getAdditionalPlugin_whenValueIsNotMap_shouldReturnNull() {
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("custom-plugin", "a-string-value");
    assertTrue(plugins.hasPlugin("custom-plugin"));
    assertNull(plugins.getAdditionalPlugin("custom-plugin"));
  }

  @Test
  void jsonDeserialization_withMixedPlugins_shouldParseCorrectly() throws Exception {
    String json =
        """
        {
          "proxy-rewrite": {
            "uri": "/users"
          },
          "response-rewrite": {
            "status_code": 200
          },
          "serverless-pre-function": {
            "phase": "rewrite",
            "functions": ["return function() end"]
          },
          "serverless-post-function": {
            "phase": "log",
            "functions": ["return function() end"]
          },
          "prometheus": {},
          "openid-connect": {
            "client_id": "my-client"
          }
        }
        """;

    RoutePlugins plugins = objectMapper.readValue(json, RoutePlugins.class);

    assertNotNull(plugins.getProxyRewrite());
    assertEquals("/users", plugins.getProxyRewrite().getUri());
    assertNotNull(plugins.getResponseRewrite());
    assertEquals(200, plugins.getResponseRewrite().getStatusCode());
    assertNotNull(plugins.getServerlessPreFunction());
    assertNotNull(plugins.getServerlessPostFunction());
    assertTrue(plugins.hasPlugin("prometheus"));
    assertTrue(plugins.hasPlugin("openid-connect"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    RoutePlugins plugins1 = new RoutePlugins();
    plugins1.setProxyRewrite(new ProxyRewritePlugin());
    RoutePlugins plugins2 = new RoutePlugins();
    plugins2.setProxyRewrite(new ProxyRewritePlugin());

    assertEquals(plugins1, plugins2);
    assertEquals(plugins1.hashCode(), plugins2.hashCode());
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    RoutePlugins plugins1 = new RoutePlugins();
    ProxyRewritePlugin p1 = new ProxyRewritePlugin();
    p1.setUri("/a");
    plugins1.setProxyRewrite(p1);

    RoutePlugins plugins2 = new RoutePlugins();
    ProxyRewritePlugin p2 = new ProxyRewritePlugin();
    p2.setUri("/b");
    plugins2.setProxyRewrite(p2);

    assertNotEquals(plugins1, plugins2);
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    RoutePlugins plugins = new RoutePlugins();
    assertEquals(plugins, plugins);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    RoutePlugins plugins = new RoutePlugins();
    assertNotEquals(null, plugins);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    RoutePlugins plugins = new RoutePlugins();
    assertNotEquals("string", plugins);
  }

  @Test
  void toString_shouldContainClassName() {
    RoutePlugins plugins = new RoutePlugins();
    assertTrue(plugins.toString().contains("RoutePlugins"));
  }
}
