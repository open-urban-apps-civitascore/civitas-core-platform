/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.util;

import java.time.Duration;
import java.util.Iterator;

/**
 * Kafka-free abstraction over a polling consumer, allowing {@link RetryConsumerLoop} to live in
 * {@code config-adapter-api} without any Kafka dependency.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface PollingConsumer<K, V> {

  /** Immutable snapshot of a single consumed record. */
  record Record<K, V>(String topic, int partition, long offset, K key, V value) {}

  /**
   * Polls for new records. When the consumer is paused, returns an empty iterator but still
   * maintains the underlying connection (e.g. Kafka heartbeats).
   */
  Iterator<Record<K, V>> poll(Duration timeout);

  /** Pauses consumption on all assigned partitions. */
  void pause();

  /** Resumes consumption on all assigned partitions. */
  void resume();

  /** Synchronously commits the current offsets. */
  void commitSync();
}
