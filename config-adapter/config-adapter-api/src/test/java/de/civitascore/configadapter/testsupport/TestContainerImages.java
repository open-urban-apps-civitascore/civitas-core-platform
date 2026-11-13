/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.testsupport;

/**
 * Central registry of Docker image tags used in Testcontainers integration tests.
 *
 * <p>Versioned constants carry a {@code // renovate: datasource=docker} annotation so Renovate
 * matches and bumps them automatically.
 */
public final class TestContainerImages {

  private TestContainerImages() {}

  // renovate: datasource=docker
  public static final String KAFKA = "confluentinc/cp-kafka:7.9.11";

  // renovate: datasource=docker
  public static final String KEYCLOAK = "quay.io/keycloak/keycloak:26.8.2";

  // renovate: datasource=docker
  public static final String MAILPIT = "axllent/mailpit:v1.32";

  // renovate: datasource=docker
  public static final String ETCD = "quay.io/coreos/etcd:v3.7.3";

  // renovate: datasource=docker
  public static final String APISIX = "apache/apisix:3.19.0-debian";

  // renovate: datasource=docker
  public static final String HTTP_ECHO = "mendhak/http-https-echo:34";

  // renovate: datasource=docker
  public static final String POSTGIS = "postgis/postgis:16-3.5-alpine";

  // Distinct image required for GeoServer Cloud compatibility.
  // renovate: datasource=docker
  public static final String POSTGIS_GEOSERVER = "imresamu/postgis:17-3.5";

  // renovate: datasource=docker
  public static final String GEOSERVER_CLOUD_REST = "geoservercloud/geoserver-cloud-rest:3.0.1.1";

  // renovate: datasource=docker
  public static final String FROST = "fraunhoferiosb/frost-server-http:2.8.1";

  // renovate: datasource=docker
  public static final String RABBITMQ = "rabbitmq:4.4.0-alpine";

  // renovate: datasource=docker
  public static final String NIFI = "apache/nifi:2.12.0";

  // renovate: datasource=docker
  public static final String MOSQUITTO = "eclipse-mosquitto:2.0";

  // renovate: datasource=docker
  public static final String POSTGRES = "postgres:16-alpine";

  // renovate: datasource=docker
  public static final String WIREMOCK = "wiremock/wiremock:3.13.2";
}
