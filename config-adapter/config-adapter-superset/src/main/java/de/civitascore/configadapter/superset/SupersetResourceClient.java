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
import java.util.List;
import java.util.Map;
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
  // Package-visible: SupersetSagaHandler builds the extraFields map these key names go into.
  static final String CIVITAS_DATASET_ID_KEY = "civitasDatasetId";
  static final String FROST_VIEW_KEY = "frostView";

  private final Client client;
  private final SupersetSession session;
  private final String baseUrl;
  private final int trinoDatabaseId;
  private final ObjectMapper objectMapper = new ObjectMapper();

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
   * <p>Left in Superset's default draft state deliberately: {@code raise_for_access}'s grant-based
   * permission check (a real {@code DATASET_DASHBOARD_READ}/{@code _WRITE} grant) is independent of
   * {@code published} and works regardless, so authorized users are never blocked by this. Only the
   * open-data/anonymous bypass path requires {@code published: true} — leaving that decision to an
   * explicit publish action in Superset (rather than defaulting every saga-provisioned dashboard to
   * it) avoids new dashboards becoming publicly/anonymously reachable the moment their backing
   * dataset happens to be flagged open data.
   *
   * <p>Does not stamp {@code civitasDatasetId} anywhere on the Superset side: authorization
   * resolves which Civitas dataset a dashboard belongs to via the platform's own {@code
   * Dashboard.dataSet} relationship (authz-repository's {@code DashboardDatasetService}, keyed on
   * this dashboard's Superset id), not by reading anything back from Superset. A Superset-side copy
   * would only ever be as reliable as this write path and Superset's own schema validation.
   *
   * <p>Doesn't send an owner/editor either: the old {@code owners} field (a plain user-id list) no
   * longer exists on Superset's dashboard-create schema — it's been replaced by {@code editors}/
   * {@code viewers} lists of Subject ids (a Subject wraps a user/role/group, distinct from a raw
   * FAB user id). Superset's own {@code populate_subjects()} defaults {@code editors} to the
   * current authenticated user when the field is omitted, which is exactly this service account —
   * so there's nothing to resolve or translate here.
   */
  int createDashboard(String title) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("dashboard_title", title);

    Response response = null;
    try {
      response = postJson("/api/v1/dashboard/", body);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> postJson("/api/v1/dashboard/", body));
      }
      checkStatus(response, "create dashboard");
      int dashboardId = parseId(response, "dashboard");
      LOG.info("Created Superset dashboard id={} for title={}", dashboardId, Encode.forJava(title));
      return dashboardId;
    } finally {
      closeQuietly(response);
    }
  }

  /**
   * Renames a dashboard in place. Does not touch {@code published}: that stays whatever the
   * dashboard owner set it to in Superset (see {@link #createDashboard} for why it isn't
   * force-enabled). Returns {@code false} if {@code dashboardId} no longer exists in Superset (404)
   * so the caller can self-heal by creating a replacement instead.
   */
  boolean updateDashboardTitle(int dashboardId, String title) {
    Map<String, Object> body = Map.of("dashboard_title", title);
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
    return createDataset(schemaName, tableName, null, civitasDatasetId);
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
        schemaName, tableName, sql, Map.of(CIVITAS_DATASET_ID_KEY, civitasDatasetId));
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
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("database", trinoDatabaseId);
    body.put("catalog", null);
    body.put("schema", schemaName);
    body.put("table_name", tableName);
    if (sql != null) {
      body.put("sql", sql);
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
