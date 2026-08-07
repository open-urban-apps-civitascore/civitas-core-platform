package de.civitascore.authz.repository.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO describing the authorization-relevant attributes of a single dataset.
 *
 * <p>Returned by the {@code /api/v1/dataset-pool/{datasetId}} endpoint and consumed by OPA. It
 * answers two questions in one round-trip:
 *
 * <ul>
 *   <li>{@link #poolId} — the datapool the dataset belongs to (Epic 1 union inheritance: a
 *       DATAPOOL-scoped grant applies to every dataset in that pool). {@code null} when the dataset
 *       is not assigned to any pool.
 *   <li>{@link #openDataAccess} — whether the dataset is flagged for open data access, i.e. anyone
 *       (including anonymous callers) may read its <b>payload</b> (ABAC, see OPA open_data policy).
 *       Open data is payload-only; metadata/discovery stay authenticated. Never {@code null} —
 *       defaults to {@code false} (secure default).
 *   <li>{@link #frostProjectId} — the FROST-Server project this dataset's sensor data (if any)
 *       lives under, straight from {@code DataSet.projectId}. {@code null} when the dataset has no
 *       FROST sink. Lets OPA's Trino row-filter policy map a readable dataset id to the FROST
 *       project id it needs to filter {@code frost_scoped.*} views by, without a separate
 *       lookup/round-trip.
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetPoolResponse {
  private UUID poolId;
  private boolean openDataAccess;
  private String frostProjectId;
}
