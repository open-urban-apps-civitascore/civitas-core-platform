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
import jakarta.ws.rs.client.Invocation;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for authenticating FROST SensorThings API requests. Implementations apply the
 * appropriate authentication header to a JAX-RS request builder.
 *
 * <p>Use the factory methods {@link #basicAuth(String, String)} and {@link #apiKey(String, String)}
 * to obtain instances. The strategy is chosen once during adapter initialization and reused for
 * every request, so the authentication decision is made at configuration time, not at call time.
 */
@FunctionalInterface
interface FrostAuthStrategy {

  Logger log = LoggerFactory.getLogger(FrostAuthStrategy.class);

  String DEFAULT_API_KEY_HEADER = "X-API-Key";

  /**
   * Applies authentication to the given request builder.
   *
   * @param builder the JAX-RS request builder
   * @return the builder with the authentication header set
   */
  Invocation.Builder apply(Invocation.Builder builder);

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
   * Factory that selects the appropriate strategy based on the provided configuration. Prefers
   * Basic Auth when a username is configured; falls back to API key otherwise.
   *
   * @param basicAuthUsername the Basic Auth username (may be {@code null})
   * @param basicAuthPassword the Basic Auth password (may be {@code null})
   * @param apiKeyHeader the header name for API key authentication
   * @param apiKey the API key value
   * @return the selected authentication strategy
   * @throws IllegalArgumentException if neither Basic Auth username nor API key is configured
   */
  static FrostAuthStrategy create(
      String basicAuthUsername, String basicAuthPassword, String apiKeyHeader, String apiKey) {
    boolean hasBasicAuth = basicAuthUsername != null && !basicAuthUsername.isBlank();
    boolean hasApiKey = apiKey != null && !apiKey.isBlank();

    if (!hasBasicAuth && !hasApiKey) {
      throw new IllegalArgumentException(
          "FROST authentication not configured: provide either basic.auth.username or api.key");
    }

    if (hasBasicAuth) {
      if (hasApiKey) {
        log.warn("Both Basic Auth and API Key configured — using Basic Auth");
      }
      return basicAuth(basicAuthUsername, basicAuthPassword);
    }
    return apiKey(apiKeyHeader, apiKey);
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
    String apiKey = config.getProperty(prefix + "api.key");
    String apiKeyHeader = config.getProperty(prefix + "api.key.header", DEFAULT_API_KEY_HEADER);
    String basicAuthUsername = config.getProperty(prefix + "basic.auth.username");
    String basicAuthPassword = config.getProperty(prefix + "basic.auth.password");
    return create(basicAuthUsername, basicAuthPassword, apiKeyHeader, apiKey);
  }
}
