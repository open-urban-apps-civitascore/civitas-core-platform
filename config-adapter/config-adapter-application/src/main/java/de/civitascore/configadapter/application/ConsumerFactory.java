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

import static de.civitascore.configadapter.util.ServiceLoaderUtils.getInstanceByFilter;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.messaging.EventConsumer;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.AdapterErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and wires event consumers, publishers, and config adapters. Handles ServiceLoader
 * discovery, configuration resolution, and adapter initialization.
 */
class ConsumerFactory {

  private static final Logger logger = LoggerFactory.getLogger(ConsumerFactory.class);

  List<EventConsumer> createAll(AppConfig config) throws FatalAdapterException {
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

      EventConsumer consumer = createConsumer(config, handlerNames.consumerName(), adapter);

      if (!Objects.equals(handlerNames.consumerName(), handlerNames.publisherName())) {
        configurePublisher(config, adapter, handlerNames.publisherName());
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
          "Failed to create consumer for adapter: " + Encode.forJava(adapterName));
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

  private EventConsumer createConsumer(
      ApplicationConfig config, String consumerName, ConfigAdapter adapter)
      throws FatalAdapterException {
    EventConsumer consumer =
        getInstanceByFilter(EventConsumer.class, ec -> Objects.equals(consumerName, ec.getName()))
            .orElseThrow(
                () ->
                    new FatalAdapterException(
                        AdapterErrorCode.CONFIGURATION_ERROR,
                        "Event consumer '"
                            + Encode.forJava(consumerName)
                            + "' not found via ServiceLoader"));
    consumer.initialize(config, adapter);
    return consumer;
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

  private ConfigAdapter createAdapter(AdapterConfig config, String adapterName)
      throws FatalAdapterException {
    ConfigAdapter adapter =
        getInstanceByFilter(ConfigAdapter.class, ca -> Objects.equals(adapterName, ca.getName()))
            .orElseThrow(
                () ->
                    new FatalAdapterException(
                        AdapterErrorCode.CONFIGURATION_ERROR,
                        "Adapter '"
                            + Encode.forJava(adapterName)
                            + "' not found via ServiceLoader"));
    adapter.initialize(config);
    return adapter;
  }

  private record EventHandlerNames(String consumerName, String publisherName) {}
}
