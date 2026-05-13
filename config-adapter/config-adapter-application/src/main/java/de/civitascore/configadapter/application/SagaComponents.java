/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import de.civitascore.configadapter.flowable.common.FlowableSagaOrchestrator;
import de.civitascore.event.handler.kafka.KafkaSagaCommandConsumer;
import java.util.Optional;

/**
 * Holds the saga orchestration components created during application bootstrap. Supports either the
 * custom orchestrator (with OrchestratorPair + KafkaSagaCommandConsumer) or the Flowable
 * orchestrator.
 */
record SagaComponents(
    Optional<KafkaSagaCommandConsumer> commandConsumer,
    Optional<OrchestratorPair> orchestrator,
    Optional<FlowableSagaOrchestrator> flowableOrchestrator) {

  SagaComponents(
      Optional<KafkaSagaCommandConsumer> commandConsumer,
      Optional<OrchestratorPair> orchestrator) {
    this(commandConsumer, orchestrator, Optional.empty());
  }

  static SagaComponents flowable(FlowableSagaOrchestrator flowable) {
    return new SagaComponents(Optional.empty(), Optional.empty(), Optional.of(flowable));
  }

  static SagaComponents empty() {
    return new SagaComponents(Optional.empty(), Optional.empty(), Optional.empty());
  }
}
