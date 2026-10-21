/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Outcome;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConnectionTestDispatcherTest {

  static {
    Awaitility.setDefaultPollDelay(Duration.ZERO);
  }

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  /** How long the backend waits for a result. */
  private static final Instant EXPIRES = NOW.plusSeconds(20);

  private final List<ConnectionTestResult> published = new CopyOnWriteArrayList<>();
  private final AtomicInteger probes = new AtomicInteger();
  private final CountDownLatch release = new CountDownLatch(1);
  private final CountDownLatch cancelledLatch = new CountDownLatch(1);
  private final AtomicInteger blocked = new AtomicInteger();
  private final AtomicInteger resourcesClosed = new AtomicInteger();
  private final AtomicInteger hangsEnded = new AtomicInteger();
  private ConnectionTestDispatcher dispatcher;

  @AfterEach
  void tearDown() {
    release.countDown();
    if (dispatcher != null) {
      dispatcher.close();
    }
  }

  /**
   * A prober that is steered by the api call: "block" waits until released, "hang" waits until its
   * registered resource is closed, anything else answers at once.
   */
  private ConnectionProber prober() {
    return new ConnectionProber() {
      @Override
      public String connectionType() {
        return "sql";
      }

      @Override
      public ProbeResult probe(
          Map<String, Object> connector, String apiCall, Cancellation cancellation)
          throws ConnectionTestFailure {
        probes.incrementAndGet();
        try {
          if ("block".equals(apiCall)) {
            blocked.incrementAndGet();
            release.await();
          } else if ("hang".equals(apiCall)) {
            cancellation.register(
                () -> {
                  resourcesClosed.incrementAndGet();
                  cancelledLatch.countDown();
                });
            try {
              cancelledLatch.await();
            } finally {
              // the dispatcher closes the resource and interrupts; either ends the hang
              hangsEnded.incrementAndGet();
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new ConnectionTestFailure("interrupted");
        }
        return ProbeResult.success("ok", null);
      }
    };
  }

  private ConnectionTestDispatcher dispatcher(Duration timeout, int maxConcurrent) {
    ConnectionTestService service =
        new ConnectionTestService(List.of(prober()), connector -> connector);
    dispatcher =
        new ConnectionTestDispatcher(
            service, published::add, new ConnectionTestSettings(timeout, maxConcurrent), CLOCK);
    return dispatcher;
  }

  private static ConnectionTestRequest request(String id, Instant expiresAt, String apiCall) {
    return new ConnectionTestRequest(id, expiresAt, Map.of("connectionType", "sql"), apiCall);
  }

  @Test
  void requestWithoutIdIsDropped() throws InterruptedException {
    ConnectionTestDispatcher d = dispatcher(Duration.ofSeconds(5), 2);

    d.dispatch(request(null, EXPIRES, null));
    d.dispatch(request("  ", EXPIRES, null));

    // Negative assertion: give a wrongly started test time to publish.
    Thread.sleep(200);
    assertTrue(published.isEmpty());
    assertEquals(0, probes.get());
  }

  @Test
  void requestWithoutExpiryIsDropped() throws InterruptedException {
    ConnectionTestDispatcher d = dispatcher(Duration.ofSeconds(5), 2);

    d.dispatch(request("r1", null, null));

    // Negative assertion: give a wrongly started test time to publish.
    Thread.sleep(200);
    assertTrue(published.isEmpty());
    assertEquals(0, probes.get());
  }

  @Test
  void requestWhoseRequesterNoLongerWaitsIsDropped() throws InterruptedException {
    ConnectionTestDispatcher d = dispatcher(Duration.ofSeconds(5), 2);

    d.dispatch(request("r1", NOW.minusSeconds(1), null));
    // Expires within the reply margin, which leaves the test no time.
    d.dispatch(request("r2", NOW.plusSeconds(1), null));

    // Negative assertion: give a wrongly started test time to publish.
    Thread.sleep(200);
    assertTrue(published.isEmpty());
    assertEquals(0, probes.get());
  }

  @Test
  void requestThatExpiresBeforeTheTimeoutGetsTheShorterTime() {
    ConnectionTestDispatcher d = dispatcher(Duration.ofSeconds(30), 2);

    d.dispatch(request("r1", NOW.plusMillis(1300), "hang"));

    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 1);
    assertEquals(Reason.TIMEOUT, published.get(0).reason());
  }

  @Test
  void timeoutMessageNamesTheTimeTheTestHadRounded() {
    ConnectionTestDispatcher d = dispatcher(Duration.ofSeconds(30), 2);

    d.dispatch(request("r1", NOW.plusMillis(2500), "hang"));

    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 1);
    assertEquals("The test did not finish within 2 seconds.", published.get(0).message());
  }

  @Test
  void normalRequestPublishesExactlyOneResult() throws InterruptedException {
    ConnectionTestDispatcher d = dispatcher(Duration.ofSeconds(5), 2);

    d.dispatch(request("r1", EXPIRES, null));

    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 1);
    assertEquals("r1", published.get(0).requestId());
    assertEquals(Outcome.SUCCESS, published.get(0).outcome());
    assertNull(published.get(0).reason());
    // Negative assertion: no second result follows.
    Thread.sleep(200);
    assertEquals(1, published.size());
  }

  @Test
  void requestBeyondConcurrencyLimitIsAnsweredAtOnce() {
    ConnectionTestDispatcher d = dispatcher(Duration.ofSeconds(30), 2);
    d.dispatch(request("r1", EXPIRES, "block"));
    d.dispatch(request("r2", EXPIRES, "block"));
    await().atMost(Duration.ofSeconds(5)).until(() -> blocked.get() == 2);

    d.dispatch(request("r3", EXPIRES, null));

    assertEquals(1, published.size());
    assertEquals("r3", published.get(0).requestId());
    assertEquals(Outcome.FAILURE, published.get(0).outcome());
    assertEquals(Reason.BUSY, published.get(0).reason());
    assertEquals(Reason.BUSY.defaultMessage(), published.get(0).message());

    release.countDown();
    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 3);
    assertEquals(2, published.stream().filter(r -> r.outcome() == Outcome.SUCCESS).count());
  }

  @Test
  void permitIsFreeAgainAfterRunningTestsEnd() {
    ConnectionTestDispatcher d = dispatcher(Duration.ofSeconds(30), 1);
    d.dispatch(request("r1", EXPIRES, "block"));
    await().atMost(Duration.ofSeconds(5)).until(() -> blocked.get() == 1);
    release.countDown();
    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 1);

    d.dispatch(request("r2", EXPIRES, null));

    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 2);
    assertEquals(Outcome.SUCCESS, published.get(1).outcome());
  }

  @Test
  void testExceedingTimeoutIsFailedAndItsConnectionsClosed() throws InterruptedException {
    ConnectionTestDispatcher d = dispatcher(Duration.ofMillis(300), 2);

    d.dispatch(request("r1", EXPIRES, "hang"));

    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 1);
    ConnectionTestResult result = published.get(0);
    assertEquals("r1", result.requestId());
    assertEquals(Outcome.FAILURE, result.outcome());
    assertEquals(Reason.TIMEOUT, result.reason());
    assertTrue(result.message().contains("did not finish within"), result.message());
    assertTrue(cancelledLatch.await(5, TimeUnit.SECONDS));
    assertEquals(1, resourcesClosed.get());
    // Negative assertion: the cancelled test must not publish a second result.
    Thread.sleep(200);
    assertEquals(1, published.size());
  }

  @Test
  void permitIsReleasedAfterTimedOutTestEnds() {
    ConnectionTestDispatcher d = dispatcher(Duration.ofMillis(300), 1);
    d.dispatch(request("r1", EXPIRES, "hang"));
    // The timeout closes the hung test's resource, which ends its worker and frees the permit.
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> published.size() == 1 && hangsEnded.get() == 1);

    d.dispatch(request("r2", EXPIRES, null));

    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 2);
    assertEquals("r2", published.get(1).requestId());
    assertEquals(Outcome.SUCCESS, published.get(1).outcome());
  }

  @Test
  void publisherFailureDoesNotBreakLaterTests() {
    ConnectionTestService service =
        new ConnectionTestService(List.of(prober()), connector -> connector);
    AtomicInteger calls = new AtomicInteger();
    dispatcher =
        new ConnectionTestDispatcher(
            service,
            result -> {
              if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("kafka down");
              }
              published.add(result);
            },
            new ConnectionTestSettings(Duration.ofSeconds(5), 1),
            CLOCK);

    dispatcher.dispatch(request("r1", EXPIRES, null));
    await().atMost(Duration.ofSeconds(5)).until(() -> calls.get() == 1);
    dispatcher.dispatch(request("r2", EXPIRES, null));

    await().atMost(Duration.ofSeconds(5)).until(() -> published.size() == 1);
    assertEquals("r2", published.get(0).requestId());
  }
}
