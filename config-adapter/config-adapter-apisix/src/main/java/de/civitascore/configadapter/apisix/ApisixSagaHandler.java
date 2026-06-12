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
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * removed). Uses PUT with deterministic IDs for idempotent operations; DELETE/RESTORE tolerate an
 * already-absent route (404) so compensation is idempotent. Compensation: {@code DELETE_ROUTE} for
 * create rollback, {@code RESTORE_ROUTE} for update rollback.
 *
 * <p><b>Drift between {@code namedApis} and the persisted {@code routeIds} map</b> (MR !547 review
 * findings 2 and 5) is handled per the established philosophy "UPDATE atomic-fail, DELETE stays
 * tolerant": a forward UPDATE whose dataset has named APIs without a persisted route id FAILS
 * instead of silently skipping them (a privacy toggle must never report success without acting),
 * while DELETE heals the same drift by deriving the deterministic route id and deleting it anyway
 * (404-tolerant). An empty {@code routeIds} map with no named APIs remains a clean no-op for
 * UPDATE/DELETE/RESTORE.
 *
 * <p>Every {@code CREATE_ROUTE} pins the resulting APISIX route to the configured API virtual host
 * ({@code apisix.api.host}) and the {@code /v1/datasets/{id}} path prefix so APISIX matches it
 * deterministically (issue #1368). Configuration is resolved and validated fail-fast by {@link
 * ApisixHandlerSettings}; what "open data" vs "protected" means on the gateway (plugin_config,
 * upstream credential, read-only method gate) is owned by {@link RouteAuthConfigurer}; the Admin
 * API mechanics live in {@link ApisixAdminClient}.
 *
 * <p><b>Why a single virtual host?</b> {@code /v1/datasets/{id}} is strictly more specific than a
 * {@code /v1/*} catch-all, so APISIX' radix tree dispatches the saga route deterministically
 * without a second host. The {@code hosts} filter on the saga route is kept for deployment
 * determinism and is pinned end-to-end by {@code
 * ApisixSagaHandlerRoutingTest#shouldWinOverV1CatchAllOnSameHost}.
 */
public class ApisixSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "apisix";
  private static final String DATASETS_PATH_PREFIX = "/v1/datasets/";
  private static final String COMPENSATE_STEP = "COMPENSATE_STEP";
  private static final String KEY_ROUTE_IDS = "routeIds";
  private static final String KEY_SERVICE_ID = "serviceId";

  private ApisixHandlerSettings settings;
  private ApisixAdminClient adminClient;
  private RouteAuthConfigurer authConfigurer;

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
    this.settings = ApisixHandlerSettings.from(config);
    this.adminClient =
        new ApisixAdminClient(this::client, settings.adminApiUrl(), settings.adminApiKey());
    this.authConfigurer = new RouteAuthConfigurer(settings);

    log.info(
        "ApisixSagaHandler initialized for: {} (api host: {}, frost upstream auth header: {})",
        Encode.forJava(settings.adminApiUrl()),
        Encode.forJava(settings.apiHost()),
        Encode.forJava(settings.frostAuth().headerName()));
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

  // The generic catch is a deliberate cleanup boundary: ANY runtime failure (HTTP, network,
  // serialization) mid-provisioning must trigger the best-effort rollback before propagating.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private SagaCommandResult handleCreateRoute(SagaCommandMessage command) {
    String datasetId = requireString(command, "datasetId");
    UpstreamTarget upstream = UpstreamTarget.parse(requireString(command, "upstreamUrl"));
    boolean openDataAccess =
        Boolean.TRUE.equals(command.payload().getOrDefault("openDataAccess", false));

    RoutePayload routePayload = RoutePayload.decode(command);
    List<String> slugs = routePayload.slugs();

    if (slugs.isEmpty()) {
      // No named APIs → nothing to publish on the data plane, so provision NO route and NO
      // upstream. Publication is strictly per named API (concept #1293/#1379: "one distribution
      // per API"), and a dataset may legitimately have none (#1311: migrated datasets start with
      // an empty named_apis table). The old dataset-level /v1/datasets/{id} + /* fallback was a
      // leftover of the non-functional pre-#1368 model and would shadow the /v1/datasets/{id}/apis
      // discovery path — hence it is gone. UPDATE/DELETE/RESTORE likewise no-op without named APIs.
      log.warn(
          "CREATE_ROUTE: dataset {} has no named APIs — no data-plane route provisioned. saga={}",
          Encode.forJava(datasetId),
          Encode.forJava(command.sagaId()));
      Map<String, Object> empty = Map.of(KEY_ROUTE_IDS, Map.of(), KEY_SERVICE_ID, datasetId);
      return SagaCommandResult.success(command.sagaId(), command.stepId(), empty, empty);
    }

    // GeoServer seam (WFS/WMS): validate every standard BEFORE provisioning anything, so a
    // non-routable named API fails fast without creating gateway state that needs cleanup.
    requireRoutableStandards(routePayload);

    // One upstream per dataset (the dataset's FROST project), shared by every named-API route.
    adminClient.putUpstream(
        datasetId,
        Map.of(
            "type", "roundrobin", "scheme", upstream.scheme(), "nodes", Map.of(upstream.node(), 1)),
        "CREATE upstream");

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
        String routeId = NamedApiHelper.derive(datasetId, slug);
        adminClient.putRoute(
            routeId,
            authConfigurer.newRouteBody(
                DATASETS_PATH_PREFIX + datasetId + "/" + slug,
                datasetId,
                openDataAccess,
                upstream.path()),
            "CREATE route");
        routeIds.put(slug, routeId);
      }
    } catch (RuntimeException e) {
      // A failed saga step records no compensation data, so the orchestrator cannot roll back this
      // step's partial state — clean up best-effort before propagating the original failure.
      log.warn(
          "CREATE_ROUTE failed mid-provisioning — cleaning up partial state: datasetId={},"
              + " routes={}, saga={}",
          Encode.forJava(datasetId),
          Encode.forJava(String.join(",", routeIds.values())),
          Encode.forJava(command.sagaId()));
      adminClient.bestEffortCleanup(datasetId, routeIds.values(), command.sagaId());
      throw e;
    }

    String publicUrl = settings.apiPublicUrl() + DATASETS_PATH_PREFIX + datasetId;
    Map<String, Object> resultData =
        Map.of(KEY_ROUTE_IDS, routeIds, KEY_SERVICE_ID, datasetId, "publicUrl", publicUrl);
    Map<String, Object> compensationData =
        Map.of(KEY_ROUTE_IDS, routeIds, KEY_SERVICE_ID, datasetId);

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
    RoutePayload payload = RoutePayload.decode(command);
    Map<String, String> routeIds = payload.routeIds();
    boolean compensating = COMPENSATE_STEP.equals(command.type());

    // Fail loud on namedApis ↔ routeIds drift (MR !547 review finding 2): a named API without a
    // persisted route id means the stored map no longer matches the dataset. Silently skipping it
    // would let a protect-toggle report success while the live gateway route keeps its old auth
    // state — a dataset meant to be private would stay publicly readable. All-or-nothing instead.
    List<String> slugsWithoutRouteId = payload.slugsWithoutRouteId();
    if (!slugsWithoutRouteId.isEmpty() && !compensating) {
      log.warn(
          "UPDATE_ROUTE: {} named API(s) without a persisted routeId (slugs={}) — failing instead"
              + " of applying a partial auth change. saga={}",
          slugsWithoutRouteId.size(),
          Encode.forJava(String.join(",", slugsWithoutRouteId)),
          Encode.forJava(command.sagaId()));
      return SagaCommandResult.failure(
          command.sagaId(),
          command.stepId(),
          "UPDATE_ROUTE: "
              + slugsWithoutRouteId.size()
              + " named API(s) have no persisted routeId (slugs="
              + Encode.forJava(String.join(",", slugsWithoutRouteId))
              + ") — no auth change applied (stored route map drifted from the dataset)");
    }

    if (routeIds.isEmpty()) {
      // No named-API routes for this dataset → nothing to update. The legacy single dataset-level
      // route was removed (see CREATE_ROUTE), so an empty routeIds map without named APIs is a
      // clean no-op — e.g. toggling openDataAccess on a dataset that publishes no named API.
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
    String serviceId = requireString(command, KEY_SERVICE_ID);
    payload.logRouteIdDivergence(serviceId, command.sagaId());

    // Phase 1 — load all target routes, collecting which slugs are absent.
    Map<String, Map<String, Object>> loadedRoutes = loadTargetRoutes(routeIds);
    List<String> absentSlugs =
        routeIds.keySet().stream().filter(slug -> !loadedRoutes.containsKey(slug)).toList();
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

    Map<String, Object> previousOpenBySlug =
        applyAuthStateToRoutes(routeIds, loadedRoutes, openDataAccess);

    Map<String, Object> resultData = Map.of(KEY_ROUTE_IDS, routeIds, KEY_SERVICE_ID, serviceId);
    Map<String, Object> compensationData =
        Map.of(
            KEY_ROUTE_IDS,
            routeIds,
            KEY_SERVICE_ID,
            serviceId,
            "previousOpenDataAccess",
            previousOpenBySlug);

    log.info(
        "APISIX routes updated: serviceId={}, slugs={}, saga={}",
        Encode.forJava(serviceId),
        Encode.forJava(String.join(",", routeIds.keySet())),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  /**
   * GeoServer seam (WFS/WMS): every saga route binds to the dataset's FROST-project upstream, so
   * only STA (SensorThings) named APIs are routable today. A non-STA standard fails fast rather
   * than silently provisioning a FROST route behind a WFS/WMS public URL. When GeoServer routing
   * lands, this is where upstream + path-rewrite get selected by standard.
   */
  private static void requireRoutableStandards(RoutePayload payload) {
    for (String slug : payload.slugs()) {
      String standard = payload.standardBySlug().get(slug);
      if (!RoutePayload.isRoutableStandard(standard)) {
        throw new IllegalStateException(
            "named API '"
                + slug
                + "' has standard '"
                + standard
                + "' which is not yet routable — only STA (FROST/SensorThings) is supported;"
                + " WFS/WMS routing via GeoServer is not implemented");
      }
    }
  }

  /**
   * Upstream URL split into the APISIX node ({@code host[:port]}), path and scheme, e.g. {@code
   * http://civitas-frost:8080/FROST-Server/v1.1/Projects(1)} → node {@code civitas-frost:8080},
   * path {@code /FROST-Server/v1.1/Projects(1)}.
   */
  private record UpstreamTarget(String node, String path, String scheme) {

    static UpstreamTarget parse(String upstreamUrl) {
      URI uri = URI.create(upstreamUrl);
      String node = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
      return new UpstreamTarget(
          node,
          uri.getPath() != null ? uri.getPath() : "/",
          uri.getScheme() != null ? uri.getScheme() : "http");
    }
  }

  /**
   * Phase 2 of UPDATE_ROUTE — applies the requested auth state to every loaded route, capturing
   * each route's previous open/protected state so RESTORE_ROUTE can roll each one back
   * individually. A slug missing from {@code loadedRoutes} is reachable only on a compensation
   * re-run (the route is already gone): the matching RESTORE for it is a no-op, so the requested
   * state is recorded as the "previous" one.
   */
  private Map<String, Object> applyAuthStateToRoutes(
      Map<String, String> routeIds,
      Map<String, Map<String, Object>> loadedRoutes,
      boolean openDataAccess) {
    Map<String, Object> previousOpenBySlug = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : routeIds.entrySet()) {
      Map<String, Object> route = loadedRoutes.get(entry.getKey());
      if (route == null) {
        previousOpenBySlug.put(entry.getKey(), openDataAccess);
        continue;
      }
      previousOpenBySlug.put(entry.getKey(), !authConfigurer.routeIsPrivate(route));
      authConfigurer.applyAuthState(route, openDataAccess);
      adminClient.putRoute(entry.getValue(), route, "UPDATE route");
    }
    return previousOpenBySlug;
  }

  /** Loads every target route via the Admin API; absent routes (404) are simply not in the map. */
  private Map<String, Map<String, Object>> loadTargetRoutes(Map<String, String> routeIds) {
    Map<String, Map<String, Object>> loadedRoutes = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : routeIds.entrySet()) {
      adminClient
          .readRoute(entry.getValue())
          .ifPresent(route -> loadedRoutes.put(entry.getKey(), route));
    }
    return loadedRoutes;
  }

  private SagaCommandResult handleDeleteRoute(SagaCommandMessage command) {
    String serviceId = requireString(command, KEY_SERVICE_ID);
    RoutePayload payload = RoutePayload.decode(command);
    Map<String, String> routeIds = new LinkedHashMap<>(payload.routeIds());

    // Heal namedApis ↔ routeIds drift on teardown (MR !547 review finding 5): a named API whose
    // persisted routeId was lost may still have a live gateway route under its deterministic id.
    // DELETE is 404-tolerant, so deriving and deleting is safe — a leftover route is worse (a
    // formerly-private route surviving unrelease would stay reachable).
    List<String> derivedSlugs = payload.slugsWithoutRouteId();
    for (String slug : derivedSlugs) {
      routeIds.put(slug, NamedApiHelper.derive(serviceId, slug));
    }
    if (!derivedSlugs.isEmpty()) {
      log.warn(
          "DELETE_ROUTE: {} named API(s) without a persisted routeId (slugs={}) — deleting the"
              + " derived deterministic route id(s) instead of leaving potential orphans. saga={}",
          derivedSlugs.size(),
          Encode.forJava(String.join(",", derivedSlugs)),
          Encode.forJava(command.sagaId()));
    }

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
      payload.logRouteIdDivergence(serviceId, command.sagaId());
      int routesRemoved = 0;
      for (Map.Entry<String, String> entry : routeIds.entrySet()) {
        if (adminClient.deleteRoute(entry.getValue(), "DELETE route")) {
          routesRemoved++;
        }
      }
      adminClient.deleteUpstream(serviceId, "DELETE upstream");
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
      if (!COMPENSATE_STEP.equals(command.type()) && routesRemoved == 0) {
        log.warn(
            "DELETE_ROUTE removed none of the {} target route(s) for serviceId={} (slugs={}) —"
                + " possible routeId mismatch; gateway routes may remain. saga={}",
            routeIds.size(),
            Encode.forJava(serviceId),
            Encode.forJava(String.join(",", routeIds.keySet())),
            Encode.forJava(command.sagaId()));
      }
    }

    return COMPENSATE_STEP.equals(command.type())
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  private SagaCommandResult handleRestoreRoute(SagaCommandMessage command) {
    String serviceId = requireString(command, KEY_SERVICE_ID);
    RoutePayload payload = RoutePayload.decode(command);
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
    // corresponding UPDATE_ROUTE step. Idempotent compensation: if a route is already gone, there
    // is nothing to restore — skip it rather than failing the rollback (the route may have been
    // removed by a concurrent delete or a re-run of this compensation step).
    payload.logRouteIdDivergence(serviceId, command.sagaId());
    for (Map.Entry<String, String> entry : routeIds.entrySet()) {
      boolean previousOpenDataAccess =
          Boolean.TRUE.equals(payload.previousOpenBySlug().getOrDefault(entry.getKey(), false));
      Optional<Map<String, Object>> route = adminClient.readRoute(entry.getValue());
      if (route.isEmpty()) {
        log.info(
            "RESTORE route — route {} already absent, skipping", Encode.forJava(entry.getValue()));
        continue;
      }
      authConfigurer.applyAuthState(route.get(), previousOpenDataAccess);
      adminClient.putRoute(entry.getValue(), route.get(), "RESTORE route");
    }
    log.info(
        "APISIX routes restored: slugs={}, saga={}",
        Encode.forJava(String.join(",", routeIds.keySet())),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
  }
}
