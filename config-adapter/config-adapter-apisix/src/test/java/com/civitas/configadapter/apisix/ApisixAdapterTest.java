/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.apisix.RouteConfigValue;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Unit tests for ApisixAdapter */
class ApisixAdapterTest {

  private ApisixAdapter adapter;
  private AdapterConfig mockConfig;

  @BeforeEach
  void setUp() {
    adapter = new ApisixAdapter();
    mockConfig = mock(AdapterConfig.class);
  }

  @Test
  void testAdapterName() {
    assertEquals("apisix", adapter.getName());
  }

  @Test
  void testInitializationWithDefaultValues() {
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("edd1c9f034335f136f87ad84b625c8f1");
    when(mockConfig.getProperty("apisix.topics"))
        .thenReturn(
            "core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted");

    adapter.initialize(mockConfig);

    List<String> subscribedTopics = adapter.getSubscribedTopics();
    assertNotNull(subscribedTopics);
    assertEquals(3, subscribedTopics.size());
    assertTrue(subscribedTopics.contains("core.civitas.api.backend.created"));
    assertTrue(subscribedTopics.contains("core.civitas.api.backend.updated"));
    assertTrue(subscribedTopics.contains("core.civitas.api.backend.deleted"));
  }

  @Test
  void testInitializationWithCustomAdminUrl() {
    when(mockConfig.getProperty("apisix.topics")).thenReturn("core.civitas.api.backend.created");
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("edd1c9f034335f136f87ad84b625c8f1");
    when(mockConfig.getProperty("apisix.admin.url")).thenReturn("http://custom-apisix:9180");

    adapter.initialize(mockConfig);

    assertNotNull(adapter.getSubscribedTopics());
    assertEquals(1, adapter.getSubscribedTopics().size());
  }

  @Test
  void testInitializationWithOutAdminKey() {
    when(mockConfig.getProperty("apisix.topics")).thenReturn("core.civitas.api.backend.created");

    try {
      adapter.initialize(mockConfig);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException e) {
      assertEquals("The APISIX admin key cannot be null or blank.", e.getMessage());
    }

    assertNotNull(adapter.getSubscribedTopics());
    assertEquals(1, adapter.getSubscribedTopics().size());
  }

  @Test
  void testCloseAdapter() {
    when(mockConfig.getProperty("apisix.topics")).thenReturn("core.civitas.api.backend.created");
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("edd1c9f034335f136f87ad84b625c8f1");

    adapter.initialize(mockConfig);
    adapter.close();
  }

  @Nested
  class ProcessEvent {

