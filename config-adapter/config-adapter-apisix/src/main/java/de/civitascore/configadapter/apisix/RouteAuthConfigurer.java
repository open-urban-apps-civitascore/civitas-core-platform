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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds saga route bodies and applies the gateway auth state to them. Every published-data route
 * is <b>always protected</b>: it carries the {@code plugin_config_id} (OIDC + OPA) and, for {@link
 * RouteUpstreamKind#STA} routes, the FROST upstream credential in {@code proxy-rewrite.headers.set}
 * (tracked via the {@link #MANAGED_AUTH_HEADER_LABEL} route label). No {@code methods} restriction
 * is applied — the per-request OPA decision is the gate.
 *
 * <p>"Open data" is no longer a route-level concept. Anonymous read access to an open dataset is
 * decided by OPA at request time (ABAC on the dataset's {@code openDataAccess} flag): the request
 * still traverses the OIDC/OPA route, and OPA returns allow. The former bypass — stripping the auth
 * plugin and making the FROST project public — has been removed so authorization is never decided
 * around OPA.
 *
 * <p>The FROST upstream credential applies only to {@link RouteUpstreamKind#STA} routes. {@link
 * RouteUpstreamKind#OWS} routes carry <b>no</b> upstream credential — GeoServer serves the
 * workspace OWS endpoint anonymously. A route's kind is recorded at CREATE via {@link
 * #MANAGED_STANDARD_LABEL} so UPDATE/RESTORE (which read the route back from APISIX without the
 * standard in their payload) re-apply the credential correctly.
 *
 * <p>Internal trust headers are always stripped via {@code proxy-rewrite.headers.remove} (see
 * {@link ApisixHandlerSettings#ALWAYS_STRIPPED_HEADERS}).
 *
 * <p>CREATE builds a fresh skeleton and runs it through the same {@link #applyAuthState} as
 * UPDATE/RESTORE, so the protected shape cannot drift between the create and re-apply paths.
 */
final class RouteAuthConfigurer {

  /**
   * APISIX route label key. Stores the header name the adapter set under {@code
   * proxy-rewrite.headers.set} so a subsequent UPDATE/RESTORE can clean it up explicitly even after
   * a config-time scheme change (e.g. Basic Auth → API key) or header rename.
   */
  static final String MANAGED_AUTH_HEADER_LABEL = "civitas-frost-upstream-auth-header";

  /**
   * APISIX route label key marking a route's named-API standard, so UPDATE/RESTORE — which read the
   * route back from APISIX without the standard in their payload — can tell a map-service (OWS)
   * route from a sensor (STA) one. Only OWS routes carry it (value {@code OWS}); an absent marker
   * means {@link RouteUpstreamKind#STA}, keeping pre-existing STA route bodies unchanged.
   */
  static final String MANAGED_STANDARD_LABEL = "civitas-named-api-standard";

  /** {@code proxy-rewrite.headers} sub-map of request headers to set on the upstream. */
  private static final String HEADERS_SET_KEY = "set";

  private final ApisixHandlerSettings settings;

  RouteAuthConfigurer(ApisixHandlerSettings settings) {
    this.settings = settings;
  }

  /**
   * Builds the full route body for CREATE_ROUTE: URIs, host pinning, upstream binding,
   * proxy-rewrite path mapping — then applies the protected auth state through the same path
   * UPDATE/RESTORE use. {@code routePath} is the per-named-API {@code /v1/datasets/{id}/{slug}};
   * {@code upstreamId} keys the dataset upstream selected by {@code kind} (the FROST-project
   * upstream for {@link RouteUpstreamKind#STA}, the map-server upstream for {@link
   * RouteUpstreamKind#OWS}) and is intentionally decoupled from the route id. {@code upstreamPath}
   * is the upstream path the gateway path is rewritten to (FROST project path, or {@code
   * /geoserver/{workspace}/ows} for map services).
   */
  Map<String, Object> newRouteBody(
      String routePath, String upstreamId, String upstreamPath, RouteUpstreamKind kind) {
    Map<String, Object> body = new HashMap<>();

    // Published-data routes are pinned to the configured API virtual host (apisix.api.host)
    // so APISIX deterministically matches them (issue #1368).
    body.put("uris", PathRewrite.matchedUris(routePath, kind));
    body.put("hosts", new String[] {settings.apiHost()});
    body.put("upstream_id", upstreamId);
    // EVERY dataset route — STA and OWS alike — carries this single shared service_id. OPA reads
    // input.service.name from it (with_service=true) to pick the backend policy, so OWS/GeoServer
    // routes are deliberately authorized under the `frost_server` backend policy too (the one
    // carrying `_open_data: true`); 9g4 proves anonymous OWS open data works through it. This is an
    // intentional coupling: if backends are ever split by service name, OWS open data must get its
    // own service/policy (or a backend-neutral name like `dataset-payload`) instead of relying on
    // `frost_server` == FROST-only.
    if (settings.serviceId() != null) {
      body.put("service_id", settings.serviceId());
    }
    body.put("status", 1);

    // Rewrite gateway path to the upstream path. STA: /v1/datasets/{id}/{slug}/Things →
    // /FROST-Server/v1.1/Projects(1)/Things. OWS: /v1/datasets/{id}/{slug}?service=WFS →
    // /geoserver/{workspace}/ows?service=WFS (query string preserved by APISIX).
    Map<String, Object> proxyRewrite = new HashMap<>();
    proxyRewrite.put("regex_uri", PathRewrite.pairs(routePath, upstreamPath, kind));
    Map<String, Object> plugins = new HashMap<>();
    plugins.put("proxy-rewrite", proxyRewrite);
    body.put("plugins", plugins);

    // An OWS route records its kind in this label so a later UPDATE/RESTORE (which reads the
    // route back from APISIX without the standard in its payload) does not inject FROST
    // credentials. Unmarked routes default to STA, keeping existing route bodies unchanged.
    if (kind == RouteUpstreamKind.OWS) {
      RouteLabels.put(body, MANAGED_STANDARD_LABEL, RouteUpstreamKind.OWS.name());
    }

    applyAuthState(body);
    return body;
  }

  /**
   * Applies the protected auth state to a route: sets {@code plugin_config_id} (OIDC + OPA),
   * injects the FROST upstream credential for STA routes, and removes any {@code methods} gate.
   * Mutates the given route map in place. Used by CREATE (on a fresh skeleton), UPDATE and RESTORE
   * (re-apply), so every path produces the same protected shape — there is no longer an "open"
   * variant.
   */
  void applyAuthState(Map<String, Object> route) {
    RouteUpstreamKind kind = readUpstreamKind(route);
    boolean sta = kind == RouteUpstreamKind.STA;

    // FROST upstream credential is tracked via a route label so a re-apply can clean a stale entry
    // left behind by a different auth scheme, a renamed API key header, or a route that no longer
    // carries it. Read the previously-set header BEFORE flipping the label — for BOTH kinds, so an
    // OWS route is provably stripped of any FROST credential it should never forward.
    String managedHeader = RouteLabels.read(route, MANAGED_AUTH_HEADER_LABEL);

    route.put("plugin_config_id", settings.pluginConfigId());
    if (sta) {
      RouteLabels.put(route, MANAGED_AUTH_HEADER_LABEL, settings.frostAuth().headerName());
    } else {
      // OWS (map-service) routes never carry the FROST upstream credential — drop any stale label.
      RouteLabels.remove(route, MANAGED_AUTH_HEADER_LABEL);
    }
    // No methods filter: OPA decides per request (it allows only safe reads for anonymous open-data
    // callers), and a future authorized-write feature must not be blocked by a route-level gate.
    route.remove("methods");

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
    PathRewrite.normalize(route, proxyRewrite, kind);

    // STA routes inject the FROST upstream credential; OWS map-service routes carry none.
    mergeProxyRewriteHeaders(proxyRewrite, managedHeader, sta);

    // OWS routes additionally rewrite GeoServer's self-referential capabilities URLs to this
    // route's external endpoint so map clients can follow them back through the gateway.
    if (kind == RouteUpstreamKind.OWS) {
      OwsCapabilitiesRewrite.apply(route, plugins, proxyRewrite, settings.apiHost());
    }
  }

  /** A route's upstream kind, read from {@link #MANAGED_STANDARD_LABEL}; absent marker ⇒ STA. */
  private static RouteUpstreamKind readUpstreamKind(Map<String, Object> route) {
    String standard = RouteLabels.read(route, MANAGED_STANDARD_LABEL);
    return RouteUpstreamKind.fromStandard(standard).orElse(RouteUpstreamKind.STA);
  }

  /**
   * Merges the adapter-managed entries into an existing {@code proxy-rewrite.headers} block instead
   * of replacing it — preserves foreign {@code headers.add} entries, {@code headers.set} entries
   * owned by other operators, and additional {@code headers.remove} items. For STA routes this
   * includes the FROST upstream credential; OWS map-service routes ({@code sta = false}) get only
   * the always-strip list, since GeoServer serves the workspace OWS endpoint anonymously and the
   * gateway/OPA gate is the authorization boundary.
   */
  @SuppressWarnings("unchecked")
  private void mergeProxyRewriteHeaders(
      Map<String, Object> proxyRewrite, String managedHeader, boolean sta) {
    Map<String, Object> headers = mutableMap((Map<String, Object>) proxyRewrite.get("headers"));

    if (sta) {
      mergeAuthSetEntries(headers, managedHeader);
    } else {
      removeManagedAuthHeader(headers, managedHeader);
    }
    mergeStripList(headers);

    if (headers.isEmpty()) {
      proxyRewrite.remove("headers");
    } else {
      proxyRewrite.put("headers", headers);
    }
  }

  /**
   * {@code headers.set} — set the adapter-managed FROST upstream credential. A stale entry from a
   * previous config (different scheme or renamed API key header) is identified via the route label
   * written when we last set it; remove it before applying the current value.
   */
  @SuppressWarnings("unchecked")
  private void mergeAuthSetEntries(Map<String, Object> headers, String staleManagedHeader) {
    Map<String, Object> set = mutableMap((Map<String, Object>) headers.get(HEADERS_SET_KEY));
    String authHeaderName = settings.frostAuth().headerName();
    if (staleManagedHeader != null && !staleManagedHeader.equals(authHeaderName)) {
      set.remove(staleManagedHeader);
    }
    set.put(authHeaderName, settings.frostAuth().headerValue());
    headers.put(HEADERS_SET_KEY, set);
  }

  /**
   * {@code headers.set} for a non-STA (OWS) route — drop the adapter-managed FROST credential if a
   * previous config left one behind (identified via the route label). OWS map-service routes must
   * forward NO upstream credential; GeoServer serves the workspace OWS endpoint anonymously.
   */
  @SuppressWarnings("unchecked")
  private void removeManagedAuthHeader(Map<String, Object> headers, String managedHeader) {
    if (managedHeader == null) {
      return;
    }
    Map<String, Object> set = (Map<String, Object>) headers.get(HEADERS_SET_KEY);
    if (set == null || !set.containsKey(managedHeader)) {
      return;
    }
    Map<String, Object> mutable = mutableMap(set);
    mutable.remove(managedHeader);
    if (mutable.isEmpty()) {
      headers.remove(HEADERS_SET_KEY);
    } else {
      headers.put(HEADERS_SET_KEY, mutable);
    }
  }

  /** {@code headers.remove} — union with the adapter's strip list. Foreign entries stay. */
  private void mergeStripList(Map<String, Object> headers) {
    List<String> remove = readStringList(headers.get("remove"));
    for (String entry : settings.proxyRewriteHeadersToRemove()) {
      if (!remove.contains(entry)) {
        remove.add(entry);
      }
    }
    headers.put("remove", remove);
  }

  /**
   * Defensive copy unless already a mutable {@link HashMap}: Jackson typically returns
   * LinkedHashMap which is mutable, but other deserialisers may return immutable views.
   */
  private static Map<String, Object> mutableMap(Map<String, Object> map) {
    if (map == null) {
      return new HashMap<>();
    }
    return map instanceof HashMap ? map : new HashMap<>(map);
  }

  static List<String> readStringList(Object value) {
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
}
