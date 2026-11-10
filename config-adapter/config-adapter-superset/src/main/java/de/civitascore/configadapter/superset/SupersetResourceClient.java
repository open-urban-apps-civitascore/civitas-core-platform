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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler.SagaApiException;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * superset.trino.database-name}), one schema per dataset.
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
  // Flask-AppBuilder's default FAB_API_MAX_PAGE_SIZE: a list call never gets more per page.
  private static final int LIST_PAGE_SIZE = 100;
  // Ends a list that never ends: 100 pages are 10,000 entries.
  private static final int MAX_LIST_PAGES = 100;

  private final Client client;
  private final SupersetSession session;
  private final String baseUrl;
  private volatile int trinoDatabaseId;
  private final TrinoDatabaseConfig trinoDatabaseConfig;
  private final ObjectMapper objectMapper = new ObjectMapper();
  // Group subjects are stable for the lifetime of this adapter. Resolve them once, because a
  // dataset release can sync several datasets and dashboards for the same CORE groups while
  // Superset applies a strict request rate limit.
  private final ConcurrentMap<String, List<Integer>> editorSubjectIdsByGroupSet =
      new ConcurrentHashMap<>();

  SupersetResourceClient(
      Client client, SupersetSession session, String baseUrl, int trinoDatabaseId) {
    this(client, session, baseUrl, trinoDatabaseId, null);
  }

  SupersetResourceClient(
      Client client,
      SupersetSession session,
      String baseUrl,
      int trinoDatabaseId,
      TrinoDatabaseConfig trinoDatabaseConfig) {
    this.client = client;
    this.session = session;
    this.baseUrl = baseUrl;
    this.trinoDatabaseId = trinoDatabaseId;
    this.trinoDatabaseConfig = trinoDatabaseConfig;
  }

  /** Ensure the CORE Trino connection exists and return its current Superset ID. */
  synchronized int ensureTrinoDatabase() {
    if (trinoDatabaseConfig == null) {
      if (trinoDatabaseId > 0) {
        return trinoDatabaseId;
      }
      throw new SagaApiException("Superset Trino connection configuration is missing", 503);
    }

    JsonNode databases = listDatabases();
    Integer databaseId = findDatabaseId(databases, trinoDatabaseConfig.databaseName());
    if (databaseId == null && "CORE".equals(trinoDatabaseConfig.databaseName())) {
      // Preserve existing dataset references when moving the former Trino connection to CORE.
      databaseId = findDatabaseId(databases, "Trino");
    }
    Map<String, Object> body = trinoDatabaseConfig.toSupersetPayload();
    if (databaseId == null) {
      Response response = null;
      try {
        response = postJson("/api/v1/database/", body);
        if (response.getStatus() == 401) {
          response = closeAndReauth(response, () -> postJson("/api/v1/database/", body));
        }
        if (response.getStatus() == 422) {
          // Another adapter replica may have created CORE at the same time. Re-read and reconcile.
          closeQuietly(response);
          response = null;
          databaseId = findDatabaseId(listDatabases(), trinoDatabaseConfig.databaseName());
          if (databaseId == null) {
            throw new SagaApiException(
                "Superset rejected creation of the CORE Trino database connection", 422);
          }
        } else {
          if (response.getStatus() >= 400) {
            // Superset can echo submitted database fields in validation errors. Do not expose a
            // Trino password embedded in sqlalchemy_uri through saga logs or error responses.
            throw new SagaApiException(
                "Superset could not create the CORE Trino database connection (HTTP "
                    + response.getStatus()
                    + ")",
                response.getStatus());
          }
          databaseId = parseId(response, "database");
        }
      } finally {
        closeQuietly(response);
      }
    }

    if (databaseId == null || databaseId <= 0) {
      throw new SagaApiException("Superset CORE Trino database ID could not be resolved", 503);
    }

    Response response = null;
    try {
      response = putJson("/api/v1/database/" + databaseId, body);
      if (response.getStatus() == 401) {
        int id = databaseId;
        response = closeAndReauth(response, () -> putJson("/api/v1/database/" + id, body));
      }
      if (response.getStatus() >= 400) {
        throw new SagaApiException(
            "Superset could not reconcile the CORE Trino database connection (HTTP "
                + response.getStatus()
                + ")",
            response.getStatus());
      }
      this.trinoDatabaseId = databaseId;
      LOG.info(
          "Reconciled Superset Trino database connection {}",
          Encode.forJava(trinoDatabaseConfig.databaseName()));
      return databaseId;
    } finally {
      closeQuietly(response);
    }
  }

  private JsonNode listDatabases() {
    Response response = null;
    try {
      response = getJson("/api/v1/database/", Map.of("q", "(page:0,page_size:1000)"));
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(
                response,
                () -> getJson("/api/v1/database/", Map.of("q", "(page:0,page_size:1000)")));
      }
      if (response.getStatus() >= 400) {
        throw new SagaApiException(
            "Superset could not list database connections (HTTP " + response.getStatus() + ")",
            response.getStatus());
      }
      return readTree(response).path("result");
    } finally {
      closeQuietly(response);
    }
  }

  private Integer findDatabaseId(JsonNode databases, String databaseName) {
    for (JsonNode database : databases) {
      if (databaseName.equals(database.path("database_name").asText())
          && database.path("id").canConvertToInt()) {
        return database.path("id").asInt();
      }
    }
    return null;
  }

  /** Ensure the least-privilege role used by dashboard editor groups can create datasets. */
  synchronized void ensureDatasetCreatorRole(int databaseId) {
    int roleId = findDatasetCreatorRoleIdOrNull();
    if (roleId == 0) {
      Response response = null;
      try {
        response = postJson("/api/v1/security/roles/", Map.of("name", DATASET_CREATOR_ROLE));
        if (response.getStatus() == 401) {
          response =
              closeAndReauth(
                  response,
                  () -> postJson("/api/v1/security/roles/", Map.of("name", DATASET_CREATOR_ROLE)));
        }
        if (response.getStatus() == 422 || response.getStatus() == 409) {
          closeQuietly(response);
          response = null;
          roleId = findDatasetCreatorRoleIdOrNull();
        } else {
          checkStatus(response, "create Superset dashboard dataset creator role");
          roleId = parseId(response, "role");
        }
      } finally {
        closeQuietly(response);
      }
    }
    if (roleId <= 0) {
      throw new SagaApiException(
          "Superset dashboard dataset creator role could not be resolved", 503);
    }
    final int resolvedRoleId = roleId;

    JsonNode database = getDatabase(databaseId);
    String databasePermission = database.path("result").path("perm").asText();
    if (databasePermission.isBlank()) {
      // Superset 7 formats the database permission menu as [name].(id:id).
      databasePermission = "[" + trinoDatabaseConfig.databaseName() + "].(id:" + databaseId + ")";
    }
    Map<String, Integer> desiredPermissions =
        findPermissionViewMenuIds(
            Map.of("database_access", databasePermission, "can_write", "Dataset"));

    Set<Integer> existingPermissionIds = getRolePermissionIds(resolvedRoleId);
    List<Integer> missingPermissionIds =
        desiredPermissions.values().stream()
            .filter(id -> !existingPermissionIds.contains(id))
            .toList();
    if (!missingPermissionIds.isEmpty()) {
      Response response = null;
      try {
        response =
            postJson(
                "/api/v1/security/roles/" + resolvedRoleId + "/permissions",
                Map.of("permission_view_menu_ids", missingPermissionIds));
        if (response.getStatus() == 401) {
          response =
              closeAndReauth(
                  response,
                  () ->
                      postJson(
                          "/api/v1/security/roles/" + resolvedRoleId + "/permissions",
                          Map.of("permission_view_menu_ids", missingPermissionIds)));
        }
        checkStatus(response, "grant Superset dashboard dataset creator permissions");
      } finally {
        closeQuietly(response);
      }
    }
  }

  private JsonNode getDatabase(int databaseId) {
    Response response = null;
    try {
      response = getJson("/api/v1/database/" + databaseId, Map.of());
      if (response.getStatus() == 401) {
        int id = databaseId;
        response = closeAndReauth(response, () -> getJson("/api/v1/database/" + id, Map.of()));
      }
      checkStatus(response, "get CORE Trino database connection");
      return readTree(response);
    } finally {
      closeQuietly(response);
    }
  }

  private Map<String, Integer> findPermissionViewMenuIds(Map<String, String> desired) {
    // Superset has more than 100 permissions out of the box, and one created late (database_access
    // on the CORE database) is on a later page.
    List<JsonNode> permissions =
        listAllPages(
            "/api/v1/security/permissions-resources/", "()", "list Superset permission resources");
    Map<String, Integer> found = new LinkedHashMap<>();
    for (JsonNode permission : permissions) {
      String permissionName = permission.path("permission").path("name").asText();
      String viewMenuName = permission.path("view_menu").path("name").asText();
      String key = permissionName + "\u0000" + viewMenuName;
      if (desired.containsKey(permissionName)
          && desired.get(permissionName).equals(viewMenuName)
          && permission.path("id").canConvertToInt()) {
        found.put(key, permission.path("id").asInt());
      }
    }
    for (Map.Entry<String, String> entry : desired.entrySet()) {
      if (!found.containsKey(entry.getKey() + "\u0000" + entry.getValue())) {
        throw new SagaApiException(
            "Required Superset permission is unavailable: "
                + entry.getKey()
                + " on "
                + Encode.forJava(entry.getValue()),
            503);
      }
    }
    Map<String, Integer> ordered = new LinkedHashMap<>();
    desired.forEach(
        (permission, viewMenu) ->
            ordered.put(permission, found.get(permission + "\u0000" + viewMenu)));
    return ordered;
  }

  private Set<Integer> getRolePermissionIds(int roleId) {
    Response response = null;
    try {
      response = getJson("/api/v1/security/roles/" + roleId + "/permissions/", Map.of());
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(
                response,
                () -> getJson("/api/v1/security/roles/" + roleId + "/permissions/", Map.of()));
      }
      checkStatus(response, "list dashboard dataset creator permissions");
      Set<Integer> ids = new LinkedHashSet<>();
      for (JsonNode permission : readTree(response).path("result")) {
        if (permission.path("id").canConvertToInt()) {
          ids.add(permission.path("id").asInt());
        }
      }
      return ids;
    } finally {
      closeQuietly(response);
    }
  }

  private int findDatasetCreatorRoleIdOrNull() {
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
      return 0;
    } finally {
      closeQuietly(response);
    }
  }

  /** Immutable, secret-bearing connection settings. Never log or render this value. */
  record TrinoDatabaseConfig(
      String databaseName,
      URI url,
      String username,
      String password,
      String catalog,
      String schema,
      String httpScheme,
      boolean verifyTls) {

    TrinoDatabaseConfig {
      if (databaseName == null
          || databaseName.isBlank()
          || url == null
          || url.getHost() == null
          || url.getUserInfo() != null
          || url.getQuery() != null
          || url.getFragment() != null
          || (url.getRawPath() != null
              && !url.getRawPath().isBlank()
              && !"/".equals(url.getRawPath()))
          || username == null
          || username.isBlank()
          || catalog == null
          || catalog.isBlank()
          || schema == null
          || schema.isBlank()
          || httpScheme == null
          || httpScheme.isBlank()) {
        throw new IllegalArgumentException("Superset Trino connection configuration is incomplete");
      }
      if (!httpScheme.equals("https") && !httpScheme.equals("http")) {
        throw new IllegalArgumentException("Superset Trino HTTP scheme must be http or https");
      }
      if ((!"https".equals(url.getScheme()) && !"http".equals(url.getScheme()))
          || !httpScheme.equals(url.getScheme())) {
        throw new IllegalArgumentException("Superset Trino URL scheme does not match http-scheme");
      }
    }

    @Override
    public String toString() {
      return "TrinoDatabaseConfig[databaseName=" + databaseName + ", password=<redacted>]";
    }

    Map<String, Object> toSupersetPayload() {
      String user = encodeUriPart(username);
      String userInfo = user;
      if (password != null && !password.isBlank()) {
        userInfo += ":" + encodeUriPart(password);
      }
      String sqlalchemyUri =
          "trino://"
              + userInfo
              + "@"
              + url.getRawAuthority()
              + "/"
              + encodeUriPart(catalog)
              + "/"
              + encodeUriPart(schema);
      Map<String, Object> connectArgs = Map.of("http_scheme", httpScheme, "verify", verifyTls);
      String extra;
      try {
        extra =
            new ObjectMapper()
                .writeValueAsString(
                    Map.of(
                        "metadata_params", Map.of(),
                        "engine_params", Map.of("connect_args", connectArgs),
                        "metadata_cache_timeout", Map.of(),
                        "schemas_allowed_for_file_upload", List.of(),
                        "allow_multi_catalog", true));
      } catch (Exception e) {
        throw new SagaApiException("Could not encode Superset Trino connection settings", 500);
      }
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("database_name", databaseName);
      payload.put("sqlalchemy_uri", sqlalchemyUri);
      payload.put("extra", extra);
      payload.put("expose_in_sqllab", true);
      payload.put("allow_run_async", false);
      payload.put("allow_file_upload", false);
      payload.put("allow_ctas", false);
      payload.put("allow_cvas", false);
      payload.put("allow_dml", false);
      payload.put("force_ctas_schema", null);
      payload.put("impersonate_user", true);
      payload.put("allow_multi_catalog", true);
      return payload;
    }

    private static String encodeUriPart(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
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
    return createDashboard(title, description, editorGroupIds, List.of(), false, released);
  }

  int createDashboard(
      String title,
      String description,
      List<String> editorGroupIds,
      List<String> viewerGroupIds,
      boolean publicAccess,
      boolean released) {
    return createDashboard(
        null, title, description, editorGroupIds, viewerGroupIds, publicAccess, released);
  }

  int createDashboard(
      String coreDashboardId,
      String title,
      String description,
      List<String> editorGroupIds,
      List<String> viewerGroupIds,
      boolean publicAccess,
      boolean released) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (coreDashboardId != null) {
      Integer existingId = findDashboardByUuid(coreDashboardId);
      if (existingId != null
          && syncDashboardAccess(
              existingId,
              title,
              description,
              editorGroupIds,
              viewerGroupIds,
              publicAccess,
              released)) {
        return existingId;
      }
      body.put("uuid", coreDashboardId);
    }
    body.put("dashboard_title", title);
    body.put("description", description);
    body.put("published", released);
    body.put("editors", resolveEditorSubjectIds(editorGroupIds));
    body.put("viewers", resolveViewerSubjectIds(viewerGroupIds, publicAccess && released));

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

  private Integer findDashboardByUuid(String dashboardUuid) {
    Response response = null;
    Map<String, String> query =
        Map.of(
            "q",
            "(filters:!((col:uuid,opr:eq,value:"
                + risonString(dashboardUuid)
                + ")),page:0,page_size:1)");
    try {
      response = getJson("/api/v1/dashboard/", query);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> getJson("/api/v1/dashboard/", query));
      }
      checkStatus(response, "find dashboard");
      JsonNode result = readTree(response).path("result");
      return result.isEmpty() ? null : result.get(0).path("id").asInt();
    } finally {
      closeQuietly(response);
    }
  }

  /** Resolve CORE UUID group names to Superset Subject ids used by dashboard editor ACLs. */
  private List<Integer> resolveEditorSubjectIds(List<String> groupIds) {
    if (groupIds == null || groupIds.isEmpty()) {
      return List.of();
    }
    List<String> canonicalGroupIds = canonicalGroupIds(groupIds);
    String cacheKey = String.join(",", canonicalGroupIds);
    return editorSubjectIdsByGroupSet.computeIfAbsent(
        cacheKey, ignored -> resolveEditorSubjectIdsUncached(canonicalGroupIds));
  }

  private List<Integer> resolveViewerSubjectIds(List<String> groupIds, boolean publicAccess) {
    Set<Integer> subjectIds = new LinkedHashSet<>(resolveEditorSubjectIds(groupIds));
    if (publicAccess) {
      boolean publicRoleFound = false;
      Response response = null;
      try {
        response =
            getJson(
                "/api/v1/dashboard/related/viewers", Map.of("q", "(filter:Public,page_size:1000)"));
        if (response.getStatus() == 401) {
          response =
              closeAndReauth(
                  response,
                  () ->
                      getJson(
                          "/api/v1/dashboard/related/viewers",
                          Map.of("q", "(filter:Public,page_size:1000)")));
        }
        checkStatus(response, "resolve Public viewer role");
        for (JsonNode subject : readTree(response).path("result")) {
          if ("Public".equals(subject.path("text").asText())
              && subject.path("extra").path("type").asInt() == 2
              && subject.path("value").canConvertToInt()) {
            subjectIds.add(subject.path("value").asInt());
            publicRoleFound = true;
          }
        }
        if (!publicRoleFound) {
          throw new SagaApiException("Superset Public role Subject was not found", 503);
        }
      } finally {
        closeQuietly(response);
      }
    }
    return List.copyOf(subjectIds);
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
    int roleId = findDatasetCreatorRoleIdOrNull();
    if (roleId <= 0) {
      throw new SagaApiException("Superset role " + DATASET_CREATOR_ROLE + " was not found", 503);
    }
    return roleId;
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

  /** Synchronize dashboard access from CORE. */
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
    return syncDashboardAccess(
        dashboardId, title, description, editorGroupIds, List.of(), false, released);
  }

  boolean syncDashboardAccess(
      int dashboardId,
      String title,
      String description,
      List<String> editorGroupIds,
      List<String> viewerGroupIds,
      boolean publicAccess,
      boolean released) {
    List<Integer> editorIds = resolveEditorSubjectIds(editorGroupIds);
    List<Integer> viewerIds = resolveViewerSubjectIds(viewerGroupIds, publicAccess && released);
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
      checkStatus(response, "get dashboard access");
    } finally {
      closeQuietly(response);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("dashboard_title", title);
    body.put("description", description);
    body.put("editors", editorIds);
    body.put("viewers", viewerIds);
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
        closeQuietly(response);
        response = null;
        int existingId = findDatasetMapBySchema(schemaName).getOrDefault(tableName, -1);
        if (existingId < 0) {
          // A 422 with no matching dataset in the schema isn't the idempotent "already exists"
          // case — it's some other validation failure (e.g. Superset couldn't introspect the
          // table through its underlying connection). Keep the response body out of saga errors:
          // it may contain database details or other sensitive information.
          throw new SagaApiException(
              "create dataset failed: schema="
                  + schemaName
                  + ", table="
                  + tableName
                  + " — HTTP 422 with no matching existing dataset",
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

  void updateDatasetExtra(int datasetId, Map<String, String> extraFields) {
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
   * Updates a virtual dataset's Trino schema and SQL in place while keeping its Superset id. This
   * moves existing FROST datasets to the per-CORE-dataset views without detaching saved charts.
   * Returns {@code false} if the dataset no longer exists (404, drift).
   */
  boolean updateDatasetDefinition(
      int datasetId, String catalog, String schemaName, String tableName, String sql) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("catalog", catalog);
    body.put("schema", schemaName);
    body.put("table_name", tableName);
    body.put("sql", sql);
    Response response = null;
    try {
      response = putJson("/api/v1/dataset/" + datasetId, body);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> putJson("/api/v1/dataset/" + datasetId, body));
      }
      if (response.getStatus() == 404) {
        LOG.info("Superset dataset id={} not found for definition update (drift)", datasetId);
        return false;
      }
      checkStatus(response, "update dataset definition");
      LOG.info(
          "Updated Superset dataset id={} to schema={}, table_name={}",
          datasetId,
          Encode.forJava(schemaName),
          Encode.forJava(tableName));
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
    Map<String, Integer> map = new LinkedHashMap<>();
    for (JsonNode item : listDatasets(databaseSchemaQuery(schemaName))) {
      String tableName = item.path("table_name").asText("");
      int id = item.path("id").asInt(-1);
      if (!tableName.isEmpty() && id != -1) {
        map.put(tableName, id);
      }
    }
    return map;
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
    List<Integer> ids = new ArrayList<>();
    for (JsonNode item : listDatasets(databaseSchemaQuery(schemaName))) {
      if (civitasDatasetId.equals(extractExtraField(item, CIVITAS_DATASET_ID_KEY))) {
        ids.add(item.path("id").asInt(-1));
      }
    }
    return ids;
  }

  /**
   * Finds PostGIS datasets by their stable CORE dataset stamp across schema renames. Superset does
   * not support filtering its dataset list by the {@code extra} JSON field, so the database-only
   * query is narrowed client-side. FROST SQL datasets are excluded by their separate marker.
   */
  Map<String, Integer> findPostgisDatasetsByCivitasId(String civitasDatasetId) {
    Map<String, Integer> datasets = new LinkedHashMap<>();
    for (JsonNode item : listDatasets(databaseOnlyQuery())) {
      String tableName = item.path("table_name").asText("");
      int id = item.path("id").asInt(-1);
      if (!tableName.isEmpty()
          && id != -1
          && civitasDatasetId.equals(extractExtraField(item, CIVITAS_DATASET_ID_KEY))
          && extractExtraField(item, FROST_VIEW_KEY) == null) {
        datasets.put(tableName, id);
      }
    }
    return datasets;
  }

  /**
   * Map of FROST view name (e.g. {@code observations_scoped}) to Superset dataset ID, for datasets
   * in the Trino database stamped with {@code extra.civitasDatasetId == civitasDatasetId} and an
   * {@code extra.frostView}. The table label and schema embed the mutable dataset display name, so
   * the stable matching key is the {@code frostView} marker together with the CORE dataset stamp.
   */
  Map<String, Integer> findFrostDatasetsByCivitasId(String civitasDatasetId) {
    Map<String, Integer> byView = new LinkedHashMap<>();
    for (JsonNode item : listDatasets(databaseOnlyQuery())) {
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
  }

  // ── Dashboard bundle import ──────────────────────────────────────────────

  /** The fields of a registered dataset that a dashboard bundle is bound to. */
  SupersetBundleBinder.DatasetTarget getDatasetTarget(int datasetId) {
    Response response = null;
    try {
      response = getJson("/api/v1/dataset/" + datasetId, Map.of());
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(response, () -> getJson("/api/v1/dataset/" + datasetId, Map.of()));
      }
      checkStatus(response, "get dataset");
      JsonNode result = readTree(response).path("result");
      String uuid = result.path("uuid").asText("");
      if (uuid.isBlank()) {
        throw new SagaApiException(
            "Superset does not report the UUID of dataset id=" + datasetId, 502);
      }
      return new SupersetBundleBinder.DatasetTarget(
          uuid,
          textOrNull(result, "schema"),
          textOrNull(result, "catalog"),
          textOrNull(result, "table_name"),
          textOrNull(result, "sql"),
          extraAsMap(result.path("extra")));
    } finally {
      closeQuietly(response);
    }
  }

  /** UUID of a Superset database connection, which a bundle's datasets refer to. */
  String getDatabaseUuid(int databaseId) {
    String uuid = getDatabase(databaseId).path("result").path("uuid").asText("");
    if (uuid.isBlank()) {
      throw new SagaApiException(
          "Superset does not report the UUID of database id=" + databaseId, 502);
    }
    return uuid;
  }

  /**
   * Imports a dashboard export ZIP and overwrites the objects whose UUIDs it carries. The bundle is
   * bound beforehand ({@link SupersetBundleBinder}), so it overwrites only this dashboard's shell,
   * its own charts and the datasets of its CORE dataset.
   */
  void importDashboardBundle(byte[] zip) {
    String boundary = "civitas-" + UUID.randomUUID();
    byte[] body = multipartBody(boundary, zip);
    Response response = null;
    try {
      response = postMultipart("/api/v1/dashboard/import/", boundary, body);
      if (response.getStatus() == 401) {
        response =
            closeAndReauth(
                response, () -> postMultipart("/api/v1/dashboard/import/", boundary, body));
      }
      int status = response.getStatus();
      if (status < 200 || status >= 300) {
        String reason = response.readEntity(String.class);
        throw new SagaApiException(
            "Superset dashboard import failed: HTTP "
                + status
                + " "
                + (reason == null ? "" : reason.substring(0, Math.min(reason.length(), 300))),
            status);
      }
      LOG.info("Imported Superset dashboard bundle ({} bytes)", zip.length);
    } finally {
      closeQuietly(response);
    }
  }

  /**
   * The export ZIP of one dashboard, as Superset's export endpoint returns it: YAML files under one
   * root folder. The export reads only; it changes nothing in Superset.
   *
   * @return the ZIP, or null if Superset does not know the dashboard
   */
  byte[] exportDashboardZip(int dashboardId) {
    Map<String, String> query = Map.of("q", "!(" + dashboardId + ")");
    Response response = null;
    try {
      response = getBinary("/api/v1/dashboard/export/", query);
      if (response.getStatus() == 401) {
        response = closeAndReauth(response, () -> getBinary("/api/v1/dashboard/export/", query));
      }
      if (response.getStatus() == 404) {
        return null;
      }
      checkStatus(response, "export dashboard");
      byte[] zip = response.readEntity(byte[].class);
      LOG.info("Exported Superset dashboard id={} ({} bytes)", dashboardId, zip.length);
      return zip;
    } finally {
      closeQuietly(response);
    }
  }

  private static byte[] multipartBody(String boundary, byte[] zip) {
    String head =
        "--"
            + boundary
            + "\r\n"
            + "Content-Disposition: form-data; name=\"formData\"; filename=\"dashboard.zip\"\r\n"
            + "Content-Type: application/zip\r\n\r\n";
    String tail =
        "\r\n--"
            + boundary
            + "\r\n"
            + "Content-Disposition: form-data; name=\"overwrite\"\r\n\r\n"
            + "true\r\n--"
            + boundary
            + "--\r\n";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(head.getBytes(StandardCharsets.UTF_8));
    out.writeBytes(zip);
    out.writeBytes(tail.getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  /** Superset reports a dataset's {@code extra} as a JSON string. */
  private Map<String, Object> extraAsMap(JsonNode extra) {
    try {
      JsonNode node = extra.isTextual() ? objectMapper.readTree(extra.asText()) : extra;
      if (node == null || !node.isObject()) {
        return Map.of();
      }
      return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  private List<JsonNode> listDatasets(String filterQuery) {
    return listAllPages("/api/v1/dataset/", filterQuery, "list datasets");
  }

  /**
   * Reads every page of a Superset list endpoint. Flask-AppBuilder cuts each list response to
   * {@code FAB_API_MAX_PAGE_SIZE} (100 by default) and reports no error, so one call with a larger
   * page size silently misses the rest. The client asks for exactly that page size, so each page
   * starts where the one before ended.
   *
   * @param path the list endpoint
   * @param risonQuery the rison query without paging, for example {@code (filters:!(...))} or
   *     {@code ()}
   * @param action what the call does, for the error message
   */
  List<JsonNode> listAllPages(String path, String risonQuery, String action) {
    String filter = risonQuery.substring(1, risonQuery.length() - 1);
    String prefix = filter.isEmpty() ? "(" : "(" + filter + ",";
    List<JsonNode> items = new ArrayList<>();
    for (int page = 0; page < MAX_LIST_PAGES; page++) {
      Map<String, String> query =
          Map.of("q", prefix + "page:" + page + ",page_size:" + LIST_PAGE_SIZE + ")");
      Response response = null;
      try {
        response = getJson(path, query);
        if (response.getStatus() == 401) {
          response = closeAndReauth(response, () -> getJson(path, query));
        }
        checkStatus(response, action);
        JsonNode body = readTree(response);
        JsonNode result = body.path("result");
        result.forEach(items::add);
        if (result.isEmpty() || items.size() >= body.path("count").asInt(Integer.MAX_VALUE)) {
          return items;
        }
      } finally {
        closeQuietly(response);
      }
    }
    throw new SagaApiException(
        "Superset list did not end after " + MAX_LIST_PAGES + " pages: " + action, 502);
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

  private String databaseOnlyQuery() {
    return "(filters:!((col:database,opr:rel_o_m,value:" + trinoDatabaseId + ")))";
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

  /** A GET whose answer is a file, such as the ZIP of a dashboard export. */
  private Response getBinary(String path, Map<String, String> queryParams) {
    var target = client.target(baseUrl).path(path);
    for (var entry : queryParams.entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    return target
        .request(MediaType.WILDCARD)
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

  private Response postMultipart(String path, String boundary, byte[] body) {
    return client
        .target(baseUrl)
        .path(path)
        .request(MediaType.APPLICATION_JSON)
        .header(AUTH_HEADER, "Bearer " + session.getAccessToken())
        .header(CSRF_HEADER, session.getCsrfToken())
        .header("Cookie", session.getSessionCookie())
        .post(Entity.entity(body, "multipart/form-data; boundary=" + boundary));
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
    throw new SagaApiException(operationDesc + " failed: HTTP " + status, status);
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
