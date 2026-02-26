/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.engine;

import de.civitascore.configadapter.model.saga.SagaContext;
import java.util.Map;
import java.util.Optional;

/**
 * Abstraction for saga state persistence. Every state transition is persisted before the next
 * action is taken, ensuring crash recovery.
 *
 * <p>Implementations: {@link InMemorySagaStateStore} (unit tests), {@code KafkaSagaStateStore}
 * (production — Kafka compacted topic).
 */
public interface SagaStateStore {

  /** Persist the current saga state. Called on every state transition. */
  void save(SagaContext saga);

  /**
   * Remove a completed saga from the store (tombstone). Called after the saga reaches a terminal
   * state and the result event has been sent.
   */
  void remove(String sagaId);

  /** Find a saga by its ID. */
  Optional<SagaContext> findById(String sagaId);

  /** Returns all active (non-terminal) sagas. Used for crash recovery on startup. */
  Map<String, SagaContext> findActiveSagas();

  /** Returns true if there is an active saga for the given dataset ID. Used for duplicate check. */
  boolean existsForDataset(String datasetId);
}
