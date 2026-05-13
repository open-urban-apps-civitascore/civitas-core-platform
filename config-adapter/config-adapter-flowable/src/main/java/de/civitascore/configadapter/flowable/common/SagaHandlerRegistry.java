/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding {@link SagaCommandHandler} instances keyed by adapter name. Made available to
 * Flowable JavaDelegates via the engine's bean map.
 */
public final class SagaHandlerRegistry {

  private final Map<String, SagaCommandHandler> handlers = new ConcurrentHashMap<>();

  /** Registers a handler. Uses {@link SagaCommandHandler#adapter()} as the key. */
  public void register(SagaCommandHandler handler) {
    handlers.put(handler.adapter(), handler);
  }

  /**
   * Returns the handler for the given adapter name.
   *
   * @throws IllegalArgumentException if no handler is registered for the adapter
   */
  public SagaCommandHandler getHandler(String adapterName) {
    SagaCommandHandler handler = handlers.get(adapterName);
    if (handler == null) {
      throw new IllegalArgumentException(
          "No SagaCommandHandler registered for adapter: " + adapterName);
    }
    return handler;
  }

  /** Returns the set of registered adapter names. */
  public Set<String> adapterNames() {
    return Set.copyOf(handlers.keySet());
  }
}
