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
import de.civitascore.configadapter.model.dataset.SafeNames;
import de.civitascore.configadapter.model.dataset.WorkspaceNames;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.owasp.encoder.Encode;

/**
 * Saga command handler for GeoServer workspace provisioning, driven by the Flowable saga engine.
 * Each operation is a single saga step so the orchestrator can sequence and compensate them
 * individually (FEED_IN_GEO_SETUP in the dataset saga):
 *
 * <ul>
 *   <li>{@code CREATE_WORKSPACE} — creates the dataset workspace (idempotent)
 *   <li>{@code CREATE_DATASTORE} — creates the PostGIS datastore inside the workspace (idempotent)
 *   <li>{@code PROVISION_LAYERS} — publishes a feature type per {@code layers} entry (idempotent)
 *   <li>{@code PROVISION_WORKSPACE} — coarse alias that runs the three create steps in one call
 *   <li>{@code UPDATE_WORKSPACE} — idempotently ensures the workspace and PostGIS datastore exist
 *       (UPDATE may be the first time geo is provisioned for a dataset), then upserts feature types
 *       from {@code layers}; captures current state for compensation
 *   <li>{@code DELETE_WORKSPACE} — deletes the workspace recursively (compensation for the create
 *       steps and the teardown step in the delete saga)
 *   <li>{@code RESTORE_WORKSPACE} — restores the previous feature type state (compensation for
 *       {@code UPDATE_WORKSPACE}). Styles are not snapshotted: a failed update that uploaded or
 *       overwrote styles leaves them in place — only a full {@code DELETE_WORKSPACE} (recursive)
 *       removes them.
 * </ul>
 *
 * <p>Input contract (process variables forwarded from the saga trigger): {@code datasetId} (the
 * workspace name is derived from it), an optional {@code datasinks} list whose {@code POSTGIS}
 * sinks carry {@code configuration.tableName}, and a {@code layers} list of {@code {layerName,
 * nativeName?, crs?}} describing the feature types to publish. A layer's native PostGIS table is
 * its {@code nativeName}; if omitted it defaults to the single {@code POSTGIS} sink table (or the
 * layer name when no sink is given), and {@code nativeName} is required when multiple table sinks
 * exist. Connection parameters for the PostGIS datastore are read from adapter config: {@code
 * geoserver.postgis.host}, {@code .port}, {@code .database}, {@code .schema}, {@code .user}, {@code
 * .password}.
 */
