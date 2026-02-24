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

/**
 * Abstraction for dispatching saga actions. The engine produces actions; this interface sends them
 * to the appropriate destination (Kafka topics in production, in-memory list in tests).
 *
 * <p>Implementations: {@code KafkaSagaActionDispatcher} (production), test doubles for unit tests.
 */
public interface SagaActionDispatcher {

  /**
   * Dispatch a saga action. Implementations translate each action type to the appropriate external
   * call (e.g. publish to Kafka topic, persist state, send manual intervention alert).
   *
   * <p>Note: {@link SagaAction.PersistState} is handled by the engine directly via the {@link
   * SagaStateStore} — dispatchers do not need to handle it.
   */
  void dispatch(SagaAction action);
}
