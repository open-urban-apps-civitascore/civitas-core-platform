/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.apisix.ApisixConfigValue;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for ApisixAdapter exception handling. Tests verify correct exception types and error
 * codes for various error scenarios.
 */
class ApisixAdapterExceptionTest {

  private ApisixAdapter adapter;
  private AdapterConfig mockConfig;
  private MockWebServer server;

  @BeforeEach
  void setUp() throws IOException {
    adapter = new ApisixAdapter();
    mockConfig = mock(AdapterConfig.class);
    server = new MockWebServer();
    server.start();

    when(mockConfig.getProperty("apisix.topics")).thenReturn("de.civitascore.api.backend.created");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn(server.url("/").toString());
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-api-key");

    adapter.initialize(mockConfig);
    adapter.setEventPublisher(mock(EventPublisher.class));
  }

  @AfterEach
  void tearDownServer() throws IOException {
    server.close();
  }

  @Nested
  @DisplayName("Network Error Tests")
  class NetworkErrorTests {

    @Test
    @DisplayName("shouldThrowRetryableOnNetworkError - connection failure triggers retry")
    void shouldThrowRetryableOnNetworkError() throws IOException {
      // Torn down before the request is even made: the connection attempt itself fails.
      server.close();

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
    void shouldThrowRetryableOnNetworkErrorForUpdate() throws IOException {
      // Torn down before the request is even made: the connection attempt itself fails.
      server.close();

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
    void shouldThrowRetryableOnNetworkErrorForDelete() throws IOException {
      // Torn down before the request is even made: the connection attempt itself fails.
      server.close();

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
      server.enqueue(new MockResponse.Builder().code(503).body("Service Unavailable").build());

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
      server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

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
      server.enqueue(new MockResponse.Builder().code(502).body("Bad Gateway").build());

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
      server.enqueue(new MockResponse.Builder().code(400).body("Invalid configuration").build());

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
      server.enqueue(new MockResponse.Builder().code(404).body("Route not found").build());

      ConfigEvent event =
          createConfigEvent(Operation.UPDATE, "routes/nonexistent", Map.of("uri", "/api"));

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

      assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
      assertTrue(exception.getMessage().contains("HTTP 404"));
    }

    @Test
    @DisplayName("createUpstreamWithConflictReturnsSuccess")
    void createUpstreamWithConflictReturnsSuccess()
        throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(new MockResponse.Builder().code(409).build());

      EventPublisher publisher = mock(EventPublisher.class);
      adapter.setEventPublisher(publisher);
      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "roundrobin"));

      adapter.processConfigEvent("test-topic", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(publisher).publish(eq("test-result-topic"), captor.capture());
      assertEquals(ConfigResultEvent.Status.SUCCESS, captor.getValue().status());
    }

    @Test
    @DisplayName("deleteUpstreamNotFoundReturnsSuccess")
    void deleteUpstreamNotFoundReturnsSuccess()
        throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(new MockResponse.Builder().code(404).build());

      EventPublisher publisher = mock(EventPublisher.class);
      adapter.setEventPublisher(publisher);
      ConfigEvent event = createConfigEvent(Operation.DELETE, "upstreams/gone-id", null);

      adapter.processConfigEvent("test-topic", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(publisher).publish(eq("test-result-topic"), captor.capture());
      assertEquals(ConfigResultEvent.Status.SUCCESS, captor.getValue().status());
    }

    @Test
    @DisplayName("deleteRouteNotFoundReturnsSuccess")
    void deleteRouteNotFoundReturnsSuccess()
        throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(new MockResponse.Builder().code(404).build());

      EventPublisher publisher = mock(EventPublisher.class);
      adapter.setEventPublisher(publisher);
      ConfigEvent event = createConfigEvent(Operation.DELETE, "routes/gone-id", null);

      adapter.processConfigEvent("test-topic", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(publisher).publish(eq("test-result-topic"), captor.capture());
      assertEquals(ConfigResultEvent.Status.SUCCESS, captor.getValue().status());
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
    void shouldThrowRetryableOnRouteNetworkError() throws IOException {
      // Torn down before the request is even made: the connection attempt itself fails.
      server.close();

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
      server.enqueue(new MockResponse.Builder().code(400).body("Invalid route config").build());

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
    void retryableExceptionShouldBeRetryable() throws IOException {
      // Torn down before the request is even made: the connection attempt itself fails.
      server.close();

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
      server.enqueue(new MockResponse.Builder().code(400).body("Bad Request").build());

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "upstreams", Map.of("type", "invalid"));

      Exception exception =
          assertThrows(Exception.class, () -> adapter.processConfigEvent("test-topic", event));

      assertInstanceOf(FatalAdapterException.class, exception);
      assertTrue(!((FatalAdapterException) exception).isRetryable());
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
  private abstract static class NoTypeInfoMixin {}

  private static final ObjectMapper MAPPER =
      new ObjectMapper().addMixIn(ConfigValue.class, NoTypeInfoMixin.class);

  private ConfigEvent createConfigEvent(
      Operation operation, String targetResource, Map<String, Object> configMap) {
    Metadata metadata =
        new Metadata(
            "msg-123",
            OffsetDateTime.now(),
            "test-source",
            "corr-123",
            "v1.0.0",
            "test-result-topic");

    ApisixConfigValue apisixValue =
        configMap != null ? MAPPER.convertValue(configMap, ApisixConfigValue.class) : null;
    Payload payload =
        new Payload("apisix", targetResource, operation, new Config(null, apisixValue));
    return new ConfigEvent(metadata, payload);
  }
}
