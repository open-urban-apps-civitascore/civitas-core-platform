/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for FROST integration tests. Uses the singleton container pattern so PostGIS and
 * FROST-Server start only once per JVM.
 */
@SuppressWarnings("resource")
abstract class AbstractFrostIntegrationTest {

  protected static final Network NETWORK = Network.newNetwork();

  protected static final GenericContainer<?> POSTGIS;
  protected static final GenericContainer<?> FROST;

  static {
    POSTGIS =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.POSTGIS))
            .withNetwork(NETWORK)
            .withNetworkAliases("database")
            .withEnv("POSTGRES_DB", "sensorthings")
            .withEnv("POSTGRES_USER", "sensorthings")
            .withEnv("POSTGRES_PASSWORD", "ChangeMe")
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
    POSTGIS.start();

    FROST =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.FROST))
            .withNetwork(NETWORK)
            .withExposedPorts(8080)
            .dependsOn(POSTGIS)
            .withEnv("serviceRootUrl", "http://localhost:8080/FROST-Server/")
            .withEnv("plugins_projects_enable", "true")
            .withEnv("plugins_projects_enableDefaultRules", "false")
            .withEnv("plugins_modelLoader_enable", "true")
            .withEnv("plugins_multiDatastream_enable", "false")
            .withEnv("plugins_actuation_enable", "false")
            .withEnv("persistence_db_driver", "org.postgresql.Driver")
            .withEnv("persistence_db_url", "jdbc:postgresql://database:5432/sensorthings")
            .withEnv("persistence_db_username", "sensorthings")
            .withEnv("persistence_db_password", "ChangeMe")
            .withEnv("persistence_autoUpdateDatabase", "true")
            .waitingFor(
                Wait.forHttp("/FROST-Server/v1.1/Things")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));
    FROST.start();

    // Singleton container pattern: containers are shared across all subclasses of
    // AbstractFrostIntegrationTest for performance (one startup instead of two).
    // A JVM shutdown hook ensures cleanup regardless of test execution order.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  FROST.stop();
                  POSTGIS.stop();
                  NETWORK.close();
                }));
  }
}
