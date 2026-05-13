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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.flowable.common.SagaFailure;
import java.util.Map;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableResultPublisherTest {

  private MockProducer<String, byte[]> mockProducer;
  private FlowableResultPublisher publisher;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  @BeforeEach
  void setUp() {
    mockProducer =
        new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer());
    publisher = new FlowableResultPublisher(mockProducer);
  }

  @Test
  void shouldPublishCompletedResultInCorrectFormat() throws Exception {
    Map<String, Object> results = Map.of("projectId", "p1", "routeId", "r1");

    publisher.publishCompleted("saga-123", results);

    assertEquals(1, mockProducer.history().size());
    ProducerRecord<String, byte[]> record = mockProducer.history().get(0);

    assertEquals(FlowableResultPublisher.SAGA_RESULT_TOPIC, record.topic());
    assertEquals("saga-123", record.key());

    Map<String, Object> message = objectMapper.readValue(record.value(), MAP_TYPE);
    assertEquals("SAGA_COMPLETED", message.get("type"));
    assertEquals("saga-123", message.get("sagaId"));
    assertEquals("COMPLETED", message.get("status"));
    assertNotNull(message.get("messageId"));
    assertNotNull(message.get("result"));
  }

  @Test
  void shouldPublishFailedResultInCorrectFormat() throws Exception {
    publisher.publishFailed(
        new SagaFailure("saga-123", "ds-456", "create-route", "APISIX connection refused", true));

    assertEquals(1, mockProducer.history().size());
    ProducerRecord<String, byte[]> record = mockProducer.history().get(0);

    assertEquals(FlowableResultPublisher.SAGA_RESULT_TOPIC, record.topic());
    assertEquals("saga-123", record.key());

    Map<String, Object> message = objectMapper.readValue(record.value(), MAP_TYPE);
    assertEquals("SAGA_FAILED", message.get("type"));
    assertEquals("saga-123", message.get("sagaId"));
    assertEquals("ds-456", message.get("datasetId"));
    assertEquals("COMPENSATED", message.get("status"));
    assertEquals("create-route", message.get("failedStep"));
    assertEquals("APISIX connection refused", message.get("error"));
    assertEquals(true, message.get("compensated"));
  }

  @Test
  void shouldSetStatusToFailedWhenNotCompensated() throws Exception {
    publisher.publishFailed(
        new SagaFailure("saga-123", "ds-456", "delete-route", "APISIX down", false));

    ProducerRecord<String, byte[]> record = mockProducer.history().get(0);
    Map<String, Object> message = objectMapper.readValue(record.value(), MAP_TYPE);

    assertEquals("FAILED", message.get("status"));
    assertEquals(false, message.get("compensated"));
  }
}
