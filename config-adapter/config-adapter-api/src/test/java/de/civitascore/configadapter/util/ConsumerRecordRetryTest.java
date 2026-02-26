/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import de.civitascore.configadapter.util.ConsumerRecordRetry.Outcome;
import de.civitascore.configadapter.util.ConsumerRecordRetry.ProcessResult;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ConsumerRecordRetryTest {

  private static final Logger LOG = LoggerFactory.getLogger(ConsumerRecordRetryTest.class);

  @Test
  @DisplayName("formatRecordContext_formatsTopicPartitionOffset")
  void formatRecordContext_formatsTopicPartitionOffset() {
    String result = ConsumerRecordRetry.formatRecordContext("my-topic", 2, 42L);

    assertEquals("topic=my-topic partition=2 offset=42", result);
  }

  @Nested
  @DisplayName("processOnce")
  class ProcessOnce {

    @Test
    @DisplayName("processOnce_success_returnsSuccessWithNoException")
    void processOnce_success_returnsSuccessWithNoException() {
      ProcessResult result = ConsumerRecordRetry.processOnce(() -> {}, "test", LOG);

      assertEquals(Outcome.SUCCESS, result.outcome());
      assertNull(result.exception());
    }

    @Test
    @DisplayName("processOnce_ioException_returnsPermanentFailure")
    void processOnce_ioException_returnsPermanentFailure() {
      IOException cause = new IOException("malformed JSON");

      ProcessResult result =
          ConsumerRecordRetry.processOnce(
              () -> {
                throw cause;
              },
              "test",
              LOG);

      assertEquals(Outcome.PERMANENT_FAILURE, result.outcome());
      assertSame(cause, result.exception());
    }

    @Test
    @DisplayName("processOnce_runtimeException_returnsTransientFailure")
    void processOnce_runtimeException_returnsTransientFailure() {
      RuntimeException cause = new RuntimeException("broker down");

      ProcessResult result =
          ConsumerRecordRetry.processOnce(
              () -> {
                throw cause;
              },
              "test",
              LOG);

      assertEquals(Outcome.TRANSIENT_FAILURE, result.outcome());
      assertSame(cause, result.exception());
    }

    @Test
    @DisplayName("processOnce_unexpectedCheckedException_returnsPermanentFailure")
    void processOnce_unexpectedCheckedException_returnsPermanentFailure() {
      Exception cause = new Exception("unexpected");

      ProcessResult result =
          ConsumerRecordRetry.processOnce(
              () -> {
                throw cause;
              },
              "test",
              LOG);

      assertEquals(Outcome.PERMANENT_FAILURE, result.outcome());
      assertSame(cause, result.exception());
    }
  }
}
