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

import de.civitascore.configadapter.adapter.DashboardExporter;
import de.civitascore.configadapter.flowable.common.FlowableSagaOrchestrator;
import java.util.Optional;

/**
 * Holds the saga orchestration components created during application bootstrap. The saga engine is
 * the Flowable orchestrator (the legacy custom orchestrator has been removed). In production {@link
 * SagaComponentFactory#create} always supplies a Flowable orchestrator (or fails fast); the {@code
 * Optional} exists so the construction-only bootstrap tests can inject an empty value to skip the
 * costly engine bootstrap (see {@code ApplicationTest}), and so {@link Application#run} degrades
 * gracefully if no orchestrator is wired.
 *
 * <p>{@code dashboardExporter} is the saga handler that exports dashboards for sharing; it is empty
 * when no dashboard tool is deployed, and then no dashboard export consumer runs.
 */
record SagaComponents(
    Optional<FlowableSagaOrchestrator> flowableOrchestrator,
    Optional<DashboardExporter> dashboardExporter) {

  /** Components without a dashboard exporter. */
  SagaComponents(Optional<FlowableSagaOrchestrator> flowableOrchestrator) {
    this(flowableOrchestrator, Optional.empty());
  }

  static SagaComponents flowable(
      FlowableSagaOrchestrator flowable, Optional<DashboardExporter> dashboardExporter) {
    return new SagaComponents(Optional.of(flowable), dashboardExporter);
  }
}
