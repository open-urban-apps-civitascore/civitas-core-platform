/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.owasp.encoder.Encode;

/**
 * Saga command handler for the APISIX API Gateway. Handles route and upstream management for
 * dataset provisioning:
 *
 * <ul>
 *   <li>{@code CREATE_ROUTE} — creates upstream + route (uses datasetId as deterministic ID)
 *   <li>{@code UPDATE_ROUTE} — updates route configuration (plugin_config_id for auth)
 *   <li>{@code DELETE_ROUTE} — deletes route + upstream
 *   <li>{@code RESTORE_ROUTE} — restores route to previous auth configuration (update compensation)
 * </ul>
 *
 * <p>Uses PUT with deterministic IDs (derived from datasetId) to ensure idempotent operations.
 * Compensation: {@code DELETE_ROUTE} for create rollback, {@code RESTORE_ROUTE} for update
 * rollback.
 *
 * <p>Every {@code CREATE_ROUTE} pins the resulting APISIX route to the configured API virtual host
 * ({@code apisix.api.host}) and the {@code /v1/datasets/{id}} path prefix so APISIX matches it
 * deterministically (issue #1368). Initialization fails fast when {@code apisix.api.host} or {@code
 * apisix.api.public.url} are missing.
 *
 * <p><b>Why a single virtual host?</b> {@code /v1/datasets/{id}} is strictly more specific than a
 * {@code /v1/*} catch-all, so APISIX' radix tree dispatches the saga route deterministically
 * without a second host. The {@code hosts} filter on the saga route is kept for deployment
 * determinism and is pinned end-to-end by {@code
 * ApisixSagaHandlerRoutingTest#shouldWinOverV1CatchAllOnSameHost}.
 */
