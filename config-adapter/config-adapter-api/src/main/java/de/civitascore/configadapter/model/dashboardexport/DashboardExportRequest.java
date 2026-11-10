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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Asks config-adapter to export one dashboard for sharing. The export reads the dashboard tool and
 * changes nothing in it.
 *
 * @param requestId correlates the {@link DashboardExportResult}
 * @param expiresAt when the requester stops waiting; a request received later is not run
 * @param externalDashboardId the dashboard tool's id of the dashboard, as the portal stores it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DashboardExportRequest(
    String requestId,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt,
    String externalDashboardId) {}
