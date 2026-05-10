/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.geoserver;

import jakarta.ws.rs.client.Invocation;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Basic Auth strategy for the GeoServer REST API (management/admin endpoints). Credentials are
 * required: the config adapter must authenticate to create, update, and delete GeoServer entities.
 *
 * <p>Note: authentication for data access (WFS/WMS endpoints) is handled upstream by APISIX and OPA
 * — this strategy applies only to the GeoServer REST management API.
 *
 * <p>Credentials are encoded once at construction time and reused for every request.
 */
@FunctionalInterface
interface GeoServerAuth {

  /**
   * Applies Basic Auth to the given request builder.
   *
   * @param builder the JAX-RS request builder
   * @return the builder with the {@code Authorization} header set
   */
  Invocation.Builder apply(Invocation.Builder builder);

  /**
   * Returns a Basic Auth strategy that encodes the given credentials once.
   *
   * @param username the Basic Auth username (must not be {@code null} or blank)
   * @param password the Basic Auth password (may be {@code null}, treated as empty)
   */
  static GeoServerAuth basicAuth(String username, String password) {
    String pwd = password != null ? password : "";
    String credentials =
        Base64.getEncoder().encodeToString((username + ":" + pwd).getBytes(StandardCharsets.UTF_8));
    String headerValue = "Basic " + credentials;
    return builder -> builder.header("Authorization", headerValue);
  }

  /**
   * Factory that creates a Basic Auth strategy from configuration. Credentials are mandatory:
   * {@code geoserver.admin.user} must be configured.
   *
   * @param username the Basic Auth username
   * @param password the Basic Auth password (may be {@code null})
   * @return a Basic Auth strategy
   * @throws IllegalArgumentException if {@code username} is {@code null} or blank
   */
  static GeoServerAuth create(String username, String password) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException(
          "GeoServer authentication not configured: provide geoserver.admin.user");
    }
    return basicAuth(username, password);
  }
}
