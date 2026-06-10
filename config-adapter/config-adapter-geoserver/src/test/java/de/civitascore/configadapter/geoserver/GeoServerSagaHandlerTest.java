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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeoServerSagaHandlerTest {

  private Invocation.Builder mockBuilder;

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
        verify(mockBuilder, times(1)).post(any(Entity.class));
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
        // Existing feature type (409 on POST) must be updated via PUT, not silently ignored.
        verify(mockBuilder).put(any(Entity.class));
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
    when(mockConfig.getProperty("geoserver.postgis.schema", "public")).thenReturn("public");
    when(mockConfig.getProperty("geoserver.postgis.user")).thenReturn("geo_user");
    when(mockConfig.getProperty("geoserver.postgis.password")).thenReturn("secret");
    handler.initialize(mockConfig);

    Client mockClient = mock(Client.class);
    WebTarget mockTarget = mock(WebTarget.class);
    WebTarget mockPathTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);

    when(mockClient.target(any(String.class))).thenReturn(mockTarget);
    when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
    when(mockPathTarget.queryParam(any(String.class), any())).thenReturn(mockPathTarget);
    when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
    when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

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
}
