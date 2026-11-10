/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.adapter;

import de.civitascore.configadapter.model.dashboardexport.DashboardExportException;
import java.util.Map;

/**
 * Exports a dashboard for sharing. A {@link SagaCommandHandler} that owns a dashboard tool
 * implements it; the application then answers dashboard export requests through that handler, which
 * holds the tool's session.
 */
public interface DashboardExporter {

  /**
   * Exports one dashboard and removes what must stay on this instance.
   *
   * @param externalDashboardId the dashboard tool's id of the dashboard
   * @return the cleaned export: {@code tool} and {@code files}, each file path without the export's
   *     root folder mapped to the parsed file
   * @throws DashboardExportException if the tool does not know the dashboard or the dashboard holds
   *     what must not be shared
   */
  Map<String, Object> exportDashboard(String externalDashboardId);
}
