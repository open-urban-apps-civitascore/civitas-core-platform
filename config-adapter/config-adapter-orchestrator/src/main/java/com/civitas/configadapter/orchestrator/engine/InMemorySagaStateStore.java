/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.orchestrator.engine;

import com.civitas.configadapter.model.saga.SagaContext;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link SagaStateStore} backed by a {@link ConcurrentHashMap}. Used
 * for unit testing — no external dependencies.
 */
public class InMemorySagaStateStore implements SagaStateStore {

  private final ConcurrentHashMap<String, SagaContext> store = new ConcurrentHashMap<>();

  @Override
  public void save(SagaContext saga) {
    store.put(saga.sagaId(), saga);
  }

  @Override
  public void remove(String sagaId) {
    store.remove(sagaId);
  }

  @Override
  public Optional<SagaContext> findById(String sagaId) {
    return Optional.ofNullable(store.get(sagaId));
  }

  @Override
  public Map<String, SagaContext> findActiveSagas() {
    return store.entrySet().stream()
        .filter(e -> !e.getValue().status().isTerminal())
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public boolean existsForDataset(String datasetId) {
    return store.values().stream()
        .anyMatch(saga -> datasetId.equals(saga.datasetId()) && !saga.status().isTerminal());
  }

  /** Returns the total number of sagas in the store (including terminal). For testing only. */
  public int size() {
    return store.size();
  }

  /** Clears the store. For testing only. */
  public void clear() {
    store.clear();
  }
}
