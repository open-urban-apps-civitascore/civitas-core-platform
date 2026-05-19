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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.geoserver.DataStoreConfig;
import de.civitascore.configadapter.model.geoserver.FeatureTypeConfig;
import de.civitascore.configadapter.model.geoserver.GeoServerConfigValue;
import de.civitascore.configadapter.model.geoserver.WorkspaceConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Integration test for {@link GeoServerAdapter} against a real GeoServer Cloud stack. Mirrors the
 * dev-environment setup ({@code dev-environment/geoserver/docker-compose.yaml}) and the test cases
 * from the Bruno collection ({@code dev-environment/geoserver/bruno/}).
 *
 * <p>Covers the same provisioning flow exercised by Bruno (steps 01–04):
 *
 * <ol>
 *   <li>Create workspace
 *   <li>Create PostGIS datastore
 *   <li>Create feature types (sensor_locations, districts)
 *   <li>Update feature type
 *   <li>Recursive workspace delete
 *   <li>Idempotency: 409 on duplicate CREATE, 404 on missing DELETE
 * </ol>
 *
 * <p>Style creation is deliberately not exercised end-to-end: the adapter sends a JSON metadata
 * body, but GeoServer style creation requires an accompanying SLD XML body uploaded as {@code
 * application/vnd.ogc.sld+xml} (see Bruno step 05). SLD body upload is out of scope for the
 * adapter's current implementation.
 */
@TestInstance(Lifecycle.PER_CLASS)
class GeoServerAdapterIntegrationTest extends AbstractGeoServerIntegrationTest {

  private static final String WORKSPACE = "it_workspace_001";
  private static final String DATASTORE = "postgis";
  private static final String FEATURE_TYPE_POINT = "sensor_locations";
  private static final String FEATURE_TYPE_POLYGON = "districts";

  private GeoServerAdapter adapter;
  private CapturingEventPublisher eventPublisher;
  private Client httpClient;
  private ObjectMapper objectMapper;
  private String basicAuthHeader;

