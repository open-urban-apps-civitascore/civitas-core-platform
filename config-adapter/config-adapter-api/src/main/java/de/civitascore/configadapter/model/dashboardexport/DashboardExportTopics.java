/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dashboardexport;

/**
 * The Kafka topics of the dashboard export. Both carry plain JSON keyed by the request id: {@link
 * DashboardExportRequest} on {@link #REQUEST}, {@link DashboardExportResult} on {@link #RESULT}.
 */
public final class DashboardExportTopics {

  public static final String REQUEST = "de.civitascore.dashboardexport.request";
  public static final String RESULT = "de.civitascore.dashboardexport.result";

  private DashboardExportTopics() {}
}
