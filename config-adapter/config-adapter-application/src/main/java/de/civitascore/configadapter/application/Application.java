/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import static java.util.Objects.requireNonNull;

import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.flowable.common.FlowableSagaOrchestrator;
import de.civitascore.configadapter.messaging.EventConsumer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application runner for the Civitas Config Adapter framework.
 *
 * <p>This class orchestrates the startup and lifecycle of config adapters and their associated
 * event consumers/publishers. Component creation is delegated to {@link ConsumerFactory} and {@link
 * SagaComponentFactory}.
 *
 * @see ConsumerFactory
 * @see SagaComponentFactory
 */
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) throws FatalAdapterException {
    logger.info("Starting Civitas Config Adapter...");

    Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Shutdown signal received")));
    Application application = new Application();
    application.run();
    logger.info("Application shutdown complete");
  }

  private final AppConfig appConfig;
  private final List<EventConsumer> consumers;
  private final SagaComponents sagaComponents;

  /**
   * Creates a new Application instance with the specified configuration file.
   *
   * @param configFileName the name of the properties file to load from the classpath, must not be
   *     null
   * @throws NullPointerException if configFileName is null
   * @throws FatalAdapterException if configuration is invalid or required components cannot be
   *     created
   */
  public Application(String configFileName) throws FatalAdapterException {
    this(configFileName, new SagaComponentFactory());
  }

  /**
   * Seam constructor for tests: lets a test inject a no-op {@link SagaComponentFactory} so
   * consumer-wiring tests don't pay for a real Flowable engine bootstrap (H2 + BPMN-process
   * deployment) on every case. Production always uses the no-arg factory via {@link
   * #Application(String)}.
   *
   * @param configFileName the name of the properties file to load from the classpath, must not be
   *     null
   * @param sagaComponentFactory the factory used to build the saga orchestration components
   * @throws NullPointerException if configFileName is null
   * @throws FatalAdapterException if configuration is invalid or required components cannot be
   *     created
   */
  Application(String configFileName, SagaComponentFactory sagaComponentFactory)
      throws FatalAdapterException {
    appConfig = new AppConfig(requireNonNull(configFileName));
    consumers = new ConsumerFactory().createAll(appConfig);
    sagaComponents = sagaComponentFactory.create(appConfig);
  }

  /**
   * Creates a new Application instance using the default configuration file
   * "application.properties".
   *
   * @throws FatalAdapterException if configuration is invalid or required components cannot be
   *     created
   */
  public Application() throws FatalAdapterException {
    this("application.properties");
  }

  /**
   * Runs the application, starting all consumers and the health check server.
   *
   * <p>This method blocks until the application is interrupted (e.g., via Ctrl+C). On shutdown, it
   * gracefully closes all consumers and releases resources.
   */
  protected void run() {

    try (HealthCheckServer healthCheckServer =
        new HealthCheckServer(appConfig.getHealthCheckPort(), consumers)) {
      healthCheckServer.start();

      for (EventConsumer consumer : consumers) {
        consumer.start();
      }

      sagaComponents.flowableOrchestrator().ifPresent(FlowableSagaOrchestrator::start);

      healthCheckServer.markReady();

      logger.info(
          "Civitas Config Adapter is running with {} consumer(s). Press Ctrl+C to stop.",
          consumers.size());

      Thread.currentThread().join();

    } catch (InterruptedException e) {
      logger.error("Application interrupted", e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      logger.error("Fatal error in application", e);
      System.exit(1);
    } finally {
      logger.info("Shutting down {} consumer(s)", consumers.size());

      sagaComponents
          .flowableOrchestrator()
          .ifPresent(
              flowable -> {
                try {
                  flowable.close();
                } catch (Exception e) {
                  logger.error("Error stopping Flowable orchestrator", e);
                }
              });

      for (EventConsumer consumer : consumers) {
        try {
          consumer.close();
        } catch (Exception e) {
          logger.error("Error closing consumer", e);
        }
      }
    }
  }
}
