/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.connectiontest.ConnectionTestConsumer;
import de.civitascore.configadapter.connectiontest.ConnectionTestFactory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the connection test as its own workload beside config-adapter, from the same image. It lacks
 * config-adapter's network identity, so platform services do not admit its tests.
 */
public final class ConnectionTestApplication {

  private static final Logger logger = LoggerFactory.getLogger(ConnectionTestApplication.class);

  private ConnectionTestApplication() {}

  public static void main(String[] args) {
    logger.info("Starting the Civitas connection test...");
    AppConfig appConfig = new AppConfig("application.properties");
    try {
      ConnectionTestConsumer consumer = ConnectionTestFactory.create(appConfig);
      HealthCheckServer healthCheckServer =
          new HealthCheckServer(appConfig.getHealthCheckPort(), List.of());
      // Closing the consumer on SIGTERM leaves the group at once, so partitions move without
      // waiting for the session timeout.
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    logger.info("Shutdown signal received");
                    consumer.close();
                    healthCheckServer.close();
                  },
                  "connection-test-shutdown"));
      healthCheckServer.start();
      consumer.start();
      healthCheckServer.markReady();
      Thread.currentThread().join();
    } catch (InterruptedException e) {
      logger.error("Connection test interrupted", e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      logger.error("Fatal error in the connection test", e);
      System.exit(1);
    }
  }
}
