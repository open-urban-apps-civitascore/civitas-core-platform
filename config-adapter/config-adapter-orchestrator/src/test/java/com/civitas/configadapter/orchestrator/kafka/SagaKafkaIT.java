/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.orchestrator.kafka;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.model.saga.SagaContext;
import com.civitas.configadapter.model.saga.SagaType;
import com.civitas.configadapter.orchestrator.engine.SagaAction;
import com.civitas.configadapter.orchestrator.engine.SagaActionDispatcher;
import com.civitas.configadapter.orchestrator.engine.SagaEngine;
import com.civitas.configadapter.orchestrator.engine.SagaStateMachine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka integration tests for the Saga Orchestrator using Testcontainers.
 *
 * <p>These tests verify that saga state is correctly persisted and recovered via Kafka, and that
 * the action dispatcher correctly publishes messages to the expected topics.
 */
@Testcontainers
class SagaKafkaIT {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final String DATASET_ID = "ds-it-001";
  private static final long PUBLISH_TIMEOUT_MS = 5000L;

  private static final Predicate<Map<String, Object>> HAS_PIPELINES =
      payload -> {
        Object pipelines = payload.get("dataPipelines");
        return pipelines instanceof List<?> list && !list.isEmpty();
      };

  @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

  private ObjectMapper objectMapper;
  private KafkaProducer<String, byte[]> producer;

  private static volatile boolean topicsCreated = false;

  @BeforeEach
  void setUp() throws InterruptedException, ExecutionException, TimeoutException {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // Create required topics (once per container lifecycle)
    if (!topicsCreated) {
      try (AdminClient admin = createAdminClient()) {
        List<NewTopic> topics =
            List.of(
                new NewTopic(KafkaSagaStateStore.STATE_TOPIC, 1, (short) 1),
                new NewTopic(KafkaSagaActionDispatcher.SAGA_RESULT_TOPIC, 1, (short) 1),
                new NewTopic(KafkaSagaActionDispatcher.MANUAL_INTERVENTION_TOPIC, 1, (short) 1),
                new NewTopic("core.civitas.dataset.frost.execute", 1, (short) 1),
                new NewTopic("core.civitas.dataset.apisix.execute", 1, (short) 1),
                new NewTopic("core.civitas.dataset.redpanda.execute", 1, (short) 1),
                new NewTopic("core.civitas.dataset.frost.compensate", 1, (short) 1),
                new NewTopic("core.civitas.dataset.apisix.compensate", 1, (short) 1),
                new NewTopic("core.civitas.dataset.frost.result", 1, (short) 1),
                new NewTopic("core.civitas.dataset.apisix.result", 1, (short) 1),
                new NewTopic("core.civitas.dataset.redpanda.result", 1, (short) 1));
        admin.createTopics(topics).all().get(10, TimeUnit.SECONDS);
      }
      topicsCreated = true;
    }

    producer = createProducer();
  }

  @AfterEach
  void tearDown() {
    if (producer != null) {
      producer.close();
    }
  }

  @Test
  @DisplayName("State store persists and recovers saga state via Kafka compacted topic")
  void stateStore_persistAndRecover_shouldContainSagaAfterRecovery() {
    // 1. Create state store and persist a saga
    var stateStore = new KafkaSagaStateStore(producer, PUBLISH_TIMEOUT_MS);
    var stateMachine = new SagaStateMachine(HAS_PIPELINES);

    var engine = new SagaEngine(stateMachine, stateStore, new NoOpDispatcher());
    Optional<SagaContext> ctx =
        engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithPipelines());
    assertTrue(ctx.isPresent());
    String sagaId = ctx.get().sagaId();

    // Verify in-memory state
    assertTrue(stateStore.findById(sagaId).isPresent());

