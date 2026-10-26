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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler.SagaApiException;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superset REST operations for maintaining physical datasets and dashboards — shared by {@link
 * SupersetSagaHandler}. One Superset "database" per Civitas tenant (configured via {@code
 * superset.trino.database-id}), one schema per dataset.
 *
 * <p>Authentication (login + CSRF token) is handled by {@link SupersetSession}; this class re-runs
 * a request once via {@link SupersetSession#authenticate()} on a 401, then surfaces any other
 * non-2xx response as a {@link SagaApiException}. Dataset create is idempotent only for the 422
 * that actually means "already provisioned" — one whose schema/table matches an existing dataset;
 * any other 422 (e.g. Superset failing to introspect the table through its underlying connection)
 * is surfaced as a failure rather than silently treated as a success. An existing dashboard with
 * the same title is idempotent unconditionally. All DELETE operations are idempotent (404 is a
 * no-op).
 */
final class SupersetResourceClient {

  private static final Logger LOG = LoggerFactory.getLogger(SupersetResourceClient.class);

  private static final String AUTH_HEADER = "Authorization";
  private static final String CSRF_HEADER = "X-CSRFToken";
  private static final String DATASET_CREATOR_ROLE = "CORE Dashboard Dataset Creator";
  // Package-visible: SupersetSagaHandler builds the extraFields map these key names go into.
  static final String CIVITAS_DATASET_ID_KEY = "civitasDatasetId";
  static final String FROST_VIEW_KEY = "frostView";

  private final Client client;
  private final SupersetSession session;
  private final String baseUrl;
  private final int trinoDatabaseId;
  private final ObjectMapper objectMapper = new ObjectMapper();
  // Group subjects are stable for the lifetime of this adapter. Resolve them once, because a
  // dataset release can sync several datasets and dashboards for the same CORE groups while
  // Superset applies a strict request rate limit.
  private final ConcurrentMap<String, List<Integer>> editorSubjectIdsByGroupSet =
      new ConcurrentHashMap<>();

  SupersetResourceClient(
      Client client, SupersetSession session, String baseUrl, int trinoDatabaseId) {
    this.client = client;
    this.session = session;
    this.baseUrl = baseUrl;
    this.trinoDatabaseId = trinoDatabaseId;
  }

  // ── Dashboard ────────────────────────────────────────────────────────────

  /**
   * Creates a dashboard titled {@code title}. Returns the new dashboard id.
   *
   * <p>The caller supplies CORE's release state. Newly created dashboards are drafts unless the
   * CORE dashboard row is explicitly released.
   *
   * <p>Does not stamp {@code civitasDatasetId} anywhere on the Superset side: authorization
   * resolves which Civitas dataset a dashboard belongs to via the platform's own {@code
   * Dashboard.dataSet} relationship (authz-repository's {@code DashboardDatasetService}, keyed on
   * this dashboard's Superset id), not by reading anything back from Superset. A Superset-side copy
   * would only ever be as reliable as this write path and Superset's own schema validation.
   *
   * <p>When CORE editor groups are supplied, they are resolved to Superset Subject ids and sent in
   * {@code editors}. Superset's create schema expects Subject ids, not user or group ids.
   */
  int createDashboard(String title) {
    return createDashboard(title, List.of(), false);
  }

  int createDashboard(String title, List<String> editorGroupIds) {
    return createDashboard(title, editorGroupIds, false);
  }

  int createDashboard(String title, List<String> editorGroupIds, boolean released) {
    return createDashboard(title, null, editorGroupIds, released);
  }

  int createDashboard(
      String title, String description, List<String> editorGroupIds, boolean released) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("dashboard_title", title);
    body.put("description", description);
    body.put("published", released);
    if (editorGroupIds != null && !editorGroupIds.isEmpty()) {
      body.put("editors", resolveEditorSubjectIds(editorGroupIds));
    }

    Response response = null;
    try {
      response = postJson("/api/v1/dashboard/", body);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> postJson("/api/v1/dashboard/", body));
      }
      checkStatus(response, "create dashboard");
      int dashboardId = parseId(response, "dashboard");
      grantDatasetCreationToGroups(editorGroupIds);
      LOG.info("Created Superset dashboard id={} for title={}", dashboardId, Encode.forJava(title));
      return dashboardId;
    } finally {
      closeQuietly(response);
    }
  }

  /** Resolve CORE UUID group names to Superset Subject ids used by dashboard editor ACLs. */
  private List<Integer> resolveEditorSubjectIds(List<String> groupIds) {
    List<String> canonicalGroupIds = canonicalGroupIds(groupIds);
    String cacheKey = String.join(",", canonicalGroupIds);
    return editorSubjectIdsByGroupSet.computeIfAbsent(
        cacheKey, ignored -> resolveEditorSubjectIdsUncached(canonicalGroupIds));
  }

  /** Give only the dashboard editor groups access to the CORE connection and dataset creation. */
  private void grantDatasetCreationToGroups(List<String> groupIds) {
    if (groupIds == null || groupIds.isEmpty()) {
      return;
    }

    List<String> canonicalIds = canonicalGroupIds(groupIds);
    int roleId = findDatasetCreatorRoleId();
    JsonNode groups = listSecurityGroups();
    for (String groupId : canonicalIds) {
      String groupName = "core-" + groupId;
      JsonNode group = null;
      for (JsonNode candidate : groups.path("result")) {
        if (groupName.equals(candidate.path("name").asText())) {
          group = candidate;
          break;
        }
      }
      if (group == null || !group.path("id").canConvertToInt()) {
        throw new SagaApiException("Superset CORE group " + groupName + " was not found", 404);
      }

      Set<Integer> roleIds = new LinkedHashSet<>();
      for (JsonNode role : group.path("roles")) {
        if (role.path("id").canConvertToInt()) {
          roleIds.add(role.path("id").asInt());
        }
      }
      if (!roleIds.add(roleId)) {
        continue;
      }

      int groupIdValue = group.path("id").asInt();
      Response response = null;
      try {
        response = putJson("/api/v1/security/groups/" + groupIdValue, Map.of("roles", roleIds));
        if (response.getStatus() == 401) {
          response =
              closeAndReauth(
                  response,
                  () ->
                      putJson("/api/v1/security/groups/" + groupIdValue, Map.of("roles", roleIds)));
        }
        checkStatus(response, "grant CORE dataset creation to group");
        LOG.info("Granted CORE dataset creation to Superset group {}", Encode.forJava(groupName));
      } finally {
        closeQuietly(response);
      }
    }
  }

  private int findDatasetCreatorRoleId() {
    Response response = null;
    try {
      response = getJson("/api/v1/security/roles/", Map.of("q", "(page:0,page_size:1000)"));
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(
                response,
                () -> getJson("/api/v1/security/roles/", Map.of("q", "(page:0,page_size:1000)")));
      }
      checkStatus(response, "list Superset roles");
      for (JsonNode role : readTree(response).path("result")) {
        if (DATASET_CREATOR_ROLE.equals(role.path("name").asText())
            && role.path("id").canConvertToInt()) {
          return role.path("id").asInt();
        }
      }
      throw new SagaApiException("Superset role " + DATASET_CREATOR_ROLE + " was not found", 503);
    } finally {
      closeQuietly(response);
    }
  }

  private JsonNode listSecurityGroups() {
    Response response = null;
    try {
      response = getJson("/api/v1/security/groups/", Map.of("q", "(page:0,page_size:1000)"));
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(
                response,
                () -> getJson("/api/v1/security/groups/", Map.of("q", "(page:0,page_size:1000)")));
      }
      checkStatus(response, "list Superset groups");
      return readTree(response);
    } finally {
      closeQuietly(response);
    }
  }

  private List<String> canonicalGroupIds(List<String> groupIds) {
    return groupIds.stream()
        .map(
            groupId -> {
              try {
                return UUID.fromString(groupId).toString();
              } catch (IllegalArgumentException e) {
                throw new SagaApiException("Invalid CORE dashboard editor group id", 422);
              }
            })
        .distinct()
        .sorted()
        .toList();
  }

  private List<Integer> resolveEditorSubjectIdsUncached(List<String> groupIds) {
    Set<Integer> subjectIds = new LinkedHashSet<>();
    for (String groupId : groupIds) {
      String groupName = "core-" + groupId;
      List<Integer> resolved = findEditorSubjectIds(groupName);
      if (resolved.isEmpty()) {
        ensureCoreGroup(groupName);
        resolved = findEditorSubjectIds(groupName);
      }
      if (resolved.isEmpty()) {
        throw new SagaApiException(
            "Superset group subject " + groupName + " could not be created or resolved", 503);
      }
      subjectIds.addAll(resolved);
    }
    return List.copyOf(subjectIds);
  }

  private List<Integer> findEditorSubjectIds(String groupName) {
    Response response = null;
    try {
      response =
          getJson(
              "/api/v1/dashboard/related/editors",
              Map.of("q", "(filter:" + groupName + ",page_size:1000)"));
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> getDashboardEditorSubjects(groupName));
      }
      checkStatus(response, "resolve dashboard editor group");
      JsonNode results = objectMapper.readTree(response.readEntity(String.class)).path("result");
      List<Integer> subjectIds = new ArrayList<>();
      String managedDescription =
          "Managed by CIVITAS CORE; group id=" + groupName.substring("core-".length());
      for (JsonNode result : results) {
        if (result.path("value").canConvertToInt()
            && result.path("extra").path("secondary_label").asText().equals(managedDescription)) {
          subjectIds.add(result.path("value").asInt());
        }
      }
      return subjectIds;
    } catch (SagaApiException e) {
      throw e;
    } catch (Exception e) {
      throw new SagaApiException(
          "Could not resolve Superset editor group " + groupName + ": " + e.getMessage(), 500);
    } finally {
      closeQuietly(response);
    }
  }

  /** Create the CORE group shell in Superset so its Subject exists before editor ACL creation. */
  private void ensureCoreGroup(String groupName) {
    Map<String, Object> group = new LinkedHashMap<>();
    group.put("name", groupName);
    group.put("label", groupName);
    group.put("description", "Managed by CIVITAS CORE; group id=" + groupName.substring(5));
    group.put("roles", List.of());
    Response response = null;
    try {
      response = postJson("/api/v1/security/groups/", group);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> postJson("/api/v1/security/groups/", group));
      }
      if (response.getStatus() == 422) {
        return; // A concurrent dashboard sync created the same CORE group.
      }
      checkStatus(response, "create CORE group subject");
    } finally {
      closeQuietly(response);
    }
  }

  private Response getDashboardEditorSubjects(String groupName) {
    return getJson(
        "/api/v1/dashboard/related/editors",
        Map.of("q", "(filter:" + groupName + ",page_size:1000)"));
  }

  /**
   * Updates the dashboard title and CORE-controlled publication state. Returns {@code false} if
   * {@code dashboardId} no longer exists in Superset (404) so the caller can self-heal.
   */
  boolean updateDashboardTitle(int dashboardId, String title, boolean released) {
    return updateDashboardTitle(dashboardId, title, null, released);
  }

  boolean updateDashboardTitle(
      int dashboardId, String title, String description, boolean released) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("dashboard_title", title);
    body.put("description", description);
    body.put("published", released);
    Response response = null;
    try {
      response = putJson("/api/v1/dashboard/" + dashboardId, body);
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(response, () -> putJson("/api/v1/dashboard/" + dashboardId, body));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dashboard id={} not found for rename (drift)", dashboardId);
        return false;
      }
      checkStatus(response, "update dashboard");
      LOG.info("Renamed Superset dashboard id={} to title={}", dashboardId, Encode.forJava(title));
      return true;
    } finally {
      closeQuietly(response);
    }
  }

  /** Update the title and add the creator's CORE groups without removing existing editors. */
  boolean syncDashboardEditors(
      int dashboardId, String title, List<String> editorGroupIds, boolean released) {
    return syncDashboardEditors(dashboardId, title, null, editorGroupIds, released);
  }

  boolean syncDashboardEditors(
      int dashboardId,
      String title,
      String description,
      List<String> editorGroupIds,
      boolean released) {
    if (editorGroupIds == null || editorGroupIds.isEmpty()) {
      return updateDashboardTitle(dashboardId, title, description, released);
    }

    Set<Integer> editorIds = new LinkedHashSet<>(resolveEditorSubjectIds(editorGroupIds));
    Response response = null;
    try {
      response = getJson("/api/v1/dashboard/" + dashboardId, Map.of());
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(response, () -> getJson("/api/v1/dashboard/" + dashboardId, Map.of()));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dashboard id={} not found for editor sync (drift)", dashboardId);
        return false;
      }
      checkStatus(response, "get dashboard editors");
      JsonNode current = readTree(response).path("result");
      for (JsonNode editor : current.path("editors")) {
        if (editor.path("id").canConvertToInt()) {
          editorIds.add(editor.path("id").asInt());
        }
      }
    } finally {
      closeQuietly(response);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("dashboard_title", title);
    body.put("description", description);
    body.put("editors", List.copyOf(editorIds));
    body.put("published", released);
    response = null;
    try {
      response = putJson("/api/v1/dashboard/" + dashboardId, body);
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(response, () -> putJson("/api/v1/dashboard/" + dashboardId, body));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dashboard id={} not found for editor sync (drift)", dashboardId);
        return false;
      }
      checkStatus(response, "sync dashboard editors");
      grantDatasetCreationToGroups(editorGroupIds);
      LOG.info(
          "Synced title and {} editor(s) for Superset dashboard id={}",
          editorIds.size(),
          dashboardId);
      return true;
    } catch (SagaApiException e) {
      throw e;
    } catch (Exception e) {
      throw new SagaApiException(
          "Could not update Superset dashboard editors: " + e.getMessage(), 500);
    } finally {
      closeQuietly(response);
    }
  }

  /** Deletes a dashboard. Idempotent — a 404 is a no-op. */
  void deleteDashboard(int dashboardId) {
    Response response = null;
    try {
      response = deleteJson("/api/v1/dashboard/" + dashboardId);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> deleteJson("/api/v1/dashboard/" + dashboardId));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dashboard id={} already deleted (idempotent)", dashboardId);
        return;
      }
      checkStatus(response, "delete dashboard");
      LOG.info("Deleted Superset dashboard id={}", dashboardId);
    } finally {
      closeQuietly(response);
    }
  }

  // ── Dataset ──────────────────────────────────────────────────────────────

  /**
   * Creates a Superset physical dataset for {@code schemaName}.{@code tableName}. Returns the
   * Superset dataset ID — newly created, or the existing one if a 422 means it's already there. A
   * 422 that does NOT match an existing dataset is a genuine failure (thrown), not idempotency.
   */
  int createDataset(String schemaName, String tableName, String civitasDatasetId) {
    return createDataset(schemaName, tableName, civitasDatasetId, List.of());
  }

  int createDataset(
      String schemaName, String tableName, String civitasDatasetId, List<String> editorGroupIds) {
    return createDataset(
        null,
        schemaName,
        tableName,
        null,
        Map.of(CIVITAS_DATASET_ID_KEY, civitasDatasetId),
        editorGroupIds);
  }

  /**
   * Creates a Superset dataset for {@code schemaName}.{@code tableName} — a physical table pointer
   * when {@code sql} is {@code null}, or a virtual (SQL-defined) dataset otherwise. {@code
   * tableName} doubles as the idempotency key via {@link #findDatasetMapBySchema} in both cases, so
   * for a virtual dataset it must be a caller-chosen label unique within the schema (Superset only
   * requires uniqueness there, not that it name a real table when {@code sql} is set) — see FROST's
   * per-view, per-project labels in {@code SupersetSagaHandler}. Returns the Superset dataset ID —
   * newly created, or the existing one if a 422 means it's already there. A 422 that does NOT match
   * an existing dataset is a genuine failure (thrown), not idempotency.
   */
  int createDataset(String schemaName, String tableName, String sql, String civitasDatasetId) {
    return createDataset(
        schemaName, tableName, sql, Map.of(CIVITAS_DATASET_ID_KEY, civitasDatasetId), List.of());
  }

  /**
   * Same as {@link #createDataset(String, String, String, String)}, but stamps {@code extraFields}
   * onto the dataset's {@code extra} JSON instead of just {@code civitasDatasetId} — FROST datasets
   * also stamp {@code frostView} (see {@link SupersetSagaHandler}), since their {@code table_name}
   * includes the mutable dataset name and can no longer double as the idempotency lookup key the
   * way it does for PostGIS.
   */
  int createDataset(
      String schemaName, String tableName, String sql, Map<String, String> extraFields) {
    return createDataset(schemaName, tableName, sql, extraFields, List.of());
  }

  int createDataset(
      String schemaName,
      String tableName,
      String sql,
      Map<String, String> extraFields,
      List<String> editorGroupIds) {
    return createDataset(null, schemaName, tableName, sql, extraFields, editorGroupIds);
  }

  int createDataset(
      String catalog,
      String schemaName,
      String tableName,
      String sql,
      Map<String, String> extraFields,
      List<String> editorGroupIds) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("database", trinoDatabaseId);
    body.put("catalog", catalog);
    body.put("schema", schemaName);
    body.put("table_name", tableName);
    if (sql != null) {
      body.put("sql", sql);
    }
    if (editorGroupIds != null && !editorGroupIds.isEmpty()) {
      body.put("editors", resolveEditorSubjectIds(editorGroupIds));
    }

    Response response = null;
    try {
      response = postJson("/api/v1/dataset/", body);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> postJson("/api/v1/dataset/", body));
      }
      int status = response.getStatus();
      if (status == 201 || status == 200) {
        int datasetId = parseId(response, "dataset");
        closeQuietly(response);
        response = null;
        LOG.info(
            "Created Superset dataset schema={}, table={}, id={}",
            Encode.forJava(schemaName),
            Encode.forJava(tableName),
            datasetId);
        updateDatasetExtra(datasetId, extraFields);
        return datasetId;
      }
      if (status == 422) {
        String responseBody = safeReadBody(response);
        closeQuietly(response);
        response = null;
        int existingId = findDatasetMapBySchema(schemaName).getOrDefault(tableName, -1);
        if (existingId < 0) {
          // A 422 with no matching dataset in the schema isn't the idempotent "already exists"
          // case — it's some other validation failure (e.g. Superset couldn't introspect the
          // table through its underlying connection, which for Trino can mean an authorization
          // denial masquerading as "table not found"). Throwing here with the real response body
          // surfaces that cause directly, instead of returning -1 and letting the caller raise a
          // vaguer "could not resolve dataset id" error with no indication of why.
          throw new SagaApiException(
              "create dataset failed: schema="
                  + schemaName
                  + ", table="
                  + tableName
                  + " — HTTP 422 with no matching existing dataset: "
                  + responseBody,
              422);
        }
        LOG.info(
            "Superset dataset already exists (idempotent) schema={}, table={}, id={}",
            Encode.forJava(schemaName),
            Encode.forJava(tableName),
            existingId);
        updateDatasetExtra(existingId, extraFields);
        return existingId;
      }
      checkStatus(response, "create dataset");
      return -1; // unreachable — checkStatus always throws for a non-2xx/422 status
    } finally {
      closeQuietly(response);
    }
  }

  /** Add CORE creator groups to a Superset dataset without removing existing editors. */
  boolean syncDatasetEditors(int datasetId, List<String> editorGroupIds) {
    if (editorGroupIds == null || editorGroupIds.isEmpty()) {
      return true;
    }

    Set<Integer> editorIds = new LinkedHashSet<>(resolveEditorSubjectIds(editorGroupIds));
    Response response = null;
    try {
      response = getJson("/api/v1/dataset/" + datasetId, Map.of());
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(response, () -> getJson("/api/v1/dataset/" + datasetId, Map.of()));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dataset id={} not found for editor sync (drift)", datasetId);
        return false;
      }
      checkStatus(response, "get dataset editors");
      JsonNode current = readTree(response).path("result");
      for (JsonNode editor : current.path("editors")) {
        if (editor.path("id").canConvertToInt()) {
          editorIds.add(editor.path("id").asInt());
        }
      }
    } finally {
      closeQuietly(response);
    }

    Map<String, Object> body = Map.of("editors", List.copyOf(editorIds));
    response = null;
    try {
      response = putJson("/api/v1/dataset/" + datasetId, body);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> putJson("/api/v1/dataset/" + datasetId, body));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dataset id={} disappeared during editor sync", datasetId);
        return false;
      }
      checkStatus(response, "sync dataset editors");
      LOG.info("Synced {} editor(s) for Superset dataset id={}", editorIds.size(), datasetId);
      return true;
    } catch (SagaApiException e) {
      throw e;
    } catch (Exception e) {
      throw new SagaApiException(
          "Could not update Superset dataset editors: " + e.getMessage(), 500);
    } finally {
      closeQuietly(response);
    }
  }

  private void updateDatasetExtra(int datasetId, Map<String, String> extraFields) {
    String extra;
    try {
      extra = objectMapper.writeValueAsString(extraFields);
    } catch (Exception e) {
      throw new SagaApiException("Failed to serialize dataset extra: " + e.getMessage(), 500);
    }
    Map<String, Object> body = Map.of("extra", extra);

    Response response = null;
    try {
      response = putJson("/api/v1/dataset/" + datasetId, body);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> putJson("/api/v1/dataset/" + datasetId, body));
      }
      checkStatus(response, "update dataset extra");
      LOG.info("Updated extra for Superset dataset id={}", datasetId);
    } finally {
      closeQuietly(response);
    }
  }

  /**
   * Renames a dataset's {@code table_name} in place — used to keep FROST's virtual dataset labels
   * (which embed the dataset's display name) current across renames, mirroring {@link
   * #updateDashboardTitle}. Returns {@code false} if {@code datasetId} no longer exists in Superset
   * (404, drift) so the caller can self-heal by creating a replacement instead.
   */
  boolean renameDataset(int datasetId, String newTableName) {
    Map<String, Object> body = Map.of("table_name", newTableName);
    Response response = null;
    try {
      response = putJson("/api/v1/dataset/" + datasetId, body);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> putJson("/api/v1/dataset/" + datasetId, body));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dataset id={} not found for rename (drift)", datasetId);
        return false;
      }
      checkStatus(response, "rename dataset");
      LOG.info(
          "Renamed Superset dataset id={} to table_name={}",
          datasetId,
          Encode.forJava(newTableName));
      return true;
    } finally {
      closeQuietly(response);
    }
  }

  /** Deletes a dashboard and its charts only when no other Superset dashboard uses them. */
  void deleteDashboardAndUnusedCharts(int dashboardId) {
    List<Integer> chartIds = findDashboardChartIds(dashboardId);
    Set<Integer> unusedChartIds = new LinkedHashSet<>();
    for (int chartId : chartIds) {
      if (!isChartUsedByAnotherDashboard(chartId, dashboardId)) {
        unusedChartIds.add(chartId);
      }
    }

    deleteDashboard(dashboardId);
    for (int chartId : unusedChartIds) {
      deleteChart(chartId);
    }
  }

  private List<Integer> findDashboardChartIds(int dashboardId) {
    Response response = null;
    try {
      response = getJson("/api/v1/dashboard/" + dashboardId + "/charts", Map.of());
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(
                response, () -> getJson("/api/v1/dashboard/" + dashboardId + "/charts", Map.of()));
      }
      if (response.getStatus() == 404) {
        return List.of();
      }
      checkStatus(response, "get dashboard charts before delete");
      JsonNode result = readTree(response).path("result");
      Set<Integer> chartIds = new LinkedHashSet<>();
      for (JsonNode chart : result) {
        if (chart.path("id").canConvertToInt()) {
          chartIds.add(chart.path("id").asInt());
        }
      }
      return List.copyOf(chartIds);
    } finally {
      closeQuietly(response);
    }
  }

  private boolean isChartUsedByAnotherDashboard(int chartId, int dashboardId) {
    Response response = null;
    try {
      response = getJson("/api/v1/chart/" + chartId, Map.of());
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> getJson("/api/v1/chart/" + chartId, Map.of()));
      }
      if (response.getStatus() == 404) {
        return false;
      }
      checkStatus(response, "get chart dashboard references");
      for (JsonNode dashboard : readTree(response).path("result").path("dashboards")) {
        if (dashboard.path("id").canConvertToInt() && dashboard.path("id").asInt() != dashboardId) {
          return true;
        }
      }
      return false;
    } finally {
      closeQuietly(response);
    }
  }

  private void deleteChart(int chartId) {
    Response response = null;
    try {
      response = deleteJson("/api/v1/chart/" + chartId);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> deleteJson("/api/v1/chart/" + chartId));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset chart id={} already deleted (idempotent)", chartId);
        return;
      }
      checkStatus(response, "delete unused chart");
      LOG.info("Deleted unused Superset chart id={}", chartId);
    } finally {
      closeQuietly(response);
    }
  }

  /** Deletes a dataset. Idempotent — a 404 is a no-op. */
  void deleteDataset(int datasetId) {
    Response response = null;
    try {
      response = deleteJson("/api/v1/dataset/" + datasetId);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> deleteJson("/api/v1/dataset/" + datasetId));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dataset id={} already deleted (idempotent)", datasetId);
        return;
      }
      checkStatus(response, "delete dataset");
      LOG.info("Deleted Superset dataset id={}", datasetId);
    } finally {
      closeQuietly(response);
    }
  }

  /** Map of table name → Superset dataset ID, for every dataset in {@code schemaName}. */
  Map<String, Integer> findDatasetMapBySchema(String schemaName) {
    Map<String, String> query = Map.of("q", databaseSchemaQuery(schemaName));
    Response response = null;
    try {
      response = getJson("/api/v1/dataset/", query);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> getJson("/api/v1/dataset/", query));
      }
      checkStatus(response, "list datasets");
      Map<String, Integer> map = new LinkedHashMap<>();
      for (JsonNode item : readTree(response).path("result")) {
        String tableName = item.path("table_name").asText("");
        int id = item.path("id").asInt(-1);
        if (!tableName.isEmpty() && id != -1) {
          map.put(tableName, id);
        }
      }
      return map;
    } finally {
      closeQuietly(response);
    }
  }

  /**
   * IDs of every dataset in {@code schemaName} whose {@code extra.civitasDatasetId} equals {@code
   * civitasDatasetId} — the authoritative lookup for deregistration, independent of whatever a saga
   * step's compensationData happened to record. {@code schemaName} scopes the server-side query to
   * this tenant's schema — {@code extra} isn't a filterable column in Superset, so without a schema
   * filter this would have to list (and client-side filter) every dataset in the whole Superset
   * instance.
   */
  List<Integer> findDatasetIdsByCivitasId(String schemaName, String civitasDatasetId) {
    Map<String, String> query = Map.of("q", databaseSchemaQuery(schemaName));
    Response response = null;
    try {
      response = getJson("/api/v1/dataset/", query);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> getJson("/api/v1/dataset/", query));
      }
      checkStatus(response, "list datasets");
      List<Integer> ids = new ArrayList<>();
      for (JsonNode item : readTree(response).path("result")) {
        if (civitasDatasetId.equals(extractExtraField(item, CIVITAS_DATASET_ID_KEY))) {
          ids.add(item.path("id").asInt(-1));
        }
      }
      return ids;
    } finally {
      closeQuietly(response);
    }
  }

  /**
   * Map of FROST view name (e.g. {@code observations_scoped}) → Superset dataset ID, for every
   * dataset in {@code schemaName} stamped with {@code extra.civitasDatasetId == civitasDatasetId}
   * and an {@code extra.frostView}. The idempotency/rename-target lookup for FROST datasets: their
   * {@code table_name} embeds the mutable dataset display name (see {@code SupersetSagaHandler}),
   * so unlike {@link #findDatasetMapBySchema} it can't double as a stable matching key — this reads
   * the stamped, immutable {@code frostView} instead.
   */
  Map<String, Integer> findFrostDatasetsByCivitasId(String schemaName, String civitasDatasetId) {
    Map<String, String> query = Map.of("q", databaseSchemaQuery(schemaName));
    Response response = null;
    try {
      response = getJson("/api/v1/dataset/", query);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> getJson("/api/v1/dataset/", query));
      }
      checkStatus(response, "list datasets");
      Map<String, Integer> byView = new LinkedHashMap<>();
      for (JsonNode item : readTree(response).path("result")) {
        if (!civitasDatasetId.equals(extractExtraField(item, CIVITAS_DATASET_ID_KEY))) {
          continue;
        }
        String view = extractExtraField(item, FROST_VIEW_KEY);
        int id = item.path("id").asInt(-1);
        if (view != null && id != -1) {
          byView.put(view, id);
        }
      }
      return byView;
    } finally {
      closeQuietly(response);
    }
  }

  private String extractExtraField(JsonNode item, String key) {
    String extra = item.path("extra").asText("");
    if (extra.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(extra).path(key).asText(null);
    } catch (Exception e) {
      return null;
    }
  }

  // ── Rison query building ─────────────────────────────────────────────────

  /**
   * A Rison {@code q} filter scoping {@code /api/v1/dataset/} to {@link #trinoDatabaseId}'s {@code
   * schemaName} — both are {@code search_columns} on Superset's Dataset REST API, so this is
   * evaluated server-side instead of paging through every dataset in the Superset instance.
   */
  private String databaseSchemaQuery(String schemaName) {
    return "(filters:!((col:database,opr:rel_o_m,value:"
        + trinoDatabaseId
        + "),(col:schema,opr:eq,value:"
        + risonString(schemaName)
        + ")))";
  }

  /** Rison-quotes a string value: wraps in {@code '...'}, escaping {@code !} and {@code '}. */
  static String risonString(String value) {
    return "'" + value.replace("!", "!!").replace("'", "!'") + "'";
  }

  // ── HTTP helpers ─────────────────────────────────────────────────────────

  private Response postJson(String path, Object body) {
    return client
        .target(baseUrl)
        .path(path)
        .request(MediaType.APPLICATION_JSON)
        .header(AUTH_HEADER, "Bearer " + session.getAccessToken())
        .header(CSRF_HEADER, session.getCsrfToken())
        .header("Cookie", session.getSessionCookie())
        .post(Entity.json(body));
  }

  private Response getJson(String path, Map<String, String> queryParams) {
    var target = client.target(baseUrl).path(path);
    for (var entry : queryParams.entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    return target
        .request(MediaType.APPLICATION_JSON)
        .header(AUTH_HEADER, "Bearer " + session.getAccessToken())
        .get();
  }

  private Response putJson(String path, Object body) {
    return client
        .target(baseUrl)
        .path(path)
        .request(MediaType.APPLICATION_JSON)
        .header(AUTH_HEADER, "Bearer " + session.getAccessToken())
        .header(CSRF_HEADER, session.getCsrfToken())
        .header("Cookie", session.getSessionCookie())
        .put(Entity.json(body));
  }

  private Response deleteJson(String path) {
    return client
        .target(baseUrl)
        .path(path)
        .request(MediaType.APPLICATION_JSON)
        .header(AUTH_HEADER, "Bearer " + session.getAccessToken())
        .header(CSRF_HEADER, session.getCsrfToken())
        .header("Cookie", session.getSessionCookie())
        .delete();
  }

  /**
   * Refreshes the session and retries {@code retryRequest} once. {@link SupersetSession} declares
   * checked adapter exceptions (a leftover from its original Kafka-adapter caller); this class
   * exposes only unchecked failures, so a re-auth failure is surfaced as a {@link SagaApiException}
   * instead.
   */
  private Response closeAndReauth(Response stale, ResponseSupplier retryRequest) {
    closeQuietly(stale);
    session.invalidate();
    try {
      session.authenticate();
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw new SagaApiException("Superset re-authentication failed: " + e.getMessage(), 401);
    }
    return retryRequest.get();
  }

  @FunctionalInterface
  private interface ResponseSupplier {
    Response get();
  }

  private JsonNode readTree(Response response) {
    try {
      return objectMapper.readTree(response.readEntity(String.class));
    } catch (Exception e) {
      throw new SagaApiException(
          "Failed to parse Superset response: " + e.getMessage(), response.getStatus());
    }
  }

  private int parseId(Response response, String resourceType) {
    JsonNode root = readTree(response);
    int id = root.path("id").asInt(-1);
    if (id == -1) {
      throw new SagaApiException(
          "Create response for " + resourceType + " did not contain id", response.getStatus());
    }
    return id;
  }

  private void checkStatus(Response response, String operationDesc) {
    int status = response.getStatus();
    if (status >= 200 && status < 300) {
      return;
    }
    throw new SagaApiException(
        operationDesc + " failed: HTTP " + status + " — " + safeReadBody(response), status);
  }

  private String safeReadBody(Response response) {
    try {
      return response.readEntity(String.class);
    } catch (Exception e) {
      return "(unreadable)";
    }
  }

  private static void closeQuietly(Response response) {
    if (response != null) {
      try {
        response.close();
      } catch (Exception ignored) {
      }
    }
  }
}
