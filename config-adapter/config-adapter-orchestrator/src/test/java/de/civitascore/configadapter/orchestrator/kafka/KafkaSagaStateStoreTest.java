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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaStatus;
import de.civitascore.configadapter.model.saga.SagaStep;
import de.civitascore.configadapter.model.saga.SagaType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
class KafkaSagaStateStoreTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  private KafkaProducer<String, byte[]> producer;
  private KafkaSagaStateStore store;

  private static final RecordMetadata METADATA =
      new RecordMetadata(new TopicPartition("t", 0), 0, 0, 0, 0, 0);

  @BeforeEach
  void setUp() {
    producer = mock(KafkaProducer.class);
    when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(METADATA));
    store = new KafkaSagaStateStore(producer, 5000L);
  }

  private SagaContext createContext(String sagaId, String datasetId, SagaStatus status) {
    return new SagaContext(
        sagaId,
        SagaType.DATASET_CREATE,
        datasetId,
        "create-project",
        status,
        List.of(SagaStep.pending("create-project", "frost", "CREATE_PROJECT")),
        null,
        Map.of(),
        NOW,
        NOW);
  }

  @Nested
  @DisplayName("save")
  class Save {

    @Test
    @DisplayName("publishes to state topic and stores in memory")
    void shouldPublishAndStore() {
      SagaContext ctx = createContext("saga-1", "ds-1", SagaStatus.EXECUTING);

      store.save(ctx);

      ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
          ArgumentCaptor.forClass(ProducerRecord.class);
      verify(producer).send(captor.capture());
      assertEquals(KafkaSagaStateStore.STATE_TOPIC, captor.getValue().topic());
      assertEquals("saga-1", captor.getValue().key());
      assertTrue(store.findById("saga-1").isPresent());
    }

    @Test
    @DisplayName("throws IllegalStateException on Kafka failure")
    void shouldThrowOnKafkaFailure() {
      when(producer.send(any()))
          .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

      SagaContext ctx = createContext("saga-1", "ds-1", SagaStatus.EXECUTING);

      assertThrows(IllegalStateException.class, () -> store.save(ctx));
    }
  }

  @Nested
  @DisplayName("remove")
  class Remove {

    @Test
    @DisplayName("sends tombstone and removes from memory")
    void shouldSendTombstoneAndRemove() {
      SagaContext ctx = createContext("saga-1", "ds-1", SagaStatus.EXECUTING);
      store.save(ctx);

      store.remove("saga-1");

      ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
          ArgumentCaptor.forClass(ProducerRecord.class);
      // save + remove = 2 calls
      verify(producer, org.mockito.Mockito.times(2)).send(captor.capture());
      ProducerRecord<String, byte[]> tombstone = captor.getValue();
      assertEquals("saga-1", tombstone.key());
      assertFalse(store.findById("saga-1").isPresent());
    }

    @Test
    @DisplayName("removes from memory even on Kafka failure")
    void shouldRemoveFromMemoryOnKafkaFailure() {
      SagaContext ctx = createContext("saga-1", "ds-1", SagaStatus.EXECUTING);
      store.save(ctx);

      // Reset mock to fail on next call
      when(producer.send(any()))
          .thenReturn(
              CompletableFuture.failedFuture(new ExecutionException(new RuntimeException("fail"))));

      store.remove("saga-1");

      assertFalse(store.findById("saga-1").isPresent());
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    @DisplayName("returns empty for unknown sagaId")
    void shouldReturnEmptyForUnknown() {
      assertFalse(store.findById("unknown").isPresent());
    }

    @Test
    @DisplayName("returns context after save")
    void shouldReturnSavedContext() {
      SagaContext ctx = createContext("saga-1", "ds-1", SagaStatus.EXECUTING);
      store.save(ctx);

      assertTrue(store.findById("saga-1").isPresent());
      assertEquals("saga-1", store.findById("saga-1").get().sagaId());
    }
  }

  @Nested
  @DisplayName("findActiveSagas")
  class FindActiveSagas {

    @Test
    @DisplayName("returns only non-terminal sagas")
    void shouldReturnOnlyActive() {
      store.save(createContext("saga-1", "ds-1", SagaStatus.EXECUTING));
      store.save(createContext("saga-2", "ds-2", SagaStatus.COMPLETED));
      store.save(createContext("saga-3", "ds-3", SagaStatus.COMPENSATING));

      Map<String, SagaContext> active = store.findActiveSagas();

      assertEquals(2, active.size());
      assertTrue(active.containsKey("saga-1"));
      assertTrue(active.containsKey("saga-3"));
      assertFalse(active.containsKey("saga-2"));
    }
  }

  @Nested
  @DisplayName("existsForDataset")
  class ExistsForDataset {

    @Test
    @DisplayName("returns true for active saga with matching datasetId")
    void shouldReturnTrueForActiveSaga() {
      store.save(createContext("saga-1", "ds-1", SagaStatus.EXECUTING));

      assertTrue(store.existsForDataset("ds-1"));
    }

    @Test
    @DisplayName("returns false for terminal saga")
    void shouldReturnFalseForTerminalSaga() {
      store.save(createContext("saga-1", "ds-1", SagaStatus.COMPLETED));

      assertFalse(store.existsForDataset("ds-1"));
    }

    @Test
    @DisplayName("returns false for unknown datasetId")
    void shouldReturnFalseForUnknown() {
      assertFalse(store.existsForDataset("ds-unknown"));
    }
  }

  @Nested
  @DisplayName("size")
  class Size {

    @Test
    @DisplayName("returns number of entries including terminal")
    void shouldReturnTotalSize() {
      store.save(createContext("saga-1", "ds-1", SagaStatus.EXECUTING));
      store.save(createContext("saga-2", "ds-2", SagaStatus.COMPLETED));

      assertEquals(2, store.size());
    }
  }

  @Nested
  @DisplayName("constructor with recovered state")
  class RecoveredState {

    @Test
    @DisplayName("pre-populates state from recovery map")
    void shouldPrePopulate() {
      SagaContext ctx = createContext("saga-1", "ds-1", SagaStatus.EXECUTING);
      KafkaSagaStateStore recovered =
          new KafkaSagaStateStore(producer, 5000L, Map.of("saga-1", ctx));

      assertTrue(recovered.findById("saga-1").isPresent());
      assertEquals(1, recovered.size());
    }
  }
}
