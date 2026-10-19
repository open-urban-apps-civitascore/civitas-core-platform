/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.geoserver;

import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.COVERAGE;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.COVERAGE_STORE;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.DATASTORE;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.FEATURE_TYPE;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.LAYER;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.STYLE;
import static de.civitascore.configadapter.geoserver.GeoServerAdapter.ResourceType.WORKSPACE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.io.IOException;
import java.time.OffsetDateTime;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  void initializationWithoutPasswordThrowsException() {
    when(mockConfig.getProperty("geoserver.topics"))
        .thenReturn("de.civitascore.geo.workspace.created");
    when(mockConfig.getProperty("geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn("http://localhost:8080/geoserver");
    when(mockConfig.getProperty("geoserver.admin.user")).thenReturn("admin");

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

    @Test
    void detectsCoverageStoreAndCoverage() {
      assertEquals(
          COVERAGE_STORE, GeoServerAdapter.detectResourceType("workspaces/ws/coveragestores/cs"));
      assertEquals(
          COVERAGE,
          GeoServerAdapter.detectResourceType("workspaces/ws/coveragestores/cs/coverages/c"));
    }

    @Test
    void resourceNamedLikeCollectionIsNotMisclassified() {
      assertEquals(WORKSPACE, GeoServerAdapter.detectResourceType("workspaces/styles"));
      assertEquals(WORKSPACE, GeoServerAdapter.detectResourceType("workspaces/layers"));
    }

    @Test
    void ignoresLeadingAndTrailingSlashes() {
      assertEquals(WORKSPACE, GeoServerAdapter.detectResourceType("/workspaces/myws/"));
      assertEquals(
          DATASTORE, GeoServerAdapter.detectResourceType("/workspaces/myws/datastores/myds/"));
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

    @Test
    void extractsResourceNameIgnoringTrailingSlash() {
      assertEquals("myws", GeoServerAdapter.extractResourceName("workspaces/myws/"));
    }
  }

  @Nested
  class RecurseFlag {

    @Test
    void workspaceDeletionUsesRecurse() {
      assertTrue(GeoServerAdapter.shouldUseRecurse(WORKSPACE));
    }

    @Test
    void datastoreDeletionUsesRecurse() {
      assertTrue(GeoServerAdapter.shouldUseRecurse(DATASTORE));
    }

    @Test
    void coverageStoreDeletionUsesRecurse() {
      assertTrue(GeoServerAdapter.shouldUseRecurse(COVERAGE_STORE));
    }

    @Test
    void featureTypeDeletionUsesRecurse() {
      assertTrue(GeoServerAdapter.shouldUseRecurse(FEATURE_TYPE));
    }

    @Test
    void coverageDeletionUsesRecurse() {
      assertTrue(GeoServerAdapter.shouldUseRecurse(COVERAGE));
    }

    @Test
    void styleDeletionDoesNotUseRecurse() {
      assertFalse(GeoServerAdapter.shouldUseRecurse(STYLE));
    }

    @Test
    void layerDeletionDoesNotUseRecurse() {
      assertFalse(GeoServerAdapter.shouldUseRecurse(LAYER));
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

    @Test
    void decodesUrlEncodedName() {
      assertEquals(
          "my layer",
          GeoServerAdapter.extractNameFromLocation(
              "http://localhost:8080/geoserver/rest/layers/my%20layer"));
    }
  }

  @Nested
  class CreateOperations {

    private MockWebServer server;
    private EventPublisher mockPublisher;

    @BeforeEach
    void setUpMocks() throws IOException {
      server = new MockWebServer();
      server.start();
      stubServerConfig(
          server,
          "de.civitascore.geo.workspace.created,de.civitascore.geo.featuretype.created,"
              + "de.civitascore.geo.datastore.created");
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @AfterEach
    void tearDownServer() throws IOException {
      server.close();
    }

    @Test
    void createWorkspaceReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(
          new MockResponse.Builder()
              .code(201)
              .addHeader("Location", "http://localhost:8080/geoserver/rest/workspaces/myws")
              .build());

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
      server.enqueue(
          new MockResponse.Builder()
              .code(201)
              .addHeader(
                  "Location",
                  "http://localhost:8080/geoserver/rest/workspaces/myws/datastores/myds/featuretypes/traffic")
              .build());

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
      server.enqueue(new MockResponse.Builder().code(409).build());

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", workspace("existing"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());
      assertEquals(ConfigResultEvent.Status.SUCCESS, captor.getValue().status());
    }

    @Test
    void createConflictResultIncludesResourceNameFromBody()
        throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(new MockResponse.Builder().code(409).build());

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", workspace("myws"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

      ConfigResultEvent result = captor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals("myws", result.resourceId());
    }

    @Test
    void createWithNullConfigValueThrowsFatalException() {
      Metadata metadata =
          new Metadata(
              "msg-001", OffsetDateTime.now(), "test-source", "corr-001", "v1.0.0", "result-topic");
      Payload payload =
          new Payload("geoserver", "workspaces", Operation.CREATE, new Config(null, null));
      ConfigEvent event = new ConfigEvent(metadata, payload);

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.created", event));

      assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
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
      server.enqueue(new MockResponse.Builder().code(400).body("Bad request").build());

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", new WorkspaceConfig());

      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.created", event));

      assertEquals(AdapterErrorCode.GEOSERVER_RESOURCE_ERROR, exception.getErrorCode());
    }

    @Test
    void createWithServerErrorThrowsRetryableException() {
      server.enqueue(new MockResponse.Builder().code(503).body("Service Unavailable").build());

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", new WorkspaceConfig());

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> adapter.processConfigEvent("de.civitascore.geo.workspace.created", event));

      assertEquals(AdapterErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void createWithNetworkErrorThrowsRetryableException() throws IOException {
      // Torn down before the request is even made: the connection attempt itself fails.
      server.close();

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

    private MockWebServer server;
    private EventPublisher mockPublisher;

    @BeforeEach
    void setUpMocks() throws IOException {
      server = new MockWebServer();
      server.start();
      stubServerConfig(
          server, "de.civitascore.geo.workspace.updated,de.civitascore.geo.featuretype.updated");
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @AfterEach
    void tearDownServer() throws IOException {
      server.close();
    }

    @Test
    void updateWorkspaceReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(new MockResponse.Builder().code(200).build());

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

    private MockWebServer server;
    private EventPublisher mockPublisher;

    @BeforeEach
    void setUpMocks() throws IOException {
      server = new MockWebServer();
      server.start();
      stubServerConfig(
          server, "de.civitascore.geo.workspace.deleted,de.civitascore.geo.featuretype.deleted");
      adapter.initialize(mockConfig);

      mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);
    }

    @AfterEach
    void tearDownServer() throws IOException {
      server.close();
    }

    @Test
    void deleteWorkspaceReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(new MockResponse.Builder().code(200).build());

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
        throws FatalAdapterException, RetryableAdapterException, InterruptedException {
      server.enqueue(new MockResponse.Builder().code(200).build());

      ConfigEvent event = createConfigEvent(Operation.DELETE, "workspaces/myws", null);
      adapter.processConfigEvent("de.civitascore.geo.workspace.deleted", event);

      assertEquals("true", server.takeRequest().getUrl().queryParameter("recurse"));
    }

    @Test
    void deleteNotFoundReturnsSuccess() throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(new MockResponse.Builder().code(404).build());

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
    void deleteFeatureTypeAddsRecurseQueryParam()
        throws FatalAdapterException, RetryableAdapterException, InterruptedException {
      server.enqueue(new MockResponse.Builder().code(200).build());

      ConfigEvent event =
          createConfigEvent(
              Operation.DELETE, "workspaces/myws/datastores/myds/featuretypes/myft", null);
      adapter.processConfigEvent("de.civitascore.geo.featuretype.deleted", event);

      assertEquals("true", server.takeRequest().getUrl().queryParameter("recurse"));
    }
  }

  @Nested
  class ResultPublishing {

    private MockWebServer server;

    @BeforeEach
    void setUpMocks() throws IOException {
      server = new MockWebServer();
      server.start();
      stubServerConfig(server, "de.civitascore.geo.workspace.created");
      adapter.initialize(mockConfig);
    }

    @AfterEach
    void tearDownServer() throws IOException {
      server.close();
    }

    @Test
    void withoutEventPublisherDoesNotPublishResults()
        throws FatalAdapterException, RetryableAdapterException {
      EventPublisher mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(null);

      server.enqueue(
          new MockResponse.Builder()
              .code(201)
              .addHeader("Location", "http://localhost:8080/geoserver/rest/workspaces/myws")
              .build());

      ConfigEvent event = createConfigEvent(Operation.CREATE, "workspaces", workspace("myws"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      verify(mockPublisher, never()).publish(any(), any());
    }

    @Test
    void withNullResultTopicDoesNotPublishResults()
        throws FatalAdapterException, RetryableAdapterException {
      EventPublisher mockPublisher = mock(EventPublisher.class);
      adapter.setEventPublisher(mockPublisher);

      server.enqueue(
          new MockResponse.Builder()
              .code(201)
              .addHeader("Location", "http://localhost:8080/geoserver/rest/workspaces/myws")
              .build());

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

  @Nested
  class RequireSafePath {

    // requireSafePath must enforce its contract on its own, independent of the caller's
    // slash-normalization: every "/"-delimited segment (including leading/trailing empties) must be
    // a safe single path segment, otherwise the input is rejected as INVALID_PAYLOAD.
    @ParameterizedTest
    @ValueSource(
        strings = {
          "workspaces/../../admin", // parent traversal in the middle
          "workspaces/..", // parent traversal at the end
          "..", // traversal-only
          ".", // single-dot segment
          "workspaces/./styles", // single-dot in the middle
          "../workspaces/myws", // leading parent traversal
          "workspaces//styles", // empty segment from double slash
          "workspaces/", // trailing empty segment (split must keep it)
          "/workspaces", // leading empty segment
          "workspaces/%2e%2e/admin", // url-encoded dot-dot
          "workspaces/%2f..%2fadmin", // url-encoded slash
          "workspaces/..\\..\\admin", // backslash traversal
          "workspaces/my ws", // space
          "workspaces/ws\nadmin", // newline / log injection
          "workspaces/ws\tadmin", // tab
          "workspaces/wörld", // non-ascii
          "" // empty path
        })
    void rejectsUnsafePath(String targetResource) {
      FatalAdapterException exception =
          assertThrows(
              FatalAdapterException.class, () -> GeoServerAdapter.requireSafePath(targetResource));
      assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "workspaces",
          "workspaces/myws",
          "workspaces/my_ws-1",
          "workspaces/my_ws/styles/base-style",
          "workspaces/myws/datastores/myds/featuretypes/Traffic_2024"
        })
    void acceptsSafePath(String targetResource) {
      assertDoesNotThrow(() -> GeoServerAdapter.requireSafePath(targetResource));
    }
  }

  @Nested
  class PathTraversalProtection {

    private MockWebServer server;

    @BeforeEach
    void setUpMocks() throws IOException {
      server = new MockWebServer();
      server.start();
      stubServerConfig(
          server,
          "de.civitascore.geo.workspace.created,de.civitascore.geo.workspace.updated,"
              + "de.civitascore.geo.workspace.deleted");
      adapter.initialize(mockConfig);
      adapter.setEventPublisher(mock(EventPublisher.class));
    }

    @AfterEach
    void tearDownServer() throws IOException {
      server.close();
    }

    @Test
    void createRejectsParentTraversalInTheMiddle() {
      assertRejectedWithoutHttpCall(
          Operation.CREATE, "de.civitascore.geo.workspace.created", "workspaces/../../admin");
    }

    @Test
    void createRejectsTraversalAtTheEnd() {
      assertRejectedWithoutHttpCall(
          Operation.CREATE, "de.civitascore.geo.workspace.created", "workspaces/..");
    }

    @Test
    void createRejectsTraversalOnlyPath() {
      assertRejectedWithoutHttpCall(Operation.CREATE, "de.civitascore.geo.workspace.created", "..");
    }

    @Test
    void createRejectsSingleDotSegment() {
      assertRejectedWithoutHttpCall(
          Operation.CREATE, "de.civitascore.geo.workspace.created", "workspaces/./styles");
    }

    @Test
    void createRejectsEmptySegmentFromDoubleSlash() {
      assertRejectedWithoutHttpCall(
          Operation.CREATE, "de.civitascore.geo.workspace.created", "workspaces//styles");
    }

    @Test
    void createRejectsUrlEncodedTraversal() {
      assertRejectedWithoutHttpCall(
          Operation.CREATE, "de.civitascore.geo.workspace.created", "workspaces/%2e%2e/admin");
    }

    @Test
    void createRejectsBackslashTraversal() {
      assertRejectedWithoutHttpCall(
          Operation.CREATE, "de.civitascore.geo.workspace.created", "workspaces/..\\..\\admin");
    }

    @Test
    void updateRejectsParentTraversal() {
      assertRejectedWithoutHttpCall(
          Operation.UPDATE, "de.civitascore.geo.workspace.updated", "workspaces/myws/../../admin");
    }

    @Test
    void deleteRejectsParentTraversal() {
      assertRejectedWithoutHttpCall(
          Operation.DELETE, "de.civitascore.geo.workspace.deleted", "workspaces/myws/../../admin");
    }

    @Test
    void createAcceptsResourceNameWithUnderscoreAndHyphen()
        throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(
          new MockResponse.Builder()
              .code(201)
              .addHeader("Location", "http://localhost:8080/geoserver/rest/workspaces/my_ws-1")
              .build());

      ConfigEvent event =
          createConfigEvent(Operation.CREATE, "workspaces/my_ws-1", workspace("my_ws-1"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      assertEquals(1, server.getRequestCount());
    }

    @Test
    void createAcceptsMultiSegmentSafePath()
        throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(
          new MockResponse.Builder()
              .code(201)
              .addHeader(
                  "Location",
                  "http://localhost:8080/geoserver/rest/workspaces/my_ws/styles/base-style")
              .build());

      ConfigEvent event =
          createConfigEvent(
              Operation.CREATE, "workspaces/my_ws/styles/base-style", workspace("base-style"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      assertEquals(1, server.getRequestCount());
    }

    @Test
    void createStillAcceptsLeadingAndTrailingSlashes()
        throws FatalAdapterException, RetryableAdapterException {
      server.enqueue(
          new MockResponse.Builder()
              .code(201)
              .addHeader("Location", "http://localhost:8080/geoserver/rest/workspaces/ws")
              .build());

      ConfigEvent event = createConfigEvent(Operation.CREATE, "/workspaces/ws/", workspace("ws"));

      adapter.processConfigEvent("de.civitascore.geo.workspace.created", event);

      assertEquals(1, server.getRequestCount());
    }

    private void assertRejectedWithoutHttpCall(
        Operation operation, String topic, String targetResource) {
      ConfigEvent event = createConfigEvent(operation, targetResource, workspace("myws"));

      FatalAdapterException exception =
          assertThrows(FatalAdapterException.class, () -> adapter.processConfigEvent(topic, event));

      assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
      assertEquals(0, server.getRequestCount());
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

  /** Points the adapter's serverUrl at {@code server} instead of the fixed localhost default. */
  private void stubServerConfig(MockWebServer server, String topics) {
    when(mockConfig.getProperty("geoserver.topics")).thenReturn(topics);
    when(mockConfig.getProperty("geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn(server.url("/geoserver").toString());
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
