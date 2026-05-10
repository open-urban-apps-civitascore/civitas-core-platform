/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.geoserver;

import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.DATASTORE;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.FEATURE_TYPE;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.LAYER;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.STYLE;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.WORKSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import de.civitascore.configadapter.model.geoserver.FeatureTypeConfig;
import de.civitascore.configadapter.model.geoserver.GeoServerConfigValue;
import de.civitascore.configadapter.model.geoserver.WorkspaceConfig;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeoServerAdapterTest {

  private GeoServerAdapter adapter;
  private AdapterConfig mockConfig;

  @BeforeEach
  void setUp() {
    adapter = new GeoServerAdapter();
    mockConfig = mock(AdapterConfig.class);
  }

  @Test
  void adapterNameIsGeoserver() {
    assertEquals("geoserver", adapter.getName());
  }

  @Test
  void initializationWithCredentialsSucceeds() {
    stubBaseConfig("de.civitascore.geo.workspace.created");

    adapter.initialize(mockConfig);

    assertEquals(1, adapter.getSubscribedTopics().size());
    assertTrue(adapter.getSubscribedTopics().contains("de.civitascore.geo.workspace.created"));
  }

  @Test
  void initializationWithMultipleTopicsSubscribesToAll() {
    stubBaseConfig("de.civitascore.geo.workspace.created,de.civitascore.geo.featuretype.created");

    adapter.initialize(mockConfig);

    assertEquals(2, adapter.getSubscribedTopics().size());
  }

  @Test
  void initializationWithoutCredentialsThrowsException() {
    when(mockConfig.getProperty("geoserver.topics"))
        .thenReturn("de.civitascore.geo.workspace.created");
    when(mockConfig.getProperty("geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn("http://localhost:8080/geoserver");

    assertThrows(IllegalArgumentException.class, () -> adapter.initialize(mockConfig));
  }

  @Test
  void closeReleasesResources() {
    stubBaseConfig("de.civitascore.geo.workspace.created");

    adapter.initialize(mockConfig);
    adapter.close();
  }

  @Nested
  class ResourceTypeDetection {

    @Test
    void detectsWorkspace() {
      assertEquals(WORKSPACE, GeoServerAdapter.detectResourceType("workspaces"));
      assertEquals(WORKSPACE, GeoServerAdapter.detectResourceType("workspaces/myws"));
    }

    @Test
    void detectsDatastore() {
      assertEquals(DATASTORE, GeoServerAdapter.detectResourceType("workspaces/myws/datastores"));
      assertEquals(
          DATASTORE, GeoServerAdapter.detectResourceType("workspaces/myws/datastores/myds"));
    }

    @Test
    void detectsFeatureType() {
      assertEquals(
          FEATURE_TYPE,
          GeoServerAdapter.detectResourceType("workspaces/myws/datastores/myds/featuretypes"));
      assertEquals(
          FEATURE_TYPE,
          GeoServerAdapter.detectResourceType("workspaces/myws/datastores/myds/featuretypes/myft"));
    }

    @Test
    void detectsStyle() {
      assertEquals(STYLE, GeoServerAdapter.detectResourceType("styles"));
      assertEquals(STYLE, GeoServerAdapter.detectResourceType("styles/mystyle"));
      assertEquals(STYLE, GeoServerAdapter.detectResourceType("workspaces/myws/styles/s"));
    }

    @Test
    void detectsLayer() {
      assertEquals(LAYER, GeoServerAdapter.detectResourceType("layers"));
      assertEquals(LAYER, GeoServerAdapter.detectResourceType("layers/mylayer"));
    }
  }

  @Nested
  class ResourceNameChecks {

    @Test
    void collectionPathHasNoResourceName() {
      assertFalse(GeoServerAdapter.hasResourceName("workspaces"));
      assertFalse(GeoServerAdapter.hasResourceName("workspaces/myws/datastores"));
      assertFalse(GeoServerAdapter.hasResourceName("styles"));
    }

    @Test
    void itemPathHasResourceName() {
      assertTrue(GeoServerAdapter.hasResourceName("workspaces/myws"));
      assertTrue(GeoServerAdapter.hasResourceName("workspaces/myws/datastores/myds"));
      assertTrue(GeoServerAdapter.hasResourceName("styles/mystyle"));
    }

    @Test
    void extractsLastPathSegmentAsResourceName() {
      assertEquals("myws", GeoServerAdapter.extractResourceName("workspaces/myws"));
      assertEquals("myds", GeoServerAdapter.extractResourceName("workspaces/myws/datastores/myds"));
      assertEquals(
          "myft",
          GeoServerAdapter.extractResourceName(
              "workspaces/myws/datastores/myds/featuretypes/myft"));
    }
  }

  @Nested
  class RecurseFlag {

    @Test
    void workspaceDeletionUsesRecurse() {
      assertTrue(GeoServerAdapter.shouldUseRecurse("workspaces/myws"));
    }

    @Test
    void datastoreDeletionUsesRecurse() {
      assertTrue(GeoServerAdapter.shouldUseRecurse("workspaces/myws/datastores/myds"));
    }

    @Test
    void featureTypeDeletionDoesNotUseRecurse() {
      assertFalse(
          GeoServerAdapter.shouldUseRecurse("workspaces/myws/datastores/myds/featuretypes/myft"));
    }

    @Test
    void styleDeletionDoesNotUseRecurse() {
      assertFalse(GeoServerAdapter.shouldUseRecurse("styles/mystyle"));
    }

    @Test
    void collectionPathDoesNotUseRecurse() {
      assertFalse(GeoServerAdapter.shouldUseRecurse("workspaces"));
    }
  }

  @Nested
  class LocationHeaderExtraction {

    @Test
    void extractsNameFromLocationHeader() {
      assertEquals(
          "myws",
          GeoServerAdapter.extractNameFromLocation(
              "http://localhost:8080/geoserver/rest/workspaces/myws"));
    }

    @Test
    void returnsNullForNullHeader() {
      assertNull(GeoServerAdapter.extractNameFromLocation(null));
    }

    @Test
    void returnsNullForBlankHeader() {
      assertNull(GeoServerAdapter.extractNameFromLocation("  "));
    }
  }

  @Nested
  class CreateOperations {

    private EventPublisher mockPublisher;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      stubBaseConfig(
          "de.civitascore.geo.workspace.created,de.civitascore.geo.featuretype.created,"
              + "de.civitascore.geo.datastore.created");

      Client mockClient = buildMockClient();
      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    private Client buildMockClient() {
      Client mockClient = mock(Client.class);
      WebTarget mockTarget = mock(WebTarget.class);
      WebTarget mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.queryParam(any(String.class), any())).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      return mockClient;
    }

    @Test
    void createWorkspaceReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/geoserver/rest/workspaces/myws");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", workspace("myws"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("GeoServer WORKSPACE created successfully", result.message());
      assertEquals("myws", result.resourceId());
    }

    @Test
    void createFeatureTypeReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn(
              "http://localhost:8080/geoserver/rest/workspaces/myws/datastores/myds/featuretypes/traffic");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(
              Operation.CREATE,
              "workspaces/myws/datastores/myds/featuretypes",
              featureType("traffic", "EPSG:4326"));

      adapter.processConfigEvent("de.civitascore.geo.featuretype.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("GeoServer FEATURE_TYPE created successfully", result.message());
      assertEquals("traffic", result.resourceId());
    }

    @Test
    void createWithConflictReturnsSuccess()
        throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(409);
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", workspace("existing"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());
      assertEquals(ConfigResultEvent.Status.SUCCESS, captor.getValue().status());
    }

    @Test
    void createWithUnknownResourceTypeThrowsFatalException() {
      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "unknownresource", new WorkspaceConfig());

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.created", event));

      assertEquals(AdapterErrorCode.INVALID_RESOURCE_TYPE, exception.getErrorCode());
    }

    @Test
    void createWithClientErrorThrowsFatalException() {
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class)).thenReturn("Bad request");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", new WorkspaceConfig());

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.created", event));

      assertEquals(AdapterErrorCode.GEOSERVER_RESOURCE_ERROR, exception.getErrorCode());
    }

    @Test
    void createWithServerErrorThrowsRetryableException() {
      when(mockResponse.getStatus()).thenReturn(503);
      when(mockResponse.readEntity(String.class)).thenReturn("Service Unavailable");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", new WorkspaceConfig());

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.created", event));

      assertEquals(AdapterErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void createWithNetworkErrorThrowsRetryableException() {
      when(mockBuilder.post(any(Entity.class)))
          .thenThrow(new ProcessingException("Connection refused"));

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", workspace("myws"));

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.created", event));

      assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
    }
  }

  @Nested
  class UpdateOperations {

    private EventPublisher mockPublisher;
    private Client mockClient;
    private WebTarget mockTarget;
    private WebTarget mockPathTarget;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      stubBaseConfig("de.civitascore.geo.workspace.updated,de.civitascore.geo.featuretype.updated");

      mockClient = mock(Client.class);
      mockTarget = mock(WebTarget.class);
      mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.queryParam(any(String.class), any())).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void updateWorkspaceReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.UPDATE, "workspaces/myws", workspace("myws"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.updated", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("GeoServer WORKSPACE updated successfully", result.message());
      assertEquals("myws", result.resourceId());
    }

    @Test
    void updateWithCollectionPathThrowsFatalException() {
      ConfigEvent event = createConfigEvent(Operation.UPDATE, "workspaces", new WorkspaceConfig());

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.updated", event));

      assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
    }
  }

  @Nested
  class DeleteOperations {

    private EventPublisher mockPublisher;
    private Client mockClient;
    private WebTarget mockTarget;
    private WebTarget mockPathTarget;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      stubBaseConfig("de.civitascore.geo.workspace.deleted,de.civitascore.geo.featuretype.deleted");

      mockClient = mock(Client.class);
      mockTarget = mock(WebTarget.class);
      mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.queryParam(any(String.class), any())).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void deleteWorkspaceReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.DELETE, "workspaces/myws", null);

      adapter.processConfigEvent("de.civitascore.geo.workspace.deleted", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("GeoServer WORKSPACE deleted successfully", result.message());
      assertEquals("myws", result.resourceId());
    }

    @Test
    void deleteWorkspaceAddsRecurseQueryParam()
        throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.DELETE, "workspaces/myws", null);
      adapter.processConfigEvent("de.civitascore.geo.workspace.deleted", event);

      ArgumentCaptor<String> paramNameCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Object> paramValueCaptor = ArgumentCaptor.forClass(Object.class);
      verify(mockPathTarget).queryParam(paramNameCaptor.capture(), paramValueCaptor.capture());
      assertEquals("recurse", paramNameCaptor.getValue());
      assertEquals("true", paramValueCaptor.getValue());
    }

    @Test
    void deleteNotFoundReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(404);
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.DELETE, "workspaces/nonexistent", null);

      adapter.processConfigEvent("de.civitascore.geo.workspace.deleted", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());
      assertEquals(ConfigResultEvent.Status.SUCCESS, captor.getValue().status());
    }

    @Test
    void deleteWithCollectionPathThrowsFatalException() {
      ConfigEvent event = createConfigEvent(Operation.DELETE, "workspaces", null);

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.deleted", event));

      assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
    }

    @Test
    void deleteFeatureTypeDoesNotAddRecurseQueryParam()
        throws FatalAdapterException, RetryableAdapterException {
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.delete()).thenReturn(mockResponse);

      ConfigEvent event =
          createConfigEvent(
              Operation.DELETE, "workspaces/myws/datastores/myds/featuretypes/myft", null);
      adapter.processConfigEvent("de.civitascore.geo.featuretype.deleted", event);

      verify(mockPathTarget, never()).queryParam(any(), any());
    }
  }

  @Nested
  class ResultPublishing {

    private EventPublisher mockPublisher;
    private Invocation.Builder mockBuilder;
    private Response mockResponse;

    @BeforeEach
    void setUpMocks() {
      stubBaseConfig("de.civitascore.geo.workspace.created");

      Client mockClient = mock(Client.class);
      WebTarget mockTarget = mock(WebTarget.class);
      WebTarget mockPathTarget = mock(WebTarget.class);
      mockBuilder = mock(Invocation.Builder.class);
      mockResponse = mock(Response.class);

      when(mockClient.target(any(String.class))).thenReturn(mockTarget);
      when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
      when(mockPathTarget.queryParam(any(String.class), any())).thenReturn(mockPathTarget);
      when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
      when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

      adapter.setClient(mockClient);
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
    }

    @Test
    void withoutEventPublisherDoesNotPublishResults()
        throws FatalAdapterException, RetryableAdapterException {
      adapter.setEventPublisher(null);

      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/geoserver/rest/workspaces/myws");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", workspace("myws"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      verify(mockPublisher, never()).publish(any(), any());
    }

    @Test
    void withNullResultTopicDoesNotPublishResults()
        throws FatalAdapterException, RetryableAdapterException {
      adapter.setEventPublisher(mockPublisher);

      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/geoserver/rest/workspaces/myws");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      Metadata metadata =
          new Metadata("msg-001", OffsetDateTime.now(), "test-source", "corr-001", "v1.0.0", null);
      Payload payload =
          new Payload(
              "geoserver", "workspaces", Operation.CREATE, new Config(null, workspace("myws")));
      ConfigEvent event = new ConfigEvent(metadata, payload);

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      verify(mockPublisher, never()).publish(any(String.class), any(ConfigResultEvent.class));
    }
  }

  // ============== HELPERS ==============

  private void stubBaseConfig(String topics) {
    when(mockConfig.getProperty("geoserver.topics")).thenReturn(topics);
    when(mockConfig.getProperty("geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn("http://localhost:8080/geoserver");
    when(mockConfig.getProperty("geoserver.admin.user")).thenReturn("admin");
    when(mockConfig.getProperty("geoserver.admin.password")).thenReturn("geoserver");
  }

  private ConfigEvent createConfigEvent(
      Operation operation, String targetResource, GeoServerConfigValue configValue) {
    Metadata metadata =
        new Metadata(
            "msg-001",
            OffsetDateTime.now(),
            "test-source",
            "corr-001",
            "v1.0.0",
            "test-result-topic");
    GeoServerConfigValue geoValue = configValue != null ? configValue : new WorkspaceConfig();
    Payload payload =
        new Payload("geoserver", targetResource, operation, new Config(null, geoValue));
    return new ConfigEvent(metadata, payload);
  }

  private static WorkspaceConfig workspace(String name) {
    WorkspaceConfig ws = new WorkspaceConfig();
    ws.setName(name);
    return ws;
  }

  private static FeatureTypeConfig featureType(String name, String srs) {
    FeatureTypeConfig ft = new FeatureTypeConfig();
    ft.setName(name);
    ft.setNativeName(name);
    ft.setSrs(srs);
    return ft;
  }
}
