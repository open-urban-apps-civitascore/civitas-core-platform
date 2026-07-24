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

import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.time.Duration;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for GeoServer integration tests. Starts a minimal GeoServer Cloud stack mirroring the
 * {@code dev-environment/geoserver/docker-compose.yaml} setup:
 *
 * <ul>
 *   <li>{@code geoserverdb} — PostGIS, catalog backend (pgconfig) and spatial data
 *   <li>{@code rabbitmq} — Spring Cloud Bus for catalog event propagation
 *   <li>{@code discovery} — Consul service discovery
 *   <li>{@code config} — Spring Cloud Config server (native profile)
 *   <li>{@code restconfig} — GeoServer Cloud REST API service (under test)
 * </ul>
 *
 * <p>Uses the singleton container pattern: containers start once per JVM and are shared across all
 * subclasses to avoid the ~60–90s GeoServer Cloud startup cost on every test class.
 */
@SuppressWarnings("resource")
abstract class AbstractGeoServerIntegrationTest {

  protected static final String GEOSERVER_CLOUD_VERSION = "2.28.3.0";
  protected static final String ADMIN_USER = "admin";
  protected static final String ADMIN_PASSWORD = "geoserver";
  protected static final String POSTGIS_DB = "geoserver";
  protected static final String POSTGIS_USER = "geoserver";
  protected static final String POSTGIS_PASSWORD = "geoserver";
  protected static final String POSTGIS_HOST_ALIAS = "geoserverdb";
  protected static final String TEST_SCHEMA = "dataset_test_uuid_001";

  protected static final Network NETWORK = Network.newNetwork();

  protected static final GenericContainer<?> POSTGIS;
  protected static final GenericContainer<?> RABBITMQ;
  protected static final GenericContainer<?> DISCOVERY;
  protected static final GenericContainer<?> CONFIG;
  protected static final GenericContainer<?> RESTCONFIG;

  static {
    POSTGIS =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.POSTGIS_GEOSERVER))
            .withNetwork(NETWORK)
            .withNetworkAliases(POSTGIS_HOST_ALIAS, "geodatabase")
            .withEnv("POSTGRES_DB", POSTGIS_DB)
            .withEnv("POSTGRES_USER", POSTGIS_USER)
            .withEnv("POSTGRES_PASSWORD", POSTGIS_PASSWORD)
            .withClasspathResourceMapping(
                "geoserver-init/01_schema.sql",
                "/docker-entrypoint-initdb.d/01_schema.sql",
                BindMode.READ_ONLY)
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
                    .withStartupTimeout(Duration.ofMinutes(2)));
    POSTGIS.start();

    RABBITMQ =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.RABBITMQ))
            .withNetwork(NETWORK)
            .withNetworkAliases("rabbitmq")
            .waitingFor(
                Wait.forLogMessage(".*Server startup complete.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));
    RABBITMQ.start();

    DISCOVERY =
        new GenericContainer<>(
                DockerImageName.parse(
                    "geoservercloud/geoserver-cloud-discovery:" + GEOSERVER_CLOUD_VERSION))
            .withNetwork(NETWORK)
            .withNetworkAliases("discovery")
            .withExposedPorts(8761)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
    DISCOVERY.start();

    CONFIG =
        new GenericContainer<>(
                DockerImageName.parse(
                    "geoservercloud/geoserver-cloud-config:" + GEOSERVER_CLOUD_VERSION))
            .withNetwork(NETWORK)
            .withNetworkAliases("config")
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(4)));
    CONFIG.start();

    RESTCONFIG =
        new GenericContainer<>(
                DockerImageName.parse(
                    "geoservercloud/geoserver-cloud-rest:" + GEOSERVER_CLOUD_VERSION))
            .withNetwork(NETWORK)
            .withNetworkAliases("restconfig")
            .withExposedPorts(8080)
            .withEnv("SPRING_PROFILES_ACTIVE", "pgconfig")
            .withEnv("EUREKA_SERVER_URL", "http://discovery:8761/eureka/")
            .withEnv("SPRING_CLOUD_CONFIG_URI", "http://config:8080")
            .withEnv("SPRING_CLOUD_CONFIG_FAIL_FAST", "true")
            .withEnv("SPRING_CLOUD_CONFIG_RETRY_MAX_ATTEMPTS", "30")
            .withEnv("SPRING_CLOUD_CONFIG_RETRY_INITIAL_INTERVAL", "2000")
            .withEnv("SPRING_CLOUD_CONFIG_RETRY_MAX_INTERVAL", "5000")
            .withEnv("PGCONFIG_HOST", POSTGIS_HOST_ALIAS)
            .withEnv("PGCONFIG_PORT", "5432")
            .withEnv("PGCONFIG_DATABASE", POSTGIS_DB)
            .withEnv("PGCONFIG_USERNAME", POSTGIS_USER)
            .withEnv("PGCONFIG_PASSWORD", POSTGIS_PASSWORD)
            .withEnv("PGCONFIG_SCHEMA", "pgconfig")
            .withEnv("PGCONFIG_INITIALIZE", "true")
            .withEnv("SPRING_RABBITMQ_HOST", "rabbitmq")
            .withEnv("SPRING_RABBITMQ_PORT", "5672")
            .withEnv("SPRING_RABBITMQ_USERNAME", "guest")
            .withEnv("SPRING_RABBITMQ_PASSWORD", "guest")
            .withEnv("GEOWEBCACHE_CACHE_DIR", "/tmp/geowebcache")
            .waitingFor(
                Wait.forHttp("/rest/about/version.json")
                    .forStatusCode(200)
                    .withBasicCredentials(ADMIN_USER, ADMIN_PASSWORD)
                    .withStartupTimeout(Duration.ofMinutes(5)));
    RESTCONFIG.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  RESTCONFIG.stop();
                  CONFIG.stop();
                  DISCOVERY.stop();
                  RABBITMQ.stop();
                  POSTGIS.stop();
                  NETWORK.close();
                }));
  }

  protected static String geoServerUrl() {
    return "http://" + RESTCONFIG.getHost() + ":" + RESTCONFIG.getMappedPort(8080);
  }
}
