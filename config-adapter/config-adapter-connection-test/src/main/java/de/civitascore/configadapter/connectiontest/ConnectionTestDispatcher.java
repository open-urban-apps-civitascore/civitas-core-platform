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

import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs each connection test on its own virtual thread and publishes exactly one result per test. A
 * test that runs out of time fails, and its connections are closed.
 */
final class ConnectionTestDispatcher implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionTestDispatcher.class);

  /** Left for the result to reach the requester before it stops waiting. */
  private static final Duration REPLY_MARGIN = Duration.ofSeconds(1);

  private final ConnectionTestService service;
  private final Consumer<ConnectionTestResult> publisher;
  private final Duration timeout;
  private final Clock clock;
  private final Semaphore permits;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  ConnectionTestDispatcher(
      ConnectionTestService service,
      Consumer<ConnectionTestResult> publisher,
      ConnectionTestSettings settings,
      Clock clock) {
    this.service = service;
    this.publisher = publisher;
    this.timeout = settings.timeout();
    this.clock = clock;
    this.permits = new Semaphore(settings.maxConcurrent());
  }

  /** Returns at once; the result is published when the test ends. */
  void dispatch(ConnectionTestRequest request) {
    String requestId = request.requestId();
    if (requestId == null || requestId.isBlank()) {
      LOG.warn("Dropping a connection test request without a request id");
      return;
    }
    Duration budget = budget(request.expiresAt());
    if (budget.isNegative() || budget.isZero()) {
      // The requester has stopped waiting, so a result would reach nobody.
      LOG.info("Dropping expired connection test request {}", Encode.forJava(requestId));
      return;
    }
    if (!permits.tryAcquire()) {
      publish(ConnectionTestResult.failure(requestId, Reason.BUSY, null, 0));
      return;
    }
    executor.execute(() -> supervise(request, budget));
  }

  /** The test's own limit, or less if the requester stops waiting before it. */
  private Duration budget(Instant expiresAt) {
    if (expiresAt == null) {
      return Duration.ZERO;
    }
    Duration left = Duration.between(clock.instant(), expiresAt).minus(REPLY_MARGIN);
    return left.compareTo(timeout) < 0 ? left : timeout;
  }

  private void supervise(ConnectionTestRequest request, Duration budget) {
    Cancellation cancellation = new Cancellation();
    AtomicBoolean started = new AtomicBoolean();
    long begin = System.nanoTime();
    Future<ConnectionTestResult> run =
        executor.submit(
            () -> {
              if (!started.compareAndSet(false, true)) {
                return null; // cancelled before it started; the supervisor released the permit
              }
              try {
                return service.test(request, cancellation);
              } finally {
                // Held until the worker really ends, so stuck tests count against the limit.
                permits.release();
              }
            });
    ConnectionTestResult result;
    try {
      result = run.get(budget.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      cancellation.cancel();
      run.cancel(true);
      if (started.compareAndSet(false, true)) {
        permits.release();
      }
      result =
          ConnectionTestResult.failure(
              request.requestId(),
              Reason.TIMEOUT,
              "The test did not finish within " + inWholeSeconds(budget) + ".",
              ConnectionTestService.millisSince(begin));
    } catch (ExecutionException e) {
      LOG.error(
          "Connection test {} failed unexpectedly",
          Encode.forJava(request.requestId()),
          ConnectionTestFailure.withoutText(e.getCause()));
      result =
          ConnectionTestResult.failure(
              request.requestId(),
              Reason.UNAVAILABLE,
              "The test failed unexpectedly.",
              ConnectionTestService.millisSince(begin));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      cancellation.cancel();
      run.cancel(true);
      return;
    }
    LOG.info(
        "Connection test {} ended with {} {} after {} ms",
        Encode.forJava(request.requestId()),
        result.outcome(),
        result.reason() == null ? "" : result.reason(),
        ConnectionTestService.millisSince(begin));
    publish(result);
  }

  /** Rounded, not cut: a budget taken from {@code expiresAt} is a little under a whole second. */
  private static String inWholeSeconds(Duration budget) {
    long seconds = Math.max(1, Math.round(budget.toMillis() / 1000.0));
    return seconds == 1 ? "1 second" : seconds + " seconds";
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException") // A failed publish must not kill a worker
  private void publish(ConnectionTestResult result) {
    try {
      publisher.accept(result);
    } catch (RuntimeException e) {
      LOG.error(
          "Publishing the result of connection test {} failed",
          Encode.forJava(result.requestId()),
          e);
    }
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }
}
