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
 * @param stopSources relationships on which the stage reports a fatal error, which the flow builder
 *     routes to the stop sink of the Pipeline
 */
public record StageResult(
    List<Processor> chain, List<Processor> failureSources, List<StopSource> stopSources) {
  public StageResult {
    chain = List.copyOf(chain);
    if (chain.isEmpty()) {
      // enforce the documented invariant here instead of failing later with a bare
      // NoSuchElementException from exit()
      throw new IllegalArgumentException("a stage must contribute at least one processor");
    }
    failureSources = List.copyOf(failureSources);
    stopSources = List.copyOf(stopSources);
  }

  /** A stage that reports no fatal error. */
  public StageResult(List<Processor> chain, List<Processor> failureSources) {
    this(chain, failureSources, List.of());
  }

  /** The processor the next stage connects to. */
  public Processor exit() {
    return chain.getLast();
  }
}
