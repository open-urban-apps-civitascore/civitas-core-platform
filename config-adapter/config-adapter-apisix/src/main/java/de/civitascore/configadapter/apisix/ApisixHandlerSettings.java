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

import de.civitascore.configadapter.configuration.AdapterConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validated configuration of the {@link ApisixSagaHandler}, resolved once at initialization.
 * Initialization fails fast (throws {@link IllegalArgumentException}) when any required setting is
 * missing: {@code apisix.admin.key}, {@code apisix.api.host}, {@code apisix.api.public.url}, {@code
 * apisix.plugin.config.id} (private routes rely on this plugin_config to enforce OIDC/OPA — without
 * it private datasets would be publicly reachable), and the FROST upstream credentials APISIX uses
 * to proxy private datasets ({@code apisix.frost.api.key} or {@code
 * apisix.frost.basic.auth.username}/{@code .password}).
 *
 * <p>{@code apisix.geoserver.url} (the gateway-reachable GeoServer base, e.g. {@code
 * http://civitas-geoserver:8080/geoserver}) is the upstream target for {@code OWS} (map services)
 * named-API routes. It is <b>not</b> required at startup — only datasets publishing an OWS named
 * API need it — so it carries a default and its presence is validated lazily in {@code
 * CREATE_ROUTE} when an OWS slug is actually routed.
 *
 * @param adminApiUrl APISIX Admin API base URL ({@code apisix.admin.url})
 * @param adminApiKey APISIX Admin API key ({@code apisix.admin.key}, required)
 * @param pluginConfigId shared plugin_config enforcing OIDC/OPA on protected routes (required)
 * @param serviceId optional APISIX service to attach saga routes to ({@code apisix.service.id})
 * @param apiHost virtual host saga routes are pinned to (required, issue #1368)
 * @param apiPublicUrl public base URL reported back to the portal (required, trailing slash
 *     stripped)
 * @param geoserverUrl gateway-reachable GeoServer base for OWS map-service routes ({@code
 *     apisix.geoserver.url}, trailing slash stripped)
 * @param frostAuth FROST upstream credentials injected on protected routes (required)
 * @param proxyRewriteHeadersToRemove client-supplied headers stripped from every saga route —
 *     always contains {@link #ALWAYS_STRIPPED_HEADERS} plus the configured list
 */
record ApisixHandlerSettings(
    String adminApiUrl,
    String adminApiKey,
    String pluginConfigId,
    String serviceId,
    String apiHost,
    String apiPublicUrl,
    String geoserverUrl,
    FrostUpstreamAuth frostAuth,
    List<String> proxyRewriteHeadersToRemove) {

  static final String ADMIN_URL_DEFAULT = "http://localhost:9180";

  /**
   * Default GeoServer base used when {@code apisix.geoserver.url} is unset. Mirrors the
   * config-adapter-geoserver default; real deployments override it with the in-cluster,
   * gateway-reachable address (e.g. {@code http://civitas-geoserver:8080/geoserver}).
   */
  static final String GEOSERVER_URL_DEFAULT = "http://localhost:8080/geoserver";

  /**
   * Internal trust headers stripped from every saga route regardless of configuration. {@code
   * X-Allowed-Scope-Ids} and {@code X-Allowed-Pool-Ids} are set by OPA ({@code
   * send_headers_upstream}) and trusted downstream for scope/datapool collection filtering — a
   * client-supplied value must never pass the gateway. The configured {@code
   * apisix.proxy.rewrite.headers.remove} list is merged ON TOP of this baseline; it cannot disable
   * it (secure-by-default, MR !547 review finding 3). Because saga routes carry a route-level
   * {@code proxy-rewrite} that overrides the plugin-config strip list (APISIX Route &gt;
   * PluginConfig precedence), this baseline MUST mirror the headers OPA emits — keep it in sync
   * with the gateway plugin-config strip list (dev-environment/apisix/apisix_conf/apisix.yaml).
   */
  static final List<String> ALWAYS_STRIPPED_HEADERS =
      List.of("X-Allowed-Scope-Ids", "X-Allowed-Pool-Ids");

  private static final String PREFIX = "apisix.";

  /** Reads and validates all handler settings from the adapter configuration. */
  static ApisixHandlerSettings from(AdapterConfig config) {
    String adminApiKey = config.getProperty(PREFIX + "admin.key");
    if (adminApiKey == null || adminApiKey.isBlank()) {
      throw new IllegalArgumentException("The APISIX admin key cannot be null or blank.");
    }
    String apiHost =
        requireNonBlank(
            config.getProperty(PREFIX + "api.host"),
            "apisix.api.host must be configured — published dataset routes are bound to this"
                + " virtual host so APISIX can match incoming requests (issue #1368).");
    String apiPublicUrl =
        requireNonBlank(
            config.getProperty(PREFIX + "api.public.url"),
            "apisix.api.public.url must be configured — used to build the publicUrl reported"
                + " back to the portal-backend after route creation (issue #1368).");
    String pluginConfigId =
        requireNonBlank(
            config.getProperty(PREFIX + "plugin.config.id"),
            "apisix.plugin.config.id must be configured — private dataset routes rely on this"
                + " APISIX plugin_config to enforce client-side OIDC/OPA. Without it, private"
                + " datasets would be publicly reachable through the gateway.");
    FrostUpstreamAuth frostAuth =
        FrostUpstreamAuth.resolve(
            config.getProperty(PREFIX + "frost.basic.auth.username"),
            config.getProperty(PREFIX + "frost.basic.auth.password"),
            config.getProperty(PREFIX + "frost.api.key"),
            config.getProperty(
                PREFIX + "frost.api.key.header", FrostUpstreamAuth.DEFAULT_API_KEY_HEADER));

    return new ApisixHandlerSettings(
        config.getProperty(PREFIX + "admin.url", ADMIN_URL_DEFAULT),
        adminApiKey,
        pluginConfigId,
        config.getProperty(PREFIX + "service.id"),
        apiHost,
        stripTrailingSlash(apiPublicUrl),
        stripTrailingSlash(config.getProperty(PREFIX + "geoserver.url", GEOSERVER_URL_DEFAULT)),
        frostAuth,
        headersToRemove(config.getProperty(PREFIX + "proxy.rewrite.headers.remove")));
  }

  private static String requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static String stripTrailingSlash(String url) {
    return url.replaceAll("/+$", "");
  }

  /**
   * Builds the route-level strip list: the hard-coded {@link #ALWAYS_STRIPPED_HEADERS} baseline
   * plus the comma-separated {@code apisix.proxy.rewrite.headers.remove} property. APISIX merges
   * plugins by Route-over-PluginConfig precedence, so any {@code proxy-rewrite.headers.remove}
   * defined in the shared plugin_config is overridden by the route-level plugin — the deployment
   * mirrors additional removals here to preserve them on saga routes.
   */
  private static List<String> headersToRemove(String csv) {
    List<String> headers = new ArrayList<>(ALWAYS_STRIPPED_HEADERS);
    if (csv != null && !csv.isBlank()) {
      Arrays.stream(csv.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .filter(s -> !headers.contains(s))
          .forEach(headers::add);
    }
    return List.copyOf(headers);
  }
}
