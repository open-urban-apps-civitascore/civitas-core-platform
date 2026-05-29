/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common.kafka;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Test helpers for driving {@link FlowableTriggerConsumer} without real Kafka record metadata.
 * Lives in test scope so production code carries no test-only entry points. Uses a monotonically
 * increasing offset so each synthesized record has a unique business key (topic:partition:offset).
 */
final class TriggerTestSupport {

  private static final AtomicLong OFFSET = new AtomicLong();

  private TriggerTestSupport() {}

  /** Feeds a trigger payload to the consumer using a unique, monotonically increasing offset. */
  static void processTrigger(FlowableTriggerConsumer consumer, byte[] value) throws IOException {
    consumer.processTriggerRecord(
        new ConsumerRecord<>(
            FlowableTriggerConsumer.TRIGGER_TOPIC, 0, OFFSET.getAndIncrement(), null, value));
  }
}
