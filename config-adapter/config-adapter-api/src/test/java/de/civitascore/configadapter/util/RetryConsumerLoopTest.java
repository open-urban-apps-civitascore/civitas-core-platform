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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RetryConsumerLoopTest {

  static {
    // Awaitility's poll delay defaults to the poll interval, delaying the first condition check.
    // Zeroing it lets conditions that already hold return immediately.
    Awaitility.setDefaultPollDelay(Duration.ZERO);
  }

  private static final Logger LOG = LoggerFactory.getLogger(RetryConsumerLoopTest.class);
  private static final BackoffCalculator FAST_BACKOFF = new BackoffCalculator(1L, 10L);

  @SuppressWarnings("unchecked")
  private final PollingConsumer<String, byte[]> consumer = mock(PollingConsumer.class);

  private RetryConsumerLoop<String, byte[]> loop;

  @BeforeEach
  void setUp() {
    // Default: polls return empty
    when(consumer.poll(any(Duration.class))).thenReturn(Collections.emptyIterator());
  }

  @AfterEach
  void tearDown() {
    if (loop != null && loop.isRunning()) {
      loop.stop();
    }
  }

  private PollingConsumer.Record<String, byte[]> record(String topic, byte[] value) {
    return new PollingConsumer.Record<>(topic, 0, 0L, "key", value);
  }

  @Test
  @DisplayName("start_startsVirtualThread")
  void start_startsVirtualThread() {
    loop = new RetryConsumerLoop<>(consumer, rec -> () -> {}, FAST_BACKOFF, LOG, "test-consumer");

    assertTrue(loop.start());
    assertTrue(loop.isRunning());

    loop.stop();
    assertFalse(loop.isRunning());
  }

  @Test
  @DisplayName("start_idempotent_secondCallReturnsFalse")
  void start_idempotent_secondCallReturnsFalse() {
    loop = new RetryConsumerLoop<>(consumer, rec -> () -> {}, FAST_BACKOFF, LOG, "test-consumer");

    assertTrue(loop.start());
    assertFalse(loop.start());

    loop.stop();
  }

  @Test
  @DisplayName("consumeLoop_successfulRecord_commitsSync")
  void consumeLoop_successfulRecord_commitsSync() {
    AtomicInteger processedCount = new AtomicInteger();
    var rec = record("test-topic", "data".getBytes());

    when(consumer.poll(any(Duration.class)))
        .thenReturn(singletonIterator(rec))
        .thenReturn(Collections.emptyIterator());

    loop =
        new RetryConsumerLoop<>(
            consumer,
            r -> () -> processedCount.incrementAndGet(),
            FAST_BACKOFF,
            LOG,
            "test-consumer");
    loop.start();

    await().atMost(2, SECONDS).untilAsserted(() -> verify(consumer).commitSync());
    loop.stop();

    assertTrue(processedCount.get() >= 1);
  }

  @Test
  @DisplayName("consumeLoop_permanentFailure_commitsSync")
  void consumeLoop_permanentFailure_commitsSync() {
    var rec = record("test-topic", "data".getBytes());

    when(consumer.poll(any(Duration.class)))
        .thenReturn(singletonIterator(rec))
        .thenReturn(Collections.emptyIterator());

    loop =
        new RetryConsumerLoop<>(
            consumer,
            r ->
                () -> {
                  throw new IOException("permanent");
                },
            FAST_BACKOFF,
            LOG,
            "test-consumer");
    loop.start();

    await().atMost(2, SECONDS).untilAsserted(() -> verify(consumer).commitSync());
    loop.stop();

    // Permanent failure → no pause, no resume
    verify(consumer, never()).pause();
  }

  @Test
  @DisplayName("consumeLoop_transientFailure_pausesThenRetriesThenCommits")
  void consumeLoop_transientFailure_pausesThenRetriesThenCommits() {
    AtomicInteger attempts = new AtomicInteger();
    var rec = record("test-topic", "data".getBytes());

    when(consumer.poll(any(Duration.class)))
        .thenReturn(singletonIterator(rec))
        .thenReturn(Collections.emptyIterator());

    loop =
        new RetryConsumerLoop<>(
            consumer,
            r ->
                () -> {
                  if (attempts.incrementAndGet() <= 1) {
                    throw new RuntimeException("transient");
                  }
                },
            FAST_BACKOFF,
            LOG,
            "test-consumer");
    loop.start();

    await().atMost(2, SECONDS).untilAsserted(() -> verify(consumer).commitSync());
    loop.stop();

    verify(consumer).pause();
    verify(consumer, atLeast(1)).resume();
    assertTrue(attempts.get() >= 2);
  }

  @Test
  @DisplayName("consumeLoop_maxRetriesExceeded_resumesAndCommits")
  void consumeLoop_maxRetriesExceeded_resumesAndCommits() {
    var rec = record("test-topic", "data".getBytes());

    when(consumer.poll(any(Duration.class)))
        .thenReturn(singletonIterator(rec))
        .thenReturn(Collections.emptyIterator());

    loop =
        new RetryConsumerLoop<>(
            consumer,
            r ->
                () -> {
                  throw new RuntimeException("always fails");
                },
            FAST_BACKOFF,
            LOG,
            "test-consumer");
    loop.start();

    await().atMost(2, SECONDS).untilAsserted(() -> verify(consumer).commitSync());
    loop.stop();

    verify(consumer).pause();
    verify(consumer, atLeast(1)).resume();
  }

  @Test
  @DisplayName("consumeLoop_poisonPillThenValid_commitsTwice")
  void consumeLoop_poisonPillThenValid_commitsTwice() {
    var badRec = record("test-topic", "bad".getBytes());
    var goodRec = record("test-topic", "good".getBytes());

    Iterator<PollingConsumer.Record<String, byte[]>> twoRecords =
        new Iterator<>() {
          private int index = 0;
          private final PollingConsumer.Record<String, byte[]>[] items =
              new PollingConsumer.Record[] {badRec, goodRec};

          @Override
          public boolean hasNext() {
            return index < items.length;
          }

          @Override
          public PollingConsumer.Record<String, byte[]> next() {
            return items[index++];
          }
        };

    AtomicInteger processed = new AtomicInteger();

    when(consumer.poll(any(Duration.class)))
        .thenReturn(twoRecords)
        .thenReturn(Collections.emptyIterator());

    loop =
        new RetryConsumerLoop<>(
            consumer,
            r ->
                () -> {
                  if (new String(r.value()).equals("bad")) {
                    throw new IOException("poison");
                  }
                  processed.incrementAndGet();
                },
            FAST_BACKOFF,
            LOG,
            "test-consumer");
    loop.start();

    await().atMost(2, SECONDS).untilAsserted(() -> verify(consumer, times(2)).commitSync());
    loop.stop();

    assertTrue(processed.get() >= 1);
  }

  @Test
  @DisplayName("consumeLoop_runtimeExceptionInLoop_continuesRunning")
  void consumeLoop_runtimeExceptionInLoop_continuesRunning() {
    // First poll throws RuntimeException at the loop level (not record-level),
    // second poll returns empty — loop should survive.
    when(consumer.poll(any(Duration.class)))
        .thenThrow(new RuntimeException("connection error"))
        .thenReturn(Collections.emptyIterator());

    loop = new RetryConsumerLoop<>(consumer, rec -> () -> {}, FAST_BACKOFF, LOG, "test-consumer");
    loop.start();

    // Loop should continue after the RuntimeException
    await()
        .atMost(2, SECONDS)
        .untilAsserted(() -> verify(consumer, atLeast(2)).poll(any(Duration.class)));
    loop.stop();

    // Loop survived the RuntimeException
    assertFalse(loop.isRunning());
  }

  @Test
  @DisplayName("resetState_clearsRetryAndPending")
  void resetState_clearsRetryAndPending() {
    var rec = record("test-topic", "data".getBytes());

    // Set up: first record triggers transient failure → enters retry state
    // Then we call resetState which should clear everything
    // Then the loop should poll for new records and eventually commit
    AtomicInteger attempts = new AtomicInteger();

    when(consumer.poll(any(Duration.class)))
        .thenReturn(singletonIterator(rec))
        .thenReturn(Collections.emptyIterator());

    // Trigger resetState after pause is called
    doAnswer(
            inv -> {
              if (attempts.get() == 1) {
                loop.resetState();
              }
              return null;
            })
        .when(consumer)
        .pause();

    loop =
        new RetryConsumerLoop<>(
            consumer,
            r ->
                () -> {
                  attempts.incrementAndGet();
                  throw new RuntimeException("transient");
                },
            FAST_BACKOFF,
            LOG,
            "test-consumer");
    loop.start();

    // After resetState, the loop resumes polling for new records — it won't commit for the
    // reset record, but it will continue running
    await()
        .atMost(2, SECONDS)
        .untilAsserted(() -> verify(consumer, atLeast(2)).poll(any(Duration.class)));
    loop.stop();
  }

  @Test
  @DisplayName("stop_duringRetryWait_exitsLoop")
  void stop_duringRetryWait_exitsLoop() {
    var rec = record("test-topic", "data".getBytes());

    when(consumer.poll(any(Duration.class)))
        .thenReturn(singletonIterator(rec))
        .thenReturn(Collections.emptyIterator());

    loop =
        new RetryConsumerLoop<>(
            consumer,
            r ->
                () -> {
                  throw new RuntimeException("transient");
                },
            FAST_BACKOFF,
            LOG,
            "test-consumer");
    loop.start();

    // Wait until the loop enters retry-wait (pause has been called)
    await().atMost(2, SECONDS).untilAsserted(() -> verify(consumer).pause());

    // Stop while the loop is in the retry-wait phase (polling at 100ms heartbeats)
    loop.stop();

    assertFalse(loop.isRunning());
  }

  private <T> Iterator<T> singletonIterator(T item) {
    return new Iterator<>() {
      private boolean consumed = false;

      @Override
      public boolean hasNext() {
        return !consumed;
      }

      @Override
      public T next() {
        consumed = true;
        return item;
      }
    };
  }
}