    // 2. Create a new state store from recovery (simulating crash + restart)
    try (KafkaConsumer<String, byte[]> recoveryConsumer =
        createByteConsumer("recovery-" + System.nanoTime())) {
      var recovery = new KafkaSagaStateRecovery(recoveryConsumer);
      Map<String, SagaContext> recovered = recovery.recover();

      assertTrue(recovered.containsKey(sagaId), "Recovered sagas should contain our sagaId");
      assertEquals(DATASET_ID, recovered.get(sagaId).datasetId());
    }
  }

  @Test
  @DisplayName("Action dispatcher publishes execute command to correct adapter topic")
  void dispatcher_executeStep_shouldPublishToAdapterTopic() {
    var dispatcher = new KafkaSagaActionDispatcher(producer, PUBLISH_TIMEOUT_MS);

    try (KafkaConsumer<String, byte[]> consumer =
        createByteConsumer("frost-listener-" + System.nanoTime())) {
      consumer.subscribe(List.of("core.civitas.dataset.frost.execute"));

      // Wait for partition assignment
      consumer.poll(Duration.ofMillis(500));

      // Dispatch an ExecuteStep action
      var payload = new HashMap<String, Object>();
      payload.put("sagaId", "saga-123");
      payload.put("datasetId", DATASET_ID);
      payload.put("datasetName", "Test Dataset");

      var action =
          new SagaAction.ExecuteStep(
              "create-project",
              "frost",
              "CREATE_PROJECT",
              "core.civitas.dataset.frost.execute",
              Map.copyOf(payload));
      dispatcher.dispatch(action);

      // Verify message arrives on topic
      await()
          .atMost(10, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                assertTrue(
                    records.count() > 0, "Expected at least one message on frost execute topic");

                var record = records.iterator().next();
                Map<String, Object> message = objectMapper.readValue(record.value(), MAP_TYPE);
                assertEquals("EXECUTE_STEP", message.get("type"));
                assertEquals("create-project", message.get("stepId"));
                assertEquals("frost", message.get("adapter"));
                assertEquals("CREATE_PROJECT", message.get("operation"));
              });
    }
  }

  @Test
  @DisplayName("Action dispatcher publishes CompleteSaga to saga result topic")
  void dispatcher_completeSaga_shouldPublishToResultTopic() {
    var dispatcher = new KafkaSagaActionDispatcher(producer, PUBLISH_TIMEOUT_MS);

    try (KafkaConsumer<String, byte[]> consumer =
        createByteConsumer("result-listener-" + System.nanoTime())) {
      consumer.subscribe(List.of(KafkaSagaActionDispatcher.SAGA_RESULT_TOPIC));
      consumer.poll(Duration.ofMillis(500));

      var action =
          new SagaAction.CompleteSaga(
              "saga-456", Map.of("datasetId", DATASET_ID, "projectId", "proj-789"));
      dispatcher.dispatch(action);

      await()
          .atMost(10, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                assertTrue(records.count() > 0);

                var record = records.iterator().next();
                assertEquals("saga-456", record.key());
                Map<String, Object> message = objectMapper.readValue(record.value(), MAP_TYPE);
                assertEquals("SAGA_COMPLETED", message.get("type"));
                assertEquals("saga-456", message.get("sagaId"));
                assertEquals("COMPLETED", message.get("status"));
              });
    }
  }

  @Test
  @DisplayName("Result consumer routes adapter responses to SagaEngine")
  void resultConsumer_stepCompleted_shouldAdvanceSagaToNextStep()
      throws InterruptedException, ExecutionException, TimeoutException, JsonProcessingException {
    // Set up engine with in-memory store + collecting dispatcher
    var stateStore = new KafkaSagaStateStore(producer, PUBLISH_TIMEOUT_MS);
    var dispatched = new ArrayList<SagaAction>();
    SagaActionDispatcher collectingDispatcher = dispatched::add;
    var stateMachine = new SagaStateMachine(HAS_PIPELINES);
    var engine = new SagaEngine(stateMachine, stateStore, collectingDispatcher);

    // Start a saga
    Optional<SagaContext> ctx =
        engine.startSaga(SagaType.DATASET_CREATE, DATASET_ID, triggerWithPipelines());
    String sagaId = ctx.get().sagaId();

    // Start result consumer
    KafkaConsumer<String, byte[]> resultConsumerKafka =
        createByteConsumer("saga-result-" + System.nanoTime());
    var resultConsumer = new SagaResultConsumer(resultConsumerKafka, engine);
    resultConsumer.start();

    try {
      // Simulate FROST adapter responding with STEP_COMPLETED on the result topic
      var adapterResponse = new HashMap<String, Object>();
      adapterResponse.put("type", "STEP_COMPLETED");
      adapterResponse.put("sagaId", sagaId);
      adapterResponse.put("stepId", "create-project");
      adapterResponse.put(
          "resultData", Map.of("projectId", "proj-it-123", "baseUrl", "http://frost/proj-it-123"));
      adapterResponse.put("compensationData", Map.of("projectId", "proj-it-123"));

      byte[] json = objectMapper.writeValueAsBytes(adapterResponse);
      producer
          .send(new ProducerRecord<>("core.civitas.dataset.frost.result", sagaId, json))
          .get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      // Wait for engine to process and advance to next step (create-route)
      await()
          .atMost(15, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                SagaAction.ExecuteStep lastExec =
                    dispatched.stream()
                        .filter(a -> a instanceof SagaAction.ExecuteStep)
                        .map(a -> (SagaAction.ExecuteStep) a)
                        .reduce((first, second) -> second)
                        .orElse(null);
                assertNotNull(lastExec, "Expected ExecuteStep action for create-route");
                assertEquals("create-route", lastExec.stepId());
              });
    } finally {
      resultConsumer.stop();
    }
  }

  @Test
  @DisplayName("State store tombstone removes saga on recovery")
  void stateStore_tombstone_shouldExcludeSagaFromRecovery() {
    var stateStore = new KafkaSagaStateStore(producer, PUBLISH_TIMEOUT_MS);
    var stateMachine = new SagaStateMachine(HAS_PIPELINES);
    var engine = new SagaEngine(stateMachine, stateStore, new NoOpDispatcher());

    // Start and complete a saga (the engine removes terminal sagas from the store)
    String tombstoneDatasetId = "ds-tombstone-" + System.nanoTime();
    Optional<SagaContext> ctx =
        engine.startSaga(
            SagaType.DATASET_CREATE,
            tombstoneDatasetId,
            Map.of(
                "id",
                tombstoneDatasetId,
                "name",
                "Test",
                "openDataAccess",
                true,
                "dataPipelines",
                List.of()));
    String sagaId = ctx.get().sagaId();

    engine.handleStepCompleted(sagaId, "create-project", Map.of("projectId", "p1"), Map.of());
    engine.handleStepCompleted(sagaId, "create-route", Map.of("routeId", "r1"), Map.of());

    // Saga is now completed and tombstoned

    // Recovery should NOT contain this specific saga
    try (KafkaConsumer<String, byte[]> recoveryConsumer =
        createByteConsumer("recovery-tombstone-" + System.nanoTime())) {
      var recovery = new KafkaSagaStateRecovery(recoveryConsumer);
      Map<String, SagaContext> recovered = recovery.recover();
      assertFalse(
          recovered.containsKey(sagaId), "Completed (tombstoned) saga should not be recovered");
    }
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  private Map<String, Object> triggerWithPipelines() {
    return Map.of(
        "id",
        DATASET_ID,
        "name",
        "IT Test Dataset",
        "openDataAccess",
        true,
        "dataPipelines",
        List.of(Map.of("id", "pl-001", "action", "ADD")));
  }

  private AdminClient createAdminClient() {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    return AdminClient.create(props);
  }

  private KafkaProducer<String, byte[]> createProducer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaProducer<>(props);
  }

  private KafkaConsumer<String, byte[]> createByteConsumer(String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(props);
  }

  /** No-op dispatcher that swallows all actions. */
  private static class NoOpDispatcher implements SagaActionDispatcher {
    @Override
    public void dispatch(SagaAction action) {
      // no-op
    }
  }
}
