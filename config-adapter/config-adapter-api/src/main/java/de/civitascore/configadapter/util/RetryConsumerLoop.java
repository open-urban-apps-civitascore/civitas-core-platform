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

import de.civitascore.configadapter.util.ConsumerRecordRetry.Outcome;
import de.civitascore.configadapter.util.ConsumerRecordRetry.ProcessResult;
import de.civitascore.configadapter.util.ConsumerRecordRetry.RecordProcessor;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;

/**
 * Reusable non-blocking poll-based consumer loop with per-record retry. Owns the virtual thread
 * lifecycle, retry state, and pending-record iterator.
 *
 * <p>Uses {@code consumer.poll()} instead of {@code Thread.sleep()} for retry delays so that the
 * underlying consumer can still send heartbeats and respond to rebalances while waiting.
 *
 * @param <K> record key type
 * @param <V> record value type
 */
public class RetryConsumerLoop<K, V> {

  private final PollingConsumer<K, V> consumer;
  private final Function<PollingConsumer.Record<K, V>, RecordProcessor> processorFactory;
  private final BackoffCalculator backoffCalculator;
  private final Logger log;
  private final String consumerName;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread consumerThread;
  private static final long STOP_TIMEOUT_MS = 5000L;

  private record RetryState<K, V>(
      PollingConsumer.Record<K, V> record, int attempt, long deadline) {}

  // Retry state — accessed only from the consumer thread (single-writer)
  private RetryState<K, V> retryState;
  private Iterator<PollingConsumer.Record<K, V>> pending = Collections.emptyIterator();

  /**
   * @param consumer the polling consumer abstraction
   * @param processorFactory maps a record to a {@link RecordProcessor} for single-attempt execution
   * @param backoffCalculator exponential backoff calculator
   * @param log logger for this consumer
   * @param consumerName human-readable name used for thread naming and log messages
   */
  public RetryConsumerLoop(
      PollingConsumer<K, V> consumer,
      Function<PollingConsumer.Record<K, V>, RecordProcessor> processorFactory,
      BackoffCalculator backoffCalculator,
      Logger log,
      String consumerName) {
    this.consumer = consumer;
    this.processorFactory = processorFactory;
    this.backoffCalculator = backoffCalculator;
    this.log = log;
    this.consumerName = consumerName;
  }

  /**
   * Starts the consumer loop in a virtual thread.
   *
   * @return {@code true} if the loop was started, {@code false} if already running
   */
  public boolean start() {
    if (running.compareAndSet(false, true)) {
      consumerThread = Thread.ofVirtual().name(consumerName).start(this::consumeLoop);
      return true;
    }
    return false;
  }

  /** Signals the loop to stop and waits up to 5 s for the thread to finish. */
  public void stop() {
    log.info("Stopping {}", consumerName);
    running.set(false);
    if (consumerThread != null) {
      try {
        consumerThread.join(STOP_TIMEOUT_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Whether the loop is currently running. */
  public boolean isRunning() {
    return running.get();
  }

  /**
   * Clears all retry state and the pending-record iterator. Safe to call from a rebalance listener
   * callback (runs on the consumer thread during {@code poll()}).
   */
  public void resetState() {
    retryState = null;
    pending = Collections.emptyIterator();
    consumer.resume();
  }

  private void consumeLoop() {
    try {
      while (running.get()) {
        try {
          // ── Retry wait: poll briefly for heartbeats (consumer paused → empty) ──
          if (retryState != null && System.currentTimeMillis() < retryState.deadline()) {
            consumer.poll(Duration.ofMillis(100));
            continue;
          }

          // ── Retry execution: deadline reached ──
          if (retryState != null) {
            handleRetryExecution();
            continue;
          }

          // ── Fill buffer from the consumer if exhausted ──
          if (!pending.hasNext()) {
            pending = consumer.poll(Duration.ofMillis(500));
          }

          // ── Process next buffered record ──
          if (pending.hasNext()) {
            handleNextRecord();
          }
        } catch (RuntimeException e) {
          // Note: the old consumers caught Kafka's WakeupException separately; this is
          // unnecessary here because stop() uses running.set(false) instead of
          // consumer.wakeup(). If a WakeupException were to propagate (e.g. from direct
          // KafkaConsumer access), the generic RuntimeException catch + running.get()
          // check handles it correctly — during shutdown running is false, so the loop
          // exits silently.
          if (running.get()) {
            log.error("Error in {} loop", consumerName, e);
          }
        }
      }
    } finally {
      resetState();
      log.info("{} loop ended", consumerName);
    }
  }

  private void handleRetryExecution() {
    String ctx =
        ConsumerRecordRetry.formatRecordContext(
            retryState.record().topic(),
            retryState.record().partition(),
            retryState.record().offset());
    ProcessResult r =
        ConsumerRecordRetry.processOnce(processorFactory.apply(retryState.record()), ctx, log);
    if (r.outcome() == Outcome.TRANSIENT_FAILURE
        && retryState.attempt() < ConsumerRecordRetry.DEFAULT_MAX_RETRIES) {
      int nextAttempt = retryState.attempt() + 1;
      long delay = backoffCalculator.calculate(nextAttempt);
      retryState =
          new RetryState<>(retryState.record(), nextAttempt, System.currentTimeMillis() + delay);
      log.warn(
          "Transient error processing record ({}) (attempt {}/{}), retrying in {}ms: {}",
          Encode.forJava(ctx),
          retryState.attempt(),
          ConsumerRecordRetry.DEFAULT_MAX_RETRIES,
          delay,
          Encode.forJava(String.valueOf(r.exception().getMessage())));
    } else {
      if (r.outcome() == Outcome.TRANSIENT_FAILURE) {
        log.error(
            "Max retries ({}) exceeded for record ({}), skipping: {}",
            ConsumerRecordRetry.DEFAULT_MAX_RETRIES,
            Encode.forJava(ctx),
            Encode.forJava(String.valueOf(r.exception().getMessage())),
            r.exception());
      }
      resetState();
      consumer.commitSync();
    }
  }

  private void handleNextRecord() {
    PollingConsumer.Record<K, V> record = pending.next();
    String ctx =
        ConsumerRecordRetry.formatRecordContext(
            record.topic(), record.partition(), record.offset());
    ProcessResult r = ConsumerRecordRetry.processOnce(processorFactory.apply(record), ctx, log);
    switch (r.outcome()) {
      case SUCCESS, PERMANENT_FAILURE -> consumer.commitSync();
      case TRANSIENT_FAILURE -> {
        long delay = backoffCalculator.calculate(1);
        retryState = new RetryState<>(record, 1, System.currentTimeMillis() + delay);
        consumer.pause();
        log.warn(
            "Transient error processing record ({}) (attempt 1/{}), retrying in {}ms: {}",
            Encode.forJava(ctx),
            ConsumerRecordRetry.DEFAULT_MAX_RETRIES,
            delay,
            Encode.forJava(String.valueOf(r.exception().getMessage())));
      }
    }
  }
}
