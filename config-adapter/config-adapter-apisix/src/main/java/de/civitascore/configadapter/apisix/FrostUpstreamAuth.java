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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FROST upstream credentials as the single HTTP header APISIX injects ({@code
 * proxy-rewrite.headers.set}) when proxying protected dataset routes. Mirrors {@code
 * FrostAuthStrategy.create} (config-adapter-frost) so the gateway speaks the same scheme as the
 * FROST adapter: Basic Auth takes precedence when both are configured; at least one must be set.
 *
 * @param headerName HTTP header carrying the credential ({@code Authorization} for Basic Auth, the
 *     configured API key header otherwise)
 * @param headerValue credential value ({@code Basic <base64>} or the raw API key)
 */
record FrostUpstreamAuth(String headerName, String headerValue) {

  /** Default header name for API key auth when {@code apisix.frost.api.key.header} is unset. */
  static final String DEFAULT_API_KEY_HEADER = "X-API-Key";

  private static final Logger LOG = LoggerFactory.getLogger(FrostUpstreamAuth.class);

  /**
   * Resolves the upstream auth header from the configured credentials. Fails fast (throws {@link
   * IllegalArgumentException}) when neither scheme is fully configured — APISIX needs to
   * authenticate against FROST when proxying protected datasets.
   */
  static FrostUpstreamAuth resolve(
      String basicAuthUser, String basicAuthPass, String apiKey, String apiKeyHeader) {
    boolean hasBasicAuth = basicAuthUser != null && !basicAuthUser.isBlank();
    boolean hasApiKey = apiKey != null && !apiKey.isBlank();

    if (!hasBasicAuth && !hasApiKey) {
      throw new IllegalArgumentException(
          "FROST upstream authentication not configured: provide either"
              + " apisix.frost.basic.auth.username (+password) or apisix.frost.api.key — APISIX"
              + " needs to authenticate against FROST when proxying private datasets.");
    }
    return hasBasicAuth
        ? basicAuth(basicAuthUser, basicAuthPass, hasApiKey)
        : apiKey(apiKey, apiKeyHeader);
  }

  private static FrostUpstreamAuth basicAuth(String user, String password, boolean apiKeyAlsoSet) {
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException(
          "apisix.frost.basic.auth.password must be configured when"
              + " apisix.frost.basic.auth.username is set.");
    }
    if (apiKeyAlsoSet) {
      LOG.warn("Both apisix.frost basic auth and api key configured — using Basic Auth");
    }
    String encoded =
        Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    return new FrostUpstreamAuth("Authorization", "Basic " + encoded);
  }

  private static FrostUpstreamAuth apiKey(String apiKey, String apiKeyHeader) {
    if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
      throw new IllegalArgumentException(
          "apisix.frost.api.key.header must not be blank when apisix.frost.api.key is set —"
              + " otherwise APISIX would receive headers.set with an empty header name.");
    }
    return new FrostUpstreamAuth(apiKeyHeader, apiKey);
  }
}
