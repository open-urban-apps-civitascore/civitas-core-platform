package de.civitascore.authz.repository.service;

import de.civitascore.authz.repository.data.DataSourceRepository;
import de.civitascore.authz.repository.model.dto.DataSourcePoolsResponse;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service resolving which datapools a data source may be used in.
 *
 * <p>Consumed by OPA (via the datasource-pools endpoint) to apply datapool inheritance to data
 * sources: a DATAPOOL-scoped grant conveys read access to the data sources usable in that pool.
 * {@code ALL} is usable in every pool, {@code SPECIFIC} in the pools it names, {@code NONE} in none
 * — the same rule the portal applies when a pipeline references a data source.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourcePoolsService {

  private final DataSourceRepository dataSourceRepository;

  /**
   * Returns the datapools a data source may be used in.
   *
   * @param dataSourceId the data source id
   * @return present with the pool usability if the data source exists, empty if it does not
   */
  @Transactional(readOnly = true)
  public Optional<DataSourcePoolsResponse> getDataSourcePools(UUID dataSourceId) {
    log.debug("Fetching datapool usability for data source: {}", dataSourceId);
    return dataSourceRepository
        .findById(dataSourceId)
        .map(
            dataSource -> {
              DatapoolScopeType scopeType = dataSource.getDatapoolScopeType();
              List<UUID> poolIds =
                  scopeType == DatapoolScopeType.SPECIFIC
                      ? dataSource.getScopedDataPools().stream().map(DataPool::getId).toList()
                      : List.of();
              return new DataSourcePoolsResponse(poolIds, scopeType == DatapoolScopeType.ALL);
            });
  }
}
