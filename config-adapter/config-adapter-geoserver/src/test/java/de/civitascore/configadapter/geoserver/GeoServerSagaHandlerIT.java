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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AppConfig;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Drives {@link GeoServerSagaHandler} against a real GeoServer Cloud stack for a workspace that
 * serves no feature type. {@link GeoServerAdapterIT} covers the event-based {@link
 * GeoServerAdapter}, a different code path.
 *
 * <p>GeoServer answers the feature-type listing of such a workspace with an empty string instead of
 * an empty object. {@link GeoServerSagaHandlerTest} asserts how the handler reads that body; only a
 * real server shows that GeoServer still sends it.
 *
 * <p>The handler runs in-process (no Kafka, no Flowable engine). The dataset id is chosen so the
 * derived workspace name equals the schema the test PostGIS container pre-seeds ({@code
 * dataset_test_uuid_001}), which the handler also uses as the datastore's schema.
 */
@TestInstance(Lifecycle.PER_CLASS)
class GeoServerSagaHandlerIT extends AbstractGeoServerIT {

  private static final String DATASET_ID = "dataset-test-uuid-001";
  private static final String WORKSPACE = TEST_SCHEMA; // == "dataset_test_uuid_001"
  private static final String DATASTORE = WORKSPACE + "_postgis";
  private static final String LAYER_POINT = "sensor_locations";

  private GeoServerSagaHandler handler;
  private OkHttpClient httpClient;
  private ObjectMapper objectMapper;
  private String basicAuthHeader;

  @BeforeAll
  void prepareClient() {
    httpClient = new OkHttpClient();
    objectMapper = new ObjectMapper();
    basicAuthHeader =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (ADMIN_USER + ":" + ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
  }

  @AfterAll
  void closeClient() {
    if (httpClient != null) {
      httpClient.dispatcher().executorService().shutdown();
      httpClient.connectionPool().evictAll();
    }
  }

  @BeforeEach
  void setUp() {
    deleteWorkspaceIfExists();

    Map<String, Object> props = new HashMap<>();
    props.put("geoserver.url", geoServerUrl());
    props.put("geoserver.public.url", geoServerUrl());
    props.put("geoserver.admin.user", ADMIN_USER);
    props.put("geoserver.admin.password", ADMIN_PASSWORD);
    props.put("geoserver.postgis.host", POSTGIS_HOST_ALIAS);
    props.put("geoserver.postgis.port", "5432");
    props.put("geoserver.postgis.database", POSTGIS_DB);
    props.put("geoserver.postgis.user", POSTGIS_USER);
    props.put("geoserver.postgis.password", POSTGIS_PASSWORD);

    handler = new GeoServerSagaHandler();
    handler.initialize(new AppConfig(new MapConfiguration(props)));
  }

  @AfterEach
  void tearDown() {
    deleteWorkspaceIfExists();
    if (handler != null) {
      handler.close();
    }
  }

  /**
   * Pins the response shape the unit-test mock encodes. A GeoServer version that answers an empty
   * collection with {@code {}} instead fails here, where the mock cannot show it.
   */
  @Test
  void geoServerListsAnEmptyFeatureTypeSetAsAnEmptyString() {
    assertStepCompleted(provision(List.of()));

    JsonNode listing = getJson(featureTypesPath());

    JsonNode featureTypes = listing.get("featureTypes");
    assertNotNull(featureTypes, "featureTypes key present");
    assertTrue(
        featureTypes.isTextual() && featureTypes.asText().isEmpty(),
        () -> "expected an empty string, got " + featureTypes.getNodeType() + ": " + featureTypes);
  }

  /** A dataset with a geographic sink and no map layer. */
  @Test
  void updateSucceedsWhenTheDatasetNeverHadAMapLayer() {
    assertStepCompleted(provision(List.of()));
    assertEquals(404, featureTypeStatus(LAYER_POINT), "no feature type is published");

    SagaCommandResult update = update(List.of());

    assertStepCompleted(update);
    assertEquals(
        List.of(),
        update.compensationData().get("previousFeatureTypes"),
        "an empty workspace yields an empty snapshot, not a failure");
  }

  @Test
  void updateSucceedsAfterTheLastFeatureTypeWasPruned() {
    assertStepCompleted(provision(List.of(layer(LAYER_POINT))));
    assertEquals(200, featureTypeStatus(LAYER_POINT), "the layer is published");

    // An empty desired set prunes every feature type the workspace still serves.
    assertStepCompleted(prune(List.of()));
    assertEquals(404, featureTypeStatus(LAYER_POINT), "the last feature type is gone");

    // The datastore stays, so the update now reads an empty feature-type listing.
    assertStepCompleted(update(List.of()));
  }

  @Test
  void pruneSucceedsWhenTheWorkspaceServesNoFeatureType() {
    assertStepCompleted(provision(List.of()));

    assertStepCompleted(prune(List.of()));
  }

  /**
   * Compensating an update of a dataset with no map layer. The recorded snapshot is empty and so is
   * the workspace, so restore deletes nothing and puts nothing back, but it still reads the
   * listing.
   */
  @Test
  void restoreSucceedsWhenTheWorkspaceServesNoFeatureType() {
    assertStepCompleted(provision(List.of()));

    SagaCommandResult update = update(List.of());
    assertStepCompleted(update);
    Object snapshot = update.compensationData().get("previousFeatureTypes");
    assertEquals(List.of(), snapshot, "the update recorded an empty snapshot");

    SagaCommandResult restore =
        handler.handle(
            command(
                "COMPENSATE_STEP",
                "RESTORE_WORKSPACE",
                Map.of("datasetId", DATASET_ID, "previousFeatureTypes", snapshot)));

    assertEquals(
        "COMPENSATION_COMPLETED", restore.type(), () -> "restore failed: " + restore.error());
    assertEquals(404, featureTypeStatus(LAYER_POINT), "restore published nothing");
  }

  // ── helpers: command construction ────────────────────────────────────────────

  private SagaCommandResult provision(List<Map<String, Object>> layers) {
    return handler.handle(
        command(
            "EXECUTE_STEP",
            "PROVISION_WORKSPACE",
            Map.of("datasetId", DATASET_ID, "layers", layers)));
  }

  private SagaCommandResult update(List<Map<String, Object>> layers) {
    return handler.handle(
        command(
            "EXECUTE_STEP", "UPDATE_WORKSPACE", Map.of("datasetId", DATASET_ID, "layers", layers)));
  }

  private SagaCommandResult prune(List<Map<String, Object>> layers) {
    return handler.handle(
        command(
            "EXECUTE_STEP",
            "PRUNE_FEATURE_TYPES",
            Map.of("datasetId", DATASET_ID, "layers", layers)));
  }

  private static Map<String, Object> layer(String layerName) {
    return Map.of("layerName", layerName, "crs", "EPSG:4326");
  }

  private static SagaCommandMessage command(
      String type, String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        type,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "step-" + operation,
        "geoserver",
        operation,
        payload);
  }

