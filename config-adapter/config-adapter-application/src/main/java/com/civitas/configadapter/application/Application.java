/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.application;

import static java.util.Objects.requireNonNull;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.messaging.EventConsumer;
import com.civitas.configadapter.messaging.EventPublisher;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) {
    logger.info("Starting Civitas Config Adapter...");

    Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Shutdown signal received")));
    Application application = new Application();
    application.run();
    logger.info("Application shutdown complete");
  }

  private final AppConfig appConfig;
  private final List<EventConsumer> consumers;

  public Application(String configFileName) {
    appConfig = new AppConfig(requireNonNull(configFileName));
    consumers = createConsumers(appConfig);
  }

  public Application() {
    this("application.properties");
  }

  protected void run() {

    try (HealthCheckServer healthCheckServer =
        new HealthCheckServer(appConfig.getHealthCheckPort(), consumers)) {
      healthCheckServer.start();

      for (EventConsumer consumer : consumers) {
        consumer.start();
      }

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

      for (EventConsumer consumer : consumers) {
        try {
          consumer.close();
        } catch (Exception e) {
          logger.error("Error closing consumer", e);
        }
      }
    }
  }

  private List<EventConsumer> createConsumers(AppConfig config) {
    List<String> adapterNames = config.getAdapterNames();

    if (adapterNames.isEmpty()) {
      throw new RuntimeException(
          "No adapters configured. Please specify 'adapters' or 'adapter.class' property");
    }

    // Determine configuration mode: combined or separate consumer/publisher
    String eventHandlerClass = config.getEventHandlerClass();
    String eventConsumerClass = config.getEventConsumerClass();
    String eventPublisherClass = config.getEventPublisherClass();

    if (eventHandlerClass != null && (eventConsumerClass != null || eventPublisherClass != null)) {
      throw new RuntimeException(
          "Configuration error: Cannot specify both 'eventhandler.class' and separate 'eventconsumer.class' or 'eventpublisher.class'");
    }

    boolean useCombinedHandler = eventHandlerClass != null;

    if (useCombinedHandler) {
      logger.info("Using combined event handler: {}", eventHandlerClass);
    } else {
      if (eventConsumerClass == null) {
        throw new RuntimeException(
            "No event consumer configured. Please specify 'eventhandler.class' or 'eventconsumer.class'");
      }
      logger.info(
          "Using separate consumer: {} and publisher: {}",
          eventConsumerClass,
          eventPublisherClass != null ? eventPublisherClass : "none");
    }

    logger.info("Creating {} adapter(s)", adapterNames.size());

    List<EventConsumer> consumers = new ArrayList<>();

    for (String adapterName : adapterNames) {
      try {
        logger.info("Loading adapter: {}", adapterName);

        ConfigAdapter adapter = createAdapter(config, adapterName);
        List<String> topics = adapter.getSubscribedTopics();

        if (topics.isEmpty()) {
          logger.warn("Adapter {} has no subscribed topics, skipping", adapterName);
          continue;
        }

        logger.info(
            "Adapter {} subscribes to {} topic(s): {}",
            adapter.getClass().getSimpleName(),
            topics.size(),
            topics);

        EventConsumer consumer;
        if (useCombinedHandler) {
          // Combined handler: single class implements both EventConsumer and EventPublisher
          consumer = createConsumer(config, eventHandlerClass, adapter);
        } else {
          // Separate consumer and publisher
          EventPublisher publisher = null;
          if (eventPublisherClass != null) {
            publisher = createPublisher(config, eventPublisherClass);
          }

          // Inject publisher into adapter
          if (publisher != null) {
            adapter.setEventPublisher(publisher);
          }

          consumer = createConsumer(config, eventConsumerClass, adapter);
        }

        consumers.add(consumer);

        logger.info("Successfully created consumer for adapter: {}", adapterName);

      } catch (Exception e) {
        logger.error("Failed to create consumer for adapter: {}", adapterName, e);
        throw new RuntimeException("Failed to create consumer for adapter: " + adapterName, e);
      }
    }

    if (consumers.isEmpty()) {
      throw new RuntimeException("No valid consumers created. Check adapter topic configurations.");
    }

    return consumers;
  }

  private EventConsumer createConsumer(
      ApplicationConfig config, String consumerClass, ConfigAdapter adapter)
      throws ClassNotFoundException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException,
          NoSuchMethodException {
    Class<?> consumerClazz = Class.forName(consumerClass);

    // Verify that the class implements EventConsumer interface
    if (!EventConsumer.class.isAssignableFrom(consumerClazz)) {
      throw new IllegalArgumentException(
          String.format("Class %s does not implement EventConsumer interface", consumerClass));
    }

    return (EventConsumer)
        consumerClazz
            .getConstructor(ApplicationConfig.class, ConfigAdapter.class)
            .newInstance(config, adapter);
  }

  private EventPublisher createPublisher(ApplicationConfig config, String publisherClass)
      throws ClassNotFoundException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException,
          NoSuchMethodException {
    Class<?> publisherClazz = Class.forName(publisherClass);

    // Verify that the class implements EventPublisher interface
    if (!EventPublisher.class.isAssignableFrom(publisherClazz)) {
      throw new IllegalArgumentException(
          String.format("Class %s does not implement EventPublisher interface", publisherClass));
    }

    return (EventPublisher)
        publisherClazz.getConstructor(ApplicationConfig.class).newInstance(config);
  }

  private ConfigAdapter createAdapter(AdapterConfig config, String adapterName) {
    ServiceLoader<ConfigAdapter> configAdapterLoader = ServiceLoader.load(ConfigAdapter.class);
    Optional<ConfigAdapter> configAdapterOpt =
        configAdapterLoader.stream()
            .filter(p -> Objects.equals(adapterName, p.get().getName()))
            .map(Provider::get)
            .findFirst();
    if (configAdapterOpt.isPresent()) {
      ConfigAdapter configAdapter = configAdapterOpt.get();
      if (configAdapter instanceof AbstractConfigAdapter aca) {
        aca.initialize(config);
        return configAdapter;
      } else {
        throw new IllegalStateException(
            String.format("Class %s does not implement AbstractConfigAdapter class", adapterName));
      }
    }
    return null;
  }
}
