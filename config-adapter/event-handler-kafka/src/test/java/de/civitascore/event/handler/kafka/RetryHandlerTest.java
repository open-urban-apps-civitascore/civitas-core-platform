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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.util.BackoffCalculator;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetryHandlerTest {

  @Mock private DlqHandler dlqHandler;
  @Mock private CloudEventProcessor processor;

  private RetryHandler retryHandler;

  private static final int MAX_RETRIES = 3;
  private static final long INITIAL_BACKOFF_MS = 10L; // short for tests

  @BeforeEach
  void setUp() {
    BackoffCalculator calculator = new BackoffCalculator(INITIAL_BACKOFF_MS, 100L);
    retryHandler = new RetryHandler(MAX_RETRIES, calculator, dlqHandler, processor);
  }

  private ConsumerRecord<String, CloudEvent> createTestRecord() {
    CloudEvent event =
        CloudEventBuilder.v1()
            .withId("test-id")
            .withSource(URI.create("test://source"))
            .withType("test.type")
            .build();
    return new ConsumerRecord<>("test-topic", 0, 0, "key", event);
  }

  @Nested
  @DisplayName("processWithRetry")
  class ProcessWithRetry {

    @Test
    @DisplayName("succeeds on first attempt without retry")
    void firstAttemptSucceeds_noDlqInvocation() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();

      retryHandler.processWithRetry(record);

      verify(processor).handleEvent("test-topic", record.value());
      verify(dlqHandler, never()).sendToDLQ(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("retries on retryable error and succeeds")
    void retryableError_succeedsOnRetry() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();
      doThrow(new RetryableAdapterException(AdapterErrorCode.NETWORK_ERROR, null, "test", "err"))
          .doNothing()
          .when(processor)
          .handleEvent("test-topic", record.value());

      retryHandler.processWithRetry(record);

      verify(processor, times(2)).handleEvent("test-topic", record.value());
      verify(dlqHandler, never()).sendToDLQ(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("sends to DLQ when max retries exceeded")
    void maxRetriesExceeded_sendsToDlq() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();
      RetryableAdapterException exception =
          new RetryableAdapterException(AdapterErrorCode.NETWORK_ERROR, null, "test", "err");
      doThrow(exception).when(processor).handleEvent("test-topic", record.value());

      retryHandler.processWithRetry(record);

      // 1 initial + 3 retries = 4 attempts total
      verify(processor, times(MAX_RETRIES + 1)).handleEvent("test-topic", record.value());
      verify(dlqHandler).sendToDLQ(eq(record), any(RetryableAdapterException.class), eq(true));
    }

    @Test
    @DisplayName("sends to DLQ immediately on fatal error")
    void fatalError_sendsToDlqImmediately() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();
      FatalAdapterException exception =
          new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "fatal");
      doThrow(exception).when(processor).handleEvent("test-topic", record.value());

      retryHandler.processWithRetry(record);

      verify(processor, times(1)).handleEvent("test-topic", record.value());
      verify(dlqHandler).sendToDLQ(record, exception, false);
    }

    @Test
    @DisplayName("wraps unexpected exception as fatal and sends to DLQ")
    void unexpectedException_wrapsAsFatalAndSendsToDlq() throws Exception {
      ConsumerRecord<String, CloudEvent> record = createTestRecord();
      doThrow(new NullPointerException("unexpected"))
          .when(processor)
          .handleEvent("test-topic", record.value());

      retryHandler.processWithRetry(record);

      verify(processor, times(1)).handleEvent("test-topic", record.value());
      verify(dlqHandler).sendToDLQ(eq(record), any(FatalAdapterException.class), eq(true));
    }
  }

  @Nested
  @DisplayName("loadAndValidate")
  class LoadAndValidate {

    @Test
    @DisplayName("returns valid config with defaults")
    void defaults_returnsValidRetryConfig() throws FatalAdapterException {
      Map<String, Object> props = new HashMap<>();
      AppConfig config = new AppConfig(new MapConfiguration(props));

      RetryHandler.RetryConfig retryConfig = RetryHandler.loadAndValidate(config);

      assertEquals(3, retryConfig.maxRetries());
      assertEquals(1000L, retryConfig.initialBackoffMs());
      assertNotNull(retryConfig.calculator());
    }

    @Test
    @DisplayName("throws on dangerous configuration")
    void dangerousConfig_throwsFatalException() {
      // 10 retries * 30000ms cap = 300_000ms total, exceeds 80% of default poll interval
      // (300_000ms * 0.8 = 240_000ms). Uses realistic values to avoid long overflow in
      // BackoffCalculator at high attempt counts.
      Map<String, Object> props = new HashMap<>();
      props.put("kafka.retry.max.attempts", "10");
      props.put("kafka.retry.initial.backoff.ms", "30000");
      AppConfig config = new AppConfig(new MapConfiguration(props));

      FatalAdapterException ex =
          assertThrows(FatalAdapterException.class, () -> RetryHandler.loadAndValidate(config));

      assertEquals(AdapterErrorCode.CONFIGURATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("accepts custom config within safe limits")
    void customSafeConfig_returnsConfiguredValues() throws FatalAdapterException {
      Map<String, Object> props = new HashMap<>();
      props.put("kafka.retry.max.attempts", "2");
      props.put("kafka.retry.initial.backoff.ms", "500");
      AppConfig config = new AppConfig(new MapConfiguration(props));

      RetryHandler.RetryConfig retryConfig = RetryHandler.loadAndValidate(config);

      assertEquals(2, retryConfig.maxRetries());
      assertEquals(500L, retryConfig.initialBackoffMs());
    }
  }
}
