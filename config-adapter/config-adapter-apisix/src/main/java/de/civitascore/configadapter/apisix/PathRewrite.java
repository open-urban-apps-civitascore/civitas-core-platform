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

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code proxy-rewrite.regex_uri} mapping a named API's gateway path onto its upstream path.
 *
 * <p>A trailing slash forwarded verbatim reaches a different upstream resource than the bare path:
 * FROST answers it with 404, and GeoServer serves it from its admin service, where the
 * map-rendering limit is switched off. Both forms therefore have to arrive at one upstream path.
 */
final class PathRewrite {

  /** The single-pair shape a route provisioned before the pairs split carries. */
  private static final Pattern LEGACY_PATTERN = Pattern.compile("^\\^(.*)\\(/\\.\\*\\)\\?\\$$");

  /** Back-reference carrying a sub-path through to the upstream path. */
  private static final String SUB_PATH_GROUP = "$1";

  private static final int LEGACY_PAIR_SIZE = 2;

  private PathRewrite() {}

  /**
   * Pattern/replacement pairs for {@code proxy-rewrite.regex_uri}. APISIX tries them in order and
   * keeps the first match, so the bare path and one carrying a lone trailing slash both reach the
   * same upstream path, while a deeper sub-path keeps its suffix.
   */
  static String[] pairs(String routePath, String upstreamPath) {
    return new String[] {
      "^" + routePath + "/?$",
      upstreamPath,
      "^" + routePath + "(/.+)$",
      upstreamPath + SUB_PATH_GROUP
    };
  }

  /**
   * Replaces the single-pair rewrite an earlier route carries with {@link #pairs}, so a route
   * already in APISIX is corrected by its next UPDATE or RESTORE instead of only when its dataset
   * is provisioned again. Left untouched when the shape is anything else.
   */
  static void normalize(Map<String, Object> proxyRewrite) {
    List<String> regexUri = RouteAuthConfigurer.readStringList(proxyRewrite.get("regex_uri"));
    if (regexUri.size() != LEGACY_PAIR_SIZE) {
      return;
    }
    Matcher legacyMatch = LEGACY_PATTERN.matcher(regexUri.get(0));
    String replacement = regexUri.get(1);
    if (!legacyMatch.matches() || !replacement.endsWith(SUB_PATH_GROUP)) {
      return;
    }
    String upstreamPath = replacement.substring(0, replacement.length() - SUB_PATH_GROUP.length());
    proxyRewrite.put("regex_uri", pairs(legacyMatch.group(1), upstreamPath));
  }
}