  private static void assertStepCompleted(SagaCommandResult result) {
    assertEquals("STEP_COMPLETED", result.type(), () -> "step failed: " + result.error());
  }

  // ── helpers: REST verification ───────────────────────────────────────────────

  private static String featureTypesPath() {
    return "/rest/workspaces/" + WORKSPACE + "/datastores/" + DATASTORE + "/featuretypes.json";
  }

  private int featureTypeStatus(String layer) {
    return statusOf(
        "/rest/workspaces/"
            + WORKSPACE
            + "/datastores/"
            + DATASTORE
            + "/featuretypes/"
            + layer
            + ".json");
  }

  private JsonNode getJson(String path) {
    Request request = authenticatedRequest(pathUrl(path)).get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      assertEquals(200, response.code(), () -> "GET " + path + " expected 200");
      return objectMapper.readTree(response.body().string());
    } catch (Exception e) {
      throw new AssertionError("Failed to parse JSON from " + path, e);
    }
  }

  private int statusOf(String path) {
    Request request = authenticatedRequest(pathUrl(path)).get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      return response.code();
    } catch (Exception e) {
      throw new AssertionError("Failed to query " + path, e);
    }
  }

  private static HttpUrl pathUrl(String path) {
    return HttpUrl.get(geoServerUrl())
        .newBuilder()
        .addPathSegments(path.replaceFirst("^/", ""))
        .build();
  }

  private Request.Builder authenticatedRequest(HttpUrl url) {
    return new Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("Authorization", basicAuthHeader);
  }

  private void deleteWorkspaceIfExists() {
    HttpUrl url =
        HttpUrl.get(geoServerUrl())
            .newBuilder()
            .addPathSegments("rest/workspaces/" + WORKSPACE)
            .addQueryParameter("recurse", "true")
            .build();
    Request request = authenticatedRequest(url).delete().build();
    try (Response response = httpClient.newCall(request).execute()) {
      int status = response.code();
      if (status != 200 && status != 404) {
        throw new AssertionError(
            "Workspace cleanup failed: HTTP " + status + " — " + response.body().string());
      }
    } catch (Exception e) {
      throw new AssertionError("Workspace cleanup failed", e);
    }
  }
}
