/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.orchestrator.kafka;

import com.civitas.configadapter.model.saga.SagaContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays the Kafka compacted state topic ({@code core.civitas.saga.state}) on startup to rebuild
 * the in-memory saga state map.
 *
 * <p>Uses a dedicated consumer that reads from the beginning of all partitions, processes all
 * records, and then closes. Tombstone records (null value) remove entries from the map. Terminal
 * saga states are filtered out.
 *
 * <p>This class is designed to be used once at startup, before the {@link KafkaSagaStateStore} is
 * created.
 */
public class KafkaSagaStateRecovery {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaSagaStateRecovery.class);

  private final KafkaConsumer<String, byte[]> consumer;
  private final ObjectMapper objectMapper;

  /**
   * Creates a recovery instance with a dedicated Kafka consumer.
   *
   * @param consumer Kafka consumer configured to read from the state topic (auto.offset.reset =
   *     earliest, enable.auto.commit = false)
   */
  public KafkaSagaStateRecovery(KafkaConsumer<String, byte[]> consumer) {
    this.consumer = consumer;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  private static final int MAX_CONSECUTIVE_EMPTY_POLLS = 3;

  /**
   * Replays the state topic and returns a map of active (non-terminal) sagas.
   *
   * <p>Steps: 1. Assign all partitions of the state topic. 2. Seek to the beginning. 3. Poll until
   * no more records arrive. 4. Return the resulting in-memory map (only non-terminal sagas).
   *
   * @return map of sagaId to SagaContext for all active sagas
   */
  public Map<String, SagaContext> recover() {
    Map<String, SagaContext> stateMap = new ConcurrentHashMap<>();

    try {
      List<TopicPartition> partitions =
          consumer.partitionsFor(KafkaSagaStateStore.STATE_TOPIC).stream()
              .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
              .toList();

      if (partitions.isEmpty()) {
        LOG.info("No partitions found for state topic — nothing to recover");
        return stateMap;
      }

      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      int totalRecords = 0;
      int consecutiveEmptyPolls = 0;

      while (consecutiveEmptyPolls < MAX_CONSECUTIVE_EMPTY_POLLS) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));

        if (records.isEmpty()) {
          consecutiveEmptyPolls++;
          continue;
        }

        consecutiveEmptyPolls = 0;

        for (var record : records) {
          totalRecords++;
          String sagaId = record.key();

          if (record.value() == null) {
            // Tombstone — saga was removed
            stateMap.remove(sagaId);
            continue;
          }

          try {
            SagaContext context = objectMapper.readValue(record.value(), SagaContext.class);

            if (context.status().isTerminal()) {
              // Terminal sagas should have been removed via tombstone, but
              // compaction may not have run yet — skip them
              stateMap.remove(sagaId);
            } else {
              stateMap.put(sagaId, context);
            }
          } catch (IOException e) {
            LOG.warn(
                "Failed to deserialize saga state for key {}, skipping", Encode.forJava(sagaId), e);
          }
        }
      }

      LOG.info(
          "Recovery complete: processed {} records, {} active sagas recovered",
          totalRecords,
          stateMap.size());

    } catch (RuntimeException e) {
      LOG.error("Failed to recover saga state from Kafka", e);
    } finally {
      consumer.close();
    }

    return Collections.unmodifiableMap(stateMap);
  }
}
