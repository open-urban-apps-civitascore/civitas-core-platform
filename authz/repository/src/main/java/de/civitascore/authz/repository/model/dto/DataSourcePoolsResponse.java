package de.civitascore.authz.repository.model.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO listing the datapools a single data source is assigned to.
 *
 * <p>Returned by the {@code /api/v1/datasource-pools/{dataSourceId}} endpoint and consumed by OPA
 * to apply datapool inheritance to data sources: a DATAPOOL-scoped grant conveys read access to the
 * data sources assigned to that pool.
 *
 * <p>Empty when the data source is assigned to no pool in particular — which covers both the
 * unrestricted and the unusable case, since neither conveys read through a pool grant. The policy
 * therefore needs no knowledge of the datapool-scope enum.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePoolsResponse {
  private List<UUID> poolIds;
}
