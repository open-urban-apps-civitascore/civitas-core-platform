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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.flow.SourceType;
import java.util.List;
import java.util.Optional;

/**
 * A self-describing pipeline source. One implementation per {@link SourceType}, owning both halves
 * of its lifecycle: plan-time binding (validation, credential decryption, property maps) and
 * build-time wiring (entry processor, source-side controller services).
 */
public interface SourceStage {

  /** The registry key. */
  SourceType type();

  /** The payload form this source emits. */
  PayloadForm output();

  /**
   * Whether a cron schedule on the entry processor is meaningful. Only pull-based sources accept
   * one; push-based sources self-trigger and reject a cron via {@link #cronRejectionMessage()}.
   */
  default boolean acceptsSchedule() {
    return false;
  }

  /**
   * The rejection message when the graph carries a cron trigger but this source does not accept a
   * schedule. Never consulted for sources with {@link #acceptsSchedule()}.
   */
  default String cronRejectionMessage() {
    return "cron scheduling is not supported for source type " + type();
  }

  /**
   * Plan-time half: validates the datasource, decrypts credentials, and fills the property maps.
   * Secrets go only into {@link PlanContext#putSensitive}, never into snapshot-bound properties.
   *
   * @throws FatalAdapterException if the datasource is invalid or unsafe to deploy
   */
  void bind(Datasource source, PlanContext out) throws FatalAdapterException;

  /** Build-time half, phase A: registers source-side controller services (before processors). */
  default void registerControllerServices(BuildContext ctx) throws FatalAdapterException {}

  /** Optional deployment-owned Parameter Context required by this source configuration. */
  default Optional<ParameterContextSpec> parameterContext(BuildContext ctx) {
    return Optional.empty();
  }

  /**
   * Build-time half, phase B: loads and configures the entry processor(s). Returns without
   * appending — the orchestrator owns array order.
   */
  StageResult build(BuildContext ctx) throws FatalAdapterException;

  /** Secret-free declaration of a Parameter Context that must be populated outside the flow. */
  record ParameterContextSpec(String name, List<ParameterSpec> parameters) {}

  /** One parameter declaration; values are deliberately not part of the flow build model. */
  record ParameterSpec(String name, String description, boolean sensitive) {}
}
