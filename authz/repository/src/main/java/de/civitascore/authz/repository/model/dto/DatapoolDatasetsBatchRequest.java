package de.civitascore.authz.repository.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/dataset-pool/datasets}.
 *
 * <p>The reverse of {@link DatasetPoolResponse} (dataset → pool): given a set of datapool ids,
 * resolves every dataset that belongs to any of them. Consumed by OPA to enumerate the datasets a
 * DATAPOOL-scoped grant covers, e.g. for Superset's schema-listing rule — {@code pool_of()} alone
 * only answers "which pool is this dataset in," not "which datasets are in this pool."
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatapoolDatasetsBatchRequest {

  @NotEmpty
  @Size(max = 1000)
  private List<
          @Pattern(
              regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
              message = "poolIds must be valid UUIDs")
          String>
      poolIds;
}
