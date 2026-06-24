package de.civitascore.authz.repository.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the datapool a dataset belongs to.
 *
 * <p>Returned by the {@code /api/v1/dataset-pool/{datasetId}} endpoint and consumed by OPA to apply
 * Epic 1 union inheritance (a DATAPOOL-scoped grant applies to every dataset in that pool).
 *
 * <p>{@link #poolId} is {@code null} when the dataset is not assigned to any pool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetPoolResponse {
  private UUID poolId;
}
