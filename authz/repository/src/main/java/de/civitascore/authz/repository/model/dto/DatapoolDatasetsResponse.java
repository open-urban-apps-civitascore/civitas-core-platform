package de.civitascore.authz.repository.model.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for {@code POST /api/v1/dataset-pool/datasets}.
 *
 * <p>A flat, deduplicated union of dataset ids across every requested pool — callers only ever need
 * "is this dataset covered by any of my qualifying pools," not a per-pool breakdown.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatapoolDatasetsResponse {
  private List<UUID> datasetIds;
}
