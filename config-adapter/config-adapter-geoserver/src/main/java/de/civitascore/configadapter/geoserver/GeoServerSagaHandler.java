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
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

  private static final Pattern WORKSPACE_NAME_PATTERN = Pattern.compile("[a-z0-9_]+");
  private static final Pattern FEATURE_TYPE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

  /**
   * Only datasinks of this type are provisioned as GeoServer feature types. Matches the {@code
   * DataSinkType.POSTGIS} value the portal-backend emits in the saga trigger payload — a
   * PostGIS-backed sink is what GeoServer can publish as a WMS/WFS feature type.
   */
  private static final String DATASINK_TYPE_POSTGIS = "POSTGIS";

  private static final String DEFAULT_PROJECTION_POLICY = "REPROJECT_TO_DECLARED";

  /** Upper bound for HTTP error bodies echoed into saga errors/logs (may be large or sensitive). */
  private static final int MAX_ERROR_BODY_LENGTH = 500;

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
    this.serverUrl = getProperty("url", DEFAULT_SERVER_URL).replaceAll("/+$", "");
    this.publicUrl = getProperty("public.url", this.serverUrl);

    byte[] stretchedKey =
        CryptoKeyLoader.loadAndStretchKeyFromEnv(GeoServerCredentials.MASTER_KEY_ENV);
    if (stretchedKey.length == 0) {
      log.warn(
          "{} not set — encrypted GeoServer credentials cannot be decrypted",
          GeoServerCredentials.MASTER_KEY_ENV);
    }
    try {
      String username = getProperty("admin.user");
      String password = GeoServerCredentials.decrypt(getProperty("admin.password"), stretchedKey);
      this.auth = GeoServerAuth.create(username, password);

      this.postgisHost = getProperty("postgis.host", DEFAULT_POSTGIS_HOST);
      this.postgisPort = getProperty("postgis.port", DEFAULT_POSTGIS_PORT);
      this.postgisDatabase = getProperty("postgis.database", DEFAULT_POSTGIS_DATABASE);
      this.postgisSchema = getProperty("postgis.schema", DEFAULT_POSTGIS_SCHEMA);
      this.postgisUser = getProperty("postgis.user");
      this.postgisPassword =
          GeoServerCredentials.decrypt(getProperty("postgis.password"), stretchedKey);
    } finally {
      Arrays.fill(stretchedKey, (byte) 0);
    }

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
    processDatasinks(command, workspaceName, datastoreName, false);

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

  private SagaCommandResult handleUpdateWorkspace(SagaCommandMessage command) {
    String workspaceName = requireWorkspaceName(command);
    String datastoreName = workspaceName + "_postgis";

    // Read current feature types for compensation
    List<Map<String, Object>> currentFeatureTypes =
        readCurrentFeatureTypes(workspaceName, datastoreName);

    // Create or update feature types from new datasinks
    processDatasinks(command, workspaceName, datastoreName, true);

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
    String workspaceName = requireWorkspaceName(command);

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
        String body = truncateBody(response.readEntity(String.class));
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
    String workspaceName = requireWorkspaceName(command);
    String datastoreName = workspaceName + "_postgis";
    List<Map<String, Object>> previousFeatureTypes =
        (List<Map<String, Object>>)
            command.payload().getOrDefault("previousFeatureTypes", List.of());

    Set<String> previousNames =
        previousFeatureTypes.stream()
            .map(ft -> (String) ft.get("name"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    // Delete feature types created during the failed update (present now, absent in the snapshot).
    // PUT-only restore would leave these orphaned, so compensation must remove them.
    for (Map<String, Object> current : readCurrentFeatureTypes(workspaceName, datastoreName)) {
      String name = (String) current.get("name");
      if (name != null && !previousNames.contains(name)) {
        deleteFeatureType(workspaceName, datastoreName, name);
      }
    }

    // Restore the previous state of feature types that existed before the update.
    for (Map<String, Object> ft : previousFeatureTypes) {
      String ftName = (String) ft.get("name");
      if (ftName == null) {
        continue;
      }
      requireSafeName(ftName, "featureType name");
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

  /**
   * Iterates the command's datasinks, provisioning a feature type for each {@code POSTGIS} sink.
   * Other sink types are skipped (handled by their own adapters). When {@code upsert} is true
   * an existing feature type is updated (used by {@code UPDATE_WORKSPACE}); otherwise an existing
   * feature type is left unchanged (idempotent {@code PROVISION_WORKSPACE}).
   */
  @SuppressWarnings("unchecked")
  private void processDatasinks(
      SagaCommandMessage command, String workspaceName, String datastoreName, boolean upsert) {
    List<Map<String, Object>> datasinks =
        (List<Map<String, Object>>) command.payload().getOrDefault("datasinks", List.of());
    for (Map<String, Object> sink : datasinks) {
      if (!DATASINK_TYPE_POSTGIS.equals(sink.get("type"))) {
        continue;
      }
      Map<String, Object> configuration =
          (Map<String, Object>) sink.getOrDefault("configuration", Map.of());
      String tableName = (String) configuration.get("tableName");
      if (tableName == null || tableName.isBlank()) {
        log.warn(
            "Skipping {} datasink without a tableName for workspace {} (saga {})",
            DATASINK_TYPE_POSTGIS,
            Encode.forJava(workspaceName),
            Encode.forJava(command.sagaId()));
        continue;
      }
      requireSafeName(tableName, "tableName");
      if (upsert) {
        upsertFeatureType(workspaceName, datastoreName, tableName, configuration);
      } else {
        createFeatureType(workspaceName, datastoreName, tableName, configuration);
      }
    }
  }

  /**
   * Builds the GeoServer {@code featureType} REST body. Only {@code tableName}, {@code crs} and
   * {@code projectionPolicy} are mapped; other datasink fields (e.g. {@code primaryKey}, {@code
   * geometryColumn}) are intentionally not forwarded — GeoServer derives them from the PostGIS
   * table. Richer mapping is deferred to the Flowable-based saga implementation.
   */
  private static Map<String, Object> featureTypePayload(
      String tableName, Map<String, Object> configuration) {
    String crs = (String) configuration.getOrDefault("crs", "EPSG:4326");
    String projectionPolicy =
        (String) configuration.getOrDefault("projectionPolicy", DEFAULT_PROJECTION_POLICY);
    return Map.of(
        "featureType",
        Map.of(
            "name", tableName,
            "nativeName", tableName,
            "title", tableName,
            "srs", crs,
            "projectionPolicy", projectionPolicy));
  }

  /** Creates a feature type idempotently: HTTP 409 (already exists) is treated as success. */
  private void createFeatureType(
      String workspaceName,
      String datastoreName,
      String tableName,
      Map<String, Object> configuration) {
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(featureTypesPath(workspaceName, datastoreName))
                    .request(MediaType.APPLICATION_JSON))
            .post(Entity.json(featureTypePayload(tableName, configuration)))) {
      if (response.getStatus() != 201 && response.getStatus() != 409) {
        checkResponse(response, "create-featuretype/" + tableName);
      }
    }
  }

  /**
   * Creates a feature type, or updates it via PUT if it already exists (HTTP 409). A plain POST
   * returns 409 for existing feature types and would otherwise leave them unchanged — so {@code
   * UPDATE_WORKSPACE} would report success without applying any change.
   */
  private void upsertFeatureType(
      String workspaceName,
      String datastoreName,
      String tableName,
      Map<String, Object> configuration) {
    Map<String, Object> payload = featureTypePayload(tableName, configuration);
    try (Response createResponse =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(featureTypesPath(workspaceName, datastoreName))
                    .request(MediaType.APPLICATION_JSON))
            .post(Entity.json(payload))) {
      if (createResponse.getStatus() == 201) {
        return;
      }
      if (createResponse.getStatus() != 409) {
        checkResponse(createResponse, "update-featuretype/create/" + tableName);
        return;
      }
    }
    try (Response updateResponse =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(featureTypesPath(workspaceName, datastoreName) + "/" + tableName)
                    .request(MediaType.APPLICATION_JSON))
            .put(Entity.json(payload))) {
      checkResponse(updateResponse, "update-featuretype/" + tableName);
    }
  }

  /** Deletes a feature type recursively (its implicitly published layer is removed too). */
  private void deleteFeatureType(String workspaceName, String datastoreName, String ftName) {
    requireSafeName(ftName, "featureType name");
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(featureTypesPath(workspaceName, datastoreName) + "/" + ftName)
                    .queryParam("recurse", "true")
                    .request(MediaType.APPLICATION_JSON))
            .delete()) {
      int status = response.getStatus();
      if (status != 200 && status != 404) {
        checkResponse(response, "RESTORE_WORKSPACE/delete-featuretype/" + ftName);
      }
    }
  }

  private static String featureTypesPath(String workspaceName, String datastoreName) {
    return "/rest/workspaces/" + workspaceName + "/datastores/" + datastoreName + "/featuretypes";
  }

  /**
   * Rejects names that are not a safe single REST path segment (e.g. containing {@code /}), since
   * feature-type and table names are concatenated into GeoServer REST URIs.
   */
  private static void requireSafeName(String name, String field) {
    if (name == null || !FEATURE_TYPE_NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
          field + " contains invalid characters (allowed: A-Z, a-z, 0-9, _, -): " + name);
    }
  }

  /**
   * Truncates an HTTP error body before echoing it into a saga error message. GeoServer error
   * bodies can be large and may contain connection details, so they are bounded to {@value
   * #MAX_ERROR_BODY_LENGTH} characters.
   */
  private static String truncateBody(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= MAX_ERROR_BODY_LENGTH
        ? body
        : body.substring(0, MAX_ERROR_BODY_LENGTH) + "… (truncated)";
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
      int status = response.getStatus();
      if (status == 404) {
        // Datastore/workspace has no feature types yet — an empty snapshot is correct here.
        return List.of();
      }
      if (status != 200) {
        // Auth/server errors must not be mistaken for "zero feature types": that would let
        // UPDATE_WORKSPACE proceed with an empty compensation snapshot and lose restore state.
        String body = truncateBody(response.readEntity(String.class));
        throw new SagaApiException(
            "UPDATE_WORKSPACE/read-featuretypes failed: HTTP " + status + " — " + body, status);
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

  /**
   * Derives the workspace name (and, with the {@code _postgis} suffix, the datastore name) from the
   * dataset id. Note: lossy normalization means distinct dataset ids can collapse to the same
   * workspace name (e.g. {@code "ds-1"} and {@code "ds_1"} both become {@code "ds_1"}); callers
   * must ensure dataset ids are unique under this mapping.
   */
  static String toWorkspaceName(String datasetId) {
    if (datasetId == null || datasetId.isBlank()) {
      throw new IllegalArgumentException("datasetId must not be blank");
    }
    return datasetId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
  }

  /**
   * Reads {@code workspaceName} from the command payload and rejects values that are not a safe
   * REST path segment. The name is concatenated into GeoServer REST URIs in update/delete/restore
   * flows, so unvalidated input (e.g. containing {@code /}) could target unintended endpoints.
   * Matches the character set produced by {@link #toWorkspaceName(String)}.
   */
  private static String requireWorkspaceName(SagaCommandMessage command) {
    String workspaceName = requireString(command, "workspaceName");
    if (!WORKSPACE_NAME_PATTERN.matcher(workspaceName).matches()) {
      throw new IllegalArgumentException(
          "workspaceName contains invalid characters (allowed: a-z, 0-9, _): " + workspaceName);
    }
    return workspaceName;
  }
}