public class GeoServerSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "geoserver";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080/geoserver";

  private static final String DEFAULT_POSTGIS_HOST = "localhost";
  private static final String DEFAULT_POSTGIS_PORT = "5432";
  private static final String DEFAULT_POSTGIS_DATABASE = "civitas_geo";

  private static final Pattern WORKSPACE_NAME_PATTERN = Pattern.compile("[a-z0-9_]+");

  /**
   * Only datasinks of this type are provisioned as GeoServer feature types. Matches the {@code
   * DataSinkType.POSTGIS} value the portal-backend emits in the saga trigger payload — a
   * PostGIS-backed sink is what GeoServer can publish as a WMS/WFS feature type.
   */
  private static final String DATASINK_TYPE_POSTGIS = "POSTGIS";

  private static final String DEFAULT_CRS = "EPSG:4326";
  private static final String DEFAULT_PROJECTION_POLICY = "REPROJECT_TO_DECLARED";

  /** Content type for SLD 1.0.0 style uploads; GeoServer rejects styles posted as plain XML. */
  private static final String STYLE_SLD_CONTENT_TYPE = "application/vnd.ogc.sld+xml";

  /** Upper bound for HTTP error bodies echoed into saga errors/logs (may be large or sensitive). */
  private static final int MAX_ERROR_BODY_LENGTH = 500;

  private String serverUrl;
  private String publicUrl;
  private GeoServerAuth auth;

  private String postgisHost;
  private String postgisPort;
  private String postgisDatabase;
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
      case "CREATE_WORKSPACE" -> handleCreateWorkspace(command);
      case "CREATE_DATASTORE" -> handleCreateDatastore(command);
      case "PROVISION_LAYERS" -> handleProvisionLayers(command);
      case "PROVISION_WORKSPACE" -> handleProvisionWorkspace(command);
      case "UPDATE_WORKSPACE" -> handleUpdateWorkspace(command);
      case "DELETE_WORKSPACE" -> handleDeleteWorkspace(command);
      case "RESTORE_WORKSPACE" -> handleRestoreWorkspace(command);
      default -> unknownOperation(command);
    };
  }

  private SagaCommandResult handleCreateWorkspace(SagaCommandMessage command) {
    String workspaceName = resolveWorkspaceName(command);
    createWorkspace(workspaceName);

    log.info(
        "GeoServer workspace created: workspaceName={}, saga={}",
        Encode.forJava(workspaceName),
        Encode.forJava(command.sagaId()));

    return endpointSuccess(command, workspaceName);
  }

  private SagaCommandResult handleCreateDatastore(SagaCommandMessage command) {
    String workspaceName = resolveWorkspaceName(command);
    String datastoreName = datastoreName(workspaceName);
    createDatastore(workspaceName, datastoreName);

    log.info(
        "GeoServer PostGIS datastore created: workspaceName={}, datastoreName={}, saga={}",
        Encode.forJava(workspaceName),
        Encode.forJava(datastoreName),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(),
        command.stepId(),
        Map.of("workspaceName", workspaceName, "datastoreName", datastoreName),
        Map.of("workspaceName", workspaceName));
  }

  private SagaCommandResult handleProvisionLayers(SagaCommandMessage command) {
    String workspaceName = resolveWorkspaceName(command);
    processLayers(command, workspaceName, datastoreName(workspaceName), false);

    log.info(
        "GeoServer layers provisioned: workspaceName={}, saga={}",
        Encode.forJava(workspaceName),
        Encode.forJava(command.sagaId()));

    return endpointSuccess(command, workspaceName);
  }

  private SagaCommandResult handleProvisionWorkspace(SagaCommandMessage command) {
    String workspaceName = resolveWorkspaceName(command);
    String datastoreName = datastoreName(workspaceName);

    createWorkspace(workspaceName);
    createDatastore(workspaceName, datastoreName);
    processLayers(command, workspaceName, datastoreName, false);

    log.info(
        "GeoServer workspace provisioned: workspaceName={}, saga={}",
        Encode.forJava(workspaceName),
        Encode.forJava(command.sagaId()));

    return endpointSuccess(command, workspaceName);
  }

  private SagaCommandResult handleUpdateWorkspace(SagaCommandMessage command) {
    String workspaceName = resolveWorkspaceName(command);
    String datastoreName = datastoreName(workspaceName);

    // Read current feature types for compensation (empty if the workspace isn't provisioned yet).
    List<Map<String, Object>> currentFeatureTypes =
        readCurrentFeatureTypes(workspaceName, datastoreName);

    // Ensure the workspace and PostGIS datastore exist before publishing feature types. UPDATE may
    // be the first time geo is provisioned for a dataset (e.g. a geo sink added on a later update),
    // in which case neither exists yet and a plain feature-type POST would 404. Both creates are
    // idempotent (HTTP 409 = already exists).
    createWorkspace(workspaceName);
    createDatastore(workspaceName, datastoreName);

    // Create or update feature types from the new layers
    processLayers(command, workspaceName, datastoreName, true);

    String wfsUrl = wfsUrl(workspaceName);
    String wmsUrl = wmsUrl(workspaceName);

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
    String workspaceName = resolveWorkspaceName(command);

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
    String workspaceName = resolveWorkspaceName(command);
    String datastoreName = datastoreName(workspaceName);

    Object snapshot = command.payload().get("previousFeatureTypes");
    if (snapshot == null) {
      // No snapshot was captured — the UPDATE step failed before storing its compensation data, so
      // we don't know which feature types pre-existed. Skip restore rather than treat a missing
      // snapshot as "the workspace was empty" and delete pre-existing feature types.
      log.warn(
          "RESTORE_WORKSPACE: no previousFeatureTypes snapshot for workspace {} (saga {}); skipping"
              + " restore",
          Encode.forJava(workspaceName),
          Encode.forJava(command.sagaId()));
      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
    }
    List<Map<String, Object>> previousFeatureTypes = (List<Map<String, Object>>) snapshot;

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
                      .path(featureTypesPath(workspaceName, datastoreName) + "/" + ftName)
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

  /** Creates the workspace idempotently: HTTP 409 (already exists) is treated as success. */
  private void createWorkspace(String workspaceName) {
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path("/rest/workspaces")
                    .request(MediaType.APPLICATION_JSON))
            .post(Entity.json(Map.of("workspace", Map.of("name", workspaceName))))) {
      if (response.getStatus() != 201 && response.getStatus() != 409) {
        checkResponse(response, "CREATE_WORKSPACE/" + workspaceName);
      }
    }
  }

  /**
   * Creates the PostGIS datastore, or updates it via PUT if it already exists (HTTP 409). A plain
   * POST returns 409 for an existing datastore and would otherwise leave its connection parameters
   * untouched — so a re-provision or update against changed config (e.g. PostGIS host/credentials)
   * would report success while keeping stale data. Updating on 409 converges it to the desired
   * state.
   */
  private void createDatastore(String workspaceName, String datastoreName) {
    // The datastore's schema is the workspace name: both derive from datasetId, so GeoServer reads
    // exactly the schema PostGIS created the table in.
    Map<String, Object> datastoreBody = buildDatastoreBody(datastoreName, workspaceName);
    try (Response createResponse =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path("/rest/workspaces/" + workspaceName + "/datastores")
                    .request(MediaType.APPLICATION_JSON))
            .post(Entity.json(datastoreBody))) {
      if (createResponse.getStatus() == 201) {
        return;
      }
      if (createResponse.getStatus() != 409) {
        checkResponse(createResponse, "CREATE_DATASTORE/" + datastoreName);
        return;
      }
    }
    try (Response updateResponse =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path("/rest/workspaces/" + workspaceName + "/datastores/" + datastoreName)
                    .request(MediaType.APPLICATION_JSON))
            .put(Entity.json(datastoreBody))) {
      checkResponse(updateResponse, "CREATE_DATASTORE/update/" + datastoreName);
    }
  }

  /**
   * Iterates the command's {@code layers}, provisioning a GeoServer feature type for each. The
   * native PostGIS table is taken from the layer's {@code nativeName}, falling back to the first
   * {@code POSTGIS} data sink's {@code tableName}, then to the layer name. When {@code upsert} is
   * true an existing feature type is updated (used by {@code UPDATE_WORKSPACE}); otherwise an
   * existing feature type is left unchanged (idempotent provisioning).
   */
  private void processLayers(
      SagaCommandMessage command, String workspaceName, String datastoreName, boolean upsert) {
    // Upload the dataset's styles before publishing layers; a layer can only reference a style that
    // already exists in the workspace.
    upsertStyles(command, workspaceName);
    List<Map<String, Object>> layers = mapList(command, "layers");
    List<String> sinkTables = sinkTableNames(command);
    for (Map<String, Object> layer : layers) {
      String layerName = stringValue(layer, "layerName");
      if (layerName == null || layerName.isBlank()) {
        // Fail rather than skip: a requested layer with no name can't be published, and silently
        // skipping would report the step COMPLETED while the layer is missing. Consistent with the
        // hard-fail on an invalid name below.
        throw new IllegalArgumentException("layer is missing the required field: layerName");
      }
      requireSafeName(layerName, "layerName");
      String nativeName = resolveNativeName(layer, layerName, sinkTables);
      requireSafeName(nativeName, "nativeName");
      String crs = stringValue(layer, "crs");
      if (crs == null || crs.isBlank()) {
        crs = DEFAULT_CRS;
      }
      // Validate style refs before publishing this layer (with the name checks above). Not a
      // global no-side-effects guarantee — styles were already uploaded and earlier layers may be
      // published — it just avoids publishing this layer with a ref that would then fail.
      String defaultStyle = stringValue(layer, "defaultStyle");
      List<String> alternativeStyles = stringList(layer, "alternativeStyles");
      if (defaultStyle != null && !defaultStyle.isBlank()) {
        requireSafeName(defaultStyle, "defaultStyle");
      }
      for (String alternativeStyle : alternativeStyles) {
        requireSafeName(alternativeStyle, "alternativeStyle");
      }
      if (upsert) {
        upsertFeatureType(workspaceName, datastoreName, layerName, nativeName, crs);
      } else {
        createFeatureType(workspaceName, datastoreName, layerName, nativeName, crs);
      }
      if ((defaultStyle != null && !defaultStyle.isBlank()) || !alternativeStyles.isEmpty()) {
        assignLayerStyles(workspaceName, layerName, defaultStyle, alternativeStyles);
      }
    }
  }

  /**
   * Resolves the native PostGIS table for a layer: the layer's explicit {@code nativeName}; else
   * the dataset's single {@code POSTGIS} sink table when there is exactly one; else the layer name
   * when no table sink is provided. Fails when multiple distinct table sinks exist and the layer
   * doesn't say which one — otherwise every nativeName-less layer would silently collapse onto the
   * first table and the other tables would never be published.
   */
  private static String resolveNativeName(
      Map<String, Object> layer, String layerName, List<String> sinkTables) {
    String explicit = stringValue(layer, "nativeName");
    if (explicit != null && !explicit.isBlank()) {
      return explicit;
    }
    if (sinkTables.size() == 1) {
      return sinkTables.get(0);
    }
    if (sinkTables.isEmpty()) {
      return layerName;
    }
    throw new IllegalArgumentException(
        "layer '"
            + layerName
            + "' must specify nativeName: the native table cannot be inferred from "
            + sinkTables.size()
            + " POSTGIS data sinks");
  }

  /** Distinct {@code tableName}s across the {@code POSTGIS} data sinks, in encounter order. */
  private static List<String> sinkTableNames(SagaCommandMessage command) {
    List<String> tables = new ArrayList<>();
    for (Map<String, Object> sink : mapList(command, "datasinks")) {
      if (!DATASINK_TYPE_POSTGIS.equals(sink.get("type"))) {
        continue;
      }
      Map<String, Object> configuration = mapValue(sink, "configuration");
      if (configuration.get("tableName") instanceof String tableName
          && !tableName.isBlank()
          && !tables.contains(tableName)) {
        tables.add(tableName);
      }
    }
    return tables;
  }

  // ── Payload type guards ─────────────────────────────────────────────────────
  // The trigger payload is untyped JSON, so guard structure/field types and fail with a clean
  // validation error instead of letting a ClassCastException escape on malformed input.

  /** Reads a payload list of objects; missing key → empty list. */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> mapList(SagaCommandMessage command, String key) {
    Object value = command.payload().getOrDefault(key, List.of());
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException(key + " must be a list, got " + typeName(value));
    }
    for (Object element : list) {
      if (!(element instanceof Map)) {
        throw new IllegalArgumentException(
            key + " entries must be objects, got " + typeName(element));
      }
    }
    return (List<Map<String, Object>>) value;
  }

  /** Reads an optional list-of-strings field (missing → empty), rejecting non-string elements. */
  @SuppressWarnings("unchecked")
  private static List<String> stringList(Map<String, Object> source, String field) {
    Object value = source.getOrDefault(field, List.of());
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException(field + " must be a list, got " + typeName(value));
    }
    for (Object element : list) {
      if (!(element instanceof String)) {
        throw new IllegalArgumentException(
            field + " entries must be strings, got " + typeName(element));
      }
    }
    return (List<String>) value;
  }

  /** Reads an optional nested string field, rejecting a non-string value. */
  private static String stringValue(Map<String, Object> source, String field) {
    Object value = source.get(field);
    if (value == null || value instanceof String) {
      return (String) value;
    }
    throw new IllegalArgumentException(field + " must be a string, got " + typeName(value));
  }

  /** Reads a nested object field (missing → empty), rejecting a non-object value. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapValue(Map<String, Object> source, String field) {
    Object value = source.getOrDefault(field, Map.of());
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException(field + " must be an object, got " + typeName(value));
    }
    return (Map<String, Object>) value;
  }

  private static String typeName(Object value) {
    return value == null ? "null" : value.getClass().getSimpleName();
  }

  /**
   * Builds the GeoServer {@code featureType} REST body. The published layer is named {@code name};
   * {@code nativeName} is the underlying PostGIS table. GeoServer derives columns, primary key and
   * geometry from the table itself, so only naming, SRS and the projection policy are mapped.
   */
  private static Map<String, Object> featureTypePayload(
      String name, String nativeName, String crs) {
    return Map.of(
        "featureType",
        Map.of(
            "name", name,
            "nativeName", nativeName,
            "title", name,
            "srs", crs,
            "projectionPolicy", DEFAULT_PROJECTION_POLICY));
  }

  /** Creates a feature type idempotently: HTTP 409 (already exists) is treated as success. */
  private void createFeatureType(
      String workspaceName, String datastoreName, String name, String nativeName, String crs) {
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(featureTypesPath(workspaceName, datastoreName))
                    .request(MediaType.APPLICATION_JSON))
            .post(Entity.json(featureTypePayload(name, nativeName, crs)))) {
      if (response.getStatus() != 201 && response.getStatus() != 409) {
        checkResponse(response, "create-featuretype/" + name);
      }
    }
  }

  /**
   * Creates a feature type, or updates it via PUT if it already exists (HTTP 409). A plain POST
   * returns 409 for existing feature types and would otherwise leave them unchanged — so {@code
   * UPDATE_WORKSPACE} would report success without applying any change.
   */
  private void upsertFeatureType(
      String workspaceName, String datastoreName, String name, String nativeName, String crs) {
    Map<String, Object> payload = featureTypePayload(name, nativeName, crs);
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
        checkResponse(createResponse, "update-featuretype/create/" + name);
        return;
      }
    }
    try (Response updateResponse =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(featureTypesPath(workspaceName, datastoreName) + "/" + name)
                    .request(MediaType.APPLICATION_JSON))
            .put(Entity.json(payload))) {
      checkResponse(updateResponse, "update-featuretype/" + name);
    }
  }

  /**
   * Uploads every style in the command's {@code styles} list into the workspace. Each entry is
   * {@code {name, sldContent}}; the SLD is created or refreshed under {@code name} so layers can
   * reference it. The list must contain every style any layer references — a layer referencing a
   * missing style would later fail its assignment.
   */
  private void upsertStyles(SagaCommandMessage command, String workspaceName) {
    for (Map<String, Object> style : mapList(command, "styles")) {
      String name = stringValue(style, "name");
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("style is missing the required field: name");
      }
      requireSafeName(name, "styleName");
      String sld = stringValue(style, "sldContent");
      if (sld == null || sld.isBlank()) {
        throw new IllegalArgumentException(
            "style '" + name + "' is missing the required field: sldContent");
      }
      upsertStyle(workspaceName, name, sld);
    }
  }

  /**
   * Creates a style from its SLD, or updates it via PUT if one of that name already exists. An
   * existing style returns 403 (not 409) on GeoServer Cloud 2.28.3.0, so the upsert branches on 403
   * and refreshes the SLD; a genuine auth 403 falls through to the same PUT and surfaces via {@link
   * #checkResponse}.
   */
  private void upsertStyle(String workspaceName, String name, String sld) {
    Entity<String> sldEntity = Entity.entity(sld, STYLE_SLD_CONTENT_TYPE);
    try (Response createResponse =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(stylesPath(workspaceName))
                    .queryParam("name", name)
                    .request(MediaType.APPLICATION_JSON))
            .post(sldEntity)) {
      if (createResponse.getStatus() == 201) {
        return;
      }
      if (createResponse.getStatus() != 403) {
        checkResponse(createResponse, "create-style/" + name);
        return;
      }
    }
    try (Response updateResponse =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(stylePath(workspaceName, name))
                    .request(MediaType.APPLICATION_JSON))
            .put(sldEntity)) {
      checkResponse(updateResponse, "update-style/" + name);
    }
  }

  /**
   * Assigns the default and/or alternative styles to a layer in one PUT, then reads the layer back
   * to confirm they were applied. References are workspace-qualified ({@code {workspace}:{name}});
   * alternatives use a {@code linked-hash-set} to preserve order. Names are validated by the caller
   * before the layer is published.
   *
   * <p>The read-back is required because GeoServer answers a PUT with an unresolved style reference
   * with HTTP 200 while silently keeping the old style — so a 200 alone does not prove the style
   * was applied (notably under GeoServer Cloud's asynchronous catalog propagation).
   */
  private void assignLayerStyles(
      String workspaceName, String layerName, String defaultStyle, List<String> alternativeStyles) {
    Map<String, Object> layerBody = new HashMap<>();
    if (defaultStyle != null && !defaultStyle.isBlank()) {
      layerBody.put(
          "defaultStyle",
          Map.of("name", workspaceName + ":" + defaultStyle, "workspace", workspaceName));
    }
    if (!alternativeStyles.isEmpty()) {
      List<Map<String, Object>> styleRefs = new ArrayList<>();
      for (String alternativeStyle : alternativeStyles) {
        styleRefs.add(Map.of("name", workspaceName + ":" + alternativeStyle));
      }
      layerBody.put("styles", Map.of("@class", "linked-hash-set", "style", styleRefs));
    }
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(layerPath(workspaceName, layerName))
                    .request(MediaType.APPLICATION_JSON))
            .put(Entity.json(Map.of("layer", layerBody)))) {
      checkResponse(response, "assign-layer-styles/" + layerName);
    }
    verifyLayerStylesApplied(workspaceName, layerName, defaultStyle, alternativeStyles);
  }

  /**
   * Reads the layer back and asserts the default and alternative styles resolve to the intended
   * workspace styles, failing the step otherwise. See {@link #assignLayerStyles} for why a 200 from
   * the assignment PUT is not a sufficient success signal.
   */
  private void verifyLayerStylesApplied(
      String workspaceName, String layerName, String defaultStyle, List<String> alternativeStyles) {
    Map<String, Object> layer = readLayer(workspaceName, layerName);
    if (defaultStyle != null && !defaultStyle.isBlank()) {
      String expected = workspaceName + ":" + defaultStyle;
      String actual = nestedString(layer, "defaultStyle", "name");
      if (!expected.equals(actual)) {
        throw new IllegalStateException(
            "layer "
                + layerName
                + " default style was not applied: GeoServer kept '"
                + actual
                + "', expected '"
                + expected
                + "'");
      }
    }
    if (!alternativeStyles.isEmpty()) {
      Set<String> applied = layerStyleNames(layer);
      for (String alternativeStyle : alternativeStyles) {
        String expected = workspaceName + ":" + alternativeStyle;
        if (!applied.contains(expected)) {
          throw new IllegalStateException(
              "layer "
                  + layerName
                  + " alternative style was not applied: '"
                  + expected
                  + "' missing from "
                  + applied);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readLayer(String workspaceName, String layerName) {
    try (Response response =
        auth.apply(
                client()
                    .target(serverUrl)
                    .path(layerPath(workspaceName, layerName) + ".json")
                    .request(MediaType.APPLICATION_JSON))
            .get()) {
      checkResponse(response, "read-layer/" + layerName);
      Object layer = response.readEntity(Map.class).get("layer");
      if (!(layer instanceof Map)) {
        throw new IllegalStateException("read-layer/" + layerName + ": unexpected response shape");
      }
      return (Map<String, Object>) layer;
    }
  }

  /** Reads {@code map.outer.inner} as a String, or {@code null} if absent/not a string. */
  private static String nestedString(Map<String, Object> map, String outer, String inner) {
    if (map.get(outer) instanceof Map<?, ?> nested && nested.get(inner) instanceof String value) {
      return value;
    }
    return null;
  }

  /**
   * The workspace-qualified names in a layer's alternative-style set. GeoServer serialises a single
   * alternative style as an object rather than a one-element array, so both shapes are handled.
   */
  @SuppressWarnings("unchecked")
  private static Set<String> layerStyleNames(Map<String, Object> layer) {
    Set<String> names = new HashSet<>();
    if (!(layer.get("styles") instanceof Map<?, ?> styles)) {
      return names;
    }
    Object style = ((Map<String, Object>) styles).get("style");
    List<Object> entries =
        switch (style) {
          case List<?> list -> (List<Object>) list;
          case Map<?, ?> single -> List.of(single);
          case null, default -> List.of();
        };
    for (Object entry : entries) {
      if (entry instanceof Map<?, ?> ref && ref.get("name") instanceof String name) {
        names.add(name);
      }
    }
    return names;
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

  private static String stylesPath(String workspaceName) {
    return "/rest/workspaces/" + workspaceName + "/styles";
  }

  private static String stylePath(String workspaceName, String styleName) {
    return "/rest/workspaces/" + workspaceName + "/styles/" + styleName;
  }

  private static String layerPath(String workspaceName, String layerName) {
    return "/rest/workspaces/" + workspaceName + "/layers/" + layerName;
  }

  private static String datastoreName(String workspaceName) {
    return workspaceName + "_postgis";
  }

  private String wfsUrl(String workspaceName) {
    return publicUrl + "/" + workspaceName + "/wfs";
  }

  private String wmsUrl(String workspaceName) {
    return publicUrl + "/" + workspaceName + "/wms";
  }

  /** Standard success carrying the workspace endpoints and the workspace name for compensation. */
  private SagaCommandResult endpointSuccess(SagaCommandMessage command, String workspaceName) {
    return SagaCommandResult.success(
        command.sagaId(),
        command.stepId(),
        Map.of(
            "workspaceName", workspaceName,
            "wfsUrl", wfsUrl(workspaceName),
            "wmsUrl", wmsUrl(workspaceName)),
        Map.of("workspaceName", workspaceName));
  }

  /**
   * Rejects names that are not a safe single REST path segment (e.g. containing {@code /}), since
   * feature-type and table names are concatenated into GeoServer REST URIs.
   */
  private static void requireSafeName(String name, String field) {
    if (name == null || !SafeNames.COMPILED_PATTERN.matcher(name).matches()) {
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

  private Map<String, Object> buildDatastoreBody(String datastoreName, String schema) {
    List<Map<String, Object>> entries = new ArrayList<>();
    entries.add(Map.of("@key", "host", "$", postgisHost));
    entries.add(Map.of("@key", "port", "$", postgisPort));
    entries.add(Map.of("@key", "database", "$", postgisDatabase));
    entries.add(Map.of("@key", "schema", "$", schema));
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
   * Derives the workspace name (and, with the {@code _postgis} suffix, the datastore name). Prefers
   * an explicit, validated {@code workspaceName} in the payload (used by update/delete/restore
   * steps that received it from an earlier step); otherwise derives it from {@code datasetId}.
   *
   * <p>Note: the {@code datasetId} normalization is lossy — distinct dataset ids can collapse to
   * the same workspace name (e.g. {@code "ds-1"} and {@code "ds_1"} both become {@code "ds_1"});
   * callers must ensure dataset ids are unique under this mapping.
   */
  private static String resolveWorkspaceName(SagaCommandMessage command) {
    Object explicit = command.payload().get("workspaceName");
    if (explicit instanceof String workspaceName && !workspaceName.isBlank()) {
      if (!WORKSPACE_NAME_PATTERN.matcher(workspaceName).matches()) {
        throw new IllegalArgumentException(
            "workspaceName contains invalid characters (allowed: a-z, 0-9, _): " + workspaceName);
      }
      return workspaceName;
    }
    return toWorkspaceName(requireString(command, "datasetId"));
  }

  static String toWorkspaceName(String datasetId) {
    // Single source of truth shared with the APISIX adapter, which derives the same workspace name
    // to build the OWS named-API route's path-rewrite target.
    return WorkspaceNames.fromDatasetId(datasetId);
  }
}
