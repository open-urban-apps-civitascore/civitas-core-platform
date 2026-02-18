/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.event.handler.kafka;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Operation;
import io.cloudevents.CloudEvent;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for KafkaEventHandler.publish() method exception handling. Tests verify that publish
 * errors are properly categorized and thrown as RetryableAdapterException or FatalAdapterException.
 */
@ExtendWith(MockitoExtension.class)
class KafkaEventHandlerPublishTest {

  @Mock private KafkaProducer<String, CloudEvent> mockProducer;

  @Mock private ConfigAdapter mockAdapter;

  @Mock
  @SuppressWarnings("rawtypes")
  private Future mockFuture;

  private KafkaEventHandler handler;
  private AppConfig config;

  @BeforeEach
  void setUp() throws Exception {
    // Create test configuration
    Map<String, Object> props = new HashMap<>();
    props.put("kafka.bootstrap.servers", "localhost:9092");
    props.put("kafka.group.id", "test-group");
    props.put("kafka.publish.timeout.ms", "1000");
    config = new AppConfig(new MapConfiguration(props));

    // Setup mock adapter
    when(mockAdapter.getSubscribedTopics()).thenReturn(List.of("test-topic"));

    // Create handler - we'll inject the mock producer after initialization
    handler = new KafkaEventHandler();
  }

  private void injectMockProducer() throws Exception {
    // Initialize with real config (creates real producer)
    handler.initialize(config, mockAdapter);

    // Inject mock producer using reflection
    Field producerField = KafkaEventHandler.class.getDeclaredField("kafkaProducer");
    producerField.setAccessible(true);
    producerField.set(handler, mockProducer);
  }

  @Test
  @DisplayName("shouldThrowRetryableException_WhenPublishTimesOut")
  @SuppressWarnings("unchecked")
  void shouldThrowRetryableException_WhenPublishTimesOut() throws Exception {
    // Given
    injectMockProducer();
    when(mockProducer.send(any())).thenReturn(mockFuture);
    when(mockFuture.get(anyLong(), any(TimeUnit.class)))
        .thenThrow(new TimeoutException("Timeout waiting for Kafka"));

    ConfigResultEvent resultEvent = createTestResultEvent();

    // When/Then
    RetryableAdapterException exception =
        assertThrows(
            RetryableAdapterException.class, () -> handler.publish("result-topic", resultEvent));

    assertEquals(AdapterErrorCode.PUBLISH_TIMEOUT, exception.getErrorCode());
    assertTrue(exception.getInternalMessage().contains("result-topic"));
  }

  @Test
  @DisplayName("shouldThrowRetryableException_WhenNetworkError")
  @SuppressWarnings("unchecked")
  void shouldThrowRetryableException_WhenNetworkError() throws Exception {
    // Given
    injectMockProducer();
    when(mockProducer.send(any())).thenReturn(mockFuture);
    when(mockFuture.get(anyLong(), any(TimeUnit.class)))
        .thenThrow(new ExecutionException(new NetworkException("Network unavailable")));

    ConfigResultEvent resultEvent = createTestResultEvent();

    // When/Then
    RetryableAdapterException exception =
        assertThrows(
            RetryableAdapterException.class, () -> handler.publish("result-topic", resultEvent));

    assertEquals(AdapterErrorCode.PUBLISH_ERROR, exception.getErrorCode());
    assertTrue(exception.getInternalMessage().contains("result-topic"));
  }

  @Test
  @DisplayName("shouldThrowRetryableException_WhenKafkaTimeoutException")
  @SuppressWarnings("unchecked")
  void shouldThrowRetryableException_WhenKafkaTimeoutException() throws Exception {
    // Given
    injectMockProducer();
    when(mockProducer.send(any())).thenReturn(mockFuture);
    when(mockFuture.get(anyLong(), any(TimeUnit.class)))
        .thenThrow(
            new ExecutionException(
                new org.apache.kafka.common.errors.TimeoutException("Kafka timeout")));

    ConfigResultEvent resultEvent = createTestResultEvent();

    // When/Then
    RetryableAdapterException exception =
        assertThrows(
            RetryableAdapterException.class, () -> handler.publish("result-topic", resultEvent));

    assertEquals(AdapterErrorCode.PUBLISH_ERROR, exception.getErrorCode());
  }

