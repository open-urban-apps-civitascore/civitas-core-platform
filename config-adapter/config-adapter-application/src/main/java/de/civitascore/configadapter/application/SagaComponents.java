/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import de.civitascore.event.handler.kafka.KafkaSagaCommandConsumer;
import java.util.Optional;

/** Holds the saga orchestration components created during application bootstrap. */
record SagaComponents(
    Optional<KafkaSagaCommandConsumer> commandConsumer, Optional<OrchestratorPair> orchestrator) {}
