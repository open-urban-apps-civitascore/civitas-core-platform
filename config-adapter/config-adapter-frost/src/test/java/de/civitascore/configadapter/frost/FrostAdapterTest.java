/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.frost.FrostConfigValue;
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

class FrostAdapterTest {

  private FrostAdapter adapter;
  private AdapterConfig mockConfig;

  @BeforeEach
  void setUp() {
    adapter = new FrostAdapter();
    mockConfig = mock(AdapterConfig.class);
  }

  @Test
  void adapterNameIsFrost() {
    assertEquals("frost", adapter.getName());
  }

  @Test
  void initializationWithDefaultUrlUsesLocalhostServer() {
    when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
    when(mockConfig.getProperty("frost.topics")).thenReturn("de.civitascore.data.thing.created");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://localhost:8080/v1.1");
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

    adapter.initialize(mockConfig);

    List<String> subscribedTopics = adapter.getSubscribedTopics();
    assertNotNull(subscribedTopics);
    assertEquals(1, subscribedTopics.size());
    assertTrue(subscribedTopics.contains("de.civitascore.data.thing.created"));
  }

  @Test
  void initializationWithMultipleTopicsSubscribesToAll() {
    when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
    when(mockConfig.getProperty("frost.topics"))
        .thenReturn(
            "de.civitascore.data.thing.created,de.civitascore.data.thing.updated,de.civitascore.data.location.created");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://localhost:8080/v1.1");
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

    adapter.initialize(mockConfig);

    List<String> subscribedTopics = adapter.getSubscribedTopics();
    assertEquals(3, subscribedTopics.size());
    assertTrue(subscribedTopics.contains("de.civitascore.data.thing.created"));
    assertTrue(subscribedTopics.contains("de.civitascore.data.thing.updated"));
    assertTrue(subscribedTopics.contains("de.civitascore.data.location.created"));
  }

  @Test
  void initializationWithCustomUrlUsesProvidedUrl() {
    when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
    when(mockConfig.getProperty("frost.topics")).thenReturn("de.civitascore.data.thing.created");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://custom-server:8080/api/v1.1");
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

    adapter.initialize(mockConfig);

    assertNotNull(adapter.getSubscribedTopics());
  }

  @Test
  void initializationWithoutAnyAuthThrowsException() {
    when(mockConfig.getProperty("frost.topics")).thenReturn("de.civitascore.data.thing.created");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://localhost:8080/v1.1");
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> adapter.initialize(mockConfig));

