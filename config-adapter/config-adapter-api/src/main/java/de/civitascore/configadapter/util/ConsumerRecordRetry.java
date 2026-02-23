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

import java.io.IOException;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;

/**
 * Retry utility for Kafka consumer record processing. Classifies exceptions as permanent or
 * transient for use with {@link RetryConsumerLoop}.
 *
 * <ul>
 *   <li>{@link IOException} → permanent (malformed data) → skip immediately, no retry
 *   <li>{@link RuntimeException} → transient → caller retries with backoff
 *   <li>Other checked exceptions → permanent → skip
 * </ul>
 */
public final class ConsumerRecordRetry {

  /** Default maximum number of retry attempts for transient errors. */
  public static final int DEFAULT_MAX_RETRIES = 3;

  /** Default initial backoff delay in milliseconds. */
  public static final long DEFAULT_INITIAL_BACKOFF_MS = 1000L;

  /** Default maximum backoff delay in milliseconds. */
  public static final long DEFAULT_MAX_BACKOFF_MS = 30_000L;

  private ConsumerRecordRetry() {}

  /** Classifies the outcome of a single processing attempt. */
  public enum Outcome {
    SUCCESS,
    PERMANENT_FAILURE,
    TRANSIENT_FAILURE
  }

  /** Result of a single processing attempt: outcome plus the exception (if any). */
  public record ProcessResult(Outcome outcome, Exception exception) {
    public static ProcessResult success() {
      return new ProcessResult(Outcome.SUCCESS, null);
    }

    public static ProcessResult permanent(Exception e) {
      return new ProcessResult(Outcome.PERMANENT_FAILURE, e);
    }

    public static ProcessResult transientFailure(Exception e) {
      return new ProcessResult(Outcome.TRANSIENT_FAILURE, e);
    }
  }

  /** Functional interface for processing a single consumer record. */
  @FunctionalInterface
  public interface RecordProcessor {
    void process() throws Exception;
  }

  /**
   * Formats a human-readable record context string for log messages. Extracted here so that all
   * consumers share the same format without duplicating the method.
   *
   * @param topic the Kafka topic name
   * @param partition the partition number
   * @param offset the record offset
   * @return formatted context string, e.g. "topic=X partition=0 offset=42"
   */
  public static String formatRecordContext(String topic, int partition, long offset) {
    return "topic=" + topic + " partition=" + partition + " offset=" + offset;
  }

  /**
   * Processes a record once and classifies the outcome. Never throws.
   *
   * <p>Logs permanent failures at ERROR (terminal — caller never retries). Does <b>not</b> log
   * transient failures (caller decides level based on attempt count).
   *
   * @param processor the processing logic to execute
   * @param recordContext human-readable context for log messages
   * @param log the logger to use
   * @return the classified result
   */
  public static ProcessResult processOnce(
      RecordProcessor processor, String recordContext, Logger log) {
    try {
      processor.process();
      return ProcessResult.success();
    } catch (IOException e) {
      log.error(
          "Permanent error processing record ({}), skipping: {}",
          Encode.forJava(recordContext),
          Encode.forJava(String.valueOf(e.getMessage())),
          e);
      return ProcessResult.permanent(e);
    } catch (RuntimeException e) {
      return ProcessResult.transientFailure(e);
    } catch (Exception e) {
      log.error(
          "Unexpected checked exception processing record ({}), skipping: {}",
          Encode.forJava(recordContext),
          Encode.forJava(String.valueOf(e.getMessage())),
          e);
      return ProcessResult.permanent(e);
    }
  }
}
