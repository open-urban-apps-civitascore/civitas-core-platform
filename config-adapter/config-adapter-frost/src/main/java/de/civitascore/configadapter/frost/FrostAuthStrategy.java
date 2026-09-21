/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import de.civitascore.configadapter.configuration.AdapterConfig;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for authenticating FROST SensorThings API requests. Implementations apply the
 * appropriate authentication header to an OkHttp request builder.
 *
 * <p>Use the factory methods {@link #basicAuth(String, String)} and {@link #apiKey(String, String)}
 * to obtain instances. The strategy is chosen once during adapter initialization and reused for
 * every request, so the authentication decision is made at configuration time, not at call time.
 */
@FunctionalInterface
interface FrostAuthStrategy {

  Logger LOG = LoggerFactory.getLogger(FrostAuthStrategy.class);

  String DEFAULT_API_KEY_HEADER = "X-API-Key";

  /**
   * Applies authentication to the given request builder.
   *
   * @param builder the OkHttp request builder
   * @return the builder with the authentication header set
   */
  Request.Builder apply(Request.Builder builder);

  /**
   * Creates a Basic Auth strategy that encodes the given credentials once and reuses the encoded
   * value for every request.
   *
   * @param username the Basic Auth username (must not be {@code null} or blank)
   * @param password the Basic Auth password (may be {@code null}, treated as empty)
   * @return a strategy that sets the {@code Authorization: Basic ...} header
   */
  static FrostAuthStrategy basicAuth(String username, String password) {
    Objects.requireNonNull(username, "username must not be null");
    String pwd = password != null ? password : "";
    String credentials =
        Base64.getEncoder().encodeToString((username + ":" + pwd).getBytes(StandardCharsets.UTF_8));
    String headerValue = "Basic " + credentials;
    return builder -> builder.header("Authorization", headerValue);
  }

  /**
   * Creates an API key strategy that sends the key in a custom header on every request.
   *
   * @param headerName the header name (e.g. {@code X-API-Key})
   * @param key the API key value
   * @return a strategy that sets the specified header
   */
  static FrostAuthStrategy apiKey(String headerName, String key) {
    Objects.requireNonNull(headerName, "headerName must not be null");
    Objects.requireNonNull(key, "key must not be null");
    return builder -> builder.header(headerName, key);
  }

  /**
   * The four FROST auth settings as read from configuration. Groups the values so factory
   * signatures stay readable; all fields may be {@code null} when unconfigured.
   */
  record Credentials(
      String basicAuthUsername, String basicAuthPassword, String apiKeyHeader, String apiKey) {}

  /**
   * Factory that selects the appropriate strategy for the given credentials. Prefers Basic Auth
   * when a username is configured; falls back to API key otherwise.
   *
   * @param credentials the configured FROST auth settings
   * @return the selected authentication strategy
   * @throws IllegalArgumentException if neither Basic Auth username nor API key is configured
   */
  static FrostAuthStrategy create(Credentials credentials) {
    boolean hasBasicAuth =
        credentials.basicAuthUsername() != null && !credentials.basicAuthUsername().isBlank();
    boolean hasApiKey = credentials.apiKey() != null && !credentials.apiKey().isBlank();

    if (!hasBasicAuth && !hasApiKey) {
      throw new IllegalArgumentException(
          "FROST authentication not configured: provide either basic.auth.username or api.key");
    }

    if (hasBasicAuth) {
      if (hasApiKey) {
        LOG.warn("Both Basic Auth and API Key configured — using Basic Auth");
      }
      return basicAuth(credentials.basicAuthUsername(), credentials.basicAuthPassword());
    }
    return apiKey(credentials.apiKeyHeader(), credentials.apiKey());
  }

  /**
   * Creates the appropriate authentication strategy by reading FROST auth properties from the given
   * configuration. Reads {@code {adapterName}.basic.auth.username}, {@code
   * {adapterName}.basic.auth.password}, {@code {adapterName}.api.key}, and {@code
   * {adapterName}.api.key.header} (defaults to {@value DEFAULT_API_KEY_HEADER}).
   *
   * @param config the adapter configuration to read properties from
   * @param adapterName the adapter name used as property prefix
   * @return the selected authentication strategy
   * @throws IllegalArgumentException if neither Basic Auth nor API key is configured
   */
  static FrostAuthStrategy fromConfig(AdapterConfig config, String adapterName) {
    String prefix = adapterName + ".";
    return create(
        new Credentials(
            config.getProperty(prefix + "basic.auth.username"),
            config.getProperty(prefix + "basic.auth.password"),
            config.getProperty(prefix + "api.key.header", DEFAULT_API_KEY_HEADER),
            config.getProperty(prefix + "api.key")));
  }
}
