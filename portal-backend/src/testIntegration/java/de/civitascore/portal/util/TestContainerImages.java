package de.civitascore.portal.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Central registry of Docker image versions used in Testcontainers integration tests.
 *
 * <p>Versions are tracked by Renovate via a {@code customManager} in {@code renovate.json}: lines
 * annotated with {@code // renovate: datasource=docker} are matched and bumped automatically, and
 * the {@code pinDigests: true} rule appends {@code @sha256:} digests for reproducibility.
 *
 * <p>{@link #POSTGIS} pins {@code 16-3.5-alpine} because the {@code postgis/postgis} image
 * publishes no PG16 + PostGIS 3.6 tag.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestContainerImages {

  // renovate: datasource=docker
  public static final String POSTGRES = "postgres:16-alpine";

  // renovate: datasource=docker
  public static final String KEYCLOAK = "quay.io/keycloak/keycloak:26.6.4";

  // renovate: datasource=docker
  public static final String KAFKA = "apache/kafka:3.8.0";

  // renovate: datasource=docker
  public static final String MOSQUITTO = "eclipse-mosquitto:2.0.20";

  // renovate: datasource=docker
  public static final String POSTGIS = "postgis/postgis:16-3.5-alpine";

  // renovate: datasource=docker
  public static final String FROST = "fraunhoferiosb/frost-server-http:2.7.3";
}