    private EventPublisher mockPublisher;
    private Client mockClient;
    private WebTarget mockTarget;
    private WebTarget mockPathTarget;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpProcessEventTests() {
      when(mockConfig.getProperty("apisix.topics"))
          .thenReturn(
              "core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted");
      when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
          .thenReturn("http://localhost:9180");
      when(mockConfig.getProperty("apisix.admin.key"))
          .thenReturn("edd1c9f034335f136f87ad84b625c8f1");

      // Mock the JAX-RS Client fluent API chain
      mockClient = mock(Client.class);
      mockTarget = mock(WebTarget.class);
      mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.resolveTemplate(any(String.class), any())).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void testCreateSuccess() {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"success\":true}");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> upstreamConfig =
          Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1, "backend2:8080", 1));

      ConfigEvent event = createConfigEvent(Operation.CREATE, "upstreams", upstreamConfig);

      adapter.processConfigEvent("core.civitas.api.backend.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("APISIX upstream created successfully", result.message());
    }

    @Test
    void testCreateFailureHttpError() {
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class))
          .thenReturn("{\"error\":\"Invalid configuration\"}");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> upstreamConfig = Map.of("type", "invalid");

      ConfigEvent event = createConfigEvent(Operation.CREATE, "upstreams", upstreamConfig);

      adapter.processConfigEvent("core.civitas.api.backend.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
      assertEquals("UPSTREAM_CREATE_FAILED", result.errorCode());
    }

    @Test
    void testCreateFailureException() {
      when(mockBuilder.post(any(Entity.class)))
          .thenThrow(new ProcessingException("Connection refused"));

      Map<String, Object> upstreamConfig = Map.of("type", "roundrobin");

      ConfigEvent event = createConfigEvent(Operation.CREATE, "upstreams", upstreamConfig);

      adapter.processConfigEvent("core.civitas.api.backend.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
      assertEquals("UPSTREAM_CREATE_FAILED", result.errorCode());
    }

    @Test
    void testUpdateSuccess() {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"success\":true}");
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> upstreamConfig =
          Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 2, "backend2:8080", 1));

      ConfigEvent event =
          createConfigEvent(Operation.UPDATE, "upstreams/test-upstream-id", upstreamConfig);

      adapter.processConfigEvent("core.civitas.api.backend.updated", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("APISIX upstream updated successfully", result.message());
    }

    @Test
    void testDeleteSuccess() {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"success\":true}");
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.DELETE, "upstreams/test-upstream-id", null);

      adapter.processConfigEvent("core.civitas.api.backend.deleted", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("APISIX upstream deleted successfully", result.message());
    }

    @Test
    void testUnknownResourceType() {
      Map<String, Object> config = Map.of("key", "value");
      ConfigEvent event = createConfigEvent(Operation.CREATE, "services/test-service", config);

      adapter.processConfigEvent("core.civitas.api.backend.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
      assertEquals("UNKNOWN_RESOURCE_TYPE", result.errorCode());
    }

    // ============== ROUTE OPERATION TESTS ==============

    @Test
    void testRouteCreateSuccess() {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"key\":\"routes/1\"}");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> routeConfig =
          Map.of(
              "uri",
              "/api/v1/users/*",
              "methods",
              List.of("GET", "POST"),
              "upstream_id",
              "backend-users",
              "plugins",
              Map.of("prometheus", Map.of(), "openid-connect", Map.of("client_id", "api-gateway")));

      ConfigEvent event = createRouteConfigEvent(Operation.CREATE, "routes", routeConfig);

      adapter.processConfigEvent("core.civitas.api.route.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("APISIX route created successfully", result.message());
    }

    @Test
    void testRouteCreateFailure() {
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class))
          .thenReturn("{\"error\":\"Invalid route config\"}");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> routeConfig = Map.of("uri", "/api/v1/invalid");

      ConfigEvent event = createRouteConfigEvent(Operation.CREATE, "routes", routeConfig);

      adapter.processConfigEvent("core.civitas.api.route.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
      assertEquals("ROUTE_CREATE_FAILED", result.errorCode());
    }

    @Test
    void testRouteUpdateSuccess() {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"key\":\"routes/test-route-id\"}");
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> routeConfig =
          Map.of(
              "uri",
              "/api/v1/users/*",
              "methods",
              List.of("GET", "POST", "PUT", "DELETE"),
              "upstream_id",
              "backend-users-updated",
              "plugins",
              Map.of("prometheus", Map.of(), "proxy-rewrite", Map.of("uri", "/users")));

      ConfigEvent event =
          createRouteConfigEvent(Operation.UPDATE, "routes/test-route-id", routeConfig);

      adapter.processConfigEvent("core.civitas.api.route.updated", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("APISIX route updated successfully", result.message());
      assertEquals("test-route-id", result.resourceId());
    }

    @Test
    void testRouteUpdateFailure() {
      when(mockResponse.getStatus()).thenReturn(404);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"error\":\"Route not found\"}");
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> routeConfig = Map.of("uri", "/api/v1/users/*");

      ConfigEvent event =
          createRouteConfigEvent(Operation.UPDATE, "routes/nonexistent-route", routeConfig);

      adapter.processConfigEvent("core.civitas.api.route.updated", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
      assertEquals("ROUTE_UPDATE_FAILED", result.errorCode());
    }

    @Test
    void testRouteDeleteSuccess() {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockResponse.readEntity(String.class))
          .thenReturn("{\"deleted\":\"routes/test-route-id\"}");
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createRouteConfigEvent(Operation.DELETE, "routes/test-route-id", null);

      adapter.processConfigEvent("core.civitas.api.route.deleted", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("APISIX route deleted successfully", result.message());
      assertEquals("test-route-id", result.resourceId());
    }

    @Test
    void testRouteDeleteFailure() {
      when(mockResponse.getStatus()).thenReturn(404);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"error\":\"Route not found\"}");
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event =
          createRouteConfigEvent(Operation.DELETE, "routes/nonexistent-route", null);

      adapter.processConfigEvent("core.civitas.api.route.deleted", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
      assertEquals("ROUTE_DELETE_FAILED", result.errorCode());
    }

    @Test
    void testRouteWithFullPluginConfiguration() {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"key\":\"routes/1\"}");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      // Create a route with all CIVITAS/CORE V1 plugins
      Map<String, Object> routeConfig =
          Map.of(
              "uri",
              "/api/v1/data/*",
              "methods",
              List.of("GET", "POST", "PUT", "DELETE"),
              "upstream_id",
              "data-service",
              "plugins",
              Map.of(
                  "openid-connect",
                  Map.of(
                      "discovery", "https://keycloak.example.com/.well-known/openid-configuration",
                      "client_id", "api-gateway",
                      "client_secret", "secret"),
                  "serverless-pre-function",
                  Map.of("phase", "rewrite", "functions", List.of("return function() end")),
                  "serverless-post-function",
                  Map.of("phase", "log", "functions", List.of("return function() end")),
                  "response-rewrite",
                  Map.of("headers", Map.of("X-Custom-Header", "value")),
                  "proxy-rewrite",
                  Map.of("regex_uri", List.of("/api/v1/(.*)", "/$1")),
                  "prometheus",
                  Map.of(),
                  "loki",
                  Map.of("endpoint", "http://loki:3100")));

      ConfigEvent event = createRouteConfigEvent(Operation.CREATE, "routes", routeConfig);

      adapter.processConfigEvent("core.civitas.api.route.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    }

    @Test
    void testRouteCreateWithConnectionException() {
      when(mockBuilder.post(any(Entity.class)))
          .thenThrow(new ProcessingException("Connection refused"));

      Map<String, Object> routeConfig = Map.of("uri", "/api/v1/test");

      ConfigEvent event = createRouteConfigEvent(Operation.CREATE, "routes", routeConfig);

      adapter.processConfigEvent("core.civitas.api.route.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
      assertEquals("ROUTE_CREATE_FAILED", result.errorCode());
      assertTrue(result.message().contains("Connection refused"));
    }

    @Test
    void testRouteWithApisixConfigValue() {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"key\":\"routes/1\"}");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      // Test that routes also work with ApisixConfigValue (backward compatibility)
      Map<String, Object> routeConfig =
          Map.of("uri", "/api/v1/legacy/*", "upstream_id", "legacy-backend");

      ConfigEvent event = createConfigEvent(Operation.CREATE, "routes", routeConfig);

      adapter.processConfigEvent("core.civitas.api.route.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("APISIX route created successfully", result.message());
    }

    @Test
    void testWithoutEventPublisher() {
      adapter.setEventPublisher(null);

      Map<String, Object> upstreamConfig = Map.of("type", "roundrobin");
      ConfigEvent event = createConfigEvent(Operation.CREATE, "upstreams", upstreamConfig);

      adapter.processConfigEvent("core.civitas.api.backend.created", event);
    }

    @Test
    void testWithNullResultTopic() {
      Map<String, Object> upstreamConfig = Map.of("type", "roundrobin");

      Metadata metadata =
          new Metadata("msg-123", OffsetDateTime.now(), "test-source", "corr-123", "v1.0.0", null);
      Payload payload =
          new Payload(
              "apisix",
              "upstreams",
              Operation.CREATE,
              new Config(null, new ApisixConfigValue(upstreamConfig)));
      ConfigEvent event = new ConfigEvent(metadata, payload);

      adapter.processConfigEvent("core.civitas.api.backend.created", event);

      verify(mockPublisher, never()).publish(any(String.class), any(ConfigResultEvent.class));
    }
  }

  private ConfigEvent createConfigEvent(
      Operation operation, String targetResource, Map<String, Object> configValue) {
    Metadata metadata =
        new Metadata(
            "msg-123",
            OffsetDateTime.now(),
            "test-source",
            "corr-123",
            "v1.0.0",
            "test-result-topic");

    ApisixConfigValue apisixValue = new ApisixConfigValue((Map<String, Object>) configValue);

    Payload payload =
        new Payload("apisix", targetResource, operation, new Config(null, apisixValue));
    return new ConfigEvent(metadata, payload);
  }

  private ConfigEvent createRouteConfigEvent(
      Operation operation, String targetResource, Map<String, Object> configValue) {
    Metadata metadata =
        new Metadata(
            "msg-123",
            OffsetDateTime.now(),
            "test-source",
            "corr-123",
            "v1.0.0",
            "test-result-topic");

    RouteConfigValue routeValue = new RouteConfigValue(configValue);

    Payload payload =
        new Payload("apisix", targetResource, operation, new Config(null, routeValue));
    return new ConfigEvent(metadata, payload);
  }
}
