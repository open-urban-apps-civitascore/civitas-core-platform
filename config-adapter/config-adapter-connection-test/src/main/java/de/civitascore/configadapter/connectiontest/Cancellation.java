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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The open connections of one running test. When the test runs out of time they are closed from
 * outside, because an interrupt does not end a blocked socket read.
 */
final class Cancellation {

  private static final Logger LOG = LoggerFactory.getLogger(Cancellation.class);

  private final List<AutoCloseable> resources = new CopyOnWriteArrayList<>();
  private volatile boolean cancelled;

  /** Closes {@code resource} on cancellation, or at once if the test is already cancelled. */
  void register(AutoCloseable resource) {
    resources.add(resource);
    if (cancelled) {
      closeQuietly(resource);
    }
  }

  void cancel() {
    cancelled = true;
    resources.forEach(Cancellation::closeQuietly);
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException") // Every client fails differently on close
  private static void closeQuietly(AutoCloseable resource) {
    try {
      resource.close();
    } catch (Exception e) {
      LOG.debug("Closing a cancelled connection failed", e);
    }
  }
}
