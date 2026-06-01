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

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.owasp.encoder.Encode;

/**
 * Minimal saga command handler for GeoServer workspace provisioning. Handles workspace-level
 * operations dispatched by the saga orchestrator:
 *
 * <ul>
 *   <li>{@code PROVISION_WORKSPACE} — atomically creates workspace, PostGIS datastore, and feature
 *       types derived from the datasinks payload
 *   <li>{@code UPDATE_WORKSPACE} — creates or updates feature types; captures current state for
 *       compensation
 *   <li>{@code DELETE_WORKSPACE} — deletes workspace recursively (compensation for {@code
 *       PROVISION_WORKSPACE})
 *   <li>{@code RESTORE_WORKSPACE} — restores previous feature type state (compensation for {@code
 *       UPDATE_WORKSPACE})
 * </ul>
 *
 * <p>The SAGA implementation is intentionally minimal; the orchestration layer will be replaced by
 * Flowable. Connection parameters for the PostGIS datastore are read from adapter config: {@code
 * geoserver.postgis.host}, {@code .port}, {@code .database}, {@code .schema}, {@code .user}, {@code
 * .password}.
 */
public class GeoServerSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "geoserver";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080/geoserver";

  private static final String DEFAULT_POSTGIS_HOST = "localhost";
  private static final String DEFAULT_POSTGIS_PORT = "5432";
  private static final String DEFAULT_POSTGIS_DATABASE = "civitas_geo";
  private static final String DEFAULT_POSTGIS_SCHEMA = "public";

  private String serverUrl;
  private String publicUrl;
  private GeoServerAuth auth;

  private String postgisHost;
  private String postgisPort;
  private String postgisDatabase;
  private String postgisSchema;
  private String postgisUser;
  private String postgisPassword;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public GeoServerSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    this.serverUrl = getProperty("url", DEFAULT_SERVER_URL).replaceAll("/$", "");
    this.publicUrl = getProperty("public.url", this.serverUrl);
    String username = getProperty("admin.user");
    String password = getProperty("admin.password");
    this.auth = GeoServerAuth.create(username, password);

    this.postgisHost = getProperty("postgis.host", DEFAULT_POSTGIS_HOST);
    this.postgisPort = getProperty("postgis.port", DEFAULT_POSTGIS_PORT);
    this.postgisDatabase = getProperty("postgis.database", DEFAULT_POSTGIS_DATABASE);
    this.postgisSchema = getProperty("postgis.schema", DEFAULT_POSTGIS_SCHEMA);
    this.postgisUser = getProperty("postgis.user");
    this.postgisPassword = getProperty("postgis.password");

    log.info("GeoServerSagaHandler initialized for: {}", Encode.forJava(serverUrl));
  }

  void setTestClient(Client client) {
    super.setClient(client);
  }

  @Override
  protected SagaCommandResult doHandle(SagaCommandMessage command) {
    return switch (command.operation()) {
      case "PROVISION_WORKSPACE" -> handleProvisionWorkspace(command);
      case "UPDATE_WORKSPACE" -> handleUpdateWorkspace(command);
      case "DELETE_WORKSPACE" -> handleDeleteWorkspace(command);
      case "RESTORE_WORKSPACE" -> handleRestoreWorkspace(command);
      default -> unknownOperation(command);
    };
  }

  @SuppressWarnings("unchecked")
  private SagaCommandResult handleProvisionWorkspace(SagaCommandMessage command) {
    String datasetId = requireString(command, "datasetId");
    String workspaceName = toWorkspaceName(datasetId);
    String datastoreName = workspaceName + "_postgis";

    // 1. Create workspace (idempotent: 409 = already exists)
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path("/rest/workspaces")
                    .request(MediaType.APPLICATION_JSON))
            .post(Entity.json(Map.of("workspace", Map.of("name", workspaceName))))) {
      if (response.getStatus() != 201 && response.getStatus() != 409) {
        checkResponse(response, "PROVISION_WORKSPACE/create-workspace");
      }
    }

    // 2. Create PostGIS datastore (idempotent: 409 = already exists)
    Map<String, Object> datastoreBody = buildDatastoreBody(datastoreName);
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path("/rest/workspaces/" + workspaceName + "/datastores")
                    .request(MediaType.APPLICATION_JSON))
            .post(Entity.json(datastoreBody))) {
      if (response.getStatus() != 201 && response.getStatus() != 409) {
        checkResponse(response, "PROVISION_WORKSPACE/create-datastore");
      }
    }

    // 3. Create feature types from datasinks (idempotent: 409 = already exists)
    List<Map<String, Object>> datasinks =
        (List<Map<String, Object>>) command.payload().getOrDefault("datasinks", List.of());
    for (Map<String, Object> sink : datasinks) {
      Map<String, Object> configuration =
          (Map<String, Object>) sink.getOrDefault("configuration", Map.of());
      String tableName = (String) configuration.get("tableName");
      if (tableName != null) {
        createFeatureType(workspaceName, datastoreName, tableName, configuration);
      }
    }

    String wfsUrl = publicUrl + "/" + workspaceName + "/wfs";
    String wmsUrl = publicUrl + "/" + workspaceName + "/wms";

    log.info(
        "GeoServer workspace provisioned: workspaceName={}, saga={}",
        Encode.forJava(workspaceName),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(),
        command.stepId(),
        Map.of("workspaceName", workspaceName, "wfsUrl", wfsUrl, "wmsUrl", wmsUrl),
        Map.of("workspaceName", workspaceName));
  }

  @SuppressWarnings("unchecked")
  private SagaCommandResult handleUpdateWorkspace(SagaCommandMessage command) {
    String workspaceName = requireString(command, "workspaceName");
    String datastoreName = workspaceName + "_postgis";

    // Read current feature types for compensation
    List<Map<String, Object>> currentFeatureTypes =
        readCurrentFeatureTypes(workspaceName, datastoreName);

    // Create or update feature types from new datasinks
    List<Map<String, Object>> datasinks =
        (List<Map<String, Object>>) command.payload().getOrDefault("datasinks", List.of());
    for (Map<String, Object> sink : datasinks) {
      Map<String, Object> configuration =
          (Map<String, Object>) sink.getOrDefault("configuration", Map.of());
      String tableName = (String) configuration.get("tableName");
      if (tableName != null) {
        createFeatureType(workspaceName, datastoreName, tableName, configuration);
      }
    }

    String wfsUrl = publicUrl + "/" + workspaceName + "/wfs";
    String wmsUrl = publicUrl + "/" + workspaceName + "/wms";

    log.info(
        "GeoServer workspace updated: workspaceName={}, saga={}",
        Encode.forJava(workspaceName),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(),
        command.stepId(),
        Map.of("workspaceName", workspaceName, "wfsUrl", wfsUrl, "wmsUrl", wmsUrl),
        Map.of("workspaceName", workspaceName, "previousFeatureTypes", currentFeatureTypes));
  }

  private SagaCommandResult handleDeleteWorkspace(SagaCommandMessage command) {
    String workspaceName = requireString(command, "workspaceName");

    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path("/rest/workspaces/" + workspaceName)
                    .queryParam("recurse", "true")
                    .request(MediaType.APPLICATION_JSON))
            .delete()) {
      int status = response.getStatus();
      if (status != 200 && status != 404) {
        String body = response.readEntity(String.class);
        throw new SagaApiException(
            "DELETE_WORKSPACE failed: HTTP " + status + " — " + body, status);
      }
    }

    log.info(
        "GeoServer workspace deleted: workspaceName={}, saga={}",
        Encode.forJava(workspaceName),
        Encode.forJava(command.sagaId()));

    return "COMPENSATE_STEP".equals(command.type())
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  @SuppressWarnings("unchecked")
  private SagaCommandResult handleRestoreWorkspace(SagaCommandMessage command) {
    String workspaceName = requireString(command, "workspaceName");
    String datastoreName = workspaceName + "_postgis";
    List<Map<String, Object>> previousFeatureTypes =
        (List<Map<String, Object>>)
            command.payload().getOrDefault("previousFeatureTypes", List.of());

    for (Map<String, Object> ft : previousFeatureTypes) {
      String ftName = (String) ft.get("name");
      if (ftName == null) continue;

      try (Response response =
          auth.apply(
                  client()
                      .target(serverUrl)
                      .path(
                          "/rest/workspaces/"
                              + workspaceName
                              + "/datastores/"
                              + datastoreName
                              + "/featuretypes/"
                              + ftName)
                      .request(MediaType.APPLICATION_JSON))
              .put(Entity.json(Map.of("featureType", ft)))) {
        checkResponse(response, "RESTORE_WORKSPACE/featuretype/" + ftName);
      }
    }

    log.info(
        "GeoServer workspace restored: workspaceName={}, saga={}",
        Encode.forJava(workspaceName),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
  }

  // ============== HELPERS ==============

  private void createFeatureType(
      String workspaceName,
      String datastoreName,
      String tableName,
      Map<String, Object> configuration) {
    String crs = (String) configuration.getOrDefault("crs", "EPSG:4326");

    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(
                        "/rest/workspaces/"
                            + workspaceName
                            + "/datastores/"
                            + datastoreName
                            + "/featuretypes")
                    .request(MediaType.APPLICATION_JSON))
            .post(
                Entity.json(
                    Map.of(
                        "featureType",
                        Map.of(
                            "name", tableName,
                            "nativeName", tableName,
                            "title", tableName,
                            "srs", crs,
                            "projectionPolicy", "REPROJECT_TO_DECLARED"))))) {
      if (response.getStatus() != 201 && response.getStatus() != 409) {
        checkResponse(response, "create-featuretype/" + tableName);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> readCurrentFeatureTypes(
      String workspaceName, String datastoreName) {
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(
                        "/rest/workspaces/"
                            + workspaceName
                            + "/datastores/"
                            + datastoreName
                            + "/featuretypes.json")
                    .request(MediaType.APPLICATION_JSON))
            .get()) {
      if (response.getStatus() != 200) {
        return List.of();
      }
      Map<String, Object> result = response.readEntity(Map.class);
      Map<String, Object> featureTypes =
          (Map<String, Object>) result.getOrDefault("featureTypes", Map.of());
      Object ftList = featureTypes.get("featureType");
      if (ftList instanceof List) {
        return (List<Map<String, Object>>) ftList;
      }
      return List.of();
    }
  }

  private Map<String, Object> buildDatastoreBody(String datastoreName) {
    List<Map<String, Object>> entries = new ArrayList<>();
    entries.add(Map.of("@key", "host", "$", postgisHost));
    entries.add(Map.of("@key", "port", "$", postgisPort));
    entries.add(Map.of("@key", "database", "$", postgisDatabase));
    entries.add(Map.of("@key", "schema", "$", postgisSchema));
    entries.add(Map.of("@key", "user", "$", postgisUser != null ? postgisUser : ""));
    entries.add(Map.of("@key", "passwd", "$", postgisPassword != null ? postgisPassword : ""));
    entries.add(Map.of("@key", "dbtype", "$", "postgis"));
    entries.add(Map.of("@key", "Expose primary keys", "$", "true"));

    Map<String, Object> dataStore = new HashMap<>();
    dataStore.put("name", datastoreName);
    dataStore.put("type", "PostGIS");
    dataStore.put("connectionParameters", Map.of("entry", entries));

    return Map.of("dataStore", dataStore);
  }

  static String toWorkspaceName(String datasetId) {
    if (datasetId == null || datasetId.isBlank()) {
      throw new IllegalArgumentException("datasetId must not be blank");
    }
    return datasetId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
  }
}
