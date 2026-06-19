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
import java.util.Set;

/**
 * Builds saga route bodies and applies the open/protected auth state to them. This is the single
 * place encoding what "open data" vs "protected" means on the gateway:
 *
 * <ul>
 *   <li><b>Protected</b> — {@code plugin_config_id} (OIDC/OPA), the FROST upstream credential in
 *       {@code proxy-rewrite.headers.set} (tracked via the {@link #MANAGED_AUTH_HEADER_LABEL} route
 *       label), no {@code methods} restriction (the gateway-side OPA decision gates per request).
 *   <li><b>Open data</b> — no {@code plugin_config_id} and no upstream credential, but {@code
 *       methods} restricted to read-only verbs ({@link #OPEN_DATA_METHODS}): "open" means anonymous
 *       <i>read</i>. Without OIDC/OPA on the route, this method gate is the only thing keeping
 *       anonymous writes (POST/PATCH/DELETE) away from FROST (MR !547 review finding 1).
 *   <li><b>Always</b> — internal trust headers are stripped via {@code
 *       proxy-rewrite.headers.remove} (see {@link ApisixHandlerSettings#ALWAYS_STRIPPED_HEADERS}).
 * </ul>
 *
 * <p>The FROST upstream credential applies only to {@link RouteUpstreamKind#SENSOR} (STA) routes.
 * {@link RouteUpstreamKind#MAP} (OWS) routes carry <b>no</b> upstream credential — GeoServer serves
 * the workspace OWS endpoint anonymously, so the protected/open gate above is the whole story for
 * them. A route's kind is recorded at CREATE via {@link #MANAGED_STANDARD_LABEL} so UPDATE/RESTORE
 * (which read the route back from APISIX without the standard in their payload) toggle it
 * correctly.
 *
 * <p>CREATE builds a fresh skeleton and runs it through the same {@link #applyAuthState} as
 * UPDATE/RESTORE, so the open/protected semantics cannot drift between the create and toggle paths.
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
   * means {@link RouteUpstreamKind#SENSOR}, keeping pre-existing STA route bodies unchanged.
   */
  static final String MANAGED_STANDARD_LABEL = "civitas-named-api-standard";

  /**
   * HTTP methods allowed on open-data routes: anonymous read plus the verbs browsers need for it
   * (HEAD probes, CORS preflight OPTIONS — FROST answers CORS itself). Everything else (writes) has
   * no route and is rejected by APISIX with 404.
   */
  static final List<String> OPEN_DATA_METHODS = List.of("GET", "HEAD", "OPTIONS");

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
      Set.of("Authorization", FrostUpstreamAuth.DEFAULT_API_KEY_HEADER);

  private final ApisixHandlerSettings settings;

  RouteAuthConfigurer(ApisixHandlerSettings settings) {
    this.settings = settings;
  }

  /**
   * Builds the full route body for CREATE_ROUTE: URIs, host pinning, upstream binding,
   * proxy-rewrite path mapping — then applies the open/protected auth state through the same path
   * UPDATE/RESTORE use. {@code routePath} is the per-named-API {@code /v1/datasets/{id}/{slug}};
   * {@code upstreamId} keys the dataset upstream selected by {@code kind} (the FROST-project
   * upstream for {@link RouteUpstreamKind#SENSOR}, the map-server upstream for {@link
   * RouteUpstreamKind#MAP}) and is intentionally decoupled from the route id. {@code upstreamPath}
   * is the upstream path the gateway path is rewritten to (FROST project path, or {@code
   * /geoserver/{workspace}/ows} for map services).
   */
  Map<String, Object> newRouteBody(
      String routePath,
      String upstreamId,
      boolean isOpenData,
      String upstreamPath,
      RouteUpstreamKind kind) {
    Map<String, Object> body = new HashMap<>();

    // Published-data routes are pinned to the configured API virtual host (apisix.api.host)
    // so APISIX deterministically matches them (issue #1368).
    body.put("uris", new String[] {routePath, routePath + "/*"});
    body.put("hosts", new String[] {settings.apiHost()});
    body.put("upstream_id", upstreamId);
    if (settings.serviceId() != null) {
      body.put("service_id", settings.serviceId());
    }
    body.put("status", 1);

    // Rewrite gateway path to the upstream path. STA: /v1/datasets/{id}/{slug}/Things →
    // /FROST-Server/v1.1/Projects(1)/Things. OWS: /v1/datasets/{id}/{slug}?service=WFS →
    // /geoserver/{workspace}/ows?service=WFS (query string preserved by APISIX).
    Map<String, Object> proxyRewrite = new HashMap<>();
    proxyRewrite.put("regex_uri", new String[] {"^" + routePath + "(/.*)?$", upstreamPath + "$1"});
    Map<String, Object> plugins = new HashMap<>();
    plugins.put("proxy-rewrite", proxyRewrite);
    body.put("plugins", plugins);

    // OWS routes self-describe their kind so a later UPDATE/RESTORE (which reads the route back
    // from
    // APISIX without the named-API standard in its payload) knows not to inject FROST credentials.
    // STA routes carry no marker and default to SENSOR — keeping existing route bodies unchanged.
    if (kind == RouteUpstreamKind.MAP) {
      RouteLabels.put(body, MANAGED_STANDARD_LABEL, RouteUpstreamKind.OWS);
    }

    applyAuthState(body, isOpenData);
    return body;
  }

  /**
   * Sets or removes {@code plugin_config_id}, the {@code methods} read-only gate, and the upstream
   * auth header in {@code proxy-rewrite} according to {@code isOpenData}. Mutates the given route
   * map in place. Used by CREATE (on a fresh skeleton), UPDATE (toggle) and RESTORE (rollback), so
   * every path produces the same auth shape.
   */
  void applyAuthState(Map<String, Object> route, boolean isOpenData) {
    boolean sensor = readUpstreamKind(route) == RouteUpstreamKind.SENSOR;

    // FROST upstream credential is tracked via a route label so a later toggle can clean a stale
    // entry left behind by a different auth scheme or a renamed API key header. Only SENSOR
    // (STA → FROST) routes carry it; read the previously-set header BEFORE flipping the label.
    String staleManagedHeader = sensor ? RouteLabels.read(route, MANAGED_AUTH_HEADER_LABEL) : null;

    if (isOpenData) {
      route.remove("plugin_config_id");
      if (sensor) {
        RouteLabels.remove(route, MANAGED_AUTH_HEADER_LABEL);
      }
      // Open data = anonymous READ. With no OIDC/OPA on the route, restricting the matchable
      // methods is the gateway's only write protection for the upstream.
      route.put("methods", OPEN_DATA_METHODS);
    } else {
      route.put("plugin_config_id", settings.pluginConfigId());
      if (sensor) {
        RouteLabels.put(route, MANAGED_AUTH_HEADER_LABEL, settings.frostAuth().headerName());
      }
      // Protected routes carry no methods filter: OPA decides per request, and a future
      // authorized-write feature must not be blocked by a stale route-level gate.
      route.remove("methods");
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
    if (sensor) {
      mergeProxyRewriteHeaders(proxyRewrite, isOpenData, staleManagedHeader);
    } else {
      // OWS map-service routes never carry an upstream credential — GeoServer serves the workspace
      // OWS endpoint anonymously and the gateway/OPA gate is the authorization boundary. Only the
      // always-strip list (internal trust headers) applies.
      applyHeaderStrip(proxyRewrite);
    }
  }

  /** A route's upstream kind, read from {@link #MANAGED_STANDARD_LABEL}; absent marker ⇒ SENSOR. */
  private static RouteUpstreamKind readUpstreamKind(Map<String, Object> route) {
    String standard = RouteLabels.read(route, MANAGED_STANDARD_LABEL);
    return RouteUpstreamKind.fromStandard(standard).orElse(RouteUpstreamKind.SENSOR);
  }

  /**
   * Applies only the adapter's {@code headers.remove} strip list (no upstream credential) — the
   * map-service (OWS) path. Foreign {@code headers.*} entries are preserved.
   */
  @SuppressWarnings("unchecked")
  private void applyHeaderStrip(Map<String, Object> proxyRewrite) {
    Map<String, Object> headers = mutableMap((Map<String, Object>) proxyRewrite.get("headers"));
    mergeStripList(headers);
    if (headers.isEmpty()) {
      proxyRewrite.remove("headers");
    } else {
      proxyRewrite.put("headers", headers);
    }
  }

  /**
   * Detects whether a route is currently configured as private. The single authoritative signal is
   * {@code plugin_config_id} — the gateway-side OIDC/OPA gate. Foreign {@code headers.set} entries
   * (operator-added trace headers, etc.) do not mark a route as private; relying on them would
   * misclassify public routes that an operator decorated independently.
   */
  boolean routeIsPrivate(Map<String, Object> route) {
    Object existingPluginConfigId = route.get("plugin_config_id");
    return existingPluginConfigId != null
        && !(existingPluginConfigId instanceof String s && s.isBlank());
  }

  /**
   * Merges the adapter-managed entries into an existing {@code proxy-rewrite.headers} block instead
   * of replacing it — preserves foreign {@code headers.add} entries, {@code headers.set} entries
   * owned by other operators, and additional {@code headers.remove} items.
   */
  @SuppressWarnings("unchecked")
  private void mergeProxyRewriteHeaders(
      Map<String, Object> proxyRewrite, boolean isOpenData, String staleManagedHeader) {
    Map<String, Object> headers = mutableMap((Map<String, Object>) proxyRewrite.get("headers"));

    mergeAuthSetEntries(headers, isOpenData, staleManagedHeader);
    mergeStripList(headers);

    if (headers.isEmpty()) {
      proxyRewrite.remove("headers");
    } else {
      proxyRewrite.put("headers", headers);
    }
  }

  /**
   * {@code headers.set} — touch only adapter-managed FROST upstream auth entries. A stale entry
   * from a previous config (different scheme or renamed API key header) is identified via the route
   * label written when we last set it; remove it before applying the current value.
   */
  @SuppressWarnings("unchecked")
  private void mergeAuthSetEntries(
      Map<String, Object> headers, boolean isOpenData, String staleManagedHeader) {
    Map<String, Object> set = mutableMap((Map<String, Object>) headers.get("set"));
    String authHeaderName = settings.frostAuth().headerName();
    if (staleManagedHeader != null && !staleManagedHeader.equals(authHeaderName)) {
      set.remove(staleManagedHeader);
    }
    if (isOpenData) {
      set.remove(authHeaderName);
      // Public routes must never carry FROST credentials. Wipe well-known adapter-managed
      // header names unconditionally — this covers pre-label legacy routes, inconsistent state
      // left behind by external mutation, and the rare case of a config-time scheme change.
      // Non-auth foreign entries (e.g. user-added X-Trace-Id) are preserved.
      for (String legacy : LEGACY_ADAPTER_AUTH_HEADERS) {
        set.remove(legacy);
      }
    } else {
      set.put(authHeaderName, settings.frostAuth().headerValue());
    }
    if (set.isEmpty()) {
      headers.remove("set");
    } else {
      headers.put("set", set);
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

  private static List<String> readStringList(Object value) {
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