    assertEquals(
        "The FROST adapter requires authentication: configure frost.api.key or "
            + "frost.basic.auth.username with frost.basic.auth.password.",
        exception.getMessage());
  }

  @Test
  void initializationWithBlankApiKeyAndNoBasicAuthThrowsException() {
    when(mockConfig.getProperty("frost.api.key")).thenReturn("   ");
    when(mockConfig.getProperty("frost.topics")).thenReturn("de.civitascore.data.thing.created");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://localhost:8080/v1.1");
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> adapter.initialize(mockConfig));

    assertEquals(
        "The FROST adapter requires authentication: configure frost.api.key or "
            + "frost.basic.auth.username with frost.basic.auth.password.",
        exception.getMessage());
  }

  @Test
  void initializationWithBasicAuthAndNoApiKeySucceeds() {
    when(mockConfig.getProperty("frost.topics")).thenReturn("de.civitascore.data.thing.created");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://localhost:8080/v1.1");
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");
    when(mockConfig.getProperty("frost.basic.auth.username")).thenReturn("admin");
    when(mockConfig.getProperty("frost.basic.auth.password")).thenReturn("secret");

    adapter.initialize(mockConfig);

    assertNotNull(adapter.getSubscribedTopics());
  }

  @Test
  void closeReleasesResources() {
    when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
    when(mockConfig.getProperty("frost.topics")).thenReturn("de.civitascore.data.thing.created");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://localhost:8080/v1.1");
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

    adapter.initialize(mockConfig);
    adapter.close();
  }

  @Nested
  class CreateEntityOperations {

    private EventPublisher mockPublisher;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      when(mockConfig.getProperty("frost.topics"))
          .thenReturn(
              "de.civitascore.data.thing.created,de.civitascore.data.location.created,de.civitascore.data.sensor.created");
      when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://localhost:8080/v1.1");
      when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
      when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

      adapter.setClient(mockClient());
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    private Client mockClient() {
      Client mockClient = mock(Client.class);
      WebTarget mockTarget = mock(WebTarget.class);
      WebTarget mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      return mockClient;
    }

    @Test
    void createThingWithValidConfigReturnsSuccess()
        throws RetryableAdapterException, FatalAdapterException {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/v1.1/Things(123)");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> thingConfig =
          Map.of(
              "name", "Temperature Sensor",
              "description", "A sensor measuring ambient temperature");

      ConfigEvent event = createConfigEvent(Operation.CREATE, "Things", thingConfig);

      adapter.processConfigEvent("de.civitascore.data.thing.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("FROST THING created successfully", result.message());
      assertEquals("123", result.resourceId());
    }

    @Test
    void createLocationWithGeoJsonReturnsSuccess()
        throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/v1.1/Locations(456)");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> locationConfig =
          Map.of(
              "name", "Building A",
              "description", "Main entrance",
              "encodingType", "application/geo+json",
              "location", Map.of("type", "Point", "coordinates", List.of(8.4037, 49.0069)));

      ConfigEvent event = createConfigEvent(Operation.CREATE, "Locations", locationConfig);

      adapter.processConfigEvent("de.civitascore.data.location.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("456", result.resourceId());
    }

    @Test
    void createSensorReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/v1.1/Sensors(789)");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> sensorConfig =
          Map.of(
              "name", "DHT22",
              "description", "Temperature and humidity sensor",
              "encodingType", "text/html",
              "metadata", "https://example.com/dht22");

      ConfigEvent event = createConfigEvent(Operation.CREATE, "Sensors", sensorConfig);

      adapter.processConfigEvent("de.civitascore.data.sensor.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("789", result.resourceId());
    }

    @Test
    void createWithConflictReturnsSuccess()
        throws RetryableAdapterException, FatalAdapterException {
      when(mockResponse.getStatus()).thenReturn(409);
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "Things", Map.of("name", "Existing"));

      adapter.processConfigEvent("de.civitascore.data.thing.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());
      assertEquals(ConfigResultEvent.Status.SUCCESS, captor.getValue().status());
    }

    @Test
    void createWithClientErrorThrowsFatalException() {
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"error\":\"Invalid entity\"}");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "Things", Map.of());

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.data.thing.created", event));

      assertEquals(AdapterErrorCode.FROST_ENTITY_ERROR, exception.getErrorCode());
    }

    @Test
    void createWithServerErrorThrowsRetryableException() {
      when(mockResponse.getStatus()).thenReturn(500);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"error\":\"Internal error\"}");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "Things", Map.of());

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.data.thing.created", event));

      assertEquals(AdapterErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void createWithConnectionErrorThrowsRetryableException() {
      when(mockBuilder.post(any(Entity.class)))
          .thenThrow(new ProcessingException("Connection refused"));

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "Things", Map.of("name", "Test Thing"));

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.data.thing.created", event));

      assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
    }

    @Test
    void createWithUnknownResourceTypeThrowsFatalException() {
      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "UnknownEntity", Map.of("name", "Test"));

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.data.thing.created", event));

      assertEquals(AdapterErrorCode.INVALID_RESOURCE_TYPE, exception.getErrorCode());
    }

    @Test
    void createThingWithBasicAuthSendsAuthorizationHeader()
        throws FatalAdapterException, RetryableAdapterException {
      AdapterConfig basicAuthConfig = mock(AdapterConfig.class);
      when(basicAuthConfig.getProperty("frost.topics"))
          .thenReturn("de.civitascore.data.thing.created");
      when(basicAuthConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://localhost:8080/v1.1");
      when(basicAuthConfig.getProperty("frost.api.key.header", "X-API-Key"))
          .thenReturn("X-API-Key");
      when(basicAuthConfig.getProperty("frost.basic.auth.username")).thenReturn("admin");
      when(basicAuthConfig.getProperty("frost.basic.auth.password")).thenReturn("secret");

      Invocation.Builder basicAuthBuilder = mock(Invocation.Builder.class);
      Response basicAuthResponse = mock(Response.class);
      Client basicAuthClient = mock(Client.class);
      WebTarget basicAuthTarget = mock(WebTarget.class);
      WebTarget basicAuthPathTarget = mock(WebTarget.class);

      when(basicAuthClient.target(any(String.class))).thenReturn(basicAuthTarget);
      when(basicAuthTarget.path(any(String.class))).thenReturn(basicAuthPathTarget);
      when(basicAuthPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(basicAuthBuilder);
      when(basicAuthBuilder.header(any(String.class), any())).thenReturn(basicAuthBuilder);
      when(basicAuthResponse.getStatus()).thenReturn(201);
      when(basicAuthResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/v1.1/Things(42)");
      when(basicAuthBuilder.post(any(Entity.class))).thenReturn(basicAuthResponse);

      try (FrostAdapter basicAuthAdapter = new FrostAdapter()) {
        basicAuthAdapter.setClient(basicAuthClient);
        basicAuthAdapter.initialize(basicAuthConfig);
        basicAuthAdapter.setEventPublisher(mockPublisher);

        ConfigEvent event = createConfigEvent(Operation.CREATE, "Things", Map.of("name", "Test"));
        basicAuthAdapter.processConfigEvent("de.civitascore.data.thing.created", event);

        ArgumentCaptor<String> headerNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> headerValueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(basicAuthBuilder).header(headerNameCaptor.capture(), headerValueCaptor.capture());
        assertEquals("Authorization", headerNameCaptor.getValue());
        assertTrue(headerValueCaptor.getValue().toString().startsWith("Basic "));
      }
    }
  }

  @Nested
  class UpdateEntityOperations {

    private EventPublisher mockPublisher;
    private Client mockClient;
    private WebTarget mockTarget;
    private WebTarget mockPathTarget;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      when(mockConfig.getProperty("frost.topics"))
          .thenReturn("de.civitascore.data.thing.updated,de.civitascore.data.location.updated");
      when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://localhost:8080/v1.1");
      when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
      when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

      mockClient = mock(Client.class);
      mockTarget = mock(WebTarget.class);
      mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void updateThingWithValidConfigReturnsSuccess()
        throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.method(eq("PATCH"), any(Entity.class))).thenReturn(mockResponse);

      Map<String, Object> thingConfig = Map.of("description", "Updated description");

      ConfigEvent event = createConfigEvent(Operation.UPDATE, "Things/123", thingConfig);

      adapter.processConfigEvent("de.civitascore.data.thing.updated", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("FROST THING updated successfully", result.message());
      assertEquals("123", result.resourceId());
    }

    @Test
    void updateWithoutResourceIdThrowsFatalException() {
      ConfigEvent event =
          createConfigEvent(Operation.UPDATE, "Things", Map.of("name", "Updated Name"));

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.data.thing.updated", event));

      assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
    }

    @Test
    void updateWithClientErrorThrowsFatalException() {
      when(mockResponse.getStatus()).thenReturn(404);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"error\":\"Not found\"}");
      when(mockBuilder.method(eq("PATCH"), any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.UPDATE, "Things/999", Map.of("name", "Test"));

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.data.thing.updated", event));

      assertEquals(AdapterErrorCode.FROST_ENTITY_ERROR, exception.getErrorCode());
    }
  }

  @Nested
  class DeleteEntityOperations {

    private EventPublisher mockPublisher;
    private Client mockClient;
    private WebTarget mockTarget;
    private WebTarget mockPathTarget;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      when(mockConfig.getProperty("frost.topics"))
          .thenReturn("de.civitascore.data.thing.deleted,de.civitascore.data.location.deleted");
      when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://localhost:8080/v1.1");
      when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
      when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

      mockClient = mock(Client.class);
      mockTarget = mock(WebTarget.class);
      mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void deleteThingReturnsSuccess() throws RetryableAdapterException, FatalAdapterException {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.DELETE, "Things/123", null);

      adapter.processConfigEvent("de.civitascore.data.thing.deleted", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("FROST THING deleted successfully", result.message());
      assertEquals("123", result.resourceId());
    }

    @Test
    void deleteWithoutResourceIdThrowsFatalException() {
      ConfigEvent event = createConfigEvent(Operation.DELETE, "Things", null);

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.data.thing.deleted", event));

      assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
    }

    @Test
    void deleteNotFoundReturnsSuccess() throws RetryableAdapterException, FatalAdapterException {
      when(mockResponse.getStatus()).thenReturn(404);
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.DELETE, "Things/999", null);

      adapter.processConfigEvent("de.civitascore.data.thing.deleted", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());
      assertEquals(ConfigResultEvent.Status.SUCCESS, captor.getValue().status());
    }

    @Test
    void deleteWithOtherClientErrorThrowsFatalException() {
      when(mockResponse.getStatus()).thenReturn(403);
      when(mockResponse.readEntity(String.class)).thenReturn("{\"error\":\"Forbidden\"}");
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.DELETE, "Things/999", null);

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.data.thing.deleted", event));

      assertEquals(AdapterErrorCode.FROST_ENTITY_ERROR, exception.getErrorCode());
    }
  }

  @Nested
  class ResultPublishing {

    private EventPublisher mockPublisher;
    private Client mockClient;
    private WebTarget mockTarget;
    private WebTarget mockPathTarget;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      when(mockConfig.getProperty("frost.topics")).thenReturn("de.civitascore.data.thing.created");
      when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://localhost:8080/v1.1");
      when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
      when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

      mockClient = mock(Client.class);
      mockTarget = mock(WebTarget.class);
      mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
    }

    @Test
    void withoutEventPublisherDoesNotPublishResults()
        throws RetryableAdapterException, FatalAdapterException {
      adapter.setEventPublisher(null);

      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/v1.1/Things(123)");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "Things", Map.of("name", "Test Thing"));

      adapter.processConfigEvent("de.civitascore.data.thing.created", event);

      verify(mockPublisher, never()).publish(any(), any());
    }

    @Test
    void withNullResultTopicDoesNotPublishResults()
        throws FatalAdapterException, RetryableAdapterException {
      adapter.setEventPublisher(mockPublisher);

      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/v1.1/Things(123)");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Metadata metadata =
          new Metadata("msg-123", OffsetDateTime.now(), "test-source", "corr-123", "v1.0.0", null);
      FrostConfigValue fv = new FrostConfigValue();
      fv.setName("Test");
      Payload payload = new Payload("frost", "Things", Operation.CREATE, new Config(null, fv));
      ConfigEvent event = new ConfigEvent(metadata, payload);

      adapter.processConfigEvent("de.civitascore.data.thing.created", event);

      verify(mockPublisher, never()).publish(any(String.class), any(ConfigResultEvent.class));
    }
  }

  @Nested
  class ProjectScopedOperations {

    private EventPublisher mockPublisher;
    private Client mockClient;
    private WebTarget mockTarget;
    private WebTarget mockPathTarget;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      when(mockConfig.getProperty("frost.topics"))
          .thenReturn(
              "de.civitascore.data.project.created,de.civitascore.data.project.updated,"
                  + "de.civitascore.data.project.deleted,de.civitascore.data.thing.created");
      when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://localhost:8080/v1.1");
      when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
      when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

      mockClient = mock(Client.class);
      mockTarget = mock(WebTarget.class);
      mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void createProjectReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/v1.1/Projects(1)");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(
              Operation.CREATE,
              "Projects",
              Map.of("name", "Smart City", "description", "A smart city project"));

      adapter.processConfigEvent("de.civitascore.data.project.created", event);

      ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockTarget).path(pathCaptor.capture());
      assertEquals("Projects", pathCaptor.getValue());

      ArgumentCaptor<ConfigResultEvent> resultCaptor =
          ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), resultCaptor.capture());

      ConfigResultEvent result = resultCaptor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("FROST PROJECT created successfully", result.message());
      assertEquals("1", result.resourceId());
    }

    @Test
    void createThingScopedToProjectReturnsSuccess()
        throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/v1.1/Things(99)");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(
              Operation.CREATE,
              "Projects/42/Things",
              Map.of("name", "Scoped Sensor", "description", "A sensor within a project"));

      adapter.processConfigEvent("de.civitascore.data.thing.created", event);

      ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockTarget).path(pathCaptor.capture());
      assertEquals("Projects(42)/Things", pathCaptor.getValue());

      ArgumentCaptor<ConfigResultEvent> resultCaptor =
          ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), resultCaptor.capture());

      ConfigResultEvent result = resultCaptor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("FROST THING created successfully", result.message());
      assertEquals("99", result.resourceId());
    }

    @Test
    void updateProjectReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.method(eq("PATCH"), any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(
              Operation.UPDATE, "Projects/42", Map.of("description", "Updated project"));

      adapter.processConfigEvent("de.civitascore.data.project.updated", event);

      ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockTarget).path(pathCaptor.capture());
      assertEquals("Projects(42)", pathCaptor.getValue());

      ArgumentCaptor<ConfigResultEvent> resultCaptor =
          ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), resultCaptor.capture());

      ConfigResultEvent result = resultCaptor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("FROST PROJECT updated successfully", result.message());
      assertEquals("42", result.resourceId());
    }

    @Test
    void deleteProjectReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.DELETE, "Projects/42", null);

      adapter.processConfigEvent("de.civitascore.data.project.deleted", event);

      ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockTarget).path(pathCaptor.capture());
      assertEquals("Projects(42)", pathCaptor.getValue());

      ArgumentCaptor<ConfigResultEvent> resultCaptor =
          ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), resultCaptor.capture());

      ConfigResultEvent result = resultCaptor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("FROST PROJECT deleted successfully", result.message());
      assertEquals("42", result.resourceId());
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

    FrostConfigValue frostValue = FrostTestFixtures.buildFrostConfigValue(configValue);

    Payload payload = new Payload("frost", targetResource, operation, new Config(null, frostValue));
    return new ConfigEvent(metadata, payload);
  }
}