public class ApisixSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "apisix";
  private static final String ADMIN_URL_DEFAULT = "http://localhost:9180";
  private static final String ROUTES_PATH = "/apisix/admin/routes/";
  private static final String UPSTREAMS_PATH = "/apisix/admin/upstreams/";
  private static final String X_API_KEY = "X-API-KEY";
  private static final String DATASETS_PATH_PREFIX = "/v1/datasets/";

  private static final String DEFAULT_API_KEY_HEADER = "X-API-Key";

  /**
   * APISIX route label key. Stores the header name the adapter set under {@code
   * proxy-rewrite.headers.set} so a subsequent UPDATE/RESTORE can clean it up explicitly even after
   * a config-time scheme change (e.g. Basic Auth → API key) or header rename.
   */
  private static final String MANAGED_AUTH_HEADER_LABEL = "civitas-frost-upstream-auth-header";

  /**
   * Well-known header names the adapter has historically set in {@code headers.set} before the
   * label tracking was introduced. Used as a migration-safety fallback to clean up stale FROST
   * credentials on public flips for routes that pre-date the {@link #MANAGED_AUTH_HEADER_LABEL}
   * mechanism.
   *
   * <p><b>Migration scope:</b> covers Basic Auth ({@code Authorization}) and the default API key
   * header ({@code X-API-Key}). Routes provisioned under a custom {@code
   * apisix.frost.api.key.header} (e.g. {@code X-Frost-Key}) before label tracking shipped are NOT
   * automatically cleaned via this fallback — the adapter cannot enumerate historical custom values
   * it never recorded. For those, the adapter still cleans the currently-configured header name on
   * every flip; operators with a different historical name must either (a) trigger an UPDATE while
   * the adapter is still configured with the historical name, or (b) clean up manually via the
   * APISIX admin API. New routes (post-label) carry the {@link #MANAGED_AUTH_HEADER_LABEL} and are
   * unaffected.
   */
  private static final java.util.Set<String> LEGACY_ADAPTER_AUTH_HEADERS =
      java.util.Set.of("Authorization", DEFAULT_API_KEY_HEADER);

  private String adminApiUrl;
  private String adminApiKey;
  private String pluginConfigId;
  private String serviceId;
  private String apiHost;
  private String apiPublicUrl;
  private String frostUpstreamAuthHeaderName;
  private String frostUpstreamAuthHeaderValue;
  private String[] proxyRewriteHeadersToRemove;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public ApisixSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    this.adminApiUrl = getProperty("admin.url", ADMIN_URL_DEFAULT);
    this.adminApiKey = getProperty("admin.key");
    this.pluginConfigId = getProperty("plugin.config.id");
    this.serviceId = getProperty("service.id");
    String rawApiHost = getProperty("api.host");
    String rawApiPublicUrl = getProperty("api.public.url");
    String rawFrostUser = getProperty("frost.basic.auth.username");
    String rawFrostPass = getProperty("frost.basic.auth.password");
    String rawFrostApiKey = getProperty("frost.api.key");
    String rawFrostApiKeyHeader = getProperty("frost.api.key.header", DEFAULT_API_KEY_HEADER);

    if (adminApiKey == null || adminApiKey.isBlank()) {
      throw new IllegalArgumentException("The APISIX admin key cannot be null or blank.");
    }
    if (rawApiHost == null || rawApiHost.isBlank()) {
      throw new IllegalArgumentException(
          "apisix.api.host must be configured — published dataset routes are bound to this"
              + " virtual host so APISIX can match incoming requests (issue #1368).");
    }
    if (rawApiPublicUrl == null || rawApiPublicUrl.isBlank()) {
      throw new IllegalArgumentException(
          "apisix.api.public.url must be configured — used to build the publicUrl reported"
              + " back to the portal-backend after route creation (issue #1368).");
    }
    if (pluginConfigId == null || pluginConfigId.isBlank()) {
      throw new IllegalArgumentException(
          "apisix.plugin.config.id must be configured — private dataset routes rely on this"
              + " APISIX plugin_config to enforce client-side OIDC/OPA. Without it, private"
              + " datasets would be publicly reachable through the gateway.");
    }
    resolveFrostUpstreamAuth(rawFrostUser, rawFrostPass, rawFrostApiKey, rawFrostApiKeyHeader);
    this.proxyRewriteHeadersToRemove =
        parseHeaderRemoveList(getProperty("proxy.rewrite.headers.remove"));

    this.apiHost = rawApiHost;
    this.apiPublicUrl = stripTrailingSlash(rawApiPublicUrl);

    log.info(
        "ApisixSagaHandler initialized for: {} (api host: {}, frost upstream auth header: {})",
        Encode.forJava(adminApiUrl),
        Encode.forJava(apiHost),
        Encode.forJava(frostUpstreamAuthHeaderName));
  }

  /**
   * Resolves FROST upstream auth from the configured credentials, mirroring {@code
   * FrostAuthStrategy.create} so the gateway speaks the same scheme as the FROST adapter. Basic
   * Auth takes precedence when both are configured; at least one must be set.
   */
  private void resolveFrostUpstreamAuth(
      String basicAuthUser, String basicAuthPass, String apiKey, String apiKeyHeader) {
    boolean hasBasicAuth = basicAuthUser != null && !basicAuthUser.isBlank();
    boolean hasApiKey = apiKey != null && !apiKey.isBlank();

    if (!hasBasicAuth && !hasApiKey) {
      throw new IllegalArgumentException(
          "FROST upstream authentication not configured: provide either"
              + " apisix.frost.basic.auth.username (+password) or apisix.frost.api.key — APISIX"
              + " needs to authenticate against FROST when proxying private datasets.");
    }
    if (hasBasicAuth) {
      if (basicAuthPass == null || basicAuthPass.isBlank()) {
        throw new IllegalArgumentException(
            "apisix.frost.basic.auth.password must be configured when"
                + " apisix.frost.basic.auth.username is set.");
      }
      if (hasApiKey) {
        log.warn("Both apisix.frost basic auth and api key configured — using Basic Auth");
      }
      String encoded =
          Base64.getEncoder()
              .encodeToString(
                  (basicAuthUser + ":" + basicAuthPass).getBytes(StandardCharsets.UTF_8));
      this.frostUpstreamAuthHeaderName = "Authorization";
      this.frostUpstreamAuthHeaderValue = "Basic " + encoded;
    } else {
      if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
        throw new IllegalArgumentException(
            "apisix.frost.api.key.header must not be blank when apisix.frost.api.key is set —"
                + " otherwise APISIX would receive headers.set with an empty header name.");
      }
      this.frostUpstreamAuthHeaderName = apiKeyHeader;
      this.frostUpstreamAuthHeaderValue = apiKey;
    }
  }

  private static String stripTrailingSlash(String url) {
    if (url == null) {
      return null;
    }
    return url.replaceAll("/+$", "");
  }

  void setTestClient(Client client) {
    super.setClient(client);
  }

  @Override
  protected SagaCommandResult doHandle(SagaCommandMessage command) {
    return switch (command.operation()) {
      case "CREATE_ROUTE" -> handleCreateRoute(command);
      case "UPDATE_ROUTE" -> handleUpdateRoute(command);
      case "DELETE_ROUTE" -> handleDeleteRoute(command);
      case "RESTORE_ROUTE" -> handleRestoreRoute(command);
      default -> unknownOperation(command);
    };
  }

  private SagaCommandResult handleCreateRoute(SagaCommandMessage command) {
    String datasetId = requireString(command, "datasetId");
    String upstreamUrl = requireString(command, "upstreamUrl");
    Object openDataAccess = command.payload().getOrDefault("openDataAccess", false);

    // Parse upstream URL into host:port and path components
    // e.g. "http://civitas-frost:8080/FROST-Server/v1.1/Projects(1)"
    //   → node = "civitas-frost:8080", path = "/FROST-Server/v1.1/Projects(1)"
    URI upstream = URI.create(upstreamUrl);
    String upstreamNode =
        upstream.getPort() > 0 ? upstream.getHost() + ":" + upstream.getPort() : upstream.getHost();
    String upstreamPath = upstream.getPath() != null ? upstream.getPath() : "/";
    String scheme = upstream.getScheme() != null ? upstream.getScheme() : "http";

    // 1. Create upstream (PUT with deterministic ID)
    Map<String, Object> upstreamBody = buildUpstreamBody(upstreamNode, scheme);
    putResource(UPSTREAMS_PATH + datasetId, upstreamBody, "CREATE upstream");

    // 2. Create route (PUT with deterministic ID)
    Map<String, Object> routeBody =
        buildCreateRouteBody(datasetId, Boolean.TRUE.equals(openDataAccess), upstreamPath);
    putResource(ROUTES_PATH + datasetId, routeBody, "CREATE route");

    String publicUrl = apiPublicUrl + DATASETS_PATH_PREFIX + datasetId;
    Map<String, Object> resultData =
        Map.of("routeId", datasetId, "serviceId", datasetId, "publicUrl", publicUrl);
    Map<String, Object> compensationData = Map.of("routeId", datasetId, "serviceId", datasetId);

    log.info(
        "APISIX route created: datasetId={}, saga={}",
        Encode.forJava(datasetId),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  private SagaCommandResult handleUpdateRoute(SagaCommandMessage command) {
    String routeId = requireString(command, "routeId");
    String serviceId = requireString(command, "serviceId");
    boolean openDataAccess =
        Boolean.TRUE.equals(command.payload().getOrDefault("openDataAccess", false));

    // GET → mutate in memory → PUT. PATCH (merge-patch) cannot remove fields like
    // plugin_config_id or the upstream Authorization header, so a full replacement is required.
    Map<String, Object> route = readCurrentRoute(routeId);
    boolean previousOpenDataAccess = !routeIsPrivate(route);
    applyAuthConfig(route, openDataAccess);
    stripReadOnlyFields(route);
    putResource(ROUTES_PATH + routeId, route, "UPDATE route");

    Map<String, Object> resultData = Map.of("routeId", routeId, "serviceId", serviceId);
    Map<String, Object> compensationData =
        Map.of(
            "routeId", routeId,
            "serviceId", serviceId,
            "previousOpenDataAccess", previousOpenDataAccess);

    log.info(
        "APISIX route updated: routeId={}, saga={}",
        Encode.forJava(routeId),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  private SagaCommandResult handleDeleteRoute(SagaCommandMessage command) {
    String routeId = requireString(command, "routeId");
    String serviceId = requireString(command, "serviceId");

    // 1. Delete route first (route depends on upstream)
    deleteResource(ROUTES_PATH + routeId, "DELETE route");

    // 2. Delete upstream
    deleteResource(UPSTREAMS_PATH + serviceId, "DELETE upstream");

    log.info(
        "APISIX route deleted: routeId={}, saga={}",
        Encode.forJava(routeId),
        Encode.forJava(command.sagaId()));

    return "COMPENSATE_STEP".equals(command.type())
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  private SagaCommandResult handleRestoreRoute(SagaCommandMessage command) {
    String routeId = requireString(command, "routeId");
    requireString(command, "serviceId");
    boolean previousOpenDataAccess =
        Boolean.TRUE.equals(command.payload().getOrDefault("previousOpenDataAccess", false));

    Map<String, Object> route = readCurrentRoute(routeId);
    applyAuthConfig(route, previousOpenDataAccess);
    stripReadOnlyFields(route);
    putResource(ROUTES_PATH + routeId, route, "RESTORE route");

    log.info(
        "APISIX route restored: routeId={}, saga={}",
        Encode.forJava(routeId),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
  }

  /** Reads the full current route from APISIX, unwrapping the etcd "value" envelope. */
  private Map<String, Object> readCurrentRoute(String routeId) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(ROUTES_PATH + routeId)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .get()) {
      checkResponse(response, "GET route for UPDATE_ROUTE");
      @SuppressWarnings("unchecked")
      Map<String, Object> responseBody = response.readEntity(Map.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> routeValue =
          responseBody.containsKey("value")
              ? (Map<String, Object>) responseBody.get("value")
              : responseBody;
      return routeValue;
    }
  }

  /**
   * Sets or removes {@code plugin_config_id} and the upstream auth header in {@code proxy-rewrite}
   * according to {@code isOpenData}. Mutates the given route map in place.
   */
  private void applyAuthConfig(Map<String, Object> route, boolean isOpenData) {
    // Read the previously-set adapter header BEFORE flipping labels — we may need to remove
    // a stale entry left behind by a different auth scheme or a renamed API key header.
    String staleManagedHeader = readManagedAuthHeaderLabel(route);

    if (isOpenData) {
      route.remove("plugin_config_id");
      removeLabel(route, MANAGED_AUTH_HEADER_LABEL);
    } else {
      route.put("plugin_config_id", pluginConfigId);
      putLabel(route, MANAGED_AUTH_HEADER_LABEL, frostUpstreamAuthHeaderName);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> plugins = (Map<String, Object>) route.get("plugins");
    if (plugins == null) {
      return;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
    if (proxyRewrite == null) {
      return;
    }
    mergeProxyRewriteHeaders(proxyRewrite, isOpenData, staleManagedHeader);
  }

  /**
   * Merges the adapter-managed entries into an existing {@code proxy-rewrite.headers} block instead
   * of replacing it — preserves foreign {@code headers.add} entries, {@code headers.set} entries
   * owned by other operators, and additional {@code headers.remove} items.
   */
  @SuppressWarnings("unchecked")
  private void mergeProxyRewriteHeaders(
      Map<String, Object> proxyRewrite, boolean isOpenData, String staleManagedHeader) {
    Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
    if (headers == null) {
      headers = new HashMap<>();
    } else if (!(headers instanceof HashMap)) {
      // Jackson typically returns LinkedHashMap which is mutable, but other deserialisers may
      // return immutable views — defensively copy to guarantee in-place mutation works.
      headers = new HashMap<>(headers);
    }

    // headers.set — touch only adapter-managed FROST upstream auth entries. A stale entry from
    // a previous config (different scheme or renamed API key header) is identified via the
    // route label written when we last set it; remove it before applying the current value.
    Map<String, Object> set = (Map<String, Object>) headers.get("set");
    if (set == null) {
      set = new HashMap<>();
    } else if (!(set instanceof HashMap)) {
      set = new HashMap<>(set);
    }
    if (staleManagedHeader != null && !staleManagedHeader.equals(frostUpstreamAuthHeaderName)) {
      set.remove(staleManagedHeader);
    }
    if (isOpenData) {
      set.remove(frostUpstreamAuthHeaderName);
      // Public routes must never carry FROST credentials. Wipe well-known adapter-managed
      // header names unconditionally — this covers pre-label legacy routes, inconsistent state
      // left behind by external mutation, and the rare case of a config-time scheme change.
      // Non-auth foreign entries (e.g. user-added X-Trace-Id) are preserved.
      for (String legacy : LEGACY_ADAPTER_AUTH_HEADERS) {
        set.remove(legacy);
      }
    } else {
      set.put(frostUpstreamAuthHeaderName, frostUpstreamAuthHeaderValue);
    }
    if (set.isEmpty()) {
      headers.remove("set");
    } else {
      headers.put("set", set);
    }

    // headers.remove — union with the adapter's configured strip list. Foreign entries stay.
    if (proxyRewriteHeadersToRemove.length > 0) {
      List<String> remove = readStringList(headers.get("remove"));
      for (String entry : proxyRewriteHeadersToRemove) {
        if (!remove.contains(entry)) {
          remove.add(entry);
        }
      }
      headers.put("remove", remove);
    }

    if (headers.isEmpty()) {
      proxyRewrite.remove("headers");
    } else {
      proxyRewrite.put("headers", headers);
    }
  }

  private static List<String> readStringList(Object value) {
    if (value == null) {
      return new ArrayList<>();
    }
    if (value instanceof String[] arr) {
      return new ArrayList<>(java.util.Arrays.asList(arr));
    }
    if (value instanceof List<?> list) {
      List<String> out = new ArrayList<>(list.size());
      for (Object o : list) {
        if (o != null) {
          out.add(o.toString());
        }
      }
      return out;
    }
    return new ArrayList<>();
  }

  /** Strips APISIX-managed read-only fields so the route map is safe to PUT back. */
  private void stripReadOnlyFields(Map<String, Object> route) {
    route.remove("create_time");
    route.remove("update_time");
  }

  /**
   * Detects whether a route is currently configured as private. The single authoritative signal is
   * {@code plugin_config_id} — the gateway-side OIDC/OPA gate. Foreign {@code headers.set} entries
   * (operator-added trace headers, etc.) do not mark a route as private; relying on them would
   * misclassify public routes that an operator decorated independently.
   */
  private boolean routeIsPrivate(Map<String, Object> route) {
    Object existingPluginConfigId = route.get("plugin_config_id");
    return existingPluginConfigId != null
        && !(existingPluginConfigId instanceof String s && s.isBlank());
  }

  // ─── HTTP helpers ────────────────────────────────────────────────────────────

  private void putResource(String path, Map<String, Object> body, String operationDesc) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(path)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .put(Entity.json(body))) {
      checkResponse(response, operationDesc);
    }
  }

  private void deleteResource(String path, String operationDesc) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(path)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .delete()) {
      checkResponse(response, operationDesc);
    }
  }

  // ─── Body builders ───────────────────────────────────────────────────────────

  private Map<String, Object> buildUpstreamBody(String node, String scheme) {
    Map<String, Object> body = new HashMap<>();
    body.put("type", "roundrobin");
    body.put("scheme", scheme);
    body.put("nodes", Map.of(node, 1));
    return body;
  }

  /**
   * Builds the full route body for CREATE_ROUTE: URIs, host pinning, upstream binding,
   * proxy-rewrite (path + optional upstream Authorization header) and plugin_config_id for
   * gateway-side auth on non-public datasets. UPDATE/RESTORE use a separate GET → mutate → PUT flow
   * instead of building from scratch, because they must preserve existing fields.
   */
  private Map<String, Object> buildCreateRouteBody(
      String upstreamId, boolean isOpenData, String upstreamPath) {
    Map<String, Object> body = new HashMap<>();

    // Published-data routes are pinned to the configured API virtual host (apisix.api.host)
    // so APISIX deterministically matches them (issue #1368).
    String datasetPath = DATASETS_PATH_PREFIX + upstreamId;
    body.put("uris", new String[] {datasetPath, datasetPath + "/*"});
    body.put("hosts", new String[] {apiHost});
    body.put("upstream_id", upstreamId);
    if (serviceId != null) {
      body.put("service_id", serviceId);
    }
    body.put("status", 1);

    // Rewrite gateway path to upstream FROST path
    // e.g. /v1/datasets/{id}/Things → /FROST-Server/v1.1/Projects(1)/Things
    Map<String, Object> proxyRewrite = new HashMap<>();
    proxyRewrite.put(
        "regex_uri", new String[] {"^" + datasetPath + "(/.*)?$", upstreamPath + "$1"});

    // `headers.set` carries FROST upstream auth (private only); `headers.remove` strips
    // client-supplied internal headers (e.g. X-Allowed-Scope-Ids) and is applied to every saga
    // route — public ones too, since the strip is a general gateway protection that would
    // otherwise be lost to Route-over-PluginConfig plugin precedence.
    Map<String, Object> headers = buildProxyRewriteHeaders(isOpenData);
    if (!headers.isEmpty()) {
      proxyRewrite.put("headers", headers);
    }
    body.put("plugins", Map.of("proxy-rewrite", proxyRewrite));

    if (!isOpenData) {
      body.put("plugin_config_id", pluginConfigId);
      Map<String, Object> labels = new HashMap<>();
      labels.put(MANAGED_AUTH_HEADER_LABEL, frostUpstreamAuthHeaderName);
      body.put("labels", labels);
    }

    return body;
  }

  @SuppressWarnings("unchecked")
  private String readManagedAuthHeaderLabel(Map<String, Object> route) {
    Map<String, Object> labels = (Map<String, Object>) route.get("labels");
    if (labels == null) {
      return null;
    }
    Object value = labels.get(MANAGED_AUTH_HEADER_LABEL);
    return value instanceof String s && !s.isBlank() ? s : null;
  }

  @SuppressWarnings("unchecked")
  private void putLabel(Map<String, Object> route, String key, String value) {
    Map<String, Object> labels = (Map<String, Object>) route.get("labels");
    if (labels == null) {
      labels = new HashMap<>();
      route.put("labels", labels);
    } else if (!(labels instanceof HashMap)) {
      labels = new HashMap<>(labels);
      route.put("labels", labels);
    }
    labels.put(key, value);
  }

  @SuppressWarnings("unchecked")
  private void removeLabel(Map<String, Object> route, String key) {
    Map<String, Object> labels = (Map<String, Object>) route.get("labels");
    if (labels == null) {
      return;
    }
    if (!(labels instanceof HashMap)) {
      labels = new HashMap<>(labels);
      route.put("labels", labels);
    }
    labels.remove(key);
    if (labels.isEmpty()) {
      route.remove("labels");
    }
  }

  /**
   * Builds the {@code proxy-rewrite.headers} block for a route. {@code set} (FROST upstream auth)
   * is added only for private routes; {@code remove} (security strip list) is added for every route
   * when configured. Returns an empty map when neither applies, so callers can drop the block
   * entirely instead of writing {@code headers: {}}.
   */
  private Map<String, Object> buildProxyRewriteHeaders(boolean isOpenData) {
    Map<String, Object> headers = new HashMap<>();
    if (!isOpenData) {
      Map<String, Object> set = new HashMap<>();
      set.put(frostUpstreamAuthHeaderName, frostUpstreamAuthHeaderValue);
      headers.put("set", set);
    }
    if (proxyRewriteHeadersToRemove.length > 0) {
      headers.put("remove", proxyRewriteHeadersToRemove);
    }
    return headers;
  }

  /**
   * Parses the comma-separated {@code apisix.proxy.rewrite.headers.remove} property. APISIX merges
   * plugins by Route-over-PluginConfig precedence, so any {@code proxy-rewrite.headers.remove}
   * defined in the shared plugin_config is overridden by the route-level plugin. To preserve those
   * removals (e.g. stripping client-supplied {@code X-Allowed-Scope-Ids} that OPA sets), the
   * deployment configures the same list here and the adapter merges it into every saga route.
   */
  private static String[] parseHeaderRemoveList(String csv) {
    if (csv == null || csv.isBlank()) {
      return new String[0];
    }
    return java.util.Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toArray(String[]::new);
  }
}
