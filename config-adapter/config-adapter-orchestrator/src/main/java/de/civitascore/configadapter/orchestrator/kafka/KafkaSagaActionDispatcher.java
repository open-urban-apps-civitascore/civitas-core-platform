/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.orchestrator.engine.SagaAction;
import de.civitascore.configadapter.orchestrator.engine.SagaActionDispatcher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka-based implementation of {@link SagaActionDispatcher}. Translates saga actions into Kafka
 * messages and publishes them to the appropriate topics.
 *
 * <p>Action routing:
 *
 * <ul>
 *   <li>{@link SagaAction.ExecuteStep} → adapter execute topic (e.g. {@code
 *       de.civitascore.dataset.frost.execute})
 *   <li>{@link SagaAction.CompensateStep} → adapter compensate topic (e.g. {@code
 *       de.civitascore.dataset.frost.compensate})
 *   <li>{@link SagaAction.CompleteSaga} → saga result topic ({@code de.civitascore.saga.result})
 *   <li>{@link SagaAction.FailSaga} → saga result topic ({@code de.civitascore.saga.result})
 *   <li>{@link SagaAction.PublishManualIntervention} → manual intervention topic ({@code
 *       de.civitascore.saga.manual-intervention})
 *   <li>{@link SagaAction.SkipStep} → logged only (no Kafka message)
 *   <li>{@link SagaAction.PersistState} → handled by {@code SagaEngine} (never dispatched here)
 * </ul>
 */
public class KafkaSagaActionDispatcher implements SagaActionDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaSagaActionDispatcher.class);

  static final String SAGA_RESULT_TOPIC = "de.civitascore.saga.result";
  static final String MANUAL_INTERVENTION_TOPIC = "de.civitascore.saga.manual-intervention";

  private final KafkaProducer<String, byte[]> producer;
  private final ObjectMapper objectMapper;
  private final long publishTimeoutMs;

  /**
   * Creates a new dispatcher.
   *
   * @param producer Kafka producer for sending messages (key=sagaId, value=JSON bytes)
   * @param publishTimeoutMs timeout for synchronous send operations
   */
  public KafkaSagaActionDispatcher(KafkaProducer<String, byte[]> producer, long publishTimeoutMs) {
    this.producer = producer;
    this.publishTimeoutMs = publishTimeoutMs;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Override
  public void dispatch(SagaAction action) {
    switch (action) {
      case SagaAction.ExecuteStep exec -> dispatchExecuteStep(exec);
      case SagaAction.CompensateStep comp -> dispatchCompensateStep(comp);
      case SagaAction.CompleteSaga complete -> dispatchCompleteSaga(complete);
      case SagaAction.FailSaga fail -> dispatchFailSaga(fail);
      case SagaAction.PublishManualIntervention intervention ->
          dispatchManualIntervention(intervention);
      case SagaAction.SkipStep skip ->
          LOG.info("Skipping step {}: {}", Encode.forJava(skip.stepId()), skip.reason());
      case SagaAction.PersistState ignored ->
          LOG.warn("PersistState should be handled by SagaEngine, not the dispatcher");
    }
  }

  private void dispatchExecuteStep(SagaAction.ExecuteStep exec) {
    var message = new HashMap<String, Object>();
    message.put("type", "EXECUTE_STEP");
    message.put("messageId", UUID.randomUUID().toString());
    message.put("stepId", exec.stepId());
    message.put("adapter", exec.adapter());
    message.put("operation", exec.operation());
    message.putAll(exec.payload());

    publishToTopic(exec.topic(), extractSagaId(exec.payload()), message);

    LOG.info(
        "Dispatched ExecuteStep: step={}, adapter={}, topic={}",
        Encode.forJava(exec.stepId()),
        Encode.forJava(exec.adapter()),
        Encode.forJava(exec.topic()));
  }

  private void dispatchCompensateStep(SagaAction.CompensateStep comp) {
    var message = new HashMap<String, Object>();
    message.put("type", "COMPENSATE_STEP");
    message.put("messageId", UUID.randomUUID().toString());
    message.put("stepId", comp.stepId());
    message.put("adapter", comp.adapter());
    message.put("operation", comp.operation());
    message.putAll(comp.payload());

    publishToTopic(comp.topic(), extractSagaId(comp.payload()), message);

    LOG.info(
        "Dispatched CompensateStep: step={}, adapter={}, topic={}",
        Encode.forJava(comp.stepId()),
        Encode.forJava(comp.adapter()),
        Encode.forJava(comp.topic()));
  }

  private void dispatchCompleteSaga(SagaAction.CompleteSaga complete) {
    var message = new HashMap<String, Object>();
    message.put("type", "SAGA_COMPLETED");
    message.put("messageId", UUID.randomUUID().toString());
    message.put("sagaId", complete.sagaId());
    message.put("status", "COMPLETED");
    message.put("result", complete.resultPayload());

    publishToTopic(SAGA_RESULT_TOPIC, complete.sagaId(), message);

    LOG.info("Dispatched CompleteSaga: sagaId={}", Encode.forJava(complete.sagaId()));
  }

  private void dispatchFailSaga(SagaAction.FailSaga fail) {
    var message = new HashMap<String, Object>();
    message.put("type", "SAGA_FAILED");
    message.put("messageId", UUID.randomUUID().toString());
    message.put("sagaId", fail.sagaId());
    message.put("status", fail.compensated() ? "COMPENSATED" : "FAILED");
    message.put("failedStep", fail.failedStep());
    message.put("error", fail.error());
    message.put("compensated", fail.compensated());
    message.put("staleResources", fail.staleResources());
    message.put("cleanedResources", fail.cleanedResources());

    publishToTopic(SAGA_RESULT_TOPIC, fail.sagaId(), message);

    LOG.info(
        "Dispatched FailSaga: sagaId={}, compensated={}, staleResources={}",
        Encode.forJava(fail.sagaId()),
        fail.compensated(),
        fail.staleResources().size());
  }

  private void dispatchManualIntervention(SagaAction.PublishManualIntervention intervention) {
    var message = new HashMap<String, Object>();
    message.put("type", "MANUAL_INTERVENTION_REQUIRED");
    message.put("messageId", UUID.randomUUID().toString());
    message.put("sagaId", intervention.sagaId());
    message.put("sagaContext", intervention.sagaContext());

    publishToTopic(MANUAL_INTERVENTION_TOPIC, intervention.sagaId(), message);

    LOG.warn(
        "Dispatched ManualIntervention: sagaId={} — REQUIRES OPERATOR ACTION",
        Encode.forJava(intervention.sagaId()));
  }

  private void publishToTopic(String topic, String key, Map<String, Object> message) {
    try {
      byte[] json = objectMapper.writeValueAsBytes(message);
      var record = new ProducerRecord<>(topic, key, json);

      producer.send(record).get(publishTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (JsonProcessingException e) {
      LOG.error(
          "Failed to serialize message for topic {}: {}",
          Encode.forJava(topic),
          Encode.forJava(String.valueOf(e.getMessage())),
          e);
      throw new IllegalStateException("Failed to serialize message for " + topic, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while publishing to " + topic, e);
    } catch (ExecutionException | TimeoutException e) {
      LOG.error(
          "Failed to publish message to topic {}: {}",
          Encode.forJava(topic),
          Encode.forJava(String.valueOf(e.getMessage())),
          e);
      throw new IllegalStateException("Failed to publish to " + topic, e);
    }
  }

  private static String extractSagaId(Map<String, Object> payload) {
    Object sagaId = payload.get("sagaId");
    return sagaId != null ? sagaId.toString() : UUID.randomUUID().toString();
  }
}
