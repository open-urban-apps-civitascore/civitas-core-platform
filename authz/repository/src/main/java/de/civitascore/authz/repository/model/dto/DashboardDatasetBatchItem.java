package de.civitascore.authz.repository.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One resolved entry in a {@code POST /api/v1/dashboard-dataset/batch} response.
 *
 * <p>The response list omits any requested {@code externalDashboardId} that doesn't match a known
 * dashboard, rather than including it with a null {@link #civitasDatasetId} — mirrors the 404
 * semantics of the single-lookup endpoint ({@link DashboardDatasetResponse}): "unknown" is absence,
 * not a null value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDatasetBatchItem {
  private String externalDashboardId;
  private UUID civitasDatasetId;
}