  @BeforeAll
  void prepareCleanWorkspace() {
    httpClient = ClientBuilder.newClient();
    objectMapper = new ObjectMapper();
    basicAuthHeader =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (ADMIN_USER + ":" + ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
    deleteWorkspaceIfExists();
  }

  @AfterAll
  void cleanupWorkspace() {
    deleteWorkspaceIfExists();
    if (httpClient != null) {
      httpClient.close();
    }
  }

  @BeforeEach
  void setUpAdapter() {
    Map<String, Object> props = new HashMap<>();
    props.put("geoserver.url", geoServerUrl());
    props.put("geoserver.admin.user", ADMIN_USER);
    props.put("geoserver.admin.password", ADMIN_PASSWORD);
    props.put(
        "geoserver.topics",
        String.join(
            ",",
            Topics.WORKSPACE_CREATED.toString(),
            Topics.WORKSPACE_UPDATED.toString(),
            Topics.WORKSPACE_DELETED.toString(),
            Topics.DATASTORE_CREATED.toString(),
            Topics.FEATURE_TYPE_CREATED.toString(),
            Topics.FEATURE_TYPE_UPDATED.toString()));
    AppConfig config = new AppConfig(new MapConfiguration(props));

    adapter = new GeoServerAdapter();
    adapter.initialize(config);
    eventPublisher = new CapturingEventPublisher();
    adapter.setEventPublisher(eventPublisher);
  }

  @AfterEach
  void tearDownAdapter() {
    if (adapter != null) {
      adapter.close();
    }
  }

  /**
   * End-to-end provisioning of a workspace, datastore and feature types — mirrors steps 01–04 of
   * the Bruno collection. Style creation is not exercised here: the adapter sends a JSON metadata
   * body but GeoServer requires an accompanying SLD XML body (Bruno uploads it as {@code
   * application/vnd.ogc.sld+xml}). SLD upload is intentionally out of scope for the adapter.
   */
  @Test
  void provisionWorkspaceDatastoreAndFeatureTypesEndToEnd() throws Exception {
    sendCreate(Topics.WORKSPACE_CREATED, "workspaces", workspace(WORKSPACE));
    assertLastResultSuccess();
    assertWorkspaceExists(WORKSPACE);

    sendCreate(
        Topics.DATASTORE_CREATED,
        "workspaces/" + WORKSPACE + "/datastores",
        postgisDatastore(DATASTORE, TEST_SCHEMA));
    assertLastResultSuccess();
    assertDatastoreExists(WORKSPACE, DATASTORE);

    sendCreate(
        Topics.FEATURE_TYPE_CREATED,
        "workspaces/" + WORKSPACE + "/datastores/" + DATASTORE + "/featuretypes",
        featureType(FEATURE_TYPE_POINT, "Sensor Locations"));
    assertLastResultSuccess();
    assertFeatureTypeExists(WORKSPACE, DATASTORE, FEATURE_TYPE_POINT);

    sendCreate(
        Topics.FEATURE_TYPE_CREATED,
        "workspaces/" + WORKSPACE + "/datastores/" + DATASTORE + "/featuretypes",
        featureType(FEATURE_TYPE_POLYGON, "Districts"));
    assertLastResultSuccess();
    assertFeatureTypeExists(WORKSPACE, DATASTORE, FEATURE_TYPE_POLYGON);

    sendUpdate(
        Topics.FEATURE_TYPE_UPDATED,
        "workspaces/"
            + WORKSPACE
            + "/datastores/"
            + DATASTORE
            + "/featuretypes/"
            + FEATURE_TYPE_POINT,
        featureTypeUpdate(FEATURE_TYPE_POINT, "Sensor Locations (updated)"));
    assertLastResultSuccess();
    JsonNode updated = getFeatureType(WORKSPACE, DATASTORE, FEATURE_TYPE_POINT);
    assertEquals("Sensor Locations (updated)", updated.path("featureType").path("title").asText());
  }

  @Test
  void duplicateWorkspaceCreateIsIdempotent409() {
    sendCreate(Topics.WORKSPACE_CREATED, "workspaces", workspace("it_idempotent_001"));
    assertLastResultSuccess();

    sendCreate(Topics.WORKSPACE_CREATED, "workspaces", workspace("it_idempotent_001"));
    assertLastResultSuccess();

    deleteWorkspace("it_idempotent_001");
  }

  @Test
  void deleteMissingWorkspaceIsIdempotent404() {
    String missing = "it_does_not_exist_" + UUID.randomUUID().toString().substring(0, 8);
    sendDelete(Topics.WORKSPACE_DELETED, "workspaces/" + missing);
    assertLastResultSuccess();
  }

  @Test
  void deleteWorkspaceUsesRecurseTrue() {
    String ws = "it_recurse_test";
    String ds = "child_ds";
    sendCreate(Topics.WORKSPACE_CREATED, "workspaces", workspace(ws));
    assertLastResultSuccess();

    sendCreate(
        Topics.DATASTORE_CREATED,
        "workspaces/" + ws + "/datastores",
        postgisDatastore(ds, TEST_SCHEMA));
    assertLastResultSuccess();

    sendDelete(Topics.WORKSPACE_DELETED, "workspaces/" + ws);
    assertLastResultSuccess();
    assertFalse(
        workspaceExists(ws), "Workspace and its child datastore should be deleted recursively");
  }

  // ---------- helpers: sending events ----------

  private void sendCreate(Topics topic, String targetResource, GeoServerConfigValue value) {
    sendEvent(topic, Operation.CREATE, targetResource, value);
  }

  private void sendUpdate(Topics topic, String targetResource, GeoServerConfigValue value) {
    sendEvent(topic, Operation.UPDATE, targetResource, value);
  }

  private void sendDelete(Topics topic, String targetResource) {
    sendEvent(topic, Operation.DELETE, targetResource, new WorkspaceConfig());
  }

  private void sendEvent(
      Topics topic, Operation operation, String targetResource, GeoServerConfigValue value) {
    eventPublisher.clear();
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "integration-test",
            UUID.randomUUID().toString(),
            "1.0",
            "test-result-topic");
    Payload payload = new Payload("geoserver", targetResource, operation, new Config(null, value));
    ConfigEvent event = new ConfigEvent(metadata, payload);
    try {
      adapter.processConfigEvent(topic.toString(), event);
    } catch (Exception e) {
      throw new AssertionError(
          "Adapter failed to process event for topic " + topic + ": " + e.getMessage(), e);
    }
  }

