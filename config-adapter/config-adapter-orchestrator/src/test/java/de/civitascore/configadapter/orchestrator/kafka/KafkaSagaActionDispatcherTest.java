/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaStatus;
import de.civitascore.configadapter.model.saga.SagaStep;
import de.civitascore.configadapter.model.saga.SagaType;
import de.civitascore.configadapter.orchestrator.engine.SagaAction;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class KafkaSagaActionDispatcherTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private KafkaProducer<String, byte[]> producer;
  private KafkaSagaActionDispatcher dispatcher;
  private ObjectMapper objectMapper;

  private static final RecordMetadata METADATA =
      new RecordMetadata(new TopicPartition("t", 0), 0, 0, 0, 0, 0);

  @BeforeEach
  void setUp() {
    producer = mock(KafkaProducer.class);
    when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(METADATA));
    dispatcher = new KafkaSagaActionDispatcher(producer, 5000L);
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  private Map<String, Object> captureMessage() throws Exception {
    ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());
    return objectMapper.readValue(captor.getValue().value(), MAP_TYPE);
  }

  private ProducerRecord<String, byte[]> captureRecord() {
    ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());
    return captor.getValue();
  }

  @Nested
  @DisplayName("ExecuteStep")
  class ExecuteStepDispatch {

    @Test
    @DisplayName("publishes EXECUTE_STEP to adapter topic with sagaId as key")
    void shouldPublishExecuteStep() throws Exception {
      var action =
          new SagaAction.ExecuteStep(
              "create-project",
              "frost",
              "CREATE_PROJECT",
              "frost.execute",
              Map.of("sagaId", "saga-1", "datasetId", "ds-1"));

      dispatcher.dispatch(action);

      ProducerRecord<String, byte[]> record = captureRecord();
      assertEquals("frost.execute", record.topic());
      assertEquals("saga-1", record.key());

      Map<String, Object> msg = objectMapper.readValue(record.value(), MAP_TYPE);
      assertEquals("EXECUTE_STEP", msg.get("type"));
      assertEquals("create-project", msg.get("stepId"));
      assertEquals("frost", msg.get("adapter"));
      assertEquals("CREATE_PROJECT", msg.get("operation"));
      assertEquals("saga-1", msg.get("sagaId"));
    }
  }

  @Nested
  @DisplayName("CompensateStep")
  class CompensateStepDispatch {

    @Test
    @DisplayName("publishes COMPENSATE_STEP to adapter topic")
    void shouldPublishCompensateStep() throws Exception {
      var action =
          new SagaAction.CompensateStep(
              "create-project",
              "frost",
              "DELETE_PROJECT",
              "frost.compensate",
              Map.of("sagaId", "saga-1", "projectId", "proj-123"));

      dispatcher.dispatch(action);

      Map<String, Object> msg = captureMessage();
      assertEquals("COMPENSATE_STEP", msg.get("type"));
      assertEquals("create-project", msg.get("stepId"));
      assertEquals("DELETE_PROJECT", msg.get("operation"));
    }
  }

  @Nested
  @DisplayName("CompleteSaga")
  class CompleteSagaDispatch {

    @Test
    @DisplayName("publishes SAGA_COMPLETED to result topic")
    void shouldPublishCompleteSaga() throws Exception {
      var action =
          new SagaAction.CompleteSaga(
              "saga-1", Map.of("projectId", "proj-123", "routeId", "r-456"));

      dispatcher.dispatch(action);

      ProducerRecord<String, byte[]> record = captureRecord();
      assertEquals(KafkaSagaActionDispatcher.SAGA_RESULT_TOPIC, record.topic());
      assertEquals("saga-1", record.key());

      Map<String, Object> msg = objectMapper.readValue(record.value(), MAP_TYPE);
      assertEquals("SAGA_COMPLETED", msg.get("type"));
      assertEquals("saga-1", msg.get("sagaId"));
      assertEquals("COMPLETED", msg.get("status"));
    }
  }

  @Nested
  @DisplayName("FailSaga")
  class FailSagaDispatch {

    @Test
    @DisplayName("publishes SAGA_FAILED with COMPENSATED status when compensated")
    void shouldPublishCompensatedFailure() throws Exception {
      var action =
          new SagaAction.FailSaga(
              "saga-1",
              "create-route",
              "Connection refused",
              true,
              List.of(),
              List.of(new SagaAction.CleanedResource("frost", "proj-123")));

      dispatcher.dispatch(action);

      Map<String, Object> msg = captureMessage();
      assertEquals("SAGA_FAILED", msg.get("type"));
      assertEquals("COMPENSATED", msg.get("status"));
      assertEquals("create-route", msg.get("failedStep"));
      assertEquals(true, msg.get("compensated"));
    }

    @Test
    @DisplayName("publishes SAGA_FAILED with FAILED status when not compensated")
    void shouldPublishUncompensatedFailure() throws Exception {
      var action =
          new SagaAction.FailSaga(
              "saga-1",
              "create-project",
              "Error",
              false,
              List.of(new SagaAction.StaleResource("frost", "proj-123", "cleanup failed")),
              List.of());

      dispatcher.dispatch(action);

      Map<String, Object> msg = captureMessage();
      assertEquals("FAILED", msg.get("status"));
      assertEquals(false, msg.get("compensated"));
    }
  }

  @Nested
  @DisplayName("PublishManualIntervention")
  class ManualInterventionDispatch {

    @Test
    @DisplayName("publishes to manual intervention topic")
    void shouldPublishManualIntervention() throws Exception {
      SagaContext ctx =
          new SagaContext(
              "saga-1",
              SagaType.DATASET_CREATE,
              "ds-1",
              "create-project",
              SagaStatus.COMPENSATION_FAILED,
              List.of(SagaStep.pending("create-project", "frost", "CREATE_PROJECT")),
              null,
              Map.of(),
              NOW,
              NOW);
      var action = new SagaAction.PublishManualIntervention("saga-1", ctx);

      dispatcher.dispatch(action);

      ProducerRecord<String, byte[]> record = captureRecord();
      assertEquals(KafkaSagaActionDispatcher.MANUAL_INTERVENTION_TOPIC, record.topic());
      assertEquals("saga-1", record.key());

      Map<String, Object> msg = objectMapper.readValue(record.value(), MAP_TYPE);
      assertEquals("MANUAL_INTERVENTION_REQUIRED", msg.get("type"));
      assertEquals("saga-1", msg.get("sagaId"));
    }
  }

  @Nested
  @DisplayName("SkipStep")
  class SkipStepDispatch {

    @Test
    @DisplayName("does not publish to Kafka")
    void shouldNotPublish() {
      var action = new SagaAction.SkipStep("deploy-pipelines", "No pipelines configured");

      dispatcher.dispatch(action);

      verify(producer, never()).send(any());
    }
  }

  @Nested
  @DisplayName("PersistState")
  class PersistStateDispatch {

    @Test
    @DisplayName("does not publish to Kafka (handled by SagaEngine)")
    void shouldNotPublish() {
      SagaContext ctx =
          new SagaContext(
              "saga-1",
              SagaType.DATASET_CREATE,
              "ds-1",
              "create-project",
              SagaStatus.EXECUTING,
              List.of(SagaStep.pending("create-project", "frost", "CREATE_PROJECT")),
              null,
              Map.of(),
              NOW,
              NOW);
      var action = new SagaAction.PersistState(ctx);

      dispatcher.dispatch(action);

      verify(producer, never()).send(any());
    }
  }

  @Nested
  @DisplayName("Kafka failure")
  class KafkaFailure {

    @Test
    @DisplayName("throws IllegalStateException on send failure")
    void shouldThrowOnSendFailure() {
      when(producer.send(any()))
          .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

      var action = new SagaAction.CompleteSaga("saga-1", Map.of());

      assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(action));
    }
  }
}
