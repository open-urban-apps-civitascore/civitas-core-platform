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

import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.util.BackoffCalculator;
import io.cloudevents.CloudEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles retry logic with exponential backoff for transient adapter errors. Delegates to a {@link
 * DlqHandler} when retries are exhausted or a fatal error occurs.
 *
 * <p>Retry behaviour:
 *
 * <ul>
 *   <li>{@link RetryableAdapterException} → exponential backoff, retry up to {@code maxRetries}
 *   <li>{@link FatalAdapterException} → immediate DLQ, no retry
 *   <li>Unknown exceptions → wrapped as fatal, immediate DLQ
 * </ul>
 */
class RetryHandler {

  private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);

  // Configuration keys
  private static final String KAFKA_RETRY_MAX_ATTEMPTS = "kafka.retry.max.attempts";
  private static final String KAFKA_RETRY_INITIAL_BACKOFF_MS = "kafka.retry.initial.backoff.ms";
  private static final String KAFKA_MAX_POLL_INTERVAL_MS =
      "kafka." + ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG;

  // Default values
  private static final int DEFAULT_MAX_RETRIES = 3;
  private static final long DEFAULT_INITIAL_BACKOFF_MS = 1000L;
  private static final long MAX_BACKOFF_MS = 30000L;

  private final int maxRetries;
  private final BackoffCalculator backoffCalculator;
  private final DlqHandler dlqHandler;
  private final CloudEventProcessor processor;

  RetryHandler(
      int maxRetries,
      BackoffCalculator backoffCalculator,
      DlqHandler dlqHandler,
      CloudEventProcessor processor) {
    this.maxRetries = maxRetries;
    this.backoffCalculator = backoffCalculator;
    this.dlqHandler = dlqHandler;
    this.processor = processor;
  }

  /**
   * Loads retry configuration from the application config and validates it against the Kafka poll
   * interval.
   *
   * @param config the application config
   * @return a {@link RetryConfig} holding the parsed values
   * @throws FatalAdapterException if the configuration is dangerous (total backoff exceeds poll
   *     interval)
   */
  static RetryConfig loadAndValidate(ApplicationConfig config) throws FatalAdapterException {
    int maxRetries =
        Integer.parseInt(
            config.getProperty(KAFKA_RETRY_MAX_ATTEMPTS, String.valueOf(DEFAULT_MAX_RETRIES)));
    long initialBackoffMs =
        Long.parseLong(
            config.getProperty(
                KAFKA_RETRY_INITIAL_BACKOFF_MS, String.valueOf(DEFAULT_INITIAL_BACKOFF_MS)));

    BackoffCalculator calculator = new BackoffCalculator(initialBackoffMs, MAX_BACKOFF_MS);

    validateRetryConfiguration(config, maxRetries, calculator);

    return new RetryConfig(maxRetries, initialBackoffMs, calculator);
  }

  private static void validateRetryConfiguration(
      ApplicationConfig config, int maxRetries, BackoffCalculator calculator)
      throws FatalAdapterException {
    String pollIntervalStr = config.getProperty(KAFKA_MAX_POLL_INTERVAL_MS, "300000");
    long maxPollIntervalMs = Long.parseLong(pollIntervalStr);

    long totalPotentialWaitTime = 0;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      totalPotentialWaitTime += calculator.calculate(attempt);
    }

    long safeLimit = (long) (maxPollIntervalMs * 0.8);

    if (totalPotentialWaitTime > safeLimit) {
      String msg =
          String.format(
              "Dangerous configuration detected! Total retry backoff (%d ms) exceeds 80%% of %s (%d ms). "
                  + "Please decrease '%s' or increase '%s'.",
              totalPotentialWaitTime,
              KAFKA_MAX_POLL_INTERVAL_MS,
              maxPollIntervalMs,
              KAFKA_RETRY_MAX_ATTEMPTS,
              KAFKA_MAX_POLL_INTERVAL_MS);

      throw new FatalAdapterException(AdapterErrorCode.CONFIGURATION_ERROR, msg);
    }
  }

  /**
   * Processes a record with blocking retry for transient errors.
   *
   * @param record the Kafka record to process
   */
  void processWithRetry(ConsumerRecord<String, CloudEvent> record) {
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        processor.handleEvent(record.topic(), record.value());
        logger.debug(
            "Successfully processed event {} on attempt {}",
            Encode.forJava(record.value().getId()),
            attempt + 1);
        return;
      } catch (RetryableAdapterException e) {
        if (attempt == maxRetries) {
          logger.error(
              "Max retries ({}) exceeded for event {}. Sending to DLQ. Error: {}",
              maxRetries,
              Encode.forJava(record.value().getId()),
              Encode.forJava(e.getInternalMessage()));
          dlqHandler.sendToDLQ(record, e, true);
          return;
        }
        if (!waitBeforeRetry(record, e, attempt + 1)) {
          return;
        }
      } catch (FatalAdapterException e) {
        logger.error(
            "Fatal error processing event {}. Sending to DLQ immediately. Error: {}",
            Encode.forJava(record.value().getId()),
            Encode.forJava(e.getInternalMessage()));
        dlqHandler.sendToDLQ(record, e, false);
        return;
      } catch (Exception e) {
        logger.error(
            "Unexpected error processing event {}. Wrapping as fatal and sending to DLQ.",
            Encode.forJava(record.value().getId()),
            e);
        dlqHandler.sendToDLQ(
            record,
            new FatalAdapterException(AdapterErrorCode.UNKNOWN_ERROR, e, e.getMessage()),
            true);
        return;
      }
    }
  }

  /**
   * Sleeps for the calculated backoff duration before the next retry attempt.
   *
   * @return {@code true} if the sleep completed normally, {@code false} if interrupted (thread
   *     interrupt flag is restored and the record is sent to DLQ)
   */
  private boolean waitBeforeRetry(
      ConsumerRecord<String, CloudEvent> record, RetryableAdapterException cause, int attempt) {
    long backoff = backoffCalculator.calculate(attempt);
    logger.warn(
        "Retryable error processing event {} (attempt {}/{}). Retrying in {}ms. Error: {}",
        Encode.forJava(record.value().getId()),
        attempt,
        maxRetries,
        backoff,
        Encode.forJava(cause.getInternalMessage()));
    try {
      Thread.sleep(backoff);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn("Retry sleep interrupted for event {}", Encode.forJava(record.value().getId()));
      dlqHandler.sendToDLQ(record, cause, true);
      return false;
    }
  }

  /** Holds parsed retry configuration values. */
  record RetryConfig(int maxRetries, long initialBackoffMs, BackoffCalculator calculator) {}
}
