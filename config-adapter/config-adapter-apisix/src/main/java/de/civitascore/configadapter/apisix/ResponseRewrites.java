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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared building blocks for the {@code response-rewrite} plugin both named-API standards install:
 * {@link OwsCapabilitiesRewrite} for GeoServer's capabilities URLs and {@link StaLinkRewrite} for
 * FROST's SensorThings links. Both face the same problem — an upstream advertises its own address
 * in the response body, and only the gateway knows the external one — so the escaping, the {@code
 * Accept-Encoding} strip and the plugin shape live here rather than in two copies.
 */
final class ResponseRewrites {

  private static final String HEADERS_KEY = "headers";
  private static final String REMOVE_KEY = "remove";

  /** Removed so the upstream answers uncompressed — the filters match raw response bytes. */
  private static final String ACCEPT_ENCODING = "Accept-Encoding";

  private ResponseRewrites() {}

  /** One {@code response-rewrite} filter: a regex applied to every occurrence in the body. */
  static Map<String, Object> filter(String regex, String replace) {
    Map<String, Object> filter = new HashMap<>();
    filter.put("regex", regex);
    filter.put("scope", "global");
    filter.put("replace", replace);
    return filter;
  }

  /**
   * Installs {@code filters} as the route's {@code response-rewrite}, replacing any earlier one so
   * a re-apply heals a route provisioned by a previous version instead of stacking filters. APISIX
   * applies them to the body in order, each seeing the previous one's output — callers that rely on
   * that ordering must pass them most-specific first.
   */
  static void install(Map<String, Object> plugins, List<Map<String, Object>> filters) {
    Map<String, Object> responseRewrite = new HashMap<>();
    responseRewrite.put("filters", filters.toArray());
    plugins.put("response-rewrite", responseRewrite);
  }

  /** Adds {@code Accept-Encoding} to {@code proxy-rewrite.headers.remove} (idempotent union). */
  @SuppressWarnings("unchecked")
  static void stripRequestBodyEncoding(Map<String, Object> proxyRewrite) {
    Object existing = proxyRewrite.get(HEADERS_KEY);
    Map<String, Object> headers =
        existing instanceof Map ? new HashMap<>((Map<String, Object>) existing) : new HashMap<>();
    List<String> remove = RouteAuthConfigurer.readStringList(headers.get(REMOVE_KEY));
    if (!remove.contains(ACCEPT_ENCODING)) {
      remove.add(ACCEPT_ENCODING);
    }
    headers.put(REMOVE_KEY, remove);
    proxyRewrite.put(HEADERS_KEY, headers);
  }

  /** Escape PCRE metacharacters in a literal path used inside a {@code response-rewrite} regex. */
  static String regexEscape(String literal) {
    return literal.replaceAll("([.^$*+?()\\[\\]{}|\\\\])", "\\\\$1");
  }
}
