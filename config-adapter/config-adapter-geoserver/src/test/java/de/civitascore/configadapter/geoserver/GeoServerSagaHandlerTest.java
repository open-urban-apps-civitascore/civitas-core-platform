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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeoServerSagaHandlerTest {

  private Invocation.Builder mockBuilder;
  private WebTarget mockTarget;
  private WebTarget mockPathTarget;

  private static final String SLD = "<StyledLayerDescriptor version=\"1.0.0\"/>";
  private static final String SLD_CONTENT_TYPE = "application/vnd.ogc.sld+xml";

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
    void createWorkspaceStepReturnsWorkspaceEndpoints() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(1)).post(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> workspace =
            (Map<String, Object>)
                ((Map<String, Object>) captor.getValue().getEntity()).get("workspace");
        assertEquals("ds_abc", workspace.get("name"));
        assertEquals(Boolean.TRUE, workspace.get("isolated"));

        // The per-workspace WMS and WFS services are enabled and titled with the workspace name, so
        // the workspace shows up as a named service in map clients (between connection and layer).
        assertTrue(capturedPaths().contains("/rest/services/wms/workspaces/ds_abc/settings"));
        assertTrue(capturedPaths().contains("/rest/services/wfs/workspaces/ds_abc/settings"));
        ArgumentCaptor<Entity> putCaptor = ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(2)).put(putCaptor.capture());
        for (Entity entity : putCaptor.getAllValues()) {
          @SuppressWarnings("unchecked")
          Map<String, Object> body = (Map<String, Object>) entity.getEntity();
          @SuppressWarnings("unchecked")
          Map<String, Object> svc = (Map<String, Object>) body.getOrDefault("wms", body.get("wfs"));
          assertEquals(Boolean.TRUE, svc.get("enabled"));
          assertEquals("ds_abc", svc.get("title"));
        }
      }
    }

    @Test
    void createDatastoreStepDerivesDatastoreName() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "CREATE_DATASTORE", Map.of("datasetId", "ds-abc")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds_abc_postgis", result.resultData().get("datastoreName"));
        assertEquals("ds_abc", result.compensationData().get("workspaceName"));
        verify(mockBuilder, times(1)).post(any(Entity.class));
      }
    }

    @Test
    void updatesDatastoreWhenAlreadyExists() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Datastore already exists (409) → its connection parameters are refreshed via PUT rather
        // than reporting success with stale config.
        Response conflict = mock(Response.class);
        when(conflict.getStatus()).thenReturn(409);
        when(mockBuilder.post(any(Entity.class))).thenReturn(conflict);
        Response updated = mock(Response.class);
        when(updated.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(updated);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "CREATE_DATASTORE", Map.of("datasetId", "ds-abc")));

        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder).put(any(Entity.class));
      }
    }

    @Test
    void createDatastoreReadsFromTheDatasetSchemaDerivedFromDatasetId() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // The datastore's schema connection parameter is the DataSet's dedicated schema, which is
        // the workspace name (both derive from datasetId) — so GeoServer reads from ds_abc, the
        // same schema PostGIS created the table in, instead of public. No sink config carries it.
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "CREATE_DATASTORE", Map.of("datasetId", "ds-abc")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds_abc_postgis", result.resultData().get("datastoreName"));
        assertEquals("ds_abc", datastoreConnectionParam("schema"));
      }
    }

    @Test
    void provisionLayersStepPublishesOneFeatureTypePerLayer() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        verify(mockBuilder, times(2)).post(any(Entity.class));
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
        verify(mockBuilder, times(0)).post(any(Entity.class));
      }
    }

    @Test
    void usesSingleSinkTableAsNativeNameWhenLayerOmitsIt() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder).post(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> featureType =
            (Map<String, Object>)
                ((Map<String, Object>) captor.getValue().getEntity()).get("featureType");
        assertEquals("roads", featureType.get("name"));
        assertEquals("traffic", featureType.get("nativeName"));
      }
    }

    @Test
    void derivesNativeCrsFromDataStructureGeometry() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        Map<String, Object> featureType = postedFeatureType();
        assertEquals("EPSG:4326", featureType.get("srs"));
        assertEquals("EPSG:25832", featureType.get("nativeCRS"));
      }
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwardsNativeBoundingBoxTaggedWithNativeCrsAndReprojectsLatLon() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        // Native box is mapped to GeoServer field names and tagged with the resolved native CRS
        // (the portal box's own crs is ignored — it may be blank).
        Map<String, Object> bbox =
            (Map<String, Object>) postedFeatureType().get("nativeBoundingBox");
        assertEquals(239323.44, bbox.get("minx"));
        assertEquals(4290145.58, bbox.get("miny"));
        assertEquals(761545.65, bbox.get("maxx"));
        assertEquals(9365801.91, bbox.get("maxy"));
        assertEquals("EPSG:25832", bbox.get("crs"));
        // With a native box supplied, only the lat/lon box is reprojected — no data-driven
        // recompute.
        verify(mockPathTarget).queryParam("recalculate", "latlonbbox");
      }
    }

    @Test
    void computesBothBoxesFromDataWhenNoNativeBoundingBoxGiven() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        assertNull(postedFeatureType().get("nativeBoundingBox"));
        verify(mockPathTarget).queryParam("recalculate", "nativebbox,latlonbbox");
      }
    }

    @Test
    void selectsGeometryByGeometryColumnRefWhenMultiple() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        assertEquals("EPSG:3857", postedFeatureType().get("nativeCRS"));
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
        verify(mockBuilder, times(0)).post(any(Entity.class));
      }
    }

    @Test
    void fallsBackToDeclaredCrsWhenGeometryHasNoCrs() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        assertEquals("EPSG:4326", postedFeatureType().get("nativeCRS"));
      }
    }

    @Test
    void fallsBackToDeclaredCrsWhenNoDataStructure() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        assertEquals("EPSG:25832", postedFeatureType().get("nativeCRS"));
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
    void provisionLayersUploadsStylesAndAssignsDefaultStyle() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);
        Response assigned = mock(Response.class);
        when(assigned.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(assigned);
        stubLayerReadback(Map.of("defaultStyle", Map.of("name", "ds_abc:civitas_default_point")));

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
        assertTrue(capturedPaths().contains("/rest/workspaces/ds_abc/styles"));
        verify(mockPathTarget).queryParam("name", "civitas_default_point");
        assertTrue(postedSldContentType());

        // Layer PUT assigns the workspace-qualified default style.
        Map<String, Object> layer = capturedLayerPutBody();
        Map<String, Object> defaultStyle = asMap(layer.get("defaultStyle"));
        assertEquals("ds_abc:civitas_default_point", defaultStyle.get("name"));
        assertEquals("ds_abc", defaultStyle.get("workspace"));
      }
    }

    @Test
    void provisionLayersAssignsAlternativeStyles() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);
        Response assigned = mock(Response.class);
        when(assigned.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(assigned);
        stubLayerReadback(
            Map.of(
                "defaultStyle",
                Map.of("name", "ds_abc:civitas_default_point"),
                "styles",
                Map.of("style", List.of(Map.of("name", "ds_abc:civitas_heat")))));

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

        Map<String, Object> layer = capturedLayerPutBody();
        Map<String, Object> styles = asMap(layer.get("styles"));
        assertEquals("linked-hash-set", styles.get("@class"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> styleRefs = (List<Map<String, Object>>) styles.get("style");
        assertEquals("ds_abc:civitas_heat", styleRefs.get(0).get("name"));
      }
    }

    @Test
    void provisionLayersWithoutStylesSkipsStyleCalls() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

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
        assertTrue(capturedPaths().stream().noneMatch(path -> path.contains("/styles")));
        verify(mockBuilder, never()).put(any(Entity.class));
      }
    }

    @Test
    void styleUpsertUpdatesSldWhenStyleExists() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // GeoServer returns 403 (not 409) when a style of that name already exists → upsert via
        // PUT.
        Response conflict = mock(Response.class);
        when(conflict.getStatus()).thenReturn(403);
        when(mockBuilder.post(any(Entity.class))).thenReturn(conflict);
        Response updated = mock(Response.class);
        when(updated.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(updated);

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
        assertTrue(
            capturedPaths().contains("/rest/workspaces/ds_abc/styles/civitas_default_point"));
        verify(mockBuilder).put(any(Entity.class));
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
        verify(mockBuilder, never()).post(any(Entity.class));
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
        Response error = mock(Response.class);
        when(error.getStatus()).thenReturn(500);
        when(error.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.post(any(Entity.class))).thenReturn(error);

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
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);
        Response assigned = mock(Response.class);
        when(assigned.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(assigned);
        // PUT returns 200 but GeoServer kept the generic style — the read-back must catch the
        // no-op.
        stubLayerReadback(Map.of("defaultStyle", Map.of("name", "generic")));

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
    void provisionLayersAssignsAlternativeStylesInOrder() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);
        Response assigned = mock(Response.class);
        when(assigned.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(assigned);
        stubLayerReadback(
            Map.of(
                "styles",
                Map.of(
                    "style",
                    List.of(
                        Map.of("name", "ds_abc:civitas_heat"),
                        Map.of("name", "ds_abc:civitas_cool")))));

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
        Map<String, Object> layer = capturedLayerPutBody();
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
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);
        Response assigned = mock(Response.class);
        when(assigned.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(assigned);
        // GeoServer serialises a lone alternative style as an object, not a one-element array.
        stubLayerReadback(Map.of("styles", Map.of("style", Map.of("name", "ds_abc:civitas_heat"))));

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
        verify(mockBuilder, never()).post(any(Entity.class));
        verify(mockBuilder, never()).put(any(Entity.class));
      }
    }
  }

  @Nested
  class ProvisionWorkspace {

    @Test
    void createsWorkspaceDatastoreAndFeatureTypesSuccessfully() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response createResponse = mock(Response.class);
        when(createResponse.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class)))
            .thenReturn(createResponse) // workspace
            .thenReturn(createResponse) // datastore
            .thenReturn(createResponse); // featuretype

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
        Response conflictResponse = mock(Response.class);
        when(conflictResponse.getStatus()).thenReturn(409);

        Response createdResponse = mock(Response.class);
        when(createdResponse.getStatus()).thenReturn(201);

        when(mockBuilder.post(any(Entity.class)))
            .thenReturn(conflictResponse) // workspace 409 = already exists
            .thenReturn(createdResponse); // datastore

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
    void createsWorkspaceAndDatastoreButNoFeatureTypesWhenNoLayers() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "PROVISION_WORKSPACE",
                Map.of("datasetId", "ds-skip", "layers", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        // Only workspace + datastore are created when there are no layers to publish.
        verify(mockBuilder, times(2)).post(any(Entity.class));
      }
    }

    @Test
    void returnsFailureWhenWorkspaceCreationFails() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response errorResponse = mock(Response.class);
        when(errorResponse.getStatus()).thenReturn(500);
        when(errorResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.post(any(Entity.class))).thenReturn(errorResponse);

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
        when(mockBuilder.post(any(Entity.class)))
            .thenThrow(new ProcessingException("Connection refused"));

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
        Response errorResponse = mock(Response.class);
        when(errorResponse.getStatus()).thenReturn(500);
        when(errorResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.get()).thenReturn(errorResponse);

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
    void provisionsWorkspaceAndDatastoreWhenMissingThenPublishesLayers() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Workspace not provisioned yet → the feature-types snapshot read returns 404 (empty).
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(mockBuilder.get()).thenReturn(notFound);
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
        // UPDATE must create the workspace + datastore (not just the feature type) when they don't
        // exist yet — otherwise the feature-type POST would 404 on first-time geo provisioning.
        verify(mockBuilder, times(3)).post(any(Entity.class));
      }
    }

    @Test
    void returnsFailureWhenWorkspaceProvisioningFailsOnUpdate() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(mockBuilder.get()).thenReturn(notFound);
        // The workspace POST fails (not 201/409) → UPDATE must fail, not silently skip
        // provisioning.
        Response error = mock(Response.class);
        when(error.getStatus()).thenReturn(500);
        when(error.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.post(any(Entity.class))).thenReturn(error);

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
        Response snapshot = snapshotResponse("t1");
        when(mockBuilder.get()).thenReturn(snapshot);
        Response conflict = mock(Response.class);
        when(conflict.getStatus()).thenReturn(409);
        when(mockBuilder.post(any(Entity.class))).thenReturn(conflict);
        Response updated = mock(Response.class);
        when(updated.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(updated);

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        // Both the existing datastore and the existing feature type return 409 on POST and must be
        // updated via PUT (not silently ignored), so the workspace converges to the desired config.
        verify(mockBuilder, times(2)).put(any(Entity.class));
      }
    }

    @Test
    void returnsFailureWhenFeatureTypeUpdateFails() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response snapshot = snapshotResponse("t1");
        when(mockBuilder.get()).thenReturn(snapshot);
        Response conflict = mock(Response.class);
        when(conflict.getStatus()).thenReturn(409);
        when(mockBuilder.post(any(Entity.class))).thenReturn(conflict);
        Response error = mock(Response.class);
        when(error.getStatus()).thenReturn(500);
        when(error.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.put(any(Entity.class))).thenReturn(error);

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "UPDATE_WORKSPACE", updatePayload("t1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  @Nested
  class DeleteWorkspace {

    @Test
    void deletesWorkspaceSuccessfully() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

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
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(404);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

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
        Response notFoundResponse = mock(Response.class);
        when(notFoundResponse.getStatus()).thenReturn(404);
        when(mockBuilder.delete()).thenReturn(notFoundResponse);

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "DELETE_WORKSPACE", Map.of("workspaceName", "gone"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    void returnsCompensationCompletedWhenUsedAsCompensation() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

        SagaCommandMessage command =
            createCommand("COMPENSATE_STEP", "DELETE_WORKSPACE", Map.of("workspaceName", "myws"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
      }
    }

    @Test
    void returnsFailureOnServerError() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response errorResponse = mock(Response.class);
        when(errorResponse.getStatus()).thenReturn(500);
        when(errorResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.delete()).thenReturn(errorResponse);

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
        verify(mockBuilder, times(0)).get();
        verify(mockBuilder, times(0)).delete();
      }
    }

    @Test
    void restoresPreviousFeatureTypesSuccessfully() {
      try (GeoServerSagaHandler handler = createHandler()) {
        // Snapshot matches the previous state — nothing new to delete, only restore via PUT.
        Response snapshot = snapshotResponse("traffic_counts");
        when(mockBuilder.get()).thenReturn(snapshot);
        Response putResponse = mock(Response.class);
        when(putResponse.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResponse);

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
        Response snapshot = snapshotResponse("traffic_counts", "new_table");
        when(mockBuilder.get()).thenReturn(snapshot);
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(ok);
        when(mockBuilder.put(any(Entity.class))).thenReturn(ok);

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
        verify(mockBuilder).delete();
      }
    }

    @Test
    void returnsCompensationFailureOnRestoreError() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response snapshot = snapshotResponse("traffic_counts");
        when(mockBuilder.get()).thenReturn(snapshot);
        Response errorResponse = mock(Response.class);
        when(errorResponse.getStatus()).thenReturn(500);
        when(errorResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.put(any(Entity.class))).thenReturn(errorResponse);

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
    when(mockConfig.getProperty("geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn("http://geoserver:8080/geoserver");
    when(mockConfig.getProperty("geoserver.public.url", "http://geoserver:8080/geoserver"))
        .thenReturn("http://geoserver:8080/geoserver");
    when(mockConfig.getProperty("geoserver.admin.user")).thenReturn("admin");
    when(mockConfig.getProperty("geoserver.admin.password")).thenReturn("geoserver");
    when(mockConfig.getProperty("geoserver.postgis.host", "localhost")).thenReturn("localhost");
    when(mockConfig.getProperty("geoserver.postgis.port", "5432")).thenReturn("5432");
    when(mockConfig.getProperty("geoserver.postgis.database", "civitas_geo"))
        .thenReturn("civitas_geo");
    when(mockConfig.getProperty("geoserver.postgis.user")).thenReturn("geo_user");
    when(mockConfig.getProperty("geoserver.postgis.password")).thenReturn("secret");
    handler.initialize(mockConfig);

    Client mockClient = mock(Client.class);
    mockTarget = mock(WebTarget.class);
    mockPathTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);

    when(mockClient.target(any(String.class))).thenReturn(mockTarget);
    when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
    when(mockPathTarget.queryParam(any(String.class), any())).thenReturn(mockPathTarget);
    when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
    when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);
    // Lenient default so the best-effort per-workspace WMS/WFS service-settings PUTs (issued after
    // a
    // fresh workspace CREATE) don't NPE in tests that don't stub put themselves; tests that assert
    // specific put behaviour override this.
    Response okPut = mock(Response.class);
    when(okPut.getStatus()).thenReturn(200);
    when(mockBuilder.put(any(Entity.class))).thenReturn(okPut);

    handler.setTestClient(mockClient);
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

  /** The {@code featureType} object from the captured feature-type POST body. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Map<String, Object> postedFeatureType() {
    ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
    verify(mockBuilder, atLeastOnce()).post(captor.capture());
    for (Entity entity : captor.getAllValues()) {
      if (entity.getEntity() instanceof Map<?, ?> body && body.get("featureType") instanceof Map) {
        return (Map<String, Object>) ((Map<String, Object>) body).get("featureType");
      }
    }
    throw new AssertionError("no featureType POST captured");
  }

  /** Mocks a 200 {@code featuretypes.json} response listing the given feature type names. */
  private static Response snapshotResponse(String... names) {
    List<Map<String, Object>> featureTypes = new java.util.ArrayList<>();
    for (String name : names) {
      featureTypes.add(Map.of("name", name));
    }
    Response response = mock(Response.class);
    when(response.getStatus()).thenReturn(200);
    when(response.readEntity(Map.class))
        .thenReturn(Map.of("featureTypes", Map.of("featureType", featureTypes)));
    return response;
  }

  /** Stubs the layer GET that {@code assignLayerStyles} reads back to verify the styles applied. */
  private void stubLayerReadback(Map<String, Object> layer) {
    Response readback = mock(Response.class);
    when(readback.getStatus()).thenReturn(200);
    when(readback.readEntity(Map.class)).thenReturn(Map.of("layer", layer));
    when(mockBuilder.get()).thenReturn(readback);
  }

  /** All REST path segments the handler requested, in call order. */
  private List<String> capturedPaths() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(mockTarget, atLeastOnce()).path(captor.capture());
    return captor.getAllValues();
  }

  /** True if any POST body carried the SLD content type (i.e. a style upload happened). */
  @SuppressWarnings("rawtypes")
  private boolean postedSldContentType() {
    ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
    verify(mockBuilder, atLeastOnce()).post(captor.capture());
    return captor.getAllValues().stream()
        .anyMatch(entity -> SLD_CONTENT_TYPE.equals(entity.getMediaType().toString()));
  }

  /** The {@code layer} object from the last layer-assignment PUT body. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Map<String, Object> capturedLayerPutBody() {
    ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
    verify(mockBuilder, atLeastOnce()).put(captor.capture());
    List<Entity> puts = captor.getAllValues();
    Map<String, Object> body = (Map<String, Object>) puts.get(puts.size() - 1).getEntity();
    return (Map<String, Object>) body.get("layer");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  /**
   * The value of a connection parameter (e.g. {@code schema}) from the last datastore POST body.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private String datastoreConnectionParam(String key) {
    ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
    verify(mockBuilder, atLeastOnce()).post(captor.capture());
    Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
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
