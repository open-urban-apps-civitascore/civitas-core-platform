/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator;

import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaType;
import de.civitascore.configadapter.orchestrator.engine.SagaActionDispatcher;
import de.civitascore.configadapter.orchestrator.engine.SagaEngine;
import de.civitascore.configadapter.orchestrator.engine.SagaStateMachine;
import de.civitascore.configadapter.orchestrator.engine.SagaStateStore;
import de.civitascore.configadapter.orchestrator.kafka.KafkaSagaActionDispatcher;
import de.civitascore.configadapter.orchestrator.kafka.KafkaSagaStateRecovery;
import de.civitascore.configadapter.orchestrator.kafka.KafkaSagaStateStore;
import de.civitascore.configadapter.orchestrator.kafka.SagaResultConsumer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Saga Orchestrator. Wires together the engine, state store, dispatcher,
 * and Kafka consumers for the Dataset provisioning saga.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>{@link #initialize} — creates Kafka clients, recovers state, builds engine
 *   <li>{@link #start} — starts the result consumer thread
 *   <li>{@link #startSaga} — triggers a new saga (called by the portal-backend trigger consumer)
 *   <li>{@link #stop} — shuts down consumers and producers
 * </ol>
 */
public class DatasetSagaOrchestrator {

  private static final Logger LOG = LoggerFactory.getLogger(DatasetSagaOrchestrator.class);

  private static final long DEFAULT_PUBLISH_TIMEOUT_MS = 5000L;
  private static final int PRODUCER_RETRIES = 3;

  private static final Predicate<Map<String, Object>> HAS_PIPELINES =
      payload -> {
        Object pipelines = payload.get("dataPipelines");
        return pipelines instanceof List<?> list && !list.isEmpty();
      };

  private SagaEngine engine;
  private SagaResultConsumer resultConsumer;
  private KafkaProducer<String, byte[]> producer;

  /**
   * Initialize the orchestrator with Kafka connectivity.
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param publishTimeoutMs timeout for synchronous Kafka produce operations
   */
  public void initialize(String bootstrapServers, long publishTimeoutMs) {
    LOG.info("Initializing DatasetSagaOrchestrator with bootstrap.servers={}", bootstrapServers);

    // 1. Create shared Kafka producer
    producer = createProducer(bootstrapServers);

    // 2. Recover state from Kafka compacted topic
    Map<String, SagaContext> recoveredState;
    try (KafkaConsumer<String, byte[]> recoveryConsumer =
        createRecoveryConsumer(bootstrapServers)) {
      var recovery = new KafkaSagaStateRecovery(recoveryConsumer);
      recoveredState = recovery.recover();
    }

    // 3. Build engine components
    SagaStateStore stateStore = new KafkaSagaStateStore(producer, publishTimeoutMs, recoveredState);
    SagaActionDispatcher dispatcher = new KafkaSagaActionDispatcher(producer, publishTimeoutMs);
    SagaStateMachine stateMachine = new SagaStateMachine(HAS_PIPELINES);
    engine = new SagaEngine(stateMachine, stateStore, dispatcher);

    // 4. Log recovery
    int recovered = engine.recoverActiveSagas();
    LOG.info("Orchestrator initialized. {} active sagas recovered.", recovered);

    // 5. Create result consumer (started via start())
    KafkaConsumer<String, byte[]> resultKafkaConsumer = createResultConsumer(bootstrapServers);
    resultConsumer = new SagaResultConsumer(resultKafkaConsumer, engine);
  }

  /** Convenience overload using defaults. */
  public void initialize(String bootstrapServers) {
    initialize(bootstrapServers, DEFAULT_PUBLISH_TIMEOUT_MS);
  }

  /** Start the result consumer to listen for adapter responses. */
  public void start() {
    if (resultConsumer == null) {
      throw new IllegalStateException("Orchestrator not initialized. Call initialize() first.");
    }
    resultConsumer.start();
    LOG.info("DatasetSagaOrchestrator started");
  }

  /**
   * Start a new saga.
   *
   * @param sagaType the workflow type
   * @param datasetId the dataset this saga operates on
   * @param triggerPayload the trigger event payload
   * @return the created SagaContext, or empty if a saga is already active for this dataset
   */
  public Optional<SagaContext> startSaga(
      SagaType sagaType, String datasetId, Map<String, Object> triggerPayload) {
    if (engine == null) {
      throw new IllegalStateException("Orchestrator not initialized. Call initialize() first.");
    }
    return engine.startSaga(sagaType, datasetId, triggerPayload);
  }

  /** Access the engine directly (for testing or advanced use cases). */
  public SagaEngine getEngine() {
    return engine;
  }

  /** Shut down the orchestrator gracefully. */
  public void stop() {
    LOG.info("Stopping DatasetSagaOrchestrator");
    if (resultConsumer != null) {
      resultConsumer.stop();
    }
    if (producer != null) {
      producer.flush();
      producer.close();
    }
    LOG.info("DatasetSagaOrchestrator stopped");
  }

  // ─── Kafka Client Factories ──────────────────────────────────────────────────

  private KafkaProducer<String, byte[]> createProducer(String bootstrapServers) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, PRODUCER_RETRIES);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    return new KafkaProducer<>(props);
  }

  private KafkaConsumer<String, byte[]> createRecoveryConsumer(String bootstrapServers) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "saga-orchestrator-recovery-" + System.nanoTime());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(props);
  }

  private KafkaConsumer<String, byte[]> createResultConsumer(String bootstrapServers) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "saga-orchestrator");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(props);
  }
}
