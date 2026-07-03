/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import java.util.List;

/**
 * The processors a source or transform stage contributes to the chain. Stages return their
 * processors instead of appending them: the orchestrator owns the append/connect loop so the
 * processor and connection array order — and with it the snapshot bytes — stays in one place.
 *
 * @param chain the stage's processors in chain order (never empty)
 * @param failureSources processors whose {@code failure} relationship the sink stage must route to
 *     the shared error sink
 */
public record StageResult(List<Processor> chain, List<Processor> failureSources) {
  public StageResult {
    chain = List.copyOf(chain);
    failureSources = List.copyOf(failureSources);
  }

  /** The processor the next stage connects to. */
  public Processor exit() {
    return chain.getLast();
  }
}