  private void assertLastResultSuccess() {
    List<ConfigResultEvent> events = eventPublisher.events();
    assertEquals(1, events.size(), "Expected exactly one result event");
    ConfigResultEvent result = events.getFirst();
    assertEquals(
        ConfigResultEvent.Status.SUCCESS,
        result.status(),
        () -> "Expected SUCCESS but got " + result.status() + " — " + result.message());
  }

  // ---------- helpers: REST verification (direct calls) ----------

  private void assertWorkspaceExists(String name) {
    assertEquals(200, statusOf("/rest/workspaces/" + name + ".json"));
  }

  private boolean workspaceExists(String name) {
    return statusOf("/rest/workspaces/" + name + ".json") == 200;
  }

  private void assertDatastoreExists(String workspace, String datastore) {
    assertEquals(
        200, statusOf("/rest/workspaces/" + workspace + "/datastores/" + datastore + ".json"));
  }

  private void assertFeatureTypeExists(String workspace, String datastore, String featureType) {
    assertEquals(
        200,
        statusOf(
            "/rest/workspaces/"
                + workspace
                + "/datastores/"
                + datastore
                + "/featuretypes/"
                + featureType
                + ".json"));
  }

  private JsonNode getFeatureType(String workspace, String datastore, String featureType)
      throws Exception {
    try (Response response =
        httpClient
            .target(geoServerUrl())
            .path(
                "/rest/workspaces/"
                    + workspace
                    + "/datastores/"
                    + datastore
                    + "/featuretypes/"
                    + featureType
                    + ".json")
            .request(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
            .get()) {
      assertEquals(200, response.getStatus());
      String body = response.readEntity(String.class);
      assertNotNull(body);
      return objectMapper.readTree(body);
    }
  }

  private int statusOf(String path) {
    try (Response response =
        httpClient
            .target(geoServerUrl())
            .path(path)
            .request(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
            .get()) {
      return response.getStatus();
    }
  }

  private void deleteWorkspaceIfExists() {
    deleteWorkspace(WORKSPACE);
  }

  private void deleteWorkspace(String name) {
    try (Response response =
        httpClient
            .target(geoServerUrl())
            .path("/rest/workspaces/" + name)
            .queryParam("recurse", "true")
            .request(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
            .delete()) {
      // 200, 404 (already gone), or 403/500 all acceptable for cleanup
      response.readEntity(String.class);
    }
  }

  // ---------- fixtures ----------

  private static WorkspaceConfig workspace(String name) {
    WorkspaceConfig w = new WorkspaceConfig();
    w.setName(name);
    return w;
  }

  private static DataStoreConfig postgisDatastore(String name, String schema) {
    DataStoreConfig ds = new DataStoreConfig();
    ds.setName(name);
    ds.setType("PostGIS");
    ds.setEnabled(true);
    ds.setHost(POSTGIS_HOST_ALIAS);
    ds.setPort("5432");
    ds.setDatabase(POSTGIS_DB);
    ds.setSchema(schema);
    ds.setUser(POSTGIS_USER);
    ds.setPasswd(POSTGIS_PASSWORD);
    ds.setDbtype("postgis");
    return ds;
  }

  private static FeatureTypeConfig featureType(String name, String title) {
    FeatureTypeConfig ft = new FeatureTypeConfig();
    ft.setName(name);
    ft.setNativeName(name);
    ft.setTitle(title);
    ft.setSrs("EPSG:4326");
    ft.setEnabled(true);
    return ft;
  }

  private static FeatureTypeConfig featureTypeUpdate(String name, String newTitle) {
    FeatureTypeConfig ft = new FeatureTypeConfig();
    ft.setName(name);
    ft.setTitle(newTitle);
    return ft;
  }

  // ---------- event publisher capture ----------

  static class CapturingEventPublisher implements EventPublisher {
    private final List<ConfigResultEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(String topic, ConfigResultEvent event) {
      events.add(event);
    }

    @Override
    public String getName() {
      return "capture";
    }

    @Override
    public void initialize(ApplicationConfig config, ConfigAdapter adapter) {}

    List<ConfigResultEvent> events() {
      return new ArrayList<>(events);
    }

    void clear() {
      events.clear();
    }
  }
}
