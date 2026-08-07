package de.civitascore.authz.repository.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO resolving a single dashboard to the Civitas dataset it belongs to.
 *
 * <p>Returned by the {@code /api/v1/dashboard-dataset/{externalDashboardId}} endpoint. Unlike
 * {@link DatasetPoolResponse#getPoolId()}, {@link #civitasDatasetId} is never {@code null} for a
 * row that exists at all — a dashboard's dataset FK is {@code NOT NULL} in the platform schema, so
 * "unknown dashboard" is expressed as a 404, not a null field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDatasetResponse {
  private UUID civitasDatasetId;
}
