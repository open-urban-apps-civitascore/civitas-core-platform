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
import de.civitascore.configadapter.model.dataset.WorkspaceNames;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * sink is present); a {@code dashboards} list of {@code {id, name, externalDashboardId}} (see
 * {@code de.civitascore.configadapter.model.dataset.Dashboard}). The PostGIS Superset schema is
 * derived from {@code datasetId} via {@link WorkspaceNames#fromDatasetId} — the same rule {@code
 * PostgisSagaHandler}'s {@code PROVISION_SINK} step uses to name the schema, so the two agree on
 * where the table actually lives. FROST datasets have no per-dataset schema — they all live in the
 * shared {@link #FROST_SCHEMA}, isolated per Civitas dataset by baking {@code projectId} into each
 * dataset's {@code sql} (defense in depth only: the real security boundary is OPA's row-filter on
 * the underlying Trino query, see {@code authz/rego/policy/trino_row_filters.rego}).
 */
public class SupersetSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "superset";
  private static final String COMPENSATE_TYPE = "COMPENSATE_STEP";
  private static final String DATASINK_TYPE_POSTGIS = "POSTGIS";
  private static final String DATASINK_TYPE_FROST = "FROST";

  /**
   * FROST lives in its own Postgres database (see {@code
   * dev-environment/frost/docker-compose.yaml}), exposed to Trino as a separate catalog from the
   * one PostGIS/GeoServer sinks use ({@code civitas} — see {@code
   * dev-environment/trino-superset/trino/start-trino.sh}, which generates both {@code
   * frost.properties} and {@code civitas.properties}). Superset's only registered Trino connection
   * ({@code trinoDatabaseId}) defaults to the {@code civitas} catalog, so every FROST SQL reference
   * below must be catalog-qualified with this constant — an unqualified {@code frost_scoped.*}
   * would resolve against {@code civitas.frost_scoped}, which doesn't exist.
   */
  private static final String FROST_CATALOG = "frost";

  /**
   * The shared Postgres schema (see {@code
   * dev-environment/frost/migrations/01-create-scoped-views.sql}) exposing FROST's
   * Things/Datastreams/Observations etc. to Trino, pre-filtered per row by {@code frost_project_id}
   * — unlike PostGIS, FROST has no per-dataset schema to point a Superset dataset at directly, so
   * each entry below becomes a virtual (SQL-defined) Superset dataset instead of a physical table
   * pointer. {@code projects_scoped} is deliberately excluded: it's always exactly one row (the
   * project itself), not useful as a chartable dataset on its own.
   */
  private static final String FROST_SCHEMA = "frost_scoped";

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
  private int trinoDatabaseId;
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
    String databaseIdStr = requireProperty("trino.database-id");
    try {
      trinoDatabaseId = Integer.parseInt(databaseIdStr.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "superset.trino.database-id must be a valid integer, got: " + databaseIdStr);
    }
    log.info(
        "SupersetSagaHandler initialized for {}, trinoDatabaseId={}",
        Encode.forJava(baseUrl),
        trinoDatabaseId);
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
      session = new SupersetSession(client(), baseUrl, adminUsername, adminPassword);
      resourceClient = new SupersetResourceClient(client(), session, baseUrl, trinoDatabaseId);
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

  // ── REGISTER_DATASET ─────────────────────────────────────────────────────

  private SagaCommandResult register(SagaCommandMessage command) {
    String datasetId = requireString(command, "datasetId");
    List<String> tables = postgisTableNames(command);
    List<DashboardEntry> dashboards = dashboardEntries(command);

    Map<String, Object> resultData = new LinkedHashMap<>();
    Map<String, Object> compensationData = new LinkedHashMap<>();
    List<Integer> datasetIds = new ArrayList<>();

    if (!tables.isEmpty()) {
      String schemaName = WorkspaceNames.fromDatasetId(datasetId);
      Map<String, Integer> existingDatasets = resourceClient.findDatasetMapBySchema(schemaName);
      for (String table : tables) {
        Integer existingDatasetId = existingDatasets.get(table);
        int supersetDatasetId =
            existingDatasetId != null
                ? existingDatasetId
                : resourceClient.createDataset(schemaName, table, datasetId);
        if (supersetDatasetId <= 0) {
          // Superset created (or already had) a dataset for this table but its id couldn't be
          // resolved — failing loudly here is essential: silently skipping would report this step
          // as SUCCESS while omitting the dataset from compensationData, leaving it un-deletable by
          // DEREGISTER_DATASET if the saga later compensates.
          throw new SagaApiException(
              "Could not resolve Superset dataset id for schema=" + schemaName + ", table=" + table,
              422);
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
      if (!frostProjectId.matches("\\d+")) {
        // frostProjectId is spliced directly into a raw SQL string below — Superset's dataset API
        // takes freeform SQL, not a parameterized query, so this is the injection guard.
        throw new SagaApiException(
            "projectId must be numeric for FROST Superset datasets, got: "
                + Encode.forJava(frostProjectId),
            422);
      }
      String datasetName = requireString(command, "datasetName");
      // Keyed by view, not table_name: unlike PostGIS's per-dataset schema, every FROST dataset's
      // table_name embeds the (mutable) dataset display name, so it can't double as the
      // idempotency/rename-target lookup the way postgisTableNames' does — extra.frostView is the
      // stable key instead (see findFrostDatasetsByCivitasId).
      Map<String, Integer> existingFrostDatasets =
          resourceClient.findFrostDatasetsByCivitasId(FROST_SCHEMA, datasetId);
      for (FrostView view : FROST_SCOPED_VIEWS) {
        String label = frostDatasetLabel(datasetName, view.label(), frostProjectId);
        Integer existingDatasetId = existingFrostDatasets.get(view.schemaName());
        int supersetDatasetId;
        if (existingDatasetId != null && resourceClient.renameDataset(existingDatasetId, label)) {
          supersetDatasetId = existingDatasetId;
        } else {
          // existingDatasetId != null but renameDataset returned false means Superset-side drift
          // (someone deleted it out of band) — falls through to recreate, self-healing like
          // syncDashboard does for dashboards.
          supersetDatasetId =
              resourceClient.createDataset(
                  FROST_SCHEMA,
                  label,
                  // Catalog-qualified: this dataset is registered against the "civitas" Trino
                  // connection (trinoDatabaseId), whose default catalog is "civitas" — frost_scoped
                  // lives in the separate "frost" catalog, so an unqualified reference would
                  // resolve to civitas.frost_scoped (doesn't exist). Trino connections aren't
                  // restricted to their default catalog; cross-catalog references like this are
                  // fine as long as the connecting user has grants on both (confirmed by the
                  // existing, though currently disabled, create_frost_dataset.py bootstrap
                  // script).
                  "SELECT * FROM "
                      + FROST_CATALOG
                      + "."
                      + FROST_SCHEMA
                      + "."
                      + view.schemaName()
                      + " WHERE frost_project_id = '"
                      + frostProjectId
                      + "'",
                  Map.of(
                      SupersetResourceClient.CIVITAS_DATASET_ID_KEY,
                      datasetId,
                      SupersetResourceClient.FROST_VIEW_KEY,
                      view.schemaName()));
        }
        if (supersetDatasetId <= 0) {
          throw new SagaApiException(
              "Could not resolve Superset dataset id for FROST view="
                  + view.schemaName()
                  + ", projectId="
                  + frostProjectId,
              422);
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
      return resourceClient.createDashboard(entry.name());
    }
    if (resourceClient.updateDashboardTitle(existingId, entry.name())) {
      return existingId;
    }
    log.warn(
        "Superset dashboard id={} referenced by dashboard {} no longer exists, recreating",
        existingId,
        Encode.forJava(entry.id()));
    return resourceClient.createDashboard(entry.name());
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
    Map<String, Object> payload = command.payload();

    // Resolve datasets authoritatively via the civitasDatasetId Superset stamps on every dataset
    // it creates, unioned with whatever the register step's compensationData recorded — this
    // heals orphans left behind by a register attempt that never made it into compensationData
    // (e.g. an id that couldn't be resolved), not just the ones the saga happened to track.
    Set<Integer> datasetIds = new LinkedHashSet<>(intList(payload, "datasetIds"));
    if (payload.get("datasetId") instanceof String civitasDatasetId) {
      String schemaName = WorkspaceNames.fromDatasetId(civitasDatasetId);
      datasetIds.addAll(resourceClient.findDatasetIdsByCivitasId(schemaName, civitasDatasetId));
      datasetIds.addAll(resourceClient.findDatasetIdsByCivitasId(FROST_SCHEMA, civitasDatasetId));
    }

    for (int datasetId : datasetIds) {
      resourceClient.deleteDataset(datasetId);
    }

    for (int dashboardId : intList(payload, "dashboardIds")) {
      resourceClient.deleteDashboard(dashboardId);
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
  private record DashboardEntry(String id, String name, String externalDashboardId) {}

  private static List<DashboardEntry> dashboardEntries(SagaCommandMessage command) {
    if (!(command.payload().get("dashboards") instanceof List<?> raw)) {
      return List.of();
    }
    List<DashboardEntry> entries = new ArrayList<>();
    for (Object item : raw) {
      if (item instanceof Map<?, ?> dash
          && dash.get("id") instanceof String id
          && dash.get("name") instanceof String name) {
        entries.add(
            new DashboardEntry(
                id, name, dash.get("externalDashboardId") instanceof String ext ? ext : null));
      }
    }
    return entries;
  }
}
