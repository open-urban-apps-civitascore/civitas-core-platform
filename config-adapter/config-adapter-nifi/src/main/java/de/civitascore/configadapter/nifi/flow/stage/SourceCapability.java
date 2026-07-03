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

/**
 * What a source stage emits or supports. Declared by the {@link SourceStage}, consumed by the
 * orchestrator (convert insertion, cron guard) and by {@link SinkStage#requiredSourceCapabilities}.
 */
public enum SourceCapability {
  /** Emits NiFi records directly, so no ConvertRecord step is needed. */
  EMITS_RECORDS,
  /** Delivers the SensorThings envelope ({@code $.things}/{@code $.observations}). */
  EMITS_STA_ENVELOPE,
  /** Pull-based: a cron schedule on the entry processor is meaningful. */
  SUPPORTS_CRON
}
