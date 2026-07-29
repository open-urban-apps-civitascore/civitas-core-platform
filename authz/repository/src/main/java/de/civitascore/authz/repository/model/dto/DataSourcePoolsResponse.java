package de.civitascore.authz.repository.model.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO describing which datapools a single data source may be used in.
 *
 * <p>Returned by the {@code /api/v1/datasource-pools/{dataSourceId}} endpoint and consumed by OPA
 * to apply datapool inheritance to data sources: a DATAPOOL-scoped grant conveys read access to the
 * data sources usable in that pool.
 *
 * <p>Two fields rather than one, because a data source usable in every pool cannot be expressed as
 * a pool list. A data source usable in no pool at all reports an empty list and a false flag. The
 * policy therefore needs no knowledge of the datapool-scope enum.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePoolsResponse {

  /** The datapools this data source names explicitly; empty unless it is confined to some. */
  private List<UUID> poolIds;

  /** Whether this data source is unrestricted and therefore usable in every datapool. */
  private boolean usableInAllPools;
}
