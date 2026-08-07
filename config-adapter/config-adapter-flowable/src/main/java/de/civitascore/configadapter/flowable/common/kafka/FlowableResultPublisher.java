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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.adapter.PipelineStatusPublisher;
import de.civitascore.configadapter.flowable.common.SagaFailure;
import de.civitascore.configadapter.flowable.common.SagaResultPublisher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes saga result messages to Kafka in the exact same format as the custom orchestrator's
 * {@code KafkaSagaActionDispatcher}. Ensures downstream consumers (portal-backend) see identical
 * messages regardless of which orchestrator is running.
 */
public class FlowableResultPublisher implements SagaResultPublisher, PipelineStatusPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(FlowableResultPublisher.class);

  static final String SAGA_RESULT_TOPIC = "de.civitascore.saga.result";
  private static final long PUBLISH_TIMEOUT_MS = 5000;

  private final Producer<String, byte[]> producer;
  private final ObjectMapper objectMapper;
  private final String pipelineStatusTopic;

  public FlowableResultPublisher(Producer<String, byte[]> producer) {
    this(producer, "de.civitascore.pipeline.status");
  }

  public FlowableResultPublisher(Producer<String, byte[]> producer, String pipelineStatusTopic) {
    this.producer = producer;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    this.pipelineStatusTopic = pipelineStatusTopic;
  }

  @Override
  public void publishCompleted(String sagaId, Map<String, Object> resultPayload) {
    var message = new HashMap<String, Object>();
    message.put("type", "SAGA_COMPLETED");
    message.put("messageId", UUID.randomUUID().toString());
    message.put("sagaId", sagaId);
    message.put("status", "COMPLETED");
    message.put("result", resultPayload);

    publish(sagaId, message);
    LOG.info("Published SAGA_COMPLETED: sagaId={}", Encode.forJava(sagaId));
  }

  @Override
  public void publishFailed(SagaFailure failure) {
    var message = new HashMap<String, Object>();
    message.put("type", "SAGA_FAILED");
    message.put("messageId", UUID.randomUUID().toString());
    message.put("sagaId", failure.sagaId());
    message.put("datasetId", failure.datasetId());
    message.put("status", failure.compensated() ? "COMPENSATED" : "FAILED");
    message.put("failedStep", failure.failedStep());
    message.put("error", failure.error());
    message.put("compensated", failure.compensated());
    if (failure.pipelineStatus() != null) {
      message.put("pipelineStatus", failure.pipelineStatus());
    }
    message.put("staleResources", List.of());
    message.put("cleanedResources", List.of());

    publish(failure.sagaId(), message);
    LOG.info(
        "Published SAGA_FAILED: sagaId={}, compensated={}",
        Encode.forJava(failure.sagaId()),
        failure.compensated());
  }

  @Override
  public void publish(Map<String, Object> event) {
    String key = event.get("datasetId") + "/" + event.get("pipelineId");
    publish(pipelineStatusTopic, key, event);
  }

  private void publish(String key, Map<String, Object> message) {
    publish(SAGA_RESULT_TOPIC, key, message);
  }

  private void publish(String topic, String key, Map<String, Object> message) {
    try {
      byte[] json = objectMapper.writeValueAsBytes(message);
      var record = new ProducerRecord<>(topic, key, json);
      producer.send(record).get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (JsonProcessingException e) {
      LOG.error("Failed to serialize saga result: {}", Encode.forJava(e.getMessage()), e);
      throw new IllegalStateException("Failed to serialize saga result", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while publishing saga result", e);
    } catch (ExecutionException | TimeoutException e) {
      LOG.error("Failed to publish saga result: {}", Encode.forJava(e.getMessage()), e);
      throw new IllegalStateException("Failed to publish saga result", e);
    }
  }

  @Override
  public void close() {
    producer.close();
  }
}
