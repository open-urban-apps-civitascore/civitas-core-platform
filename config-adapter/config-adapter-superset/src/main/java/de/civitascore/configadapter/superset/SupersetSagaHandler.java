/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.superset;

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.dataset.DatasetSchemaNames;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.owasp.encoder.Encode;

/**
 * Saga command handler for Superset, driven by the Flowable saga engine. Registers/deregisters
 * Superset datasets for a Civitas dataset's PostGIS sink table(s) and/or FROST sink, and
 * creates/updates the Superset dashboard shell for each entry the trigger carries in {@code
 * dashboards} — invoked by both the {@code dataset-create} and {@code dataset-update} sagas. Charts
 * are not created by this adapter — users add their own charts to a provisioned dashboard in
 * Superset.
 *
 * <ul>
 *   <li>{@code REGISTER_DATASET} — creates one Superset physical dataset per {@code POSTGIS} sink
 *       table (idempotent via {@code table_name}, which never changes for PostGIS), plus one
 *       Superset virtual (SQL-defined) dataset per {@code FROST_SCOPED_VIEWS} entry when the
 *       trigger carries a {@code FROST} sink — labeled with the dataset's current display name
 *       (idempotent via a stamped {@code frostView} marker instead, since the label itself changes
 *       on every rename; see {@link #frostDatasetLabel}) — and syncs each {@code dashboards} entry:
 *       {@code externalDashboardId == null} creates a new dashboard titled {@code name}; non-null
 *       renames the existing dashboard in place, self-healing by creating a replacement if that id
 *       no longer exists in Superset (drift). Any of the three (PostGIS tables, FROST sink,
 *       dashboards) may be absent — the step is a no-op for whichever part has nothing to do. FROST
 *       sink count doesn't matter: {@code hasFrostSink} is an existence check, not a count — a
 *       dataset always gets exactly one project (see {@code FrostSagaHandler}) and exactly one set
 *       of these 7 datasets, however many FROST sinks feed into it.
 *   <li>{@code DEREGISTER_DATASET} — compensation for {@code REGISTER_DATASET}; deletes exactly the
 *       dataset(s) and dashboard(s) recorded in that step's compensation data. Only meaningful as a
 *       full rollback of a single saga run (e.g. {@code dataset-create} compensation) — a dashboard
 *       rename made during a {@code dataset-update} run is not compensated, since the prior title
 *       is not retained and Superset's state is otherwise left idempotently reconcilable by the
 *       next successful sync.
 * </ul>
 *
 * <p>Input contract (process variables forwarded from the saga trigger): {@code datasetId}; a
 * {@code datasinks} list whose {@code POSTGIS} entries carry {@code configuration.tableName} and
 * whose presence of a {@code FROST} entry (no per-entry configuration needed) gates the FROST
 * branch; {@code projectId} (the dataset's FROST project id — required only when a {@code FROST}
 * sink is present); a {@code dashboards} binding list and an {@code artifacts} map keyed by each
 * Dashboard's {@code modelUrn} (see {@code de.civitascore.configadapter.model.dataset.Dashboard}).
 * The dashboard model is the source of its title, description and release state. PostGIS keeps the
 * physical UUID schema used by GeoServer and exposes the table through a readable per-dataset view
 * schema for Trino. FROST uses the same readable naming rule for its project-scoped views. OPA
 * resolves either schema name back to the CORE dataset before checking permission.
 */