  @Test
  @DisplayName("shouldThrowFatalException_WhenSerializationError")
  @SuppressWarnings("unchecked")
  void shouldThrowFatalException_WhenSerializationError() throws Exception {
    // Given
    injectMockProducer();
    when(mockProducer.send(any())).thenReturn(mockFuture);
    when(mockFuture.get(anyLong(), any(TimeUnit.class)))
        .thenThrow(new ExecutionException(new SerializationException("Cannot serialize")));

    ConfigResultEvent resultEvent = createTestResultEvent();

    // When/Then
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class, () -> handler.publish("result-topic", resultEvent));

    assertEquals(AdapterErrorCode.PUBLISH_ERROR, exception.getErrorCode());
    assertTrue(exception.getInternalMessage().contains("result-topic"));
  }

  @Test
  @DisplayName("shouldThrowRetryableException_WhenInterrupted")
  @SuppressWarnings("unchecked")
  void shouldThrowRetryableException_WhenInterrupted() throws Exception {
    // Given
    injectMockProducer();
    when(mockProducer.send(any())).thenReturn(mockFuture);
    when(mockFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

    ConfigResultEvent resultEvent = createTestResultEvent();

    // When/Then
    RetryableAdapterException exception =
        assertThrows(
            RetryableAdapterException.class, () -> handler.publish("result-topic", resultEvent));

    assertEquals(AdapterErrorCode.PUBLISH_ERROR, exception.getErrorCode());
    assertTrue(exception.getInternalMessage().contains("interrupted"));

    // Verify interrupt flag is set
    assertTrue(Thread.currentThread().isInterrupted());
    // Clear the interrupt flag for other tests
    Thread.interrupted();
  }

  @Test
  @DisplayName("shouldSucceed_WhenPublishSuccessful")
  @SuppressWarnings("unchecked")
  void shouldSucceed_WhenPublishSuccessful() throws Exception {
    // Given
    injectMockProducer();
    RecordMetadata metadata =
        new RecordMetadata(new TopicPartition("result-topic", 0), 0L, 0, 0L, 0, 0);
    when(mockProducer.send(any())).thenReturn(mockFuture);
    when(mockFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(metadata);

    ConfigResultEvent resultEvent = createTestResultEvent();

    // When/Then - should not throw
    assertDoesNotThrow(() -> handler.publish("result-topic", resultEvent));

    // Verify producer was called
    verify(mockProducer).send(any());
  }

  @Test
  @DisplayName("shouldUseConfiguredTimeout")
  void shouldUseConfiguredTimeout() throws Exception {
    // Given
    Map<String, Object> props = new HashMap<>();
    props.put("kafka.bootstrap.servers", "localhost:9092");
    props.put("kafka.group.id", "test-group");
    props.put("kafka.publish.timeout.ms", "3000");
    AppConfig customConfig = new AppConfig(new MapConfiguration(props));

    KafkaEventHandler customHandler = new KafkaEventHandler();
    customHandler.initialize(customConfig, mockAdapter);

    // When/Then
    assertEquals(3000L, customHandler.getPublishTimeoutMs());

    customHandler.close();
  }

  @Test
  @DisplayName("shouldUseDefaultTimeout_WhenNotConfigured")
  void shouldUseDefaultTimeout_WhenNotConfigured() throws Exception {
    // Given - config without explicit timeout
    Map<String, Object> props = new HashMap<>();
    props.put("kafka.bootstrap.servers", "localhost:9092");
    props.put("kafka.group.id", "test-group");
    AppConfig defaultConfig = new AppConfig(new MapConfiguration(props));

    KafkaEventHandler defaultHandler = new KafkaEventHandler();
    defaultHandler.initialize(defaultConfig, mockAdapter);

    // When/Then - default is 5000ms
    assertEquals(5000L, defaultHandler.getPublishTimeoutMs());

    defaultHandler.close();
  }

  private ConfigResultEvent createTestResultEvent() {
    return ConfigResultEvent.success(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "Test success message",
        "resource-123",
        Operation.CREATE,
        "users/resource-123",
        "test.source",
        "de.civitascore.test.result");
  }
}
