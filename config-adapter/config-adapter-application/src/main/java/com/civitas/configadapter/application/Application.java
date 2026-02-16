/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.application;

import static com.civitas.configadapter.util.ServiceLoaderUtils.getInstanceByFilter;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.adapter.SagaCommandHandler;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.messaging.EventConsumer;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.event.handler.kafka.KafkaSagaCommandConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application runner for the Civitas Config Adapter framework.
 *
 * <p>This class orchestrates the startup and lifecycle of config adapters and their associated
 * event consumers/publishers. It uses {@link java.util.ServiceLoader} to discover and instantiate
 * components by name.
 *
 * <h2>Configuration Modes</h2>
 *
 * <p>The application supports two configuration modes:
 *
 * <ul>
 *   <li><b>Combined handler</b>: Use {@code eventhandler.name} to specify a single component that
 *       handles both consuming and publishing (e.g., KafkaEventHandler)
 *   <li><b>Separate consumer/publisher</b>: Use {@code eventconsumer.name} and optionally {@code
 *       eventpublisher.name} to configure independent components
 * </ul>
 *
 * <h2>Component Discovery</h2>
 *
 * <p>Components are discovered via ServiceLoader and matched by name:
 *
 * <ul>
 *   <li>Adapters: Registered under {@link ConfigAdapter}, matched by {@code adapters} config
 *   <li>Consumers: Registered under {@link EventConsumer}, matched by {@code eventconsumer.name}
 *   <li>Publishers: Registered under {@link EventPublisher}, matched by {@code eventpublisher.name}
 * </ul>
 *
 * @see ApplicationConfig
 * @see ConfigAdapter
 * @see EventConsumer
 * @see EventPublisher
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
  private KafkaSagaCommandConsumer sagaCommandConsumer;

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
    appConfig = new AppConfig(requireNonNull(configFileName));
    consumers = createConsumers(appConfig);
    sagaCommandConsumer = createSagaCommandConsumer(appConfig);
  }

  /**
   * Creates a new Application instance using the default configuration file
   * "application.properties".
   *
   * @throws com.civitas.configadapter.exception.FatalAdapterException if configuration is invalid
   *     or required components cannot be created
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

      if (sagaCommandConsumer != null) {
        sagaCommandConsumer.start();
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

      if (sagaCommandConsumer != null) {
        try {
          sagaCommandConsumer.close();
        } catch (Exception e) {
          logger.error("Error closing saga command consumer", e);
        }
      }

      for (EventConsumer consumer : consumers) {
        try {
          consumer.close();
        } catch (Exception e) {
          logger.error("Error closing consumer", e);
        }
      }
    }
  }

  private List<EventConsumer> createConsumers(AppConfig config) throws FatalAdapterException {
    List<String> adapterNames = config.getAdapterNames();
    if (adapterNames.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.CONFIGURATION_ERROR,
          "No adapters configured. Please specify 'adapters' or 'adapter.class' property");
    }

    EventHandlerNames handlerNames = resolveEventHandlerNames(config);
    logger.info("Creating {} adapter(s)", adapterNames.size());

    List<EventConsumer> consumers = new ArrayList<>();
    for (String adapterName : adapterNames) {
      EventConsumer consumer = createConsumerForAdapter(config, adapterName, handlerNames);
      if (consumer != null) {
        consumers.add(consumer);
      }
    }

    if (consumers.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.CONFIGURATION_ERROR,
          "No valid consumers created. Check adapter topic configurations.");
    }

    return consumers;
  }

  private EventHandlerNames resolveEventHandlerNames(AppConfig config)
      throws FatalAdapterException {
    String eventHandlerName = config.getEventHandlerName();
    String eventConsumerName = config.getEventConsumerName();
    String eventPublisherName = config.getEventPublisherName();

    if (nonNull(eventHandlerName) && (nonNull(eventConsumerName) || nonNull(eventPublisherName))) {
      throw new FatalAdapterException(
          AdapterErrorCode.CONFIGURATION_ERROR,
          "Configuration error: Cannot specify both 'eventhandler.name' and separate 'eventconsumer.name' or 'eventpublisher.name'");
    }

    if (nonNull(eventHandlerName)) {
      logger.info("Using combined event handler: {}", Encode.forJava(eventHandlerName));
      return new EventHandlerNames(eventHandlerName, eventHandlerName);
    }

    if (isNull(eventConsumerName)) {
      throw new FatalAdapterException(
          AdapterErrorCode.CONFIGURATION_ERROR,
          "No event consumer configured. Please specify 'eventhandler.name' or 'eventconsumer.name'");
    }

    logger.info(
        "Using separate consumer: {} and publisher: {}",
        Encode.forJava(eventConsumerName),
        eventPublisherName != null ? Encode.forJava(eventPublisherName) : "none");
    return new EventHandlerNames(eventConsumerName, eventPublisherName);
  }

  private EventConsumer createConsumerForAdapter(
      AppConfig config, String adapterName, EventHandlerNames handlerNames)
      throws FatalAdapterException {
    try {
      logger.info("Loading adapter: {}", Encode.forJava(adapterName));

      ConfigAdapter adapter = createAdapter(config, adapterName);
      if (adapter == null) {
        throw new FatalAdapterException(
            AdapterErrorCode.CONFIGURATION_ERROR,
            "Adapter '" + adapterName + "' not found via ServiceLoader");
      }

      List<String> topics = adapter.getSubscribedTopics();
      if (topics.isEmpty()) {
        logger.warn("Adapter {} has no subscribed topics, skipping", Encode.forJava(adapterName));
        return null;
      }

      logger.info(
          "Adapter {} subscribes to {} topic(s): {}",
          adapter.getClass().getSimpleName(),
          topics.size(),
          Encode.forJava(String.valueOf(topics)));

      configurePublisher(config, adapter, handlerNames.publisherName());

      EventConsumer consumer = createConsumer(config, handlerNames.consumerName(), adapter);
      if (isNull(consumer)) {
        throw new FatalAdapterException(
            AdapterErrorCode.CONFIGURATION_ERROR,
            "Event consumer '" + handlerNames.consumerName() + "' not found via ServiceLoader");
      }

      logger.info("Successfully created consumer for adapter: {}", Encode.forJava(adapterName));
      return consumer;
    } catch (FatalAdapterException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Failed to create consumer for adapter: {}", Encode.forJava(adapterName), e);
      throw new FatalAdapterException(
          AdapterErrorCode.CONFIGURATION_ERROR,
          e,
          "Failed to create consumer for adapter: " + adapterName);
    }
  }

  private void configurePublisher(AppConfig config, ConfigAdapter adapter, String publisherName)
      throws FatalAdapterException {
    if (isNull(publisherName)) {
      return;
    }

    EventPublisher publisher = createPublisher(config, publisherName, adapter);
    if (nonNull(publisher)) {
      adapter.setEventPublisher(publisher);
    } else {
      logger.warn(
          "Event publisher '{}' not found via ServiceLoader, continuing without publisher",
          Encode.forJava(publisherName));
    }
  }

  private record EventHandlerNames(String consumerName, String publisherName) {}

  private EventConsumer createConsumer(
      ApplicationConfig config, String consumerName, ConfigAdapter adapter)
      throws FatalAdapterException {
    Optional<EventConsumer> consumerOpt =
        getInstanceByFilter(EventConsumer.class, ec -> Objects.equals(consumerName, ec.getName()));
    if (consumerOpt.isPresent()) {
      EventConsumer consumer = consumerOpt.get();
      consumer.initialize(config, adapter);
      return consumer;
    }
    return null;
  }

  private EventPublisher createPublisher(
      ApplicationConfig config, String publisherName, ConfigAdapter adapter)
      throws FatalAdapterException {
    Optional<EventPublisher> publisherOpt =
        getInstanceByFilter(
            EventPublisher.class, ep -> Objects.equals(publisherName, ep.getName()));
    if (publisherOpt.isPresent()) {
      EventPublisher ep = publisherOpt.get();
      ep.initialize(config, adapter);
      return ep;
    }
    return null;
  }

  private KafkaSagaCommandConsumer createSagaCommandConsumer(AppConfig config) {
    Map<String, SagaCommandHandler> handlers = discoverSagaHandlers(config);
    if (handlers.isEmpty()) {
      logger.info("No SagaCommandHandler implementations found, saga consumer disabled");
      return null;
    }

    String bootstrapServers = config.getProperty("kafka.bootstrap.servers", "localhost:9092");
    String groupId = config.getProperty("kafka.group.id", "config-adapter-group");

    KafkaConsumer<String, byte[]> consumer =
        new KafkaConsumer<>(createSagaConsumerProperties(bootstrapServers, groupId + "-saga"));
    KafkaProducer<String, byte[]> producer =
        new KafkaProducer<>(createSagaProducerProperties(bootstrapServers));

    logger.info(
        "SagaCommandConsumer created with {} handler(s): {}",
        handlers.size(),
        Encode.forJava(String.valueOf(handlers.keySet())));

    return new KafkaSagaCommandConsumer(consumer, producer, handlers);
  }

  private Map<String, SagaCommandHandler> discoverSagaHandlers(AdapterConfig config) {
    Map<String, SagaCommandHandler> handlers = new HashMap<>();
    ServiceLoader<SagaCommandHandler> loader = ServiceLoader.load(SagaCommandHandler.class);

    for (SagaCommandHandler handler : loader) {
      try {
        handler.initialize(config);
        handlers.put(handler.adapter(), handler);
        logger.info(
            "Discovered SagaCommandHandler: {} ({})",
            Encode.forJava(handler.adapter()),
            handler.getClass().getSimpleName());
      } catch (Exception e) {
        logger.error(
            "Failed to initialize SagaCommandHandler {}: {}",
            handler.getClass().getSimpleName(),
            Encode.forJava(e.getMessage()),
            e);
      }
    }
    return handlers;
  }

  private Properties createSagaConsumerProperties(String bootstrapServers, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    return props;
  }

  private Properties createSagaProducerProperties(String bootstrapServers) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, 3);
    return props;
  }

  private ConfigAdapter createAdapter(AdapterConfig config, String adapterName) {
    Optional<ConfigAdapter> configAdapterOpt =
        getInstanceByFilter(ConfigAdapter.class, ca -> Objects.equals(adapterName, ca.getName()));
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
