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
import de.civitascore.configadapter.model.dataset.NamedApiHelper;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.owasp.encoder.Encode;

/**
 * Saga command handler for the APISIX API Gateway. Handles route and upstream management for
 * dataset provisioning:
 *
 * <ul>
 *   <li>{@code CREATE_ROUTE} — creates one shared dataset upstream + one route per named-API slug
 *   <li>{@code UPDATE_ROUTE} — updates each slug route's configuration (plugin_config_id for auth)
 *   <li>{@code DELETE_ROUTE} — deletes each slug route + the shared upstream
 *   <li>{@code RESTORE_ROUTE} — restores each slug route to its previous auth config (compensation)
 * </ul>
 *
 * <p>Per the per-NamedApi route model (#1311/#1379) the saga provisions one route per slug at
 * {@code /v1/datasets/{id}/{slug}} with a deterministic id ({@code NamedApiHelper.derive(id,
 * slug)}) bound to a single per-dataset upstream (keyed by {@code datasetId}); it returns a
 * slug-keyed {@code routeIds} map. A command with no {@code namedApis} provisions NO data-plane
 * route (a dataset with no named APIs has nothing to publish — the old dataset-level fallback was
 * removed); UPDATE/DELETE/RESTORE on an empty {@code routeIds} map are no-ops. Uses PUT with
 * deterministic IDs for idempotent operations; DELETE/RESTORE tolerate an already-absent route
 * (404) so compensation is idempotent. Compensation: {@code DELETE_ROUTE} for create rollback,
 * {@code RESTORE_ROUTE} for update rollback.
 *
 * <p>Every {@code CREATE_ROUTE} pins the resulting APISIX route to the configured API virtual host
 * ({@code apisix.api.host}) and the {@code /v1/datasets/{id}} path prefix so APISIX matches it
 * deterministically (issue #1368). Initialization fails fast (throws {@link
 * IllegalArgumentException}) when any required setting is missing: {@code apisix.admin.key}, {@code
 * apisix.api.host}, {@code apisix.api.public.url}, {@code apisix.plugin.config.id} (private routes
 * rely on this plugin_config to enforce OIDC/OPA — without it private datasets would be publicly
 * reachable), and the FROST upstream credentials APISIX uses to proxy private datasets ({@code
 * apisix.frost.api.key} or {@code apisix.frost.basic.auth.username}/{@code .password}).
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
  private static final Set<String> LEGACY_ADAPTER_AUTH_HEADERS =
      Set.of("Authorization", DEFAULT_API_KEY_HEADER);

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
  public Map<String, String> fieldAliases() {
    return Map.of("baseUrl", "upstreamUrl");
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
    boolean openDataAccess =
        Boolean.TRUE.equals(command.payload().getOrDefault("openDataAccess", false));

    // Parse upstream URL into host:port and path components
    // e.g. "http://civitas-frost:8080/FROST-Server/v1.1/Projects(1)"
    //   → node = "civitas-frost:8080", path = "/FROST-Server/v1.1/Projects(1)"
    URI upstream = URI.create(upstreamUrl);
    String upstreamNode =
        upstream.getPort() > 0 ? upstream.getHost() + ":" + upstream.getPort() : upstream.getHost();
    String upstreamPath = upstream.getPath() != null ? upstream.getPath() : "/";
    String scheme = upstream.getScheme() != null ? upstream.getScheme() : "http";

    String publicUrl = apiPublicUrl + DATASETS_PATH_PREFIX + datasetId;
    RoutePayload routePayload = decodeRoutePayload(command);
    List<String> slugs = routePayload.slugs();
    Map<String, String> standardBySlug = routePayload.standardBySlug();

    if (slugs.isEmpty()) {
      // No named APIs → nothing to publish on the data plane, so provision NO route and NO
      // upstream.
      // Publication is strictly per named API (concept #1293/#1379: "one distribution per API"),
      // and
      // a dataset may legitimately have none (#1311: migrated datasets start with an empty
      // named_apis table). The old dataset-level /v1/datasets/{id} + /* fallback was a leftover of
      // the non-functional pre-#1368 model and would shadow the /v1/datasets/{id}/apis discovery
      // path — hence it is gone. UPDATE/DELETE/RESTORE likewise no-op on an empty routeIds map.
      log.warn(
          "CREATE_ROUTE: dataset {} has no named APIs — no data-plane route provisioned. saga={}",
          Encode.forJava(datasetId),
          Encode.forJava(command.sagaId()));
      Map<String, Object> empty = Map.of("routeIds", Map.of(), "serviceId", datasetId);
      return SagaCommandResult.success(command.sagaId(), command.stepId(), empty, empty);
    }

    // One upstream per dataset (the dataset's FROST project), shared by every named-API route.
    Map<String, Object> upstreamBody = buildUpstreamBody(upstreamNode, scheme);
    putResource(UPSTREAMS_PATH + datasetId, upstreamBody, "CREATE upstream");

    // Per-named-API model (#1311/#1379): one route per slug at /v1/datasets/{id}/{slug}, all
    // bound to the shared dataset upstream. The slug-keyed routeIds map is the saga contract the
    // portal-backend persists onto each NamedApi entity.
    //
    // The loop is a non-atomic sequence of PUTs. If a later route fails after earlier ones (and the
    // shared upstream) were created, a failed saga step records no compensation data, so the
    // orchestrator cannot roll back this step's partial state. We therefore best-effort clean up
    // what this step already created before propagating the failure, leaving no orphaned routes or
    // upstream behind.
    Map<String, String> routeIds = new LinkedHashMap<>();
    try {
      for (String slug : slugs) {
        // GeoServer seam (WFS/WMS): every saga route binds to the dataset's FROST-project upstream,
        // so only STA (SensorThings) named APIs are routable today. A non-STA standard fails fast
        // here rather than silently provisioning a FROST route behind a WFS/WMS public URL. When
        // GeoServer routing lands, this is where upstream + path-rewrite are selected by standard.
        String standard = standardBySlug.get(slug);
        if (!isRoutableStandard(standard)) {
          throw new IllegalStateException(
              "named API '"
                  + slug
                  + "' has standard '"
                  + standard
                  + "' which is not yet routable — only STA (FROST/SensorThings) is supported;"
                  + " WFS/WMS routing via GeoServer is not implemented");
        }
        String routeId = NamedApiHelper.derive(datasetId, slug);
        Map<String, Object> routeBody =
            buildRouteBody(
                DATASETS_PATH_PREFIX + datasetId + "/" + slug,
                datasetId,
                openDataAccess,
                upstreamPath);
        putResource(ROUTES_PATH + routeId, routeBody, "CREATE route");
        routeIds.put(slug, routeId);
      }
    } catch (RuntimeException e) {
      cleanUpPartialCreate(datasetId, routeIds, command.sagaId());
      throw e;
    }

    Map<String, Object> resultData =
        Map.of("routeIds", routeIds, "serviceId", datasetId, "publicUrl", publicUrl);
    Map<String, Object> compensationData = Map.of("routeIds", routeIds, "serviceId", datasetId);

    log.info(
        "APISIX routes created: datasetId={}, slugs={}, saga={}",
        Encode.forJava(datasetId),
        Encode.forJava(String.join(",", slugs)),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  private SagaCommandResult handleUpdateRoute(SagaCommandMessage command) {
    boolean openDataAccess =
        Boolean.TRUE.equals(command.payload().getOrDefault("openDataAccess", false));
    Map<String, String> routeIds = decodeRoutePayload(command).routeIds();

    if (routeIds.isEmpty()) {
      // No named-API routes for this dataset → nothing to update. The legacy single dataset-level
      // route was removed (see CREATE_ROUTE), so an empty routeIds map is a clean no-op rather than
      // a failure — e.g. toggling openDataAccess on a dataset that publishes no named API.
      log.warn(
          "UPDATE_ROUTE: empty routeIds (dataset has no named-API routes) — nothing to update."
              + " saga={}",
          Encode.forJava(command.sagaId()));
      return SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
    }

    // Per-named-API model. A forward UPDATE must be all-or-nothing: a partially applied auth change
    // leaves the dataset in a mixed, unintended state (e.g. one named API still public while it
    // should have gone private), and — because only COMPLETED steps contribute compensation data
    // (SagaStepDelegate) — a step that mutated some routes and then failed would leave those routes
    // un-rolled-back. We therefore validate presence BEFORE mutating anything: phase 1 loads every
    // target route; if any is absent on a forward step we fail before the first PUT (nothing
    // applied, nothing to roll back). Phase 2 applies the change to every now-known-present route.
    // A compensation re-run (COMPENSATE_STEP) stays tolerant — an absent route during rollback is
    // expected and recorded as a per-slug no-op.
    String serviceId = requireString(command, "serviceId");
    boolean compensating = "COMPENSATE_STEP".equals(command.type());

    // Phase 1 — load all target routes, collecting which slugs are absent.
    Map<String, Map<String, Object>> loadedRoutes = new LinkedHashMap<>();
    List<String> absentSlugs = new ArrayList<>();
    for (Map.Entry<String, String> entry : routeIds.entrySet()) {
      warnOnRouteIdDivergence(serviceId, entry.getKey(), entry.getValue(), command.sagaId());
      Map<String, Object> route = readRoute(entry.getValue(), true);
      if (route == null) {
        absentSlugs.add(entry.getKey());
      } else {
        loadedRoutes.put(entry.getKey(), route);
      }
    }
    if (!absentSlugs.isEmpty() && !compensating) {
      log.warn(
          "UPDATE_ROUTE: {} of {} target route(s) absent for serviceId={} (slugs={}; absent={}) —"
              + " failing before any change so the dataset is not left in a mixed auth state"
              + " (possible routeId mismatch). saga={}",
          absentSlugs.size(),
          routeIds.size(),
          Encode.forJava(serviceId),
          Encode.forJava(String.join(",", routeIds.keySet())),
          Encode.forJava(String.join(",", absentSlugs)),
          Encode.forJava(command.sagaId()));
      return SagaCommandResult.failure(
          command.sagaId(),
          command.stepId(),
          "UPDATE_ROUTE: "
              + absentSlugs.size()
              + " of "
              + routeIds.size()
              + " target route(s) absent for serviceId="
              + Encode.forJava(serviceId)
              + " (absent slugs="
              + Encode.forJava(String.join(",", absentSlugs))
              + ") — no auth change applied (possible routeId mismatch)");
    }

    // Phase 2 — apply the requested auth state to every present route, capturing each route's
    // previous open/protected state so RESTORE_ROUTE can roll each one back individually.
    Map<String, Object> previousOpenBySlug = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : routeIds.entrySet()) {
      Map<String, Object> route = loadedRoutes.get(entry.getKey());
      if (route == null) {
        // Reachable only on a compensation re-run: the route is already gone, so the matching
        // RESTORE for this slug is a no-op. Record the requested state as the "previous" one.
        previousOpenBySlug.put(entry.getKey(), openDataAccess);
        continue;
      }
      previousOpenBySlug.put(entry.getKey(), !routeIsPrivate(route));
      applyAuthConfig(route, openDataAccess);
      stripReadOnlyFields(route);
      putResource(ROUTES_PATH + entry.getValue(), route, "UPDATE route");
    }

    Map<String, Object> resultData = Map.of("routeIds", routeIds, "serviceId", serviceId);
    Map<String, Object> compensationData =
        Map.of(
            "routeIds", routeIds,
            "serviceId", serviceId,
            "previousOpenDataAccess", previousOpenBySlug);

    log.info(
        "APISIX routes updated: serviceId={}, slugs={}, saga={}",
        Encode.forJava(serviceId),
        Encode.forJava(String.join(",", routeIds.keySet())),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  /**
   * Per-slug routeIds are deterministic: {@code routeId == NamedApiHelper.derive(datasetId, slug)}.
   * The persisted map is round-tripped from the CREATE_ROUTE result through the portal-backend, so
   * a value that no longer matches the derivation means the stored map drifted from the gateway —
   * exactly the mismatch that produces UPDATE/DELETE 404s (#1368). We don't auto-heal (the stored
   * id is what the gateway route was created under), but we surface the divergence so it's
   * attributable rather than a silent later 404.
   */
  private void warnOnRouteIdDivergence(
      String datasetId, String slug, String persistedRouteId, String sagaId) {
    String derived = NamedApiHelper.derive(datasetId, slug);
    if (!derived.equals(persistedRouteId)) {
      log.warn(
          "Route command: persisted routeId {} for slug '{}' diverges from the deterministic id {}"
              + " (datasetId={}) — stored route map may be stale. saga={}",
          Encode.forJava(persistedRouteId),
          Encode.forJava(slug),
          Encode.forJava(derived),
          Encode.forJava(datasetId),
          Encode.forJava(sagaId));
    }
  }

  private SagaCommandResult handleDeleteRoute(SagaCommandMessage command) {
    String serviceId = requireString(command, "serviceId");
    Map<String, String> routeIds = decodeRoutePayload(command).routeIds();

    if (routeIds.isEmpty()) {
      // No named-API routes for this dataset → nothing to tear down (no routes and no shared
      // upstream were provisioned; the legacy single dataset-level route was removed, see
      // CREATE_ROUTE). Clean no-op.
      log.info(
          "DELETE_ROUTE: empty routeIds (dataset has no named-API routes) — nothing to delete."
              + " saga={}",
          Encode.forJava(command.sagaId()));
    } else {
      // Per-named-API model: delete every slug route, then the shared dataset upstream.
      int routesRemoved = 0;
      for (Map.Entry<String, String> entry : routeIds.entrySet()) {
        warnOnRouteIdDivergence(serviceId, entry.getKey(), entry.getValue(), command.sagaId());
        if (deleteResource(ROUTES_PATH + entry.getValue(), "DELETE route")) {
          routesRemoved++;
        }
      }
      deleteResource(UPSTREAMS_PATH + serviceId, "DELETE upstream");
      log.info(
          "APISIX routes deleted: serviceId={}, slugs={}, removed={}/{}, saga={}",
          Encode.forJava(serviceId),
          Encode.forJava(String.join(",", routeIds.keySet())),
          routesRemoved,
          routeIds.size(),
          Encode.forJava(command.sagaId()));
      // A forward DELETE (not a compensation re-run) that found NONE of its target routes is
      // suspicious — most likely the persisted routeIds don't match what was provisioned (id
      // mismatch). Nothing was torn down, yet we report success; surface it so it isn't silent.
      if (!"COMPENSATE_STEP".equals(command.type()) && routesRemoved == 0) {
        log.warn(
            "DELETE_ROUTE removed none of the {} target route(s) for serviceId={} (slugs={}) —"
                + " possible routeId mismatch; gateway routes may remain. saga={}",
            routeIds.size(),
            Encode.forJava(serviceId),
            Encode.forJava(String.join(",", routeIds.keySet())),
            Encode.forJava(command.sagaId()));
      }
    }

    return "COMPENSATE_STEP".equals(command.type())
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  private SagaCommandResult handleRestoreRoute(SagaCommandMessage command) {
    String serviceId = requireString(command, "serviceId");
    RoutePayload payload = decodeRoutePayload(command);
    Map<String, String> routeIds = payload.routeIds();

    if (routeIds.isEmpty()) {
      // No named-API routes for this dataset → nothing to restore (the legacy single dataset-level
      // route was removed, see CREATE_ROUTE). Clean no-op.
      log.info(
          "RESTORE_ROUTE: empty routeIds (dataset has no named-API routes) — nothing to restore."
              + " saga={}",
          Encode.forJava(command.sagaId()));
      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
    }

    // Per-named-API model: restore each slug route to the open/protected state captured by the
    // corresponding UPDATE_ROUTE step.
    Map<String, Boolean> previousOpenBySlug = payload.previousOpenBySlug();
    for (Map.Entry<String, String> entry : routeIds.entrySet()) {
      warnOnRouteIdDivergence(serviceId, entry.getKey(), entry.getValue(), command.sagaId());
      boolean previousOpenDataAccess =
          Boolean.TRUE.equals(previousOpenBySlug.getOrDefault(entry.getKey(), false));
      restoreSingleRoute(entry.getValue(), previousOpenDataAccess);
    }
    log.info(
        "APISIX routes restored: slugs={}, saga={}",
        Encode.forJava(String.join(",", routeIds.keySet())),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
  }

  private void restoreSingleRoute(String routeId, boolean previousOpenDataAccess) {
    // Idempotent compensation: if the route is already gone, there is nothing to restore — skip it
    // rather than failing the rollback (the route may have been removed by a concurrent delete or a
    // re-run of this compensation step).
    Map<String, Object> route = readRoute(routeId, true);
    if (route == null) {
      log.info("RESTORE route — route {} already absent, skipping", Encode.forJava(routeId));
      return;
    }
    applyAuthConfig(route, previousOpenDataAccess);
    stripReadOnlyFields(route);
    putResource(ROUTES_PATH + routeId, route, "RESTORE route");
  }

  /**
   * Best-effort rollback of a partially-completed CREATE_ROUTE: deletes the routes already created
   * in this step plus the shared dataset upstream. Each delete is idempotent (404-tolerant) and any
   * secondary failure is logged and swallowed so the original CREATE failure is the one propagated.
   */
  private void cleanUpPartialCreate(
      String datasetId, Map<String, String> createdRouteIds, String sagaId) {
    log.warn(
        "CREATE_ROUTE failed mid-provisioning — cleaning up partial state: datasetId={}, routes={},"
            + " saga={}",
        Encode.forJava(datasetId),
        Encode.forJava(String.join(",", createdRouteIds.values())),
        Encode.forJava(sagaId));
    for (String routeId : createdRouteIds.values()) {
      try {
        deleteResource(ROUTES_PATH + routeId, "CLEANUP partial route");
      } catch (RuntimeException ex) {
        log.warn(
            "CLEANUP: could not delete partial route {} (saga={}) — may be orphaned on the gateway:"
                + " {}",
            Encode.forJava(routeId),
            Encode.forJava(sagaId),
            Encode.forJava(ex.getMessage()));
      }
    }
    try {
      deleteResource(UPSTREAMS_PATH + datasetId, "CLEANUP partial upstream");
    } catch (RuntimeException ex) {
      log.warn(
          "CLEANUP: could not delete partial upstream {} (saga={}) — may be orphaned on the gateway:"
              + " {}",
          Encode.forJava(datasetId),
          Encode.forJava(sagaId),
          Encode.forJava(ex.getMessage()));
    }
  }

  /**
   * Reads the route, unwrapping the etcd {@code value} envelope. When {@code tolerateMissing} is
   * true a 404 yields {@code null} (for idempotent UPDATE/RESTORE compensation); otherwise a
   * non-2xx throws.
   */
  private Map<String, Object> readRoute(String routeId, boolean tolerateMissing) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(ROUTES_PATH + routeId)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .get()) {
      if (tolerateMissing && response.getStatus() == 404) {
        return null;
      }
      checkResponse(response, "GET route");
      @SuppressWarnings("unchecked")
      Map<String, Object> responseBody = response.readEntity(Map.class);
      Object value = responseBody.get("value");
      if (responseBody.containsKey("value") && !(value instanceof Map)) {
        // Guard the etcd-envelope unwrap: a 2xx with a non-object `value` (or an unexpected body
        // shape) would otherwise surface as an opaque ClassCastException. Fail with a clear
        // message.
        throw new SagaApiException(
            "GET route returned an unexpected body shape (value is not an object) for routeId="
                + Encode.forJava(routeId),
            502);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> routeValue =
          responseBody.containsKey("value") ? (Map<String, Object>) value : responseBody;
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
      return new ArrayList<>(Arrays.asList(arr));
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

  /**
   * Typed, validated view of a route command's payload — decoded once from the loosely-typed saga
   * {@code Map} (which arrives via the Kafka → Flowable variable round-trip). It carries the
   * named-API {@code slugs} (CREATE), the slug-keyed {@code routeIds} (UPDATE/DELETE/RESTORE) and
   * the per-slug previous open-data flags (RESTORE). An empty {@link #slugs}/{@link #routeIds}
   * means the dataset has no named APIs: CREATE provisions nothing and UPDATE/DELETE/RESTORE no-op
   * (the legacy single dataset-level route was removed), expressed in one place instead of
   * re-derived in each handler.
   */
  private record RoutePayload(
      List<String> slugs,
      Map<String, String> routeIds,
      Map<String, Boolean> previousOpenBySlug,
      Map<String, String> standardBySlug) {}

  private RoutePayload decodeRoutePayload(SagaCommandMessage command) {
    return new RoutePayload(
        readSlugs(command),
        readRouteIds(command),
        readPreviousOpenBySlug(command),
        readStandardBySlug(command));
  }

  /**
   * Per-slug API standard ({@code STA}/{@code WFS}/{@code WMS}) from the {@code namedApis} payload,
   * used to gate routing in CREATE_ROUTE. Only STA is routable today (FROST); see {@link
   * #isRoutableStandard}.
   */
  private Map<String, String> readStandardBySlug(SagaCommandMessage command) {
    Object raw = command.payload().get("namedApis");
    if (!(raw instanceof List<?> list)) {
      return Map.of();
    }
    Map<String, String> standards = new LinkedHashMap<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Object slug = map.get("slug");
        if (slug != null && !slug.toString().isBlank()) {
          Object standard = map.get("standard");
          standards.put(slug.toString(), standard == null ? null : standard.toString());
        }
      }
    }
    return standards;
  }

  /**
   * Whether a named API's standard can be routed by the current (FROST-only) data plane. Every saga
   * route binds to the dataset's FROST-project upstream, so only {@code STA} (SensorThings) is
   * routable; {@code WFS}/{@code WMS} will route to a separate GeoServer upstream that is not wired
   * yet. A null/blank standard is treated as STA for backward compatibility (the production saga
   * always sends STA). Non-STA standards fail fast in CREATE_ROUTE rather than producing a FROST
   * route behind a WFS/WMS public URL — that is the seam GeoServer support plugs into.
   */
  private static boolean isRoutableStandard(String standard) {
    return standard == null || standard.isBlank() || "STA".equalsIgnoreCase(standard);
  }

  private List<String> readSlugs(SagaCommandMessage command) {
    Object raw = command.payload().get("namedApis");
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> slugs = new ArrayList<>();
    int dropped = 0;
    for (Object item : list) {
      Object slug = item instanceof Map<?, ?> map ? map.get("slug") : null;
      if (slug != null && !slug.toString().isBlank()) {
        slugs.add(slug.toString());
      } else {
        dropped++;
      }
    }
    if (dropped > 0) {
      // A malformed namedApi entry means a published API would silently get no gateway route — make
      // the drop visible instead of failing 404 unexplained later.
      log.warn(
          "Route command: ignored {} namedApi entry/entries without a usable slug (saga={})",
          dropped,
          Encode.forJava(command.sagaId()));
    }
    return slugs;
  }

  /**
   * Reads the slug-keyed {@code routeIds} map from an UPDATE/DELETE/RESTORE command (persisted by
   * the portal-backend from the prior CREATE_ROUTE result). Returns an empty map when absent — that
   * is a dataset with no named-API routes, for which UPDATE/DELETE/RESTORE are no-ops (the legacy
   * singular {@code routeId} fallback was removed).
   */
  private Map<String, String> readRouteIds(SagaCommandMessage command) {
    Object raw = command.payload().get("routeIds");
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, String> routeIds = new LinkedHashMap<>();
    int dropped = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        routeIds.put(entry.getKey().toString(), entry.getValue().toString());
      } else {
        dropped++;
      }
    }
    if (dropped > 0) {
      log.warn(
          "Route command: ignored {} routeIds entry/entries with a null key/value (saga={})",
          dropped,
          Encode.forJava(command.sagaId()));
    }
    return routeIds;
  }

  /** Reads the per-slug previous open-data state captured by UPDATE_ROUTE for RESTORE_ROUTE. */
  private Map<String, Boolean> readPreviousOpenBySlug(SagaCommandMessage command) {
    Object raw = command.payload().get("previousOpenDataAccess");
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Boolean> previous = new LinkedHashMap<>();
    int dropped = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() == null) {
        dropped++;
        continue;
      }
      previous.put(
          entry.getKey().toString(),
          Boolean.TRUE.equals(entry.getValue())
              || "true".equalsIgnoreCase(String.valueOf(entry.getValue())));
    }
    if (dropped > 0) {
      log.warn(
          "RESTORE_ROUTE: ignored {} previousOpenDataAccess entry/entries with a null key (saga={})",
          dropped,
          Encode.forJava(command.sagaId()));
    }
    return previous;
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

  /**
   * Deletes a resource. Returns {@code true} if it was actually removed (2xx), {@code false} if it
   * was already absent (404). A 404 is tolerated — the desired end state is reached — so saga
   * DELETE and its compensation stay idempotent across re-runs and partial provisioning. Other
   * non-2xx still throw. The boolean lets callers detect a wholesale "nothing existed" miss.
   */
  private boolean deleteResource(String path, String operationDesc) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(path)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .delete()) {
      if (response.getStatus() == 404) {
        log.info(
            "{} — resource already absent (404): {}",
            Encode.forJava(operationDesc),
            Encode.forJava(path));
        return false;
      }
      checkResponse(response, operationDesc);
      return true;
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
  private Map<String, Object> buildRouteBody(
      String routePath, String upstreamId, boolean isOpenData, String upstreamPath) {
    Map<String, Object> body = new HashMap<>();

    // Published-data routes are pinned to the configured API virtual host (apisix.api.host)
    // so APISIX deterministically matches them (issue #1368). routePath is the per-named-API
    // /v1/datasets/{id}/{slug}; upstreamId keys the shared dataset upstream and is intentionally
    // decoupled from the route id.
    body.put("uris", new String[] {routePath, routePath + "/*"});
    body.put("hosts", new String[] {apiHost});
    body.put("upstream_id", upstreamId);
    if (serviceId != null) {
      body.put("service_id", serviceId);
    }
    body.put("status", 1);

    // Rewrite gateway path to upstream FROST path
    // e.g. /v1/datasets/{id}/{slug}/Things → /FROST-Server/v1.1/Projects(1)/Things
    Map<String, Object> proxyRewrite = new HashMap<>();
    proxyRewrite.put("regex_uri", new String[] {"^" + routePath + "(/.*)?$", upstreamPath + "$1"});

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
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toArray(String[]::new);
  }
}
