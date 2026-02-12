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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ApisixAdapter exception handling. Tests verify correct exception types and error
 * codes for various error scenarios.
 */
class ApisixAdapterExceptionTest {

  private ApisixAdapter adapter;
  private AdapterConfig mockConfig;
  private Client mockClient;
  private WebTarget mockTarget;
  private WebTarget mockPathTarget;
  private Invocation.Builder mockBuilder;
  private Response mockResponse;

  @BeforeEach
  void setUp() {
    adapter = new ApisixAdapter();
    mockConfig = mock(AdapterConfig.class);

    when(mockConfig.getProperty("apisix.topics")).thenReturn("de.civitascore.api.backend.created");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn("http://localhost:9180");
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-api-key");

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
    adapter.setEventPublisher(mock(EventPublisher.class));
  }

  @Nested
  @DisplayName("Network Error Tests")
  class NetworkErrorTests {

    @Test
    @DisplayName("shouldThrowRetryableOnNetworkError - ProcessingException triggers retry")
    void shouldThrowRetryableOnNetworkError() {
      when(mockBuilder.post(any(Entity.class)))
          .thenThrow(new ProcessingException("Connection refused"));

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "roundrobin"));

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
      assertTrue(exception.isRetryable());
    }

    @Test
    @DisplayName("shouldThrowRetryableOnNetworkErrorForUpdate")
    void shouldThrowRetryableOnNetworkErrorForUpdate() {
      when(mockBuilder.put(any(Entity.class)))
          .thenThrow(new ProcessingException("Connection timeout"));

      ConfigEvent event =
          createConfigEvent(Operation.UPDATE, "upstreams/test-id", Map.of("type", "roundrobin"));

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("shouldThrowRetryableOnNetworkErrorForDelete")
    void shouldThrowRetryableOnNetworkErrorForDelete() {
      when(mockBuilder.delete()).thenThrow(new ProcessingException("Network unreachable"));

      ConfigEvent event = createConfigEvent(Operation.DELETE, "upstreams/test-id", null);

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
    }
  }

  @Nested
  @DisplayName("HTTP 5xx Error Tests")
  class ServerErrorTests {

    @Test
    @DisplayName("shouldThrowRetryableOn5xxResponse - HTTP 503 triggers retry")
    void shouldThrowRetryableOn5xxResponse() {
      when(mockResponse.getStatus()).thenReturn(503);
      when(mockResponse.readEntity(String.class)).thenReturn("Service Unavailable");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "roundrobin"));

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
      assertTrue(exception.isRetryable());
    }

    @Test
    @DisplayName("shouldThrowRetryableOn500Response")
    void shouldThrowRetryableOn500Response() {
      when(mockResponse.getStatus()).thenReturn(500);
      when(mockResponse.readEntity(String.class)).thenReturn("Internal Server Error");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "roundrobin"));

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    @DisplayName("shouldThrowRetryableOn502Response")
    void shouldThrowRetryableOn502Response() {
      when(mockResponse.getStatus()).thenReturn(502);
      when(mockResponse.readEntity(String.class)).thenReturn("Bad Gateway");
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(Operation.UPDATE, "routes/test-id", Map.of("uri", "/api"));

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
    }
  }

  @Nested
  @DisplayName("HTTP 4xx Error Tests")
  class ClientErrorTests {

    @Test
    @DisplayName("shouldThrowFatalOn4xxResponse - HTTP 400 goes to DLQ")
    void shouldThrowFatalOn4xxResponse() {
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class)).thenReturn("Invalid configuration");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "invalid"));

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.APISIX_UPSTREAM_ERROR, exception.getErrorCode());
      assertTrue(exception.getMessage().contains("HTTP 400"));
    }

    @Test
    @DisplayName("shouldThrowFatalOn404Response")
    void shouldThrowFatalOn404Response() {
      when(mockResponse.getStatus()).thenReturn(404);
      when(mockResponse.readEntity(String.class)).thenReturn("Route not found");
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(Operation.UPDATE, "routes/nonexistent", Map.of("uri", "/api"));

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
      assertTrue(exception.getMessage().contains("HTTP 404"));
    }

    @Test
    @DisplayName("shouldThrowFatalOn409ConflictResponse")
    void shouldThrowFatalOn409ConflictResponse() {
      when(mockResponse.getStatus()).thenReturn(409);
      when(mockResponse.readEntity(String.class)).thenReturn("Resource already exists");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "roundrobin"));

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.APISIX_UPSTREAM_ERROR, exception.getErrorCode());
      assertTrue(exception.getMessage().contains("HTTP 409"));
    }
  }

  @Nested
  @DisplayName("Invalid Resource Type Tests")
  class InvalidResourceTypeTests {

    @Test
    @DisplayName("shouldThrowFatalOnUnknownResourceType")
    void shouldThrowFatalOnUnknownResourceType() {
      ConfigEvent event = createConfigEvent(Operation.CREATE, "services/test", Map.of());

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.INVALID_RESOURCE_TYPE, exception.getErrorCode());
    }
  }

  @Nested
  @DisplayName("Route Operation Exception Tests")
  class RouteOperationExceptionTests {

    @Test
    @DisplayName("shouldThrowRetryableOnRouteNetworkError")
    void shouldThrowRetryableOnRouteNetworkError() {
      when(mockBuilder.post(any(Entity.class)))
          .thenThrow(new ProcessingException("Connection refused"));

      ConfigEvent event = createConfigEvent(Operation.CREATE, "routes", Map.of("uri", "/api"));

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("shouldThrowFatalOnRouteClientError")
    void shouldThrowFatalOnRouteClientError() {
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class)).thenReturn("Invalid route config");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "routes", Map.of("uri", "invalid"));

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
    }
  }

  @Nested
  @DisplayName("Exception Type Verification")
  class ExceptionTypeVerificationTests {

    @Test
    @DisplayName("retryableExceptionShouldBeRetryable")
    void retryableExceptionShouldBeRetryable() {
      when(mockBuilder.post(any(Entity.class))).thenThrow(new ProcessingException("Network error"));

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "roundrobin"));

      Exception exception =
          assertThrows(Exception.class, () -> adapter.processConfigEvent("test-topic", event));

      assertInstanceOf(RetryableAdapterException.class, exception);
      assertTrue(((RetryableAdapterException) exception).isRetryable());
    }

    @Test
    @DisplayName("fatalExceptionShouldNotBeRetryable")
    void fatalExceptionShouldNotBeRetryable() {
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class)).thenReturn("Bad Request");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "invalid"));

      Exception exception =
          assertThrows(Exception.class, () -> adapter.processConfigEvent("test-topic", event));

      assertInstanceOf(FatalAdapterException.class, exception);
      assertTrue(!((FatalAdapterException) exception).isRetryable());
    }
  }

  private ConfigEvent createConfigEvent(
      Operation operation, String targetResource, Map<String, Object> configValue) {
    return createConfigEventWithOperation(operation, targetResource, configValue);
  }

  private ConfigEvent createConfigEventWithOperation(
      Operation operation, String targetResource, Map<String, Object> configValue) {
    Metadata metadata =
        new Metadata(
            "msg-123",
            OffsetDateTime.now(),
            "test-source",
            "corr-123",
            "v1.0.0",
            "test-result-topic");

    ApisixConfigValue apisixValue = new ApisixConfigValue(configValue);
    Payload payload =
        new Payload("apisix", targetResource, operation, new Config(null, apisixValue));
    return new ConfigEvent(metadata, payload);
  }
}
