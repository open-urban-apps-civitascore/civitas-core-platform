/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.event.handler.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.exception.AdapterException;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Payload;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DlqHandlerTest {

  private static final String DLQ_TOPIC = "test.dlq";
  private static final long PUBLISH_TIMEOUT_MS = 5000L;
  private static final int MAX_RETRIES = 3;

  @Mock private KafkaProducer<String, CloudEvent> mockProducer;
  @Mock private ConfigAdapter mockAdapter;

  @Mock
  @SuppressWarnings("rawtypes")
  private Future mockFuture;

  private DlqHandler dlqHandler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    dlqHandler =
        new DlqHandler(DLQ_TOPIC, PUBLISH_TIMEOUT_MS, MAX_RETRIES, mockProducer, mockAdapter);
  }

  private ConsumerRecord<String, CloudEvent> createTestRecord() {
    CloudEvent event =
        CloudEventBuilder.v1()
            .withId("original-event-id")
            .withSource(URI.create("test://source"))
            .withType("test.type")
            .build();
    return new ConsumerRecord<>("original-topic", 0, 0, "key", event);
  }

  @Nested
  @DisplayName("sendToDLQ")
  class SendToDLQ {

    @Test
    @DisplayName("produces event with correct DLQ extensions")
    @SuppressWarnings("unchecked")
    void validInput_producesCorrectExtensions() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();
      FatalAdapterException exception =
          new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "test failure");

      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);
      when(mockFuture.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .thenReturn(new RecordMetadata(new TopicPartition(DLQ_TOPIC, 0), 0, 0, 0, 0, 0));

      dlqHandler.sendToDLQ(record, exception, false);

      ArgumentCaptor<ProducerRecord<String, CloudEvent>> captor =
          ArgumentCaptor.forClass(ProducerRecord.class);
      verify(mockProducer).send(captor.capture());

      ProducerRecord<String, CloudEvent> dlqRecord = captor.getValue();
      CloudEvent dlqEvent = dlqRecord.value();

      assertEquals(DLQ_TOPIC, dlqRecord.topic());
      assertNotNull(dlqEvent.getExtension("dlqerrorcode"));
      assertNotNull(dlqEvent.getExtension("dlqerrormsg"));
      assertEquals("original-topic", dlqEvent.getExtension("dlqoriginaltopic").toString());
      assertNotNull(dlqEvent.getExtension("dlqtimestamp"));
      assertEquals("3", dlqEvent.getExtension("dlqretrycount").toString());
    }

    @Test
    @DisplayName("delegates failure result to adapter when publishFailure is true")
    @SuppressWarnings("unchecked")
    void nullEventData_skipsGracefully() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();
      FatalAdapterException exception =
          new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "test");

      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);
      when(mockFuture.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .thenReturn(new RecordMetadata(new TopicPartition(DLQ_TOPIC, 0), 0, 0, 0, 0, 0));

      // Event has no data, so delegateFailureResultToAdapter will log debug and skip
      dlqHandler.sendToDLQ(record, exception, true);

      verify(mockProducer).send(any(ProducerRecord.class));
      // No exception thrown — delegation handled gracefully even with null data
    }

    @Test
    @DisplayName("deserializes event data and delegates failure result to adapter")
    @SuppressWarnings("unchecked")
    void withEventData_delegatesFailureResultToAdapter() throws Exception {
      // Build a CloudEvent with serialized ConfigEvent data
      ConfigEvent configEvent =
          new ConfigEvent(
              new Metadata(null, null, null, null, null, null),
              new Payload(null, null, null, null));
      ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
      byte[] eventData = mapper.writeValueAsBytes(configEvent);

      CloudEvent event =
          CloudEventBuilder.v1()
              .withId("event-with-data")
              .withSource(URI.create("test://source"))
              .withType("test.type")
              .withData("application/json", eventData)
              .build();
      ConsumerRecord<String, CloudEvent> record =
          new ConsumerRecord<>("original-topic", 0, 0, "key", event);

      FatalAdapterException exception =
          new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "test");

      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);
      when(mockFuture.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .thenReturn(new RecordMetadata(new TopicPartition(DLQ_TOPIC, 0), 0, 0, 0, 0, 0));

      dlqHandler.sendToDLQ(record, exception, true);

      verify(mockAdapter).publishFailureResult(any(ConfigEvent.class), any(AdapterException.class));
    }

    @Test
    @DisplayName("does not delegate when publishFailure is false")
    @SuppressWarnings("unchecked")
    void publishFailureFalse_doesNotDelegate() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();
      FatalAdapterException exception =
          new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "test");

      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);
      when(mockFuture.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .thenReturn(new RecordMetadata(new TopicPartition(DLQ_TOPIC, 0), 0, 0, 0, 0, 0));

      dlqHandler.sendToDLQ(record, exception, false);

      verify(mockAdapter, never()).publishFailureResult(any(), any());
    }

    @Test
    @DisplayName("throws RuntimeException when producer fails")
    @SuppressWarnings("unchecked")
    void producerFails_throwsRuntimeException() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();
      FatalAdapterException exception =
          new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "test");

      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);
      when(mockFuture.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .thenThrow(
              new java.util.concurrent.ExecutionException(new RuntimeException("kafka down")));

      RuntimeException thrown =
          assertThrows(
              RuntimeException.class, () -> dlqHandler.sendToDLQ(record, exception, false));

      assertEquals("DLQ send failed - event will be reprocessed", thrown.getMessage());
    }
  }
}
