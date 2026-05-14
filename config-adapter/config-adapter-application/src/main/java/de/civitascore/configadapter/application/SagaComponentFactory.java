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

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.flowable.common.FlowableSagaOrchestrator;
import de.civitascore.configadapter.orchestrator.DatasetSagaOrchestrator;
import de.civitascore.configadapter.orchestrator.kafka.SagaTriggerConsumer;
import de.civitascore.event.handler.kafka.KafkaSagaCommandConsumer;
import java.util.HashMap;
import java.util.Map;
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
 * Creates and initializes saga orchestration components: the command consumer, orchestrator, and
 * trigger consumer.
 */
class SagaComponentFactory {

  private static final Logger logger = LoggerFactory.getLogger(SagaComponentFactory.class);

  private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";
  private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

  SagaComponents create(AppConfig config) {
    String engine = config.getProperty("orchestrator.engine", "flowable");

    if ("flowable".equals(engine)) {
      logger.info("Using Flowable saga orchestrator (orchestrator.engine=flowable)");
      return createFlowableComponents(config);
    }

    logger.info("Using custom saga orchestrator (orchestrator.engine=custom)");
    String bootstrapServers =
        config.getProperty(KAFKA_BOOTSTRAP_SERVERS, DEFAULT_BOOTSTRAP_SERVERS);
    Optional<KafkaSagaCommandConsumer> commandConsumer =
        Optional.ofNullable(createSagaCommandConsumer(config, bootstrapServers));
    Optional<OrchestratorPair> orchestratorPair = initializeSagaOrchestrator(bootstrapServers);
    return new SagaComponents(commandConsumer, orchestratorPair);
  }

  private SagaComponents createFlowableComponents(AppConfig config) {
    Map<String, SagaCommandHandler> handlers = discoverSagaHandlers(config);
    FlowableSagaOrchestrator flowable = new FlowableSagaOrchestrator(config, handlers);
    try {
      flowable.initialize();
    } catch (Exception e) {
      try {
        flowable.close();
      } catch (Exception closeEx) {
        e.addSuppressed(closeEx);
      }
      throw new IllegalStateException(
          "Flowable orchestrator initialization failed (orchestrator.engine=flowable). "
              + "Check flowable.jdbc.* configuration.",
          e);
    }
    logger.info("FlowableSagaOrchestrator initialized successfully");
    return SagaComponents.flowable(flowable);
  }

  private Optional<OrchestratorPair> initializeSagaOrchestrator(String bootstrapServers) {
    try {
      DatasetSagaOrchestrator orchestrator = new DatasetSagaOrchestrator();
      orchestrator.initialize(bootstrapServers);

      KafkaConsumer<String, byte[]> triggerConsumer =
          new KafkaConsumer<>(
              createSagaConsumerProperties(bootstrapServers, "saga-orchestrator-trigger"));
      SagaTriggerConsumer sagaTriggerConsumer =
          new SagaTriggerConsumer(triggerConsumer, orchestrator);

      logger.info("DatasetSagaOrchestrator initialized successfully");
      return Optional.of(new OrchestratorPair(orchestrator, sagaTriggerConsumer));
    } catch (Exception e) {
      logger.error("Failed to initialize saga orchestrator: {}", e.getMessage(), e);
      return Optional.empty();
    }
  }

  private KafkaSagaCommandConsumer createSagaCommandConsumer(
      AppConfig config, String bootstrapServers) {
    Map<String, SagaCommandHandler> handlers = discoverSagaHandlers(config);
    if (handlers.isEmpty()) {
      logger.info("No SagaCommandHandler implementations found, saga consumer disabled");
      return null;
    }

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
    props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    return props;
  }
}
