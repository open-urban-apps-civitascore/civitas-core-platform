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
                            "GEO_PERSISTENCE",
                            "configuration",
                            Map.of(
                                "tableName", "traffic_counts",
                                "crs", "EPSG:4326")))));

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
                Map.of("datasetId", "ds-existing", "datasinks", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
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
                Map.of("datasetId", "ds-fail", "datasinks", List.of()));

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
                Map.of("datasetId", "ds-net", "datasinks", List.of()));

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
                Map.of("workspaceName", "myws", "datasinks", List.of()));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    void treatsMissingSnapshotAsEmptyAndUpdatesSuccessfully() {
      try (GeoServerSagaHandler handler = createHandler()) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(mockBuilder.get()).thenReturn(notFound);
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(mockBuilder.post(any(Entity.class))).thenReturn(created);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_WORKSPACE",
                Map.of(
                    "workspaceName",
                    "myws",
                    "datasinks",
                    List.of(Map.of("configuration", Map.of("tableName", "t1")))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
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
    void returnsCompensationFailureOnRestoreError() {
      try (GeoServerSagaHandler handler = createHandler()) {
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
}
