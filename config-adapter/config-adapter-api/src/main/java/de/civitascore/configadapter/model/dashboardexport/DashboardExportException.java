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

import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult.Reason;

/**
 * A dashboard export that fails for a known {@link Reason}. Its message goes back to the requester,
 * so it must hold no secret and no platform internal.
 */
public class DashboardExportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final Reason reason;

  public DashboardExportException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
