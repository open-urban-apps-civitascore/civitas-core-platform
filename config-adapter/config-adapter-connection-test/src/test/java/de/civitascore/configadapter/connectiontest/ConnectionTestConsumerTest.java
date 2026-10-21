/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class ConnectionTestConsumerTest {

  /** The shutdown hook and the normal path may both close it; the second close does nothing. */
  @Test
  void closingTwiceClosesTheClientsOnce() {
    MockConsumer<String, String> kafka = new MockConsumer<>("latest");
    MockProducer<String, String> producer =
        new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
    ConnectionTestConsumer consumer =
        new ConnectionTestConsumer(
            kafka,
            producer,
            "requests",
            "results",
            new ConnectionTestService(List.of(), connector -> connector),
            new ConnectionTestSettings(Duration.ofSeconds(5), 1),
            Clock.systemUTC());
    consumer.start();

    consumer.close();

    assertTrue(kafka.closed());
    assertTrue(producer.closed());
    assertDoesNotThrow(consumer::close);
  }
}