public class SupersetSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "superset";
  private static final String COMPENSATE_TYPE = "COMPENSATE_STEP";
  private static final String DATASINK_TYPE_POSTGIS = "POSTGIS";
  private static final String DATASINK_TYPE_FROST = "FROST";

  /**
   * FROST lives in its own Postgres database (see {@code
   * dev-environment/frost/docker-compose.yaml}), exposed to Trino as a separate catalog from the
   * one PostGIS/GeoServer sinks use ({@code geodata} — see {@code
   * dev-environment/trino-superset/trino/start-trino.sh}, which generates both {@code
   * sensordata.properties} and {@code geodata.properties}). Superset's only registered Trino
   * connection ({@code CORE}) defaults to the {@code geodata} catalog. FROST datasets set their
   * catalog explicitly because they live in the separate {@code sensordata} catalog.
   */
  private static final String SENSORDATA_CATALOG = "sensordata";

  /** One FROST-scoped view, paired with the human-readable label its Superset dataset gets. */
  private record FrostView(String schemaName, String label) {}

  private static final List<FrostView> FROST_SCOPED_VIEWS =
      List.of(
          new FrostView("things_scoped", "Things"),
          new FrostView("datastreams_scoped", "Datastreams"),
          new FrostView("observations_scoped", "Observations"),
          new FrostView("sensors_scoped", "Sensors"),
          new FrostView("locations_scoped", "Locations"),
          new FrostView("features_scoped", "Features"),
          new FrostView("hist_locations_scoped", "Historical Locations"));

  private String baseUrl;
  private String adminUsername;
  private String adminPassword;
  private int legacyTrinoDatabaseId;
  private SupersetResourceClient.TrinoDatabaseConfig trinoDatabaseConfig;
  private Client supersetClient;
  private SupersetSession session;
  private SupersetResourceClient resourceClient;

  /** No-arg constructor for ServiceLoader discovery. */
  public SupersetSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    baseUrl = requireProperty("url").replaceAll("/+$", "");
    adminUsername = requireProperty("admin.username");
    adminPassword = requireProperty("admin.password");
    String databaseIdStr = getProperty("trino.database-id");
    if (databaseIdStr != null && !databaseIdStr.isBlank()) {
      try {
        legacyTrinoDatabaseId = Integer.parseInt(databaseIdStr.trim());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "superset.trino.database-id must be a valid integer, got: " + databaseIdStr);
      }
    }

    String trinoUrl = getProperty("trino.url");
    if (trinoUrl != null && !trinoUrl.isBlank()) {
      trinoDatabaseConfig =
          new SupersetResourceClient.TrinoDatabaseConfig(
              getProperty("trino.database-name", "CORE"),
              java.net.URI.create(trinoUrl),
              getProperty("trino.username"),
              getProperty("trino.password"),
              getProperty("trino.catalog", "geodata"),
              getProperty("trino.schema", "information_schema"),
              getProperty("trino.http-scheme", "https"),
              Boolean.parseBoolean(getProperty("trino.tls-verify", "false")));
    }
    supersetClient =
        ClientBuilder.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    log.info(
        "SupersetSagaHandler initialized for {}, trinoDatabase={}",
        Encode.forJava(baseUrl),
        trinoDatabaseConfig == null
            ? "legacy-id"
            : Encode.forJava(trinoDatabaseConfig.databaseName()));
  }

  private String requireProperty(String key) {
    String value = getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("superset." + key + " must not be blank");
    }
    return value;
  }

  @Override
  protected SagaCommandResult doHandle(SagaCommandMessage command) {
    ensureAuthenticated();
    boolean compensation = COMPENSATE_TYPE.equals(command.type());
    return switch (command.operation()) {
      case "REGISTER_DATASET" -> register(command);
      case "DEREGISTER_DATASET" -> deregister(command, compensation);
      default -> unknownOperation(command);
    };
  }

  /**
   * {@link SupersetSession} declares checked adapter exceptions (a leftover from its original
   * Kafka-adapter caller); {@code doHandle} can't add checked exceptions to an override whose
   * superclass signature has none, so a failure here is wrapped as an unchecked {@link
   * SagaApiException} — caught by {@link #handle} like any other Superset API failure.
   */
  private void ensureAuthenticated() {
    if (session == null) {
      session = new SupersetSession(supersetClient, baseUrl, adminUsername, adminPassword);
      resourceClient =
          new SupersetResourceClient(
              supersetClient, session, baseUrl, legacyTrinoDatabaseId, trinoDatabaseConfig);
    }
    if (session.isAuthenticated()) {
      return;
    }
    try {
      session.authenticate();
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw new SagaApiException("Superset authentication failed: " + e.getMessage(), 401);
    }
  }

  @Override
  public void close() {
    super.close();
    if (supersetClient != null) {
      supersetClient.close();
      supersetClient = null;
    }
  }

  // ── REGISTER_DATASET ─────────────────────────────────────────────────────

  private SagaCommandResult register(SagaCommandMessage command) {
    int trinoDatabaseId = resourceClient.ensureTrinoDatabase();
    String datasetId = requireString(command, "datasetId");
    List<String> tables = postgisTableNames(command);
    List<DashboardEntry> dashboards = dashboardEntries(command);
    List<String> editorGroupIds = stringList(command.payload(), "editorGroupIds");
    if (!dashboards.isEmpty() && !editorGroupIds.isEmpty()) {
      resourceClient.ensureDatasetCreatorRole(trinoDatabaseId);
    }

    Map<String, Object> resultData = new LinkedHashMap<>();
    Map<String, Object> compensationData = new LinkedHashMap<>();
    List<Integer> datasetIds = new ArrayList<>();

    if (!tables.isEmpty()) {
      String datasetName = requireString(command, "datasetName");
      String schemaName =
          DatasetSchemaNames.fromDataset(datasetName, java.util.UUID.fromString(datasetId));
      Map<String, Integer> existingDatasets =
          resourceClient.findPostgisDatasetsByCivitasId(datasetId);
      for (String table : tables) {
        Integer existingDatasetId = existingDatasets.get(table);
        int supersetDatasetId;
        if (existingDatasetId != null) {
          // Move an existing Superset dataset to the PostGIS view schema in place. This keeps
          // saved charts attached while the physical ds_<uuid> schema remains available to
          // GeoServer.
          if (!resourceClient.updateDatasetDefinition(
              existingDatasetId, "geodata", schemaName, table, null)) {
            throw new SagaApiException(
                "Could not update Superset dataset id=" + existingDatasetId, 404);
          }
          resourceClient.updateDatasetExtra(
              existingDatasetId, Map.of(SupersetResourceClient.CIVITAS_DATASET_ID_KEY, datasetId));
          supersetDatasetId = existingDatasetId;
        } else {
          supersetDatasetId =
              resourceClient.createDataset(
                  "geodata",
                  schemaName,
                  table,
                  null,
                  Map.of(SupersetResourceClient.CIVITAS_DATASET_ID_KEY, datasetId),
                  editorGroupIds);
        }
        if (supersetDatasetId <= 0) {
          // Superset created (or already had) a dataset for this table but its id couldn't be
          // resolved — failing loudly here is essential: silently skipping would report this step
          // as SUCCESS while omitting the dataset from compensationData, leaving it un-deletable by
          // DEREGISTER_DATASET if the saga later compensates.
          throw new SagaApiException(
              "Could not resolve Superset dataset id for schema=" + schemaName + ", table=" + table,
              422);
        }
        if (!resourceClient.syncDatasetEditors(supersetDatasetId, editorGroupIds)) {
          throw new SagaApiException(
              "Superset dataset id=" + supersetDatasetId + " disappeared during editor sync", 404);
        }
        datasetIds.add(supersetDatasetId);
      }
      log.info(
          "Registered {} Superset PostGIS dataset(s) for datasetId={}, saga={}",
          tables.size(),
          Encode.forJava(datasetId),
          Encode.forJava(command.sagaId()));
    }

    if (hasFrostSink(command)) {
      String frostProjectId = requireString(command, "projectId");
      String datasetName = requireString(command, "datasetName");
      String datasetSchemaName =
          DatasetSchemaNames.fromDataset(datasetName, java.util.UUID.fromString(datasetId));
      // Search by the stable dataset stamp across schemas. A rename changes the readable schema
      // too, while updating the existing Superset dataset in place keeps saved charts attached.
      Map<String, Integer> existingFrostDatasets =
          resourceClient.findFrostDatasetsByCivitasId(datasetId);
      for (FrostView view : FROST_SCOPED_VIEWS) {
        String label = frostDatasetLabel(datasetName, view.label(), frostProjectId);
        String sql =
            "SELECT * FROM "
                + SENSORDATA_CATALOG
                + "."
                + datasetSchemaName
                + "."
                + view.schemaName();
        Integer existingDatasetId = existingFrostDatasets.get(view.schemaName());
        int supersetDatasetId;
        if (existingDatasetId != null
            && resourceClient.updateDatasetDefinition(
                existingDatasetId, SENSORDATA_CATALOG, datasetSchemaName, label, sql)) {
          supersetDatasetId = existingDatasetId;
        } else {
          // existingDatasetId != null but updateDatasetDefinition returned false means
          // Superset-side drift
          // (someone deleted it out of band) — falls through to recreate, self-healing like
          // syncDashboard does for dashboards.
          supersetDatasetId =
              resourceClient.createDataset(
                  SENSORDATA_CATALOG,
                  datasetSchemaName,
                  label,
                  sql,
                  Map.of(
                      SupersetResourceClient.CIVITAS_DATASET_ID_KEY,
                      datasetId,
                      SupersetResourceClient.FROST_VIEW_KEY,
                      view.schemaName()),
                  editorGroupIds);
        }
        if (supersetDatasetId <= 0) {
          throw new SagaApiException(
              "Could not resolve Superset dataset id for FROST view="
                  + view.schemaName()
                  + ", projectId="
                  + frostProjectId,
              422);
        }
        if (!resourceClient.syncDatasetEditors(supersetDatasetId, editorGroupIds)) {
          throw new SagaApiException(
              "Superset dataset id=" + supersetDatasetId + " disappeared during editor sync", 404);
        }
        datasetIds.add(supersetDatasetId);
      }
      log.info(
          "Registered {} Superset FROST dataset(s) for datasetId={}, saga={}",
          FROST_SCOPED_VIEWS.size(),
          Encode.forJava(datasetId),
          Encode.forJava(command.sagaId()));
    }

    if (!datasetIds.isEmpty()) {
      compensationData.put("datasetIds", datasetIds);
    }

    if (!dashboards.isEmpty()) {
      List<Integer> dashboardIds = new ArrayList<>();
      List<Map<String, Object>> dashboardResults = new ArrayList<>();
      for (DashboardEntry entry : dashboards) {
        int supersetDashboardId = syncDashboard(entry);
        dashboardIds.add(supersetDashboardId);
        dashboardResults.add(
            Map.of(
                "dashboardId", entry.id(),
                "externalDashboardId", String.valueOf(supersetDashboardId)));
      }
      compensationData.put("dashboardIds", dashboardIds);
      resultData.put("dashboards", dashboardResults);
      log.info(
          "Synced {} Superset dashboard(s) for datasetId={}, saga={}",
          dashboardIds.size(),
          Encode.forJava(datasetId),
          Encode.forJava(command.sagaId()));
    }

    for (String externalDashboardId : stringList(command.payload(), "deletedDashboardIds")) {
      int dashboardId;
      try {
        dashboardId = Integer.parseInt(externalDashboardId);
      } catch (NumberFormatException e) {
        throw new SagaApiException("Invalid Superset dashboard id in delete request", 422);
      }
      resourceClient.deleteDashboardAndUnusedCharts(dashboardId);
    }

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  /**
   * Creates the dashboard if {@code externalDashboardId} is {@code null}; otherwise renames the
   * existing one in place. Self-heals by creating a replacement if the referenced id is malformed
   * or no longer exists in Superset (drift), so the platform always gets back a valid id.
   *
   * <p>Which Civitas dataset this dashboard belongs to is authoritatively the platform's own {@code
   * Dashboard.dataSet} relationship, resolved by authz-repository's {@code DashboardDatasetService}
   * from this dashboard's Superset id — nothing needs to be written back onto the Superset
   * dashboard itself for authorization to work.
   */
  private int syncDashboard(DashboardEntry entry) {
    Integer existingId = parseSupersetId(entry.externalDashboardId());
    if (existingId == null) {
      return resourceClient.createDashboard(
          entry.title(),
          entry.description(),
          entry.editorGroupIds(),
          entry.viewerGroupIds(),
          entry.publicAccess(),
          entry.released());
    }
    if (resourceClient.syncDashboardAccess(
        existingId,
        entry.title(),
        entry.description(),
        entry.editorGroupIds(),
        entry.viewerGroupIds(),
        entry.publicAccess(),
        entry.released())) {
      return existingId;
    }
    log.warn(
        "Superset dashboard id={} referenced by dashboard {} no longer exists, recreating",
        existingId,
        Encode.forJava(entry.id()));
    return resourceClient.createDashboard(
        entry.title(),
        entry.description(),
        entry.editorGroupIds(),
        entry.viewerGroupIds(),
        entry.publicAccess(),
        entry.released());
  }

  private static Integer parseSupersetId(String externalId) {
    if (externalId == null) {
      return null;
    }
    try {
      return Integer.parseInt(externalId);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // ── DEREGISTER_DATASET (compensation) ────────────────────────────────────

  private SagaCommandResult deregister(SagaCommandMessage command, boolean compensation) {
    resourceClient.ensureTrinoDatabase();
    Map<String, Object> payload = command.payload();

    // Resolve datasets authoritatively via the civitasDatasetId Superset stamps on every dataset
    // it creates, unioned with whatever the register step's compensationData recorded — this
    // heals orphans left behind by a register attempt that never made it into compensationData
    // (e.g. an id that couldn't be resolved), not just the ones the saga happened to track.
    Set<Integer> datasetIds = new LinkedHashSet<>(intList(payload, "datasetIds"));
    if (payload.get("datasetId") instanceof String civitasDatasetId) {
      datasetIds.addAll(resourceClient.findPostgisDatasetsByCivitasId(civitasDatasetId).values());
      datasetIds.addAll(resourceClient.findFrostDatasetsByCivitasId(civitasDatasetId).values());
    }

    for (int datasetId : datasetIds) {
      resourceClient.deleteDataset(datasetId);
    }

    for (int dashboardId : intList(payload, "dashboardIds")) {
      resourceClient.deleteDashboardAndUnusedCharts(dashboardId);
    }
    log.info("Deregistered Superset resources, saga={}", Encode.forJava(command.sagaId()));
    return compensation
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private static List<String> postgisTableNames(SagaCommandMessage command) {
    if (!(command.payload().get("datasinks") instanceof List<?> sinks)) {
      return List.of();
    }
    List<String> tables = new ArrayList<>();
    for (Object item : sinks) {
      if (!(item instanceof Map<?, ?> sink) || !DATASINK_TYPE_POSTGIS.equals(sink.get("type"))) {
        continue;
      }
      if (sink.get("configuration") instanceof Map<?, ?> config
          && config.get("tableName") instanceof String tableName
          && !tableName.isBlank()) {
        tables.add(tableName);
      }
    }
    return tables;
  }

  private static boolean hasFrostSink(SagaCommandMessage command) {
    return command.payload().get("datasinks") instanceof List<?> sinks
        && sinks.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .anyMatch(sink -> DATASINK_TYPE_FROST.equals(sink.get("type")));
  }

  /**
   * A FROST view's Superset dataset label, e.g. {@code "DS_Frost — Observations (12)"}. The portal
   * allows duplicate dataset display names (see {@code FrostSagaHandler.frostProjectName}'s Javadoc
   * for the identical problem on the FROST project's own name), so {@code frostProjectId} —
   * globally unique and stable for the dataset's lifetime — keeps two same-named datasets' FROST
   * views from colliding on table_name. Never used as a lookup key itself: {@code
   * findFrostDatasetsByCivitasId}'s stamped {@code frostView} is, precisely so this label can
   * safely change on every rename.
   */
  private static String frostDatasetLabel(
      String datasetName, String viewLabel, String frostProjectId) {
    return datasetName + " — " + viewLabel + " (" + frostProjectId + ")";
  }

  private static List<Integer> intList(Map<String, Object> payload, String key) {
    if (!(payload.get(key) instanceof List<?> raw)) {
      return List.of();
    }
    List<Integer> ids = new ArrayList<>();
    for (Object o : raw) {
      if (o instanceof Number n) {
        ids.add(n.intValue());
      }
    }
    return ids;
  }

  /** One entry of the trigger's {@code dashboards} list — see {@code Dashboard}'s Javadoc. */
  private record DashboardEntry(
      String id,
      String modelUrn,
      String externalDashboardId,
      List<String> editorGroupIds,
      List<String> viewerGroupIds,
      boolean publicAccess,
      Map<?, ?> model) {
    String title() {
      return requiredModelString("title");
    }

    String description() {
      Object description = model.get("description");
      return description instanceof String value ? value : null;
    }

    boolean released() {
      return Boolean.TRUE.equals(model.get("released"));
    }

    private String requiredModelString(String key) {
      if (model.get(key) instanceof String value && !value.isBlank()) {
        return value;
      }
      throw new SagaApiException(
          "Dashboard model " + Encode.forJava(modelUrn) + " has no valid '" + key + "'", 422);
    }
  }

  private static List<DashboardEntry> dashboardEntries(SagaCommandMessage command) {
    if (!(command.payload().get("dashboards") instanceof List<?> raw)) {
      return List.of();
    }
    Map<?, ?> artifacts =
        command.payload().get("artifacts") instanceof Map<?, ?> values ? values : Map.of();
    List<DashboardEntry> entries = new ArrayList<>();
    for (Object item : raw) {
      if (item instanceof Map<?, ?> dash
          && dash.get("id") instanceof String id
          && dash.get("modelUrn") instanceof String modelUrn) {
        Object modelValue = artifacts.get(modelUrn);
        if (!(modelValue instanceof Map<?, ?> model)) {
          throw new SagaApiException(
              "Dashboard model " + Encode.forJava(modelUrn) + " is missing from saga artifacts",
              422);
        }
        entries.add(
            new DashboardEntry(
                id,
                modelUrn,
                dash.get("externalDashboardId") instanceof String ext ? ext : null,
                stringList(dash, "editorGroupIds"),
                stringList(dash, "viewerGroupIds"),
                Boolean.TRUE.equals(dash.get("publicAccess")),
                model));
      }
    }
    return entries;
  }

  private static List<String> stringList(Map<?, ?> payload, String key) {
    if (!(payload.get(key) instanceof List<?> raw)) {
      return List.of();
    }
    return raw.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }
}
