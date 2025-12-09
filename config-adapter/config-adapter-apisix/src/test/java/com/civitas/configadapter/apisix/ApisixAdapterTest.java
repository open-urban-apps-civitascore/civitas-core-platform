/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    when(mockConfig.getProperty("apisix.admin.url")).thenReturn("http://custom-apisix:9180");

    adapter.initialize(mockConfig);

    assertNotNull(adapter.getSubscribedTopics());
    assertEquals(1, adapter.getSubscribedTopics().size());
  }

  @Test
  void testCloseAdapter() {
    when(mockConfig.getProperty("apisix.topics")).thenReturn("core.civitas.api.backend.created");

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
      when(mockConfig.getProperty("apisix.admin.key", "edd1c9f034335f136f87ad84b625c8f1"))
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
      ConfigEvent event = createConfigEvent(Operation.CREATE, "routes/test-route", config);

      adapter.processConfigEvent("core.civitas.api.backend.created", event);

      verify(mockPublisher).publish(eq("test-result-topic"), any(ConfigResultEvent.class));
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
}
