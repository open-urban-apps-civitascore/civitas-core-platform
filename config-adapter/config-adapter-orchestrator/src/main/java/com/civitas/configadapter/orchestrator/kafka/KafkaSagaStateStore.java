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

import com.civitas.configadapter.model.saga.SagaContext;
import com.civitas.configadapter.orchestrator.engine.SagaStateStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka-backed {@link SagaStateStore} implementation. Writes saga state to a compacted Kafka topic
 * ({@code core.civitas.saga.state}) with sagaId as key.
 *
 * <p>The in-memory map is the read-path; Kafka is the write-ahead log for crash recovery. On
 * startup, {@link KafkaSagaStateRecovery} replays the compacted topic to populate the in-memory
 * map.
 *
 * <p>Write path: synchronous {@code producer.send().get()} to ensure state is persisted before the
 * engine dispatches the next action. Remove path: tombstone (value=null) to trigger compaction.
 */
public class KafkaSagaStateStore implements SagaStateStore {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaSagaStateStore.class);

  static final String STATE_TOPIC = "core.civitas.saga.state";

  private final KafkaProducer<String, byte[]> producer;
  private final ObjectMapper objectMapper;
  private final Map<String, SagaContext> stateMap;
  private final long publishTimeoutMs;

  /**
   * Creates a new KafkaSagaStateStore.
   *
   * @param producer Kafka producer for writing state (key=sagaId, value=JSON bytes)
   * @param publishTimeoutMs timeout for synchronous send operations
   */
  public KafkaSagaStateStore(KafkaProducer<String, byte[]> producer, long publishTimeoutMs) {
    this(producer, publishTimeoutMs, new ConcurrentHashMap<>());
  }

  /**
   * Creates a new KafkaSagaStateStore with a pre-populated state map (used after recovery).
   *
   * @param producer Kafka producer for writing state
   * @param publishTimeoutMs timeout for synchronous send operations
   * @param recoveredState state map populated by {@link KafkaSagaStateRecovery}
   */
  public KafkaSagaStateStore(
      KafkaProducer<String, byte[]> producer,
      long publishTimeoutMs,
      Map<String, SagaContext> recoveredState) {
    this.producer = producer;
    this.publishTimeoutMs = publishTimeoutMs;
    this.stateMap = new ConcurrentHashMap<>(recoveredState);
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Override
  public void save(SagaContext context) {
    try {
      byte[] json = objectMapper.writeValueAsBytes(context);
      var record = new ProducerRecord<>(STATE_TOPIC, context.sagaId(), json);

      producer.send(record).get(publishTimeoutMs, TimeUnit.MILLISECONDS);
      stateMap.put(context.sagaId(), context);

      LOG.debug(
          "Persisted saga state: sagaId={}, status={}",
          Encode.forJava(context.sagaId()),
          context.status());
    } catch (JsonProcessingException e) {
      LOG.error(
          "CRITICAL: Failed to serialize saga state for {}.", Encode.forJava(context.sagaId()), e);
      throw new IllegalStateException("Failed to serialize saga state for " + context.sagaId(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while persisting saga state for " + context.sagaId(), e);
    } catch (ExecutionException | TimeoutException e) {
      LOG.error(
          "CRITICAL: Failed to persist saga state for {}. Saga may be in inconsistent state.",
          Encode.forJava(context.sagaId()),
          e);
      throw new IllegalStateException("Failed to persist saga state for " + context.sagaId(), e);
    }
  }

  @Override
  public void remove(String sagaId) {
    try {
      // Tombstone record (null value) triggers Kafka log compaction cleanup
      var record = new ProducerRecord<String, byte[]>(STATE_TOPIC, sagaId, null);

      producer.send(record).get(publishTimeoutMs, TimeUnit.MILLISECONDS);
      stateMap.remove(sagaId);

      LOG.debug("Removed saga state (tombstone): sagaId={}", Encode.forJava(sagaId));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Interrupted while sending tombstone for saga {}.", Encode.forJava(sagaId), e);
    } catch (ExecutionException | TimeoutException e) {
      LOG.error(
          "Failed to send tombstone for saga {}. State will be cleaned up by compaction eventually.",
          Encode.forJava(sagaId),
          e);
      // Still remove from in-memory map to avoid stale reads
      stateMap.remove(sagaId);
    }
  }

  @Override
  public Optional<SagaContext> findById(String sagaId) {
    return Optional.ofNullable(stateMap.get(sagaId));
  }

  @Override
  public Map<String, SagaContext> findActiveSagas() {
    return stateMap.entrySet().stream()
        .filter(e -> !e.getValue().status().isTerminal())
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public boolean existsForDataset(String datasetId) {
    return stateMap.values().stream()
        .anyMatch(ctx -> datasetId.equals(ctx.datasetId()) && !ctx.status().isTerminal());
  }

  /** Returns the current number of entries in the in-memory state map. */
  public int size() {
    return stateMap.size();
  }
}
