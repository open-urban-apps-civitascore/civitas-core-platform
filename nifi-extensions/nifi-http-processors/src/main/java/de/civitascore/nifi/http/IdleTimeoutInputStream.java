/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A response body whose reads each end after the idle timeout at the latest. The web client's Read
 * Timeout is the JDK request timeout, so without this a stalled server holds the run until it
 * closes the connection.
 */
final class IdleTimeoutInputStream extends FilterInputStream {

  /**
   * Shared by every run; its thread ends while no read waits, so it needs no processor lifecycle.
   */
  private static final ScheduledThreadPoolExecutor TIMER = timer();

  private final Duration timeout;
  private final Runnable abort;
  private volatile boolean timedOut;

  /**
   * @param abort closes the response, so that a blocked read throws
   */
  IdleTimeoutInputStream(InputStream body, Duration timeout, Runnable abort) {
    super(body);
    this.timeout = timeout;
    this.abort = abort;
  }

  @Override
  public int read() throws IOException {
    ScheduledFuture<?> alarm = arm();
    try {
      return super.read();
    } catch (IOException e) {
      throw timedOut ? timeout(e) : e;
    } finally {
      alarm.cancel(false);
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    ScheduledFuture<?> alarm = arm();
    try {
      return super.read(buffer, offset, length);
    } catch (IOException e) {
      throw timedOut ? timeout(e) : e;
    } finally {
      alarm.cancel(false);
    }
  }

  private ScheduledFuture<?> arm() {
    return TIMER.schedule(
        () -> {
          timedOut = true;
          abort.run();
        },
        timeout.toNanos(),
        TimeUnit.NANOSECONDS);
  }

  private static ScheduledThreadPoolExecutor timer() {
    ScheduledThreadPoolExecutor timer =
        new ScheduledThreadPoolExecutor(
            1, Thread.ofPlatform().daemon().name("GetHttpApiCall body read timer").factory());
    timer.setKeepAliveTime(1, TimeUnit.MINUTES);
    timer.allowCoreThreadTimeOut(true);
    timer.setRemoveOnCancelPolicy(true);
    return timer;
  }

  private IOException timeout(IOException cause) {
    HttpTimeoutException timeoutException =
        new HttpTimeoutException("no body bytes within " + timeout);
    timeoutException.initCause(cause);
    return timeoutException;
  }
}
