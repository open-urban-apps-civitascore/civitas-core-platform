/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.DashboardExporter;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportException;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult.Outcome;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult.Reason;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class DashboardExportConsumerTest {

  private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
  private static final Map<String, Object> DOCUMENT =
      Map.of("tool", "superset", "files", Map.of("metadata.yaml", Map.of("version", "1.0.0")));

  private final ObjectMapper mapper = new ObjectMapper();
  private final MockProducer<String, String> producer =
      new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());

  @Test
  void publishesTheExportUnderTheRequestId() throws Exception {
    AtomicReference<String> askedFor = new AtomicReference<>();
    DashboardExportConsumer consumer =
        consumer(
            id -> {
              askedFor.set(id);
              return DOCUMENT;
            });

    consumer.handle(request("r-1", NOW.plusSeconds(30), "7"));

    assertEquals("7", askedFor.get());
    ProducerRecord<String, String> sent = onlySent();
    assertEquals("results", sent.topic());
    assertEquals("r-1", sent.key());
    DashboardExportResult result = mapper.readValue(sent.value(), DashboardExportResult.class);
    assertEquals(Outcome.SUCCESS, result.outcome());
    assertEquals(DOCUMENT, result.document());
  }

  @Test
  void publishesARefusalWithItsReasonAndMessage() throws Exception {
    DashboardExportConsumer consumer =
        consumer(
            id -> {
              throw new DashboardExportException(Reason.REFUSED, "reads with its own SQL query");
            });

    consumer.handle(request("r-2", NOW.plusSeconds(30), "7"));

    DashboardExportResult result =
        mapper.readValue(onlySent().value(), DashboardExportResult.class);
    assertEquals(Outcome.FAILURE, result.outcome());
    assertEquals(Reason.REFUSED, result.reason());
    assertEquals("reads with its own SQL query", result.message());
    assertNull(result.document());
  }

  @Test
  void answersAnUnexpectedFailureAsUnavailableWithoutItsText() throws Exception {
    DashboardExportConsumer consumer =
        consumer(
            id -> {
              throw new IllegalStateException("Superset at http://superset:8088 said 500");
            });

    consumer.handle(request("r-3", NOW.plusSeconds(30), "7"));

    DashboardExportResult result =
        mapper.readValue(onlySent().value(), DashboardExportResult.class);
    assertEquals(Reason.UNAVAILABLE, result.reason());
    assertEquals(DashboardExportConsumer.UNEXPECTED_FAILURE, result.message());
  }

  @Test
  void dropsARequestWhoseRequesterHasStoppedWaiting() {
    AtomicReference<String> askedFor = new AtomicReference<>();
    DashboardExportConsumer consumer =
        consumer(
            id -> {
              askedFor.set(id);
              return DOCUMENT;
            });

    consumer.handle(request("r-4", NOW, "7"));

    assertNull(askedFor.get());
    assertTrue(producer.history().isEmpty());
  }

  @Test
  void dropsAMalformedRequest() {
    DashboardExportConsumer consumer = consumer(id -> DOCUMENT);

    consumer.handle(new ConsumerRecord<>("requests", 0, 0L, "r-5", "not json"));

    assertTrue(producer.history().isEmpty());
  }

  private DashboardExportConsumer consumer(DashboardExporter exporter) {
    return new DashboardExportConsumer(
        new MockConsumer<>("latest"),
        producer,
        "requests",
        "results",
        exporter,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static ConsumerRecord<String, String> request(
      String requestId, Instant expiresAt, String externalDashboardId) {
    String json =
        "{\"requestId\":\""
            + requestId
            + "\",\"expiresAt\":\""
            + expiresAt
            + "\",\"externalDashboardId\":\""
            + externalDashboardId
            + "\"}";
    return new ConsumerRecord<>("requests", 0, 0L, requestId, json);
  }

  private ProducerRecord<String, String> onlySent() {
    assertEquals(1, producer.history().size());
    return producer.history().getFirst();
  }
}
