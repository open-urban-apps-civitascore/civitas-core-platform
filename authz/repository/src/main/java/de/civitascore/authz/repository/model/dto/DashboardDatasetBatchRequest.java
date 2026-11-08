package de.civitascore.authz.repository.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/dashboard-dataset/batch}.
 *
 * <p>The first (and so far only) batch-shaped endpoint in this service — every other lookup here is
 * a single-id {@code GET}. Batching exists specifically so a caller resolving a whole dashboard
 * list (e.g. civitas_security.py's {@code DashboardAccessFilter} override) can do it in one
 * round-trip instead of one request per dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDatasetBatchRequest {

  @NotEmpty
  @Size(max = 1000)
  private List<@Pattern(regexp = "^[0-9]+$") String> externalDashboardIds;
}
