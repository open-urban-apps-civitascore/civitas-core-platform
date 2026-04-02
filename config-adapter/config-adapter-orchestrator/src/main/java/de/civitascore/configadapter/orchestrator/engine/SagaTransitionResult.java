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
import java.util.List;

/**
 * Result of a state machine transition: the updated saga context plus actions to execute. Returned
 * by all {@link SagaStateMachine} methods.
 *
 * @param context the updated saga context after the transition
 * @param actions ordered list of actions the engine must dispatch
 */
public record SagaTransitionResult(SagaContext context, List<SagaAction> actions) {

  /** Convenience factory for a single action. */
  public static SagaTransitionResult of(SagaContext context, SagaAction... actions) {
    return new SagaTransitionResult(context, List.of(actions));
  }
}
