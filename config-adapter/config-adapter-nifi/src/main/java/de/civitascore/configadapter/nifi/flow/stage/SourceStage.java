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
import java.util.Set;

/**
 * A self-describing pipeline source. One implementation per {@link SourceType}, owning both halves
 * of its lifecycle: plan-time binding (validation, credential decryption, property maps) and
 * build-time wiring (entry processor, source-side controller services).
 */
public interface SourceStage {

  /** The registry key. */
  SourceType type();

  Set<SourceCapability> capabilities();

  /**
   * The rejection message when the graph carries a cron trigger but this source lacks {@link
   * SourceCapability#SUPPORTS_CRON}.
   */
  String cronRejectionMessage();

  /**
   * Plan-time half: validates the datasource, decrypts credentials, and fills the property maps.
   * Secrets go only into {@link PlanContext#putSensitive}, never into snapshot-bound properties.
   *
   * @throws FatalAdapterException if the datasource is invalid or unsafe to deploy
   */
  void bind(Datasource source, PlanContext out) throws FatalAdapterException;

  /** Build-time half, phase A: registers source-side controller services (before processors). */
  default void registerControllerServices(BuildContext ctx) throws FatalAdapterException {}

  /**
   * Build-time half, phase B: loads and configures the entry processor(s). Returns without
   * appending — the orchestrator owns array order.
   */
  StageResult build(BuildContext ctx) throws FatalAdapterException;
}
