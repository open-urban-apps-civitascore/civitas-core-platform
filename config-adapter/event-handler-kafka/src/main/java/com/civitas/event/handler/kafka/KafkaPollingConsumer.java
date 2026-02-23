/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.event.handler.kafka;

import com.civitas.configadapter.util.PollingConsumer;
import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

/**
 * Adapts a {@link KafkaConsumer} to the Kafka-free {@link PollingConsumer} interface. Installs a
 * {@link ConsumerRebalanceListener} that invokes a configurable revocation callback to clear stale
 * retry state on partition reassignment.
 *
 * <p><strong>Intentional duplicate:</strong> An identical copy of this class exists in {@code
 * config-adapter-orchestrator} at {@code
 * com.civitas.configadapter.orchestrator.kafka.KafkaPollingConsumer}. The {@code
 * config-adapter-api} module is deliberately Kafka-free, and introducing a shared {@code
 * config-adapter-kafka-common} module is not justified for a single ~90-line adapter class. Changes
 * to this file must be applied to the other copy as well.
 */
final class KafkaPollingConsumer implements PollingConsumer<String, byte[]> {

  private final KafkaConsumer<String, byte[]> delegate;
  private Runnable revocationCallback;

  KafkaPollingConsumer(KafkaConsumer<String, byte[]> delegate) {
    this.delegate = delegate;
  }

  void setRevocationCallback(Runnable callback) {
    this.revocationCallback = callback;
  }

  void subscribe(List<String> topics) {
    delegate.subscribe(
        topics,
        new ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            if (revocationCallback != null) {
              revocationCallback.run();
            }
          }

          @Override
          public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            // no-op
          }
        });
  }

  @Override
  public Iterator<Record<String, byte[]>> poll(Duration timeout) {
    Iterator<ConsumerRecord<String, byte[]>> it = delegate.poll(timeout).iterator();
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public Record<String, byte[]> next() {
        ConsumerRecord<String, byte[]> cr = it.next();
        return new Record<>(cr.topic(), cr.partition(), cr.offset(), cr.key(), cr.value());
      }
    };
  }

  @Override
  public void pause() {
    delegate.pause(delegate.assignment());
  }

  @Override
  public void resume() {
    delegate.resume(delegate.assignment());
  }

  @Override
  public void commitSync() {
    delegate.commitSync();
  }

  KafkaConsumer<String, byte[]> delegate() {
    return delegate;
  }
}
