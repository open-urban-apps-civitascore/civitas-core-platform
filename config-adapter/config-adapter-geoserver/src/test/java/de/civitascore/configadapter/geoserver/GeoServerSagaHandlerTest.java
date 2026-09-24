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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.util.PayloadConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeoServerSagaHandlerTest {

  private MockWebServer server;

  private static final String SLD = "<StyledLayerDescriptor version=\"1.0.0\"/>";
  private static final String SLD_CONTENT_TYPE = "application/vnd.ogc.sld+xml";

  @BeforeEach
  void startServer() throws IOException {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void stopServer() throws IOException {
    server.close();
  }

  @Test
  void adapterNameIsGeoserver() {
    try (GeoServerSagaHandler handler = createHandler()) {
      assertEquals("geoserver", handler.adapter());
    }
  }

  @Test
  void initializationWithoutCredentialsThrowsException() {
    try (GeoServerSagaHandler h = new GeoServerSagaHandler()) {
      AdapterConfig config = mock(AdapterConfig.class);
      when(config.getProperty("geoserver.url", "http://localhost:8080/geoserver"))
          .thenReturn("http://geoserver:8080/geoserver");
      when(config.getProperty("geoserver.public.url", "http://geoserver:8080/geoserver"))
          .thenReturn("http://geoserver:8080/geoserver");

      assertThrows(IllegalArgumentException.class, () -> h.initialize(config));
    }
  }

  @Test
  void toWorkspaceNameNormalizesDatasetId() {
    assertEquals("ds_123", GeoServerSagaHandler.toWorkspaceName("ds-123"));
    assertEquals("my_dataset_1", GeoServerSagaHandler.toWorkspaceName("My Dataset 1"));
    assertEquals("ds_abc123", GeoServerSagaHandler.toWorkspaceName("DS:ABC123"));
  }

  @Test
  void toWorkspaceNameThrowsForBlankDatasetId() {
    assertThrows(IllegalArgumentException.class, () -> GeoServerSagaHandler.toWorkspaceName(""));
    assertThrows(IllegalArgumentException.class, () -> GeoServerSagaHandler.toWorkspaceName("  "));
    assertThrows(IllegalArgumentException.class, () -> GeoServerSagaHandler.toWorkspaceName(null));
  }

  @Nested
  class FineGrainedSteps {

    @Test
    void createWorkspaceStepReturnsWorkspaceEndpoints() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // workspace
        server.enqueue(ok()); // workspace wms service settings

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "CREATE_WORKSPACE", Map.of("datasetId", "ds-abc")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds_abc", result.resultData().get("workspaceName"));
        assertNotNull(result.resultData().get("wfsUrl"));
        assertEquals("ds_abc", result.compensationData().get("workspaceName"));

        // The workspace is created isolated: reachable only via its virtual OWS services (matching
        // globalServices=false) with its own namespace, so the WMS service resolves its layers for
        // anonymous (APISIX-gated) requests and same-named layers across datasets don't collide.
        RecordedRequest workspaceRequest = server.takeRequest();
        assertEquals("POST", workspaceRequest.getMethod());
        assertEquals("/geoserver/rest/workspaces", workspaceRequest.getUrl().encodedPath());
        Map<String, Object> workspace = asMap(requestBodyAsMap(workspaceRequest).get("workspace"));
        assertEquals("ds_abc", workspace.get("name"));
        assertEquals(Boolean.TRUE, workspace.get("isolated"));

        // Only the per-workspace WMS service is enabled (and titled with the workspace name), so
        // the workspace shows up as a named service in map clients. WFS is deliberately not enabled
        // per workspace (flat feature-type list, and a REST-created WFSInfo has a null serviceLevel
        // that breaks WFS GetCapabilities).
        RecordedRequest wmsRequest = server.takeRequest();
        assertEquals("PUT", wmsRequest.getMethod());
        assertEquals(
            "/geoserver/rest/services/wms/workspaces/ds_abc/settings",
            wmsRequest.getUrl().encodedPath());
        Map<String, Object> svc = asMap(requestBodyAsMap(wmsRequest).get("wms"));
        assertEquals(Boolean.TRUE, svc.get("enabled"));
        assertEquals("ds_abc", svc.get("title"));
        assertEquals(2, server.getRequestCount());
      }
    }

    @Test
    void titlesWorkspaceServicesWithDatasetName() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // workspace
        server.enqueue(ok()); // workspace wms service settings

        // With a datasetName in the trigger, the workspace WMS/WFS services are titled with the
        // human-facing dataset name (not the technical workspace name).
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "CREATE_WORKSPACE",
                    Map.of("datasetId", "ds-abc", "datasetName", "Bewohnerparkzonen Bielefeld")));

        assertEquals("STEP_COMPLETED", result.type());
        server.takeRequest(); // workspace
        RecordedRequest wmsRequest = server.takeRequest();
        Map<String, Object> svc = asMap(requestBodyAsMap(wmsRequest).get("wms"));
        assertEquals("Bewohnerparkzonen Bielefeld", svc.get("title"));
      }
    }

    @Test
    void createDatastoreStepDerivesDatastoreName() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // datastore

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "CREATE_DATASTORE", Map.of("datasetId", "ds-abc")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds_abc_postgis", result.resultData().get("datastoreName"));
        assertEquals("ds_abc", result.compensationData().get("workspaceName"));
        assertEquals(1, server.getRequestCount());
        assertEquals("POST", server.takeRequest().getMethod());
      }
    }

    @Test
    void updatesDatastoreWhenAlreadyExists() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Datastore already exists (409) → its connection parameters are refreshed via PUT rather
        // than reporting success with stale config.
        server.enqueue(new MockResponse.Builder().code(409).build());
        server.enqueue(ok());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "CREATE_DATASTORE", Map.of("datasetId", "ds-abc")));

        assertEquals("STEP_COMPLETED", result.type());
        server.takeRequest();
        assertEquals("PUT", server.takeRequest().getMethod());
      }
    }

    @Test
    void createDatastoreReadsFromTheDatasetSchemaDerivedFromDatasetId()
        throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        // The datastore's schema connection parameter is the DataSet's dedicated schema, which is
        // the workspace name (both derive from datasetId) — so GeoServer reads from ds_abc, the
        // same schema PostGIS created the table in, instead of public. No sink config carries it.
        server.enqueue(created());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "CREATE_DATASTORE", Map.of("datasetId", "ds-abc")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds_abc_postgis", result.resultData().get("datastoreName"));
        assertEquals("ds_abc", datastoreConnectionParam(server.takeRequest(), "schema"));
      }
    }

    @Test
    void provisionLayersStepPublishesOneFeatureTypePerLayer() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // feature type 1
        server.enqueue(created()); // feature type 2

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "layers",
                        List.of(
                            Map.of("layerName", "traffic_counts", "crs", "EPSG:4326"),
                            Map.of("layerName", "speed_limits", "crs", "EPSG:25832")))));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds_abc", result.resultData().get("workspaceName"));
        assertEquals(2, server.getRequestCount());
        assertEquals("POST", server.takeRequest().getMethod());
        assertEquals("POST", server.takeRequest().getMethod());
      }
    }

    @Test
    void provisionLayersNeverPrunesWhatItWasNotAskedAbout() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Pruning on the provisioning path would drop live layers of a workspace that a re-release
        // is only topping up. PROVISION_LAYERS doesn't read a snapshot at all — no GET is issued.
        server.enqueue(created());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "layers",
                        List.of(Map.of("layerName", "traffic_counts", "crs", "EPSG:4326")))));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(1, server.getRequestCount());
      }
    }

    @Test
    void provisionLayersAcceptsAnAlreadyPublishedFeatureTypeReportedAsHttp500() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // A re-release republishes the feature types the unrelease left behind. Failing on them
        // makes provision-layers compensate, which drops the workspace and the PostGIS schema the
        // sink-preserving unrelease kept the data in.
        server.enqueue(
            new MockResponse.Builder()
                .code(500)
                .body("Resource named 'traffic_counts' already exists in store: 'ds'")
                .build());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "layers",
                        List.of(Map.of("layerName", "traffic_counts", "crs", "EPSG:4326")))));

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    void failsWhenLayerMissingLayerName() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // A requested layer without a layerName can't be published — the step must fail rather than
        // silently skip it and report success.
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of("datasetId", "ds-abc", "layers", List.of(Map.of("crs", "EPSG:4326")))));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    void usesSingleSinkTableAsNativeNameWhenLayerOmitsIt() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(
                            Map.of(
                                "type",
                                "POSTGIS",
                                "configuration",
                                Map.of("tableName", "traffic"))),
                        "layers",
                        List.of(Map.of("layerName", "roads")))));

        assertEquals("STEP_COMPLETED", result.type());
        // A layer without nativeName resolves to the single sink table, not its own layer name.
        Map<String, Object> featureType = postedFeatureType(server.takeRequest());
        assertEquals("roads", featureType.get("name"));
        assertEquals("traffic", featureType.get("nativeName"));
      }
    }

    @Test
    void derivesNativeCrsFromDataStructureGeometry() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(postgisSinkWithGeometry("roads", Map.of("geom", "EPSG:25832"))),
                        "layers",
                        List.of(Map.of("layerName", "roads", "crs", "EPSG:4326")))));

        assertEquals("STEP_COMPLETED", result.type());
        // Declared SRS from the layer, native CRS read from the geometry in the data structure —
        // the same source PostGIS uses for the column SRID.
        Map<String, Object> featureType = postedFeatureType(server.takeRequest());
        assertEquals("EPSG:4326", featureType.get("srs"));
        assertEquals("EPSG:25832", featureType.get("nativeCRS"));
      }
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwardsNativeBoundingBoxTaggedWithNativeCrsAndReprojectsLatLon()
        throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(postgisSinkWithGeometry("roads", Map.of("geom", "EPSG:25832"))),
                        "layers",
                        List.of(
                            Map.of(
                                "layerName",
                                "roads",
                                "crs",
                                "EPSG:4326",
                                "nativeBoundingBox",
                                Map.of(
                                    "minX", 239323.44,
                                    "minY", 4290145.58,
                                    "maxX", 761545.65,
                                    "maxY", 9365801.91,
                                    "crs", ""))))));

        assertEquals("STEP_COMPLETED", result.type());
        RecordedRequest request = server.takeRequest();
        // Native box is mapped to GeoServer field names and tagged with the resolved native CRS
        // (the portal box's own crs is ignored — it may be blank).
        Map<String, Object> bbox =
            (Map<String, Object>) postedFeatureType(request).get("nativeBoundingBox");
        assertEquals(239323.44, bbox.get("minx"));
        assertEquals(4290145.58, bbox.get("miny"));
        assertEquals(761545.65, bbox.get("maxx"));
        assertEquals(9365801.91, bbox.get("maxy"));
        assertEquals("EPSG:25832", bbox.get("crs"));
        // With a native box supplied, only the lat/lon box is reprojected — no data-driven
        // recompute.
        assertEquals("latlonbbox", request.getUrl().queryParameter("recalculate"));
      }
    }

    @Test
    void computesBothBoxesFromDataWhenNoNativeBoundingBoxGiven() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(postgisSinkWithGeometry("roads", Map.of("geom", "EPSG:25832"))),
                        "layers",
                        List.of(Map.of("layerName", "roads", "crs", "EPSG:4326")))));

        assertEquals("STEP_COMPLETED", result.type());
        RecordedRequest request = server.takeRequest();
        assertNull(postedFeatureType(request).get("nativeBoundingBox"));
        assertEquals("nativebbox,latlonbbox", request.getUrl().queryParameter("recalculate"));
      }
    }

    @Test
    void selectsGeometryByGeometryColumnRefWhenMultiple() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created());

        // Two geometry columns with different CRS; geometryColumnRef picks which one is published.
        LinkedHashMap<String, String> geometries = new LinkedHashMap<>();
        geometries.put("geom_a", "EPSG:25832");
        geometries.put("geom_b", "EPSG:3857");

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(postgisSinkWithGeometry("roads", geometries)),
                        "layers",
                        List.of(
                            Map.of(
                                "layerName", "roads",
                                "crs", "EPSG:4326",
                                "geometryColumnRef", "geom_b")))));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("EPSG:3857", postedFeatureType(server.takeRequest()).get("nativeCRS"));
      }
    }

    @Test
    void failsWhenMultipleGeometriesAndNoGeometryColumnRef() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Ambiguous: two geometry columns and no geometryColumnRef → fail rather than guess,
        // mirroring the nativeName handling across multiple sinks.
        LinkedHashMap<String, String> geometries = new LinkedHashMap<>();
        geometries.put("geom_a", "EPSG:25832");
        geometries.put("geom_b", "EPSG:3857");

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(postgisSinkWithGeometry("roads", geometries)),
                        "layers",
                        List.of(Map.of("layerName", "roads", "crs", "EPSG:4326")))));

        assertEquals("STEP_FAILED", result.type());
        assertTrue(result.error().contains("geometryColumnRef"), result.error());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    void fallsBackToDeclaredCrsWhenGeometryHasNoCrs() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created());

        // Geometry present but without an explicit crs — PostGIS defaults such a column to
        // EPSG:4326, so the native CRS falls back to the declared CRS (which is EPSG:4326 here).
        LinkedHashMap<String, String> geometries = new LinkedHashMap<>();
        geometries.put("geom", null);

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(postgisSinkWithGeometry("roads", geometries)),
                        "layers",
                        List.of(Map.of("layerName", "roads", "crs", "EPSG:4326")))));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("EPSG:4326", postedFeatureType(server.takeRequest()).get("nativeCRS"));
      }
    }

    @Test
    void fallsBackToDeclaredCrsWhenNoDataStructure() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created());

        // A POSTGIS sink without a data structure (nothing to derive from) → the native CRS mirrors
        // the declared CRS so the layer stays valid under REPROJECT_TO_DECLARED.
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(
                            Map.of(
                                "type", "POSTGIS", "configuration", Map.of("tableName", "roads"))),
                        "layers",
                        List.of(Map.of("layerName", "roads", "crs", "EPSG:25832")))));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("EPSG:25832", postedFeatureType(server.takeRequest()).get("nativeCRS"));
      }
    }

    @Test
    void failsWhenNativeNameAmbiguousAcrossMultipleSinks() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Two table sinks and a layer without nativeName → can't infer the table → fail (don't
        // silently collapse onto the first table and drop the second).
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "datasinks",
                        List.of(
                            Map.of("type", "POSTGIS", "configuration", Map.of("tableName", "t1")),
                            Map.of("type", "POSTGIS", "configuration", Map.of("tableName", "t2"))),
                        "layers",
                        List.of(Map.of("layerName", "roads")))));

        assertEquals("STEP_FAILED", result.type());
        assertTrue(result.error().contains("nativeName"), result.error());
      }
    }

    @Test
    void failsCleanlyWhenLayersElementIsNotAnObject() {
      try (GeoServerSagaHandler handler = createHandler()) {
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of("datasetId", "ds-abc", "layers", List.of(123))));

        assertEquals("STEP_FAILED", result.type());
        // A clean validation error, not a leaked ClassCastException.
        assertTrue(result.error().contains("must be"), result.error());
      }
    }

    @Test
    void failsCleanlyWhenLayerNameIsNotAString() {
      try (GeoServerSagaHandler handler = createHandler()) {
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of("datasetId", "ds-abc", "layers", List.of(Map.of("layerName", 123)))));

        assertEquals("STEP_FAILED", result.type());
        assertTrue(result.error().contains("must be a string"), result.error());
      }
    }

    @Test
    void failsCleanlyWhenDataSinkElementIsNotAnObject() {
      try (GeoServerSagaHandler handler = createHandler()) {
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId", "ds-abc",
                        "layers", List.of(Map.of("layerName", "t1")),
                        "datasinks", List.of("not-an-object"))));

        assertEquals("STEP_FAILED", result.type());
        // The datasinks list is also type-guarded (read via firstSinkTableName) — clean error.
        assertTrue(result.error().contains("must be"), result.error());
      }
    }
  }

  @Nested
  class Styles {

    @Test
    void provisionLayersUploadsStylesAndAssignsDefaultStyle() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // style upload
        server.enqueue(created()); // feature type
        server.enqueue(ok()); // layer PUT (style assignment)
        server.enqueue(
            jsonResponse(
                200,
                Map.of(
                    "layer",
                    Map.of("defaultStyle", Map.of("name", "ds_abc:civitas_default_point")))));

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "styles",
                        List.of(Map.of("name", "civitas_default_point", "sldContent", SLD)),
                        "layers",
                        List.of(
                            Map.of(
                                "layerName",
                                "sensor_locations",
                                "defaultStyle",
                                "civitas_default_point")))));

        assertEquals("STEP_COMPLETED", result.type());

        // SLD uploaded to .../styles?name=civitas_default_point with the SLD content type.
        RecordedRequest styleRequest = server.takeRequest();
        assertEquals(
            "/geoserver/rest/workspaces/ds_abc/styles", styleRequest.getUrl().encodedPath());
        assertEquals("civitas_default_point", styleRequest.getUrl().queryParameter("name"));
        assertTrue(styleRequest.getHeaders().get("Content-Type").startsWith(SLD_CONTENT_TYPE));

        server.takeRequest(); // feature type creation

        // Layer PUT assigns the workspace-qualified default style.
        RecordedRequest putRequest = server.takeRequest();
        Map<String, Object> layer = asMap(requestBodyAsMap(putRequest).get("layer"));
        Map<String, Object> defaultStyle = asMap(layer.get("defaultStyle"));
        assertEquals("ds_abc:civitas_default_point", defaultStyle.get("name"));
        assertEquals("ds_abc", defaultStyle.get("workspace"));
      }
    }

    @Test
    void provisionLayersAssignsAlternativeStyles() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // style 1
        server.enqueue(created()); // style 2
        server.enqueue(created()); // feature type
        server.enqueue(ok()); // layer PUT
        server.enqueue(
            jsonResponse(
                200,
                Map.of(
                    "layer",
                    Map.of(
                        "defaultStyle",
                        Map.of("name", "ds_abc:civitas_default_point"),
                        "styles",
                        Map.of("style", List.of(Map.of("name", "ds_abc:civitas_heat")))))));

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "styles",
                        List.of(
                            Map.of("name", "civitas_default_point", "sldContent", SLD),
                            Map.of("name", "civitas_heat", "sldContent", SLD)),
                        "layers",
                        List.of(
                            Map.of(
                                "layerName",
                                "sensor_locations",
                                "defaultStyle",
                                "civitas_default_point",
                                "alternativeStyles",
                                List.of("civitas_heat"))))));

        assertEquals("STEP_COMPLETED", result.type());

        server.takeRequest(); // style 1
        server.takeRequest(); // style 2
        server.takeRequest(); // feature type
        RecordedRequest putRequest = server.takeRequest();
        Map<String, Object> layer = asMap(requestBodyAsMap(putRequest).get("layer"));
        Map<String, Object> styles = asMap(layer.get("styles"));
        assertEquals("linked-hash-set", styles.get("@class"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> styleRefs = (List<Map<String, Object>>) styles.get("style");
        assertEquals("ds_abc:civitas_heat", styleRefs.get(0).get("name"));
      }
    }

    @Test
    void provisionLayersWithoutStylesSkipsStyleCalls() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // feature type only

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "layers",
                        List.of(Map.of("layerName", "sensor_locations")))));

        assertEquals("STEP_COMPLETED", result.type());
        // Backward-compatible: no SLD upload and no layer PUT when the payload carries no styles.
        assertEquals(1, server.getRequestCount());
        RecordedRequest request = server.takeRequest();
        assertFalse(request.getUrl().encodedPath().contains("/styles"));
        assertEquals("POST", request.getMethod());
      }
    }

    @Test
    void styleUpsertUpdatesSldWhenStyleExists() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        // GeoServer returns 403 (not 409) when a style of that name already exists → upsert via
        // PUT.
        server.enqueue(new MockResponse.Builder().code(403).build());
        server.enqueue(ok());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "styles",
                        List.of(Map.of("name", "civitas_default_point", "sldContent", SLD)))));

        assertEquals("STEP_COMPLETED", result.type());
        server.takeRequest(); // failed POST
        RecordedRequest putRequest = server.takeRequest();
        assertEquals("PUT", putRequest.getMethod());
        assertEquals(
            "/geoserver/rest/workspaces/ds_abc/styles/civitas_default_point",
            putRequest.getUrl().encodedPath());
      }
    }

    @Test
    void failsCleanlyWhenStylesIsNotAList() {
      assertStyleStepFailsWithoutHttp(Map.of("datasetId", "ds-abc", "styles", "not-a-list"));
    }

    @Test
    void failsWithClearErrorWhenStyleMissingName() {
      try (GeoServerSagaHandler handler = createHandler()) {
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of("datasetId", "ds-abc", "styles", List.of(Map.of("sldContent", SLD)))));

        assertEquals("STEP_FAILED", result.type());
        // A missing name reads as a missing-field error, not a "contains invalid characters" one.
        assertTrue(result.error().contains("missing the required field: name"), result.error());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    void failsCleanlyWhenSldContentBlank() {
      assertStyleStepFailsWithoutHttp(
          Map.of(
              "datasetId",
              "ds-abc",
              "styles",
              List.of(Map.of("name", "civitas_default_point", "sldContent", "   "))));
    }

    @Test
    void failsCleanlyWhenAlternativeStylesNotListOfStrings() {
      assertStyleStepFailsWithoutHttp(
          Map.of(
              "datasetId",
              "ds-abc",
              "layers",
              List.of(Map.of("layerName", "sensor_locations", "alternativeStyles", List.of(123)))));
    }

    @Test
    void invalidStyleReferenceFailsBeforeFeatureTypeIsCreated() {
      // A bad defaultStyle name must fail before any feature-type or style HTTP call (validated up
      // front, not inside the layer PUT).
      assertStyleStepFailsWithoutHttp(
          Map.of(
              "datasetId",
              "ds-abc",
              "layers",
              List.of(Map.of("layerName", "sensor_locations", "defaultStyle", "bad/name"))));
    }

    @Test
    void styleUploadFailsOnUnexpectedStatus() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Style POST returns a non-201/non-403 status → the step fails (not silently treated as
        // ok).
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "styles",
                        List.of(Map.of("name", "civitas_default_point", "sldContent", SLD)))));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    void layerStyleAssignmentFailsWhenReadbackShowsStyleNotApplied() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // style upload
        server.enqueue(created()); // feature type
        server.enqueue(ok()); // layer PUT
        // PUT returns 200 but GeoServer kept the generic style — the read-back must catch the
        // no-op.
        server.enqueue(
            jsonResponse(200, Map.of("layer", Map.of("defaultStyle", Map.of("name", "generic")))));

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "styles",
                        List.of(Map.of("name", "civitas_default_point", "sldContent", SLD)),
                        "layers",
                        List.of(
                            Map.of(
                                "layerName",
                                "sensor_locations",
                                "defaultStyle",
                                "civitas_default_point")))));

        assertEquals("STEP_FAILED", result.type());
        assertTrue(result.error().contains("was not applied"), result.error());
      }
    }

    @Test
    void provisionLayersAssignsAlternativeStylesInOrder() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // style 1
        server.enqueue(created()); // style 2
        server.enqueue(created()); // feature type
        server.enqueue(ok()); // layer PUT
        server.enqueue(
            jsonResponse(
                200,
                Map.of(
                    "layer",
                    Map.of(
                        "styles",
                        Map.of(
                            "style",
                            List.of(
                                Map.of("name", "ds_abc:civitas_heat"),
                                Map.of("name", "ds_abc:civitas_cool")))))));

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "styles",
                        List.of(
                            Map.of("name", "civitas_heat", "sldContent", SLD),
                            Map.of("name", "civitas_cool", "sldContent", SLD)),
                        "layers",
                        List.of(
                            Map.of(
                                "layerName",
                                "sensor_locations",
                                "alternativeStyles",
                                List.of("civitas_heat", "civitas_cool"))))));

        assertEquals("STEP_COMPLETED", result.type());
        server.takeRequest();
        server.takeRequest();
        server.takeRequest(); // feature type
        RecordedRequest putRequest = server.takeRequest();
        Map<String, Object> layer = asMap(requestBodyAsMap(putRequest).get("layer"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> styleRefs =
            (List<Map<String, Object>>) asMap(layer.get("styles")).get("style");
        assertEquals("ds_abc:civitas_heat", styleRefs.get(0).get("name"));
        assertEquals("ds_abc:civitas_cool", styleRefs.get(1).get("name"));
      }
    }

    @Test
    void readbackAcceptsSingleAlternativeStyleSerialisedAsObject() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // style upload
        server.enqueue(created()); // feature type
        server.enqueue(ok()); // layer PUT
        // GeoServer serialises a lone alternative style as an object, not a one-element array.
        server.enqueue(
            jsonResponse(
                200,
                Map.of(
                    "layer",
                    Map.of("styles", Map.of("style", Map.of("name", "ds_abc:civitas_heat"))))));

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "PROVISION_LAYERS",
                    Map.of(
                        "datasetId",
                        "ds-abc",
                        "styles",
                        List.of(Map.of("name", "civitas_heat", "sldContent", SLD)),
                        "layers",
                        List.of(
                            Map.of(
                                "layerName",
                                "sensor_locations",
                                "alternativeStyles",
                                List.of("civitas_heat"))))));

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    private void assertStyleStepFailsWithoutHttp(Map<String, Object> payload) {
      try (GeoServerSagaHandler handler = createHandler()) {
        SagaCommandResult result =
            handler.handle(createCommand("EXECUTE_STEP", "PROVISION_LAYERS", payload));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(0, server.getRequestCount());
      }
    }
  }

  @Nested
  class ProvisionWorkspace {

    @Test
    void createsWorkspaceDatastoreAndFeatureTypesSuccessfully() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // workspace
        server.enqueue(ok()); // workspace wms service settings
        server.enqueue(created()); // datastore
        server.enqueue(created()); // featuretype

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "PROVISION_WORKSPACE",
                Map.of(
                    "datasetId",
                    "ds-abc",
                    "datasetName",
                    "Test Dataset",
                    "datasinks",
                    List.of(
                        Map.of(
                            "type",
                            "POSTGIS",
                            "configuration",
                            Map.of(
                                "tableName", "traffic_counts",
                                "crs", "EPSG:4326"))),
                    "layers",
                    List.of(Map.of("layerName", "traffic_counts", "crs", "EPSG:4326"))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds_abc", result.resultData().get("workspaceName"));
        assertNotNull(result.resultData().get("wfsUrl"));
        assertNotNull(result.resultData().get("wmsUrl"));
        assertEquals("ds_abc", result.compensationData().get("workspaceName"));
      }
    }

    @Test
    void treatsDuplicateWorkspaceAs409Idempotent() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(409).build()); // workspace 409 = exists
        server.enqueue(created()); // datastore

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "PROVISION_WORKSPACE",
                Map.of("datasetId", "ds-existing", "layers", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    void treatsDuplicateWorkspaceReportedAsHttp500Idempotent() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(
            new MockResponse.Builder()
                .code(500)
                .body("Workspace 'ds_existing' already exists")
                .build());
        server.enqueue(created()); // datastore

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "PROVISION_WORKSPACE",
                Map.of("datasetId", "ds-existing", "layers", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        // Only a fresh 201 may configure the WMS service — doing it here would overwrite the
        // service title of a workspace that already serves data.
        assertEquals(2, server.getRequestCount());
        assertFalse(server.takeRequest().getUrl().encodedPath().contains("/services/wms/"));
        assertFalse(server.takeRequest().getUrl().encodedPath().contains("/services/wms/"));
      }
    }

    @Test
    void failsWhenWorkspaceCreationReturnsAnUnrelatedHttp500() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(
            new MockResponse.Builder().code(500).body("java.lang.NullPointerException").build());

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "PROVISION_WORKSPACE",
                Map.of("datasetId", "ds-broken", "layers", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    void createsWorkspaceAndDatastoreButNoFeatureTypesWhenNoLayers() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(created()); // workspace
        server.enqueue(ok()); // workspace wms service settings
        server.enqueue(created()); // datastore

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "PROVISION_WORKSPACE",
                Map.of("datasetId", "ds-skip", "layers", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        // Only workspace + datastore are created when there are no layers to publish.
        assertEquals(3, server.getRequestCount());
      }
    }

    @Test
    void returnsFailureWhenWorkspaceCreationFails() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "PROVISION_WORKSPACE",
                Map.of("datasetId", "ds-fail", "layers", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    void returnsFailureOnNetworkError() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Torn down before any request is made: the connection attempt itself fails.
        server.close();

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "PROVISION_WORKSPACE",
                Map.of("datasetId", "ds-net", "layers", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  @Nested
  class UpdateWorkspace {

    @Test
    void returnsFailureWhenSnapshotReadFails() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_WORKSPACE",
                Map.of("workspaceName", "myws", "layers", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    void succeedsWhenTheWorkspaceServesNoFeatureType() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // A workspace with a datastore and no published feature type is a normal state: a dataset
        // may have a geographic sink and no map layer, and the clean-up step leaves one behind.
        server.enqueue(emptySnapshotResponse());
        server.enqueue(created()); // workspace
        server.enqueue(ok()); // workspace wms service settings
        server.enqueue(created()); // datastore
        server.enqueue(created()); // featuretype

        SagaCommandResult result =
            handler.handle(createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1")));

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
        assertEquals(List.of(), result.compensationData().get("previousFeatureTypes"));
      }
    }

    @Test
    void failsWhenTheFeatureTypesWrapperCannotBeRead() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // An unreadable listing must not pass as an empty workspace: compensation deletes every
        // feature type absent from the snapshot, so a wrong empty unpublishes the whole dataset.
        server.enqueue(unexpectedShapeSnapshotResponse());

        SagaCommandResult result =
            handler.handle(createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1")));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    void failsWhenTheFeatureTypeEntryListCannotBeRead() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // The wrapper is a map, but its entry list is not a list — as unreadable as a bad wrapper.
        server.enqueue(unexpectedEntryShapeSnapshotResponse());

        SagaCommandResult result =
            handler.handle(createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1")));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    void provisionsWorkspaceAndDatastoreWhenMissingThenPublishesLayers() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Workspace not provisioned yet → the feature-types snapshot read returns 404 (empty).
        server.enqueue(new MockResponse.Builder().code(404).build());
        server.enqueue(created()); // workspace
        server.enqueue(ok()); // workspace wms service settings
        server.enqueue(created()); // datastore
        server.enqueue(created()); // featuretype

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
        // UPDATE must create the workspace + datastore (not just the feature type) when they don't
        // exist yet — otherwise the feature-type POST would 404 on first-time geo provisioning.
        assertEquals(5, server.getRequestCount());
      }
    }

    @Test
    void returnsFailureWhenWorkspaceProvisioningFailsOnUpdate() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(404).build());
        // The workspace POST fails (not 201/409) → UPDATE must fail, not silently skip
        // provisioning.
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    void updatesExistingFeatureTypeViaPutOn409() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(snapshotResponse("t1"));
        server.enqueue(new MockResponse.Builder().code(409).build()); // workspace exists
        server.enqueue(new MockResponse.Builder().code(409).build()); // datastore exists
        server.enqueue(ok()); // datastore update PUT
        server.enqueue(new MockResponse.Builder().code(409).build()); // feature type exists
        server.enqueue(ok()); // feature type update PUT

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        // Both the existing datastore and the existing feature type return 409 on POST and must be
        // updated via PUT (not silently ignored), so the workspace converges to the desired config.
        assertEquals(6, server.getRequestCount());
      }
    }

    @Test
    void returnsFailureWhenFeatureTypeUpdateFails() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(snapshotResponse("t1"));
        server.enqueue(new MockResponse.Builder().code(409).build()); // workspace exists
        server.enqueue(new MockResponse.Builder().code(409).build()); // datastore exists
        server.enqueue(ok()); // datastore update PUT
        server.enqueue(new MockResponse.Builder().code(409).build()); // feature type exists
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    void updateWorkspaceLeavesStaleFeatureTypesToThePruneStep() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // A delete here would sit in the compensable window, where RESTORE_WORKSPACE cannot undo
        // it.
        server.enqueue(snapshotResponse("t1", "removed_layer"));
        server.enqueue(new MockResponse.Builder().code(409).build()); // workspace exists
        server.enqueue(new MockResponse.Builder().code(409).build()); // datastore exists
        server.enqueue(ok()); // datastore update PUT
        server.enqueue(new MockResponse.Builder().code(409).build()); // feature type exists
        server.enqueue(ok()); // feature type update PUT

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNoDeleteRequests();
      }
    }

    @Test
    void keepsPublishedFeatureTypesTheUpdateStillAsksFor() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(snapshotResponse("t1"));
        server.enqueue(new MockResponse.Builder().code(409).build()); // workspace exists
        server.enqueue(new MockResponse.Builder().code(409).build()); // datastore exists
        server.enqueue(ok()); // datastore update PUT
        server.enqueue(new MockResponse.Builder().code(409).build()); // feature type exists
        server.enqueue(ok()); // feature type update PUT

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(6, server.getRequestCount());
      }
    }

    @Test
    void updatesFeatureTypeWhenGeoServerReportsTheConflictAsHttp500() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Otherwise every metadata edit on a released dataset with layers fails, since an update
        // re-publishes all of them.
        server.enqueue(snapshotResponse("t1"));
        server.enqueue(new MockResponse.Builder().code(409).build()); // workspace precedes it
        server.enqueue(new MockResponse.Builder().code(409).build()); // datastore precedes it
        server.enqueue(ok()); // datastore update PUT
        server.enqueue(
            new MockResponse.Builder()
                .code(500)
                .body("Resource named 't1' already exists in store: 'ds'")
                .build());
        server.enqueue(ok()); // feature type update PUT

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    void failsWhenAFeatureTypePostReturnsAnUnrelatedHttp500() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(snapshotResponse("t1"));
        server.enqueue(new MockResponse.Builder().code(409).build()); // workspace exists
        server.enqueue(new MockResponse.Builder().code(409).build()); // datastore exists
        server.enqueue(ok()); // datastore update PUT
        server.enqueue(
            new MockResponse.Builder().code(500).body("java.lang.NullPointerException").build());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }
  }

  @Nested
  class PruneFeatureTypes {

    @Test
    void deletesPublishedFeatureTypesTheDatasetNoLongerHasALayerFor() throws InterruptedException {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(snapshotResponse("t1", "removed_layer"));
        server.enqueue(ok()); // delete removed_layer

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "PRUNE_FEATURE_TYPES", updatePayload("t1")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(2, server.getRequestCount());
        server.takeRequest();
        RecordedRequest deleteRequest = server.takeRequest();
        assertEquals("DELETE", deleteRequest.getMethod());
        assertTrue(
            deleteRequest.getUrl().encodedPath().endsWith("/featuretypes/removed_layer"),
            "the stale feature type must be the one deleted");
      }
    }

    @Test
    void deletesNothingWhenTheWorkspaceServesNoFeatureType() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(emptySnapshotResponse());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "PRUNE_FEATURE_TYPES", updatePayload("t1")));

        assertEquals("STEP_COMPLETED", result.type());
        assertNoDeleteRequests();
      }
    }

    @Test
    void keepsFeatureTypesTheDatasetStillHasALayerFor() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(snapshotResponse("t1"));

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "PRUNE_FEATURE_TYPES", updatePayload("t1")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(1, server.getRequestCount());
      }
    }

    @Test
    void reportsFeatureTypesItCouldNotUnpublishAndPrunesTheRest() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Failing the step would report an applied update as failed; staying silent would leave the
        // layer served with nothing in the portal able to see it.
        server.enqueue(snapshotResponse("t1", "stuck", "removable"));
        server.enqueue(new MockResponse.Builder().code(500).build());
        server.enqueue(ok());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "PRUNE_FEATURE_TYPES", updatePayload("t1")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(List.of("stuck"), result.resultData().get("staleFeatureTypes"));
        assertEquals(3, server.getRequestCount());
      }
    }

    @Test
    void prunesEveryFeatureTypeWhenTheDatasetHasNoLayersLeft() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // An empty desired set must prune, not be treated as "unknown" and skipped.
        server.enqueue(snapshotResponse("t1", "t2"));
        server.enqueue(ok());
        server.enqueue(ok());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP", "PRUNE_FEATURE_TYPES", Map.of("datasetId", "ds-abc")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(3, server.getRequestCount());
      }
    }
  }

  @Nested
  class DeleteWorkspace {

    @Test
    void deletesWorkspaceSuccessfully() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(ok());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "DELETE_WORKSPACE", Map.of("workspaceName", "myws"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
      }
    }

    @Test
    void derivesWorkspaceFromDatasetIdWhenNotGiven() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(404).build());

        // No explicit workspaceName: a 404 (nothing to delete) is idempotent success. This is the
        // path taken when the delete saga's compensate step runs but no GeoServer state exists.
        SagaCommandMessage command =
            createCommand("COMPENSATE_STEP", "DELETE_WORKSPACE", Map.of("datasetId", "ds-gone"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
      }
    }

    @Test
    void treatsNotFoundAs404Idempotent() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(404).build());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "DELETE_WORKSPACE", Map.of("workspaceName", "gone"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    void returnsCompensationCompletedWhenUsedAsCompensation() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(ok());

        SagaCommandMessage command =
            createCommand("COMPENSATE_STEP", "DELETE_WORKSPACE", Map.of("workspaceName", "myws"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
      }
    }

    @Test
    void returnsFailureOnServerError() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "DELETE_WORKSPACE", Map.of("workspaceName", "myws"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  @Nested
  class RestoreWorkspace {

    @Test
    void skipsRestoreWhenNoSnapshotPresent() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // No previousFeatureTypes key: the UPDATE step failed before storing compensation data.
        // Restore must skip — not treat the missing snapshot as "empty" and delete existing types.
        SagaCommandMessage command =
            createCommand("COMPENSATE_STEP", "RESTORE_WORKSPACE", Map.of("workspaceName", "myws"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    void restoresNothingWhenTheWorkspaceServesNoFeatureType() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // An update of a layerless dataset records an empty snapshot, so compensation has nothing
        // to delete and nothing to put back.
        server.enqueue(emptySnapshotResponse());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_WORKSPACE",
                Map.of("workspaceName", "myws", "previousFeatureTypes", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertNull(result.error());
        assertEquals(1, server.getRequestCount());
      }
    }

    @Test
    void restoresPreviousFeatureTypesSuccessfully() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Snapshot matches the previous state — nothing new to delete, only restore via PUT.
        server.enqueue(snapshotResponse("traffic_counts"));
        server.enqueue(ok());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_WORKSPACE",
                Map.of(
                    "workspaceName",
                    "myws",
                    "previousFeatureTypes",
                    List.of(Map.of("name", "traffic_counts", "srs", "EPSG:4326"))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertNull(result.error());
      }
    }

    @Test
    void deletesNewlyCreatedFeatureTypesOnRestore() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Snapshot has a feature type ("new_table") absent from the previous state — compensation
        // must delete it, then restore the previous one.
        server.enqueue(snapshotResponse("traffic_counts", "new_table"));
        server.enqueue(ok()); // delete new_table
        server.enqueue(ok()); // restore traffic_counts

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_WORKSPACE",
                Map.of(
                    "workspaceName",
                    "myws",
                    "previousFeatureTypes",
                    List.of(Map.of("name", "traffic_counts"))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals(3, server.getRequestCount());
      }
    }

    @Test
    void returnsCompensationFailureOnRestoreError() {
      try (GeoServerSagaHandler handler = createHandler()) {
        server.enqueue(snapshotResponse("traffic_counts"));
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_WORKSPACE",
                Map.of(
                    "workspaceName",
                    "myws",
                    "previousFeatureTypes",
                    List.of(Map.of("name", "traffic_counts"))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  @Nested
  class UnknownOperation {

    @Test
    void returnsStepFailedForUnknownForwardOperation() {
      try (GeoServerSagaHandler handler = createHandler()) {
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "UNKNOWN_OP", Map.of());
        SagaCommandResult result = handler.handle(command);
        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    void returnsCompensationFailedForUnknownCompensationOperation() {
      try (GeoServerSagaHandler handler = createHandler()) {
        SagaCommandMessage command = createCommand("COMPENSATE_STEP", "UNKNOWN_OP", Map.of());
        SagaCommandResult result = handler.handle(command);
        assertEquals("COMPENSATION_FAILED", result.type());
      }
    }
  }

  // ============== HELPERS ==============

  private GeoServerSagaHandler createHandler() {
    GeoServerSagaHandler handler = new GeoServerSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    String url = server.url("/geoserver").toString();
    when(mockConfig.getProperty("geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn(url);
    when(mockConfig.getProperty("geoserver.public.url", url)).thenReturn(url);
    when(mockConfig.getProperty("geoserver.admin.user")).thenReturn("admin");
    when(mockConfig.getProperty("geoserver.admin.password")).thenReturn("geoserver");
    when(mockConfig.getProperty("geoserver.postgis.host", "localhost")).thenReturn("localhost");
    when(mockConfig.getProperty("geoserver.postgis.port", "5432")).thenReturn("5432");
    when(mockConfig.getProperty("geoserver.postgis.database", "civitas_geo"))
        .thenReturn("civitas_geo");
    when(mockConfig.getProperty("geoserver.postgis.user")).thenReturn("geo_user");
    when(mockConfig.getProperty("geoserver.postgis.password")).thenReturn("secret");
    handler.initialize(mockConfig);
    return handler;
  }

  private SagaCommandMessage createCommand(
      String type, String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        type, "msg-001", "saga-001", "provision-workspace", "geoserver", operation, payload);
  }

  /** UPDATE_WORKSPACE payload with a single layer for the given feature type / table. */
  private static Map<String, Object> updatePayload(String layerName) {
    return Map.of(
        "workspaceName",
        "myws",
        "datasinks",
        List.of(Map.of("type", "POSTGIS", "configuration", Map.of("tableName", layerName))),
        "layers",
        List.of(Map.of("layerName", layerName, "crs", "EPSG:4326")));
  }

  /**
   * A POSTGIS data sink for {@code tableName} whose {@code dataStructure} defines the given
   * geometry columns (column name → CRS; a {@code null} CRS means the geometry declares none), plus
   * a scalar {@code id} column so the schema resolves as a normal table.
   */
  private static Map<String, Object> postgisSinkWithGeometry(
      String tableName, Map<String, String> geometryCrs) {
    LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
    properties.put("id", Map.of("type", "integer"));
    geometryCrs.forEach(
        (column, crs) -> {
          LinkedHashMap<String, Object> spec = new LinkedHashMap<>();
          spec.put("$ref", "https://geojson.org/schema/Point.json");
          if (crs != null) {
            spec.put("crs", crs);
          }
          properties.put(column, spec);
        });
    return Map.of(
        "type", "POSTGIS",
        "configuration", Map.of("tableName", tableName),
        "dataStructure", Map.of("properties", properties));
  }

  /** The {@code featureType} object from a captured feature-type POST/PUT body. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> postedFeatureType(RecordedRequest request) {
    Map<String, Object> body = requestBodyAsMap(request);
    return (Map<String, Object>) body.get("featureType");
  }

  /** A 200 {@code featuretypes.json} response listing the given feature type names. */
  private static MockResponse snapshotResponse(String... names) {
    List<Map<String, Object>> featureTypes = new ArrayList<>();
    for (String name : names) {
      featureTypes.add(Map.of("name", name));
    }
    return jsonResponse(200, Map.of("featureTypes", Map.of("featureType", featureTypes)));
  }

  private static MockResponse jsonResponse(int code, Map<String, Object> body) {
    try {
      return new MockResponse.Builder()
          .code(code)
          .body(PayloadConverter.objectMapper().writeValueAsString(body))
          .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * The 200 {@code featuretypes.json} response for a workspace that serves no feature type:
   * GeoServer answers an empty collection with an empty string, not an empty object.
   */
  private static MockResponse emptySnapshotResponse() {
    return jsonResponse(200, Map.of("featureTypes", ""));
  }

  /** A 200 {@code featuretypes.json} response whose {@code featureTypes} has an odd shape. */
  private static MockResponse unexpectedShapeSnapshotResponse() {
    return jsonResponse(200, Map.of("featureTypes", List.of("t1")));
  }

  /** A 200 {@code featuretypes.json} response whose {@code featureType} is not a list. */
  private static MockResponse unexpectedEntryShapeSnapshotResponse() {
    return jsonResponse(200, Map.of("featureTypes", Map.of("featureType", Map.of("name", "t1"))));
  }

  private static MockResponse created() {
    return new MockResponse.Builder().code(201).build();
  }

  private static MockResponse ok() {
    return new MockResponse.Builder().code(200).build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  /** Drains every recorded request so far and asserts none of them was a DELETE. */
  private void assertNoDeleteRequests() {
    int count = server.getRequestCount();
    for (int i = 0; i < count; i++) {
      try {
        assertFalse("DELETE".equals(server.takeRequest().getMethod()));
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static Map<String, Object> requestBodyAsMap(RecordedRequest request) {
    try {
      return PayloadConverter.readMap(request.getBody().toByteArray());
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /**
   * The value of a connection parameter (e.g. {@code schema}) from a captured datastore POST/PUT
   * body.
   */
  @SuppressWarnings("unchecked")
  private static String datastoreConnectionParam(RecordedRequest request, String key) {
    Map<String, Object> body = requestBodyAsMap(request);
    Map<String, Object> dataStore = (Map<String, Object>) body.get("dataStore");
    Map<String, Object> connectionParameters =
        (Map<String, Object>) dataStore.get("connectionParameters");
    List<Map<String, Object>> entries =
        (List<Map<String, Object>>) connectionParameters.get("entry");
    return entries.stream()
        .filter(entry -> key.equals(entry.get("@key")))
        .map(entry -> (String) entry.get("$"))
        .findFirst()
        .orElse(null);
  }
}
